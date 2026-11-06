package cl.duoc.levelup.exception;

/**
 * Se lanza desde los services cuando se busca algo por id y no existe.
 * GlobalExceptionHandler la convierte en una respuesta 404 Not Found.
 * Extiende RuntimeException para no tener que declararla con "throws" en cada método.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
