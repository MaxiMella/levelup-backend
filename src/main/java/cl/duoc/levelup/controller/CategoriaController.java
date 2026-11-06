package cl.duoc.levelup.controller;

import cl.duoc.levelup.dto.CategoriaRequest;
import cl.duoc.levelup.model.Categoria;
import cl.duoc.levelup.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de categorías: /api/v1/categorias.
 * El controller solo recibe la petición, llama al service y elige el código HTTP.
 * Las validaciones de negocio están en CategoriaService y los errores los responde GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorías", description = "Administración de las categorías de productos de la tienda")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    @Operation(summary = "Listar todas las categorías",
            description = "Devuelve todas las categorías, activas e inactivas (para el panel de administración).")
    public List<Categoria> listarTodas() {
        return categoriaService.listarTodas();
    }

    @GetMapping("/activas")
    @Operation(summary = "Listar categorías activas",
            description = "Devuelve solo las categorías activas, que son las que se muestran en la tienda.")
    public List<Categoria> listarActivas() {
        return categoriaService.listarActivas();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una categoría por id",
            description = "Devuelve la categoría indicada. Responde 404 si no existe.")
    public Categoria obtenerPorId(@PathVariable Long id) {
        return categoriaService.obtenerPorId(id);
    }

    @PostMapping
    @Operation(summary = "Crear una categoría",
            description = "Crea una categoría nueva y responde 201. Responde 400 si faltan datos "
                    + "y 409 si ya existe otra categoría con el mismo nombre.")
    public ResponseEntity<Categoria> crear(@Valid @RequestBody CategoriaRequest request) {
        Categoria creada = categoriaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una categoría",
            description = "Reemplaza los datos de la categoría. Responde 400 si faltan datos, "
                    + "404 si no existe y 409 si el nombre ya lo usa otra categoría.")
    public Categoria actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una categoría",
            description = "Elimina la categoría y responde 204 sin contenido. Responde 404 si no existe "
                    + "y 409 si todavía tiene productos asociados.")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        categoriaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
