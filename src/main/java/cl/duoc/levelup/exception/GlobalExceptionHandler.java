package cl.duoc.levelup.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejo centralizado de errores: atrapa las excepciones que salen de cualquier controller
 * y las transforma en un JSON ErrorResponse con el código HTTP correcto.
 * Gracias a esta clase la API nunca se cae ni devuelve la traza de Java al cliente,
 * y los controllers no necesitan try/catch.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404: se buscó por id algo que no existe (lanzada por los services). */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    /** 409: nombre repetido, categoría con productos, producto ya vendido, etc. */
    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponse> manejarConflicto(ConflictoException ex) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    /** 400: regla de negocio que compara campos (ej: precio de oferta mayor que el precio). */
    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<ErrorResponse> manejarDatosInvalidos(DatosInvalidosException ex) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    /** 401: correo o contraseña incorrectos en el login. */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> manejarCredencialesInvalidas(CredencialesInvalidasException ex) {
        return responder(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    /**
     * 403: el usuario tiene sesión, pero el dato no le pertenece (ej: historial de compras de otro cliente).
     * Los 401 y 403 de las reglas por ruta de SecurityConfig ocurren antes de llegar al controller,
     * por eso los responde security/SeguridadErrorHandler con este mismo formato.
     */
    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(AccesoDenegadoException ex) {
        return responder(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    /**
     * 400: falló alguna anotación de Validation (@NotBlank, @Positive...) en un DTO con @Valid.
     * Se arma un mapa campo -> mensaje para que el frontend sepa qué corregir.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.put(error.getField(), error.getDefaultMessage());
        }
        return responder(HttpStatus.BAD_REQUEST, "Hay campos con datos inválidos", campos);
    }

    /** 400: el cuerpo no es un JSON válido o un campo trae un tipo incorrecto (ej: "precio": "abc"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", null);
    }

    /** 400: un parámetro de la URL no tiene el tipo esperado (ej: /api/v1/productos/abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarTipoParametro(MethodArgumentTypeMismatchException ex) {
        String mensaje = "El valor '" + ex.getValue() + "' no es válido para el parámetro '" + ex.getName() + "'";
        return responder(HttpStatus.BAD_REQUEST, mensaje, null);
    }

    /** 404: la ruta no existe (ej: /api/v1/productoss). Sin esto caería en el 500 de más abajo. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> manejarRutaNoExiste(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, "La ruta /" + ex.getResourcePath() + " no existe", null);
    }

    /** 405: la ruta existe pero no con ese método (ej: PATCH donde solo hay PUT). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "El método " + ex.getMethod() + " no está permitido en esta ruta", null);
    }

    /**
     * 409: la base de datos rechazó la operación por una restricción (UNIQUE, clave foránea).
     * Es un respaldo: normalmente los services lo revisan antes y lanzan ConflictoException.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarIntegridad(DataIntegrityViolationException ex) {
        return responder(HttpStatus.CONFLICT, "La operación no se puede realizar porque entra en conflicto con datos existentes", null);
    }

    /**
     * 500: cualquier otro error inesperado. Se registra completo en la consola (para el desarrollador)
     * pero al cliente solo se le envía un mensaje genérico, sin detalles internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorGeneral(Exception ex) {
        log.error("Error inesperado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado en el servidor", null);
    }

    /** Arma el ErrorResponse y lo envuelve en un ResponseEntity con el código HTTP indicado. */
    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String mensaje, Map<String, String> campos) {
        ErrorResponse cuerpo = new ErrorResponse(status.value(), status.getReasonPhrase(), mensaje,
                LocalDateTime.now(), campos);
        return ResponseEntity.status(status).body(cuerpo);
    }
}
