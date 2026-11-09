package cl.duoc.levelup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS: permite que el frontend React (Vite, puerto 5173) llame a esta API (puerto 8080).
 * Sin esto, el navegador bloquea las llamadas fetch/axios por venir de otro origen.
 * Spring Security usa este bean con http.cors() en security/SecurityConfig: así CORS se aplica dentro de
 * la cadena de seguridad y la consulta previa del navegador (OPTIONS) se responde sin pedir token.
 * Por eso ya no hay un CorsFilter aparte: quedaba después de Spring Security y esa consulta recibía 401.
 * Se permite el header Authorization, que es donde el frontend envía "Bearer <token>".
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
