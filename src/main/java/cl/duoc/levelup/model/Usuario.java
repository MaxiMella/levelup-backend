package cl.duoc.levelup.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Tabla USUARIO: clientes, vendedores y administradores del sistema. */
@Entity
@Table(name = "usuario")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String apellidos;

    @Column(nullable = false, unique = true, length = 120)
    private String correo;

    /**
     * Se guarda cifrada con BCrypt (ver config/PasswordConfig).
     * WRITE_ONLY: se puede recibir en un JSON, pero nunca se envía en las respuestas.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol = Rol.CLIENTE;

    /** 20% de descuento de por vida para correos @duoc.cl / @duocuc.cl. */
    @Column(name = "descuento_duoc", nullable = false)
    private boolean descuentoDuoc = false;

    /** Puntos LevelUp del programa de referidos. */
    @Column(nullable = false)
    private Integer puntos = 0;

    @Column(name = "codigo_referido", unique = true, length = 20)
    private String codigoReferido;

    /** Código del usuario que lo refirió (puede ser null). */
    @Column(name = "referido_por", length = 20)
    private String referidoPor;
}
