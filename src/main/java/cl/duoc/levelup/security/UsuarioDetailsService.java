package cl.duoc.levelup.security;

import cl.duoc.levelup.model.Usuario;
import cl.duoc.levelup.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Le explica a Spring Security cómo encontrar a un usuario: lo busca por correo en la tabla usuario.
 * Devuelve un UserDetails (la forma en que Spring Security representa al usuario) con:
 * - username = correo,
 * - password = el hash BCrypt,
 * - authority = "ROLE_" + rol, ej: ROLE_ADMIN. El prefijo ROLE_ es el que espera hasRole("ADMIN")
 *   en SecurityConfig (hasRole("ADMIN") busca la authority "ROLE_ADMIN").
 * El filtro JWT lo usa en cada petición, así el rol sale siempre de la base de datos:
 * si el administrador le cambia el rol a alguien, el cambio aplica de inmediato.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new UsernameNotFoundException("No existe el usuario " + correo));

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPassword())
                .authorities("ROLE_" + usuario.getRol().name())
                .build();
    }
}
