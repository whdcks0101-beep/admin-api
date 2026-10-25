package com.portfolio.admin_api.order.controller;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.portfolio.admin_api.auth.model.AuthenticatedUser;
import com.portfolio.admin_api.common.ApiExceptionHandler.ApiErrorResponse;
import com.portfolio.admin_api.common.dto.PageResponse;
import com.portfolio.admin_api.common.ratelimit.RateLimitExceededException;
import com.portfolio.admin_api.common.ratelimit.RateLimitResult;
import com.portfolio.admin_api.common.ratelimit.RedisRateLimiter;
import com.portfolio.admin_api.order.dto.CreateOrderRequest;
import com.portfolio.admin_api.order.dto.OrderDetailResponse;
import com.portfolio.admin_api.order.dto.OrderResponse;
import com.portfolio.admin_api.order.dto.OrderSearchRequest;
import com.portfolio.admin_api.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "주문", description = "주문 생성 및 목록 조회 API")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;
	private final RedisRateLimiter redisRateLimiter;

	@Operation(summary = "주문 생성", description = "주문과 주문 항목을 저장하고 재고를 차감합니다. " + "상품 중 하나라도 재고가 부족하면 전체 작업을 롤백합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "주문 생성 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
			@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 중복 상품", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "요청한 상품의 재고 정보를 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "재고 부족 또는 동시 재고 변경 충돌", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))) })
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request,
			@AuthenticationPrincipal AuthenticatedUser loginUser) {
		if (loginUser == null || loginUser.getUserId() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		String key = "admin-api:rate-limit:orders:user:" + loginUser.getUserId();
		RateLimitResult result;

		try {
			result = redisRateLimiter.tryAcquire(key, 5, 60);
		} catch (RedisConnectionFailureException | QueryTimeoutException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "요청 제한 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.",
					exception);
		}

		if (!result.allowed()) {
			throw new RateLimitExceededException(result.retryAfterSeconds());
		}

		return OrderResponse.from(orderService.createOrder(request.toCommand()));
	}

	@Operation(summary = "주문 목록 조회", description = "주문 번호 내림차순으로 조회합니다. "
			+ "page는 0부터 시작하며, size는 1부터 100까지 지정할 수 있습니다.")
	@GetMapping
	public PageResponse<OrderResponse> getOrders(@Valid @ModelAttribute OrderSearchRequest request) {
		return orderService.getOrders(request);
	}

	@Operation(summary = "주문 상세 조회", description = "주문 번호로 주문 정보와 상품별 주문 수량을 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDetailResponse.class))),
			@ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))) })
	@GetMapping("/{orderId}")
	public OrderDetailResponse getOrder(@PathVariable("orderId") Long orderId) {
		return orderService.getOrder(orderId);
	}

}
