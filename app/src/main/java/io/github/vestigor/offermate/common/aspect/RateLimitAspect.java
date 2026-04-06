package io.github.vestigor.offermate.common.aspect;

import io.github.vestigor.offermate.common.annotiation.RateLimit;
import io.github.vestigor.offermate.common.exception.RateLimitExceededException;
import io.github.vestigor.offermate.common.security.SecurityUtils;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    /**
     * Lua 脚本缓存
     */
    private static String LUA_SCRIPT;
    private String luaScriptSha;

    /*
      加载限流 Lua 脚本
     */
    static {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/rate_limit.lua");
            LUA_SCRIPT = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("加载限流 Lua 脚本失败", e);
        }
    }

    /**
     * 预加载脚本到 Redis 提高性能
     */
    @PostConstruct
    public void init() {
        this.luaScriptSha = redissonClient.getScript(StringCodec.INSTANCE).scriptLoad(LUA_SCRIPT);
        log.info("限流 Lua 脚本加载完成, SHA1: {}", luaScriptSha);
    }

    /**
     * 拦截带 @RateLimit 注解的方法
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        // 计算时间窗口（毫秒）
        long intervalMs = calculateIntervalMs(rateLimit.interval(), rateLimit.timeUnit());

        // 根据配置维度动态生成 Redis Keys
        List<String> keys = generateKeys(className, methodName, rateLimit.dimensions());

        // 调用 Lua 脚本执行原子限流
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);

        // 准备参数
        List<Object> keysList = new ArrayList<>(keys);
        Object[] args = {
                String.valueOf(System.currentTimeMillis()),  // ARGV[1]: 当前时间戳
                String.valueOf(1),                          // ARGV[2]: 申请令牌数（默认1个）
                String.valueOf(intervalMs),                  // ARGV[3]: 时间窗口
                String.valueOf(rateLimit.count()),           // ARGV[4]: 最大令牌数
                UUID.randomUUID().toString()                 // ARGV[5]: 请求唯一标识
        };

        Object resultObj = script.evalSha(
                RScript.Mode.READ_WRITE,
                luaScriptSha,
                RScript.ReturnType.VALUE,
                keysList,
                args
        );

        Long result = convertToLong(resultObj);

        // 处理限流结果
        if (result == null || result == 0) {
            return handleRateLimitExceeded(joinPoint, rateLimit, keys);
        }

        // 执行原方法
        return joinPoint.proceed();
    }

    private long calculateIntervalMs(long interval, RateLimit.TimeUnit timeUnit) {
        return switch (timeUnit) {
            case MILLISECONDS -> interval;
            case SECONDS -> interval * 1000;
            case MINUTES -> interval * 1000 * 60;
            case HOURS -> interval * 1000 * 60 * 60;
            case DAYS -> interval * 1000 * 60 * 60 * 24;
        };
    }

    /**
     * 处理限流超出情况
     */
    private Object handleRateLimitExceeded(ProceedingJoinPoint joinPoint, RateLimit rateLimit, List<String> keys) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        // 如果配置了降级方法，则调用降级方法
        if (rateLimit.fallback() != null && !rateLimit.fallback().isEmpty()) {
            try {
                Method fallbackMethod = findFallbackMethod(joinPoint, rateLimit.fallback());
                if (fallbackMethod != null) {
                    log.debug("限流触发，执行降级方法: {}.{} -> {}",
                            joinPoint.getTarget().getClass().getSimpleName(),
                            methodName,
                            rateLimit.fallback());
                    // 如果降级方法有参数，传入原方法的参数
                    if (fallbackMethod.getParameterCount() > 0) {
                        return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
                    } else {
                        return fallbackMethod.invoke(joinPoint.getTarget());
                    }
                }
            } catch (Exception e) {
                log.error("降级方法执行失败: {}", rateLimit.fallback(), e);
            }
        }

        // 没有降级方法或降级失败，抛出限流异常
        log.debug("限流触发，拒绝请求: keys={}, count={} per {} {}",
                keys, rateLimit.count(), rateLimit.interval(), rateLimit.timeUnit());
        throw new RateLimitExceededException("请求过于频繁，请稍后再试");
    }

    /**
     * 查找降级方法
     */
    private Method findFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackName) {
        Class<?> targetClass = joinPoint.getTarget().getClass();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?>[] parameterTypes = signature.getParameterTypes();

        try {
            // 1. 尝试查找同参数列表的方法
            Method method = targetClass.getDeclaredMethod(fallbackName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            // 2. 尝试查找无参方法
            try {
                Method method = targetClass.getDeclaredMethod(fallbackName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ex) {
                log.warn("未找到降级方法: {}.{} (需无参或参数列表一致)",
                        targetClass.getSimpleName(), fallbackName);
                return null;
            }
        }
    }

    /**
     * 生成限流键列表
     */
    private List<String> generateKeys(String className, String methodName, RateLimit.Dimension[] dimensions){
        List<String> keys = new ArrayList<>();

        String hashTag = "{" + className + ":" + methodName + "}";
        String keyPrefix = "ratelimit:" + hashTag;

        for (RateLimit.Dimension dimension : dimensions) {
            switch (dimension) {
                case GLOBAL -> keys.add(keyPrefix + ":global");
                case IP -> keys.add(keyPrefix + ":ip:" + getClientIp());
                case USER -> keys.add(keyPrefix + ":user:" + getCurrentUserId());
            }
        }

        return keys;
    }

    /**
     * 将结果对象安全转换为 Long
     */
    private Long convertToLong(Object obj) {
        switch (obj) {
            case null -> {
                return null;
            }
            case Long l -> {
                return l;
            }
            case Integer i -> {
                return i.longValue();
            }
            case Short i -> {
                return i.longValue();
            }
            case Byte b -> {
                return b.longValue();
            }
            case String s -> {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException e) {
                    log.warn("无法将字符串转换为Long: {}", obj);
                    return null;
                }
            }
            default -> {
            }
        }
        log.warn("不支持的对象类型转换为Long: {}", obj.getClass().getName());
        return null;
    }

    /**
     * 获取客户端真实 IP
     * 处理 X-Forwarded-For 头，支持代理服务器场景
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多个 IP 的情况（X-Forwarded-For 可能包含多个 IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }


    /**
     * 获取当前用户 ID
     * 从 Spring Security 上下文中获取
     */
    private String getCurrentUserId() {
        return String.valueOf(SecurityUtils.getUserId());
    }
}
