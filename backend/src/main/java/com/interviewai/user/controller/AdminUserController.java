package com.interviewai.user.controller;

import com.interviewai.user.dto.AdminUserPageResponse;
import com.interviewai.user.dto.AdminUserResponse;
import com.interviewai.user.dto.ChangeUserRoleRequest;
import com.interviewai.user.dto.ChangeUserStatusRequest;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.enums.UserStatus;
import com.interviewai.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;


    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }


    @GetMapping
    public AdminUserPageResponse search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AuthProvider provider,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminUserService.search(jwt.getSubject(), keyword, role, provider, status, page, size);
    }


    @GetMapping("/{userId}")
    public AdminUserResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return adminUserService.get(jwt.getSubject(), userId);
    }


    @PatchMapping("/{userId}/role")
    public AdminUserResponse changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeUserRoleRequest request
    ) {
        return adminUserService.changeRole(jwt.getSubject(), userId, request);
    }


    @PatchMapping("/{userId}/status")
    public AdminUserResponse changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeUserStatusRequest request
    ) {
        return adminUserService.changeStatus(jwt.getSubject(), userId, request);
    }


    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> forceDelete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId
    ) {
        adminUserService.forceDelete(jwt.getSubject(), userId);
        return ResponseEntity.noContent().build();
    }
}
