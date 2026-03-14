package io.github.vestigor.offermate.infrastructure.mapper;

import io.github.vestigor.offermate.modules.interview.model.dto.InterviewDetailDTO;
import io.github.vestigor.offermate.modules.interview.model.dto.InterviewReportDTO;
import io.github.vestigor.offermate.modules.interview.model.entity.InterviewAnswerEntity;
import io.github.vestigor.offermate.modules.interview.model.entity.InterviewSessionEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 面试相关的对象映射器
 * 注意：JSON字段需要在Service层手动处理
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InterviewMapper {

    // ========== QuestionEvaluation 映射 ==========

    /**
     * 将面试答案实体转换为问题评估详情
     */
    @Mapping(target = "questionIndex", source = "questionIndex", qualifiedByName = "nullIndexToZero")
    @Mapping(target = "question",      source = "question")
    @Mapping(target = "category",      source = "category")
    @Mapping(target = "userAnswer",    source = "userAnswer")
    @Mapping(target = "score",         source = "score", qualifiedByName = "nullScoreToZero")
    @Mapping(target = "feedback",      source = "feedback")
    InterviewReportDTO.QuestionEvaluation toQuestionEvaluation(InterviewAnswerEntity entity);

    /**
     * 批量转换面试答案实体
     */
    List<InterviewReportDTO.QuestionEvaluation> toQuestionEvaluations(List<InterviewAnswerEntity> entities);

    // ========== AnswerDetailDTO 映射 ==========

    /**
     * InterviewAnswerEntity 转换为 AnswerDetailDTO
     * keyPoints 需要在 Service 层从 JSON 解析后传入
     */
    @Mapping(target = "keyPoints", source = "keyPoints")
    InterviewDetailDTO.AnswerDetailDTO toAnswerDetailDTO(
            InterviewAnswerEntity entity,
            List<String> keyPoints
    );

    /**
     * 批量转换（JSON 解析委托给 Service 层）
     */
    default List<InterviewDetailDTO.AnswerDetailDTO> toAnswerDetailDTOList(
            List<InterviewAnswerEntity> entities,
            Function<InterviewAnswerEntity, List<String>> keyPointsExtractor) {
        return entities.stream()
                .map(e -> toAnswerDetailDTO(e, keyPointsExtractor.apply(e)))
                .toList();
    }

    // ========== InterviewDetailDTO 映射 ==========

    /**
     * InterviewSessionEntity 转换为 InterviewDetailDTO
     * questions, strengths, improvements, referenceAnswers, answers 需要在 Service 层处理
     */
    @Mapping(target = "status",          expression = "java(session.getStatus().toString())")
    @Mapping(target = "evaluateStatus",  expression = "java(session.getEvaluateStatus() != null ? session.getEvaluateStatus().name() : null)")
    @Mapping(target = "evaluateError",   source = "session.evaluateError")
    @Mapping(target = "generateError",   source = "session.generateError")
    @Mapping(target = "followUpCount",   source = "session.followUpCount")
    @Mapping(target = "questions",       source = "questions")
    @Mapping(target = "strengths",       source = "strengths")
    @Mapping(target = "improvements",    source = "improvements")
    @Mapping(target = "referenceAnswers",source = "referenceAnswers")
    @Mapping(target = "answers",         source = "answers")
    InterviewDetailDTO toDetailDTO(
            InterviewSessionEntity session,
            List<Object> questions,
            List<String> strengths,
            List<String> improvements,
            List<Object> referenceAnswers,
            List<InterviewDetailDTO.AnswerDetailDTO> answers
    );

    // ========== InterviewSessionEntity 更新映射 ==========

    /**
     * 从 InterviewReportDTO 更新 InterviewSessionEntity
     * JSON 字段需要在 Service 层单独设置
     */
    @Mapping(target = "id",                   ignore = true)
    @Mapping(target = "sessionId",            ignore = true)
    @Mapping(target = "resume",               ignore = true)
    @Mapping(target = "totalQuestions",       ignore = true)
    @Mapping(target = "currentQuestionIndex", ignore = true)
    @Mapping(target = "questionsJson",        ignore = true)
    @Mapping(target = "strengthsJson",        ignore = true)
    @Mapping(target = "improvementsJson",     ignore = true)
    @Mapping(target = "referenceAnswersJson", ignore = true)
    @Mapping(target = "answers",              ignore = true)
    @Mapping(target = "createdAt",            ignore = true)
    @Mapping(target = "status",               ignore = true)
    @Mapping(target = "completedAt",          ignore = true)
    void updateSessionFromReport(InterviewReportDTO report, @MappingTarget InterviewSessionEntity session);

    // ========== 面试历史列表项映射 ==========

    /**
     * InterviewSessionEntity 转换为简要信息 Map（用于 ResumeDetailDTO 中的面试历史列表）
     */
    default Map<String, Object> toInterviewHistoryItem(InterviewSessionEntity session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",             session.getId());
        map.put("sessionId",      session.getSessionId());
        map.put("totalQuestions", session.getTotalQuestions());
        map.put("status",         session.getStatus().toString());
        map.put("evaluateStatus", session.getEvaluateStatus() != null ? session.getEvaluateStatus().name() : null);
        map.put("evaluateError",  session.getEvaluateError());
        map.put("generateError",  session.getGenerateError());
        map.put("followUpCount",  session.getFollowUpCount());
        map.put("overallScore",   session.getOverallScore());
        map.put("createdAt",      session.getCreatedAt());
        map.put("completedAt",    session.getCompletedAt());
        return map;
    }

    /**
     * 批量转换面试历史
     */
    default List<Object> toInterviewHistoryList(List<InterviewSessionEntity> sessions) {
        return sessions.stream()
                .map(s -> (Object) toInterviewHistoryItem(s))
                .toList();
    }

    // ========== 工具方法 ==========

    @Named("nullIndexToZero")
    default int nullIndexToZero(Integer value) {
        return value != null ? value : 0;
    }

    @Named("nullScoreToZero")
    default int nullScoreToZero(Integer value) {
        return value != null ? value : 0;
    }
}
