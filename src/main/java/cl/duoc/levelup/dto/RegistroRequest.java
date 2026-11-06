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
 * Datos del formulario de registro de la tienda (el cliente se registra solo).
 * Las reglas son las mismas del frontend (src/utils/validaciones.js).
 * La edad mínima de 18 años se revisa en UsuarioService porque depende de la fecha de hoy.
 * "confirmarPassword" solo lo valida el formulario del frontend; no llega al backend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroRequest {

    /**
     * Solo letras (con tildes y ñ) separadas por espacio, guion o apóstrofe. Ej: "María José", "O'Higgins".
     * Se aceptan espacios al inicio y al final (el frontend valida con trim()); el service los quita.
     */
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

    /**
     * Misma expresión del frontend: exige un dominio con punto (ej: rechaza "ana@gmail").
     * Los \s* de los extremos aceptan espacios al inicio y al final, porque el frontend valida
     * con correo.trim(); UsuarioService los quita antes de guardar.
     */
    @NotBlank(message = "El correo es obligatorio")
    @Size(max = 120, message = "El correo puede tener como máximo 120 caracteres")
    @Pattern(regexp = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}\\s*$", message = "Ingresa un correo electrónico válido")
    private String correo;

    /** Mínimo 8 caracteres con letras y números. Máximo 72 porque BCrypt ignora lo que sigue. */
    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,72}$",
            message = "Mínimo 8 caracteres (máximo 72), combinando letras y números")
    private String password;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    private LocalDate fechaNacimiento;

    /** Opcional: código de quien lo invitó, ej: LVLPEDRO. Ambos ganan puntos LevelUp. */
    @Size(max = 20, message = "El código de referido puede tener como máximo 20 caracteres")
    private String codigoReferido;
}
