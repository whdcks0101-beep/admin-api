package com.portfolio.admin_api.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

import com.portfolio.admin_api.auth.model.AuthenticatedUser;
import com.portfolio.admin_api.common.ratelimit.RedisRateLimiter;
import com.portfolio.admin_api.common.ratelimit.RateLimitResult;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.portfolio.admin_api.common.ApiExceptionHandler;
import com.portfolio.admin_api.order.application.command.CreateOrderCommand;
import com.portfolio.admin_api.order.service.OrderService;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

	@Mock
	private OrderService orderService;

	@Mock
	private RedisRateLimiter redisRateLimiter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		// AuthenticatedUser의 getUserId()가 8L을 반환하도록 구성
		AuthenticatedUser loginUser = () -> 8L;

		var authentication = new UsernamePasswordAuthenticationToken(loginUser, null, List.of());

		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService, redisRateLimiter))
				.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
				.setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("주문 중 재고 수정 충돌이 발생하면 409와 재시도 안내를 반환한다")
	void createOrderReturnsConflictWhenStockUpdateCollides() throws Exception {

		when(redisRateLimiter.tryAcquire("admin-api:rate-limit:orders:user:8", 5, 60))
				.thenReturn(new RateLimitResult(true, 4, 0));

		// given: 주문 서비스에서 충돌 예외가 발생하도록 설정
		when(orderService.createOrder(any(CreateOrderCommand.class)))
				.thenThrow(new OptimisticLockingFailureException("테스트용 재고 충돌"));

		// when & then: 요청 후 HTTP 응답 검증
		mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "items": [
				    {
				      "productId": 1,
				      "quantity": 2
				    }
				  ]
				}
				""")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("다른 요청이 재고를 변경했습니다. 다시 시도해 주세요."));
	}

	@Test
	@DisplayName("주문 요청 한도를 초과하면 429와 대기 시간을 반환하고 주문 서비스를 호출하지 않는다")
	void createOrderReturnsTooManyRequests() throws Exception {
		when(redisRateLimiter.tryAcquire("admin-api:rate-limit:orders:user:8", 5, 60))
				.thenReturn(new RateLimitResult(false, 0, 23));

		mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "items": [
				    {
				      "productId": 1,
				      "quantity": 2
				    }
				  ]
				}
				""")).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "23"))
				.andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));

		verify(redisRateLimiter).tryAcquire("admin-api:rate-limit:orders:user:8", 5, 60);

		verifyNoInteractions(orderService);
	}

	@Test
	@DisplayName("요청 제한용 Redis 연결이 실패하면 503을 반환하고 주문 서비스를 호출하지 않는다")
	void createOrderReturnsServiceUnavailableWhenRedisFails() throws Exception {

		when(redisRateLimiter.tryAcquire("admin-api:rate-limit:orders:user:8", 5, 60))
				.thenThrow(new RedisConnectionFailureException("테스트용 Redis 연결 실패"));

		mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "items": [
				    {
				      "productId": 1,
				      "quantity": 2
				    }
				  ]
				}
				""")).andExpect(status().isServiceUnavailable());

		verify(redisRateLimiter).tryAcquire("admin-api:rate-limit:orders:user:8", 5, 60);

		verifyNoInteractions(orderService);
	}

}
