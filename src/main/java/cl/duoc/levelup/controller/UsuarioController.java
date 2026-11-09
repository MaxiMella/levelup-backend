package cl.duoc.levelup.controller;

import cl.duoc.levelup.config.OpenApiConfig;
import cl.duoc.levelup.dto.UsuarioRequest;
import cl.duoc.levelup.dto.UsuarioResponse;
import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
 * Endpoints REST de usuarios: /api/v1/usuarios. Es el mantenedor del administrador.
 * Permisos (SecurityConfig): todos los endpoints son solo para ADMIN.
 * Siempre responde UsuarioResponse, que no tiene contraseña.
 * El registro de clientes y el perfil propio están en AuthController (/api/v1/auth/registro y /perfil).
 * El historial de compras de un usuario está en GET /api/v1/boletas/usuario/{id}.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Mantenedor de usuarios del administrador (crear, editar, eliminar y cambiar roles)")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios",
            description = "**Acceso: solo ADMIN.** Devuelve los usuarios sin contraseña. Se puede filtrar por rol (?rol=VENDEDOR). "
                    + "Responde 400 si el rol no existe.")
    public List<UsuarioResponse> listar(
            @Parameter(description = "Rol para filtrar: ADMIN, VENDEDOR o CLIENTE (opcional)")
            @RequestParam(required = false) Rol rol) {
        return usuarioService.listar(rol);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un usuario por id",
            description = "**Acceso: solo ADMIN.** Devuelve los datos del usuario sin contraseña. Responde 404 si no existe.")
    public UsuarioResponse obtenerPorId(@PathVariable Long id) {
        return usuarioService.obtenerPorId(id);
    }

    @PostMapping
    @Operation(summary = "Crear un usuario (administrador)",
            description = "**Acceso: solo ADMIN.** Crea un usuario con cualquier rol y responde 201. La contraseña es obligatoria. "
                    + "Responde 400 si los datos no son válidos o es menor de 18 años, y 409 si el correo ya existe.")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        UsuarioResponse creado = usuarioService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un usuario",
            description = "**Acceso: solo ADMIN.** Reemplaza los datos del usuario (incluido su rol). Si la contraseña viene vacía se mantiene la actual "
                    + "y el descuento Duoc se recalcula según el correo. Responde 400, 404 si no existe "
                    + "y 409 si el correo pertenece a otro usuario.")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request) {
        return usuarioService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un usuario",
            description = "**Acceso: solo ADMIN.** Elimina el usuario y responde 204 sin contenido. Responde 404 si no existe "
                    + "y 409 si tiene boletas asociadas.")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
