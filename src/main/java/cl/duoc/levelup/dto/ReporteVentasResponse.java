package cl.duoc.levelup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Reporte de ventas (GET /api/v1/reportes/ventas): las mismas métricas que calculaba AdminReportes.jsx.
 * Solo las boletas PAGADAS cuentan como ventas; las rechazadas solo aparecen en pagosRechazados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReporteVentasResponse {

    /** Suma del total (con descuento) de las boletas pagadas. */
    private long ingresosTotales;

    /** Cantidad de boletas pagadas. */
    private long ordenesPagadas;

    /** ingresosTotales / ordenesPagadas, redondeado. 0 si no hay ventas. */
    private long ticketPromedio;

    /** Suma de las cantidades de todos los detalles de las boletas pagadas. */
    private long unidadesVendidas;

    /** Cantidad de boletas RECHAZADAS. */
    private long pagosRechazados;

    /** Suma de los descuentos Duoc de las boletas pagadas (lo que ahorraron los clientes). */
    private long totalDescuentos;

    /** Monto vendido por categoría, de mayor a menor. */
    private List<VentaCategoriaResponse> ventasPorCategoria;

    /** Los 5 productos más vendidos por unidades. */
    private List<ProductoVendidoResponse> rankingProductos;
}
