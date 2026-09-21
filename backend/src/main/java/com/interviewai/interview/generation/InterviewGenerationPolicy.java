package com.interviewai.interview.generation;

import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InterviewGenerationPolicy {

    public static final String VERSION = "interview-v1";
    public static final int MAX_ATTEMPTS = 3;
    public static final int MAX_MANUAL_RETRIES = 2;
    public static final int LEASE_SECONDS = 120;


    private InterviewGenerationPolicy() {

    }


    public static List<Question> validate(List<Question> questions) {
        if (questions == null || questions.size() != 5) {
            throw invalidOutput();
        }

        Set<String> contents = new HashSet<>();
        int technical = 0;
        int behavioral = 0;

        for (Question question : questions) {
            if (question == null || question.type() == null || question.content() == null) {
                throw invalidOutput();
            }

            String content = question.content().strip();
            int length = content.codePointCount(0, content.length());

            if (length < 20 || length > 1000) {
                throw invalidOutput();
            }

            String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC)
                    .replaceAll("(?U)\\s+", " ")
                    .toLowerCase(Locale.ROOT);

            if (!contents.add(normalized)) {
                throw invalidOutput();
            }

            switch (question.type()) {
                case TECHNICAL -> technical++;
                case BEHAVIORAL -> behavioral++;
                default -> throw invalidOutput();
            }
        }

        if (technical != 3 || behavioral != 2) {
            throw invalidOutput();
        }

        return questions.stream()
                .map(question -> new Question(question.type(), question.content().strip()))
                .toList();
    }


    public static Batch fallback(String reason) {
        return new Batch(
                List.of(
                        new Question(
                                InterviewQuestionType.TECHNICAL,
                                "지원 직무와 관련된 문제를 해결할 때 원인을 어떤 순서로 분석하고 검증하는지 설명해 주세요."
                        ),
                        new Question(
                                InterviewQuestionType.TECHNICAL,
                                "기술적인 대안을 비교하여 하나를 선택했던 경험과 그 판단 기준을 설명해 주세요."
                        ),
                        new Question(
                                InterviewQuestionType.TECHNICAL,
                                "작업 결과의 품질을 확인하기 위해 어떤 테스트와 검증 방법을 사용하는지 설명해 주세요."
                        ),
                        new Question(
                                InterviewQuestionType.BEHAVIORAL,
                                "팀원과 의견이 달랐던 상황에서 어떻게 대화하고 합의에 도달했는지 설명해 주세요."
                        ),
                        new Question(
                                InterviewQuestionType.BEHAVIORAL,
                                "예상하지 못한 어려움을 겪었던 경험과 그 과정에서 배운 점을 설명해 주세요."
                        )
                ),
                QuestionGenerationSource.FALLBACK,
                null,
                reason
        );
    }


    public static GenerationException invalidOutput() {
        return new GenerationException("AI_INVALID_OUTPUT", true);
    }


    public enum Mode {
        AI,
        FALLBACK_ONLY
    }


    public record Question(
            InterviewQuestionType type,
            String content
    ) {

    }


    public record Batch(
            List<Question> questions,
            QuestionGenerationSource source,
            String contextSnapshot,
            String fallbackReason
    ) {

        public Batch {
            questions = validate(questions);

            if (source == null) {
                throw new IllegalArgumentException("생성 출처는 필수입니다.");
            }

            if (source == QuestionGenerationSource.AI) {
                if (contextSnapshot == null || contextSnapshot.isBlank()) {
                    throw new IllegalArgumentException("AI 입력 스냅샷은 필수입니다.");
                }

                if (fallbackReason != null) {
                    throw new IllegalArgumentException("AI 결과에는 fallback 사유를 저장하지 않습니다.");
                }

            } else {
                if (contextSnapshot != null) {
                    throw new IllegalArgumentException("fallback은 RAG context를 저장하지 않습니다.");
                }

                if (fallbackReason == null || fallbackReason.isBlank()) {
                    throw new IllegalArgumentException("fallback 사유는 필수입니다.");
                }
            }
        }
    }


    public static final class GenerationException extends RuntimeException {
        private final String code;
        private final boolean retryable;


        public GenerationException(String code, boolean retryable) {
            super(code);
            this.code = code;
            this.retryable = retryable;
        }


        public String code() {
            return code;
        }


        public boolean retryable() {
            return retryable;
        }
    }
}
