package io.github.vestigor.offermate.modules.interview.listener;

import io.github.vestigor.offermate.common.async.AbstractStreamConsumer;
import io.github.vestigor.offermate.common.constant.AsyncTaskStreamConstants;
import io.github.vestigor.offermate.infrastructure.redis.RedisService;
import io.github.vestigor.offermate.modules.interview.model.dto.InterviewQuestionDTO;
import io.github.vestigor.offermate.modules.interview.service.InterviewPersistenceService;
import io.github.vestigor.offermate.modules.interview.service.InterviewQuestionService;
import io.github.vestigor.offermate.modules.resume.repository.ResumeRepository;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 面试题目生成 Stream 消费者
 * 负责从 Redis Stream 消费消息并异步生成面试题目
 */
@Slf4j
@Component
public class GenerateStreamConsumer extends AbstractStreamConsumer<GenerateStreamConsumer.GeneratePayload> {

    private final InterviewPersistenceService persistenceService;
    private final InterviewQuestionService questionService;
    private final ResumeRepository resumeRepository;

    public GenerateStreamConsumer(
            RedisService redisService,
            InterviewPersistenceService persistenceService,
            InterviewQuestionService questionService,
            ResumeRepository resumeRepository
    ) {
        super(redisService);
        this.persistenceService = persistenceService;
        this.questionService = questionService;
        this.resumeRepository = resumeRepository;
    }

    record GeneratePayload(String sessionId, Long resumeId, int questionCount) {}

    @Override
    protected String taskDisplayName() {
        return "题目生成";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.INTERVIEW_GENERATE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.INTERVIEW_GENERATE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.INTERVIEW_GENERATE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "generate-consumer";
    }

    @Override
    protected GeneratePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String sessionId = data.get(AsyncTaskStreamConstants.FIELD_SESSION_ID);
        String resumeIdStr = data.get(AsyncTaskStreamConstants.FIELD_RESUME_ID);
        String questionCountStr = data.get(AsyncTaskStreamConstants.FIELD_QUESTION_COUNT);
        if (sessionId == null || resumeIdStr == null || questionCountStr == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        return new GeneratePayload(sessionId, Long.parseLong(resumeIdStr), Integer.parseInt(questionCountStr));
    }

    @Override
    protected String payloadIdentifier(GeneratePayload payload) {
        return "sessionId=" + payload.sessionId();
    }

    @Override
    protected void markProcessing(GeneratePayload payload) {
        // 状态已是 GENERATING，仅记录日志
        log.debug("开始处理题目生成任务: sessionId={}", payload.sessionId());
    }

    @Override
    protected void processBusiness(GeneratePayload payload) {
        String sessionId = payload.sessionId();

        // 检查简历是否仍存在
        var resumeOpt = resumeRepository.findById(payload.resumeId());
        if (resumeOpt.isEmpty()) {
            log.warn("简历已被删除，跳过题目生成任务: resumeId={}", payload.resumeId());
            return;
        }
        String resumeText = resumeOpt.get().getResumeText();

        // 获取历史问题（避免重复出题）
        List<String> historicalQuestions = persistenceService.getHistoricalQuestionsByResumeId(payload.resumeId());

        // 生成面试题目
        List<InterviewQuestionDTO> questions = questionService.generateQuestions(
                resumeText,
                payload.questionCount(),
                historicalQuestions
        );

        // 统计追问数量
        int followUpCount = (int) questions.stream().filter(InterviewQuestionDTO::isFollowUp).count();

        // 更新会话
        persistenceService.updateSessionAfterGenerate(sessionId, questions, followUpCount);
    }

    @Override
    protected void markCompleted(GeneratePayload payload) {
        // updateSessionAfterGenerate 已将状态设置为 CREATED
        log.info("题目生成任务完成: sessionId={}", payload.sessionId());
    }

    @Override
    protected void markFailed(GeneratePayload payload, String error) {
        persistenceService.setGenerateError(payload.sessionId(), truncateError(error));
        log.error("题目生成任务最终失败: sessionId={}, error={}", payload.sessionId(), error);
    }

    @Override
    protected void retryMessage(GeneratePayload payload, int retryCount) {
        try {
            Map<String, String> message = Map.of(
                    AsyncTaskStreamConstants.FIELD_SESSION_ID, payload.sessionId(),
                    AsyncTaskStreamConstants.FIELD_RESUME_ID, payload.resumeId().toString(),
                    AsyncTaskStreamConstants.FIELD_QUESTION_COUNT, String.valueOf(payload.questionCount()),
                    AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );
            redisService().streamAdd(
                    AsyncTaskStreamConstants.INTERVIEW_GENERATE_STREAM_KEY,
                    message,
                    AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("题目生成任务已重新入队: sessionId={}, retryCount={}", payload.sessionId(), retryCount);
        } catch (Exception e) {
            log.error("重试入队失败: sessionId={}, error={}", payload.sessionId(), e.getMessage(), e);
            persistenceService.setGenerateError(payload.sessionId(),
                    truncateError("重试入队失败: " + e.getMessage()));
        }
    }
}
