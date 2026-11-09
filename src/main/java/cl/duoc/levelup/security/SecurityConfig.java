package cl.duoc.levelup.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de Spring Security: quién puede usar cada ruta de la API.
 *
 * - Stateless: el servidor no guarda sesiones. Cada petición trae su token JWT y se valida de nuevo.
 * - Sin CSRF: el ataque CSRF aprovecha las cookies de sesión que el navegador envía solo; aquí no hay
 *   cookies, el token va en el header Authorization y el navegador nunca lo agrega por su cuenta.
 * - CORS: usa el bean corsConfigurationSource de config/CorsConfig. Va dentro de Spring Security para
 *   que la consulta previa del navegador (OPTIONS) se responda antes de pedir token.
 *
 * Las reglas se revisan de ARRIBA hacia ABAJO y gana la primera que calza. Por eso las rutas
 * específicas (ej: /productos/criticos) van antes que las generales (ej: GET /productos/**).
 * "/**" también incluye la ruta base: "/api/v1/productos/**" calza con "/api/v1/productos".
 * hasRole("ADMIN") revisa la authority "ROLE_ADMIN" que entrega UsuarioDetailsService.
 */
@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;
    private final SeguridadErrorHandler seguridadErrorHandler;

    public SecurityConfig(JwtService jwtService, UsuarioDetailsService usuarioDetailsService,
                          SeguridadErrorHandler seguridadErrorHandler) {
        this.jwtService = jwtService;
        this.usuarioDetailsService = usuarioDetailsService;
        this.seguridadErrorHandler = seguridadErrorHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sin formulario de login ni ventana de usuario/clave del navegador: solo JWT.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // 401 y 403 con el mismo JSON que GlobalExceptionHandler.
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(seguridadErrorHandler)
                        .accessDeniedHandler(seguridadErrorHandler))
                .authorizeHttpRequests(rutas -> rutas
                        // ----- Públicas -----
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll() // página de error interna de Spring Boot
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/registro").permitAll()

                        // ----- Sesión propia (cualquier rol) -----
                        .requestMatchers("/api/v1/auth/**").authenticated() // /me y /perfil

                        // ----- Productos y categorías -----
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos/criticos").hasAnyRole("ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos/**", "/api/v1/categorias/**").permitAll()
                        // Lo que queda es POST, PUT y DELETE (el mantenedor).
                        .requestMatchers("/api/v1/productos/**", "/api/v1/categorias/**").hasRole("ADMIN")

                        // ----- Boletas -----
                        .requestMatchers(HttpMethod.POST, "/api/v1/boletas").permitAll() // comprar sin cuenta
                        .requestMatchers(HttpMethod.GET, "/api/v1/boletas/mis-compras").hasAnyRole("CLIENTE", "ADMIN")
                        // Que el cliente sea el dueño del historial lo revisa BoletaService (403 si no).
                        .requestMatchers(HttpMethod.GET, "/api/v1/boletas/usuario/**").hasAnyRole("CLIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/boletas/**").hasAnyRole("ADMIN", "VENDEDOR")

                        // ----- Usuarios (mantenedor) -----
                        .requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN")

                        // ----- Dashboard y reportes del panel -----
                        .requestMatchers("/api/v1/reportes/**").hasRole("ADMIN")

                        // Cualquier otra ruta exige sesión.
                        .anyRequest().authenticated())
                // El filtro JWT va antes del filtro estándar de usuario/contraseña de Spring Security.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
