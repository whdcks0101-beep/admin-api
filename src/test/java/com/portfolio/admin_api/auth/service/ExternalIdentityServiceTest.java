package com.portfolio.admin_api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import com.portfolio.admin_api.auth.entity.UserExternalIdentity;
import com.portfolio.admin_api.auth.repository.UserExternalIdentityRepository;
import com.portfolio.admin_api.user.entity.User;

@ExtendWith(MockitoExtension.class)
class ExternalIdentityServiceTest {

	  @Mock
	    private UserExternalIdentityRepository identityRepository;

	    private ExternalIdentityService externalIdentityService;

	    @BeforeEach
	    void setUp() {
	        externalIdentityService =
	                new ExternalIdentityService(identityRepository);
	    }

	    @Test
	    @DisplayName("인증 서버와 사용자 식별자에 연결된 우리 회원을 반환한다")
	    void getLinkedUserReturnsMappedUser() {
	        // given
	        String issuer = "http://localhost:8180/realms/portfolio";
	        String subject = "test-subject";

	        User user = new User();
	        user.setId(9L);
	        user.setEmail("linked@example.com");
	        user.setRole("일반회원");

	        UserExternalIdentity identity =
	                new UserExternalIdentity(user, issuer, subject);

	        when(identityRepository.findByIssuerAndSubject(issuer, subject))
	                .thenReturn(Optional.of(identity));

	        // when
	        User result = externalIdentityService.getLinkedUser(
	                issuer, subject
	        );

	        // then
	        assertThat(result).isSameAs(user);

	        verify(identityRepository)
	                .findByIssuerAndSubject(issuer, subject);
	    }

	    @Test
	    @DisplayName("우리 회원과 연결되지 않은 SSO 계정은 인증 실패로 처리한다")
	    void getLinkedUserRejectsUnlinkedAccount() {
	        // given
	        String issuer = "http://localhost:8180/realms/portfolio";
	        String subject = "unlinked-subject";

	        when(identityRepository.findByIssuerAndSubject(issuer, subject))
	                .thenReturn(Optional.empty());

	        // when
	        OAuth2AuthenticationException exception = assertThrows(
	                OAuth2AuthenticationException.class,
	                () -> externalIdentityService.getLinkedUser(
	                        issuer, subject
	                )
	        );

	        // then
	        assertThat(exception.getError().getErrorCode())
	                .isEqualTo("account_not_linked");

	        assertThat(exception.getMessage())
	                .isEqualTo("서비스에 연결되지 않은 계정입니다.");

	        verify(identityRepository)
	                .findByIssuerAndSubject(issuer, subject);
	    }
    
}
