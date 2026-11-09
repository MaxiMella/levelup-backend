package cl.duoc.levelup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Datos del formulario de checkout del frontend para crear una boleta.
 * Los montos (subtotal, descuento, neto, IVA, total) no vienen aquí: los calcula BoletaService.
 * Los largos máximos son los mismos que las columnas de la tabla boleta.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoletaRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 60, message = "El nombre puede tener como máximo 60 caracteres")
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 80, message = "Los apellidos pueden tener como máximo 80 caracteres")
    private String apellidos;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 120, message = "El correo puede tener como máximo 120 caracteres")
    private String correo;

    @NotBlank(message = "La calle es obligatoria")
    @Size(max = 150, message = "La calle puede tener como máximo 150 caracteres")
    private String calle;

    /** Opcional: departamento, casa, oficina. */
    @Size(max = 50, message = "El departamento puede tener como máximo 50 caracteres")
    private String depto;

    @NotBlank(message = "La región es obligatoria")
    @Size(max = 80, message = "La región puede tener como máximo 80 caracteres")
    private String region;

    @NotBlank(message = "La comuna es obligatoria")
    @Size(max = 80, message = "La comuna puede tener como máximo 80 caracteres")
    private String comuna;

    /** Opcional: indicaciones para la entrega. */
    @Size(max = 255, message = "Las indicaciones pueden tener como máximo 255 caracteres")
    private String indicaciones;

    /**
     * Opcional: id del usuario que compra; null si compra como visitante.
     * Solo se respeta si quien compra es ADMIN. Con token de CLIENTE se usa el usuario del token,
     * y sin token (o con token de VENDEDOR) se ignora: así nadie compra a nombre de otro
     * ni usa su descuento Duoc (ver BoletaService.crear(request, correoSesion)).
     */
    private Long usuarioId;

    /**
     * Productos del carrito. @NotEmpty exige al menos 1 item y
     * List<@Valid DetalleRequest> hace que también se validen los campos de cada item
     * (los errores aparecen como "items[0].cantidad").
     */
    @NotEmpty(message = "La orden debe tener al menos un producto")
    private List<@Valid DetalleRequest> items;
}
