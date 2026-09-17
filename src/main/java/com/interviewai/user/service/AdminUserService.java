package com.interviewai.user.service;

import com.interviewai.auth.service.RefreshTokenService;
import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.user.dto.AdminUserPageResponse;
import com.interviewai.user.dto.AdminUserResponse;
import com.interviewai.user.dto.ChangeUserRoleRequest;
import com.interviewai.user.dto.ChangeUserStatusRequest;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.enums.UserStatus;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminAuthorizationService authorizationService;
    private final RefreshTokenService refreshTokenService;
    private final UserDeletionService userDeletionService;


    public AdminUserService(
            UserRepository userRepository,
            AdminAuthorizationService authorizationService,
            RefreshTokenService refreshTokenService,
            UserDeletionService userDeletionService
    ) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.refreshTokenService = refreshTokenService;
        this.userDeletionService = userDeletionService;
    }


    public AdminUserPageResponse search(
            String subject,
            String keyword,
            UserRole role,
            AuthProvider provider,
            UserStatus status,
            int page,
            int size
    ) {
        authorizationService.requireAdmin(subject);

        String pattern = CatalogInput.pattern(keyword);
        PageRequest pageable = CatalogInput.page(page, size);

        return AdminUserPageResponse.from(
                userRepository.searchForAdmin(pattern, role, provider, status, pageable)
        );
    }


    public AdminUserResponse get(String subject, long userId) {
        authorizationService.requireAdmin(subject);

        Long validatedUserId = CatalogInput.id(userId, "userId");

        User user = userRepository.findById(validatedUserId).orElseThrow(UserNotFoundException::new);

        return AdminUserResponse.from(user);
    }


    @Transactional
    public AdminUserResponse changeRole(String subject, Long userId, ChangeUserRoleRequest request) {
        User currentAdmin = authorizationService.requireAdmin(subject);
        Long validatedUserId = CatalogInput.id(userId, "userId");

        List<User> lockedAdmins = lockAndValidateActor(currentAdmin);
        User target = findLockedTarget(validatedUserId, lockedAdmins);

        UserRole requestedRole = request.role();

        if (target.getRole() == requestedRole) {
            return AdminUserResponse.from(target);
        }

        if (target.getRole() == UserRole.ADMIN && requestedRole == UserRole.USER) {
            validateAdminDemotion(currentAdmin, target,lockedAdmins);
        }

        target.changeRole(requestedRole);

        return AdminUserResponse.from(target);
    }


    @Transactional
    public AdminUserResponse changeStatus(String subject, Long userId, ChangeUserStatusRequest request) {
        User currentAdmin = authorizationService.requireAdmin(subject);
        Long validatedUserId = CatalogInput.id(userId, "userId");

        List<User> lockedAdmins = lockAndValidateActor(currentAdmin);
        User target = findLockedTarget(validatedUserId, lockedAdmins);

        if (target.getStatus() == request.status()) {
            return AdminUserResponse.from(target);
        }

        if (request.status() == UserStatus.SUSPENDED) {
            validateAdminRemovalFromActiveSet(currentAdmin, target, lockedAdmins);
            target.suspend(LocalDateTime.now());
            refreshTokenService.revokeAll(target.getId());

        } else {
            target.activate();
        }

        return AdminUserResponse.from(target);
    }


    @Transactional
    public void forceDelete(String subject, Long userId) {
        User currentAdmin = authorizationService.requireAdmin(subject);
        Long validatedUserId = CatalogInput.id(userId, "userId");

        List<User> lockedAdmins = lockAndValidateActor(currentAdmin);
        User target = findLockedTarget(validatedUserId, lockedAdmins);

        if (currentAdmin.getId().equals(target.getId())) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "ADMIN_SELF_DELETE_NOT_ALLOWED",
                    "관리자는 자신의 계정을 강제 삭제할 수 없습니다."
            );
        }

        validateAdminRemovalFromActiveSet(currentAdmin, target, lockedAdmins);

        refreshTokenService.revokeAll(target.getId());
        userDeletionService.deleteLocked(target.getId());
    }


    private void validateAdminDemotion(User currentAdmin, User target, List<User> lockedAdmins) {
        if (currentAdmin.getId().equals(target.getId())) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "ADMIN_SELF_DEMOTION_NOT_ALLOWED",
                    "관리자는 자신의 관리자 권한을 해제할 수 없습니다."
            );
        }

        if (target.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        long activeAdminCount = lockedAdmins.stream().filter(User::isActive).count();

        if (activeAdminCount <= 1) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "LAST_ADMIN_DEMOTION_NOT_ALLOWED",
                    "마지막 관리자의 권한은 해제할 수 없습니다."
            );
        }
    }


    private List<User> lockAndValidateActor(User currentAdmin) {
        List<User> lockedAdmins = userRepository.findAllAdminsForUpdate();

        boolean stillActiveAdmin = lockedAdmins
                .stream()
                .anyMatch(admin ->
                        admin.getId().equals(currentAdmin.getId()) && admin.getStatus() == UserStatus.ACTIVE
                );

        if (!stillActiveAdmin) {
            throw new CatalogException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "관리자 권한이 필요합니다."
            );
        }

        return lockedAdmins;
    }


    private User findLockedTarget(Long userId, List<User> lockedAdmins) {
        return lockedAdmins.stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst()
                .orElseGet(() -> userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new));
    }


    private void validateAdminRemovalFromActiveSet(User currentAdmin, User target, List<User> lockedAdmins) {
        if (target.getRole() != UserRole.ADMIN || target.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        if (currentAdmin.getId().equals(target.getId())) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "ADMIN_SELF_SUSPENSION_NOT_ALLOWED",
                    "관리자는 자신의 계정을 정지할 수 없습니다."
            );
        }

        long activeAdminCount = lockedAdmins.stream().filter(User::isActive).count();

        if (activeAdminCount <= 1) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "LAST_ADMIN_REMOVAL_NOT_ALLOWED",
                    "마지막 활성 관리자는 정지하거나 삭제할 수 없습니다."
            );
        }
    }
}
