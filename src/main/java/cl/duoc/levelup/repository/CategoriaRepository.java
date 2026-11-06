package cl.duoc.levelup.repository;

import cl.duoc.levelup.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JpaRepository ya trae findAll, findById, save, deleteById, count, etc.
 * Aquí solo se agregan consultas extra; Spring las genera a partir del nombre del método.
 */
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    List<Categoria> findByActivaTrue();
}
