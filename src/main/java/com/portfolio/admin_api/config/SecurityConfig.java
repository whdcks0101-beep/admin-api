package com.portfolio.admin_api.config;

import org.springframework.beans.factory.ObjectProvider;
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
				.formLogin(form -> form.defaultSuccessUrl("http://localhost:5173", true))
				.logout(logout -> logout.logoutSuccessUrl("http://localhost:5173"))
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
		            "http://localhost:5173/"
		    );

		    // 이메일·비밀번호 로그인 사용자가 로그아웃 후 돌아올 주소
		    logoutSuccessHandler.setDefaultTargetUrl(
		            "http://localhost:5173/"
		    );

		    http.oauth2Login(oauth2 -> oauth2
		            .userInfoEndpoint(userInfo -> userInfo
		                    .oidcUserService(oidcUserService)
		            )
		            .defaultSuccessUrl("http://localhost:5173", true)
		    );

		    http.logout(logout -> logout
		            .logoutSuccessHandler(logoutSuccessHandler)
		    );
		}		

		return http.build();
	}
}
