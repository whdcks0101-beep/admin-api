package com.portfolio.admin_api.user.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.user.dto.CreateUserRequest;
import com.portfolio.admin_api.user.dto.UpdateUserRequest;
import com.portfolio.admin_api.user.dto.UserResponse;
import com.portfolio.admin_api.user.entity.User;
import com.portfolio.admin_api.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public List<UserResponse> getUsers(String name) {
		List<User> users;

		if (name != null && !name.trim().isEmpty()) {
			users = userRepository.findByNameContaining(name);
		} else {
			users = userRepository.findAll();
		}

		return users.stream().map(UserResponse::from).toList();
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new IllegalStateException("이미 사용 중인 이메일입니다.");
		}

		String password = request.password();

		if (password == null || password.isBlank()) {
			throw new IllegalArgumentException("비밀번호는 필수입니다.");
		}

		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new IllegalArgumentException("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
		}

		String passwordHash = passwordEncoder.encode(password);

		User user = new User();
		user.setName(request.name());
		user.setEmail(request.email());
		user.setRole(request.role());
		user.setPasswordHash(passwordHash);

		User savedUser = userRepository.save(user);

		return UserResponse.from(savedUser);
	}

	@Transactional
	public UserResponse updateUser(Long id, UpdateUserRequest request, Long loginUserId) {
		if (loginUserId == null) {
			throw new IllegalStateException("로그인 사용자 정보를 확인할 수 없습니다.");
		}

		User user = userRepository.findById(id).orElseThrow(() -> new NoSuchElementException("해당 회원이 없습니다. id=" + id));

		if (id.equals(loginUserId) && "관리자".equals(user.getRole()) && !"관리자".equals(request.role())) {
			throw new IllegalStateException("자기 계정의 관리자 권한은 해제할 수 없습니다.");
		}

		if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
			throw new IllegalStateException("이미 사용 중인 이메일입니다.");
		}

		user.setName(request.name());
		user.setEmail(request.email());
		user.setRole(request.role());

		return UserResponse.from(user);
	}

	@Transactional
	public void deleteUser(Long id, Long loginUserId) {
		if (loginUserId == null) {
			throw new IllegalStateException("로그인 사용자 정보를 확인할 수 없습니다.");
		}

		if (id.equals(loginUserId)) {
			throw new IllegalStateException("자기 계정은 삭제할 수 없습니다.");
		}

		User user = userRepository.findById(id).orElseThrow(() -> new NoSuchElementException("해당 회원이 없습니다. id=" + id));

		userRepository.delete(user);
	}
	
	public UserResponse getUserProfile(Long userId) {
	    User user = userRepository.findById(userId)
	            .orElseThrow(() ->
	                    new NoSuchElementException(
	                            "해당 회원이 없습니다. id=" + userId
	                    )
	            );

	    return UserResponse.from(user);
	}

}
