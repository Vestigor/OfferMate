package io.github.vestigor.offermate.modules.resume.service.impl;

import io.github.vestigor.offermate.common.ai.StructuredOutputInvoker;
import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeAnalysisResponse;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeAnalysisResponseDTO;
import io.github.vestigor.offermate.modules.resume.service.ResumeGradingService;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ResumeGradingServiceImpl implements ResumeGradingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeGradingService.class);

    private final ChatClient chatClient;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<ResumeAnalysisResponseDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;

    public ResumeGradingServiceImpl(
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/resume-analysis-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/resume-analysis-user.st") Resource userPromptResource) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(ResumeAnalysisResponseDTO.class);
    }

    /**
     * 分析简历并返回评分和建议
     */
    public ResumeAnalysisResponse analyzeResume(String resumeText) {
        log.info("开始分析简历，文本长度: {} 字符", resumeText.length());
        try {
            // 加载 ai 系统提示词
            String systemPrompt = systemPromptTemplate.render();

            // 加载用户提示词并填充变量
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeText", resumeText);
            String userPrompt = userPromptTemplate.render();

            // 添加格式指令到系统提示词
            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();

            // 调用AI
            ResumeAnalysisResponseDTO dto;
            try {
                dto = structuredOutputInvoker.invoke(
                        chatClient,
                        systemPromptWithFormat,
                        userPrompt,
                        outputConverter,
                        ErrorCode.RESUME_ANALYSIS_FAILED,
                        "简历分析失败",
                        "简历分析",
                        log
                );
                log.debug("AI 响应解析成功: overallScore = {}", dto.overallScore());
            } catch (Exception e) {
                log.error("简历分析AI调用失败: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "简历分析失败：" + e.getMessage());
            }

            ResumeAnalysisResponse result = convertToResponse(dto, resumeText);
            log.info("简历分析完成，总分: {}", result.overallScore());

            return result;
        } catch (Exception e) {
            log.error("简历分析失败: {}", e.getMessage(), e);
            return createErrorResponse(resumeText, e.getMessage());
        }
    }

    /**
     * 转换DTO为业务对象
     */
    private ResumeAnalysisResponse convertToResponse(ResumeAnalysisResponseDTO dto, String originalText) {
        ResumeAnalysisResponse.ScoreDetail scoreDetail = new ResumeAnalysisResponse.ScoreDetail(
                dto.scoreDetail().contentScore(),
                dto.scoreDetail().structureScore(),
                dto.scoreDetail().skillMatchScore(),
                dto.scoreDetail().expressionScore(),
                dto.scoreDetail().projectScore()
        );

        List<ResumeAnalysisResponse.Suggestion> suggestions = dto.suggestions().stream()
                .map(s -> new ResumeAnalysisResponse.Suggestion(s.category(), s.priority(), s.issue(), s.recommendation()))
                .toList();

        return new ResumeAnalysisResponse(
                dto.overallScore(),
                scoreDetail,
                dto.summary(),
                dto.strengths(),
                suggestions,
                originalText
        );
    }

    private ResumeAnalysisResponse createErrorResponse(String originalText, String errorMessage) {
        return new ResumeAnalysisResponse(
                0,
                new ResumeAnalysisResponse.ScoreDetail(0, 0, 0, 0, 0),
                "分析过程出现错误: " + errorMessage,
                List.of(),
                List.of(new ResumeAnalysisResponse.Suggestion(
                        "系统",
                        "高",
                        "AI 分析服务暂不可用",
                        "请稍后重试，或检查 AI 服务是否正常运行"

                )),
                originalText
        );
    }
}
