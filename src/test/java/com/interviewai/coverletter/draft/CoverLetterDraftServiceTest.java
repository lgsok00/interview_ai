package com.interviewai.coverletter.draft;

import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterRepresentativeRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.global.error.CatalogException;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CoverLetterDraftServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 10, 0);

    private final CoverLetterDraftRepository drafts = mock(CoverLetterDraftRepository.class);
    private final CoverLetterDraftSnapshotAssembler snapshots = mock(CoverLetterDraftSnapshotAssembler.class);
    private final CoverLetterRepository coverLetters = mock(CoverLetterRepository.class);
    private final CoverLetterVersionRepository versions = mock(CoverLetterVersionRepository.class);
    private final CoverLetterRepresentativeRepository representatives = mock(CoverLetterRepresentativeRepository.class);
    private final RagSourceChangeRegistrationService rag = mock(RagSourceChangeRegistrationService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private User user;
    private CoverLetter coverLetter;
    private CoverLetterDraft.InputSnapshot input;

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("user@example.com", "encoded", "사용자");
        ReflectionTestUtils.setField(user, "id", 1L);
        coverLetter = CoverLetter.create(user, "기존 제목");
        ReflectionTestUtils.setField(coverLetter, "id", 10L);
        input = input(1);

        when(jdbc.queryForObject(eq("SELECT UTC_TIMESTAMP(6)"), eq(LocalDateTime.class)))
                .thenReturn(NOW);
        when(drafts.save(any(CoverLetterDraft.class))).thenAnswer(invocation -> {
            CoverLetterDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", 40L);
            return draft;
        });
    }

    @Test
    void createsPendingDraftFromPreparedSnapshot() {
        CoverLetterDraftService service = service(true, "test-model");
        CreateCoverLetterDraftRequest request = new CreateCoverLetterDraftRequest(20L, 30L, "강조");
        when(snapshots.assemble(1L, 10L, request)).thenReturn(prepared(input));

        CoverLetterDraftResponse response = service.create("1", 10L, request);

        assertThat(response.id()).isEqualTo(40L);
        assertThat(response.status()).isEqualTo(CoverLetterDraftStatus.PENDING);
        assertThat(response.baseVersionNumber()).isEqualTo(1);
        assertThat(response.failureCode()).isNull();
    }

    @Test
    void storesFailedDraftWithoutFallbackWhenAiIsDisabled() {
        CoverLetterDraftService service = service(false, "");
        CreateCoverLetterDraftRequest request = new CreateCoverLetterDraftRequest(20L, 30L, null);
        when(snapshots.assemble(1L, 10L, request)).thenReturn(prepared(input));

        CoverLetterDraftResponse response = service.create("1", 10L, request);

        assertThat(response.status()).isEqualTo(CoverLetterDraftStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("DRAFT_AI_NOT_CONFIGURED");
        assertThat(response.generatedContent()).isNull();
    }

    @Test
    void regeneratesTerminalDraftWithNewSnapshotAndSourceLink() {
        CoverLetterDraft source = readyDraft();
        ReflectionTestUtils.setField(source, "id", 40L);
        CoverLetterDraft.InputSnapshot latest = input(2);
        CreateCoverLetterDraftRequest request = new CreateCoverLetterDraftRequest(20L, 30L, "강조");
        when(drafts.findOwned(40L, 1L, 10L)).thenReturn(Optional.of(source));
        when(snapshots.assemble(1L, 10L, request)).thenReturn(prepared(latest));

        CoverLetterDraftResponse response = service(true, "test-model")
                .regenerate("1", 10L, 40L);

        assertThat(response.sourceDraftId()).isEqualTo(40L);
        assertThat(response.baseVersionNumber()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(CoverLetterDraftStatus.PENDING);
    }

    @Test
    void rejectsRegenerationWhileOriginalIsPending() {
        CoverLetterDraft pending = draft(input);
        ReflectionTestUtils.setField(pending, "id", 40L);
        when(drafts.findOwned(40L, 1L, 10L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service(true, "test-model")
                .regenerate("1", 10L, 40L))
                .isInstanceOf(CatalogException.class)
                .extracting(exception -> ((CatalogException) exception).getCode())
                .isEqualTo("COVER_LETTER_DRAFT_CONFLICT");

        verify(snapshots, never()).assemble(any(), any(), any());
    }

    @Test
    void appliesReviewedDraftAsNewVersionAndRegistersRagUpsert() {
        CoverLetterDraft draft = readyDraft();
        ReflectionTestUtils.setField(draft, "id", 40L);
        when(coverLetters.findOwnedForUpdate(10L, 1L)).thenReturn(Optional.of(coverLetter));
        when(drafts.findOwnedForUpdate(40L, 1L, 10L)).thenReturn(Optional.of(draft));
        when(versions.save(any(CoverLetterVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(representatives.existsByUserIdAndCoverLetter_Id(1L, 10L)).thenReturn(false);

        var response = service(true, "test-model").apply(
                "1", 10L, 40L, new ApplyCoverLetterDraftRequest("검수 제목", "검수 본문")
        );

        assertThat(response.currentVersionNumber()).isEqualTo(2);
        assertThat(response.title()).isEqualTo("검수 제목");
        assertThat(response.content()).isEqualTo("검수 본문");
        assertThat(draft.getStatus()).isEqualTo(CoverLetterDraftStatus.APPLIED);
        assertThat(draft.getAppliedVersionNumber()).isEqualTo(2);
        verify(rag).registerCoverLetterUpsert(eq(coverLetter), any(CoverLetterVersion.class));
    }

    @Test
    void rejectsApplyWhenCurrentVersionChangedAfterGeneration() {
        CoverLetterDraft draft = readyDraft();
        ReflectionTestUtils.setField(draft, "id", 40L);
        coverLetter.addVersion("사용자 수정");
        when(coverLetters.findOwnedForUpdate(10L, 1L)).thenReturn(Optional.of(coverLetter));
        when(drafts.findOwnedForUpdate(40L, 1L, 10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service(true, "test-model").apply(
                "1", 10L, 40L, new ApplyCoverLetterDraftRequest("검수 제목", "검수 본문")
        ))
                .isInstanceOf(CatalogException.class)
                .extracting(exception -> ((CatalogException) exception).getCode())
                .isEqualTo("DRAFT_BASE_VERSION_CONFLICT");

        verify(versions, never()).save(any());
        verify(rag, never()).registerCoverLetterUpsert(any(), any());
    }

    @Test
    void repeatedApplyWithSameContentReturnsOriginalVersionWithoutNewWrite() {
        CoverLetterDraft draft = readyDraft();
        ReflectionTestUtils.setField(draft, "id", 40L);
        coverLetter.addVersion("검수 제목");
        draft.apply(2, "검수 제목", "검수 본문", NOW);
        CoverLetterVersion applied = CoverLetterVersion.create(coverLetter, 2, "검수 제목", "검수 본문");

        when(coverLetters.findOwnedForUpdate(10L, 1L)).thenReturn(Optional.of(coverLetter));
        when(drafts.findOwnedForUpdate(40L, 1L, 10L)).thenReturn(Optional.of(draft));
        when(versions.findByCoverLetter_IdAndVersionNumber(10L, 2)).thenReturn(Optional.of(applied));

        var response = service(true, "test-model").apply(
                "1", 10L, 40L, new ApplyCoverLetterDraftRequest(" 검수 제목 ", " 검수 본문 ")
        );

        assertThat(response.currentVersionNumber()).isEqualTo(2);
        verify(versions, never()).save(any());
        verify(rag, never()).registerCoverLetterUpsert(any(), any());
    }

    @Test
    void repeatedApplyWithDifferentContentIsConflict() {
        CoverLetterDraft draft = readyDraft();
        ReflectionTestUtils.setField(draft, "id", 40L);
        coverLetter.addVersion("검수 제목");
        draft.apply(2, "검수 제목", "검수 본문", NOW);
        when(coverLetters.findOwnedForUpdate(10L, 1L)).thenReturn(Optional.of(coverLetter));
        when(drafts.findOwnedForUpdate(40L, 1L, 10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service(true, "test-model").apply(
                "1", 10L, 40L, new ApplyCoverLetterDraftRequest("다른 제목", "검수 본문")
        ))
                .isInstanceOf(CatalogException.class)
                .extracting(exception -> ((CatalogException) exception).getCode())
                .isEqualTo("COVER_LETTER_DRAFT_CONFLICT");
    }

    private CoverLetterDraftService service(boolean enabled, String model) {
        return new CoverLetterDraftService(
                drafts,
                snapshots,
                new CoverLetterDraftProperties(
                        enabled,
                        model,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        20
                ),
                coverLetters,
                versions,
                representatives,
                rag,
                jdbc
        );
    }

    private CoverLetterDraftSnapshotAssembler.PreparedInput prepared(
            CoverLetterDraft.InputSnapshot snapshot
    ) {
        return new CoverLetterDraftSnapshotAssembler.PreparedInput(
                coverLetter,
                snapshot,
                "a".repeat(64)
        );
    }

    private CoverLetterDraft readyDraft() {
        CoverLetterDraft draft = draft(input);
        CoverLetterDraft.Attempt attempt = draft.claim(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                NOW
        );
        draft.complete(
                attempt.id(),
                new CoverLetterDraft.GenerateDraft("AI 제목", "AI 본문", "요약", List.of()),
                NOW.plusSeconds(1)
        );
        return draft;
    }

    private CoverLetterDraft draft(CoverLetterDraft.InputSnapshot snapshot) {
        return CoverLetterDraft.create(
                user,
                coverLetter,
                null,
                snapshot,
                "a".repeat(64),
                CoverLetterDraftPolicy.PROMPT_TEMPLATE_VERSION,
                "test-model",
                NOW
        );
    }

    private CoverLetterDraft.InputSnapshot input(int baseVersion) {
        return new CoverLetterDraft.InputSnapshot(
                20L, 30L, baseVersion, "기존 제목", "기존 본문",
                40L, "회사", "IT", "회사 설명", null, null,
                "공고", "백엔드", "FULL_TIME", null, "공고 설명", null,
                null, null, "이력서", "이력서 본문", "강조"
        );
    }
}
