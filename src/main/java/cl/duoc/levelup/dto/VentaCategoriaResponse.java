package cl.duoc.levelup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una fila de "Ventas por categoría" del reporte. Ejemplo: { "categoria": "Consolas", "monto": 999980 }.
 * El monto es la suma de los subtotales de los detalles (precio unitario × cantidad), ANTES del descuento Duoc,
 * igual que el frontend. Por eso la suma de las categorías puede ser mayor que ingresosTotales.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaCategoriaResponse {

    private String categoria;

    private long monto;
}
