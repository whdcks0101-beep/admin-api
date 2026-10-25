package com.portfolio.admin_api.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.portfolio.admin_api.auth.model.AuthenticatedUser;
import com.portfolio.admin_api.common.ApiExceptionHandler;
import com.portfolio.admin_api.common.ratelimit.RedisRateLimiter;
import com.portfolio.admin_api.order.application.command.CreateOrderCommand;
import com.portfolio.admin_api.order.application.result.CreateOrderResult;
import com.portfolio.admin_api.order.service.OrderService;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class OrderRateLimitIntegrationTest {

	@Test
	@DisplayName("실제 Redis로 주문 요청을 제한하여 5회 허용하고 6번째는 429를 반환한다")
	void sixthRequestIsRejected() throws Exception {
		var connectionFactory = new LettuceConnectionFactory("localhost", 6379);

		try {
			connectionFactory.afterPropertiesSet();
			connectionFactory.start();

			var redisTemplate = new StringRedisTemplate(connectionFactory);

			var limiter = new RedisRateLimiter(redisTemplate);

			OrderService orderService = mock(OrderService.class);

			when(orderService.createOrder(any(CreateOrderCommand.class)))
					.thenReturn(new CreateOrderResult(100L, LocalDateTime.now()));

			// 실제 회원의 양수 ID와 구분하기 위한 테스트 전용 음수 ID
			long userId = -ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

			AuthenticatedUser loginUser = () -> userId;

			var authentication = new UsernamePasswordAuthenticationToken(loginUser, null, List.of());

			var securityContext = SecurityContextHolder.createEmptyContext();

			securityContext.setAuthentication(authentication);
			SecurityContextHolder.setContext(securityContext);

			var mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService, limiter))
					.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
					.setControllerAdvice(new ApiExceptionHandler()).build();

			String requestBody = """
					{
					  "items": [
					    {
					      "productId": 1,
					      "quantity": 1
					    }
					  ]
					}
					""";

			for (int i = 0; i < 5; i++) {
				mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(requestBody))
						.andExpect(status().isCreated());
			}

			mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(requestBody))
					.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

			// 요청은 6번이지만 주문 서비스는 5번만 실행
			verify(orderService, times(5)).createOrder(
					new CreateOrderCommand(List.of(new CreateOrderCommand.Item(1L, 1))));

		} finally {
			SecurityContextHolder.clearContext();
			connectionFactory.destroy();
		}
	}
}
