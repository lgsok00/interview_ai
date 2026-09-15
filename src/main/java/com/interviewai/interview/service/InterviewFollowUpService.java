package com.interviewai.interview.service;

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


    public InterviewFollowUpService(
            InterviewAnswerService answerService,
            InterviewFollowUpGenerator generator,
            InterviewGenerationDeadline deadline
    ) {
        this.answerService = answerService;
        this.generator = generator;
        this.deadline = deadline;
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
            Thread.currentThread().interrupt();
            throw unavailable();

        } catch (RuntimeException exception) {
            throw unavailable();
        }

        return answerService.saveFollowUp(subject, sessionId, questionId, generated);
    }


    private CatalogException unavailable() {
        return new CatalogException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INTERVIEW_FOLLOW_UP_UNAVAILABLE",
                "꼬리 질문을 생성하지 못했습니다. 저장된 답변으로 다시 시도해 주세요."
        );
    }
}
