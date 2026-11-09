package cl.duoc.levelup.exception;

/**
 * Se lanza desde los services cuando el usuario tiene sesión, pero no puede ver un dato en particular
 * (ej: un cliente que pide el historial de compras de otro cliente).
 * Los permisos por rol y por ruta están en SecurityConfig; esta excepción es para las reglas
 * que dependen del dato pedido ("solo el dueño"). GlobalExceptionHandler la convierte en 403 Forbidden.
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
