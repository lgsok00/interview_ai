package com.interviewai.interview.generation;

import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.interview.service.InterviewRagSearchService;
import com.interviewai.rag.search.RagSearchResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InterviewGenerationInputService {

    private final InterviewSessionRepository sessions;
    private final ObjectProvider<InterviewRagSearchService> ragSearch;


    public InterviewGenerationInputService(
            InterviewSessionRepository sessions,
            ObjectProvider<InterviewRagSearchService> ragSearch
    ) {
        this.sessions = sessions;
        this.ragSearch = ragSearch;
    }


    public Input load(long sessionId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new InterviewGenerationPolicy.GenerationException("SESSION_NOT_FOUND", false));

        InterviewRagSearchService searchService = ragSearch.getIfAvailable();

        if (searchService == null) {
            throw new InterviewGenerationPolicy.GenerationException("RAG_NOT_CONFIGURED", false);
        }

        String query = session.getJobRole().strip();

        if (query.length() > 100) {
            query = query.substring(0, 100);
        }

        List<RagSearchResult> context = searchService.search(session, query);

        return new Input(
                session.getCompanyName(),
                session.getJobPostingTitle(),
                session.getJobRole(),
                session.getJobPostingContent(),
                session.getCoverLetterContent(),
                session.getResumeContent(),
                List.copyOf(context)
        );
    }


    public record Input(
            String companyName,
            String jobPostingTitle,
            String jobRole,
            String jobPostingContent,
            String coverLetterContent,
            String resumeContent,
            List<RagSearchResult> context
    ) {

    }
}
