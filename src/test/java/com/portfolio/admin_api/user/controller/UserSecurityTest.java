package com.portfolio.admin_api.user.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.portfolio.admin_api.config.SecurityConfig;
import com.portfolio.admin_api.security.handler.ApiAccessDeniedHandler;
import com.portfolio.admin_api.security.handler.ApiAuthenticationEntryPoint;
import com.portfolio.admin_api.user.dto.UserResponse;
import com.portfolio.admin_api.user.service.UserService;

@WebMvcTest(UserController.class)
@ActiveProfiles("test")
@Import({ SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class })
class UserSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	@DisplayName("비로그인 회원 목록 조회는 401로 차단하고 서비스를 호출하지 않는다")
	void getUsersWithoutLoginReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("일반 사용자 권한의 회원 목록 조회는 403으로 차단한다")
	void getUsersWithUserRoleReturnsForbidden() throws Exception {
		mockMvc.perform(get("/api/users").with(user("user").roles("USER"))).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("요청이 허용되지 않습니다."));

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("관리자 권한으로 회원 목록을 조회하면 200과 회원 정보를 반환한다")
	void getUsersWithAdminRoleReturnsOk() throws Exception {
		when(userService.getUsers(null))
				.thenReturn(List.of(new UserResponse(1L, "테스트 회원", "test@example.com", "일반회원")));

		mockMvc.perform(get("/api/users").with(user("admin").roles("ADMIN", "USER"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].email").value("test@example.com"));

		verify(userService).getUsers(null);
	}
}
