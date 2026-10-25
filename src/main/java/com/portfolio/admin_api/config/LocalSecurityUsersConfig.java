package com.portfolio.admin_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.portfolio.admin_api.auth.service.DbUserDetailsService;
import com.portfolio.admin_api.auth.service.ExternalIdentityService;
import com.portfolio.admin_api.auth.service.PortfolioOidcUserService;
import com.portfolio.admin_api.user.repository.UserRepository;

@Configuration
@Profile("local")
public class LocalSecurityUsersConfig {

	@Bean
	public UserDetailsService userDetailsService(UserRepository userRepository) {
		return new DbUserDetailsService(userRepository);
	}

	@Bean
	public PortfolioOidcUserService portfolioOidcUserService(ExternalIdentityService externalIdentityService) {
		return new PortfolioOidcUserService(externalIdentityService);
	}

}
