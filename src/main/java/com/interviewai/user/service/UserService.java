package com.interviewai.user.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.user.dto.CurrentUserResponse;
import com.interviewai.user.entity.User;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;


    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    public CurrentUserResponse getCurrentUser(String subject) {
        Long userId = parseUserId(subject);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        return CurrentUserResponse.from(user);
    }


    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);

        } catch (NumberFormatException exception) {
            throw new InvalidAccessTokenException();
        }
    }
}
