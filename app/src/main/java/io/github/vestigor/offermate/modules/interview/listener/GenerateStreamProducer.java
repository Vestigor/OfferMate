package io.github.vestigor.offermate.modules.interview.listener;

import io.github.vestigor.offermate.common.async.AbstractStreamProducer;
import io.github.vestigor.offermate.common.constant.AsyncTaskStreamConstants;
import io.github.vestigor.offermate.infrastructure.redis.RedisService;
import io.github.vestigor.offermate.modules.interview.service.InterviewPersistenceService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 面试题目生成任务生产者
 * 负责发送题目生成任务到 Redis Stream
 */
@Slf4j
@Component
public class GenerateStreamProducer extends AbstractStreamProducer<GenerateStreamProducer.GenerateTaskPayload> {

    private final InterviewPersistenceService persistenceService;

    public record GenerateTaskPayload(String sessionId, Long resumeId, int questionCount) {}

    public GenerateStreamProducer(RedisService redisService, InterviewPersistenceService persistenceService) {
        super(redisService);
        this.persistenceService = persistenceService;
    }

    /**
     * 发送题目生成任务到 Redis Stream
     */
    public void sendGenerateTask(String sessionId, Long resumeId, int questionCount) {
        sendTask(new GenerateTaskPayload(sessionId, resumeId, questionCount));
    }

    @Override
    protected String taskDisplayName() {
        return "题目生成";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.INTERVIEW_GENERATE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(GenerateTaskPayload payload) {
        return Map.of(
                AsyncTaskStreamConstants.FIELD_SESSION_ID, payload.sessionId(),
                AsyncTaskStreamConstants.FIELD_RESUME_ID, payload.resumeId().toString(),
                AsyncTaskStreamConstants.FIELD_QUESTION_COUNT, String.valueOf(payload.questionCount()),
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
        );
    }

    @Override
    protected String payloadIdentifier(GenerateTaskPayload payload) {
        return "sessionId=" + payload.sessionId();
    }

    @Override
    protected void onSendFailed(GenerateTaskPayload payload, String error) {
        persistenceService.setGenerateError(payload.sessionId(), truncateError("题目生成任务入队失败: " + error));
    }
}
