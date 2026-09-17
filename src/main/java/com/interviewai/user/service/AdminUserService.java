package com.interviewai.user.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.user.dto.AdminUserPageResponse;
import com.interviewai.user.dto.AdminUserResponse;
import com.interviewai.user.dto.ChangeUserRoleRequest;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminAuthorizationService authorizationService;


    public AdminUserService(UserRepository userRepository, AdminAuthorizationService authorizationService) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }


    public AdminUserPageResponse search(
            String subject,
            String keyword,
            UserRole role,
            AuthProvider provider,
            int page,
            int size
    ) {
        authorizationService.requireAdmin(subject);

        String pattern = CatalogInput.pattern(keyword);
        PageRequest pageable = CatalogInput.page(page, size);

        return AdminUserPageResponse.from(
                userRepository.searchForAdmin(pattern, role, provider, pageable)
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

        List<User> lockedAdmins = userRepository.findAllAdminsForUpdate();

        boolean stillAdmin = lockedAdmins.stream()
                .anyMatch(admin -> admin.getId().equals(currentAdmin.getId()));

        if (!stillAdmin) {
            throw new CatalogException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "관리자 권한이 필요합니다."
            );
        }

        User target = lockedAdmins.stream()
                .filter(user -> user.getId().equals(validatedUserId))
                .findFirst()
                .orElseGet(() -> userRepository
                        .findByIdForUpdate(validatedUserId).orElseThrow(UserNotFoundException::new)
                );

        UserRole requestedRole = request.role();

        if (target.getRole() == requestedRole) {
            return AdminUserResponse.from(target);
        }

        if (target.getRole() == UserRole.ADMIN && requestedRole == UserRole.USER) {
            validateAdminDemotion(currentAdmin, target, lockedAdmins.size());
        }

        target.changeRole(requestedRole);

        return AdminUserResponse.from(target);
    }


    private void validateAdminDemotion(User currentAdmin, User target, int adminCount) {
        if (currentAdmin.getId().equals(target.getId())) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "ADMIN_SELF_DEMOTION_NOT_ALLOWED",
                    "관리자는 자신의 관리자 권한을 해제할 수 없습니다."
            );
        }

        if (adminCount <= 1) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "LAST_ADMIN_DEMOTION_NOT_ALLOWED",
                    "마지막 관리자의 권한은 해제할 수 없습니다."
            );
        }
    }
}
