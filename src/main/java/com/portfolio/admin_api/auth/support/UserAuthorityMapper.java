package com.portfolio.admin_api.auth.support;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class UserAuthorityMapper {

	private UserAuthorityMapper() {
	}

	public static List<GrantedAuthority> toAuthorities(String role) {
		if (role == null || role.isBlank()) {
			throw new IllegalArgumentException("회원 권한이 비어 있습니다.");
		}

		return switch (role) {
		case "관리자" -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));
		case "체험관리자" -> List.of(new SimpleGrantedAuthority("ROLE_DEMO_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));

		case "일반회원", "우수회원" -> List.of(new SimpleGrantedAuthority("ROLE_USER"));

		default -> throw new IllegalArgumentException("지원하지 않는 회원 권한입니다.");
		};
	}
}
