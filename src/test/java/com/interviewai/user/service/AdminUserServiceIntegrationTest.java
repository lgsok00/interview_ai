package com.interviewai.user.service;

import com.interviewai.auth.service.RefreshTokenService;
import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.support.MySqlIntegrationTest;
import com.interviewai.user.dto.AdminUserResponse;
import com.interviewai.user.dto.ChangeUserRoleRequest;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest
@Import({AdminUserService.class, AdminAuthorizationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminUserServiceIntegrationTest extends MySqlIntegrationTest {

    private final List<Long> userIds = new ArrayList<>();
    @Autowired
    private AdminUserService service;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    private AdminAuthorizationService authorizationService;
    @MockitoBean
    private RefreshTokenService refreshTokenService;
    @MockitoBean
    private UserDeletionService userDeletionService;

    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }


    @Test
    @DisplayName("MySQL에서 이메일·닉네임 검색과 역할·가입 제공자 필터를 적용한다")
    void searchesByKeywordRoleAndProviderOnMySql() {
        User admin = createLocal("admin@example.com", "관리자", UserRole.ADMIN);
        User matching = createGoogle("spring.user@example.com", "백엔드 개발자", UserRole.USER);
        createGoogle("other@example.com", "Spring 관리자", UserRole.ADMIN);
        createLocal("local.spring@example.com", "로컬 사용자", UserRole.USER);

        var byEmail = service.search(
                admin.getId().toString(),
                "  SPRING.USER  ",
                UserRole.USER,
                AuthProvider.GOOGLE,
                null,
                0,
                20
        );
        var byNickname = service.search(
                admin.getId().toString(),
                "백엔드",
                null,
                null,
                null,
                0,
                20
        );

        assertThat(byEmail.items()).extracting(AdminUserResponse::id).containsExactly(matching.getId());
        assertThat(byNickname.items()).extracting(AdminUserResponse::id).containsExactly(matching.getId());
    }


    @Test
    @DisplayName("MySQL LIKE 와일드카드는 일반 문자로 검색하고 생성 역순 페이지를 반환한다")
    void escapesLikeWildcardsAndPagesInStableOrder() {
        User admin = createLocal("admin@example.com", "관리자", UserRole.ADMIN);
        User percent = createLocal("percent@example.com", "지원자 100%", UserRole.USER);
        User underscore = createLocal("underscore@example.com", "지원자_A", UserRole.USER);
        User plain = createLocal("plain@example.com", "지원자 1000", UserRole.USER);

        var percentResult = service.search(admin.getId().toString(), "%", null, null, null, 0, 20);
        var underscoreResult = service.search(admin.getId().toString(), "_", null, null, null, 0, 20);
        var firstPage = service.search(admin.getId().toString(), null, null, null, null, 0, 2);
        var secondPage = service.search(admin.getId().toString(), null, null, null, null, 1, 2);

        assertThat(percentResult.items()).extracting(AdminUserResponse::id).containsExactly(percent.getId());
        assertThat(underscoreResult.items()).extracting(AdminUserResponse::id).containsExactly(underscore.getId());
        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.items()).extracting(AdminUserResponse::id)
                .containsExactly(plain.getId(), underscore.getId());
        assertThat(secondPage.items()).extracting(AdminUserResponse::id)
                .containsExactly(percent.getId(), admin.getId());
    }


    @Test
    @DisplayName("관리자 목록 비관적 잠금은 다른 권한 변경 트랜잭션을 커밋까지 대기시킨다")
    void adminLockSerializesRoleChanges() throws Exception {
        User first = createLocal("first@example.com", "첫 관리자", UserRole.ADMIN);
        User second = createLocal("second@example.com", "둘째 관리자", UserRole.ADMIN);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attempting = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> tx().executeWithoutResult(status -> {
                users.findAllAdminsForUpdate();
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            var waiter = executor.submit(() -> {
                attempting.countDown();
                return service.changeRole(
                        first.getId().toString(),
                        second.getId(),
                        new ChangeUserRoleRequest(UserRole.USER)
                );
            });
            assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(waiter.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertThat(waiter.get(10, TimeUnit.SECONDS).role()).isEqualTo(UserRole.USER);
        } finally {
            release.countDown();
        }
    }


    @Test
    @DisplayName("서로를 동시에 강등해도 한 요청만 성공하고 관리자 한 명이 남는다")
    void concurrentDemotionsLeaveOneAdmin() throws Exception {
        User first = createLocal("first@example.com", "첫 관리자", UserRole.ADMIN);
        User second = createLocal("second@example.com", "둘째 관리자", UserRole.ADMIN);
        CyclicBarrier authenticated = new CyclicBarrier(2);

        doAnswer(invocation -> {
            User actor = (User) invocation.callRealMethod();
            authenticated.await(5, TimeUnit.SECONDS);
            return actor;
        }).when(authorizationService).requireAdmin(org.mockito.ArgumentMatchers.anyString());

        List<String> outcomes = race(
                () -> demote(first, second),
                () -> demote(second, first)
        );

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "FORBIDDEN");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND id IN (?, ?)",
                Integer.class,
                first.getId(),
                second.getId()
        )).isEqualTo(1);
    }


    private String demote(User actor, User target) {
        try {
            service.changeRole(
                    actor.getId().toString(),
                    target.getId(),
                    new ChangeUserRoleRequest(UserRole.USER)
            );
            return "OK";
        } catch (CatalogException exception) {
            return exception.getCode();
        }
    }


    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(first);
            var secondResult = executor.submit(second);
            return List.of(
                    firstResult.get(20, TimeUnit.SECONDS),
                    secondResult.get(20, TimeUnit.SECONDS)
            );
        }
    }


    private User createLocal(String email, String nickname, UserRole role) {
        return save(User.createLocalUser(email, "{bcrypt}encoded", nickname), role);
    }


    private User createGoogle(String email, String nickname, UserRole role) {
        return save(User.createGoogleUser(email, nickname, UUID.randomUUID().toString()), role);
    }


    private User save(User user, UserRole role) {
        user.changeRole(role);
        User saved = users.saveAndFlush(user);
        userIds.add(saved.getId());
        return saved;
    }


    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }


    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
