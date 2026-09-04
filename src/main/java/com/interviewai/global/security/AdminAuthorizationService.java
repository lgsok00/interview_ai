package com.interviewai.global.security;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.global.error.CatalogException;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAuthorizationService {

    private final UserRepository users;


    public AdminAuthorizationService(UserRepository users) {
        this.users = users;
    }


    public User requireUser(String subject) {
        long userId;

        try {
            userId = Long.parseLong(subject);

            if (userId <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException exception) {
            throw new InvalidAccessTokenException();
        }

        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }


    public User requireAdmin(String subject) {
        User user = requireUser(subject);

        if (user.getRole() != UserRole.ADMIN) {
            throw new CatalogException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자 권한이 필요합니다.");
        }

        return user;
    }
}
