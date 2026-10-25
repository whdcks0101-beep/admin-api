package com.portfolio.admin_api.order.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.security.core.authority.AuthorityUtils;

import com.portfolio.admin_api.auth.model.LoginUser;
import com.portfolio.admin_api.common.ratelimit.RedisRateLimiter;
import com.portfolio.admin_api.common.ratelimit.RateLimitResult;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.portfolio.admin_api.config.SecurityConfig;
import com.portfolio.admin_api.order.service.OrderService;
import com.portfolio.admin_api.security.handler.ApiAccessDeniedHandler;
import com.portfolio.admin_api.security.handler.ApiAuthenticationEntryPoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDateTime;
import java.util.List;

import com.portfolio.admin_api.order.application.command.CreateOrderCommand;
import com.portfolio.admin_api.order.application.result.CreateOrderResult;

@WebMvcTest(OrderController.class)
@ActiveProfiles("test")
@Import({ SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class })
class OrderSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OrderService orderService;

	@MockitoBean
	private RedisRateLimiter redisRateLimiter;

	@Test
	@DisplayName("CSRF 토큰 없는 주문 생성은 403으로 차단하고 서비스를 호출하지 않는다")
	void createOrderWithoutCsrfReturnsForbidden() throws Exception {
		mockMvc.perform(post("/api/orders").with(user(adminUser())).contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "items": [
				    {
				      "productId": 1,
				      "quantity": 2
				    }
				  ]
				}
				""")).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("요청이 허용되지 않습니다."));

		verifyNoInteractions(orderService, redisRateLimiter);
	}

	@Test
	@DisplayName("관리자가 유효한 CSRF 토큰으로 주문하면 201과 주문 번호를 반환한다")
	void createOrderWithCsrfReturnsCreated() throws Exception {
		when(redisRateLimiter.tryAcquire("admin-api:rate-limit:orders:user:8", 5, 60))
				.thenReturn(new RateLimitResult(true, 4, 0));

		// given: 주문 서비스가 반환할 테스트 응답
		when(orderService.createOrder(any(CreateOrderCommand.class)))
				.thenReturn(new CreateOrderResult(100L, LocalDateTime.of(2026, 10, 14, 12, 0)));

		// when & then: 로그인 상태 + 유효한 CSRF 토큰
		mockMvc.perform(post("/api/orders").with(user(adminUser())).with(csrf().asHeader())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "items": [
						    {
						      "productId": 1,
						      "quantity": 2
						    }
						  ]
						}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.orderId").value(100))
				.andExpect(jsonPath("$.createdAt").value("2026-10-14T12:00:00"));

		verify(orderService).createOrder(
				new CreateOrderCommand(List.of(new CreateOrderCommand.Item(1L, 2))));
	}

	@Test
	@DisplayName("잘못된 CSRF 토큰의 주문 생성은 403으로 차단하고 서비스를 호출하지 않는다")
	void createOrderWithInvalidCsrfReturnsForbidden() throws Exception {
		mockMvc.perform(post("/api/orders").with(user(adminUser())).with(csrf().asHeader().useInvalidToken())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "items": [
						    {
						      "productId": 1,
						      "quantity": 2
						    }
						  ]
						}
						""")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("요청이 허용되지 않습니다."));

		verifyNoInteractions(orderService, redisRateLimiter);
	}

	@Test
	@DisplayName("비로그인 주문 조회는 401로 차단하고 서비스를 호출하지 않는다")
	void getOrdersWithoutLoginReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

		verifyNoInteractions(orderService, redisRateLimiter);
	}

	@Test
	@DisplayName("USER 권한 없는 주문 조회는 403으로 차단하고 서비스를 호출하지 않는다")
	void getOrdersWithoutRequiredRoleReturnsForbidden() throws Exception {
		mockMvc.perform(get("/api/orders").with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("요청이 허용되지 않습니다."));

		verifyNoInteractions(orderService, redisRateLimiter);
	}

	@Test
	@DisplayName("USER 권한과 유효한 CSRF 토큰으로 주문하면 201과 주문 번호를 반환한다")
	void createOrderWithUserRoleReturnsCreated() throws Exception {

		when(redisRateLimiter.tryAcquire("admin-api:rate-limit:orders:user:9", 5, 60))
				.thenReturn(new RateLimitResult(true, 4, 0));

		when(orderService.createOrder(any(CreateOrderCommand.class)))
				.thenReturn(new CreateOrderResult(101L, LocalDateTime.of(2026, 10, 14, 12, 0)));

		mockMvc.perform(post("/api/orders").with(user(normalUser())).with(csrf().asHeader())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "items": [
						    {
						      "productId": 1,
						      "quantity": 2
						    }
						  ]
						}
						""")).andExpect(status().isCreated()).andExpect(jsonPath("$.orderId").value(101))
				.andExpect(jsonPath("$.createdAt").value("2026-10-14T12:00:00"));

		verify(orderService).createOrder(
				new CreateOrderCommand(List.of(new CreateOrderCommand.Item(1L, 2))));
	}

	private LoginUser adminUser() {
		return new LoginUser(8L, "admin@example.com", "unused-test-password",
				AuthorityUtils.createAuthorityList("ROLE_ADMIN", "ROLE_USER"));
	}

	private LoginUser normalUser() {
		return new LoginUser(9L, "user@example.com", "unused-test-password",
				AuthorityUtils.createAuthorityList("ROLE_USER"));
	}

}
