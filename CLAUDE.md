# Reglas del proyecto Level-Up Gamer – Backend

## Contexto
- Proyecto académico DSY1104 Desarrollo Fullstack II (Duoc UC), caso Level-Up Gamer, Evaluación Parcial 3.
- API REST de una tienda online de productos gamer en Chile, consumida por un frontend React (Vite, http://localhost:5173). Usuarios: clientes, vendedores y administradores.
- Tecnologías: Java 21, Spring Boot 4.1 (starters modulares, ej: spring-boot-starter-webmvc), Maven, Spring Data JPA, MySQL, Lombok, Validation. Se agregarán Swagger (springdoc-openapi), Spring Security y JWT. Datos ficticios.
- Estructura (paquete base cl.duoc.levelup):
  - model: entidades JPA (usuario, boleta, detalle_boleta, producto, categoria) y enums (Rol, EstadoBoleta).
  - repository: interfaces JpaRepository.
  - service: lógica de negocio.
  - controller: endpoints REST.
  - dto: objetos de entrada/salida cuando no calzan con la entidad (login, registro, crear boleta).
  - config: CORS, Swagger y carga de datos iniciales (DataInitializer).
  - security: Spring Security, JWT y filtros.
- Roles: ADMIN (acceso total), VENDEDOR (solo ver productos y órdenes con su detalle), CLIENTE (solo la tienda).
- Los precios incluyen IVA (Chile): neto = round(total / 1.19) e iva = total - neto. La boleta guarda subtotal, descuento, neto, iva y total.
- Proyecto en equipo: respetar el estilo y la estructura que ya existen; no reescribir archivos completos si basta con un cambio puntual.

## Nivel del código
- Código simple y al nivel de un curso universitario: nada de patrones avanzados ni librerías que no hayamos visto en clases (ej: MapStruct, WebFlux, arquitectura hexagonal, módulos múltiples).
- Preferir código claro aunque sea un poco más largo, antes que código corto difícil de explicar.
- Separar responsabilidades: el controller solo recibe la petición y responde; la lógica y las validaciones de negocio van en el service; el acceso a datos, en el repository.
- Inyección de dependencias por constructor.
- Todas las rutas van versionadas bajo /api/v1 (ej: /api/v1/productos), con nombres en plural y los códigos HTTP correctos (200, 201, 204, 400, 401, 403, 404, 409).
- La API nunca debe caerse: manejar los errores en un @RestControllerAdvice que devuelva un JSON con el mensaje y el código HTTP adecuado.
- Las contraseñas nunca se devuelven en las respuestas y se guardan cifradas con BCrypt.
- Las versiones de las dependencias van en pom.xml. La configuración (base de datos, JWT), en application.properties. Nunca escribir contraseñas reales en archivos que se suben a GitHub: usar variables de entorno (ej: DB_PASSWORD).

## Cambios
- Hacer solo lo que pide la tarea: no cambiar código ni archivos que no estén relacionados.
- No borrar los comentarios existentes; actualizarlos si el código cambia.
- En tareas grandes, mostrar primero el plan de archivos antes de escribir código.
- Al terminar, compilar y ejecutar (./mvnw spring-boot:run o ./mvnw test) para confirmar que no se rompió nada.
- La lógica de negocio de los services se prueba con JUnit 5 y Mockito.

## Documentación (el estudiante debe defender y modificar este código en vivo)
- Comentarios en español explicando qué hace cada clase y función y por qué.
- Documentar cada endpoint en Swagger con @Operation y una descripción en español.
- Después de cada cambio, explicar qué archivos se tocaron y cómo se conectan entre sí (controller → service → repository → base de datos).
- Mantener docs/ESTUDIO.md con la explicación de cada parte y ejemplos de modificaciones que el profesor podría pedir en la defensa (ej: agregar un campo a una entidad, crear un endpoint nuevo, cambiar los permisos de un rol).

## Git
- No hacer commits ni push. Al terminar cada tarea, sugerir un mensaje de commit técnico (feat:, fix:, refactor:, docs:, build:, test:).
- Hacer una tarea a la vez.
