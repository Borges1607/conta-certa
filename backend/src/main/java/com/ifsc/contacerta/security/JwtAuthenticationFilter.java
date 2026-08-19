package com.ifsc.contacerta.security;

import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.service.SessionAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final SessionAuthenticationService sessionAuthenticationService;
	private final SecurityProblemWriter problemWriter;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			String token = authorization.substring(BEARER_PREFIX.length());
			AccessTokenClaims claims = jwtService.parse(token);
			CurrentUser currentUser = sessionAuthenticationService.authenticate(claims);
			var authentication = UsernamePasswordAuthenticationToken.authenticated(
					currentUser,
					null,
					List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role().name()))
			);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
		} catch (ApiException exception) {
			SecurityContextHolder.clearContext();
			problemWriter.writeInvalidAccessToken(request, response);
		}
	}
}
