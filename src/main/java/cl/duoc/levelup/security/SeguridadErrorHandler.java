package cl.duoc.levelup.security;

import cl.duoc.levelup.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Responde los errores de seguridad con el mismo JSON (ErrorResponse) que GlobalExceptionHandler.
 * Hace falta una clase aparte porque Spring Security rechaza la petición en su cadena de filtros,
 * ANTES de llegar al controller, y @RestControllerAdvice solo atrapa errores de los controllers.
 *
 * - 401 Unauthorized (AuthenticationEntryPoint): la ruta pide sesión y no hay token o no es válido.
 * - 403 Forbidden (AccessDeniedHandler): hay sesión, pero el rol no tiene permiso (ej: VENDEDOR creando productos).
 */
@Component
public class SeguridadErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** El mismo convertidor JSON (Jackson 3) que usa Spring para las respuestas de los controllers. */
    private final ObjectMapper objectMapper;

    public SeguridadErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401: sin token, o con un token inválido o vencido (el motivo lo deja JwtAuthenticationFilter). */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String mensaje = "Debes iniciar sesión para acceder a este recurso";
        Object errorToken = request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_ERROR_TOKEN);
        if (errorToken != null) {
            mensaje = errorToken.toString();
        }
        escribir(response, HttpStatus.UNAUTHORIZED, mensaje);
    }

    /** 403: el usuario inició sesión, pero su rol no puede usar esta ruta. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escribir(response, HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción");
    }

    /** Escribe el ErrorResponse como JSON en la respuesta. */
    private void escribir(HttpServletResponse response, HttpStatus status, String mensaje) throws IOException {
        ErrorResponse cuerpo = new ErrorResponse(status.value(), status.getReasonPhrase(), mensaje,
                LocalDateTime.now(), null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
