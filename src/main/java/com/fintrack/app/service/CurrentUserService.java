package com.fintrack.app.service;

import com.fintrack.app.domain.User;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the authenticated application user and common authorization helpers.
 */
@Service
@Transactional(readOnly = true)
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .orElseThrow(() -> new IllegalStateException("Current user not found"));
    }

    public String getCurrentUserLogin() {
        return SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new IllegalStateException("Current user is not authenticated"));
    }

    public boolean isAdmin() {
        return SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
    }
}
