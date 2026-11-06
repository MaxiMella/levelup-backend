package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.ProductoRequest;
import cl.duoc.levelup.exception.ConflictoException;
import cl.duoc.levelup.exception.DatosInvalidosException;
import cl.duoc.levelup.exception.RecursoNoEncontradoException;
import cl.duoc.levelup.model.Categoria;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.repository.CategoriaRepository;
import cl.duoc.levelup.repository.DetalleBoletaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lógica de negocio de los productos.
 * Reglas (además de las anotaciones de ProductoRequest):
 * - precioOferta, si viene, debe ser menor que precio.
 * - la categoría indicada debe existir.
 * - no se puede eliminar un producto que ya aparece en una boleta (se perdería el historial de ventas).
 */
@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final DetalleBoletaRepository detalleBoletaRepository;

    public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
                           DetalleBoletaRepository detalleBoletaRepository) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.detalleBoletaRepository = detalleBoletaRepository;
    }

    /**
     * Lista productos con filtros opcionales.
     * Según qué filtros vengan, se usa una consulta distinta del repositorio.
     */
    public List<Producto> listar(Long categoriaId, String buscar) {
        boolean hayBusqueda = buscar != null && !buscar.isBlank();

        if (categoriaId != null && hayBusqueda) {
            return productoRepository.findByCategoriaIdAndNombreContainingIgnoreCase(categoriaId, buscar.trim());
        }
        if (categoriaId != null) {
            return productoRepository.findByCategoriaId(categoriaId);
        }
        if (hayBusqueda) {
            return productoRepository.findByNombreContainingIgnoreCase(buscar.trim());
        }
        return productoRepository.findAll();
    }

    /** Busca por id; si no existe lanza RecursoNoEncontradoException (404). */
    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto con id " + id));
    }

    public List<Producto> listarOfertas() {
        return productoRepository.findEnOferta();
    }

    /** Productos con stock igual o menor a su stock crítico. */
    public List<Producto> listarCriticos() {
        return productoRepository.findCriticos();
    }

    public Producto crear(ProductoRequest request) {
        validarPrecioOferta(request);
        Categoria categoria = buscarCategoria(request.getCategoriaId());

        Producto producto = new Producto();
        copiarDatos(request, producto, categoria);
        return productoRepository.save(producto);
    }

    public Producto actualizar(Long id, ProductoRequest request) {
        Producto producto = obtenerPorId(id);
        validarPrecioOferta(request);
        Categoria categoria = buscarCategoria(request.getCategoriaId());

        copiarDatos(request, producto, categoria);
        return productoRepository.save(producto);
    }

    public void eliminar(Long id) {
        Producto producto = obtenerPorId(id);

        if (detalleBoletaRepository.existsByProductoId(id)) {
            throw new ConflictoException("No se puede eliminar el producto '" + producto.getNombre()
                    + "' porque ya aparece en una o más boletas");
        }

        productoRepository.delete(producto);
    }

    /** El precio de oferta es opcional, pero si viene debe ser menor que el precio normal. */
    private void validarPrecioOferta(ProductoRequest request) {
        if (request.getPrecioOferta() != null && request.getPrecioOferta() >= request.getPrecio()) {
            throw new DatosInvalidosException("El precio de oferta (" + request.getPrecioOferta()
                    + ") debe ser menor que el precio (" + request.getPrecio() + ")");
        }
    }

    /** La categoría enviada debe existir; si no, 404. */
    private Categoria buscarCategoria(Long categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la categoría con id " + categoriaId));
    }

    /** Pasa los datos del DTO a la entidad (se usa al crear y al actualizar). */
    private void copiarDatos(ProductoRequest request, Producto producto, Categoria categoria) {
        producto.setNombre(request.getNombre().trim());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecio(request.getPrecio());
        producto.setPrecioOferta(request.getPrecioOferta());
        producto.setStock(request.getStock());
        producto.setStockCritico(request.getStockCritico());
        producto.setImagen(request.getImagen());
        producto.setCategoria(categoria);
    }
}
