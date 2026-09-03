package com.interviewai.resume.controller;

import com.interviewai.resume.dto.ResumeResponse;
import com.interviewai.resume.dto.ResumeSummaryResponse;
import com.interviewai.resume.dto.ResumeUploadRequest;
import com.interviewai.resume.dto.UpdateResumeTitleRequest;
import com.interviewai.resume.file.ResumeDownload;
import com.interviewai.resume.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;


    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("metadata") ResumeUploadRequest request,
            @RequestPart("file") MultipartFile file
    ) {
        return resumeService.create(jwt.getSubject(), request, file);
    }


    @GetMapping
    public List<ResumeSummaryResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return resumeService.getAll(jwt.getSubject());
    }


    @GetMapping("/representative")
    public ResumeResponse getRepresentative(@AuthenticationPrincipal Jwt jwt) {
        return resumeService.getRepresentative(jwt.getSubject());
    }


    @DeleteMapping("/representative")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearRepresentative(@AuthenticationPrincipal Jwt jwt) {
        resumeService.clearRepresentative(jwt.getSubject());
    }


    @GetMapping("/{resumeId}")
    public ResumeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long resumeId) {
        return resumeService.get(jwt.getSubject(), resumeId);
    }


    @GetMapping("/{resumeId}/file")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long resumeId) {
        ResumeDownload download = resumeService.download(jwt.getSubject(), resumeId);

        ContentDisposition disposition = ContentDisposition
                .attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contents().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.contents());
    }


    @PutMapping("/{resumeId}")
    public ResumeResponse updateTitle(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long resumeId,
            @Valid @RequestBody UpdateResumeTitleRequest request
    ) {
        return resumeService.updateTitle(jwt.getSubject(), resumeId, request);
    }


    @PutMapping(path = "/{resumeId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeResponse replaceFile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long resumeId,
            @RequestPart("file") MultipartFile file
    ) {
        return resumeService.replaceFile(jwt.getSubject(), resumeId, file);
    }


    @DeleteMapping("/{resumeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long resumeId) {
        resumeService.delete(jwt.getSubject(), resumeId);
    }


    @PutMapping("/{resumeId}/representative")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRepresentative(@AuthenticationPrincipal Jwt jwt, @PathVariable Long resumeId) {
        resumeService.setRepresentative(jwt.getSubject(), resumeId);
    }
}
