package com.portfolio.admin_api.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration(proxyBeanMethods = false)
@Profile("docker")
public class DockerOAuth2ClientConfig {

	@Bean
	public ClientRegistrationRepository clientRegistrationRepository(
			@Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId,
			@Value("${spring.security.oauth2.client.registration.keycloak.client-secret}") String clientSecret,
			@Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}") String issuerUri,
			@Value("${spring.security.oauth2.client.provider.keycloak.authorization-uri}") String authorizationUri,
			@Value("${spring.security.oauth2.client.provider.keycloak.token-uri}") String tokenUri,
			@Value("${spring.security.oauth2.client.provider.keycloak.user-info-uri}") String userInfoUri,
			@Value("${spring.security.oauth2.client.provider.keycloak.jwk-set-uri}") String jwkSetUri) {

		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak").clientId(clientId)
				.clientSecret(clientSecret).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid", "profile", "email")
				.issuerUri(issuerUri).authorizationUri(authorizationUri).tokenUri(tokenUri).userInfoUri(userInfoUri)
				.userNameAttributeName("sub").jwkSetUri(jwkSetUri)
				.providerConfigurationMetadata(
						Map.of("end_session_endpoint", issuerUri + "/protocol/openid-connect/logout"))
				.clientName("Keycloak").build();

		return new InMemoryClientRegistrationRepository(registration);
	}
}