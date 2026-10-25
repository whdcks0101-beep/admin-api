package com.portfolio.admin_api.order.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.portfolio.admin_api.order.statistics.dto.DailyOrderStatisticResponse;
import com.portfolio.admin_api.order.statistics.repository.OrderStatisticRepository;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderStatisticServiceIntegrationTest {

    // 개발 DB와 분리된 임시 DB다. 테스트 종료 시 컨테이너도 정리된다.
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("order_statistics_test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private OrderStatisticService service;

    @Autowired
    private OrderStatisticRepository repository;

    private final LocalDateTime orderedAt =
            LocalDateTime.of(2026, 10, 22, 20, 18, 47, 424595000);

    @BeforeEach
    void clearTestRecords() {
        repository.deleteAllInBatch();
    }

    @Test
    @DisplayName("처음 받은 주문은 주문 번호와 마이크로초 시각을 그대로 저장한다")
    void storesNewOrder() {
        service.recordOrder(36L, orderedAt);

        var saved = repository.findById(36L).orElseThrow();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(saved.getOrderedAt()).isEqualTo(orderedAt);
    }

    @Test
    @DisplayName("같은 주문 이벤트를 두 번 처리해도 통계 기록은 한 건이다")
    void duplicateOrderDoesNotAddAnotherRecord() {
        // 테스트에 @Transactional을 붙이지 않아 각각의 서비스 호출이 커밋된다.
        service.recordOrder(36L, orderedAt);
        service.recordOrder(36L, orderedAt);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(36L).orElseThrow().getOrderedAt())
                .isEqualTo(orderedAt);
    }

    @Test
    @DisplayName("같은 주문 번호에 다른 시각이 와도 최초 저장한 시각을 유지한다")
    void duplicateOrderPreservesOriginalTime() {
        service.recordOrder(36L, orderedAt);
        service.recordOrder(36L, orderedAt.plusDays(1));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(36L).orElseThrow().getOrderedAt())
                .isEqualTo(orderedAt);
    }

    @Test
    @DisplayName("서로 다른 주문 번호는 각각 별도의 통계 기록으로 저장한다")
    void differentOrdersAreStoredSeparately() {
        service.recordOrder(36L, orderedAt);
        service.recordOrder(37L, orderedAt.plusMinutes(1));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findById(36L)).isPresent();
        assertThat(repository.findById(37L)).isPresent();
    }

    @Test
    @DisplayName("시간이 달라도 같은 날짜는 합산하며 날짜 오름차순으로 반환하고 빈 날짜는 제외한다")
    void groupsByDateInAscendingOrder() {
        LocalDate first = LocalDate.of(2026, 10, 20);
        LocalDate last = first.plusDays(2);
        // 삽입 순서와 조회 정렬 순서를 다르게 해 정렬도 검증한다.
        service.recordOrder(40L, last.atTime(10, 0));
        service.recordOrder(41L, first.atTime(9, 0));
        service.recordOrder(42L, first.atTime(18, 30));
        service.recordOrder(42L, first.atTime(18, 30));

        assertThat(service.getDailyStatistics(first, last)).containsExactly(
                new DailyOrderStatisticResponse(first, 2L),
                new DailyOrderStatisticResponse(last, 1L));
    }

    @Test
    @DisplayName("시작일 0시와 종료일 마지막 마이크로초는 포함하고 기간 밖 기록은 제외한다")
    void includesDateBoundariesButExcludesOutsideRange() {
        LocalDate from = LocalDate.of(2026, 10, 21);
        LocalDate to = from.plusDays(1);
        service.recordOrder(50L, from.atStartOfDay().minusNanos(1000));
        service.recordOrder(51L, from.atStartOfDay());
        service.recordOrder(52L, to.plusDays(1).atStartOfDay().minusNanos(1000));
        service.recordOrder(53L, to.plusDays(1).atStartOfDay());

        assertThat(service.getDailyStatistics(from, to)).containsExactly(
                new DailyOrderStatisticResponse(from, 1L),
                new DailyOrderStatisticResponse(to, 1L));
    }

    @Test
    @DisplayName("시작일과 종료일이 같으면 그 하루만 조회한다")
    void supportsSingleDayRange() {
        LocalDate day = LocalDate.of(2026, 10, 22);
        service.recordOrder(60L, day.minusDays(1).atTime(12, 0));
        service.recordOrder(61L, day.atStartOfDay());
        service.recordOrder(62L, day.atTime(23, 59, 59, 999999000));
        service.recordOrder(63L, day.plusDays(1).atStartOfDay());

        assertThat(service.getDailyStatistics(day, day)).containsExactly(
                new DailyOrderStatisticResponse(day, 2L));
    }

    @Test
    @DisplayName("조회 기간에 주문 기록이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenRangeHasNoRecords() {
        LocalDate day = LocalDate.of(2026, 10, 22);
        service.recordOrder(70L, day.minusDays(1).atTime(12, 0));

        assertThat(service.getDailyStatistics(day, day)).isEmpty();
    }

    @Test
    @DisplayName("조회 날짜 누락과 역전된 기간 및 다음 날 계산이 불가능한 종료일을 거부한다")
    void rejectsInvalidDateRanges() {
        LocalDate day = LocalDate.of(2026, 10, 22);
        assertThatThrownBy(() -> service.getDailyStatistics(null, day))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작일과 종료일은 필수입니다.");
        assertThatThrownBy(() -> service.getDailyStatistics(day, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작일과 종료일은 필수입니다.");
        assertThatThrownBy(() -> service.getDailyStatistics(day.plusDays(1), day))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작일은 종료일보다 늦을 수 없습니다.");
        assertThatThrownBy(() -> service.getDailyStatistics(day, LocalDate.MAX))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 종료일이 허용 범위를 벗어났습니다.");
    }

    @Test
    @DisplayName("잘못된 주문 번호나 누락된 시각은 거부하고 통계에 저장하지 않는다")
    void invalidInputDoesNotCreateRecords() {
        assertThatThrownBy(() -> service.recordOrder(-1L, orderedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordOrder(0L, orderedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordOrder(null, orderedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordOrder(36L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(repository.count()).isZero();
    }
}
