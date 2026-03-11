package io.github.vestigor.offermate.modules.interview.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试答案实体
 */
@Data
@Entity
@Table(name = "interview_answers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_interview_answer_session_question", columnNames = {"session_id", "question_index"})
        },
        indexes = {
                @Index(name = "idx_interview_answer_session_question", columnList = "session_id,question_index")
        })
public class InterviewAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联的会话
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;

    // 问题索引
    @Column(name = "question_index")
    private Integer questionIndex;

    // 问题内容
    @Column(columnDefinition = "TEXT")
    private String question;

    // 问题类别
    private String category;

    // 用户答案
    @Column(columnDefinition = "TEXT")
    private String userAnswer;

    // 得分 (0-100)
    private Integer score;

    // 反馈
    @Column(columnDefinition = "TEXT")
    private String feedback;

    // 参考答案
    @Column(columnDefinition = "TEXT")
    private String referenceAnswer;

    // 关键点 (JSON)
    @Column(columnDefinition = "TEXT")
    private String keyPointsJson;

    // 回答时间
    @Column(nullable = false)
    private LocalDateTime answeredAt;

    @PrePersist
    protected void onCreate() {
        answeredAt = LocalDateTime.now();
    }
}
