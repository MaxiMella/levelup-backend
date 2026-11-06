package cl.duoc.levelup.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tabla BOLETA: una compra realizada en la tienda.
 * La relación con USUARIO es opcional (línea punteada en el modelo de la pauta):
 * un visitante puede comprar sin cuenta, por eso se guardan también los datos del cliente.
 */
@Entity
@Table(name = "boleta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Boleta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número visible para el cliente, ej: 20261102-001. */
    @Column(name = "numero_orden", nullable = false, unique = true, length = 20)
    private String numeroOrden;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoBoleta estado;

    /** Usuario que compró; null si compró como visitante. */
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // ----- Datos del cliente y dirección de entrega (formulario de checkout) -----
    @Column(name = "nombre_cliente", nullable = false, length = 60)
    private String nombreCliente;

    @Column(name = "apellidos_cliente", nullable = false, length = 80)
    private String apellidosCliente;

    @Column(name = "correo_cliente", nullable = false, length = 120)
    private String correoCliente;

    @Column(nullable = false, length = 150)
    private String calle;

    @Column(length = 50)
    private String depto;

    @Column(nullable = false, length = 80)
    private String region;

    @Column(nullable = false, length = 80)
    private String comuna;

    @Column(length = 255)
    private String indicaciones;

    // ----- Montos en CLP (los precios de la tienda incluyen IVA) -----
    @Column(nullable = false)
    private Integer subtotal;

    @Column(nullable = false)
    private Integer descuento = 0;

    /** Total sin impuesto: total / 1.19, redondeado. */
    @Column(nullable = false)
    private Integer neto;

    /** IVA 19% incluido en el total: total - neto. */
    @Column(nullable = false)
    private Integer iva;

    @Column(nullable = false)
    private Integer total;

    /** Productos de la boleta. Se guardan y eliminan junto con ella (cascade). */
    @OneToMany(mappedBy = "boleta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleBoleta> detalles = new ArrayList<>();

    /** Agrega un detalle manteniendo la relación en ambos sentidos. */
    public void agregarDetalle(DetalleBoleta detalle) {
        detalle.setBoleta(this);
        detalles.add(detalle);
    }
}
