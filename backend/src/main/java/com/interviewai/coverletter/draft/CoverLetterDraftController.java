package com.interviewai.coverletter.draft;

import com.interviewai.coverletter.dto.CoverLetterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/cover-letters/{coverLetterId}/drafts")
public class CoverLetterDraftController {

    private final CoverLetterDraftService draftService;


    public CoverLetterDraftController(CoverLetterDraftService draftService) {
        this.draftService = draftService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CoverLetterDraftResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId,
            @Valid @RequestBody CreateCoverLetterDraftRequest request
    ) {
        return draftService.create(jwt.getSubject(), coverLetterId, request);
    }


    @GetMapping
    public List<CoverLetterDraftSummaryResponse> getAll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId
    ) {
        return draftService.getAll(jwt.getSubject(), coverLetterId);
    }


    @GetMapping("/{draftId}")
    public CoverLetterDraftResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId,
            @PathVariable Long draftId
    ) {
        return draftService.get(jwt.getSubject(), coverLetterId, draftId);
    }


    @PostMapping("/{draftId}/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CoverLetterDraftResponse regenerate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId,
            @PathVariable Long draftId
    ) {
        return draftService.regenerate(jwt.getSubject(), coverLetterId, draftId);
    }


    @PostMapping("/{draftId}/apply")
    public CoverLetterResponse apply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId,
            @PathVariable Long draftId,
            @Valid @RequestBody ApplyCoverLetterDraftRequest request
    ) {
        return draftService.apply(jwt.getSubject(), coverLetterId, draftId, request);
    }
}
