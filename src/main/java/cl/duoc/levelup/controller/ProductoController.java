package cl.duoc.levelup.controller;

import cl.duoc.levelup.dto.ProductoRequest;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de productos: /api/v1/productos.
 * El controller solo recibe la petición, llama al service y elige el código HTTP.
 * Las validaciones de negocio están en ProductoService y los errores los responde GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos", description = "Catálogo de productos: listado, búsqueda, ofertas, stock crítico y mantenedor")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    @Operation(summary = "Listar productos",
            description = "Devuelve los productos. Se puede filtrar por categoría (?categoriaId=1), "
                    + "buscar por nombre (?buscar=mouse) o combinar ambos filtros.")
    public List<Producto> listar(
            @Parameter(description = "Id de la categoría para filtrar (opcional)")
            @RequestParam(required = false) Long categoriaId,
            @Parameter(description = "Texto a buscar en el nombre, sin distinguir mayúsculas (opcional)")
            @RequestParam(required = false) String buscar) {
        return productoService.listar(categoriaId, buscar);
    }

    // Las rutas fijas (/ofertas, /criticos) no chocan con /{id}: Spring prioriza la ruta más específica.
    @GetMapping("/ofertas")
    @Operation(summary = "Listar productos en oferta",
            description = "Devuelve los productos que tienen un precio de oferta menor a su precio normal.")
    public List<Producto> listarOfertas() {
        return productoService.listarOfertas();
    }

    @GetMapping("/criticos")
    @Operation(summary = "Listar productos con stock crítico",
            description = "Devuelve los productos cuyo stock es igual o menor a su stock crítico.")
    public List<Producto> listarCriticos() {
        return productoService.listarCriticos();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto por id",
            description = "Devuelve el producto indicado con su categoría. Responde 404 si no existe.")
    public Producto obtenerPorId(@PathVariable Long id) {
        return productoService.obtenerPorId(id);
    }

    @PostMapping
    @Operation(summary = "Crear un producto",
            description = "Crea un producto y responde 201. Responde 400 si los datos no son válidos "
                    + "(precio > 0, stock y stock crítico >= 0, oferta menor al precio) "
                    + "y 404 si la categoría no existe.")
    public ResponseEntity<Producto> crear(@Valid @RequestBody ProductoRequest request) {
        Producto creado = productoService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un producto",
            description = "Reemplaza los datos del producto. Responde 400 si los datos no son válidos "
                    + "y 404 si el producto o la categoría no existen.")
    public Producto actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return productoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un producto",
            description = "Elimina el producto y responde 204 sin contenido. Responde 404 si no existe "
                    + "y 409 si el producto ya aparece en una boleta.")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
