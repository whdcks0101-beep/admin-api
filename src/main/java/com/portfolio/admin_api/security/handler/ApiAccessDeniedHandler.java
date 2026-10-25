package com.portfolio.admin_api.security.handler;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.portfolio.admin_api.common.ApiExceptionHandler.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
	
	private final ObjectMapper objectMapper;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {

		log.warn(
		        "API 접근 거부: method={}, path={}, reason={}",
		        request.getMethod(),
		        request.getRequestURI(),
		        accessDeniedException.getClass().getSimpleName()
		);		
		
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());

		objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse("요청이 허용되지 않습니다."));
	}
	
}
