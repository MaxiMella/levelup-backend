package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.RegistroRequest;
import cl.duoc.levelup.dto.UsuarioRequest;
import cl.duoc.levelup.dto.UsuarioResponse;
import cl.duoc.levelup.exception.ConflictoException;
import cl.duoc.levelup.exception.DatosInvalidosException;
import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import cl.duoc.levelup.repository.BoletaRepository;
import cl.duoc.levelup.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de UsuarioService con Mockito (sin base de datos).
 * El PasswordEncoder también es un mock: se comprueba que el service lo use y que guarde el hash,
 * no la contraseña en texto plano.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private BoletaRepository boletaRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    // ----- datos de prueba -----

    private RegistroRequest registro(String correo, LocalDate fechaNacimiento, String codigoReferido) {
        return new RegistroRequest("María José", "Pérez", correo, "clave1234", fechaNacimiento, codigoReferido);
    }

    private UsuarioRequest usuarioRequest(String correo, String password) {
        return new UsuarioRequest("Ana", "Soto", correo, password, LocalDate.of(2000, 1, 15), Rol.VENDEDOR);
    }

    private Usuario usuarioExistente(Long id, String correo) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Ana");
        usuario.setCorreo(correo);
        usuario.setPassword("HASH_ACTUAL");
        usuario.setPuntos(0);
        return usuario;
    }

    /** save devuelve el mismo usuario que recibe (como si MySQL lo guardara). */
    private void saveDevuelveElUsuario() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ----- registro -----

    @Test
    void registrar_correcto_cifraPasswordDetectaDuocYGeneraCodigo() {
        when(passwordEncoder.encode("clave1234")).thenReturn("HASH_BCRYPT");
        saveDevuelveElUsuario();

        UsuarioResponse respuesta = usuarioService.registrar(
                registro("  Maria.Perez@DuocUC.cl ", LocalDate.of(2000, 5, 10), null));

        // ArgumentCaptor "atrapa" el Usuario que se le pasó a save para revisar cómo se guardó
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();

        assertEquals("HASH_BCRYPT", guardado.getPassword());          // se guarda el hash, no "clave1234"
        assertEquals("maria.perez@duocuc.cl", respuesta.getCorreo()); // sin espacios y en minúsculas
        assertTrue(respuesta.isDescuentoDuoc());
        assertEquals(Rol.CLIENTE, respuesta.getRol());
        assertEquals(0, respuesta.getPuntos());
        // "María José" -> letras sin tilde "MaraJos" -> primeras 5 en mayúsculas "MARAJ" + 2 dígitos
        assertTrue(respuesta.getCodigoReferido().matches("LVLMARAJ\\d{2}"));
    }

    @Test
    void registrar_correoNoDuoc_sinDescuento() {
        when(passwordEncoder.encode("clave1234")).thenReturn("HASH_BCRYPT");
        saveDevuelveElUsuario();

        UsuarioResponse respuesta = usuarioService.registrar(registro("maria@gmail.com", LocalDate.of(2000, 5, 10), null));

        assertFalse(respuesta.isDescuentoDuoc());
    }

    @Test
    void registrar_correoRepetido_lanzaConflictoYNoGuarda() {
        when(usuarioRepository.findByCorreoIgnoreCase("ana@gmail.com"))
                .thenReturn(Optional.of(usuarioExistente(1L, "ana@gmail.com")));

        assertThrows(ConflictoException.class,
                () -> usuarioService.registrar(registro("Ana@Gmail.com", LocalDate.of(2000, 5, 10), null)));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registrar_menorDeEdad_lanzaDatosInvalidosYNoGuarda() {
        LocalDate tiene17 = LocalDate.now().minusYears(17);

        assertThrows(DatosInvalidosException.class,
                () -> usuarioService.registrar(registro("nina@gmail.com", tiene17, null)));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registrar_cumple18Hoy_esValido() {
        when(passwordEncoder.encode("clave1234")).thenReturn("HASH_BCRYPT");
        saveDevuelveElUsuario();

        UsuarioResponse respuesta = usuarioService.registrar(
                registro("joven@gmail.com", LocalDate.now().minusYears(18), null));

        assertEquals("joven@gmail.com", respuesta.getCorreo());
    }

    @Test
    void registrar_conReferidoValido_sumaPuntosAAmbos() {
        Usuario pedro = usuarioExistente(3L, "pedro@duocuc.cl");
        pedro.setCodigoReferido("LVLPEDRO");
        pedro.setPuntos(100);
        // lenient: findByCodigoReferido también se llama con otros códigos al generar el código del nuevo usuario
        lenient().when(usuarioRepository.findByCodigoReferido(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByCodigoReferido("LVLPEDRO")).thenReturn(Optional.of(pedro));
        when(passwordEncoder.encode("clave1234")).thenReturn("HASH_BCRYPT");
        saveDevuelveElUsuario();

        UsuarioResponse respuesta = usuarioService.registrar(
                registro("maria@gmail.com", LocalDate.of(2000, 5, 10), " lvlpedro ")); // se normaliza

        assertEquals(100, respuesta.getPuntos());          // el nuevo gana 100
        assertEquals("LVLPEDRO", respuesta.getReferidoPor());
        assertEquals(200, pedro.getPuntos());              // quien lo refirió también gana 100
        verify(usuarioRepository).save(pedro);
    }

    @Test
    void registrar_conReferidoInexistente_lanzaDatosInvalidosYNoGuarda() {
        assertThrows(DatosInvalidosException.class,
                () -> usuarioService.registrar(registro("maria@gmail.com", LocalDate.of(2000, 5, 10), "NOEXISTE")));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registrar_sinReferido_noGuardaReferidoPor() {
        when(passwordEncoder.encode("clave1234")).thenReturn("HASH_BCRYPT");
        saveDevuelveElUsuario();

        UsuarioResponse respuesta = usuarioService.registrar(registro("maria@gmail.com", LocalDate.of(2000, 5, 10), "  "));

        assertNull(respuesta.getReferidoPor());
        assertEquals(0, respuesta.getPuntos());
    }

    // ----- mantenedor del administrador -----

    @Test
    void listar_conRol_filtraPorRol() {
        when(usuarioRepository.findByRol(Rol.VENDEDOR)).thenReturn(List.of(usuarioExistente(2L, "v@levelup.cl")));

        List<UsuarioResponse> resultado = usuarioService.listar(Rol.VENDEDOR);

        assertEquals(1, resultado.size());
        verify(usuarioRepository, never()).findAll();
    }

    @Test
    void crear_sinPassword_lanzaDatosInvalidos() {
        assertThrows(DatosInvalidosException.class, () -> usuarioService.crear(usuarioRequest("ana@gmail.com", "")));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void crear_conRolElegido_cifraPassword() {
        when(passwordEncoder.encode("clave1234")).thenReturn("HASH_BCRYPT");
        saveDevuelveElUsuario();

        UsuarioResponse respuesta = usuarioService.crear(usuarioRequest("ana@levelup.cl", "clave1234"));

        assertEquals(Rol.VENDEDOR, respuesta.getRol());
        verify(passwordEncoder).encode("clave1234");
    }

    @Test
    void actualizar_passwordVacia_mantieneLaActual() {
        Usuario existente = usuarioExistente(1L, "ana@gmail.com");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByCorreoIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(existente));
        saveDevuelveElUsuario();

        usuarioService.actualizar(1L, usuarioRequest("ana@gmail.com", ""));

        assertEquals("HASH_ACTUAL", existente.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void actualizar_cambiaCorreoADuoc_recalculaDescuento() {
        Usuario existente = usuarioExistente(1L, "ana@gmail.com");
        existente.setDescuentoDuoc(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existente));
        saveDevuelveElUsuario();

        UsuarioResponse respuesta = usuarioService.actualizar(1L, usuarioRequest("ana@duoc.cl", null));

        assertTrue(respuesta.isDescuentoDuoc());
        assertEquals("ana@duoc.cl", respuesta.getCorreo());
    }

    @Test
    void actualizar_conCorreoDeOtroUsuario_lanzaConflicto() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioExistente(1L, "ana@gmail.com")));
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl"))
                .thenReturn(Optional.of(usuarioExistente(3L, "pedro@duocuc.cl")));

        assertThrows(ConflictoException.class,
                () -> usuarioService.actualizar(1L, usuarioRequest("pedro@duocuc.cl", null)));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void eliminar_conBoletas_lanzaConflictoYNoElimina() {
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuarioExistente(3L, "pedro@duocuc.cl")));
        when(boletaRepository.existsByUsuarioId(3L)).thenReturn(true);

        assertThrows(ConflictoException.class, () -> usuarioService.eliminar(3L));
        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    @Test
    void eliminar_sinBoletas_loElimina() {
        Usuario existente = usuarioExistente(2L, "ana@gmail.com");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(existente));
        when(boletaRepository.existsByUsuarioId(2L)).thenReturn(false);

        usuarioService.eliminar(2L);

        verify(usuarioRepository).delete(existente);
    }
}
