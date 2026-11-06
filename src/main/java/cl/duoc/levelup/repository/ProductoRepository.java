package cl.duoc.levelup.repository;

import cl.duoc.levelup.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByCategoriaId(Long categoriaId);

    long countByCategoriaId(Long categoriaId);

    List<Producto> findByNombreContainingIgnoreCase(String texto);

    /** Filtro combinado: productos de una categoría cuyo nombre contiene el texto buscado. */
    List<Producto> findByCategoriaIdAndNombreContainingIgnoreCase(Long categoriaId, String texto);

    /** Productos con stock igual o menor a su stock crítico (listado del administrador). */
    @Query("SELECT p FROM Producto p WHERE p.stock <= p.stockCritico")
    List<Producto> findCriticos();

    /** Productos en oferta: tienen precio de oferta y es menor al precio normal. */
    @Query("SELECT p FROM Producto p WHERE p.precioOferta IS NOT NULL AND p.precioOferta < p.precio")
    List<Producto> findEnOferta();
}
