package cl.duoc.levelup.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI: datos generales que aparecen arriba en Swagger UI.
 * springdoc lee los controllers (@Tag, @Operation) y arma la documentación automáticamente:
 * - Swagger UI:  http://localhost:8080/swagger-ui/index.html
 * - JSON OpenAPI: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI levelUpOpenApi() {
        Info info = new Info()
                .title("Level-Up Gamer API")
                .version("v1")
                .description("API REST de la tienda online Level-Up Gamer: categorías, productos, "
                        + "usuarios y boletas. Los precios están en pesos chilenos (CLP) e incluyen IVA.");
        return new OpenAPI().info(info);
    }
}
