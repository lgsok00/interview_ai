package com.interviewai.user.controller;

import com.interviewai.user.dto.CurrentUserResponse;
import com.interviewai.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
