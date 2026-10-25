package com.portfolio.admin_api.auth.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.portfolio.admin_api.auth.model.OidcLoginUser;
import com.portfolio.admin_api.auth.support.UserAuthorityMapper;
import com.portfolio.admin_api.user.entity.User;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PortfolioOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final ExternalIdentityService externalIdentityService;

	private final OidcUserService delegate = new OidcUserService();

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

		OidcUser oidcUser = delegate.loadUser(userRequest);

		String issuer = oidcUser.getIdToken().getIssuer().toString();
		String subject = oidcUser.getIdToken().getSubject();

		User user = externalIdentityService.getLinkedUser(issuer, subject);

		Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());

		try {
		    authorities.addAll(
		            UserAuthorityMapper.toAuthorities(user.getRole())
		    );
		} catch (IllegalArgumentException e) {
		    throw new OAuth2AuthenticationException(
		            new OAuth2Error("invalid_service_role"),
		            "서비스 권한을 확인할 수 없습니다.",
		            e
		    );
		}

		return new OidcLoginUser(user.getId(), authorities, oidcUser);
	}

}
