package cl.duoc.levelup.repository;

import cl.duoc.levelup.model.Boleta;
import cl.duoc.levelup.model.EstadoBoleta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoletaRepository extends JpaRepository<Boleta, Long> {

    /** Todas las órdenes, de la más reciente a la más antigua (panel admin y vendedor). */
    List<Boleta> findAllByOrderByFechaDesc();

    /** Historial de compras de un usuario (perfil del cliente y gestión de usuarios). */
    List<Boleta> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    /** Solo las pagadas cuentan como ventas en el dashboard y los reportes. */
    List<Boleta> findByEstado(EstadoBoleta estado);

    Optional<Boleta> findByNumeroOrden(String numeroOrden);

    /** Para generar el correlativo del número de orden del día, ej: 20261102-003. */
    long countByNumeroOrdenStartingWith(String prefijo);

    /** Permite saber si un usuario tiene compras antes de eliminarlo. */
    boolean existsByUsuarioId(Long usuarioId);
}
