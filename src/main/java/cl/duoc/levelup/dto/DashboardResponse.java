package cl.duoc.levelup.dto;

import cl.duoc.levelup.model.Boleta;
import cl.duoc.levelup.model.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Datos del panel de administración (GET /api/v1/reportes/dashboard).
 * Son las mismas métricas que calculaba el frontend en AdminDashboard.jsx.
 * Los montos son long (no Integer): una suma de muchas boletas puede pasar el máximo de un int (~2.147 millones).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    /** Suma del total de las boletas PAGADAS (las rechazadas no son ventas). */
    private long ventasTotales;

    /** Todas las boletas, pagadas y rechazadas. */
    private long ordenesRealizadas;

    private long totalProductos;

    private long totalUsuarios;

    /** Productos con stock igual o menor a su stock crítico (alertas de inventario). */
    private List<Producto> productosCriticos;

    /** Las 5 boletas más recientes, de cualquier estado. */
    private List<Boleta> ultimasOrdenes;
}
