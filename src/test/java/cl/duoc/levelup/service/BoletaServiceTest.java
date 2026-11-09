package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.BoletaRequest;
import cl.duoc.levelup.dto.DetalleRequest;
import cl.duoc.levelup.exception.AccesoDenegadoException;
import cl.duoc.levelup.exception.ConflictoException;
import cl.duoc.levelup.exception.DatosInvalidosException;
import cl.duoc.levelup.exception.RecursoNoEncontradoException;
import cl.duoc.levelup.model.Boleta;
import cl.duoc.levelup.model.DetalleBoleta;
import cl.duoc.levelup.model.EstadoBoleta;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import cl.duoc.levelup.repository.BoletaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import cl.duoc.levelup.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de BoletaService con Mockito (sin base de datos).
 * Cubren el pago simulado, el descuento de stock, los cálculos de montos y los errores,
 * y las reglas según quién tiene la sesión (usuario del token, historial solo del dueño).
 */
@ExtendWith(MockitoExtension.class)
class BoletaServiceTest {

    @Mock
    private BoletaRepository boletaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private BoletaService boletaService;

    // ----- datos de prueba -----

    private Producto producto(Long id, String nombre, int precio, Integer precioOferta, int stock) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setPrecio(precio);
        producto.setPrecioOferta(precioOferta);
        producto.setStock(stock);
        producto.setStockCritico(1);
        return producto;
    }

    private BoletaRequest request(String correo, Long usuarioId, DetalleRequest... items) {
        return new BoletaRequest("Ana", "Pérez Soto", correo, "Av. Siempre Viva 123", null,
                "Región Metropolitana", "Santiago", null, usuarioId, List.of(items));
    }

    private Usuario usuario(Long id, boolean descuentoDuoc) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCorreo("ana@duocuc.cl");
        usuario.setDescuentoDuoc(descuentoDuoc);
        return usuario;
    }

    /** save devuelve la misma boleta que recibe (como si MySQL la guardara). */
    private void saveDevuelveLaBoleta() {
        when(boletaRepository.save(any(Boleta.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ----- compra pagada / rechazada -----

    @Test
    void crear_compraPagada_descuentaStockYCalculaMontos() {
        // Audífonos: precio 59990 con oferta 49990 (se usa la oferta). Mouse: 19990 sin oferta.
        Producto audifonos = producto(11L, "Audífonos", 59990, 49990, 11);
        Producto mouse = producto(5L, "Mouse", 19990, null, 5);
        when(productoRepository.findById(11L)).thenReturn(Optional.of(audifonos));
        when(productoRepository.findById(5L)).thenReturn(Optional.of(mouse));
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("ana@gmail.com", null,
                new DetalleRequest(11L, 2), new DetalleRequest(5L, 1)));

        assertEquals(EstadoBoleta.PAGADA, boleta.getEstado());
        // Detalles con el precio correcto y la relación en ambos sentidos
        assertEquals(2, boleta.getDetalles().size());
        DetalleBoleta detalleAudifonos = boleta.getDetalles().get(0);
        assertEquals(49990, detalleAudifonos.getPrecioUnitario());
        assertEquals(99980, detalleAudifonos.getSubtotal());
        assertSame(boleta, detalleAudifonos.getBoleta());
        // Montos: 99980 + 19990 = 119970; sin descuento; neto = round(119970 / 1.19) = 100815
        assertEquals(119970, boleta.getSubtotal());
        assertEquals(0, boleta.getDescuento());
        assertEquals(119970, boleta.getTotal());
        assertEquals(100815, boleta.getNeto());
        assertEquals(19155, boleta.getIva());
        assertNull(boleta.getUsuario()); // visitante
        // Stock descontado
        assertEquals(9, audifonos.getStock());
        assertEquals(4, mouse.getStock());
        verify(productoRepository).save(audifonos);
        verify(productoRepository).save(mouse);
    }

    @Test
    void crear_correoConError_quedaRechazadaYNoDescuentaStock() {
        Producto mouse = producto(5L, "Mouse", 19990, null, 5);
        when(productoRepository.findById(5L)).thenReturn(Optional.of(mouse));
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("cliente.ERROR@gmail.com", null, new DetalleRequest(5L, 2)));

        assertEquals(EstadoBoleta.RECHAZADA, boleta.getEstado());
        assertEquals(5, mouse.getStock());                     // stock intacto
        verify(productoRepository, never()).save(any(Producto.class));
        verify(boletaRepository).save(boleta);                 // la boleta rechazada sí se guarda
    }

    // ----- errores -----

    @Test
    void crear_sinStockSuficiente_lanzaConflictoYNoGuarda() {
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto(5L, "Mouse", 19990, null, 2)));

        ConflictoException ex = assertThrows(ConflictoException.class,
                () -> boletaService.crear(request("ana@gmail.com", null, new DetalleRequest(5L, 3))));

        assertTrue(ex.getMessage().contains("Mouse"));         // indica qué producto
        verify(boletaRepository, never()).save(any(Boleta.class));
    }

    @Test
    void crear_productoInexistente_lanzaNoEncontradoYNoGuarda() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> boletaService.crear(request("ana@gmail.com", null, new DetalleRequest(99L, 1))));
        verify(boletaRepository, never()).save(any(Boleta.class));
    }

    @Test
    void crear_usuarioInexistente_lanzaNoEncontrado() {
        when(usuarioRepository.findById(50L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> boletaService.crear(request("ana@gmail.com", 50L, new DetalleRequest(5L, 1))));
        verify(boletaRepository, never()).save(any(Boleta.class));
    }

    @Test
    void crear_productoRepetido_lanzaDatosInvalidos() {
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto(5L, "Mouse", 19990, null, 5)));

        assertThrows(DatosInvalidosException.class, () -> boletaService.crear(request("ana@gmail.com", null,
                new DetalleRequest(5L, 1), new DetalleRequest(5L, 1))));
        verify(boletaRepository, never()).save(any(Boleta.class));
    }

    // ----- cálculos -----

    @Test
    void crear_usuarioDuoc_aplica20PorCientoYCalculaNetoEIva() {
        Usuario duoc = usuario(1L, true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(duoc));
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto(5L, "Silla", 50000, null, 10)));
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("ana@duocuc.cl", 1L, new DetalleRequest(5L, 3)));

        // subtotal 150000; descuento 30000; total 120000; neto round(120000 / 1.19) = 100840; iva 19160
        assertEquals(150000, boleta.getSubtotal());
        assertEquals(30000, boleta.getDescuento());
        assertEquals(120000, boleta.getTotal());
        assertEquals(100840, boleta.getNeto());
        assertEquals(19160, boleta.getIva());
        assertSame(duoc, boleta.getUsuario());
    }

    @Test
    void crear_usuarioDuoc_descuentoSeRedondea() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, true)));
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto(5L, "Mousepad", 12348, null, 10)));
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("ana@duocuc.cl", 1L, new DetalleRequest(5L, 1)));

        // 12348 * 0.20 = 2469.6 -> 2470; total 9878; neto round(8300.84) = 8301; iva 1577
        assertEquals(2470, boleta.getDescuento());
        assertEquals(9878, boleta.getTotal());
        assertEquals(8301, boleta.getNeto());
        assertEquals(1577, boleta.getIva());
    }

    @Test
    void crear_usuarioSinDescuentoDuoc_noTieneDescuento() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario(2L, false)));
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto(5L, "Silla", 50000, null, 10)));
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("ana@gmail.com", 2L, new DetalleRequest(5L, 1)));

        assertEquals(0, boleta.getDescuento());
        assertEquals(50000, boleta.getTotal());
    }

    @Test
    void crear_numeroOrden_usaFechaDeHoyYCorrelativoDeTresDigitos() {
        String prefijoHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto(5L, "Mouse", 19990, null, 5)));
        when(boletaRepository.countByNumeroOrdenStartingWith(prefijoHoy)).thenReturn(4L); // ya hay 4 hoy
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("ana@gmail.com", null, new DetalleRequest(5L, 1)));

        assertEquals(prefijoHoy + "005", boleta.getNumeroOrden());
        assertTrue(boleta.getNumeroOrden().matches("\\d{8}-\\d{3}"));
    }

    // ----- consultas -----

    @Test
    void obtenerPorNumeroOrden_inexistente_lanzaNoEncontrado() {
        when(boletaRepository.findByNumeroOrden("20261106-999")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> boletaService.obtenerPorNumeroOrden("20261106-999"));
    }

    @Test
    void historialUsuario_usuarioInexistente_lanzaNoEncontrado() {
        when(usuarioRepository.existsById(50L)).thenReturn(false);

        assertThrows(RecursoNoEncontradoException.class, () -> boletaService.historialUsuario(50L));
    }

    // ----- según quién tiene la sesión (JWT) -----

    private Usuario usuarioConRol(Long id, String correo, Rol rol, boolean descuentoDuoc) {
        Usuario usuario = usuario(id, descuentoDuoc);
        usuario.setCorreo(correo);
        usuario.setRol(rol);
        return usuario;
    }

    @Test
    void crear_conSesionDeCliente_usaElUsuarioDelTokenEIgnoraUsuarioId() {
        // Pedro (id 3, Duoc) tiene sesión, pero el JSON dice usuarioId 99: se usa Pedro.
        Usuario pedro = usuarioConRol(3L, "pedro@duocuc.cl", Rol.CLIENTE, true);
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.of(pedro));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(pedro));
        when(productoRepository.findById(14L)).thenReturn(Optional.of(producto(14L, "Mousepad", 18990, 14990, 20)));
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("pedro@duocuc.cl", 99L, new DetalleRequest(14L, 1)),
                "pedro@duocuc.cl");

        assertSame(pedro, boleta.getUsuario());
        assertEquals(2998, boleta.getDescuento()); // 20% de 14990, el descuento Duoc de Pedro
        verify(usuarioRepository, never()).findById(99L);
    }

    @Test
    void crear_visitanteConUsuarioId_loIgnoraYNoAplicaDescuento() {
        // Sin token alguien envía usuarioId 3 (Pedro) para usar su descuento: se ignora.
        when(productoRepository.findById(14L)).thenReturn(Optional.of(producto(14L, "Mousepad", 18990, 14990, 20)));
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("ana@gmail.com", 3L, new DetalleRequest(14L, 1)), null);

        assertNull(boleta.getUsuario());
        assertEquals(0, boleta.getDescuento());
        verify(usuarioRepository, never()).findById(any());
    }

    @Test
    void crear_conSesionDeAdmin_respetaElUsuarioIdDelJson() {
        Usuario admin = usuarioConRol(1L, "admin@levelup.cl", Rol.ADMIN, false);
        Usuario pedro = usuarioConRol(3L, "pedro@duocuc.cl", Rol.CLIENTE, true);
        when(usuarioRepository.findByCorreoIgnoreCase("admin@levelup.cl")).thenReturn(Optional.of(admin));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(pedro));
        when(productoRepository.findById(14L)).thenReturn(Optional.of(producto(14L, "Mousepad", 18990, 14990, 20)));
        saveDevuelveLaBoleta();

        Boleta boleta = boletaService.crear(request("pedro@duocuc.cl", 3L, new DetalleRequest(14L, 1)),
                "admin@levelup.cl");

        assertSame(pedro, boleta.getUsuario());
    }

    @Test
    void historialUsuario_clientePideHistorialAjeno_lanzaAccesoDenegado() {
        Usuario pedro = usuarioConRol(3L, "pedro@duocuc.cl", Rol.CLIENTE, true);
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.of(pedro));

        assertThrows(AccesoDenegadoException.class, () -> boletaService.historialUsuario(5L, "pedro@duocuc.cl"));
        verify(boletaRepository, never()).findByUsuarioIdOrderByFechaDesc(any());
    }

    @Test
    void historialUsuario_clientePideSuPropioHistorial_loDevuelve() {
        Usuario pedro = usuarioConRol(3L, "pedro@duocuc.cl", Rol.CLIENTE, true);
        List<Boleta> boletas = List.of(new Boleta());
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.of(pedro));
        when(usuarioRepository.existsById(3L)).thenReturn(true);
        when(boletaRepository.findByUsuarioIdOrderByFechaDesc(3L)).thenReturn(boletas);

        assertEquals(boletas, boletaService.historialUsuario(3L, "pedro@duocuc.cl"));
    }

    @Test
    void historialUsuario_adminPuedeVerElDeCualquierUsuario() {
        Usuario admin = usuarioConRol(1L, "admin@levelup.cl", Rol.ADMIN, false);
        List<Boleta> boletas = List.of(new Boleta());
        when(usuarioRepository.findByCorreoIgnoreCase("admin@levelup.cl")).thenReturn(Optional.of(admin));
        when(usuarioRepository.existsById(3L)).thenReturn(true);
        when(boletaRepository.findByUsuarioIdOrderByFechaDesc(3L)).thenReturn(boletas);

        assertEquals(boletas, boletaService.historialUsuario(3L, "admin@levelup.cl"));
    }

    @Test
    void misCompras_devuelveLasBoletasDelUsuarioDelToken() {
        Usuario pedro = usuarioConRol(3L, "pedro@duocuc.cl", Rol.CLIENTE, true);
        List<Boleta> boletas = List.of(new Boleta(), new Boleta());
        when(usuarioRepository.findByCorreoIgnoreCase("pedro@duocuc.cl")).thenReturn(Optional.of(pedro));
        when(boletaRepository.findByUsuarioIdOrderByFechaDesc(3L)).thenReturn(boletas);

        assertEquals(2, boletaService.misCompras("pedro@duocuc.cl").size());
    }
}
