package cl.duoc.levelup.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI: datos generales que aparecen arriba en Swagger UI.
 * springdoc lee los controllers (@Tag, @Operation) y arma la documentación automáticamente:
 * - Swagger UI:  http://localhost:8080/swagger-ui/index.html
 * - JSON OpenAPI: http://localhost:8080/v3/api-docs
 *
 * Seguridad: se declara el esquema "bearerAuth" (token JWT en el header Authorization).
 * Eso agrega el botón "Authorize" en Swagger UI: se pega el token que entrega POST /api/v1/auth/login
 * y Swagger lo envía en los endpoints marcados con @SecurityRequirement(name = "bearerAuth").
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema; es el que se usa en @SecurityRequirement(name = ...) en los controllers. */
    public static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI levelUpOpenApi() {
        Info info = new Info()
                .title("Level-Up Gamer API")
                .version("v1")
                .description("API REST de la tienda online Level-Up Gamer: categorías, productos, "
                        + "usuarios y boletas. Los precios están en pesos chilenos (CLP) e incluyen IVA.\n\n"
                        + "Para probar los endpoints protegidos: POST /api/v1/auth/login, copiar el token y "
                        + "pegarlo en el botón Authorize (sin escribir \"Bearer\").");

        SecurityScheme esquemaJwt = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Token JWT entregado por POST /api/v1/auth/login");

        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, esquemaJwt));
    }
}
