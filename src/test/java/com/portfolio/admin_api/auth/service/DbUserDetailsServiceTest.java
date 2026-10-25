package com.portfolio.admin_api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.portfolio.admin_api.user.entity.User;
import com.portfolio.admin_api.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DbUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private DbUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new DbUserDetailsService(userRepository);
    }

	/*
	 * 1. 입력한 이메일로 Repository를 조회하는지. 
	 * 2. Security의 사용자 이름에 이메일을 넣는지. 
	 * 3. 저장된 해시를 다시 변환하지 않고 그대로 전달하는지.
	 * 4. 관리자에게 ROLE_ADMIN, ROLE_USER를 부여하는지.
	 */
    
    @Test
    @DisplayName("관리자 이메일 조회 시 저장된 비밀번호 해시와 관리자·사용자 권한을 반환한다")
    void loadAdminReturnsStoredPasswordAndRoles() {
        // given
        String email = "admin-test@example.com";
        String storedHash = "{bcrypt}test-placeholder";

        User user = new User();
        user.setName("테스트 관리자");
        user.setEmail(email);
        user.setRole("관리자");
        user.setPasswordHash(storedHash);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        // when
        UserDetails result =
                userDetailsService.loadUserByUsername(email);

        // then
        assertThat(result.getUsername()).isEqualTo(email);
        assertThat(result.getPassword()).isEqualTo(storedHash);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");

        verify(userRepository).findByEmail(email);
    }
    
    @Test
    @DisplayName("비밀번호가 없는 회원의 이메일 로그인을 거부한다")
    void loadUserWithoutPasswordThrowsException() {
        // given: 비밀번호를 설정하지 않은 기존 회원
        String email = "legacy-user@example.com";

        User user = new User();
        user.setName("기존 회원");
        user.setEmail(email);
        user.setRole("일반회원");
        user.setPasswordHash(null);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(
                () -> userDetailsService.loadUserByUsername(email)
        )
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("로그인 정보를 확인해 주세요.");

        verify(userRepository).findByEmail(email);
    }
    
}
