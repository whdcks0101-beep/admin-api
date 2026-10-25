package com.portfolio.admin_api.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.portfolio.admin_api.config.PasswordConfig;
import com.portfolio.admin_api.user.dto.CreateUserRequest;
import com.portfolio.admin_api.user.dto.UpdateUserRequest;
import com.portfolio.admin_api.user.dto.UserResponse;
import com.portfolio.admin_api.user.entity.User;
import com.portfolio.admin_api.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	private PasswordEncoder passwordEncoder;
	private UserService userService;

	@BeforeEach
	void setUp() {
		passwordEncoder = new PasswordConfig().passwordEncoder();
		userService = new UserService(userRepository, passwordEncoder);
	}

	@Test
	@DisplayName("회원 등록 시 원문 대신 비밀번호 해시를 저장하고 비밀번호 일치 여부를 검증한다")
	void createUserStoresEncodedPassword() {
		// given
		String rawPassword = "Test-password-1234";

		CreateUserRequest request = new CreateUserRequest("테스트 회원", "password-test@example.com", "일반회원", rawPassword);

		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		userService.createUser(request);

		// then: 저장하려고 전달한 엔티티를 확인
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

		verify(userRepository).save(captor.capture());

		String storedHash = captor.getValue().getPasswordHash();

		assertNotNull(storedHash);
		assertNotEquals(rawPassword, storedHash);
		assertTrue(passwordEncoder.matches(rawPassword, storedHash));
		assertFalse(passwordEncoder.matches("Wrong-password-1234", storedHash));
	}

	@Test
	@DisplayName("자기 계정 삭제는 저장소 호출 없이 거부한다")
	void deleteOwnAccountThrowsException() {
		// given: 삭제 대상과 로그인한 회원이 동일
		Long userId = 8L;

		// when
		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> userService.deleteUser(userId, userId));

		// then
		assertEquals("자기 계정은 삭제할 수 없습니다.", exception.getMessage());

		verifyNoInteractions(userRepository);
	}

	@Test
	@DisplayName("다른 회원 계정은 조회 후 삭제한다")
	void deleteOtherAccountDeletesUser() {
		// given: 요청자와 삭제 대상이 서로 다름
		Long loginUserId = 8L;
		Long targetUserId = 9L;

		User targetUser = new User();
		targetUser.setId(targetUserId);

		when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

		// when
		userService.deleteUser(targetUserId, loginUserId);

		// then
		verify(userRepository).findById(targetUserId);
		verify(userRepository).delete(targetUser);
	}

	@Test
	@DisplayName("자기 관리자 권한 해제를 거부하고 기존 회원 정보를 유지한다")
	void updateOwnAccountCannotRemoveAdminRole() {
		// given
		Long loginUserId = 8L;

		User user = new User();
		user.setId(loginUserId);
		user.setName("기존 관리자");
		user.setEmail("admin-test@example.com");
		user.setRole("관리자");

		when(userRepository.findById(loginUserId)).thenReturn(Optional.of(user));

		UpdateUserRequest request = new UpdateUserRequest("변경한 이름", "admin-test@example.com", "일반회원");

		// when
		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> userService.updateUser(loginUserId, request, loginUserId));

		// then
		assertEquals("자기 계정의 관리자 권한은 해제할 수 없습니다.", exception.getMessage());

		assertEquals("관리자", user.getRole());
		assertEquals("기존 관리자", user.getName());

		verify(userRepository).findById(loginUserId);
		verifyNoMoreInteractions(userRepository);
	}
	
	@Test
	@DisplayName("자기 관리자 권한을 유지하면서 이름을 변경할 수 있다")
	void updateOwnAccountCanChangeNameWhileKeepingAdminRole() {
	    // given
	    Long loginUserId = 8L;
	    String email = "admin-test@example.com";

	    User user = new User();
	    user.setId(loginUserId);
	    user.setName("기존 관리자");
	    user.setEmail(email);
	    user.setRole("관리자");

	    when(userRepository.findById(loginUserId))
	            .thenReturn(Optional.of(user));

	    when(userRepository.existsByEmailAndIdNot(email, loginUserId))
	            .thenReturn(false);

	    UpdateUserRequest request = new UpdateUserRequest(
	            "변경한 관리자 이름",
	            email,
	            "관리자"
	    );

	    // when
	    UserResponse response = userService.updateUser(
	            loginUserId,
	            request,
	            loginUserId
	    );

	    // then
	    assertEquals("변경한 관리자 이름", user.getName());
	    assertEquals("관리자", user.getRole());

	    assertEquals(loginUserId, response.id());
	    assertEquals("변경한 관리자 이름", response.name());
	    assertEquals("관리자", response.role());

	    verify(userRepository).findById(loginUserId);
	    verify(userRepository)
	            .existsByEmailAndIdNot(email, loginUserId);
	    verifyNoMoreInteractions(userRepository);
	}

}
