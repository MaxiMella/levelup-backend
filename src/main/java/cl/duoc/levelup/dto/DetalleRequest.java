package cl.duoc.levelup.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un producto del carrito dentro de BoletaRequest: qué producto y cuántas unidades.
 * El precio NO viene del frontend: lo calcula BoletaService con el precio guardado en la base,
 * así nadie puede comprar más barato modificando el JSON.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleRequest {

    @NotNull(message = "El producto es obligatorio")
    private Long productoId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;
}
