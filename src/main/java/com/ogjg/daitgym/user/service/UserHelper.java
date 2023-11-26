package com.ogjg.daitgym.user.service;

import com.ogjg.daitgym.common.exception.user.NotFoundUser;
import com.ogjg.daitgym.domain.User;
import com.ogjg.daitgym.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserHelper {

    private final UserRepository userRepository;

    public User findUserByNickname(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(NotFoundUser::new);
    }

    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(NotFoundUser::new);
    }

    public boolean isUserNotFoundByEmail(String loginEmail) {
        return userRepository.findByEmail(loginEmail).isEmpty();
    }

    public boolean isNicknameAlreadyExist(String loginNickname, String newNickname) {
        return isNotMyNickname(loginNickname, newNickname) && userRepository.findByNickname(newNickname).isPresent();
    }

    private boolean isNotMyNickname(String loginNickname, String newNickname) {
        return !loginNickname.equals(newNickname);
    }
}
