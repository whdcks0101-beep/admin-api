package com.portfolio.admin_api.auth.model;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class LoginUser extends User implements AuthenticatedUser{

	private static final long serialVersionUID = 1L;

	private final Long userId;

	public LoginUser(Long userId, String email, String passwordHash,
			Collection<? extends GrantedAuthority> authorities) {
		super(email, passwordHash, authorities);
		this.userId = userId;
	}

	@Override
	public Long getUserId() {
		return userId;
	}

}
