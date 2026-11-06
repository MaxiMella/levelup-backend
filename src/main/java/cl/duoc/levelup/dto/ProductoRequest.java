package cl.duoc.levelup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos que envía el frontend para crear o actualizar un producto.
 * La categoría llega solo como categoriaId (un número); el service busca la Categoria completa.
 * Las anotaciones validan cada campo por separado; la regla "precioOferta < precio"
 * compara dos campos y por eso se revisa en ProductoService.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre puede tener como máximo 120 caracteres")
    private String nombre;

    @Size(max = 500, message = "La descripción puede tener como máximo 500 caracteres")
    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor a 0")
    private Integer precio;

    /** Opcional: si viene, debe ser mayor a 0 y menor que el precio. */
    @Positive(message = "El precio de oferta debe ser mayor a 0")
    private Integer precioOferta;

    @NotNull(message = "El stock es obligatorio")
    @PositiveOrZero(message = "El stock no puede ser negativo")
    private Integer stock;

    @NotNull(message = "El stock crítico es obligatorio")
    @PositiveOrZero(message = "El stock crítico no puede ser negativo")
    private Integer stockCritico;

    @Size(max = 500, message = "La URL de la imagen puede tener como máximo 500 caracteres")
    private String imagen;

    @NotNull(message = "La categoría es obligatoria")
    private Long categoriaId;
}
