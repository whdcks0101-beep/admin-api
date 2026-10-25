package com.portfolio.admin_api.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.portfolio.admin_api.auth.model.AuthenticatedUser;
import com.portfolio.admin_api.auth.model.LoginUser;
import com.portfolio.admin_api.user.dto.CreateUserRequest;
import com.portfolio.admin_api.user.dto.UpdateUserRequest;
import com.portfolio.admin_api.user.dto.UserResponse;
import com.portfolio.admin_api.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping
	public List<UserResponse> getUser(@RequestParam(value = "name", required = false) String name) {
		return userService.getUsers(name);
	}

	@PostMapping
	public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
		return userService.createUser(request);
	}

	@DeleteMapping("/{id}")
	public void deleteUser(@PathVariable("id") Long id,  @AuthenticationPrincipal AuthenticatedUser loginUser) {
	    if (loginUser == null) {
	        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
	    }		
		
		userService.deleteUser(id,loginUser.getUserId());
	}

	@PutMapping("/{id}")
	public UserResponse updateUser(@PathVariable("id") Long id, @Valid @RequestBody UpdateUserRequest request, @AuthenticationPrincipal AuthenticatedUser loginUser) {

	    if (loginUser == null) {
	        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
	    }
		
		return userService.updateUser(id, request ,loginUser.getUserId());
	}
}
