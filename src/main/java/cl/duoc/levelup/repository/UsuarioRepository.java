package cl.duoc.levelup.repository;

import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Se usa en el login (Spring Security buscará al usuario por su correo). */
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    Optional<Usuario> findByCodigoReferido(String codigoReferido);

    List<Usuario> findByRol(Rol rol);
}
