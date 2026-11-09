package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.DashboardResponse;
import cl.duoc.levelup.dto.ProductoVendidoResponse;
import cl.duoc.levelup.dto.ReporteVentasResponse;
import cl.duoc.levelup.dto.VentaCategoriaResponse;
import cl.duoc.levelup.model.Boleta;
import cl.duoc.levelup.model.Categoria;
import cl.duoc.levelup.model.DetalleBoleta;
import cl.duoc.levelup.model.EstadoBoleta;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.repository.BoletaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import cl.duoc.levelup.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de ReporteService con Mockito (sin base de datos).
 * Se arman boletas a mano con sus detalles y se comprueba que las métricas sean las mismas que calculaba el frontend.
 */
@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock
    private BoletaRepository boletaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ReporteService reporteService;

    // ----- datos de prueba -----

    private final Categoria consolas = categoria("Consolas");
    private final Categoria perifericos = categoria("Periféricos");

    private final Producto ps5 = producto(1L, "PlayStation 5", consolas);
    private final Producto mouse = producto(5L, "Mouse Gamer", perifericos);
    private final Producto audifonos = producto(11L, "Audífonos 7.1", perifericos);

    private Categoria categoria(String nombre) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        return categoria;
    }

    private Producto producto(Long id, String nombre, Categoria categoria) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setCategoria(categoria);
        return producto;
    }

    private DetalleBoleta detalle(Producto producto, int cantidad, int precioUnitario) {
        DetalleBoleta detalle = new DetalleBoleta();
        detalle.setProducto(producto);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(precioUnitario);
        detalle.setSubtotal(cantidad * precioUnitario);
        return detalle;
    }

    /** Boleta con sus detalles; el total y el descuento se indican a mano para controlar los resultados. */
    private Boleta boleta(EstadoBoleta estado, int total, int descuento, DetalleBoleta... detalles) {
        Boleta boleta = new Boleta();
        boleta.setEstado(estado);
        boleta.setTotal(total);
        boleta.setDescuento(descuento);
        for (DetalleBoleta detalle : detalles) {
            boleta.agregarDetalle(detalle);
        }
        return boleta;
    }

    // ===================== Dashboard =====================

    @Test
    void obtenerDashboard_sumaSoloPagadasYCuentaTodasLasOrdenes() {
        Boleta pagada1 = boleta(EstadoBoleta.PAGADA, 499990, 0, detalle(ps5, 1, 499990));
        Boleta pagada2 = boleta(EstadoBoleta.PAGADA, 23992, 5998, detalle(mouse, 1, 29990));
        Boleta rechazada = boleta(EstadoBoleta.RECHAZADA, 59990, 0, detalle(audifonos, 1, 59990));
        when(boletaRepository.findByEstado(EstadoBoleta.PAGADA)).thenReturn(List.of(pagada1, pagada2));
        when(boletaRepository.count()).thenReturn(3L);
        when(productoRepository.count()).thenReturn(15L);
        when(usuarioRepository.count()).thenReturn(3L);
        when(productoRepository.findCriticos()).thenReturn(List.of(mouse));
        when(boletaRepository.findTop5ByOrderByFechaDesc()).thenReturn(List.of(rechazada, pagada2, pagada1));

        DashboardResponse dashboard = reporteService.obtenerDashboard();

        assertEquals(523982, dashboard.getVentasTotales()); // 499990 + 23992; la rechazada no suma
        assertEquals(3, dashboard.getOrdenesRealizadas());  // pagadas y rechazadas
        assertEquals(15, dashboard.getTotalProductos());
        assertEquals(3, dashboard.getTotalUsuarios());
        assertEquals(List.of(mouse), dashboard.getProductosCriticos());
        assertEquals(3, dashboard.getUltimasOrdenes().size());
    }

    @Test
    void obtenerDashboard_sinVentas_ventasEnCero() {
        when(boletaRepository.findByEstado(EstadoBoleta.PAGADA)).thenReturn(List.of());
        when(productoRepository.count()).thenReturn(15L);

        DashboardResponse dashboard = reporteService.obtenerDashboard();

        assertEquals(0, dashboard.getVentasTotales());
        assertEquals(0, dashboard.getOrdenesRealizadas());
        assertEquals(15, dashboard.getTotalProductos());
        assertTrue(dashboard.getUltimasOrdenes().isEmpty());
    }

    // ===================== Reporte de ventas =====================

    @Test
    void obtenerReporteVentas_sinVentas_todoEnCeroYTicketCero() {
        when(boletaRepository.findByEstado(EstadoBoleta.PAGADA)).thenReturn(List.of());
        when(boletaRepository.countByEstado(EstadoBoleta.RECHAZADA)).thenReturn(2L);

        ReporteVentasResponse reporte = reporteService.obtenerReporteVentas();

        assertEquals(0, reporte.getIngresosTotales());
        assertEquals(0, reporte.getOrdenesPagadas());
        assertEquals(0, reporte.getTicketPromedio()); // no divide por cero
        assertEquals(0, reporte.getUnidadesVendidas());
        assertEquals(0, reporte.getTotalDescuentos());
        assertEquals(2, reporte.getPagosRechazados());
        assertTrue(reporte.getVentasPorCategoria().isEmpty());
        assertTrue(reporte.getRankingProductos().isEmpty());
    }

    @Test
    void obtenerReporteVentas_rechazadasNoCuentanComoVentas() {
        Boleta pagada = boleta(EstadoBoleta.PAGADA, 99980, 0, detalle(audifonos, 2, 49990));
        Boleta rechazada = boleta(EstadoBoleta.RECHAZADA, 499990, 0, detalle(ps5, 1, 499990));
        when(boletaRepository.findByEstado(EstadoBoleta.PAGADA)).thenReturn(List.of(pagada));
        when(boletaRepository.countByEstado(EstadoBoleta.RECHAZADA)).thenReturn(1L);
        // Si el service usara findAll() en vez de solo las pagadas, la rechazada se sumaría y la prueba fallaría.
        lenient().when(boletaRepository.findAll()).thenReturn(List.of(pagada, rechazada));

        ReporteVentasResponse reporte = reporteService.obtenerReporteVentas();

        assertEquals(99980, reporte.getIngresosTotales());
        assertEquals(1, reporte.getOrdenesPagadas());
        assertEquals(2, reporte.getUnidadesVendidas());
        assertEquals(1, reporte.getPagosRechazados());
        assertEquals(1, reporte.getVentasPorCategoria().size()); // solo Periféricos, no Consolas
        assertEquals("Periféricos", reporte.getVentasPorCategoria().get(0).getCategoria());
        assertEquals(1, reporte.getRankingProductos().size());
    }

    @Test
    void obtenerReporteVentas_calculaIngresosDescuentosUnidadesYTicketRedondeado() {
        // Totales 100000 y 50001: promedio 75000,5 -> se redondea a 75001 (igual que Math.round en JavaScript).
        Boleta b1 = boleta(EstadoBoleta.PAGADA, 100000, 25000, detalle(ps5, 1, 100000), detalle(mouse, 1, 25000));
        Boleta b2 = boleta(EstadoBoleta.PAGADA, 50001, 0, detalle(audifonos, 3, 16667));
        when(boletaRepository.findByEstado(EstadoBoleta.PAGADA)).thenReturn(List.of(b1, b2));

        ReporteVentasResponse reporte = reporteService.obtenerReporteVentas();

        assertEquals(150001, reporte.getIngresosTotales());
        assertEquals(2, reporte.getOrdenesPagadas());
        assertEquals(75001, reporte.getTicketPromedio());
        assertEquals(25000, reporte.getTotalDescuentos());
        assertEquals(5, reporte.getUnidadesVendidas()); // 1 + 1 + 3
    }

    @Test
    void obtenerReporteVentas_ventasPorCategoria_agrupaYOrdenaDeMayorAMenor() {
        // Periféricos: 2 x 29990 + 1 x 49990 = 109970 · Consolas: 1 x 499990
        Boleta b1 = boleta(EstadoBoleta.PAGADA, 559960, 0, detalle(mouse, 2, 29990), detalle(ps5, 1, 499990));
        Boleta b2 = boleta(EstadoBoleta.PAGADA, 49990, 0, detalle(audifonos, 1, 49990));
        when(boletaRepository.findByEstado(EstadoBoleta.PAGADA)).thenReturn(List.of(b1, b2));

        List<VentaCategoriaResponse> categorias = reporteService.obtenerReporteVentas().getVentasPorCategoria();

        assertEquals(2, categorias.size());
        assertEquals("Consolas", categorias.get(0).getCategoria());
        assertEquals(499990, categorias.get(0).getMonto());
        assertEquals("Periféricos", categorias.get(1).getCategoria());
        assertEquals(109970, categorias.get(1).getMonto());
    }

    @Test
    void obtenerReporteVentas_ventasPorCategoria_usaElSubtotalAntesDelDescuento() {
        // Cliente Duoc: subtotal 29990, descuento 5998, total 23992. La categoría suma 29990 (como el frontend).
        Boleta duoc = boleta(EstadoBoleta.PAGADA, 23992, 5998, detalle(mouse, 1, 29990));
        when(boletaRepository.findByEstado(EstadoBoleta.PAGADA)).thenReturn(List.of(duoc));

        ReporteVentasResponse reporte = reporteService.obtenerReporteVentas();

        assertEquals(23992, reporte.getIngresosTotales());
        assertEquals(29990, reporte.getVentasPorCategoria().get(0).getMonto());
    }

    @Test
    void obtenerReporteVentas_ranking_ordenaPorUnidadesSumaEntreBoletasYDejaSolo5() {
        Producto p1 = producto(21L, "Producto 1", perifericos);
        Producto p2 = producto(22L, "Producto 2", perifericos);
        Producto p3 = producto(23L, "Producto 3", perifericos);
        Producto p4 = producto(24L, "Producto 4", perifericos);
        Producto p5 = producto(25L, "Producto 5", perifericos);
        Producto p6 = producto(26L, "Producto 6", perifericos);
        // Producto 2 aparece en dos boletas: 3 + 4 = 7 unidades (el más vendido).
        // Producto 4 y Producto 5 empatan en 2 unidades: va primero el que recaudó más (Producto 5).
        Boleta b1 = boleta(EstadoBoleta.PAGADA, 0, 0,
                detalle(p1, 1, 1000), detalle(p2, 3, 1000), detalle(p3, 5, 1000));
        Boleta b2 = boleta(EstadoBoleta.PAGADA, 0, 0,
                detalle(p2, 4, 1000), detalle(p4, 2, 1000), detalle(p5, 2, 5000), detalle(p6, 1, 9000));
        when(boletaRepository.findByEstado(EstadoBoleta.PAGADA)).thenReturn(List.of(b1, b2));

        List<ProductoVendidoResponse> ranking = reporteService.obtenerReporteVentas().getRankingProductos();

        assertEquals(5, ranking.size()); // 6 productos vendidos, solo el top 5
        assertEquals("Producto 2", ranking.get(0).getNombre());
        assertEquals(7, ranking.get(0).getUnidades());
        assertEquals(7000, ranking.get(0).getMonto());
        assertEquals(22L, ranking.get(0).getProductoId());
        assertEquals("Producto 3", ranking.get(1).getNombre()); // 5 unidades
        assertEquals("Producto 5", ranking.get(2).getNombre()); // 2 unidades, 10000
        assertEquals("Producto 4", ranking.get(3).getNombre()); // 2 unidades, 2000
        // Producto 1 y Producto 6 empatan en 1 unidad: entra Producto 6 (9000) y queda fuera Producto 1 (1000).
        assertEquals("Producto 6", ranking.get(4).getNombre());
    }
}
