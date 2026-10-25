package com.portfolio.admin_api.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.admin_api.order.dto.OrderSearchRequest;
import com.portfolio.admin_api.order.entity.Order;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderQueryRepositoryTest {
	
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderQueryRepository orderQueryRepository;
    
    @Test
    @DisplayName("주문 번호로 검색하면 일치하는 주문만 반환한다")
    void searchByOrderIdReturnsOnlyMatchingOrder() {
    	 // given: 주문 두 개와 검색 조건 준비
        Order target = orderRepository.save(Order.create());
        orderRepository.save(Order.create());

        OrderSearchRequest request = new OrderSearchRequest();
        request.setOrderId(target.getId());
        
        // when: 첫 번째 주문 번호로 검색
        Page<Order> result = orderQueryRepository.search(
                request,
                PageRequest.of(0, 10)
        );        
        
        // then: 해당 주문 하나만 반환되는지 검증
        assertThat(result.getContent())
                .extracting(Order::getId)
                .containsExactly(target.getId());

        assertThat(result.getTotalElements()).isEqualTo(1L);
    	
    }
    
    @Test
    @DisplayName("검색 조건이 없으면 주문 번호 내림차순으로 페이징하고 전체 건수를 반환한다")
    void searchWithoutConditionsReturnsOrdersWithPagination() {
        // given: 주문 세 개, 검색 조건 없음
        Order first = orderRepository.save(Order.create());
        Order second = orderRepository.save(Order.create());
        Order third = orderRepository.save(Order.create());

        OrderSearchRequest request = new OrderSearchRequest();

        // when: 첫 페이지를 두 건씩 조회
        Page<Order> firstPage = orderQueryRepository.search(
                request,
                PageRequest.of(0, 2)
        );

        // then: 주문 번호 내림차순으로 두 건 반환
        assertThat(firstPage.getContent())
                .extracting(Order::getId)
                .containsExactly(third.getId(), second.getId());

        assertThat(firstPage.getTotalElements()).isEqualTo(3L);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        // when: 두 번째 페이지 조회
        Page<Order> secondPage = orderQueryRepository.search(
                request,
                PageRequest.of(1, 2)
        );

        // then: 남은 한 건 반환
        assertThat(secondPage.getContent())
                .extracting(Order::getId)
                .containsExactly(first.getId());

        assertThat(secondPage.getTotalElements()).isEqualTo(3L);
    }    
    
    private Order saveOrderAt(String createdAt) {
        Order order = Order.create();

        ReflectionTestUtils.setField(
                order,
                "createdAt",
                LocalDateTime.parse(createdAt)
        );

        return orderRepository.save(order);
    }
    
    @Test
    @DisplayName("기간 검색은 시작일과 종료일의 주문을 포함하고 기간 밖 주문은 제외한다")
    void searchByDateRangeIncludesBothDatesButExcludesOutsideRange() {
        // given: 검색 범위 안팎의 주문 네 개
        saveOrderAt("2026-10-10T23:59:59");

        Order startOrder = saveOrderAt("2026-10-11T00:00:00");
        Order endOrder = saveOrderAt("2026-10-12T23:59:59");

        saveOrderAt("2026-10-13T00:00:00");

        OrderSearchRequest request = new OrderSearchRequest();
        request.setFromDate(LocalDate.of(2026, 10, 11));
        request.setToDate(LocalDate.of(2026, 10, 12));

        // when: 10월 11일부터 12일까지 검색
        Page<Order> result = orderQueryRepository.search(
                request,
                PageRequest.of(0, 10)
        );

        // then: 범위 안의 두 주문만 번호 내림차순으로 반환
        assertThat(result.getContent())
                .extracting(Order::getId)
                .containsExactly(endOrder.getId(), startOrder.getId());

        assertThat(result.getTotalElements()).isEqualTo(2L);
    }
    
    @Test
    @DisplayName("주문 번호와 기간을 함께 검색하면 두 조건을 모두 만족해야 한다")
    void searchByOrderIdAndDateRangeRequiresBothConditions() {
        // given: 검색 기간 밖의 주문
        Order target = saveOrderAt("2026-10-10T12:00:00");

        // 검색 기간 안이지만 주문 번호가 다른 주문
        saveOrderAt("2026-10-12T12:00:00");

        OrderSearchRequest request = new OrderSearchRequest();
        request.setOrderId(target.getId());
        request.setFromDate(LocalDate.of(2026, 10, 11));
        request.setToDate(LocalDate.of(2026, 10, 12));

        // when
        Page<Order> result = orderQueryRepository.search(
                request,
                PageRequest.of(0, 10)
        );

        // then: 두 조건을 모두 만족하는 주문은 없음
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }    
    
}
