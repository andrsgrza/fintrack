package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.User;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserService currentUserService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserLoginShouldReturnAuthenticatedLogin() {
        authenticate("user", AuthoritiesConstants.USER);

        assertThat(currentUserService.getCurrentUserLogin()).isEqualTo("user");
    }

    @Test
    void getCurrentUserLoginShouldFailWhenUnauthenticated() {
        assertThatThrownBy(() -> currentUserService.getCurrentUserLogin()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getCurrentUserShouldResolveUserFromRepository() {
        User user = new User();
        user.setId(2L);
        user.setLogin("user");

        authenticate("user", AuthoritiesConstants.USER);
        when(userRepository.findOneByLogin("user")).thenReturn(Optional.of(user));

        assertThat(currentUserService.getCurrentUser()).isEqualTo(user);
    }

    @Test
    void isAdminShouldReturnTrueForAdminAuthority() {
        authenticate("admin", AuthoritiesConstants.ADMIN, AuthoritiesConstants.USER);

        assertThat(currentUserService.isAdmin()).isTrue();
    }

    @Test
    void isAdminShouldReturnFalseForRegularUser() {
        authenticate("user", AuthoritiesConstants.USER);

        assertThat(currentUserService.isAdmin()).isFalse();
    }

    private void authenticate(String login, String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(login, "password", grantedAuthorities));
    }
}
