package com.interviewai.interview.service;

import com.interviewai.ai.usage.AiUsageLifecycleService;
import com.interviewai.global.error.CatalogException;
import com.interviewai.interview.dto.InterviewFollowUpResponse;
import com.interviewai.interview.generation.InterviewFollowUpGenerator;
import com.interviewai.interview.generation.InterviewGenerationDeadline;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class InterviewFollowUpService {

    private final InterviewAnswerService answerService;
    private final InterviewFollowUpGenerator generator;
    private final InterviewGenerationDeadline deadline;
    private final AiUsageLifecycleService lifecycle;


    public InterviewFollowUpService(
            InterviewAnswerService answerService,
            InterviewFollowUpGenerator generator,
            InterviewGenerationDeadline deadline,
            AiUsageLifecycleService lifecycle
    ) {
        this.answerService = answerService;
        this.generator = generator;
        this.deadline = deadline;
        this.lifecycle = lifecycle;
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InterviewFollowUpResponse generate(String subject, long sessionId, long questionId) {
        InterviewAnswerService.Preparation preparation = answerService.prepareFollowUp(subject, sessionId, questionId);

        if (preparation.existing() != null) {
            return preparation.existing();
        }

        InterviewFollowUpGenerator.Generated generated;

        try {
            generated = deadline.call(
                    () -> generator.generate(preparation.input()),
                    Duration.ofSeconds(45),
                    "FOLLOW_UP_TIMEOUT"
            );

        } catch (InterruptedException exception) {
            CatalogException failure = unavailable();

            try {
                cancelAfterFailure(preparation, failure);

            } finally {
                Thread.currentThread().interrupt();
            }

            throw failure;

        } catch (RuntimeException exception) {
            CatalogException failure = unavailable();
            cancelAfterFailure(preparation, failure);

            throw failure;

        } catch (Error failure) {
            cancelAfterFailure(preparation, failure);
            throw failure;
        }

        try {
            return answerService.saveFollowUp(subject, sessionId, questionId, preparation.reservationId(), generated);

        } catch (RuntimeException | Error failure) {
            cancelAfterFailure(preparation, failure);
            throw failure;
        }
    }


    private void cancelAfterFailure(InterviewAnswerService.Preparation preparation, Throwable failure) {
        try {
            lifecycle.cancelFollowUp(preparation.reservationId());

        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }


    private CatalogException unavailable() {
        return new CatalogException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INTERVIEW_FOLLOW_UP_UNAVAILABLE",
                "꼬리 질문을 생성하지 못했습니다. 저장된 답변으로 다시 시도해 주세요."
        );
    }
}
