package com.interviewai.coverletter.controller;

import com.interviewai.coverletter.dto.CoverLetterResponse;
import com.interviewai.coverletter.dto.CoverLetterSummaryResponse;
import com.interviewai.coverletter.dto.CoverLetterVersionResponse;
import com.interviewai.coverletter.dto.CoverLetterVersionSummaryResponse;
import com.interviewai.coverletter.dto.CreateCoverLetterRequest;
import com.interviewai.coverletter.dto.UpdateCoverLetterRequest;
import com.interviewai.coverletter.service.CoverLetterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cover-letters")
public class CoverLetterController {

    private final CoverLetterService coverLetterService;


    public CoverLetterController(CoverLetterService coverLetterService) {
        this.coverLetterService = coverLetterService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CoverLetterResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCoverLetterRequest request
    ) {
        return coverLetterService.create(jwt.getSubject(), request);
    }


    @GetMapping
    public List<CoverLetterSummaryResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return coverLetterService.getAll(jwt.getSubject());
    }


    @GetMapping("/representative")
    public CoverLetterResponse getRepresentative(@AuthenticationPrincipal Jwt jwt) {
        return coverLetterService.getRepresentative(jwt.getSubject());
    }


    @DeleteMapping("/representative")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearRepresentative(@AuthenticationPrincipal Jwt jwt) {
        coverLetterService.clearRepresentative(jwt.getSubject());
    }


    @GetMapping("/{coverLetterId}")
    public CoverLetterResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long coverLetterId) {
        return coverLetterService.get(jwt.getSubject(), coverLetterId);
    }


    @PutMapping("/{coverLetterId}")
    public CoverLetterResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId,
            @Valid @RequestBody UpdateCoverLetterRequest request
    ) {
        return coverLetterService.update(jwt.getSubject(), coverLetterId, request);
    }


    @DeleteMapping("/{coverLetterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long coverLetterId) {
        coverLetterService.delete(jwt.getSubject(), coverLetterId);
    }


    @GetMapping("/{coverLetterId}/versions")
    public List<CoverLetterVersionSummaryResponse> getVersions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId
    ) {
        return coverLetterService.getVersions(jwt.getSubject(), coverLetterId);
    }


    @GetMapping("/{coverLetterId}/versions/{versionNumber}")
    public CoverLetterVersionResponse getVersion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId,
            @PathVariable Integer versionNumber
    ) {
        return coverLetterService.getVersion(jwt.getSubject(), coverLetterId, versionNumber);
    }


    @PostMapping("/{coverLetterId}/versions/{versionNumber}/restore")
    public CoverLetterResponse restore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long coverLetterId,
            @PathVariable Integer versionNumber
    ) {
        return coverLetterService.restore(jwt.getSubject(), coverLetterId, versionNumber);
    }


    @PutMapping("/{coverLetterId}/representative")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRepresentative(@AuthenticationPrincipal Jwt jwt, @PathVariable Long coverLetterId) {
        coverLetterService.setRepresentative(jwt.getSubject(), coverLetterId);
    }
}
