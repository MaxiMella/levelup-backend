package cl.duoc.levelup.controller;

import cl.duoc.levelup.config.OpenApiConfig;
import cl.duoc.levelup.dto.LoginRequest;
import cl.duoc.levelup.dto.LoginResponse;
import cl.duoc.levelup.dto.PerfilRequest;
import cl.duoc.levelup.dto.RegistroRequest;
import cl.duoc.levelup.dto.UsuarioResponse;
import cl.duoc.levelup.service.AuthService;
import cl.duoc.levelup.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticación y de la cuenta propia: /api/v1/auth.
 * - login y registro son públicos (permitAll en SecurityConfig).
 * - me y perfil necesitan token: el usuario se identifica con el correo del token, nunca con un id en la URL,
 *   así cada uno solo puede ver y editar sus propios datos.
 *
 * "Authentication authentication" lo entrega Spring Security: es el usuario que dejó JwtAuthenticationFilter
 * en el SecurityContext. authentication.getName() devuelve el correo (el "sub" del token).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Login con JWT, registro de clientes y perfil del usuario con sesión")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    public AuthController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "**Acceso: público.** Recibe correo y contraseña. Si son correctos responde 200 con el "
                    + "token JWT (válido por 8 horas), el tipo \"Bearer\" y los datos del usuario con su rol. "
                    + "Responde 400 si falta un campo y 401 si el correo o la contraseña son incorrectos.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/registro")
    @Operation(summary = "Registrar un cliente",
            description = "**Acceso: público.** Crea una cuenta con rol CLIENTE y responde 201. Detecta el descuento "
                    + "Duoc (@duoc.cl o @duocuc.cl) y genera el código de referido. Si se envía un código de referido "
                    + "válido, ambos usuarios ganan 100 puntos. Responde 400 si los datos no son válidos, si es menor "
                    + "de 18 años o si el código de referido no existe, y 409 si el correo ya está registrado. "
                    + "Después hay que iniciar sesión con POST /api/v1/auth/login.")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        UsuarioResponse registrado = usuarioService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registrado);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
    @Operation(summary = "Usuario de la sesión actual",
            description = "**Acceso: cualquier usuario con sesión.** Devuelve los datos del dueño del token. "
                    + "El frontend lo usa al abrir la aplicación para saber si el token guardado sigue sirviendo. "
                    + "Responde 401 sin token o con un token inválido o vencido.")
    public UsuarioResponse me(Authentication authentication) {
        return usuarioService.obtenerPerfil(authentication.getName());
    }

    @GetMapping("/perfil")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
    @Operation(summary = "Ver mi perfil",
            description = "**Acceso: cualquier usuario con sesión.** Devuelve los datos del perfil del usuario "
                    + "del token (sin contraseña). Responde 401 sin token.")
    public UsuarioResponse verPerfil(Authentication authentication) {
        return usuarioService.obtenerPerfil(authentication.getName());
    }

    @PutMapping("/perfil")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
    @Operation(summary = "Editar mi perfil",
            description = "**Acceso: cualquier usuario con sesión.** Cambia nombre, apellidos, fecha de nacimiento "
                    + "y, si viene, la contraseña (vacía = mantener la actual). No se puede cambiar el correo, "
                    + "el rol, los puntos ni el descuento Duoc. Responde 400 si los datos no son válidos y 401 sin token.")
    public UsuarioResponse editarPerfil(Authentication authentication, @Valid @RequestBody PerfilRequest request) {
        return usuarioService.actualizarPerfil(authentication.getName(), request);
    }
}
