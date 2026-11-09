package cl.duoc.levelup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Datos que un usuario con sesión puede cambiar en su propio perfil (PUT /api/v1/auth/perfil).
 * A propósito NO tiene:
 * - rol, puntos ni descuentoDuoc: nadie puede darse más permisos, puntos o descuento a sí mismo.
 * - correo: es el "sub" del token (si cambiara, el token dejaría de servir) y de él depende el descuento Duoc.
 *   Si hay que cambiarlo, lo hace el administrador en PUT /api/v1/usuarios/{id}.
 * Las reglas de cada campo son las mismas de RegistroRequest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerfilRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^\\s*\\p{L}+(?:[ '-]\\p{L}+)*\\s*$",
            message = "El nombre solo puede tener letras, espacios, guiones o apóstrofes (mínimo 2 letras)")
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(min = 2, max = 50, message = "Los apellidos deben tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^\\s*\\p{L}+(?:[ '-]\\p{L}+)*\\s*$",
            message = "El apellido solo puede tener letras, espacios, guiones o apóstrofes (mínimo 2 letras)")
    private String apellidos;

    /** Vacía o null = mantener la contraseña actual. Si viene, misma regla del registro. */
    @Pattern(regexp = "^$|^(?=.*[a-zA-Z])(?=.*\\d).{8,72}$",
            message = "Mínimo 8 caracteres (máximo 72), combinando letras y números")
    private String password;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    private LocalDate fechaNacimiento;
}
