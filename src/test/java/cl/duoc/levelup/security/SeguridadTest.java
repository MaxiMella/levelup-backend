package cl.duoc.levelup.security;

import cl.duoc.levelup.config.CorsConfig;
import cl.duoc.levelup.dto.BoletaRequest;
import cl.duoc.levelup.dto.LoginRequest;
import cl.duoc.levelup.dto.LoginResponse;
import cl.duoc.levelup.dto.ProductoRequest;
import cl.duoc.levelup.dto.UsuarioResponse;
import cl.duoc.levelup.exception.AccesoDenegadoException;
import cl.duoc.levelup.exception.CredencialesInvalidasException;
import cl.duoc.levelup.model.Boleta;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import cl.duoc.levelup.repository.UsuarioRepository;
import cl.duoc.levelup.service.AuthService;
import cl.duoc.levelup.service.BoletaService;
import cl.duoc.levelup.service.CategoriaService;
import cl.duoc.levelup.service.ProductoService;
import cl.duoc.levelup.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de permisos por rol con MockMvc: simulan peticiones HTTP sin levantar el servidor ni usar MySQL.
 *
 * - @WebMvcTest carga solo la capa web (controllers, GlobalExceptionHandler, Jackson y Spring Security).
 * - @Import agrega nuestra configuración de seguridad, que @WebMvcTest no carga sola.
 * - @MockitoBean reemplaza los services y el repositorio por mocks: aquí se prueba QUIÉN puede entrar a cada ruta,
 *   no la lógica de negocio (esa está en los tests de cada service).
 * - @WithMockUser simula un usuario con sesión y un rol, sin necesidad de token.
 * - En las pruebas con "token real" se genera un JWT con JwtService y se envía en el header Authorization,
 *   así se prueba el camino completo: filtro JWT → UsuarioDetailsService → reglas de SecurityConfig → controller.
 */
@WebMvcTest(properties = {
        "jwt.secret=clave-de-prueba-para-los-tests-de-seguridad-123456",
        "jwt.expiracion=3600000"
})
@Import({SecurityConfig.class, CorsConfig.class, JwtService.class, UsuarioDetailsService.class,
        SeguridadErrorHandler.class})
class SeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ProductoService productoService;

    @MockitoBean
    private CategoriaService categoriaService;

    @MockitoBean
    private BoletaService boletaService;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private AuthService authService;

    /** Lo usa UsuarioDetailsService (real) para cargar al usuario del token. */
    @MockitoBean
    private UsuarioRepository usuarioRepository;

    // ----- datos de prueba -----

    private static final String PRODUCTO_JSON = """
            { "nombre": "Control Pro", "precio": 49990, "stock": 10, "stockCritico": 2, "categoriaId": 1 }
            """;

    private static final String BOLETA_JSON = """
            { "nombre": "Ana", "apellidos": "Pérez", "correo": "ana@gmail.com", "calle": "Av. Siempre Viva 123",
              "region": "Región Metropolitana", "comuna": "Santiago", "usuarioId": 3,
              "items": [ { "productoId": 1, "cantidad": 1 } ] }
            """;

    private Usuario usuario(Long id, String correo, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCorreo(correo);
        usuario.setPassword("HASH");
        usuario.setRol(rol);
        return usuario;
    }

    /** Genera un token real y deja al usuario "guardado" en el repositorio simulado. */
    private String tokenDe(Usuario usuario) {
        when(usuarioRepository.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        return "Bearer " + jwtService.generarToken(usuario);
    }

    // ===================== Visitante (sin token) =====================

    @Test
    void visitante_puedeVerProductosYCategorias() throws Exception {
        when(productoService.listar(null, null)).thenReturn(List.of(new Producto()));

        mockMvc.perform(get("/api/v1/productos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/productos/ofertas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categorias")).andExpect(status().isOk());
    }

    @Test
    void visitante_noPuedeCrearProductos_401ConJsonDeError() throws Exception {
        mockMvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(PRODUCTO_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.mensaje").value("Debes iniciar sesión para acceder a este recurso"))
                .andExpect(jsonPath("$.fecha").exists());

        verify(productoService, never()).crear(any());
    }

    @Test
    void visitante_noPuedeVerStockCritico() throws Exception {
        mockMvc.perform(get("/api/v1/productos/criticos")).andExpect(status().isUnauthorized());
    }

    @Test
    void visitante_puedeComprarSinCuenta() throws Exception {
        when(boletaService.crear(any(BoletaRequest.class), isNull())).thenReturn(new Boleta());

        mockMvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).content(BOLETA_JSON))
                .andExpect(status().isCreated());

        // Sin token, el controller le pasa null al service (compra de visitante).
        verify(boletaService).crear(any(BoletaRequest.class), isNull());
    }

    @Test
    void visitante_noPuedeVerBoletasNiUsuarios() throws Exception {
        mockMvc.perform(get("/api/v1/boletas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    // ===================== VENDEDOR =====================

    @Test
    @WithMockUser(username = "vendedor@levelup.cl", roles = "VENDEDOR")
    void vendedor_puedeVerBoletasYStockCritico() throws Exception {
        mockMvc.perform(get("/api/v1/boletas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/boletas/1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/productos/criticos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "vendedor@levelup.cl", roles = "VENDEDOR")
    void vendedor_noPuedeCrearProductos_403ConJsonDeError() throws Exception {
        mockMvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(PRODUCTO_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción"));

        verify(productoService, never()).crear(any());
    }

    @Test
    @WithMockUser(username = "vendedor@levelup.cl", roles = "VENDEDOR")
    void vendedor_noPuedeVerUsuariosNiHistorialesDeClientes() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/boletas/usuario/3")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/categorias/1")).andExpect(status().isForbidden());
    }

    // ===================== CLIENTE =====================

    @Test
    @WithMockUser(username = "pedro@duocuc.cl", roles = "CLIENTE")
    void cliente_noPuedeVerTodasLasBoletasNiStockCritico() throws Exception {
        mockMvc.perform(get("/api/v1/boletas")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/boletas/numero/20261109-001")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/productos/criticos")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "pedro@duocuc.cl", roles = "CLIENTE")
    void cliente_noPuedeVerElHistorialDeOtroCliente() throws Exception {
        // La regla "solo el dueño" está en BoletaService (probada en BoletaServiceTest);
        // aquí se comprueba que el controller le pase el correo del token y que el 403 llegue como JSON.
        when(boletaService.historialUsuario(5L, "pedro@duocuc.cl"))
                .thenThrow(new AccesoDenegadoException("Solo puedes ver tu propio historial de compras"));

        mockMvc.perform(get("/api/v1/boletas/usuario/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Solo puedes ver tu propio historial de compras"));
    }

    @Test
    @WithMockUser(username = "pedro@duocuc.cl", roles = "CLIENTE")
    void cliente_puedeVerSusCompras() throws Exception {
        when(boletaService.misCompras("pedro@duocuc.cl")).thenReturn(List.of(new Boleta(), new Boleta()));

        mockMvc.perform(get("/api/v1/boletas/mis-compras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = "pedro@duocuc.cl", roles = "CLIENTE")
    void cliente_alComprar_seUsaElCorreoDelToken() throws Exception {
        when(boletaService.crear(any(BoletaRequest.class), eq("pedro@duocuc.cl"))).thenReturn(new Boleta());

        mockMvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).content(BOLETA_JSON))
                .andExpect(status().isCreated());

        verify(boletaService).crear(any(BoletaRequest.class), eq("pedro@duocuc.cl"));
    }

    @Test
    @WithMockUser(username = "pedro@duocuc.cl", roles = "CLIENTE")
    void cliente_noPuedeUsarElMantenedorDeUsuarios() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/3")).andExpect(status().isForbidden());
    }

    // ===================== ADMIN =====================

    @Test
    @WithMockUser(username = "admin@levelup.cl", roles = "ADMIN")
    void admin_puedeAdministrarProductosYCategorias() throws Exception {
        when(productoService.crear(any(ProductoRequest.class))).thenReturn(new Producto());

        mockMvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(PRODUCTO_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/categorias/1")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/productos/criticos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@levelup.cl", roles = "ADMIN")
    void admin_puedeVerBoletasUsuariosEHistoriales() throws Exception {
        mockMvc.perform(get("/api/v1/boletas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/boletas/usuario/3")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/boletas/mis-compras")).andExpect(status().isOk());
    }

    // ===================== Con token real (filtro JWT) =====================

    @Test
    void tokenValido_meDevuelveElUsuarioDelToken() throws Exception {
        String token = tokenDe(usuario(3L, "pedro@duocuc.cl", Rol.CLIENTE));
        UsuarioResponse pedro = new UsuarioResponse();
        pedro.setCorreo("pedro@duocuc.cl");
        pedro.setRol(Rol.CLIENTE);
        when(usuarioService.obtenerPerfil("pedro@duocuc.cl")).thenReturn(pedro);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("pedro@duocuc.cl"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void tokenDeVendedor_noPuedeCrearProductos() throws Exception {
        String token = tokenDe(usuario(2L, "vendedor@levelup.cl", Rol.VENDEDOR));

        mockMvc.perform(post("/api/v1/productos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCTO_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenDeAdmin_puedeVerUsuarios() throws Exception {
        String token = tokenDe(usuario(1L, "admin@levelup.cl", Rol.ADMIN));

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void tokenInvalido_enRutaProtegida_401ConMensajeDeToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer token.falso.123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El token no es válido o expiró. Inicia sesión nuevamente"));
    }

    @Test
    void tokenInvalido_enRutaPublica_seAtiendeComoVisitante() throws Exception {
        mockMvc.perform(get("/api/v1/productos").header("Authorization", "Bearer token.falso.123"))
                .andExpect(status().isOk());
    }

    // ===================== Login =====================

    @Test
    void login_correcto_devuelveToken() throws Exception {
        UsuarioResponse pedro = new UsuarioResponse();
        pedro.setCorreo("pedro@duocuc.cl");
        pedro.setRol(Rol.CLIENTE);
        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse("abc.def.ghi", "Bearer", pedro));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"correo\": \"pedro@duocuc.cl\", \"password\": \"pedro1234\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("abc.def.ghi"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.usuario.password").doesNotExist());
    }

    @Test
    void login_incorrecto_401ConJsonDeError() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new CredencialesInvalidasException("Correo o contraseña incorrectos"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"correo\": \"pedro@duocuc.cl\", \"password\": \"malaClave1\" }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"));
    }

    @Test
    void login_sinPassword_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"correo\": \"pedro@duocuc.cl\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.password").value("La contraseña es obligatoria"));
    }
}
