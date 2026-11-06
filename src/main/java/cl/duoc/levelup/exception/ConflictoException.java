package cl.duoc.levelup.exception;

/**
 * Se lanza cuando la operación choca con datos que ya existen, por ejemplo:
 * un nombre de categoría repetido, eliminar una categoría con productos
 * o eliminar un producto que ya aparece en una boleta.
 * GlobalExceptionHandler la convierte en una respuesta 409 Conflict.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
