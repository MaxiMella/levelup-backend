package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.DashboardResponse;
import cl.duoc.levelup.dto.ProductoVendidoResponse;
import cl.duoc.levelup.dto.ReporteVentasResponse;
import cl.duoc.levelup.dto.VentaCategoriaResponse;
import cl.duoc.levelup.model.Boleta;
import cl.duoc.levelup.model.DetalleBoleta;
import cl.duoc.levelup.model.EstadoBoleta;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.repository.BoletaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import cl.duoc.levelup.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Métricas del panel de administración: dashboard y reporte de ventas.
 * Reemplaza los cálculos que hacía el frontend (AdminDashboard.jsx y AdminReportes.jsx), con las mismas reglas.
 *
 * Regla principal: solo las boletas PAGADAS cuentan como ventas. Las RECHAZADAS solo se cuentan como
 * "órdenes realizadas" (dashboard) y como "pagos rechazados" (reporte).
 *
 * Se calcula en Java recorriendo las listas (igual que el frontend con reduce), en vez de usar consultas
 * SUM/GROUP BY: es más fácil de leer y se puede probar con Mockito entregando boletas armadas a mano.
 *
 * @Transactional(readOnly = true): los detalles de una boleta (@OneToMany) se cargan recién cuando se usan (LAZY);
 * la transacción mantiene abierta la conexión mientras se recorren. readOnly indica que no se modifica nada.
 */
@Service
public class ReporteService {

    /** Cantidad de productos del ranking (Top 5 del frontend). */
    private static final int TOP_PRODUCTOS = 5;

    private final BoletaRepository boletaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public ReporteService(BoletaRepository boletaRepository, ProductoRepository productoRepository,
                          UsuarioRepository usuarioRepository) {
        this.boletaRepository = boletaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Tarjetas, alertas de inventario y últimas órdenes del panel de administración. */
    @Transactional(readOnly = true)
    public DashboardResponse obtenerDashboard() {
        List<Boleta> pagadas = boletaRepository.findByEstado(EstadoBoleta.PAGADA);

        DashboardResponse dashboard = new DashboardResponse();
        dashboard.setVentasTotales(sumarTotales(pagadas));
        dashboard.setOrdenesRealizadas(boletaRepository.count()); // todas: pagadas y rechazadas
        dashboard.setTotalProductos(productoRepository.count());
        dashboard.setTotalUsuarios(usuarioRepository.count());
        dashboard.setProductosCriticos(productoRepository.findCriticos());
        dashboard.setUltimasOrdenes(boletaRepository.findTop5ByOrderByFechaDesc());
        return dashboard;
    }

    /** Reporte de ventas: solo con las boletas pagadas, más la cantidad de pagos rechazados. */
    @Transactional(readOnly = true)
    public ReporteVentasResponse obtenerReporteVentas() {
        List<Boleta> pagadas = boletaRepository.findByEstado(EstadoBoleta.PAGADA);

        long ingresos = sumarTotales(pagadas);
        long ordenesPagadas = pagadas.size();

        long descuentos = 0;
        long unidades = 0;
        for (Boleta boleta : pagadas) {
            descuentos += boleta.getDescuento();
            for (DetalleBoleta detalle : boleta.getDetalles()) {
                unidades += detalle.getCantidad();
            }
        }

        ReporteVentasResponse reporte = new ReporteVentasResponse();
        reporte.setIngresosTotales(ingresos);
        reporte.setOrdenesPagadas(ordenesPagadas);
        reporte.setTicketPromedio(calcularTicketPromedio(ingresos, ordenesPagadas));
        reporte.setUnidadesVendidas(unidades);
        reporte.setPagosRechazados(boletaRepository.countByEstado(EstadoBoleta.RECHAZADA));
        reporte.setTotalDescuentos(descuentos);
        reporte.setVentasPorCategoria(calcularVentasPorCategoria(pagadas));
        reporte.setRankingProductos(calcularRankingProductos(pagadas));
        return reporte;
    }

    // ===================== Métodos de apoyo =====================

    /** Suma del total (ya con descuento) de las boletas. */
    private long sumarTotales(List<Boleta> boletas) {
        long suma = 0;
        for (Boleta boleta : boletas) {
            suma += boleta.getTotal();
        }
        return suma;
    }

    /** Promedio por compra, redondeado. Si no hay ventas es 0 (así no se divide por cero). */
    private long calcularTicketPromedio(long ingresos, long ordenes) {
        if (ordenes == 0) {
            return 0;
        }
        return Math.round((double) ingresos / ordenes);
    }

    /**
     * Suma el subtotal de cada detalle en la categoría de su producto y ordena de mayor a menor monto.
     * El mapa usa el nombre de la categoría como clave: "Consolas" -> 999980.
     */
    private List<VentaCategoriaResponse> calcularVentasPorCategoria(List<Boleta> pagadas) {
        Map<String, Long> montoPorCategoria = new LinkedHashMap<>();
        for (Boleta boleta : pagadas) {
            for (DetalleBoleta detalle : boleta.getDetalles()) {
                String categoria = detalle.getProducto().getCategoria().getNombre();
                long montoActual = montoPorCategoria.getOrDefault(categoria, 0L);
                montoPorCategoria.put(categoria, montoActual + detalle.getSubtotal());
            }
        }

        List<VentaCategoriaResponse> resultado = new ArrayList<>();
        for (Map.Entry<String, Long> entrada : montoPorCategoria.entrySet()) {
            resultado.add(new VentaCategoriaResponse(entrada.getKey(), entrada.getValue()));
        }
        // De mayor a menor monto: se compara b con a (al revés) para que quede descendente.
        resultado.sort((a, b) -> Long.compare(b.getMonto(), a.getMonto()));
        return resultado;
    }

    /**
     * Agrupa los detalles por producto (sumando unidades y monto) y deja los 5 con más unidades.
     * Si dos productos empatan en unidades, va primero el que recaudó más.
     */
    private List<ProductoVendidoResponse> calcularRankingProductos(List<Boleta> pagadas) {
        Map<Long, ProductoVendidoResponse> porProducto = new LinkedHashMap<>();
        for (Boleta boleta : pagadas) {
            for (DetalleBoleta detalle : boleta.getDetalles()) {
                Producto producto = detalle.getProducto();
                ProductoVendidoResponse fila = porProducto.get(producto.getId());
                if (fila == null) {
                    fila = new ProductoVendidoResponse(producto.getId(), producto.getNombre(), 0, 0);
                    porProducto.put(producto.getId(), fila);
                }
                fila.setUnidades(fila.getUnidades() + detalle.getCantidad());
                fila.setMonto(fila.getMonto() + detalle.getSubtotal());
            }
        }

        List<ProductoVendidoResponse> ranking = new ArrayList<>(porProducto.values());
        ranking.sort((a, b) -> {
            if (a.getUnidades() != b.getUnidades()) {
                return Long.compare(b.getUnidades(), a.getUnidades()); // más unidades primero
            }
            return Long.compare(b.getMonto(), a.getMonto()); // empate: más monto primero
        });

        if (ranking.size() > TOP_PRODUCTOS) {
            return new ArrayList<>(ranking.subList(0, TOP_PRODUCTOS));
        }
        return ranking;
    }
}
