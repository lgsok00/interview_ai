package com.interviewai.user.service;

import com.interviewai.ai.usage.AiUsageLifecycleService;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.storage.ResumeFileTransactionCleanup;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

    @Mock
    UserRepository users;
    @Mock
    CoverLetterRepository coverLetters;
    @Mock
    ResumeRepository resumes;
    @Mock
    RagSourceChangeRegistrationService ragRegistration;
    @Mock
    ResumeFileTransactionCleanup fileCleanup;
    @Mock
    AiUsageLifecycleService lifecycle;
    @Mock
    CoverLetter coverLetter;
    @Mock
    Resume resume;

    UserDeletionService service;

    @BeforeEach
    void setUp() {
        service = new UserDeletionService(users, coverLetters, resumes, ragRegistration, fileCleanup, lifecycle);
    }

    @Test
    void registersRagDeletesSchedulesFilesAndDeletesUserInOrder() {
        when(coverLetters.findAllOwnedForUpdate(1L)).thenReturn(List.of(coverLetter));
        when(resumes.findAllOwnedForUpdate(1L)).thenReturn(List.of(resume));
        when(coverLetter.getId()).thenReturn(11L);
        when(resume.getId()).thenReturn(21L);
        when(resume.getStorageKey()).thenReturn("1/resume.pdf");

        service.deleteLocked(1L);

        InOrder order = inOrder(lifecycle, coverLetters, resumes, ragRegistration, fileCleanup, users);
        order.verify(lifecycle).lockExecution();
        order.verify(coverLetters).findAllOwnedForUpdate(1L);
        order.verify(resumes).findAllOwnedForUpdate(1L);
        order.verify(ragRegistration).registerDelete(RagSourceType.COVER_LETTER, 11L);
        order.verify(ragRegistration).registerDelete(RagSourceType.RESUME, 21L);
        order.verify(fileCleanup).deleteAfterCommit("1/resume.pdf");
        order.verify(users).flush();
        order.verify(lifecycle).cancelUser(1L);
        order.verify(users).deleteAllByIdInBatch(List.of(1L));
    }

    @Test
    void doesNotDeleteUserOrScheduleFilesWhenRagRegistrationFails() {
        when(coverLetters.findAllOwnedForUpdate(1L)).thenReturn(List.of(coverLetter));
        when(resumes.findAllOwnedForUpdate(1L)).thenReturn(List.of(resume));
        when(coverLetter.getId()).thenReturn(11L);
        doThrow(new IllegalStateException("registration failed"))
                .when(ragRegistration).registerDelete(RagSourceType.COVER_LETTER, 11L);

        assertThatThrownBy(() -> service.deleteLocked(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("registration failed");

        verifyNoInteractions(fileCleanup);
        verify(lifecycle, never()).cancelUser(anyLong());
        verify(users, never()).flush();
        verify(users, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void usageLockFailureStopsBeforeDomainLocks() {
        doThrow(new IllegalStateException("usage unavailable")).when(lifecycle).lockExecution();
        assertThatThrownBy(() -> service.deleteLocked(1L)).hasMessage("usage unavailable");
        verifyNoInteractions(coverLetters, resumes, ragRegistration, fileCleanup, users);
    }

    @Test
    void cancellationFailurePreventsUserDeletion() {
        doThrow(new IllegalStateException("cancel failed")).when(lifecycle).cancelUser(1L);
        assertThatThrownBy(() -> service.deleteLocked(1L)).hasMessage("cancel failed");
        verify(users).flush();
        verify(users, never()).deleteAllByIdInBatch(any());
    }
}
