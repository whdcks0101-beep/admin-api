package com.portfolio.admin_api.auth.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.portfolio.admin_api.auth.dto.CurrentUserResponse;
import com.portfolio.admin_api.auth.model.AuthenticatedUser;
import com.portfolio.admin_api.user.dto.UserResponse;
import com.portfolio.admin_api.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Tag(name = "인증", description = "현재 로그인 사용자 조회")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
	
	private final UserService userService;

	@Operation(summary = "현재 로그인 사용자 조회")
	@GetMapping("/me")
	public CurrentUserResponse getCurrentUser(Authentication authentication, @AuthenticationPrincipal AuthenticatedUser loginUser) {
		
	    if (loginUser == null) {
	        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
	    }		
	    
	    UserResponse profile = userService.getUserProfile(loginUser.getUserId());	    
		
	    List<String> authorities = authentication.getAuthorities()
	            .stream()
	            .map(GrantedAuthority::getAuthority)
	            .sorted()
	            .toList();

		return new CurrentUserResponse(profile.email(), authorities);
	}

	@Operation(summary = "CSRF 토큰 조회")
	@GetMapping("/csrf")
	public CsrfToken getCsrfToken(CsrfToken csrfToken) {
		return csrfToken;
	}

}
