package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.LoginRequest;
import cl.duoc.levelup.dto.LoginResponse;
import cl.duoc.levelup.exception.CredencialesInvalidasException;
import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import cl.duoc.levelup.repository.UsuarioRepository;
import cl.duoc.levelup.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias del login (AuthService) con Mockito: sin base de datos.
 * JwtService también es un mock: aquí solo importa que se genere el token cuando las credenciales son correctas.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private Usuario pedro() {
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setNombre("Pedro");
        usuario.setCorreo("pedro@duocuc.cl");
        usuario.setPassword("HASH_PEDRO");
        usuario.setRol(Rol.CLIENTE);
        return usuario;
    }

    @Test
    void login_correcto_devuelveTokenTipoBearerYUsuarioSinPassword() {
        Usuario pedro = pedro();
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.of(pedro));
        when(passwordEncoder.matches("pedro1234", "HASH_PEDRO")).thenReturn(true);
        when(jwtService.generarToken(pedro)).thenReturn("TOKEN_DE_PRUEBA");

        // El correo viene con espacios y mayúsculas: se normaliza igual que en el registro.
        LoginResponse respuesta = authService.login(new LoginRequest("  Pedro@DuocUC.cl ", "pedro1234"));

        assertEquals("TOKEN_DE_PRUEBA", respuesta.getToken());
        assertEquals("Bearer", respuesta.getTipo());
        assertEquals("pedro@duocuc.cl", respuesta.getUsuario().getCorreo());
        assertEquals(Rol.CLIENTE, respuesta.getUsuario().getRol());
    }

    @Test
    void login_passwordIncorrecta_lanzaCredencialesInvalidasYNoGeneraToken() {
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.of(pedro()));
        when(passwordEncoder.matches("otraClave1", "HASH_PEDRO")).thenReturn(false);

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest("pedro@duocuc.cl", "otraClave1")));

        assertEquals("Correo o contraseña incorrectos", ex.getMessage());
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void login_correoInexistente_lanzaElMismoMensaje() {
        when(usuarioRepository.findByCorreoIgnoreCase("nadie@gmail.com")).thenReturn(Optional.empty());

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest("nadie@gmail.com", "clave1234")));

        // Mismo mensaje que con contraseña incorrecta: no se revela si el correo existe.
        assertEquals("Correo o contraseña incorrectos", ex.getMessage());
        verify(jwtService, never()).generarToken(any());
    }
}
