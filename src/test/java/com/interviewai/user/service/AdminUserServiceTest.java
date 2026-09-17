package com.interviewai.user.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.user.dto.ChangeUserRoleRequest;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    UserRepository users;
    @Mock
    AdminAuthorizationService authorization;
    AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(users, authorization);
    }

    @Test
    void searchesWithEscapedKeywordFiltersAndStableOrder() {
        PageRequest page = PageRequest.of(0, 100,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        User user = user(2L, UserRole.USER);
        when(users.searchForAdmin("%a!%!_!!%", UserRole.USER, AuthProvider.LOCAL, page))
                .thenReturn(new PageImpl<>(List.of(user), page, 1));

        var result = service.search("1", " a%_! ", UserRole.USER, AuthProvider.LOCAL, 0, 100);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo(2L);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        var order = inOrder(authorization, users);
        order.verify(authorization).requireAdmin("1");
        order.verify(users).searchForAdmin("%a!%!_!!%", UserRole.USER, AuthProvider.LOCAL, page);
    }

    @Test
    void blankKeywordAndNoFiltersReturnEmptyPage() {
        when(users.searchForAdmin(eq("%"), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        var result = service.search("1", "  ", null, null, 0, 20);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalPages()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void rejectsInvalidPagination(int page, int size) {
        assertThatThrownBy(() -> service.search("1", null, null, null, page, size))
                .isInstanceOf(CatalogException.class);
        verifyNoInteractions(users);
    }

    @Test
    void rejectsOverlongKeyword() {
        assertThatThrownBy(() -> service.search("1", "a".repeat(101), null, null, 0, 20))
                .isInstanceOf(CatalogException.class);
        verifyNoInteractions(users);
    }

    @Test
    void returnsUserDetail() {
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, UserRole.USER)));
        assertThat(service.get("1", 2L).email()).isEqualTo("user2@example.com");
        verify(authorization).requireAdmin("1");
    }

    @Test
    void rejectsMissingUser() {
        assertThatThrownBy(() -> service.get("1", 2L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void rejectsInvalidId() {
        assertThatThrownBy(() -> service.get("1", 0L)).isInstanceOf(CatalogException.class);
        verifyNoInteractions(users);
    }

    @Test
    void deniesAllOperationsBeforeReadingTargets() {
        when(authorization.requireAdmin("1")).thenThrow(
                new CatalogException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자 권한이 필요합니다."));
        assertThatThrownBy(() -> service.search("1", null, null, null, 0, 20))
                .isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> service.get("1", 2L)).isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> service.changeRole("1", 2L, new ChangeUserRoleRequest(UserRole.ADMIN)))
                .isInstanceOf(CatalogException.class);
        verifyNoInteractions(users);
    }

    @Test
    void promotesUser() {
        User admin = user(1L, UserRole.ADMIN);
        User target = user(2L, UserRole.USER);
        when(authorization.requireAdmin("1")).thenReturn(admin);
        when(users.findAllAdminsForUpdate()).thenReturn(List.of(admin));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        assertThat(service.changeRole("1", 2L, new ChangeUserRoleRequest(UserRole.ADMIN)).role())
                .isEqualTo(UserRole.ADMIN);
        assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void demotesAnotherAdmin() {
        User admin = user(1L, UserRole.ADMIN);
        User target = user(2L, UserRole.ADMIN);
        when(authorization.requireAdmin("1")).thenReturn(admin);
        when(users.findAllAdminsForUpdate()).thenReturn(List.of(admin, target));
        assertThat(service.changeRole("1", 2L, new ChangeUserRoleRequest(UserRole.USER)).role())
                .isEqualTo(UserRole.USER);
    }

    @Test
    void sameRoleIsIdempotentIncludingSelf() {
        User admin = user(1L, UserRole.ADMIN);
        when(authorization.requireAdmin("1")).thenReturn(admin);
        when(users.findAllAdminsForUpdate()).thenReturn(List.of(admin));
        assertThat(service.changeRole("1", 1L, new ChangeUserRoleRequest(UserRole.ADMIN)).role())
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void rejectsSelfDemotionIncludingLastAdmin() {
        User admin = user(1L, UserRole.ADMIN);
        when(authorization.requireAdmin("1")).thenReturn(admin);
        when(users.findAllAdminsForUpdate()).thenReturn(List.of(admin));
        assertThatThrownBy(() -> service.changeRole("1", 1L, new ChangeUserRoleRequest(UserRole.USER)))
                .isInstanceOfSatisfying(CatalogException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("ADMIN_SELF_DEMOTION_NOT_ALLOWED");
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void rejectsMissingRoleChangeTarget() {
        User admin = user(1L, UserRole.ADMIN);
        when(authorization.requireAdmin("1")).thenReturn(admin);
        when(users.findAllAdminsForUpdate()).thenReturn(List.of(admin));
        assertThatThrownBy(() -> service.changeRole("1", 2L, new ChangeUserRoleRequest(UserRole.ADMIN)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void rejectsActorDemotedWhileWaitingForAdminLocks() {
        // 최초 인증에서 읽은 JPA 객체는 ADMIN이지만 잠금 조회의 최신 결과에는 호출자가 없다.
        User staleActor = user(1L, UserRole.ADMIN);
        User remainingAdmin = user(2L, UserRole.ADMIN);
        User target = user(3L, UserRole.USER);
        when(authorization.requireAdmin("1")).thenReturn(staleActor);
        when(users.findAllAdminsForUpdate()).thenReturn(List.of(remainingAdmin));
        lenient().when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.changeRole("1", 3L, new ChangeUserRoleRequest(UserRole.ADMIN)))
                .isInstanceOfSatisfying(CatalogException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getCode()).isEqualTo("FORBIDDEN");
                });
        assertThat(target.getRole()).isEqualTo(UserRole.USER);
    }

    private User user(long id, UserRole role) {
        User user = User.createLocalUser("user" + id + "@example.com", "hash", "사용자" + id);
        ReflectionTestUtils.setField(user, "id", id);
        user.changeRole(role);
        return user;
    }
}
