package cl.duoc.levelup.controller;

import cl.duoc.levelup.config.OpenApiConfig;
import cl.duoc.levelup.dto.BoletaRequest;
import cl.duoc.levelup.model.Boleta;
import cl.duoc.levelup.service.BoletaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de boletas (órdenes de compra): /api/v1/boletas.
 * La respuesta es la entidad Boleta con sus detalles y el producto de cada detalle.
 * La contraseña del usuario nunca aparece porque Usuario.password es WRITE_ONLY.
 * Los permisos por rol están en SecurityConfig. "Authentication authentication" es el usuario del token
 * (null si es un visitante); authentication.getName() devuelve su correo.
 */
@RestController
@RequestMapping("/api/v1/boletas")
@Tag(name = "Boletas", description = "Órdenes de compra: checkout de la tienda, listado e historial de compras")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
public class BoletaController {

    private final BoletaService boletaService;

    public BoletaController(BoletaService boletaService) {
        this.boletaService = boletaService;
    }

    @PostMapping
    @Operation(summary = "Crear una boleta (checkout)",
            description = "**Acceso: público (token opcional).** Registra la compra del carrito y responde 201 con "
                    + "la boleta. Con token de CLIENTE la boleta queda a su nombre (se ignora usuarioId); sin token "
                    + "es una compra de visitante; solo un ADMIN puede indicar usuarioId. Calcula precios "
                    + "(usa el de oferta si existe), descuento Duoc del 20%, neto e IVA. Pago simulado: si el correo "
                    + "contiene \"error\" queda RECHAZADA; si no, PAGADA y se descuenta el stock. "
                    + "Responde 400 si los datos no son válidos, 404 si un producto o el usuario no existen "
                    + "y 409 si no hay stock suficiente.")
    public ResponseEntity<Boleta> crear(@Valid @RequestBody BoletaRequest request, Authentication authentication) {
        String correoSesion = null;
        if (authentication != null) {
            correoSesion = authentication.getName();
        }
        Boleta creada = boletaService.crear(request, correoSesion);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @GetMapping
    @Operation(summary = "Listar todas las boletas",
            description = "**Acceso: ADMIN y VENDEDOR.** Devuelve todas las boletas con sus detalles, de la más "
                    + "reciente a la más antigua (panel de administrador y vendedor).")
    public List<Boleta> listarTodas() {
        return boletaService.listarTodas();
    }

    // "/mis-compras" no choca con "/{id}": Spring prioriza la ruta fija sobre la variable.
    @GetMapping("/mis-compras")
    @Operation(summary = "Mis compras",
            description = "**Acceso: CLIENTE y ADMIN.** Devuelve las boletas del usuario del token, de la más "
                    + "reciente a la más antigua (lista vacía si no ha comprado). Responde 401 sin token.")
    public List<Boleta> misCompras(Authentication authentication) {
        return boletaService.misCompras(authentication.getName());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una boleta por id",
            description = "**Acceso: ADMIN y VENDEDOR.** Devuelve la boleta con sus detalles. Responde 404 si no existe.")
    public Boleta obtenerPorId(@PathVariable Long id) {
        return boletaService.obtenerPorId(id);
    }

    @GetMapping("/numero/{numeroOrden}")
    @Operation(summary = "Obtener una boleta por número de orden",
            description = "**Acceso: ADMIN y VENDEDOR.** Busca por el número que ve el cliente, ej: 20261106-001. "
                    + "Responde 404 si no existe.")
    public Boleta obtenerPorNumeroOrden(@PathVariable String numeroOrden) {
        return boletaService.obtenerPorNumeroOrden(numeroOrden);
    }

    @GetMapping("/usuario/{usuarioId}")
    @Operation(summary = "Historial de compras de un usuario",
            description = "**Acceso: ADMIN (cualquier usuario) o el CLIENTE dueño del historial.** Devuelve las "
                    + "boletas del usuario, de la más reciente a la más antigua (lista vacía si no ha comprado). "
                    + "Responde 403 si un cliente pide el historial de otro y 404 si el usuario no existe.")
    public List<Boleta> historialUsuario(@PathVariable Long usuarioId, Authentication authentication) {
        return boletaService.historialUsuario(usuarioId, authentication.getName());
    }
}
