package cl.duoc.levelup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos que envía el frontend para crear o actualizar una categoría.
 * Se usa un DTO en vez de la entidad para que el cliente no pueda enviar el id
 * y para validar los datos con @Valid antes de llegar al service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 60, message = "El nombre puede tener como máximo 60 caracteres")
    private String nombre;

    @Size(max = 255, message = "La descripción puede tener como máximo 255 caracteres")
    private String descripcion;

    /** Opcional: si no viene, la categoría queda activa. */
    private Boolean activa;
}
