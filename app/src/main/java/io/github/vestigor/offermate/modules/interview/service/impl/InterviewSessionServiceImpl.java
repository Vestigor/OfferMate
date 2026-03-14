package io.github.vestigor.offermate.modules.interview.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.common.model.AsyncTaskStatus;
import io.github.vestigor.offermate.infrastructure.redis.InterviewSessionCache;
import io.github.vestigor.offermate.modules.interview.listener.EvaluateStreamProducer;
import io.github.vestigor.offermate.modules.interview.listener.GenerateStreamProducer;
import io.github.vestigor.offermate.modules.interview.model.dto.*;
import io.github.vestigor.offermate.modules.interview.model.entity.InterviewAnswerEntity;
import io.github.vestigor.offermate.modules.interview.model.entity.InterviewSessionEntity;
import io.github.vestigor.offermate.modules.interview.service.AnswerEvaluationService;
import io.github.vestigor.offermate.modules.interview.service.InterviewPersistenceService;

import io.github.vestigor.offermate.modules.interview.service.InterviewSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 面试会话管理服务
 * 管理面试会话的生命周期，使用 Redis 缓存会话状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSessionCache sessionCache;
    private final ObjectMapper objectMapper;
    private final EvaluateStreamProducer evaluateStreamProducer;
    private final GenerateStreamProducer generateStreamProducer;

    /**
     * 创建新的面试会话（异步生成题目）
     * 立即返回 GENERATING 状态的会话，题目生成通过 Redis Stream 异步执行
     */
    @Override
    public InterviewSessionDTO createSession(CreateInterviewRequest request) {
        // 如果指定了resumeId且未强制创建，检查是否有未完成的会话（含GENERATING）
        if (request.resumeId() != null && !Boolean.TRUE.equals(request.forceCreate())) {
            Optional<InterviewSessionDTO> unfinishedOpt = findUnfinishedSession(request.resumeId());
            if (unfinishedOpt.isPresent()) {
                log.info("检测到未完成的面试会话，返回现有会话: resumeId={}, sessionId={}, status={}",
                        request.resumeId(), unfinishedOpt.get().sessionId(), unfinishedOpt.get().status());
                return unfinishedOpt.get();
            }
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        log.info("创建新面试会话（异步生成题目）: {}, 题目数量: {}, resumeId: {}",
                sessionId, request.questionCount(), request.resumeId());

        // 保存到数据库（GENERATING 状态，题目为空）
        if (request.resumeId() != null) {
            try {
                persistenceService.saveSessionForGenerate(sessionId, request.resumeId(), request.questionCount());
            } catch (Exception e) {
                log.error("保存面试会话到数据库失败: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建面试会话失败");
            }
        }

        // 发送题目生成任务到 Redis Stream
        generateStreamProducer.sendGenerateTask(sessionId, request.resumeId(), request.questionCount());

        return new InterviewSessionDTO(
                sessionId,
                request.resumeText(),
                request.questionCount(),
                0,
                List.of(),
                InterviewSessionDTO.SessionStatus.GENERATING,
                null,
                null
        );
    }

    /**
     * 获取会话信息，优先从缓存获取，缓存未命中则从数据库恢复
     */
    @Override
    public InterviewSessionDTO getSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<InterviewSessionCache.CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            return toDTO(cachedOpt.get());
        }

        // 2. 缓存未命中，从数据库恢复
        Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
        if (entityOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        InterviewSessionEntity entity = entityOpt.get();

        // GENERATING 状态不放入缓存，直接返回
        if (entity.getStatus() == InterviewSessionEntity.SessionStatus.GENERATING) {
            return toGeneratingDTO(entity);
        }

        InterviewSessionCache.CachedSession restoredSession = restoreSessionFromEntity(entity);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return toDTO(restoredSession);
    }

    /**
     * 查找并恢复未完成的面试会话（含GENERATING状态）
     */
    @Override
    public Optional<InterviewSessionDTO> findUnfinishedSession(Long resumeId) {
        try {
            // 1. 先从 Redis 缓存查找（只包含 CREATED/IN_PROGRESS）
            Optional<String> cachedSessionIdOpt = sessionCache.findUnfinishedSessionId(resumeId);
            if (cachedSessionIdOpt.isPresent()) {
                String sessionId = cachedSessionIdOpt.get();
                Optional<InterviewSessionCache.CachedSession> cachedOpt = sessionCache.getSession(sessionId);
                if (cachedOpt.isPresent()) {
                    log.debug("从 Redis 缓存找到未完成会话: resumeId={}, sessionId={}", resumeId, sessionId);
                    return Optional.of(toDTO(cachedOpt.get()));
                }
            }

            // 2. 缓存未命中，从数据库查找（含GENERATING）
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findUnfinishedSession(resumeId);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }

            InterviewSessionEntity entity = entityOpt.get();

            // GENERATING 状态不放入缓存，直接返回
            if (entity.getStatus() == InterviewSessionEntity.SessionStatus.GENERATING) {
                return Optional.of(toGeneratingDTO(entity));
            }

            InterviewSessionCache.CachedSession restoredSession = restoreSessionFromEntity(entity);
            if (restoredSession != null) {
                return Optional.of(toDTO(restoredSession));
            }
        } catch (Exception e) {
            log.error("恢复未完成会话失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 查找并恢复未完成的面试会话，如果不存在则抛出异常
     */
    @Override
    public InterviewSessionDTO findUnfinishedSessionOrThrow(Long resumeId) {
        return findUnfinishedSession(resumeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "未找到未完成的面试会话"));
    }

    /**
     * 从数据库恢复会话并缓存到 Redis
     */
    private InterviewSessionCache.CachedSession restoreSessionFromDatabase(String sessionId) {
        try {
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
            if (entityOpt.isEmpty()) return null;
            InterviewSessionEntity entity = entityOpt.get();
            if (entity.getStatus() == InterviewSessionEntity.SessionStatus.GENERATING) {
                // GENERATING 状态不放缓存
                return null;
            }
            return restoreSessionFromEntity(entity);
        } catch (Exception e) {
            log.error("从数据库恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从实体恢复会话并缓存到 Redis
     */
    private InterviewSessionCache.CachedSession restoreSessionFromEntity(InterviewSessionEntity entity) {
        try {
            String questionsJson = entity.getQuestionsJson();
            if (questionsJson == null || questionsJson.isBlank() || "[]".equals(questionsJson)) {
                log.warn("会话题目为空，无法恢复: sessionId={}", entity.getSessionId());
                return null;
            }

            // 解析问题列表
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                    questionsJson,
                    new TypeReference<>() {}
            );

            // 恢复已保存的答案
            List<InterviewAnswerEntity> answers = persistenceService.findAnswersBySessionId(entity.getSessionId());
            for (InterviewAnswerEntity answer : answers) {
                int index = answer.getQuestionIndex();
                if (index >= 0 && index < questions.size()) {
                    InterviewQuestionDTO question = questions.get(index);
                    questions.set(index, question.withAnswer(answer.getUserAnswer()));
                }
            }

            InterviewSessionDTO.SessionStatus status = convertStatus(entity.getStatus());

            // 保存到 Redis 缓存
            sessionCache.saveSession(
                    entity.getSessionId(),
                    entity.getResume().getResumeText(),
                    entity.getResume().getId(),
                    questions,
                    entity.getCurrentQuestionIndex(),
                    status
            );

            log.info("从数据库恢复会话到 Redis: sessionId={}, currentIndex={}, status={}",
                    entity.getSessionId(), entity.getCurrentQuestionIndex(), entity.getStatus());

            return sessionCache.getSession(entity.getSessionId()).orElse(null);
        } catch (Exception e) {
            log.error("恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private InterviewSessionDTO.SessionStatus convertStatus(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case GENERATING -> InterviewSessionDTO.SessionStatus.GENERATING;
            case CREATED -> InterviewSessionDTO.SessionStatus.CREATED;
            case IN_PROGRESS -> InterviewSessionDTO.SessionStatus.IN_PROGRESS;
            case COMPLETED -> InterviewSessionDTO.SessionStatus.COMPLETED;
            case EVALUATED -> InterviewSessionDTO.SessionStatus.EVALUATED;
        };
    }

    /**
     * 获取当前问题的响应
     */
    @Override
    public Map<String, Object> getCurrentQuestionResponse(String sessionId) {
        InterviewQuestionDTO question = getCurrentQuestion(sessionId);
        if (question == null) {
            return Map.of(
                    "completed", true,
                    "message", "所有问题已回答完毕"
            );
        }
        return Map.of(
                "completed", false,
                "question", question
        );
    }

    /**
     * 获取当前问题
     */
    @Override
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        InterviewSessionCache.CachedSession session = getOrRestoreSession(sessionId);
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        if (session.getCurrentIndex() >= questions.size()) {
            return null; // 所有问题已回答完
        }

        // 更新状态为进行中
        if (session.getStatus() == InterviewSessionDTO.SessionStatus.CREATED) {
            session.setStatus(InterviewSessionDTO.SessionStatus.IN_PROGRESS);
            sessionCache.updateSessionStatus(sessionId, InterviewSessionDTO.SessionStatus.IN_PROGRESS);

            // 同步到数据库
            try {
                persistenceService.updateSessionStatus(sessionId,
                        InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            } catch (Exception e) {
                log.warn("更新会话状态失败: {}", e.getMessage());
            }
        }

        return questions.get(session.getCurrentIndex());
    }

    /**
     * 提交答案并进入下一题；如果是最后一题，自动触发异步评估
     */
    @Override
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        InterviewSessionCache.CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 移动到下一题
        int newIndex = index + 1;

        // 检查是否全部完成
        boolean hasNextQuestion = newIndex < questions.size();
        InterviewQuestionDTO nextQuestion = hasNextQuestion ? questions.get(newIndex) : null;

        InterviewSessionDTO.SessionStatus newStatus = hasNextQuestion ? InterviewSessionDTO.SessionStatus.IN_PROGRESS : InterviewSessionDTO.SessionStatus.COMPLETED;

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), questions);
        sessionCache.updateCurrentIndex(request.sessionId(), newIndex);
        if (newStatus == InterviewSessionDTO.SessionStatus.COMPLETED) {
            sessionCache.updateSessionStatus(request.sessionId(), InterviewSessionDTO.SessionStatus.COMPLETED);
        }

        // 保存答案到数据库
        try {
            persistenceService.saveAnswer(
                    request.sessionId(), index,
                    question.question(), question.category(),
                    request.answer(), 0, null  // 分数在报告生成时更新
            );
            persistenceService.updateCurrentQuestionIndex(request.sessionId(), newIndex);
            persistenceService.updateSessionStatus(request.sessionId(),
                    newStatus == InterviewSessionDTO.SessionStatus.COMPLETED
                            ? InterviewSessionEntity.SessionStatus.COMPLETED
                            : InterviewSessionEntity.SessionStatus.IN_PROGRESS);

            // 如果是最后一题，设置评估状态为 PENDING 并触发异步评估
            if (!hasNextQuestion) {
                persistenceService.updateEvaluateStatus(request.sessionId(), AsyncTaskStatus.PENDING, null);
                evaluateStreamProducer.sendEvaluateTask(request.sessionId());
                log.info("会话 {} 已完成所有问题，评估任务已入队", request.sessionId());
            }
        } catch (Exception e) {
            log.warn("保存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 提交答案: 问题{}, 剩余{}题",
                request.sessionId(), index, questions.size() - newIndex);

        return new SubmitAnswerResponse(
                hasNextQuestion,
                nextQuestion,
                newIndex,
                questions.size()
        );
    }

    /**
     * 暂存答案
     */
    @Override
    public void saveAnswer(SubmitAnswerRequest request) {
        InterviewSessionCache.CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), questions);

        // 更新状态为进行中
        if (session.getStatus() == InterviewSessionDTO.SessionStatus.CREATED) {
            sessionCache.updateSessionStatus(request.sessionId(), InterviewSessionDTO.SessionStatus.IN_PROGRESS);
        }

        // 保存答案到数据库（不更新currentIndex）
        try {
            persistenceService.saveAnswer(
                    request.sessionId(), index,
                    question.question(), question.category(),
                    request.answer(), 0, null
            );
            persistenceService.updateSessionStatus(request.sessionId(),
                    InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        } catch (Exception e) {
            log.warn("暂存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 暂存答案: 问题{}", request.sessionId(), index);
    }

    /**
     * 提前交卷，进行异步评估
     */
    @Override
    public void completeInterview(String sessionId) {
        InterviewSessionCache.CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() == InterviewSessionDTO.SessionStatus.COMPLETED || session.getStatus() == InterviewSessionDTO.SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        // 更新 Redis 缓存
        sessionCache.updateSessionStatus(sessionId, InterviewSessionDTO.SessionStatus.COMPLETED);

        // 更新数据库状态
        try {
            persistenceService.updateSessionStatus(sessionId,
                    InterviewSessionEntity.SessionStatus.COMPLETED);
            // 设置评估状态为 PENDING
            persistenceService.updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
        } catch (Exception e) {
            log.warn("更新会话状态失败: {}", e.getMessage());
        }

        // 发送评估任务到 Redis Stream
        evaluateStreamProducer.sendEvaluateTask(sessionId);

        log.info("会话 {} 提前交卷，评估任务已入队", sessionId);
    }

    /**
     * 获取或恢复会话（优先从缓存获取）
     */
    private InterviewSessionCache.CachedSession getOrRestoreSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<InterviewSessionCache.CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            // 刷新 TTL
            sessionCache.refreshSessionTTL(sessionId);
            return cachedOpt.get();
        }

        // 2. 缓存未命中，从数据库恢复
        InterviewSessionCache.CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return restoredSession;
    }

    /**
     * 生成评估报告
     */
    @Override
    public InterviewReportDTO generateReport(String sessionId) {
        InterviewSessionCache.CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() != InterviewSessionDTO.SessionStatus.COMPLETED && session.getStatus() != InterviewSessionDTO.SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_COMPLETED, "面试尚未完成，无法生成报告");
        }

        log.info("生成面试报告: {}", sessionId);

        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        InterviewReportDTO report = evaluationService.evaluateInterview(
                sessionId,
                session.getResumeText(),
                questions
        );

        // 更新 Redis 缓存状态
        sessionCache.updateSessionStatus(sessionId, InterviewSessionDTO.SessionStatus.EVALUATED);

        // 保存报告到数据库
        try {
            persistenceService.saveReport(sessionId, report);
        } catch (Exception e) {
            log.warn("保存报告到数据库失败: {}", e.getMessage());
        }

        return report;
    }

    /**
     * 删除面试会话（清理缓存并删除数据库记录）
     */
    @Override
    public void deleteSession(String sessionId) {
        sessionCache.deleteSession(sessionId);
        persistenceService.deleteSessionBySessionId(sessionId);
    }

    /**
     * 将缓存会话转换为 DTO
     */
    private InterviewSessionDTO toDTO(InterviewSessionCache.CachedSession session) {
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        return new InterviewSessionDTO(
                session.getSessionId(),
                session.getResumeText(),
                questions.size(),
                session.getCurrentIndex(),
                questions,
                session.getStatus(),
                null,
                null
        );
    }

    /**
     * 将 GENERATING 状态的实体转换为 DTO
     */
    private InterviewSessionDTO toGeneratingDTO(InterviewSessionEntity entity) {
        return new InterviewSessionDTO(
                entity.getSessionId(),
                entity.getResume() != null ? entity.getResume().getResumeText() : "",
                entity.getTotalQuestions() != null ? entity.getTotalQuestions() : 0,
                0,
                List.of(),
                InterviewSessionDTO.SessionStatus.GENERATING,
                entity.getGenerateError(),
                entity.getFollowUpCount()
        );
    }
}
