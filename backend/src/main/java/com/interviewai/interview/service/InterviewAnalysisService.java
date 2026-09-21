package com.interviewai.interview.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.dto.InterviewGrowthAnalysisResponse;
import com.interviewai.interview.dto.InterviewResultResponse;
import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.enums.InterviewAnalysisStatus;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.repository.InterviewAnalysisQueryRepository;
import com.interviewai.interview.repository.InterviewAnalysisQueryRepository.GrowthRow;
import com.interviewai.interview.repository.InterviewAnalysisQueryRepository.ResultRow;
import com.interviewai.interview.repository.InterviewAnalysisQueryRepository.SessionView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class InterviewAnalysisService {

    private static final int MINIMUM_SAMPLE_COUNT = 3;

    private final InterviewAnalysisQueryRepository repository;
    private final AdminAuthorizationService authorization;
    private final Clock catalogClock;


    public InterviewAnalysisService(
            InterviewAnalysisQueryRepository repository,
            AdminAuthorizationService authorization,
            Clock catalogClock
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.catalogClock = catalogClock;
    }


    public InterviewResultResponse getResult(String subject, long sessionId) {
        long userId = authorization.requireUser(subject).getId();

        SessionView session = repository.findOwnedSession(userId, sessionId).orElseThrow(this::sessionNotFound);

        if (!"COMPLETED".equals(session.status())) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "INTERVIEW_RESULT_NOT_READY",
                    "완료된 면접의 결과만 조회할 수 있습니다."
            );
        }

        List<ResultRow> rows = repository.findResultRows(sessionId);
        List<ResultRow> answers = rows.stream()
                .filter(row -> row.answerId() != null)
                .toList();
        List<ResultRow> completed = answers.stream()
                .filter(this::isCompleted)
                .toList();

        int pending = countStatus(answers, AnswerEvaluationStatus.PENDING);
        int processing = countStatus(answers, AnswerEvaluationStatus.PROCESSING);
        int failed = countStatus(answers, AnswerEvaluationStatus.FAILED);
        int notRequested = (int) answers.stream()
                .filter(row -> row.evaluationStatus() == null)
                .count();

        return new InterviewResultResponse(
                new InterviewResultResponse.Session(
                        session.id(),
                        session.companyName(),
                        session.jobPostingTitle(),
                        session.jobRole(),
                        toOffset(session.completedAt())
                ),
                analysisStatus(answers.size(), completed.size(), failed, notRequested),
                answers.size(),
                completed.size(),
                pending,
                processing,
                failed,
                notRequested,
                scoreSummary(completed),
                questionTypeSummaries(rows),
                rows.stream().map(this::questionResult).toList()
        );
    }


    public InterviewGrowthAnalysisResponse getGrowthAnalysis(
            String subject,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            String requestedJobRole,
            Long companyId,
            Long jobPostingId
    ) {
        long userId = authorization.requireUser(subject).getId();
        LocalDate to = requestedTo == null ? LocalDate.now(catalogClock) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(90) : requestedFrom;
        String jobRole = normalizeFilter(requestedJobRole);

        validateRange(from, to);
        validatePositive(companyId, "companyId");
        validatePositive(jobPostingId, "jobPostingId");

        LocalDateTime fromInclusive = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();

        List<GrowthRow> rows = repository.findGrowthRows(
                userId,
                fromInclusive,
                toExclusive,
                jobRole,
                companyId,
                jobPostingId
        );

        int excluded = repository.countExcludedCompletedSessions(
                userId,
                fromInclusive,
                toExclusive,
                jobRole,
                companyId,
                jobPostingId
        );

        List<SessionAggregate> sessions = aggregateSessions(rows);
        List<DimensionAggregate> dimensions = aggregateDimensions(rows);

        List<InterviewGrowthAnalysisResponse.Insight> strengths =
                dimensions.stream()
                        .filter(item -> item.sampleCount() >= MINIMUM_SAMPLE_COUNT)
                        .sorted(Comparator
                                .comparing(DimensionAggregate::score)
                                .reversed()
                                .thenComparing(DimensionAggregate::dimension)
                                .thenComparing(item -> item.questionType().name())
                        )
                        .limit(3)
                        .map(item -> insight(item, "평균 점수가 높은 역량입니다."))
                        .toList();

        List<DimensionAggregate> weakDimensions =
                dimensions.stream()
                        .filter(item -> item.sampleCount() >= MINIMUM_SAMPLE_COUNT)
                        .sorted(Comparator
                                .comparing(DimensionAggregate::score)
                                .thenComparing(DimensionAggregate::dimension)
                                .thenComparing(item -> item.questionType().name())
                        )
                        .limit(3)
                        .toList();

        List<InterviewGrowthAnalysisResponse.Insight> weaknesses =
                weakDimensions.stream()
                        .map(item -> insight(item, "평균 점수가 낮아 우선 개선이 필요합니다."))
                        .toList();

        return new InterviewGrowthAnalysisResponse(
                new InterviewGrowthAnalysisResponse.Period(from, to),
                new InterviewGrowthAnalysisResponse.Filters(jobRole, companyId, jobPostingId),
                growthSummary(sessions, rows.size(), excluded),
                growthQuestionTypeSummaries(rows),
                sessions.stream().map(this::trend).toList(),
                change(sessions),
                strengths,
                weaknesses,
                learningRoadmaps(weakDimensions),
                rows.size() < MINIMUM_SAMPLE_COUNT,
                MINIMUM_SAMPLE_COUNT
        );
    }


    private List<SessionAggregate> aggregateSessions(List<GrowthRow> rows) {
        Map<Long, List<GrowthRow>> grouped = new LinkedHashMap<>();

        rows.forEach(row ->
                grouped
                        .computeIfAbsent(
                                row.sessionId(),
                                ignored -> new ArrayList<>()
                        )
                        .add(row)
        );

        return grouped.values().stream()
                .map(sessionRows -> new SessionAggregate(
                        sessionRows.getFirst().sessionId(),
                        sessionRows.getFirst().completedAt(),
                        sessionRows.getFirst().companyName(),
                        sessionRows.getFirst().jobRole(),
                        sessionRows.size(),
                        average(sessionRows, Dimension.STAR),
                        average(sessionRows, Dimension.LOGIC),
                        average(sessionRows, Dimension.JOB_FIT)
                ))
                .toList();
    }


    private List<DimensionAggregate> aggregateDimensions(List<GrowthRow> rows) {
        List<DimensionAggregate> result = new ArrayList<>();

        for (InterviewQuestionType type : InterviewQuestionType.values()) {
            List<GrowthRow> typeRows = rows.stream()
                    .filter(row -> row.questionType() == type)
                    .toList();

            if (typeRows.isEmpty()) {
                continue;
            }

            for (Dimension dimension : Dimension.values()) {
                result.add(new DimensionAggregate(
                        dimension.name(),
                        type,
                        average(typeRows, dimension),
                        typeRows.size(),
                        typeRows.stream()
                                .map(GrowthRow::sessionId)
                                .distinct()
                                .toList()
                ));
            }
        }

        return result;
    }


    private List<InterviewResultResponse.QuestionTypeSummary> questionTypeSummaries(List<ResultRow> rows) {
        List<InterviewResultResponse.QuestionTypeSummary> result = new ArrayList<>();

        for (InterviewQuestionType type : InterviewQuestionType.values()) {
            List<ResultRow> answers = rows.stream()
                    .filter(row -> row.questionType() == type)
                    .filter(row -> row.answerId() != null)
                    .toList();

            List<ResultRow> completed = answers.stream()
                    .filter(this::isCompleted)
                    .toList();

            if (!answers.isEmpty()) {
                InterviewResultResponse.ScoreSummary score = scoreSummary(completed);

                result.add(
                        new InterviewResultResponse.QuestionTypeSummary(
                                type,
                                answers.size(),
                                completed.size(),
                                score.averageScore(),
                                score.starScore(),
                                score.logicScore(),
                                score.jobFitScore()
                        )
                );
            }
        }

        return List.copyOf(result);
    }


    private List<InterviewGrowthAnalysisResponse.QuestionTypeSummary> growthQuestionTypeSummaries(List<GrowthRow> rows) {
        List<InterviewGrowthAnalysisResponse.QuestionTypeSummary> result = new ArrayList<>();

        for (InterviewQuestionType type : InterviewQuestionType.values()) {
            List<GrowthRow> typeRows = rows.stream()
                    .filter(row -> row.questionType() == type)
                    .toList();

            if (!typeRows.isEmpty()) {
                BigDecimal star = average(typeRows, Dimension.STAR);
                BigDecimal logic = average(typeRows, Dimension.LOGIC);
                BigDecimal jobFit = average(typeRows, Dimension.JOB_FIT);

                result.add(
                        new InterviewGrowthAnalysisResponse.QuestionTypeSummary(
                                type,
                                typeRows.size(),
                                averageOf(star, logic, jobFit),
                                star,
                                logic,
                                jobFit
                        )
                );
            }
        }

        return List.copyOf(result);
    }


    private InterviewResultResponse.QuestionResult questionResult(ResultRow row) {
        InterviewResultResponse.Evaluation evaluation = null;

        if (row.evaluationStatus() != null) {
            evaluation = new InterviewResultResponse.Evaluation(
                    row.evaluationStatus(),
                    row.starScore(),
                    row.logicScore(),
                    row.jobFitScore(),
                    isCompleted(row)
                            ? averageOf(decimal(row.starScore()), decimal(row.logicScore()), decimal(row.jobFitScore()))
                            : null,
                    row.strengths(),
                    row.improvements(),
                    row.improvedAnswer(),
                    row.failureCode(),
                    toOffset(row.evaluationCompletedAt())
            );
        }

        return new InterviewResultResponse.QuestionResult(
                row.questionId(),
                row.parentQuestionId(),
                row.questionType(),
                row.sequence(),
                row.question(),
                row.answerId(),
                row.answer(),
                evaluation
        );
    }


    private InterviewResultResponse.ScoreSummary scoreSummary(List<ResultRow> rows) {
        if (rows.isEmpty()) {
            return new InterviewResultResponse.ScoreSummary(
                    0,
                    null,
                    null,
                    null,
                    null
            );
        }

        BigDecimal star = averageResult(rows, Dimension.STAR);
        BigDecimal logic = averageResult(rows, Dimension.LOGIC);
        BigDecimal jobFit = averageResult(rows, Dimension.JOB_FIT);

        return new InterviewResultResponse.ScoreSummary(
                rows.size(),
                averageOf(star, logic, jobFit),
                star,
                logic,
                jobFit
        );
    }


    private InterviewGrowthAnalysisResponse.Summary growthSummary(
            List<SessionAggregate> sessions,
            int evaluatedAnswerCount,
            int excludedSessionCount
    ) {
        if (sessions.isEmpty()) {
            return new InterviewGrowthAnalysisResponse.Summary(
                    0,
                    0,
                    excludedSessionCount,
                    null,
                    null,
                    null,
                    null
            );
        }

        BigDecimal star = weightedAverage(sessions, SessionAggregate::starScore);
        BigDecimal logic = weightedAverage(sessions, SessionAggregate::logicScore);
        BigDecimal jobFit = weightedAverage(sessions, SessionAggregate::jobFitScore);

        return new InterviewGrowthAnalysisResponse.Summary(
                sessions.size(),
                evaluatedAnswerCount,
                excludedSessionCount,
                averageOf(star, logic, jobFit),
                star,
                logic,
                jobFit
        );
    }


    private InterviewGrowthAnalysisResponse.Trend trend(SessionAggregate session) {
        return new InterviewGrowthAnalysisResponse.Trend(
                session.sessionId(),
                toOffset(session.completedAt()),
                session.companyName(),
                session.jobRole(),
                session.sampleCount(),
                averageOf(session.starScore(), session.logicScore(), session.jobFitScore()),
                session.starScore(),
                session.logicScore(),
                session.jobFitScore()
        );
    }


    private InterviewGrowthAnalysisResponse.Change change(List<SessionAggregate> sessions) {
        if (sessions.size() < 2) {
            return new InterviewGrowthAnalysisResponse.Change(
                    "RECENT_VS_PREVIOUS",
                    sessions.size(),
                    0,
                    null,
                    null,
                    null,
                    null
            );
        }

        int recentCount = Math.min(3, sessions.size() - 1);
        int recentStart = sessions.size() - recentCount;
        int previousStart = Math.max(0, recentStart - 3);

        List<SessionAggregate> recent = sessions.subList(recentStart, sessions.size());
        List<SessionAggregate> previous = sessions.subList(previousStart, recentStart);

        BigDecimal recentStar = sessionAverage(recent, Dimension.STAR);
        BigDecimal recentLogic = sessionAverage(recent, Dimension.LOGIC);
        BigDecimal recentJobFit = sessionAverage(recent, Dimension.JOB_FIT);

        BigDecimal previousStar = sessionAverage(previous, Dimension.STAR);
        BigDecimal previousLogic = sessionAverage(previous, Dimension.LOGIC);
        BigDecimal previousJobFit = sessionAverage(previous, Dimension.JOB_FIT);

        return new InterviewGrowthAnalysisResponse.Change(
                "RECENT_VS_PREVIOUS",
                recent.size(),
                previous.size(),
                subtract(
                        averageOf(recentStar, recentLogic, recentJobFit),
                        averageOf(previousStar, previousLogic, previousJobFit)
                ),
                subtract(recentStar, previousStar),
                subtract(recentLogic, previousLogic),
                subtract(recentJobFit, previousJobFit)
        );
    }


    private InterviewGrowthAnalysisResponse.Insight insight(DimensionAggregate aggregate, String reason) {
        return new InterviewGrowthAnalysisResponse.Insight(
                aggregate.dimension(),
                aggregate.questionType(),
                aggregate.score(),
                aggregate.sampleCount(),
                reason
        );
    }


    private List<InterviewGrowthAnalysisResponse.LearningRoadmap> learningRoadmaps(List<DimensionAggregate> weaknesses) {
        List<InterviewGrowthAnalysisResponse.LearningRoadmap> result = new ArrayList<>();
        int priority = 1;

        for (DimensionAggregate weakness : weaknesses) {
            if (weakness.score().compareTo(BigDecimal.valueOf(80)) >= 0) {
                continue;
            }

            Dimension dimension = Dimension.valueOf(weakness.dimension());

            result.add(
                    new InterviewGrowthAnalysisResponse.LearningRoadmap(
                            priority++,
                            dimension.name(),
                            weakness.questionType(),
                            weakness.score(),
                            targetScore(weakness.score()),
                            roadmapTitle(dimension),
                            roadmapActions(dimension),
                            new InterviewGrowthAnalysisResponse.Evidence(weakness.sampleCount(), weakness.sessionIds())
                    )
            );
        }

        return List.copyOf(result);
    }


    private String roadmapTitle(Dimension dimension) {
        return switch (dimension) {
            case STAR -> "STAR 구조로 경험 답변 정리";
            case LOGIC -> "답변의 핵심 주장과 근거 강화";
            case JOB_FIT -> "경험과 지원 직무의 연결 강화";
        };
    }


    private List<String> roadmapActions(Dimension dimension) {
        return switch (dimension) {
            case STAR -> List.of(
                    "상황·과제·행동·결과를 각각 한 문장으로 작성합니다.",
                    "행동에서 본인의 판단과 기여를 구체화합니다.",
                    "결과에 수치 또는 검증 가능한 변화를 포함합니다."
            );
            case LOGIC -> List.of(
                    "답변 첫 문장에 결론을 먼저 제시합니다.",
                    "결론을 뒷받침하는 근거를 두 개 이하로 정리합니다.",
                    "질문과 직접 관련 없는 설명을 제거합니다."
            );
            case JOB_FIT -> List.of(
                    "경험에서 사용한 역량을 지원 직무 요구사항과 연결합니다.",
                    "지원 공고의 업무를 기준으로 기여 방식을 설명합니다.",
                    "입사 후 적용할 수 있는 구체적인 행동을 제시합니다."
            );
        };
    }


    private BigDecimal targetScore(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(60)) < 0) {
            return BigDecimal.valueOf(70).setScale(1, RoundingMode.UNNECESSARY);
        }

        if (score.compareTo(BigDecimal.valueOf(70)) < 0) {
            return BigDecimal.valueOf(75).setScale(1, RoundingMode.UNNECESSARY);
        }

        return BigDecimal.valueOf(85).setScale(1, RoundingMode.UNNECESSARY);
    }


    private InterviewAnalysisStatus analysisStatus(
            int answerCount,
            int completedCount,
            int failedCount,
            int notRequestedCount
    ) {
        if (completedCount == 0) {
            return InterviewAnalysisStatus.PENDING;
        }

        if (completedCount == answerCount && failedCount == 0 && notRequestedCount == 0) {
            return InterviewAnalysisStatus.COMPLETED;
        }

        return InterviewAnalysisStatus.PARTIAL;
    }


    private int countStatus(List<ResultRow> rows, AnswerEvaluationStatus status) {
        return (int) rows.stream()
                .filter(row -> row.evaluationStatus() == status)
                .count();
    }


    private boolean isCompleted(ResultRow row) {
        return row.evaluationStatus() == AnswerEvaluationStatus.COMPLETED;
    }


    private BigDecimal averageResult(List<ResultRow> rows, Dimension dimension) {
        double average = rows.stream()
                .mapToDouble(row -> switch (dimension) {
                    case STAR -> row.starScore();
                    case LOGIC -> row.logicScore();
                    case JOB_FIT -> row.jobFitScore();
                })
                .average()
                .orElseThrow();

        return rounded(average);
    }


    private BigDecimal average(List<GrowthRow> rows, Dimension dimension) {
        double average = rows.stream()
                .mapToInt(row -> switch (dimension) {
                    case STAR -> row.starScore();
                    case LOGIC -> row.logicScore();
                    case JOB_FIT -> row.jobFitScore();
                })
                .average()
                .orElseThrow();

        return rounded(average);
    }


    private BigDecimal weightedAverage(
            List<SessionAggregate> sessions,
            Function<SessionAggregate, BigDecimal> extractor
    ) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;

        for (SessionAggregate session : sessions) {
            sum = sum.add(
                    extractor
                            .apply(session)
                            .multiply(BigDecimal.valueOf(session.sampleCount()))
            );

            count += session.sampleCount();
        }

        return sum.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }


    private BigDecimal sessionAverage(List<SessionAggregate> sessions, Dimension dimension) {
        double average = sessions.stream()
                .mapToDouble(session -> switch (dimension) {
                    case STAR -> session.starScore().doubleValue();
                    case LOGIC -> session.logicScore().doubleValue();
                    case JOB_FIT -> session.jobFitScore().doubleValue();
                })
                .average()
                .orElseThrow();

        return rounded(average);
    }


    private BigDecimal averageOf(BigDecimal first, BigDecimal second, BigDecimal third) {
        return first.add(second)
                .add(third)
                .divide(BigDecimal.valueOf(3), 1, RoundingMode.HALF_UP);
    }


    private BigDecimal subtract(BigDecimal first, BigDecimal second) {
        return first.subtract(second).setScale(1, RoundingMode.HALF_UP);
    }


    private BigDecimal decimal(Integer value) {
        return BigDecimal.valueOf(Objects.requireNonNull(value));
    }


    private BigDecimal rounded(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }


    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }


    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > 100) {
            throw CatalogException.invalid("jobRole", "직무는 100자 이하여야 합니다.");
        }

        return normalized;
    }


    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw CatalogException.invalid("from", "시작일은 종료일보다 늦을 수 없습니다.");
        }

        if (from.plusDays(365).isBefore(to)) {
            throw CatalogException.invalid("from", "분석 기간은 최대 365일입니다.");
        }
    }


    private void validatePositive(Long value, String field) {
        if (value != null && value <= 0) {
            throw CatalogException.invalid(field, field + "는 1이상이어야 합니다.");
        }
    }


    private CatalogException sessionNotFound() {
        return new CatalogException(
                HttpStatus.NOT_FOUND,
                "INTERVIEW_SESSION_NOT_FOUND",
                "면접 세션을 찾을 수 없습니다."
        );
    }


    private enum Dimension {
        STAR,
        LOGIC,
        JOB_FIT
    }


    private record SessionAggregate(
            long sessionId,
            LocalDateTime completedAt,
            String companyName,
            String jobRole,
            int sampleCount,
            BigDecimal starScore,
            BigDecimal logicScore,
            BigDecimal jobFitScore
    ) {

    }


    private record DimensionAggregate(
            String dimension,
            InterviewQuestionType questionType,
            BigDecimal score,
            int sampleCount,
            List<Long> sessionIds
    ) {

    }
}
