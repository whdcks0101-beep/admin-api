package com.portfolio.admin_api.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.auth.model.LoginUser;
import com.portfolio.admin_api.auth.support.UserAuthorityMapper;
import com.portfolio.admin_api.user.entity.User;
import com.portfolio.admin_api.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		User user = userRepository.findByEmail(username)
				.orElseThrow(() -> new UsernameNotFoundException("로그인 정보를 확인해 주세요."));

		if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
			throw new UsernameNotFoundException("로그인 정보를 확인해 주세요.");
		}

		try {
		    return new LoginUser(
		            user.getId(),
		            user.getEmail(),
		            user.getPasswordHash(),
		            UserAuthorityMapper.toAuthorities(user.getRole())
		    );
		} catch (IllegalArgumentException e) {
		    throw new UsernameNotFoundException(
		            "로그인 정보를 확인해 주세요.",
		            e
		    );
		}
	}
}
