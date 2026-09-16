package com.interviewai.interview.repository;

import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.enums.InterviewQuestionType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class InterviewAnalysisQueryRepository {

    private final JdbcClient jdbcClient;


    public InterviewAnalysisQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }


    public Optional<SessionView> findOwnedSession(long userId, long sessionId) {
        return jdbcClient.sql("""
                        SELECT id,
                               status,
                               company_id,
                               company_name,
                               job_posting_id,
                               job_posting_title,
                               job_role,
                               completed_at
                        FROM interview_sessions
                        WHERE id = :sessionId
                          AND user_id = :userId
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .query((resultSet, rowNumber) -> new SessionView(
                        resultSet.getLong("id"),
                        resultSet.getString("status"),
                        resultSet.getLong("company_id"),
                        resultSet.getString("company_name"),
                        resultSet.getLong("job_posting_id"),
                        resultSet.getString("job_posting_title"),
                        resultSet.getString("job_role"),
                        resultSet.getObject("completed_at", LocalDateTime.class)
                ))
                .optional();
    }


    public List<ResultRow> findResultRows(long sessionId) {
        return jdbcClient.sql("""
                        SELECT question.id AS question_id,
                               question.parent_question_id,
                               question.question_type,
                               question.sequence_number,
                               question.content AS question_content,
                               answer.id AS answer_id,
                               answer.content AS answer_content,
                               evaluation.status AS evaluation_status,
                               evaluation.star_score,
                               evaluation.logic_score,
                               evaluation.job_fit_score,
                               evaluation.strengths,
                               evaluation.improvements,
                               evaluation.improved_answer,
                               evaluation.failure_code,
                               evaluation.completed_at AS evaluation_completed_at
                        FROM interview_questions question
                        LEFT JOIN interview_answers answer
                               ON answer.question_id = question.id
                        LEFT JOIN interview_answer_evaluations evaluation
                               ON evaluation.answer_id = answer.id
                        WHERE question.session_id = :sessionId
                        ORDER BY question.sequence_number, question.id
                        """)
                .param("sessionId", sessionId)
                .query((resultSet, rowNumber) -> {
                    String evaluationStatus = resultSet.getString("evaluation_status");

                    return new ResultRow(
                            resultSet.getLong("question_id"),
                            resultSet.getObject("parent_question_id", Long.class),
                            InterviewQuestionType.valueOf(resultSet.getString("question_type")),
                            resultSet.getInt("sequence_number"),
                            resultSet.getString("question_content"),
                            resultSet.getObject("answer_id", Long.class),
                            resultSet.getString("answer_content"),
                            evaluationStatus == null ? null : AnswerEvaluationStatus.valueOf(evaluationStatus),
                            resultSet.getObject("star_score", Integer.class),
                            resultSet.getObject("logic_score", Integer.class),
                            resultSet.getObject("job_fit_score", Integer.class),
                            resultSet.getString("strengths"),
                            resultSet.getString("improvements"),
                            resultSet.getString("improved_answer"),
                            resultSet.getString("failure_code"),
                            resultSet.getObject("evaluation_completed_at", LocalDateTime.class)
                    );
                })
                .list();
    }


    public List<GrowthRow> findGrowthRows(
            long userId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            String jobRole,
            Long companyId,
            Long jobPostingId
    ) {
        return jdbcClient.sql("""
                        SELECT session.id AS session_id,
                               session.completed_at,
                               session.company_name,
                               session.job_role,
                               question.question_type,
                               evaluation.star_score,
                               evaluation.logic_score,
                               evaluation.job_fit_score
                        FROM interview_sessions session
                        JOIN interview_questions question
                          ON question.session_id = session.id
                        JOIN interview_answers answer
                          ON answer.question_id = question.id
                        JOIN interview_answer_evaluations evaluation
                          ON evaluation.answer_id = answer.id
                        WHERE session.user_id = :userId
                          AND session.status = 'COMPLETED'
                          AND session.completed_at >= :fromInclusive
                          AND session.completed_at < :toExclusive
                          AND evaluation.status = 'COMPLETED'
                          AND (:jobRole IS NULL OR session.job_role = :jobRole)
                          AND (:companyId IS NULL OR session.company_id = :companyId)
                          AND (:jobPostingId IS NULL OR session.job_posting_id = :jobPostingId)
                        ORDER BY session.completed_at,
                                 session.id,
                                 question.sequence_number
                        """)
                .param("userId", userId)
                .param("fromInclusive", fromInclusive)
                .param("toExclusive", toExclusive)
                .param("jobRole", jobRole)
                .param("companyId", companyId)
                .param("jobPostingId", jobPostingId)
                .query((resultSet, rowNumber) -> new GrowthRow(
                        resultSet.getLong("session_id"),
                        resultSet.getObject("completed_at", LocalDateTime.class),
                        resultSet.getString("company_name"),
                        resultSet.getString("job_role"),
                        InterviewQuestionType.valueOf(resultSet.getString("question_type")),
                        resultSet.getInt("star_score"),
                        resultSet.getInt("logic_score"),
                        resultSet.getInt("job_fit_score")
                ))
                .list();
    }


    public int countExcludedCompletedSessions(
            long userId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            String jobRole,
            Long companyId,
            Long jobPostingId
    ) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM interview_sessions session
                        WHERE session.user_id = :userId
                          AND session.status = 'COMPLETED'
                          AND session.completed_at >= :fromInclusive
                          AND session.completed_at < :toExclusive
                          AND (:jobRole IS NULL OR session.job_role = :jobRole)
                          AND (:companyId IS NULL OR session.company_id = :companyId)
                          AND (:jobPostingId IS NULL OR session.job_posting_id = :jobPostingId)
                          AND NOT EXISTS(
                              SELECT 1
                              FROM interview_questions question
                              JOIN interview_answers answer
                                ON answer.question_id = question.id
                              JOIN interview_answer_evaluations evaluation
                                ON evaluation.answer_id = answer.id
                              WHERE question.session_id = session.id
                                AND evaluation.status = 'COMPLETED'
                          )
                        """)
                .param("userId", userId)
                .param("fromInclusive", fromInclusive)
                .param("toExclusive", toExclusive)
                .param("jobRole", jobRole)
                .param("companyId", companyId)
                .param("jobPostingId", jobPostingId)
                .query(Integer.class)
                .single();
    }


    public record SessionView(
            long id,
            String status,
            long companyId,
            String companyName,
            long jobPostingId,
            String jobPostingTitle,
            String jobRole,
            LocalDateTime completedAt
    ) {

    }


    public record ResultRow(
            long questionId,
            Long parentQuestionId,
            InterviewQuestionType questionType,
            int sequence,
            String question,
            Long answerId,
            String answer,
            AnswerEvaluationStatus evaluationStatus,
            Integer starScore,
            Integer logicScore,
            Integer jobFitScore,
            String strengths,
            String improvements,
            String improvedAnswer,
            String failureCode,
            LocalDateTime evaluationCompletedAt
    ) {

    }


    public record GrowthRow(
            long sessionId,
            LocalDateTime completedAt,
            String companyName,
            String jobRole,
            InterviewQuestionType questionType,
            int starScore,
            int logicScore,
            int jobFitScore
    ) {

    }
}
