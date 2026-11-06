package cl.duoc.levelup.dto;

import cl.duoc.levelup.model.Rol;
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
 * Datos que envía el administrador para crear o editar un usuario.
 * A diferencia del registro: elige el rol y no recibe código de referido
 * (los puntos por referido son solo para el registro del cliente).
 * La contraseña es opcional en este DTO porque al editar puede venir vacía (se mantiene la actual);
 * al crear, UsuarioService la exige.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRequest {

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

    @NotBlank(message = "El correo es obligatorio")
    @Size(max = 120, message = "El correo puede tener como máximo 120 caracteres")
    /** Misma regla que RegistroRequest: se aceptan espacios en los extremos y el service los quita. */
    @Pattern(regexp = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}\\s*$", message = "Ingresa un correo electrónico válido")
    private String correo;

    /** Vacía o null = mantener la actual (solo al editar). Si viene, debe cumplir la misma regla del registro. */
    @Pattern(regexp = "^$|^(?=.*[a-zA-Z])(?=.*\\d).{8,72}$",
            message = "Mínimo 8 caracteres (máximo 72), combinando letras y números")
    private String password;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    private LocalDate fechaNacimiento;

    /** ADMIN, VENDEDOR o CLIENTE (en mayúsculas). */
    @NotNull(message = "El rol es obligatorio")
    private Rol rol;
}
