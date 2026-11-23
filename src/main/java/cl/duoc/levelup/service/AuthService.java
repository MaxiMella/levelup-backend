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
 * También renueva el token antes de que venza (refresh).
 * El registro y el perfil siguen en UsuarioService; aquí solo están el login y la renovación.
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
     * 3. Genera el token con el correo y el rol, y lo devuelve junto a su vencimiento (expiraEn)
     *    y los datos del usuario (sin contraseña).
     * Si el correo no existe o la contraseña no coincide → 401.
     */
    public LoginResponse login(LoginRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_CREDENCIALES));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }

        return armarRespuesta(usuario);
    }

    /**
     * Renueva el token (POST /api/v1/auth/refresh). Para llegar aquí, JwtAuthenticationFilter ya revisó que el
     * token enviado sea válido y no esté vencido; el correo sale de ese token (authentication.getName()).
     * 1. Busca al usuario en la base de datos: el token nuevo lleva sus datos ACTUALES
     *    (si un admin le cambió el rol, el token nuevo trae el rol nuevo).
     * 2. Genera un token nuevo con otros 60 minutos y responde lo mismo que el login.
     * Si el usuario ya no existe → 401. Normalmente lo rechaza antes el filtro ("El usuario del token ya no existe");
     * esta revisión cubre el caso de que lo eliminen justo entre el filtro y este método.
     */
    public LoginResponse renovarToken(String correo) {
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new CredencialesInvalidasException("El usuario del token ya no existe"));

        return armarRespuesta(usuario);
    }

    /**
     * Genera el token y arma la respuesta que comparten login y renovación:
     * token, tipo "Bearer", fecha de vencimiento (expiraEn) y datos del usuario sin contraseña.
     */
    private LoginResponse armarRespuesta(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        long expiraEn = jwtService.obtenerVencimiento(token).getTime();
        return new LoginResponse(token, "Bearer", expiraEn, UsuarioResponse.desde(usuario));
    }
}
