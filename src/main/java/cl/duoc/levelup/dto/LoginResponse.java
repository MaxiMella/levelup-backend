package cl.duoc.levelup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del login. Ejemplo:
 * <pre>
 * { "token": "eyJhbGciOi...", "tipo": "Bearer",
 *   "usuario": { "id": 3, "correo": "pedro@duocuc.cl", "rol": "CLIENTE", ... } }
 * </pre>
 * El frontend guarda el token y lo envía en cada petición como "Authorization: Bearer <token>".
 * "usuario" es un UsuarioResponse, así que nunca trae la contraseña.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;

    /** Siempre "Bearer": indica cómo se envía el token en el header Authorization. */
    private String tipo;

    private UsuarioResponse usuario;
}
