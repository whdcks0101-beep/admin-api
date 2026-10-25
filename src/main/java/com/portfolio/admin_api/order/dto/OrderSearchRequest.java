package com.portfolio.admin_api.order.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.portfolio.admin_api.common.dto.PageQueryRequest;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderSearchRequest extends PageQueryRequest{
	
    @Positive(message = "주문 번호는 1 이상이어야 합니다.")
    private Long orderId;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
    
    @AssertTrue(message = "시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isDateRangeValid() {
        if (fromDate == null || toDate == null) {
            return true;
        }

        return !fromDate.isAfter(toDate);
    }
	
}

