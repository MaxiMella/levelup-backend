package cl.duoc.levelup.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tabla PRODUCTO: cada producto pertenece a una CATEGORIA (relación muchos a uno). */
@Entity
@Table(name = "producto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Precio normal en pesos chilenos (CLP no usa decimales). */
    @Column(nullable = false)
    private Integer precio;

    /** Precio rebajado; si es null el producto no está en oferta. */
    @Column(name = "precio_oferta")
    private Integer precioOferta;

    @Column(nullable = false)
    private Integer stock;

    /** Bajo este número de unidades el producto aparece como stock crítico. */
    @Column(name = "stock_critico", nullable = false)
    private Integer stockCritico;

    @Column(length = 500)
    private String imagen;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;
}
