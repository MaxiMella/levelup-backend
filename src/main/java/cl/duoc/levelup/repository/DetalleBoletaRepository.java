package cl.duoc.levelup.repository;

import cl.duoc.levelup.model.DetalleBoleta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetalleBoletaRepository extends JpaRepository<DetalleBoleta, Long> {

    List<DetalleBoleta> findByBoletaId(Long boletaId);

    /** Permite saber si un producto ya se vendió antes de eliminarlo. */
    boolean existsByProductoId(Long productoId);
}
