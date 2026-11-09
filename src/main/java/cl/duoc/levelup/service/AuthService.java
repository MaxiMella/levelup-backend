package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.LoginRequest;
import cl.duoc.levelup.dto.LoginResponse;
import cl.duoc.levelup.dto.UsuarioResponse;
import cl.duoc.levelup.exception.CredencialesInvalidasException;
import cl.duoc.levelup.model.Usuario;
import cl.duoc.levelup.repository.UsuarioRepository;
import cl.duoc.levelup.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Inicio de sesión: revisa correo y contraseña y, si son correctos, entrega un token JWT.
 * El registro y el perfil siguen en UsuarioService; aquí solo está el login.
 */
@Service
public class AuthService {

    /** Mismo mensaje si el correo no existe o si la contraseña es incorrecta: no se revela qué correos están registrados. */
    private static final String MENSAJE_CREDENCIALES = "Correo o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 1. Busca al usuario por correo (sin espacios y sin distinguir mayúsculas, igual que el registro).
     * 2. Compara la contraseña escrita con el hash guardado usando BCrypt (matches); el hash no se "descifra".
     * 3. Genera el token con el correo y el rol, y lo devuelve junto a los datos del usuario (sin contraseña).
     * Si el correo no existe o la contraseña no coincide → 401.
     */
    public LoginResponse login(LoginRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_CREDENCIALES));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }

        String token = jwtService.generarToken(usuario);
        return new LoginResponse(token, "Bearer", UsuarioResponse.desde(usuario));
    }
}
