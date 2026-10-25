package com.portfolio.admin_api.auth.service;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.auth.entity.UserExternalIdentity;
import com.portfolio.admin_api.auth.repository.UserExternalIdentityRepository;
import com.portfolio.admin_api.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalIdentityService {

	private final UserExternalIdentityRepository identityRepository;

	public User getLinkedUser(String issuer, String subject) {
		UserExternalIdentity identity = identityRepository.findByIssuerAndSubject(issuer, subject)
				.orElseThrow(() 
					-> new OAuth2AuthenticationException(
							new OAuth2Error("account_not_linked"), "서비스에 연결되지 않은 계정입니다."
					   )
				);

		return identity.getUser();
	}

}
