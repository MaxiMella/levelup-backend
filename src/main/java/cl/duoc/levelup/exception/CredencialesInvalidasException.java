package cl.duoc.levelup.exception;

/**
 * Se lanza en el login cuando el correo no existe o la contraseña no corresponde.
 * El mensaje es el mismo en ambos casos para no revelar qué correos están registrados.
 * GlobalExceptionHandler la convierte en una respuesta 401 Unauthorized.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
