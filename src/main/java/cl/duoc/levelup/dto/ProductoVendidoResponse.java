package cl.duoc.levelup.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una fila del "Top 5 productos más vendidos". Ejemplo:
 * { "productoId": 11, "nombre": "Audífonos Gamer 7.1 Surround", "unidades": 4, "monto": 199960 }.
 * El monto es la suma de los subtotales de sus detalles (antes del descuento Duoc), igual que el frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoVendidoResponse {

    private Long productoId;

    private String nombre;

    private long unidades;

    private long monto;
}
