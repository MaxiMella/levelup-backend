package cl.duoc.levelup.dto;

import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Datos de un usuario que devuelve la API. Tiene todo menos la contraseña:
 * como el campo password no existe en esta clase, es imposible que se envíe por error.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private Long id;
    private String nombre;
    private String apellidos;
    private String correo;
    private LocalDate fechaNacimiento;
    private Rol rol;
    private boolean descuentoDuoc;
    private Integer puntos;
    private String codigoReferido;
    private String referidoPor;

    /** Convierte la entidad Usuario (con contraseña) en la respuesta (sin contraseña). */
    public static UsuarioResponse desde(Usuario usuario) {
        UsuarioResponse response = new UsuarioResponse();
        response.setId(usuario.getId());
        response.setNombre(usuario.getNombre());
        response.setApellidos(usuario.getApellidos());
        response.setCorreo(usuario.getCorreo());
        response.setFechaNacimiento(usuario.getFechaNacimiento());
        response.setRol(usuario.getRol());
        response.setDescuentoDuoc(usuario.isDescuentoDuoc());
        response.setPuntos(usuario.getPuntos());
        response.setCodigoReferido(usuario.getCodigoReferido());
        response.setReferidoPor(usuario.getReferidoPor());
        return response;
    }
}
