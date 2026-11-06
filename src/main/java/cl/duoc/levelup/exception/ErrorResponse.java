package cl.duoc.levelup.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato JSON que devuelve la API cuando ocurre un error. Ejemplo:
 * <pre>
 * {
 *   "status": 404,
 *   "error": "Not Found",
 *   "mensaje": "No existe la categoría con id 99",
 *   "fecha": "2026-11-06T15:30:00"
 * }
 * </pre>
 * Así el frontend siempre recibe la misma estructura y puede mostrar "mensaje" al usuario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /** Código HTTP numérico (400, 404, 409, 500...). */
    private int status;

    /** Nombre estándar del código HTTP (ej: "Bad Request", "Not Found", "Conflict"). */
    private String error;

    /** Explicación en español de lo que pasó. */
    private String mensaje;

    private LocalDateTime fecha;

    /**
     * Solo en errores de @Valid: el problema de cada campo, ej: {"precio": "El precio debe ser mayor a 0"}.
     * NON_NULL hace que este atributo no aparezca en el JSON cuando es null (los demás errores).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> campos;
}
