package com.interviewai.coverletter.draft;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.coverletter.dto.CoverLetterResponse;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.exception.CoverLetterNotFoundException;
import com.interviewai.coverletter.exception.CoverLetterVersionNotFoundException;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterRepresentativeRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.global.error.CatalogException;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class CoverLetterDraftService {

    private static final String UNCONFIGURED_MODEL = "unconfigured";

    private final CoverLetterDraftRepository draftRepository;
    private final CoverLetterDraftSnapshotAssembler snapshotAssembler;
    private final CoverLetterDraftProperties properties;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterVersionRepository versionRepository;
    private final CoverLetterRepresentativeRepository representativeRepository;
    private final RagSourceChangeRegistrationService ragRegistrationService;
    private final JdbcTemplate jdbc;


    public CoverLetterDraftService(
            CoverLetterDraftRepository draftRepository,
            CoverLetterDraftSnapshotAssembler snapshotAssembler,
            CoverLetterDraftProperties properties,
            CoverLetterRepository coverLetterRepository,
            CoverLetterVersionRepository versionRepository,
            CoverLetterRepresentativeRepository representativeRepository,
            RagSourceChangeRegistrationService ragRegistrationService,
            JdbcTemplate jdbc
    ) {
        this.draftRepository = draftRepository;
        this.snapshotAssembler = snapshotAssembler;
        this.properties = properties;
        this.coverLetterRepository = coverLetterRepository;
        this.versionRepository = versionRepository;
        this.representativeRepository = representativeRepository;
        this.ragRegistrationService = ragRegistrationService;
        this.jdbc = jdbc;
    }


    @Transactional
    public CoverLetterDraftResponse create(String subject, Long coverLetterId, CreateCoverLetterDraftRequest request) {
        Long userId = parseUserId(subject);

        return createDraft(userId, coverLetterId, request, null);
    }


    public List<CoverLetterDraftSummaryResponse> getAll(String subject, Long coverLetterId) {
        Long userId = parseUserId(subject);
        requireOwnedCoverLetter(userId, coverLetterId);

        return draftRepository
                .findOwnedAll(userId, coverLetterId)
                .stream()
                .map(CoverLetterDraftSummaryResponse::of)
                .toList();
    }


    public CoverLetterDraftResponse get(String subject, Long coverLetterId, Long draftId) {
        Long userId = parseUserId(subject);

        return CoverLetterDraftResponse.of(findOwned(userId, coverLetterId, draftId));
    }


    @Transactional
    public CoverLetterDraftResponse regenerate(String subject, Long coverLetterId, Long draftId) {
        Long userId = parseUserId(subject);

        CoverLetterDraft source = findOwned(userId, coverLetterId, draftId);

        if (source.getStatus() == CoverLetterDraftStatus.PENDING
                || source.getStatus() == CoverLetterDraftStatus.RUNNING) {
            throw conflict("실행 중이거나 대기 중인 초안은 재생성할 수 없습니다.");
        }

        CreateCoverLetterDraftRequest request = new CreateCoverLetterDraftRequest(
                source.getJobPostingId(), source.getResumeId(), source.getInstruction()
        );

        return createDraft(userId, coverLetterId, request, source);
    }


    @Transactional
    public CoverLetterResponse apply(
            String subject,
            Long coverLetterId,
            Long draftId,
            ApplyCoverLetterDraftRequest request
    ) {
        Long userId = parseUserId(subject);

        CoverLetter coverLetter = coverLetterRepository.findOwnedForUpdate(coverLetterId, userId)
                .orElseThrow(CoverLetterNotFoundException::new);

        CoverLetterDraft draft = draftRepository.findOwnedForUpdate(draftId, userId, coverLetterId)
                .orElseThrow(this::notFound);

        if (draft.getStatus() == CoverLetterDraftStatus.APPLIED) {
            if (!draft.hasSameAppliedContent(request.title(), request.content())) {
                throw conflict("이미 다른 내용으로 적용된 초안입니다.");
            }

            CoverLetterVersion appliedVersion = versionRepository
                    .findByCoverLetter_IdAndVersionNumber(coverLetterId, draft.getAppliedVersionNumber())
                    .orElseThrow(CoverLetterVersionNotFoundException::new);

            return CoverLetterResponse.of(coverLetter, appliedVersion, isRepresentative(userId, coverLetterId));
        }

        if (draft.getStatus() != CoverLetterDraftStatus.REVIEW_READY) {
            throw conflict("검수 대기 상태의 초안만 적용할 수 있습니다.");
        }

        if (!draft.getBaseVersionNumber().equals(coverLetter.getCurrentVersionNumber())) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "DRAFT_BASE_VERSION_CONFLICT",
                    "초안 생성 후 자기소개서가 변경되었습니다."
            );
        }

        LocalDateTime now = databaseNow();
        int nextVersionNumber = coverLetter.addVersion(request.title());

        CoverLetterVersion version = versionRepository.save(
                CoverLetterVersion.create(coverLetter, nextVersionNumber, request.title(), request.content())
        );

        ragRegistrationService.registerCoverLetterUpsert(coverLetter, version);

        draft.apply(nextVersionNumber, request.title(), request.content(), now);

        return CoverLetterResponse.of(coverLetter, version, isRepresentative(userId, coverLetterId));
    }


    private CoverLetterDraftResponse createDraft(
            Long userId,
            Long coverLetterId,
            CreateCoverLetterDraftRequest request,
            CoverLetterDraft sourceDraft
    ) {
        CoverLetterDraftSnapshotAssembler.PreparedInput prepared =
                snapshotAssembler.assemble(userId, coverLetterId, request);

        LocalDateTime now = databaseNow();
        String configuredModel = properties.model().isBlank() ? UNCONFIGURED_MODEL : properties.model();

        CoverLetterDraft draft = CoverLetterDraft.create(
                prepared.coverLetter().getUser(),
                prepared.coverLetter(),
                sourceDraft,
                prepared.input(),
                prepared.inputHash(),
                CoverLetterDraftPolicy.PROMPT_TEMPLATE_VERSION,
                configuredModel,
                now
        );

        draftRepository.save(draft);

        if (!properties.enabled() || properties.model().isBlank()) {
            draft.failPending("DRAFT_AI_NOT_CONFIGURED", now);
        }

        return CoverLetterDraftResponse.of(draft);
    }


    private CoverLetterDraft findOwned(Long userId, Long coverLetterId, Long draftId) {
        return draftRepository.findOwned(draftId, userId, coverLetterId).orElseThrow(this::notFound);
    }


    private void requireOwnedCoverLetter(Long userId, Long coverLetterId) {
        if (!coverLetterRepository.existsByIdAndUser_Id(coverLetterId, userId)) {
            throw new CoverLetterNotFoundException();
        }
    }


    private boolean isRepresentative(Long userId, Long coverLetterId) {
        return representativeRepository.existsByUserIdAndCoverLetter_Id(userId, coverLetterId);
    }


    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);

        } catch (NumberFormatException exception) {
            throw new InvalidAccessTokenException();
        }
    }


    private CatalogException notFound() {
        return new CatalogException(
                HttpStatus.NOT_FOUND,
                "COVER_LETTER_DRAFT_NOT_FOUND",
                "자기소개서 초안을 찾을 수 없습니다."
        );
    }


    private CatalogException conflict(String message) {
        return new CatalogException(HttpStatus.CONFLICT, "COVER_LETTER_DRAFT_CONFLICT", message);
    }


    private LocalDateTime databaseNow() {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class),
                "DB 현재 시각은 필수입니다."
        );
    }
}
