package com.interviewai.user.controller;

import com.interviewai.user.dto.ChangePasswordRequest;
import com.interviewai.user.dto.CurrentUserResponse;
import com.interviewai.user.dto.UpdateUserRequest;
import com.interviewai.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;


    public UserController(UserService userService) {
        this.userService = userService;
    }


    @GetMapping("/me")
    public CurrentUserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.getCurrentUser(jwt.getSubject());
    }


    @PutMapping("/me")
    public CurrentUserResponse updateCurrentUser(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.updateCurrentUser(jwt.getSubject(), request);
    }


    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(jwt.getSubject(), request);
    }


    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        userService.deleteCurrentUser(jwt.getSubject());
    }
}
