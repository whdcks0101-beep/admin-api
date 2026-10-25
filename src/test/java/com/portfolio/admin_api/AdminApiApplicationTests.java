package com.portfolio.admin_api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AdminApiApplicationTests {

	@Test
	@DisplayName("테스트 환경에서 스프링 애플리케이션이 정상적으로 시작된다")
	void contextLoads() {
	}

}
