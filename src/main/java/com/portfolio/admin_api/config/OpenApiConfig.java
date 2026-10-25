package com.portfolio.admin_api.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI adminOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("주문·재고 관리 API")
                        .description(
                                "상품 등록, 재고 입·출고, 주문 생성 및 조회를 제공하는 API입니다."
                        )
                        .version("v1"));
    }
    
}
