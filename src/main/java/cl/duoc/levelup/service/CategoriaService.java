package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.CategoriaRequest;
import cl.duoc.levelup.exception.ConflictoException;
import cl.duoc.levelup.exception.RecursoNoEncontradoException;
import cl.duoc.levelup.model.Categoria;
import cl.duoc.levelup.repository.CategoriaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Lógica de negocio de las categorías.
 * Reglas: no puede haber dos categorías con el mismo nombre (sin importar mayúsculas)
 * y no se puede eliminar una categoría que todavía tiene productos.
 * Cuando una regla no se cumple, lanza una excepción que GlobalExceptionHandler convierte en JSON.
 */
@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, ProductoRepository productoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
    }

    public List<Categoria> listarTodas() {
        return categoriaRepository.findAll();
    }

    /** Solo las categorías activas (las que se muestran en la tienda). */
    public List<Categoria> listarActivas() {
        return categoriaRepository.findByActivaTrue();
    }

    /** Busca por id; si no existe lanza RecursoNoEncontradoException (404). */
    public Categoria obtenerPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la categoría con id " + id));
    }

    public Categoria crear(CategoriaRequest request) {
        String nombre = request.getNombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("Ya existe una categoría con el nombre '" + nombre + "'");
        }

        Categoria categoria = new Categoria();
        copiarDatos(request, categoria);
        return categoriaRepository.save(categoria);
    }

    public Categoria actualizar(Long id, CategoriaRequest request) {
        Categoria categoria = obtenerPorId(id);

        // El nombre puede estar repetido solo si es el de esta misma categoría (ej: se guarda sin cambiarlo).
        String nombre = request.getNombre().trim();
        Optional<Categoria> conMismoNombre = categoriaRepository.findByNombreIgnoreCase(nombre);
        if (conMismoNombre.isPresent() && !conMismoNombre.get().getId().equals(id)) {
            throw new ConflictoException("Ya existe una categoría con el nombre '" + nombre + "'");
        }

        copiarDatos(request, categoria);
        return categoriaRepository.save(categoria);
    }

    public void eliminar(Long id) {
        Categoria categoria = obtenerPorId(id);

        long cantidadProductos = productoRepository.countByCategoriaId(id);
        if (cantidadProductos > 0) {
            throw new ConflictoException("No se puede eliminar la categoría '" + categoria.getNombre()
                    + "' porque tiene " + cantidadProductos + " producto(s) asociados");
        }

        categoriaRepository.delete(categoria);
    }

    /** Pasa los datos del DTO a la entidad (se usa al crear y al actualizar). */
    private void copiarDatos(CategoriaRequest request, Categoria categoria) {
        categoria.setNombre(request.getNombre().trim());
        categoria.setDescripcion(request.getDescripcion());
        // Si no se envía "activa", la categoría queda activa.
        categoria.setActiva(request.getActiva() == null || request.getActiva());
    }
}
