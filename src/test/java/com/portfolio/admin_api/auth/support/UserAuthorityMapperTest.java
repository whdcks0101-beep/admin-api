package com.portfolio.admin_api.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;

@ExtendWith(MockitoExtension.class)
class UserAuthorityMapperTest {

	@Test
    @DisplayName("관리자는 관리자와 사용자 권한을 가진다")
    void 관리자는_관리자와_사용자_권한을_가진다() {
        var authorities =
                UserAuthorityMapper.toAuthorities("관리자");

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_ADMIN",
                        "ROLE_USER"
                );
    }

    @Test
    @DisplayName("일반회원과 우수회원은 사용자 권한만 가진다")
    void 일반회원과_우수회원은_사용자_권한만_가진다() {
        for (String role : new String[]{"일반회원", "우수회원"}) {
            var authorities =
                    UserAuthorityMapper.toAuthorities(role);

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_USER");
        }
    }

    @Test
    @DisplayName("등록되지 않은 회원 구분은 권한 변환을 거부한다")
    void 알수없는_회원구분은_거부한다() {
        assertThatThrownBy(
                () -> UserAuthorityMapper.toAuthorities("미등록등급")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("회원 구분이 null이거나 빈 문자열 또는 공백이면 거부한다")
    void 비어있는_회원구분은_거부한다() {
        for (String role : new String[]{null, "", " "}) {
            assertThatThrownBy(
                    () -> UserAuthorityMapper.toAuthorities(role)
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }
    
}
