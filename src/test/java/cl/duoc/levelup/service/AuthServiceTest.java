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

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias del login y de la renovación del token (AuthService) con Mockito: sin base de datos.
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

    /** Fecha de vencimiento ficticia (milisegundos epoch) que devuelve el JwtService simulado. */
    private static final long VENCIMIENTO = 1795442400000L;

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
        when(jwtService.obtenerVencimiento("TOKEN_DE_PRUEBA")).thenReturn(new Date(VENCIMIENTO));

        // El correo viene con espacios y mayúsculas: se normaliza igual que en el registro.
        LoginResponse respuesta = authService.login(new LoginRequest("  Pedro@DuocUC.cl ", "pedro1234"));

        assertEquals("TOKEN_DE_PRUEBA", respuesta.getToken());
        assertEquals("Bearer", respuesta.getTipo());
        assertEquals(VENCIMIENTO, respuesta.getExpiraEn());
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

    // ===================== Renovar token (refresh) =====================

    @Test
    void renovarToken_usuarioExiste_devuelveTokenNuevoConExpiraEn() {
        Usuario pedro = pedro();
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.of(pedro));
        when(jwtService.generarToken(pedro)).thenReturn("TOKEN_NUEVO");
        when(jwtService.obtenerVencimiento("TOKEN_NUEVO")).thenReturn(new Date(VENCIMIENTO));

        LoginResponse respuesta = authService.renovarToken("pedro@duocuc.cl");

        assertEquals("TOKEN_NUEVO", respuesta.getToken());
        assertEquals("Bearer", respuesta.getTipo());
        assertEquals(VENCIMIENTO, respuesta.getExpiraEn());
        assertEquals("pedro@duocuc.cl", respuesta.getUsuario().getCorreo());
        // Al renovar no se pide la contraseña: basta con el token válido.
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void renovarToken_rolCambiadoPorAdmin_tokenNuevoUsaElRolActual() {
        // En la base de datos Pedro ya es VENDEDOR (un admin le cambió el rol después del login).
        Usuario pedro = pedro();
        pedro.setRol(Rol.VENDEDOR);
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.of(pedro));
        when(jwtService.generarToken(pedro)).thenReturn("TOKEN_NUEVO");
        when(jwtService.obtenerVencimiento("TOKEN_NUEVO")).thenReturn(new Date(VENCIMIENTO));

        LoginResponse respuesta = authService.renovarToken("pedro@duocuc.cl");

        assertEquals(Rol.VENDEDOR, respuesta.getUsuario().getRol());
        verify(jwtService).generarToken(pedro); // el token se genera con el usuario leído de la base de datos
    }

    @Test
    void renovarToken_usuarioBorrado_lanzaCredencialesInvalidas401YNoGeneraToken() {
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.empty());

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.renovarToken("pedro@duocuc.cl"));

        assertEquals("El usuario del token ya no existe", ex.getMessage());
        verify(jwtService, never()).generarToken(any());
    }
}
