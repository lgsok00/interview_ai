package com.interviewai.coverletter.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.coverletter.dto.CoverLetterResponse;
import com.interviewai.coverletter.dto.CoverLetterSummaryResponse;
import com.interviewai.coverletter.dto.CoverLetterVersionResponse;
import com.interviewai.coverletter.dto.CoverLetterVersionSummaryResponse;
import com.interviewai.coverletter.dto.CreateCoverLetterRequest;
import com.interviewai.coverletter.dto.UpdateCoverLetterRequest;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterRepresentative;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.exception.CoverLetterNotFoundException;
import com.interviewai.coverletter.exception.CoverLetterVersionNotFoundException;
import com.interviewai.coverletter.exception.RepresentativeCoverLetterNotFoundException;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterRepresentativeRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.user.entity.User;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CoverLetterService {

    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterVersionRepository versionRepository;
    private final CoverLetterRepresentativeRepository representativeRepository;
    private final UserRepository userRepository;


    public CoverLetterService(
            CoverLetterRepository coverLetterRepository,
            CoverLetterVersionRepository versionRepository,
            CoverLetterRepresentativeRepository representativeRepository,
            UserRepository userRepository
    ) {
        this.coverLetterRepository = coverLetterRepository;
        this.versionRepository = versionRepository;
        this.representativeRepository = representativeRepository;
        this.userRepository = userRepository;
    }


    @Transactional
    public CoverLetterResponse create(String subject, CreateCoverLetterRequest request) {
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.create(user, request.title()));

        CoverLetterVersion version = versionRepository
                .save(CoverLetterVersion.createInitial(coverLetter, request.title(), request.content()));

        return CoverLetterResponse.of(coverLetter, version, false);
    }


    public List<CoverLetterSummaryResponse> getAll(String subject) {
        Long userId = parseUserId(subject);

        Optional<Long> representativeId = representativeRepository.findById(userId)
                .map(representative -> representative.getCoverLetter().getId());

        return coverLetterRepository.findAllByUser_IdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(coverLetter -> CoverLetterSummaryResponse.of(
                        coverLetter,
                        representativeId
                                .map(id -> id.equals(coverLetter.getId()))
                                .orElse(false)
                ))
                .toList();
    }


    public CoverLetterResponse get(String subject, Long coverLetterId) {
        Long userId = parseUserId(subject);
        CoverLetter coverLetter = findOwned(userId, coverLetterId);
        CoverLetterVersion currentVersion = findVersion(coverLetterId, coverLetter.getCurrentVersionNumber());

        return CoverLetterResponse.of(coverLetter, currentVersion, isRepresentative(userId, coverLetterId));
    }


    @Transactional
    public CoverLetterResponse update(String subject, Long coverLetterId, UpdateCoverLetterRequest request) {
        Long userId = parseUserId(subject);
        CoverLetter coverLetter = findOwnedForUpdate(userId, coverLetterId);

        int versionNumber = coverLetter.addVersion(request.title());

        CoverLetterVersion version = versionRepository.save(
                CoverLetterVersion.create(coverLetter, versionNumber, request.title(), request.content())
        );

        return CoverLetterResponse.of(coverLetter, version, isRepresentative(userId, coverLetterId));
    }


    @Transactional
    public void delete(String subject, Long coverLetterId) {
        Long userId = parseUserId(subject);
        CoverLetter coverLetter = findOwned(userId, coverLetterId);

        coverLetterRepository.delete(coverLetter);
    }


    public List<CoverLetterVersionSummaryResponse> getVersions(String subject, Long coverLetterId) {
        Long userId = parseUserId(subject);
        CoverLetter coverLetter = findOwned(userId, coverLetterId);

        return versionRepository
                .findAllByCoverLetter_IdOrderByVersionNumberDesc(coverLetterId)
                .stream()
                .map(version -> CoverLetterVersionSummaryResponse.of(
                        version,
                        version.getVersionNumber().equals(coverLetter.getCurrentVersionNumber())
                ))
                .toList();
    }


    public CoverLetterVersionResponse getVersion(String subject, Long coverLetterId, Integer versionNumber) {
        Long userId = parseUserId(subject);
        CoverLetter coverLetter = findOwned(userId, coverLetterId);
        CoverLetterVersion version = findVersion(coverLetterId, versionNumber);

        return CoverLetterVersionResponse.of(version, versionNumber.equals(coverLetter.getCurrentVersionNumber()));
    }


    @Transactional
    public CoverLetterResponse restore(String subject, Long coverLetterId, Integer versionNumber) {
        Long userId = parseUserId(subject);
        CoverLetter coverLetter = findOwnedForUpdate(userId, coverLetterId);
        CoverLetterVersion sourceVersion = findVersion(coverLetterId, versionNumber);

        int newVersionNumber = coverLetter.addVersion(sourceVersion.getTitle());

        CoverLetterVersion restoredVersion = versionRepository.save(
                CoverLetterVersion.create(
                        coverLetter,
                        newVersionNumber,
                        sourceVersion.getTitle(),
                        sourceVersion.getContent()
                )
        );

        return CoverLetterResponse.of(coverLetter, restoredVersion, isRepresentative(userId, coverLetterId));
    }


    public CoverLetterResponse getRepresentative(String subject) {
        Long userId = parseUserId(subject);

        CoverLetterRepresentative representative = representativeRepository
                .findById(userId)
                .orElseThrow(RepresentativeCoverLetterNotFoundException::new);

        CoverLetter coverLetter = representative.getCoverLetter();
        CoverLetterVersion currentVersion = findVersion(coverLetter.getId(), coverLetter.getCurrentVersionNumber());

        return CoverLetterResponse.of(coverLetter, currentVersion, true);
    }


    @Transactional
    public void setRepresentative(String subject, Long coverLetterId) {
        Long userId = parseUserId(subject);
        CoverLetter coverLetter = findOwned(userId, coverLetterId);

        representativeRepository.findById(userId)
                .ifPresentOrElse(
                        representative -> representative.changeCoverLetter(coverLetter),
                        () -> representativeRepository.save(CoverLetterRepresentative.create(userId, coverLetter))
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


    private CoverLetter findOwned(Long userId, Long coverLetterId) {
        return coverLetterRepository.findByIdAndUser_Id(coverLetterId, userId)
                .orElseThrow(CoverLetterNotFoundException::new);
    }


    private CoverLetterVersion findVersion(Long coverLetterId, Integer versionNumber) {
        return versionRepository
                .findByCoverLetter_IdAndVersionNumber(coverLetterId, versionNumber)
                .orElseThrow(CoverLetterVersionNotFoundException::new);
    }


    private boolean isRepresentative(Long userId, Long coverLetterId) {
        return representativeRepository.existsByUserIdAndCoverLetter_Id(userId, coverLetterId);
    }


    private CoverLetter findOwnedForUpdate(Long userId, Long coverLetterId) {
        return coverLetterRepository.findOwnedForUpdate(coverLetterId, userId)
                .orElseThrow(CoverLetterNotFoundException::new);
    }
}
