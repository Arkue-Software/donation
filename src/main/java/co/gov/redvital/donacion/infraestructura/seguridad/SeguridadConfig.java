package co.gov.redvital.donacion.infraestructura.seguridad;

import co.gov.redvital.donacion.infraestructura.configuracion.PropiedadesRedVital;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * El servicio vuelve a validar el token aunque el gateway ya lo hizo (EC-03):
 * firma RS256 contra el JWKS de Identidad, emisor, audiencia y vigencia. Sin
 * llaves disponibles, falla cerrado con 401.
 */
@Configuration
@EnableMethodSecurity
class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, ManejadorDenegaciones denegaciones,
                                        JwtAuthenticationConverter conversor) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/intenciones", "/v1/donantes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/intenciones/*").permitAll()
                        // /internal no tiene controladores: responde 404 sin revelar nada.
                        .requestMatchers("/internal/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(conversor))
                        .authenticationEntryPoint(denegaciones)
                        .accessDeniedHandler(denegaciones))
                .exceptionHandling(e -> e.authenticationEntryPoint(denegaciones).accessDeniedHandler(denegaciones));
        return http.build();
    }

    @Bean
    JwtDecoder decodificadorJwt(PropiedadesRedVital propiedades) {
        var jwt = propiedades.jwt();
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withJwkSetUri(jwt.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        var audiencia = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(jwt.audiencia()));
        var rol = new JwtClaimValidator<String>("role", r -> r != null && !r.isBlank());
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwt.emisor()), audiencia, rol));
        return decodificador;
    }

    /** La reivindicación role se convierte en la autoridad ROLE_&lt;rol&gt;; el principal es sub. */
    @Bean
    JwtAuthenticationConverter conversorAutenticacion() {
        var autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("role");
        autoridades.setAuthorityPrefix("ROLE_");
        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        conversor.setPrincipalClaimName("sub");
        return conversor;
    }
}
