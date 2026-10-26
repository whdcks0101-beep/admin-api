package com.portfolio.admin_api.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import com.portfolio.admin_api.auth.service.PortfolioOidcUserService;
import com.portfolio.admin_api.security.handler.ApiAccessDeniedHandler;
import com.portfolio.admin_api.security.handler.ApiAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http
		  ,ApiAuthenticationEntryPoint apiAuthenticationEntryPoint
		  , ApiAccessDeniedHandler apiAccessDeniedHandler
		  , ObjectProvider<ClientRegistrationRepository> clientRegistrations	
		  , ObjectProvider<PortfolioOidcUserService> oidcUserServices
		  , @Value("${app.frontend-origin}") String frontendOrigin
	) throws Exception {

		http.cors(Customizer.withDefaults())
				.authorizeHttpRequests(
						auth -> auth.requestMatchers(
						        "/login",
						        "/error",
						        "/oauth2/**",
						        "/login/oauth2/**"
						).permitAll()
						.requestMatchers(
						        HttpMethod.GET,
						        "/actuator/health",
						        "/actuator/health/liveness",
						        "/actuator/health/readiness"
						).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/me")
						.authenticated()						
						// 회원 관리는 조회를 포함해 관리자만 허용
						.requestMatchers("/api/users", "/api/users/**").hasRole("ADMIN")
						// 체험 관리자는 명시한 상품·재고 변경만 허용한다. ADMIN 권한은 부여하지 않는다.
						.requestMatchers(HttpMethod.POST, "/api/products",
						        "/api/products/{productId}/stock-in", "/api/products/{productId}/stock-out")
						.hasAnyRole("ADMIN", "DEMO_ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/products/{productId}")
						.hasAnyRole("ADMIN", "DEMO_ADMIN")
						// 그 외 API 조회
						.requestMatchers(HttpMethod.GET, "/api/**").hasRole("USER")
						// 주문 생성
						.requestMatchers(HttpMethod.POST, "/api/orders").hasRole("USER")
						// 나머지 API 요청은 관리자만 허용
						.requestMatchers("/api/**").hasRole("ADMIN")
						// API 외 경로는 로그인 필요
						.anyRequest().authenticated()
						)
				.exceptionHandling(exceptions -> exceptions
						.defaultAuthenticationEntryPointFor(apiAuthenticationEntryPoint,
								PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
						.defaultAccessDeniedHandlerFor(apiAccessDeniedHandler,
								PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
						.defaultAccessDeniedHandlerFor(new AccessDeniedHandlerImpl(), AnyRequestMatcher.INSTANCE))
				.formLogin(form -> form.defaultSuccessUrl(frontendOrigin, true))
				.logout(logout -> logout.logoutSuccessUrl(frontendOrigin))
				.httpBasic(Customizer.withDefaults());
		
		ClientRegistrationRepository registrations =
		        clientRegistrations.getIfAvailable();

		if (registrations != null) {
		    PortfolioOidcUserService oidcUserService =
		            oidcUserServices.getObject();

		    OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
		            new OidcClientInitiatedLogoutSuccessHandler(registrations);

		    // SSO 로그인 사용자가 Keycloak 로그아웃까지 마친 뒤 돌아올 주소
		    logoutSuccessHandler.setPostLogoutRedirectUri(
		            frontendOrigin + "/"
		    );

		    // 이메일·비밀번호 로그인 사용자가 로그아웃 후 돌아올 주소
		    logoutSuccessHandler.setDefaultTargetUrl(
		            frontendOrigin + "/"
		    );

		    http.oauth2Login(oauth2 -> oauth2
		            .userInfoEndpoint(userInfo -> userInfo
		                    .oidcUserService(oidcUserService)
		            )
		            .defaultSuccessUrl(frontendOrigin, true)
		    );

		    http.logout(logout -> logout
		            .logoutSuccessHandler(logoutSuccessHandler)
		    );
		}		

		return http.build();
	}
}
