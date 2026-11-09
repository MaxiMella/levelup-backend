package cl.duoc.levelup.controller;

import cl.duoc.levelup.config.OpenApiConfig;
import cl.duoc.levelup.dto.DashboardResponse;
import cl.duoc.levelup.dto.ReporteVentasResponse;
import cl.duoc.levelup.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del dashboard y los reportes del panel de administración: /api/v1/reportes.
 * Permisos (SecurityConfig): solo ADMIN. Los cálculos están en ReporteService.
 */
@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = "Reportes", description = "Métricas del panel de administración: dashboard y reporte de ventas")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard del administrador",
            description = "**Acceso: solo ADMIN.** Devuelve las ventas totales (suma del total de las boletas "
                    + "PAGADAS), las órdenes realizadas (todas), la cantidad de productos y de usuarios, los productos "
                    + "con stock crítico y las 5 órdenes más recientes. Responde 401 sin token y 403 con otro rol.")
    public DashboardResponse dashboard() {
        return reporteService.obtenerDashboard();
    }

    @GetMapping("/ventas")
    @Operation(summary = "Reporte de ventas",
            description = "**Acceso: solo ADMIN.** Solo las boletas PAGADAS cuentan como ventas. Devuelve ingresos "
                    + "totales, órdenes pagadas, ticket promedio (redondeado; 0 si no hay ventas), unidades vendidas, "
                    + "pagos rechazados, total de descuentos Duoc, ventas por categoría (de mayor a menor, antes del "
                    + "descuento) y el top 5 de productos más vendidos por unidades. Responde 401 sin token y 403 con otro rol.")
    public ReporteVentasResponse ventas() {
        return reporteService.obtenerReporteVentas();
    }
}
