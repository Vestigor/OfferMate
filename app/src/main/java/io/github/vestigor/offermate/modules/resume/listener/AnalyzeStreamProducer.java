package io.github.vestigor.offermate.modules.resume.listener;

import io.github.vestigor.offermate.common.async.AbstractStreamProducer;
import io.github.vestigor.offermate.common.constant.AsyncTaskStreamConstants;
import io.github.vestigor.offermate.common.model.AsyncTaskStatus;
import io.github.vestigor.offermate.infrastructure.redis.RedisService;
import io.github.vestigor.offermate.modules.resume.repository.ResumeRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AnalyzeStreamProducer extends AbstractStreamProducer<AnalyzeStreamProducer.AnalyzeTaskPayload> {

    private final ResumeRepository resumeRepository;

    record AnalyzeTaskPayload(Long resumeId, String content) {}

    public AnalyzeStreamProducer(RedisService redisService, ResumeRepository resumeRepository) {
        super(redisService);
        this.resumeRepository = resumeRepository;
    }

    /**
     * 发送分析任务到 Redis Stream
     */
    public void sendAnalyzeTask(Long resumeId, String content) {
        sendTask(new AnalyzeTaskPayload(resumeId, content));
    }

    @Override
    protected String taskDisplayName() {
        return "分析";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(AnalyzeTaskPayload payload) {
        return Map.of(
                AsyncTaskStreamConstants.FIELD_RESUME_ID, payload.resumeId().toString(),
                AsyncTaskStreamConstants.FIELD_CONTENT, payload.content(),
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
        );
    }

    @Override
    protected String payloadIdentifier(AnalyzeTaskPayload payload) {
        return "resumeId=" + payload.resumeId();
    }

    @Override
    protected void onSendFailed(AnalyzeTaskPayload payload, String error) {
        updateAnalyzeStatus(payload.resumeId(), AsyncTaskStatus.FAILED, truncateError(error));
    }

    /**
     * 更新分析状态
     */
    private void updateAnalyzeStatus(Long resumeId, AsyncTaskStatus status, String error) {
        resumeRepository.findById(resumeId).ifPresent(resume -> {
            resume.setAnalyzeStatus(status);
            if (error != null) {
                resume.setAnalyzeError(error.length() > 500 ? error.substring(0, 500) : error);
            }
            resumeRepository.save(resume);
        });
    }

}
