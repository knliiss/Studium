package dev.knalis.gateway.config;

import dev.knalis.gateway.exception.GatewayAccessDeniedHandler;
import dev.knalis.gateway.exception.GatewayAuthenticationEntryPoint;
import dev.knalis.shared.security.jwt.ReactiveJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@EnableReactiveMethodSecurity
public class GatewaySecurityConfig {
    
    private final PublicEndpointsConfig publicEndpointsConfig;
    private final ReactiveJwtAuthenticationConverter jwtAuthenticationConverter;
    private final GatewayAuthenticationEntryPoint authenticationEntryPoint;
    private final GatewayAccessDeniedHandler accessDeniedHandler;
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .cors(Customizer.withDefaults())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(publicEndpointsConfig.getPublicEndpoints().toArray(new String[0])).permitAll()
                        .pathMatchers("/api/v1/audit/**", "/api/v1/search/**").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/schedule/semesters/active",
                                "/api/v1/schedule/slots",
                                "/api/v1/schedule/me/week",
                                "/api/v1/schedule/me/range",
                                "/api/v1/schedule/me/export.ics"
                        ).hasAnyRole("OWNER", "ADMIN", "TEACHER", "STUDENT")
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/schedule/groups/*/week",
                                "/api/v1/schedule/groups/*/range",
                                "/api/v1/schedule/groups/*/export.ics",
                                "/api/v1/schedule/teachers/*/week",
                                "/api/v1/schedule/teachers/*/range",
                                "/api/v1/schedule/teachers/*/export.ics",
                                "/api/v1/schedule/rooms/*/week",
                                "/api/v1/schedule/rooms/*/range",
                                "/api/v1/schedule/search"
                        ).hasAnyRole("OWNER", "ADMIN", "TEACHER", "STUDENT")
                        .pathMatchers(HttpMethod.POST, "/api/v1/schedule/conflicts/check")
                        .hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers("/api/v1/schedule/templates", "/api/v1/schedule/templates/**")
                        .hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers("/api/v1/schedule/semesters", "/api/v1/schedule/semesters/**")
                        .hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers("/api/v1/schedule/slots", "/api/v1/schedule/slots/**")
                        .hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/schedule/rooms", "/api/v1/schedule/rooms/**")
                        .hasAnyRole("OWNER", "ADMIN", "TEACHER", "STUDENT")
                        .pathMatchers(HttpMethod.POST, "/api/v1/schedule/rooms")
                        .hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/schedule/rooms/**")
                        .hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/schedule/rooms/**")
                        .hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers("/api/v1/schedule/overrides", "/api/v1/schedule/overrides/**")
                        .hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/education/search",
                                "/api/v1/assignments/search",
                                "/api/v1/testing/tests/search"
                        ).hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/assignments").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/assignments/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/assignments/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/testing/tests/*/start").hasAnyRole("OWNER", "ADMIN", "STUDENT")
                        .pathMatchers(HttpMethod.POST, "/api/v1/testing/tests/*/finish").hasAnyRole("OWNER", "ADMIN", "STUDENT")
                        .pathMatchers(HttpMethod.POST, "/api/v1/testing/tests/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/testing/questions/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/testing/answers/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/grades/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.GET, "/api/v1/submissions/assignment/*/mine").hasAnyRole("OWNER", "ADMIN", "STUDENT")
                        .pathMatchers(HttpMethod.GET, "/api/v1/submissions/assignment/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.POST, "/api/v1/submissions/*/comments").hasAnyRole("OWNER", "ADMIN", "TEACHER", "STUDENT")
                        .pathMatchers(HttpMethod.GET, "/api/v1/submissions/*/comments").hasAnyRole("OWNER", "ADMIN", "TEACHER", "STUDENT")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/submissions/*/comments/*").hasAnyRole("OWNER", "ADMIN", "TEACHER", "STUDENT")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/submissions/*/comments/*").hasAnyRole("OWNER", "ADMIN", "TEACHER", "STUDENT")
                        .pathMatchers("/api/v1/submissions/**").hasAnyRole("OWNER", "ADMIN", "TEACHER", "STUDENT")
                        .pathMatchers(HttpMethod.POST, "/api/v1/testing/results").hasAnyRole("OWNER", "ADMIN", "STUDENT")
                        .pathMatchers(HttpMethod.GET, "/api/v1/testing/results/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/testing/results/**").hasAnyRole("OWNER", "ADMIN", "TEACHER")
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .build();
    }
}
