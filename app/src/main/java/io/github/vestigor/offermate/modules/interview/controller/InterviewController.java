package io.github.vestigor.offermate.modules.interview.controller;

import io.github.vestigor.offermate.common.annotiation.RateLimit;
import io.github.vestigor.offermate.common.result.Result;
import io.github.vestigor.offermate.modules.interview.model.dto.*;
import io.github.vestigor.offermate.modules.interview.service.InterviewHistoryService;
import io.github.vestigor.offermate.modules.interview.service.InterviewSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 面试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewSessionService sessionService;
    private final InterviewHistoryService historyService;

    /**
     * 创建面试会话
     */
    @PostMapping("/sessions")
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 5)
    public Result<InterviewSessionDTO> createSession(@RequestBody CreateInterviewRequest request) {
        log.info("创建面试会话，题目数量: {}", request.questionCount());
        return Result.success(sessionService.createSession(request));
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<InterviewSessionDTO> getSession(@PathVariable String sessionId) {
        return Result.success(sessionService.getSession(sessionId));
    }

    /**
     * 获取当前问题
     */
    @GetMapping("/sessions/{sessionId}/question")
    public Result<Map<String, Object>> getCurrentQuestion(@PathVariable String sessionId) {
        return Result.success(sessionService.getCurrentQuestionResponse(sessionId));
    }

    /**
     * 提交答案
     */
    @PostMapping("/sessions/{sessionId}/answers")
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL}, count = 10)
    public Result<SubmitAnswerResponse> submitAnswer(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        Integer questionIndex = (Integer) body.get("questionIndex");
        String answer = (String) body.get("answer");
        log.info("提交答案: 会话{}, 问题{}", sessionId, questionIndex);
        return Result.success(sessionService.submitAnswer(new SubmitAnswerRequest(sessionId, questionIndex, answer)));
    }

    /**
     * 生成面试报告
     */
    @GetMapping("/sessions/{sessionId}/report")
    public Result<InterviewReportDTO> getReport(@PathVariable String sessionId) {
        log.info("生成面试报告: {}", sessionId);
        return Result.success(sessionService.generateReport(sessionId));
    }

    /**
     * 查找未完成的面试会话
     */
    @GetMapping("/sessions/unfinished/{resumeId}")
    public Result<InterviewSessionDTO> findUnfinishedSession(@PathVariable Long resumeId) {
        return Result.success(sessionService.findUnfinishedSessionOrThrow(resumeId));
    }

    /**
     * 暂存答案
     */
    @PutMapping("/sessions/{sessionId}/answers")
    public Result<Void> saveAnswer(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        Integer questionIndex = (Integer) body.get("questionIndex");
        String answer = (String) body.get("answer");
        log.info("暂存答案: 会话{}, 问题{}", sessionId, questionIndex);
        sessionService.saveAnswer(new SubmitAnswerRequest(sessionId, questionIndex, answer));
        return Result.success(null);
    }

    /**
     * 提前交卷
     */
    @PostMapping("/sessions/{sessionId}/complete")
    public Result<Void> completeInterview(@PathVariable String sessionId) {
        log.info("提前交卷: {}", sessionId);
        sessionService.completeInterview(sessionId);
        return Result.success(null);
    }

    /**
     * 获取面试会话详情
     */
    @GetMapping("/sessions/{sessionId}/details")
    public Result<InterviewDetailDTO> getInterviewDetail(@PathVariable String sessionId) {
        return Result.success(historyService.getInterviewDetail(sessionId));
    }

    /**
     * 导出面试报告为PDF
     */
    @GetMapping("/sessions/{sessionId}/export")
    public ResponseEntity<byte[]> exportInterviewPdf(@PathVariable String sessionId) {
        try {
            byte[] pdfBytes = historyService.exportInterviewPdf(sessionId);
            String filename = URLEncoder.encode("模拟面试报告_" + sessionId + ".pdf",
                    StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("导出PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除面试会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteInterview(@PathVariable String sessionId) {
        log.info("删除面试会话: {}", sessionId);
        sessionService.deleteSession(sessionId);
        return Result.success(null);
    }

}
