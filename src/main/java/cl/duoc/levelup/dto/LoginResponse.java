package cl.duoc.levelup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del login y de la renovación del token (POST /api/v1/auth/refresh). Ejemplo:
 * <pre>
 * { "token": "eyJhbGciOi...", "tipo": "Bearer", "expiraEn": 1795442400000,
 *   "usuario": { "id": 3, "correo": "pedro@duocuc.cl", "rol": "CLIENTE", ... } }
 * </pre>
 * El frontend guarda el token y lo envía en cada petición como "Authorization: Bearer <token>".
 * Con "expiraEn" sabe cuándo vence y lo renueva antes con /refresh.
 * "usuario" es un UsuarioResponse, así que nunca trae la contraseña.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;

    /** Siempre "Bearer": indica cómo se envía el token en el header Authorization. */
    private String tipo;

    /**
     * Fecha de vencimiento del token en milisegundos desde el 1/1/1970 (epoch), igual que Date.getTime()
     * en Java o Date.now() en JavaScript. Ej: el frontend renueva cuando falten menos de 5 minutos.
     */
    private long expiraEn;

    private UsuarioResponse usuario;
}
