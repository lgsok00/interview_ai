package com.interviewai.interview.service;

import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.search.RagSearchResult;
import com.interviewai.rag.search.RagSearchScope;
import com.interviewai.rag.service.RagSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(prefix = "rag.search", name = "enabled", havingValue = "true")
public class InterviewRagSearchService {

    private final RagSearchService ragSearchService;


    public InterviewRagSearchService(RagSearchService ragSearchService) {
        this.ragSearchService = ragSearchService;
    }


    public List<RagSearchResult> search(InterviewSession session, String query) {
        Objects.requireNonNull(session, "session은 필수입니다.");

        Set<RagSourceKey> allowedSourceKeys = new LinkedHashSet<>();
        allowedSourceKeys.add(new RagSourceKey(RagSourceType.COMPANY, session.getCompanyId()));
        allowedSourceKeys.add(new RagSourceKey(RagSourceType.JOB_POSTING, session.getJobPostingId()));
        addOptional(allowedSourceKeys, RagSourceType.COVER_LETTER, session.getCoverLetterId());
        addOptional(allowedSourceKeys, RagSourceType.RESUME, session.getResumeId());

        return ragSearchService.searchWithin(new RagSearchScope(session.getUser(), allowedSourceKeys), query);
    }


    private void addOptional(Set<RagSourceKey> sourceKeys, RagSourceType sourceType, Long sourceId) {
        if (sourceId != null) {
            sourceKeys.add(new RagSourceKey(sourceType, sourceId));
        }
    }
}
