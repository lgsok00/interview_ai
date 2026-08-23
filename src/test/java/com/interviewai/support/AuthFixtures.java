package com.interviewai.support;

import com.interviewai.auth.dto.LoginRequest;
import com.interviewai.auth.dto.SignupRequest;
import com.interviewai.user.entity.User;

public final class AuthFixtures {

    public static final String EMAIL = "user@example.com";
    public static final String RAW_PASSWORD = "password123";
    public static final String ENCODED_PASSWORD = "{bcrypt}encoded-password";
    public static final String NICKNAME = "테스트유저";


    private AuthFixtures() {
    }


    public static SignupRequest signupRequest() {
        return new SignupRequest(EMAIL, RAW_PASSWORD, NICKNAME);
    }


    public static LoginRequest loginRequest() {
        return new LoginRequest(EMAIL, RAW_PASSWORD);
    }


    public static User localUser() {
        return User.createLocalUser(EMAIL, ENCODED_PASSWORD, NICKNAME);
    }
}
