package cl.duoc.levelup.exception;

/**
 * Se lanza cuando los datos no cumplen una regla de negocio que las anotaciones de @Valid
 * no pueden revisar solas, porque compara dos campos (ej: precioOferta debe ser menor que precio).
 * GlobalExceptionHandler la convierte en una respuesta 400 Bad Request.
 */
public class DatosInvalidosException extends RuntimeException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
