package cl.duoc.levelup.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que se ejecuta una vez en cada petición, antes de llegar al controller.
 * Lee el header "Authorization: Bearer <token>" y, si el token es válido, deja al usuario
 * autenticado en el SecurityContext (ahí Spring Security revisa el rol para las reglas de SecurityConfig).
 *
 * Casos:
 * - Sin header (visitante): sigue sin usuario. Si la ruta es pública funciona; si no, 401.
 * - Token válido: se carga el usuario desde la base de datos (con su rol actual) y sigue.
 * - Token inválido o vencido: sigue como visitante, pero se guarda el motivo en la petición para que
 *   SeguridadErrorHandler responda "El token no es válido o expiró" si la ruta pedía sesión.
 *   Así un token viejo guardado en el frontend no bloquea el catálogo, que es público.
 *
 * No es un @Component a propósito: SecurityConfig lo crea con "new" y lo agrega a la cadena de Spring Security.
 * Si fuera un bean, Spring Boot además lo registraría como filtro general del servidor.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Nombre del atributo de la petición donde se deja el motivo cuando el token no sirve. */
    public static final String ATRIBUTO_ERROR_TOKEN = "errorToken";

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // 1. Sin token: la petición sigue como visitante.
        if (header == null || !header.startsWith(PREFIJO_BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Se quita "Bearer " y se revisa la firma y el vencimiento.
        String token = header.substring(PREFIJO_BEARER.length()).trim();
        if (!jwtService.esTokenValido(token)) {
            request.setAttribute(ATRIBUTO_ERROR_TOKEN, "El token no es válido o expiró. Inicia sesión nuevamente");
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Token válido: se busca al usuario por el correo del token (puede haber sido eliminado).
        try {
            String correo = jwtService.obtenerCorreo(token);
            UserDetails usuario = userDetailsService.loadUserByUsername(correo);

            // 4. Se deja autenticado: principal = usuario, credenciales = null (ya se validó con el token),
            //    authorities = su rol (ROLE_ADMIN, ROLE_VENDEDOR o ROLE_CLIENTE).
            UsernamePasswordAuthenticationToken autenticacion =
                    new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacion);
            SecurityContextHolder.setContext(contexto);
        } catch (UsernameNotFoundException e) {
            request.setAttribute(ATRIBUTO_ERROR_TOKEN, "El usuario del token ya no existe");
        }

        filterChain.doFilter(request, response);
    }
}
