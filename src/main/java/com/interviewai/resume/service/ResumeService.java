package com.interviewai.resume.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.resume.dto.ResumeResponse;
import com.interviewai.resume.dto.ResumeSummaryResponse;
import com.interviewai.resume.dto.ResumeUploadRequest;
import com.interviewai.resume.dto.UpdateResumeTitleRequest;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.entity.ResumeRepresentative;
import com.interviewai.resume.exception.RepresentativeResumeNotFoundException;
import com.interviewai.resume.exception.ResumeNotFoundException;
import com.interviewai.resume.file.ResumeDownload;
import com.interviewai.resume.file.ResumePdfAnalysis;
import com.interviewai.resume.file.ResumePdfProcessor;
import com.interviewai.resume.file.ResumeUploadFileValidator;
import com.interviewai.resume.file.ValidatedResumeFile;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.repository.ResumeRepresentativeRepository;
import com.interviewai.resume.storage.ResumeFileStorage;
import com.interviewai.resume.storage.ResumeFileTransactionCleanup;
import com.interviewai.user.entity.User;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeRepresentativeRepository representativeRepository;
    private final UserRepository userRepository;
    private final ResumeUploadFileValidator fileValidator;
    private final ResumePdfProcessor pdfProcessor;
    private final ResumeFileStorage fileStorage;
    private final ResumeFileTransactionCleanup fileCleanup;


    public ResumeService(
            ResumeRepository resumeRepository,
            ResumeRepresentativeRepository representativeRepository,
            UserRepository userRepository,
            ResumeUploadFileValidator fileValidator,
            ResumePdfProcessor pdfProcessor,
            ResumeFileStorage fileStorage,
            ResumeFileTransactionCleanup fileCleanup
    ) {
        this.resumeRepository = resumeRepository;
        this.representativeRepository = representativeRepository;
        this.userRepository = userRepository;
        this.fileValidator = fileValidator;
        this.pdfProcessor = pdfProcessor;
        this.fileStorage = fileStorage;
        this.fileCleanup = fileCleanup;
    }


    @Transactional
    public ResumeResponse create(String subject, ResumeUploadRequest request, MultipartFile file) {
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        ValidatedResumeFile validatedFile = fileValidator.validate(file);
        ResumePdfAnalysis analysis = pdfProcessor.analyze(validatedFile.contents());

        String storageKey = fileStorage.store(userId, validatedFile.contents());

        fileCleanup.deleteAfterRollback(storageKey);

        Resume resume = Resume.create(
                user,
                request.title(),
                validatedFile.originalFilename(),
                storageKey,
                validatedFile.contentType(),
                validatedFile.size(),
                analysis.sha256()
        );

        applyExtractionResult(resume, analysis);

        Resume savedResume = resumeRepository.save(resume);

        return ResumeResponse.of(savedResume, false);
    }


    public List<ResumeSummaryResponse> getAll(String subject) {
        Long userId = parseUserId(subject);

        Optional<Long> representativeId = representativeRepository
                .findById(userId)
                .map(representative -> representative.getResume().getId());

        return resumeRepository
                .findAllByUser_IdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(resume -> ResumeSummaryResponse.of(
                        resume,
                        representativeId
                                .map(id -> id.equals(resume.getId()))
                                .orElse(false)
                ))
                .toList();
    }


    public ResumeResponse get(String subject, Long resumeId) {
        Long userId = parseUserId(subject);
        Resume resume = findOwned(userId, resumeId);

        return ResumeResponse.of(resume, isRepresentative(userId, resumeId));
    }


    public ResumeDownload download(String subject, Long resumeId) {
        Long userId = parseUserId(subject);
        Resume resume = findOwned(userId, resumeId);
        byte[] contents = fileStorage.read(resume.getStorageKey());

        return new ResumeDownload(resume.getOriginalFileName(), resume.getContentType(), contents);
    }


    @Transactional
    public ResumeResponse updateTitle(String subject, Long resumeId, UpdateResumeTitleRequest request) {
        Long userId = parseUserId(subject);
        Resume resume = findOwnedForUpdate(userId, resumeId);

        resume.updateTitle(request.title());

        return ResumeResponse.of(resume, isRepresentative(userId, resumeId));
    }


    @Transactional
    public ResumeResponse replaceFile(String subject, Long resumeId, MultipartFile file) {
        Long userId = parseUserId(subject);
        Resume resume = findOwnedForUpdate(userId, resumeId);

        ValidatedResumeFile validatedFile = fileValidator.validate(file);
        ResumePdfAnalysis analysis = pdfProcessor.analyze(validatedFile.contents());

        String previousStorageKey = resume.getStorageKey();
        String newStorageKey = fileStorage.store(userId, validatedFile.contents());

        fileCleanup.deleteAfterRollback(newStorageKey);
        fileCleanup.deleteAfterCommit(previousStorageKey);

        resume.replaceFile(
                validatedFile.originalFilename(),
                newStorageKey,
                validatedFile.contentType(),
                validatedFile.size(),
                analysis.sha256()
        );

        applyExtractionResult(resume, analysis);

        return ResumeResponse.of(resume, isRepresentative(userId, resumeId));
    }


    @Transactional
    public void delete(String subject, Long resumeId) {
        Long userId = parseUserId(subject);
        Resume resume = findOwned(userId, resumeId);
        String storageKey = resume.getStorageKey();

        resumeRepository.delete(resume);
        fileCleanup.deleteAfterCommit(storageKey);
    }


    public ResumeResponse getRepresentative(String subject) {
        Long userId = parseUserId(subject);

        ResumeRepresentative representative = representativeRepository
                .findById(userId)
                .orElseThrow(RepresentativeResumeNotFoundException::new);

        return ResumeResponse.of(representative.getResume(), true);
    }


    @Transactional
    public void setRepresentative(String subject, Long resumeId) {
        Long userId = parseUserId(subject);
        Resume resume = findOwned(userId, resumeId);

        representativeRepository.findById(userId)
                .ifPresentOrElse(
                        representative -> representative.changeResume(resume),
                        () -> representativeRepository.save(ResumeRepresentative.create(userId, resume))
                );
    }


    @Transactional
    public void clearRepresentative(String subject) {
        Long userId = parseUserId(subject);

        representativeRepository.deleteById(userId);
    }


    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);

        } catch (NumberFormatException exception) {
            throw new InvalidAccessTokenException();
        }
    }


    private void applyExtractionResult(Resume resume, ResumePdfAnalysis analysis) {
        if (analysis.extractionSucceeded()) {
            resume.completeExtraction(analysis.extractedText());
            return;
        }

        resume.failExtraction(analysis.extractionFailureCode());
    }


    private Resume findOwned(Long userId, Long resumeId) {
        return resumeRepository
                .findByIdAndUser_Id(resumeId, userId)
                .orElseThrow(ResumeNotFoundException::new);
    }


    private boolean isRepresentative(Long userId, Long resumeId) {
        return representativeRepository.existsByUserIdAndResume_Id(userId, resumeId);
    }


    private Resume findOwnedForUpdate(Long userId, Long resumeId) {
        return resumeRepository
                .findOwnedForUpdate(resumeId, userId)
                .orElseThrow(ResumeNotFoundException::new);
    }
}
