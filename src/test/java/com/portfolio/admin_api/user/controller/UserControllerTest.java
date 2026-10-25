package com.portfolio.admin_api.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.portfolio.admin_api.common.ApiExceptionHandler;
import com.portfolio.admin_api.user.dto.CreateUserRequest;
import com.portfolio.admin_api.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

	@Mock
	private UserService userService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
				.setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@Test
	@DisplayName("회원 등록 중 DB 제약 위반이 발생하면 409와 공통 오류 안내를 반환한다")
	void createUserReturnsConflictWhenDatabaseConstraintFails() throws Exception {

		when(userService.createUser(any(CreateUserRequest.class)))
				.thenThrow(new DataIntegrityViolationException("테스트용 DB 제약 위반"));

		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "name": "테스트 회원",
				  "email": "duplicate@example.com",
				  "role": "일반회원",
				  "password": "Test-password-1234"
				}
				""")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("데이터 제약 조건으로 요청을 처리할 수 없습니다. " + "중복 값이나 연결된 데이터를 확인해 주세요."));
	}
}
