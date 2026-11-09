# Guía de estudio – Level-Up Gamer Backend

Este documento explica cada parte del backend para poder defenderlo y modificarlo en vivo.
Se irá completando a medida que se agreguen services, controllers, seguridad y Swagger.

---

## 1. Cómo arranca el proyecto

1. `LevelupBackendApplication` (con `@SpringBootApplication`) inicia Spring Boot.
2. Spring lee `src/main/resources/application.properties`, que importa `local.properties`
   (con `spring.config.import`). Ahí están `DB_PASSWORD` (la contraseña de MySQL de cada integrante) y
   `JWT_SECRET` (la clave para firmar los tokens, mínimo 32 caracteres).
   `local.properties` está en `.gitignore`, así que nunca se sube a GitHub
   (`local.properties.example` muestra el formato). Si falta `JWT_SECRET`, la app no arranca y
   `JwtService` explica qué hacer.
3. Con `createDatabaseIfNotExist=true` en la URL, MySQL crea la base `levelup_db` si no existe.
4. Con `spring.jpa.hibernate.ddl-auto=update`, Hibernate revisa las clases `@Entity` del paquete
   `model` y crea (o actualiza) las tablas. No borra datos.
5. Cuando el contexto termina de cargar, `DataInitializer` (un `CommandLineRunner`) carga dos cosas,
   cada una revisada por separado para no duplicar datos:
   - `cargarCatalogo()`: 6 categorías y 15 productos, solo si la tabla `categoria` está vacía.
   - `cargarUsuarios()`: 3 usuarios de prueba con contraseña cifrada, solo si la tabla `usuario` está vacía
     (ver sección 11).
6. `CorsConfig` permite que el frontend React (`http://localhost:5173`) llame a `/api/**`.
   Lo aplica Spring Security con `http.cors()` (ver sección 14).
7. `OpenApiConfig` + springdoc generan la documentación en Swagger UI:
   `http://localhost:8080/swagger-ui/index.html`.
8. `security/SecurityConfig` decide qué rol puede usar cada ruta y agrega el filtro JWT (sección 14).

Flujo general de una petición:

```
Frontend React → Spring Security (filtro JWT + reglas por rol) → Controller (/api/v1/...)
              → Service (lógica y validaciones) → Repository (JpaRepository) → Hibernate → MySQL

Sin permiso → Spring Security responde 401/403 (SeguridadErrorHandler) sin llegar al controller
Si algo falla en cualquier capa → se lanza una excepción → GlobalExceptionHandler → JSON de error
```

Ejemplo completo, `POST /api/v1/productos`:
1. `ProductoController.crear` recibe el JSON y lo convierte en `ProductoRequest`.
2. `@Valid` revisa las anotaciones del DTO (`@NotBlank`, `@Positive`...). Si algo falla, responde 400
   sin llegar al service.
3. `ProductoService.crear` revisa las reglas de negocio (oferta < precio, la categoría existe)
   y copia los datos del DTO a una entidad `Producto`.
4. `ProductoRepository.save` → Hibernate hace el `INSERT INTO producto ...` en MySQL.
5. El controller responde `201 Created` con el producto guardado (ya con su `id`).

---

## 2. Modelo de datos

### Diagrama de relaciones

```
 CATEGORIA 1 ──────< N PRODUCTO 1 ──────< N DETALLE_BOLETA N >────── 1 BOLETA N >- - - - 0..1 USUARIO
```

- `──<` : uno a muchos obligatorio.
- `- - -` : relación opcional (la boleta puede no tener usuario: compra como visitante).

| Tabla            | Entidad         | Clave foránea                     | Relación JPA                                  |
|------------------|-----------------|-----------------------------------|-----------------------------------------------|
| `categoria`      | `Categoria`     | –                                 | –                                             |
| `producto`       | `Producto`      | `categoria_id` (obligatoria)      | `@ManyToOne(optional = false)` → Categoria    |
| `usuario`        | `Usuario`       | –                                 | –                                             |
| `boleta`         | `Boleta`        | `usuario_id` (puede ser `NULL`)   | `@ManyToOne` → Usuario                        |
| `detalle_boleta` | `DetalleBoleta` | `boleta_id`, `producto_id`        | `@ManyToOne` → Boleta y `@ManyToOne` → Producto |

### 2.1 Categoria (`model/Categoria.java`)
Agrupa los productos (Consolas, Periféricos, Sillas, Juegos de Mesa, Computadores Gamer, Accesorios).
- `nombre` es único (`unique = true`): no puede haber dos categorías con el mismo nombre.
- `activa`: solo las categorías activas se muestran en la tienda (`findByActivaTrue()`).

### 2.2 Producto (`model/Producto.java`)
- Cada producto pertenece a **una** categoría; una categoría tiene **muchos** productos
  (`@ManyToOne` + `@JoinColumn(name = "categoria_id")`). Es obligatoria (`optional = false`).
- Precios en `Integer` porque el peso chileno (CLP) no usa decimales. Los precios **incluyen IVA**.
- `precioOferta`: si es `null`, el producto no está en oferta.
- `stockCritico`: si `stock <= stockCritico`, el producto aparece en `findCriticos()`.

### 2.3 Usuario (`model/Usuario.java`)
- `correo` es único: se usa para el login (`findByCorreoIgnoreCase`).
- `password`: tiene `@JsonProperty(access = WRITE_ONLY)`, así **nunca se envía en las respuestas JSON**.
  Se guardará cifrada con BCrypt cuando se configure Spring Security.
- `rol`: enum `Rol` guardado como texto (`@Enumerated(EnumType.STRING)`): `ADMIN`, `VENDEDOR`, `CLIENTE`.
  Se guarda como texto y no como número para que la base sea legible y no se rompa si cambia el orden del enum.
- `descuentoDuoc`: 20% de descuento para correos `@duoc.cl` / `@duocuc.cl`.
- `puntos`, `codigoReferido`, `referidoPor`: programa de referidos LevelUp.

### 2.4 Boleta (`model/Boleta.java`)
Una compra realizada.
- `usuario` es **opcional** (`usuario_id` puede ser `NULL`): un visitante puede comprar sin cuenta.
  Por eso la boleta guarda también los datos del cliente (`nombreCliente`, `correoCliente`, dirección).
- `numeroOrden` único, ej: `20261102-001` (`countByNumeroOrdenStartingWith` ayuda a calcular el correlativo).
- `estado`: enum `EstadoBoleta` (`PAGADA` o `RECHAZADA`).
- Montos: `subtotal`, `descuento`, `neto`, `iva`, `total`. Como los precios incluyen IVA:
  - `neto = Math.round(total / 1.19)`
  - `iva = total - neto`
  - Ejemplo: total 119.000 → neto 100.000 → IVA 19.000.
- `detalles`: `@OneToMany(mappedBy = "boleta", cascade = CascadeType.ALL, orphanRemoval = true)`.
  - `mappedBy = "boleta"`: la clave foránea está en `detalle_boleta`, no en `boleta`.
  - `cascade = ALL`: al guardar la boleta se guardan sus detalles (no hay que guardarlos uno por uno).
  - `orphanRemoval = true`: si se quita un detalle de la lista, se borra de la base.
- `agregarDetalle(detalle)`: asigna la boleta al detalle **y** lo agrega a la lista, para que la
  relación quede bien en ambos sentidos antes de guardar.

### 2.5 DetalleBoleta (`model/DetalleBoleta.java`)
Cada línea de la boleta: qué producto, cuántas unidades y a qué precio.
- Une `boleta` y `producto`: una boleta tiene muchos productos y un producto aparece en muchas
  boletas (relación muchos a muchos resuelta con una tabla intermedia que tiene datos propios).
- `precioUnitario`: se copia el precio **al momento de la compra**, así la boleta no cambia si
  después se edita el precio del producto.
- `boleta` tiene `@JsonIgnore`, `@ToString.Exclude` y `@EqualsAndHashCode.Exclude` para evitar
  un ciclo infinito (boleta → detalle → boleta → ...) al convertir a JSON o al usar `toString()`.
- `existsByProductoId`: permite impedir que se elimine un producto que ya se vendió.

### 2.6 Anotaciones que se repiten
| Anotación | Para qué sirve |
|---|---|
| `@Entity` / `@Table(name = ...)` | La clase es una tabla de la base de datos. |
| `@Id` + `@GeneratedValue(strategy = IDENTITY)` | Clave primaria autoincremental de MySQL. |
| `@Column(nullable, unique, length)` | Restricciones de la columna (`NOT NULL`, `UNIQUE`, `VARCHAR(n)`). |
| `@ManyToOne` + `@JoinColumn` | Clave foránea hacia otra tabla. |
| `@Data` (Lombok) | Genera getters, setters, `toString`, `equals` y `hashCode`. |
| `@NoArgsConstructor` / `@AllArgsConstructor` | Constructores vacío (lo exige JPA) y con todos los campos. |

---

## 3. Repositorios (`repository/`)

Cada interfaz extiende `JpaRepository<Entidad, Long>`, que ya trae `findAll`, `findById`, `save`,
`deleteById`, `count`, etc. Las consultas extra se escriben de dos formas:

- **Por nombre del método**: Spring arma la consulta leyendo el nombre.
  `findByNombreContainingIgnoreCase(texto)` → `WHERE LOWER(nombre) LIKE LOWER('%texto%')`.
- **Con `@Query` (JPQL)**: cuando la condición compara dos columnas, como
  `findCriticos()` (`p.stock <= p.stockCritico`). En JPQL se usan nombres de clases y atributos, no de tablas.

Consulta agregada para el filtro combinado de productos:
`findByCategoriaIdAndNombreContainingIgnoreCase(categoriaId, texto)` → `WHERE categoria_id = ? AND LOWER(nombre) LIKE ?`.

---

## 4. DTOs de entrada (`dto/`)

`CategoriaRequest`, `ProductoRequest`, `BoletaRequest` (con su lista de `DetalleRequest`), `RegistroRequest`,
`UsuarioRequest`, `LoginRequest` y `PerfilRequest` son los datos que **envía** el frontend al crear o actualizar.
Las respuestas devuelven directamente las entidades, **excepto en usuarios**, que responden `UsuarioResponse`
(sin contraseña), y en el login, que responde `LoginResponse` (token + `UsuarioResponse`).

¿Por qué no recibir la entidad directamente?
- El cliente no debería poder mandar el `id` (lo genera MySQL).
- En el producto, el frontend envía solo `categoriaId` (un número) y no la categoría completa.
- Las anotaciones de validación quedan en una clase pensada para la entrada de datos.

| Anotación | Regla | Ejemplo |
|---|---|---|
| `@NotBlank` | No null, no vacío, no solo espacios | `nombre` |
| `@NotNull` | Obligatorio | `precio`, `stock`, `categoriaId` |
| `@Positive` | Mayor a 0 | `precio`, `precioOferta` |
| `@PositiveOrZero` | Mayor o igual a 0 | `stock`, `stockCritico` |
| `@Size(max = n)` | Largo máximo (igual al `length` de la columna) | `nombre`, `descripcion` |

Las anotaciones solo se revisan cuando el controller pone `@Valid` antes de `@RequestBody`.
`precioOferta < precio` compara dos campos, así que esa regla se revisa en `ProductoService`.

---

## 5. Services (`service/`)

Contienen la lógica de negocio. Reciben los repositorios por **constructor** (inyección de dependencias).

**CategoriaService**
| Método | Qué hace | Error posible |
|---|---|---|
| `listarTodas()` / `listarActivas()` | `findAll()` / `findByActivaTrue()` | – |
| `obtenerPorId(id)` | `findById(id)` | 404 si no existe |
| `crear(request)` | Revisa que el nombre no exista (`existsByNombreIgnoreCase`) y guarda | 409 nombre repetido |
| `actualizar(id, request)` | Busca otra categoría con ese nombre (`findByNombreIgnoreCase`); solo es conflicto si su `id` es distinto | 404, 409 |
| `eliminar(id)` | Cuenta los productos de la categoría (`countByCategoriaId`) | 404, 409 si tiene productos |

**ProductoService**
| Método | Qué hace | Error posible |
|---|---|---|
| `listar(categoriaId, buscar)` | Elige la consulta según los filtros que vengan (ninguno, uno o ambos) | – |
| `obtenerPorId(id)` | `findById(id)` | 404 |
| `listarOfertas()` / `listarCriticos()` | `findEnOferta()` / `findCriticos()` | – |
| `crear(request)` | Valida la oferta, busca la categoría, copia los datos y guarda | 400 oferta, 404 categoría |
| `actualizar(id, request)` | Igual que crear, pero sobre el producto existente | 400, 404 |
| `eliminar(id)` | Revisa si el producto está en alguna boleta (`existsByProductoId`) | 404, 409 si ya se vendió |

¿Por qué no se puede eliminar un producto vendido? `detalle_boleta.producto_id` es una clave foránea:
borrarlo dejaría boletas apuntando a un producto que no existe y se perdería el historial de ventas.
Lo mismo pasa con `producto.categoria_id` al eliminar una categoría con productos.

---

## 6. Controllers y endpoints (`controller/`)

El controller **solo** recibe la petición, llama al service y elige el código HTTP. No tiene `try/catch`:
los errores los responde `GlobalExceptionHandler`. Quién puede usar cada endpoint está en la sección 14.

| Método | Ruta | OK | Errores |
|---|---|---|---|
| GET | `/api/v1/categorias` | 200 | – |
| GET | `/api/v1/categorias/activas` | 200 | – |
| GET | `/api/v1/categorias/{id}` | 200 | 404 |
| POST | `/api/v1/categorias` | 201 | 400, 409 |
| PUT | `/api/v1/categorias/{id}` | 200 | 400, 404, 409 |
| DELETE | `/api/v1/categorias/{id}` | 204 | 404, 409 |
| GET | `/api/v1/productos?categoriaId=&buscar=` | 200 | – |
| GET | `/api/v1/productos/{id}` | 200 | 404 |
| GET | `/api/v1/productos/ofertas` | 200 | – |
| GET | `/api/v1/productos/criticos` | 200 | – |
| POST | `/api/v1/productos` | 201 | 400, 404 (categoría) |
| PUT | `/api/v1/productos/{id}` | 200 | 400, 404 |
| DELETE | `/api/v1/productos/{id}` | 204 | 404, 409 |

Anotaciones clave:
- `@RestController` + `@RequestMapping("/api/v1/...")`: clase que responde JSON en esa ruta base.
- `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`: método HTTP de cada endpoint.
- `@PathVariable Long id`: toma el `{id}` de la URL.
- `@RequestParam(required = false)`: parámetro opcional después del `?`.
- `@Valid @RequestBody`: convierte el JSON al DTO y lo valida.
- `ResponseEntity.status(HttpStatus.CREATED).body(...)` → 201; `ResponseEntity.noContent().build()` → 204.
- `/ofertas` y `/criticos` no chocan con `/{id}` porque Spring prioriza la ruta fija sobre la variable.

---

## 7. Manejo de errores (`exception/`)

Los services lanzan excepciones propias y `GlobalExceptionHandler` (`@RestControllerAdvice`) las
atrapa y responde siempre con el mismo JSON (`ErrorResponse`):

```json
{ "status": 409, "error": "Conflict",
  "mensaje": "Ya existe una categoría con el nombre 'Consolas'",
  "fecha": "2026-11-06T15:23:25.72" }
```

En errores de `@Valid` se agrega `campos`, con el problema de cada campo:

```json
{ "status": 400, "error": "Bad Request", "mensaje": "Hay campos con datos inválidos",
  "fecha": "...", "campos": { "precio": "El precio debe ser mayor a 0",
                              "categoriaId": "La categoría es obligatoria" } }
```

| Excepción | Quién la lanza | HTTP |
|---|---|---|
| `RecursoNoEncontradoException` | Services (id que no existe) | 404 |
| `ConflictoException` | Services (nombre repetido, categoría con productos, producto vendido) | 409 |
| `DatosInvalidosException` | Services (oferta >= precio) | 400 |
| `CredencialesInvalidasException` | `AuthService` (correo o contraseña incorrectos en el login) | 401 |
| `AccesoDenegadoException` | `BoletaService` (un cliente pide el historial de otro) | 403 |
| `MethodArgumentNotValidException` | Spring, cuando falla `@Valid` | 400 + `campos` |
| `HttpMessageNotReadableException` | Spring, JSON mal escrito | 400 |
| `MethodArgumentTypeMismatchException` | Spring, ej: `/productos/abc` | 400 |
| `NoResourceFoundException` | Spring, ruta que no existe | 404 |
| `HttpRequestMethodNotSupportedException` | Spring, ej: PATCH donde no existe | 405 |
| `DataIntegrityViolationException` | MySQL rechaza por UNIQUE o clave foránea (respaldo) | 409 |
| `Exception` (cualquier otra) | Error inesperado: se registra en consola y al cliente se le envía un mensaje genérico | 500 |

Las excepciones propias extienden `RuntimeException`, así no hay que declarar `throws` en cada método.

Los 401 (sin token) y 403 (rol sin permiso) de las reglas de `SecurityConfig` **no pasan por
`GlobalExceptionHandler`**: Spring Security rechaza la petición antes de llegar al controller. Por eso
`security/SeguridadErrorHandler` escribe el mismo `ErrorResponse` a mano (ver sección 14.5).

---

## 8. Swagger / OpenAPI

- Dependencia `springdoc-openapi-starter-webmvc-ui` (versión 3.2.0 en `pom.xml`, la línea 3.x es la de Spring Boot 4).
- `OpenApiConfig` define el título, la versión y la descripción.
- En los controllers: `@Tag` agrupa los endpoints (Categorías, Productos) y `@Operation` describe cada uno.
  `@Parameter` describe los filtros `categoriaId` y `buscar`.
- Cada `@Operation` empieza con **"Acceso: ..."** (público, ADMIN, ADMIN y VENDEDOR, etc.).
- `OpenApiConfig` declara el esquema `bearerAuth` (HTTP bearer, formato JWT): eso agrega el botón **Authorize**.
  Los endpoints con `@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)` muestran un candado y Swagger
  les envía el token. Cómo usarlo: sección 14.7.
- Swagger UI: `http://localhost:8080/swagger-ui/index.html` (se puede probar cada endpoint con "Try it out").
- JSON OpenAPI: `http://localhost:8080/v3/api-docs`.

---

## 9. Pruebas unitarias (`src/test/.../service/`)

`CategoriaServiceTest` (11 pruebas), `ProductoServiceTest` (14), `BoletaServiceTest` (19),
`UsuarioServiceTest` (18) y `AuthServiceTest` (3) usan **JUnit 5 + Mockito**:

- `@ExtendWith(MockitoExtension.class)`: activa Mockito en la clase de prueba.
- `@Mock`: crea un repositorio falso; **no se usa MySQL**.
- `@InjectMocks`: crea el service real y le pasa los mocks por el constructor.
- `when(repo.metodo(...)).thenReturn(...)`: decide qué devuelve el mock.
- `assertEquals`, `assertThrows`: comprueban el resultado o que se lance la excepción esperada.
- `verify(repo).save(...)` / `verify(repo, never()).save(...)`: comprueban si se llamó (o no) a un método.

- `ArgumentCaptor` (en `UsuarioServiceTest`): "atrapa" el objeto que se le pasó a `save` para revisar cómo
  se iba a guardar (ej: que la contraseña sea el hash y no el texto plano).
- `lenient()`: permite que un método simulado se llame también con argumentos que la prueba no revisa
  (ej: `findByCodigoReferido` al generar el código del usuario nuevo).

Patrón de cada prueba: **preparar** (`when`) → **ejecutar** (llamar al service) → **comprobar** (`assert`/`verify`).

Se ejecutan con `./mvnw test`. `LevelupBackendApplicationTests` levanta la aplicación completa,
por eso sí necesita MySQL encendido (y `JWT_SECRET` en `local.properties`).

Pruebas de seguridad (sección 14.8): `JwtServiceTest` (6, sin Spring ni Mockito) y `SeguridadTest`
(23, MockMvc, sin MySQL). En total: **95 pruebas**.

---

## 10. Boletas (órdenes de compra)

Reemplazan el checkout simulado del frontend. Archivos:
`BoletaController` → `BoletaService` → `BoletaRepository`, `ProductoRepository`, `UsuarioRepository` → MySQL.

### 10.1 Entrada: `BoletaRequest` y `DetalleRequest`
- Datos del cliente: `nombre`, `apellidos`, `correo` (`@Email`), `calle`, `region` y `comuna` son obligatorios
  (`@NotBlank`); `depto` e `indicaciones` son opcionales.
- `usuarioId` es opcional (`null` = visitante) y **solo lo respeta un ADMIN**. Con token de CLIENTE se usa el
  usuario del token; sin token (o con token de VENDEDOR) se ignora. Ver sección 14.6.
- `items`: `@NotEmpty` (al menos 1) y `List<@Valid DetalleRequest>`, así se valida cada item
  (`productoId` obligatorio, `cantidad` `@Min(1)`). Los errores aparecen como `"items[0].cantidad"`.
- **El precio no viene del frontend**: se toma de la base de datos, así nadie puede comprar más barato
  modificando el JSON.

### 10.2 Qué hace `BoletaService.crear` (paso a paso)
0. `crear(request, correoSesion)` decide el `usuarioId` según quién compra (sección 14.6) y llama a `crear(request)`.
1. Si viene `usuarioId`, busca al usuario → **404** si no existe.
2. Por cada item:
   - Si el producto está repetido en la orden → **400** (si no, dos líneas podrían superar el stock en total).
   - Busca el producto → **404** si no existe.
   - Revisa el stock → **409** si no alcanza, indicando el producto, la cantidad disponible y la solicitada.
   - Precio unitario = `precioOferta` si existe y es menor que `precio`; si no, `precio`.
   - Crea el `DetalleBoleta` (cantidad, precioUnitario, subtotal) y lo agrega con `boleta.agregarDetalle(detalle)`.
3. Montos (los precios **incluyen IVA**):
   - `subtotal` = suma de los subtotales de los detalles.
   - `descuento` = `Math.round(subtotal * 0.20)` si el usuario tiene `descuentoDuoc`; si no, 0.
   - `total = subtotal - descuento`.
   - `neto = Math.round(total / 1.19)` e `iva = total - neto`.
4. Pago simulado (igual que el frontend): si el correo contiene `"error"` → `RECHAZADA`; si no → `PAGADA`.
   **Solo si queda PAGADA** se descuenta el stock de cada producto.
5. `fecha = LocalDateTime.now()` y `numeroOrden = yyyyMMdd-NNN`: se cuentan las boletas que ya empiezan
   con el prefijo de hoy (`countByNumeroOrdenStartingWith("20261106-")`) y se suma 1 → `20261106-003`.
6. `boletaRepository.save(boleta)` guarda la boleta **y sus detalles** gracias a `cascade = CascadeType.ALL`.

La boleta rechazada **también se guarda** (queda el registro del intento) y la respuesta es 201 en ambos
casos: el frontend lee `estado` para mostrar "pago exitoso" o "pago con error".

### 10.3 ¿Por qué `@Transactional` en `crear`?
Todo lo que se hace en el método es una sola transacción de base de datos: o se guarda todo
(boleta, detalles y stock descontado) o no se guarda nada. Si algo falla después de modificar la base,
por ejemplo al guardar la boleta, se deshace el stock que ya se había descontado.

### 10.4 Ejemplo de cálculo
| Producto | Precio | Oferta | Cantidad | Precio unitario | Subtotal |
|---|---|---|---|---|---|
| PlayStation 5 Slim 1TB | 499.990 | – | 1 | 499.990 | 499.990 |
| Audífonos Gamer 7.1 | 59.990 | 49.990 | 2 | 49.990 | 99.980 |

- Visitante: subtotal 599.970, descuento 0, total 599.970 → neto = round(599.970 / 1,19) = **504.176**, IVA = **95.794**.
- Usuario Duoc con el mismo carrito: descuento = round(599.970 × 0,20) = 119.994, total 479.976,
  neto = round(479.976 / 1,19) = 403.341, IVA = 76.635.

### 10.5 Endpoints
| Método | Ruta | Acceso | OK | Errores |
|---|---|---|---|---|
| POST | `/api/v1/boletas` | público (token opcional) | 201 (PAGADA o RECHAZADA) | 400, 404, 409 |
| GET | `/api/v1/boletas` | ADMIN, VENDEDOR | 200, de la más reciente a la más antigua | 401, 403 |
| GET | `/api/v1/boletas/mis-compras` | CLIENTE, ADMIN | 200 (las del usuario del token) | 401, 403 |
| GET | `/api/v1/boletas/{id}` | ADMIN, VENDEDOR | 200 | 401, 403, 404 |
| GET | `/api/v1/boletas/numero/{numeroOrden}` | ADMIN, VENDEDOR | 200 | 401, 403, 404 |
| GET | `/api/v1/boletas/usuario/{usuarioId}` | ADMIN, o el CLIENTE dueño | 200 (lista vacía si no ha comprado) | 401, 403 (historial ajeno), 404 |

### 10.6 JSON de ejemplo para probar `POST /api/v1/boletas` en Swagger
En Swagger UI → **Boletas** → `POST /api/v1/boletas` → **Try it out** → pegar (sin token es una compra de
visitante y el `usuarioId` se ignora; para que quede a nombre de Pedro con su descuento, primero
**Authorize** con el token de Pedro):

```json
{
  "nombre": "Ana",
  "apellidos": "Pérez Soto",
  "correo": "ana.perez@gmail.com",
  "calle": "Av. Siempre Viva 123",
  "depto": "Depto 42",
  "region": "Región Metropolitana",
  "comuna": "Santiago",
  "indicaciones": "Dejar en conserjería",
  "usuarioId": null,
  "items": [
    { "productoId": 1, "cantidad": 1 },
    { "productoId": 11, "cantidad": 2 }
  ]
}
```

Variantes para la demostración:
- Pago rechazado: cambiar el correo a `"ana.error@gmail.com"` → `estado: "RECHAZADA"` y el stock no cambia.
- Sin stock: `{ "productoId": 5, "cantidad": 50 }` → 409.
- Producto inexistente: `{ "productoId": 999, "cantidad": 1 }` → 404.
- Datos inválidos: `"correo": "no-es-correo"` o `"cantidad": 0` → 400 con el detalle en `campos`.
- Después de una compra pagada: `GET /api/v1/productos/1` muestra el stock descontado.

### 10.7 Respuesta y datos del usuario
- Se devuelve la entidad `Boleta` con `detalles` y el `producto` de cada detalle.
- `DetalleBoleta.boleta` tiene `@JsonIgnore` para evitar el ciclo boleta → detalle → boleta.
- La contraseña nunca aparece: `Usuario.password` tiene `@JsonProperty(access = WRITE_ONLY)`
  (comprobado con una boleta de `pedro@duocuc.cl`: el usuario sale con todos sus datos menos `password`).
- `detalles` es `@OneToMany`, que por defecto es **LAZY** (se carga cuando se usa). Se puede convertir a JSON
  en el controller porque Spring Boot deja `spring.jpa.open-in-view` activado por defecto (aparece un aviso
  en el log al arrancar), lo que mantiene abierta la conexión hasta enviar la respuesta.

### 10.8 Preguntas posibles de la defensa
- **¿Por qué se guarda `precioUnitario` en el detalle?** Si después cambia el precio del producto,
  la boleta debe seguir mostrando lo que realmente se cobró.
- **¿Qué pasa si dos compras llegan al mismo tiempo?** Podrían calcular el mismo correlativo. Como
  `numero_orden` es `UNIQUE`, MySQL rechaza la segunda y `GlobalExceptionHandler` responde 409; no se
  corrompen datos. Una mejora sería reintentar o usar una tabla de secuencias.
- **¿Por qué el stock se revisa también si el pago se rechaza?** Primero se valida la orden completa y
  después se simula el pago; una orden imposible de despachar no debería llegar al pago.

---

## 11. Usuarios (registro, administración y perfil)

Archivos: `UsuarioController` (mantenedor, solo ADMIN) y `AuthController` (registro, login y perfil propio)
→ `UsuarioService` / `AuthService` → `UsuarioRepository`, `BoletaRepository` (+ `PasswordEncoder`, `JwtService`) → MySQL.
Las reglas son las mismas del frontend (`src/data/usuarios.js` y `src/utils/validaciones.js`).

### 11.1 Contraseñas con BCrypt
- `BCryptPasswordEncoder` viene en `spring-boot-starter-security` (antes se usaba solo `spring-security-crypto`,
  mientras los endpoints seguían abiertos).
- `config/PasswordConfig` declara el bean `PasswordEncoder` → `new BCryptPasswordEncoder()`.
- `passwordEncoder.encode("pedro1234")` → `$2a$10$...` (60 caracteres). Cada vez da un hash distinto porque
  incluye una **sal** aleatoria, y no se puede revertir.
- `passwordEncoder.matches("pedro1234", hash)` → `true`/`false`. Es lo que usa `AuthService.login`.
- Se cifra en: `UsuarioService.registrar`, `crear`, `actualizar` (solo si viene una contraseña nueva)
  y `DataInitializer`.
- Para comprobarlo en MySQL: `SELECT correo, password FROM usuario;` → todas empiezan con `$2a$10$`.

### 11.2 Por qué hay `UsuarioResponse`
En el resto de la API se devuelven entidades. En usuarios se responde `UsuarioResponse`, que **no tiene el
campo `password`**, así es imposible enviarlo por error. `UsuarioResponse.desde(usuario)` copia los campos
uno por uno. Además, la entidad `Usuario` sigue teniendo `@JsonProperty(WRITE_ONLY)` en `password`, para
cuando aparece dentro de una `Boleta`.

### 11.3 DTOs y validaciones (iguales al frontend)
| Campo | Regla | Anotación |
|---|---|---|
| nombre / apellidos | 2 a 50 caracteres; letras (con tildes y ñ) separadas por espacio, `-` o `'` | `@Size(min = 2, max = 50)` + `@Pattern("^\\s*\\p{L}+(?:[ '-]\\p{L}+)*\\s*$")` |
| correo | `algo@dominio.ext`, con extensión de 2 o más caracteres | `@Pattern("^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}\\s*$")` |
| password | Mínimo 8, con letras y números (máximo 72, el límite de BCrypt) | `@Pattern("^(?=.*[a-zA-Z])(?=.*\\d).{8,72}$")` |
| fechaNacimiento | Obligatoria y pasada | `@NotNull @Past` (los 18 años se revisan en el service) |

- `\p{L}` = cualquier letra Unicode (incluye á, ñ, ü). `(?=.*\d)` = "en algún lugar hay un dígito" (lookahead).
- Los `\s*` de los extremos aceptan espacios al inicio y al final, porque el frontend valida con `trim()`.
  El service guarda los datos sin esos espacios.
- No se usa `@Email` porque acepta `ana@gmail` sin `.com`, y el frontend lo rechaza.
- `RegistroRequest`: lo que llena el cliente, más `codigoReferido` opcional. No tiene `rol`: siempre es CLIENTE.
- `UsuarioRequest` (admin): agrega `rol`, no tiene `codigoReferido` (el admin no puede regalar puntos) y
  `password` es opcional (al editar, vacía = mantener la actual; al crear, el service la exige → 400).

### 11.4 Qué hace `UsuarioService.registrar` (con `@Transactional`)
1. Normaliza el correo (`trim().toLowerCase()`) y revisa que no exista → **409**.
2. Calcula la edad con `Period.between(fechaNacimiento, hoy).getYears()` → **400** si es menor de 18.
3. Si viene `codigoReferido`, lo busca (`trim().toUpperCase()`) → **400** si no existe. Se revisa **antes**
   de guardar, para no crear el usuario con un código inválido.
4. Crea el usuario: contraseña cifrada, rol `CLIENTE`, `descuentoDuoc` si el correo termina en `@duoc.cl`
   o `@duocuc.cl`, 0 puntos y un código de referido nuevo.
5. Con referido válido: el nuevo queda con **100 puntos** y `referidoPor = "LVLPEDRO"`, y quien lo refirió
   suma **100 puntos**. Se guardan los dos; por eso el método es `@Transactional`.

**Código de referido** (misma lógica del frontend): `"LVL"` + hasta 5 letras A–Z del nombre en mayúsculas
+ un número de 10 a 99. Ej: `"Valentina"` → `LVLVALEN58`; `"María José"` → `LVLMARAJ42` (las letras con tilde
se descartan, igual que en JavaScript). A diferencia del frontend, se revisa que el código no exista. Con 2 dígitos
hay solo 90 opciones por nombre, así que después de 20 intentos se usan 4 dígitos.

### 11.5 Mantenedor del administrador
- `listar(rol)`: `findAll()` o `findByRol(rol)`. Con `?rol=jefe` responde 400, porque Spring no puede
  convertir "jefe" al enum `Rol`.
- `crear`: igual que el registro, pero con el rol elegido y sin referido.
- `actualizar`: el correo no puede ser de **otro** usuario (409); el suyo propio sí. `descuentoDuoc` se
  recalcula siempre según el correo. Puntos y código de referido no cambian.
- `eliminar`: si `boletaRepository.existsByUsuarioId(id)` → **409** (sus boletas quedarían apuntando a un
  usuario que no existe).
- **Perfil propio**: `GET/PUT /api/v1/auth/perfil` (cualquier usuario con sesión). El usuario sale del correo
  del token, nunca de un id en la URL, así nadie puede editar a otro. `UsuarioService.actualizarPerfil` cambia
  nombre, apellidos, fecha de nacimiento y contraseña (vacía = se mantiene). `PerfilRequest` **no tiene** rol,
  puntos, descuento ni correo: el correo es el `sub` del token (si cambiara, el token dejaría de servir) y de él
  depende el descuento Duoc. Si hay que cambiar el correo, lo hace el ADMIN con `PUT /api/v1/usuarios/{id}`.

### 11.6 Endpoints
Registro, login y perfil (`AuthController`):

| Método | Ruta | Acceso | OK | Errores |
|---|---|---|---|---|
| POST | `/api/v1/auth/registro` | público | 201 | 400 (validación, menor de edad, referido inexistente), 409 (correo) |
| POST | `/api/v1/auth/login` | público | 200 (token + usuario) | 400, 401 (credenciales) |
| GET | `/api/v1/auth/me` | con sesión | 200 | 401 |
| GET | `/api/v1/auth/perfil` | con sesión | 200 | 401 |
| PUT | `/api/v1/auth/perfil` | con sesión | 200 | 400, 401 |

El registro antes estaba en `/api/v1/usuarios/registro`: **el frontend debe usar la ruta nueva** (la antigua
ahora cae en `/api/v1/usuarios/**`, solo para ADMIN, y responde 401).

Mantenedor (`UsuarioController`), todo **solo ADMIN** (sin token 401, otro rol 403):

| Método | Ruta | OK | Errores |
|---|---|---|---|
| GET | `/api/v1/usuarios?rol=` | 200 | 400 (rol inválido) |
| GET | `/api/v1/usuarios/{id}` | 200 | 404 |
| POST | `/api/v1/usuarios` | 201 | 400, 409 |
| PUT | `/api/v1/usuarios/{id}` | 200 | 400, 404, 409 |
| DELETE | `/api/v1/usuarios/{id}` | 204 | 404, 409 (tiene boletas) |

Historial de compras: `GET /api/v1/boletas/usuario/{id}` (sección 10).

### 11.7 Usuarios de prueba (`DataInitializer.cargarUsuarios`)
| Correo | Contraseña | Rol | Datos |
|---|---|---|---|
| admin@levelup.cl | admin1234 | ADMIN | código `LVLADMIN` |
| vendedor@levelup.cl | vendedor1234 | VENDEDOR | código `LVLVENDE` |
| pedro@duocuc.cl | pedro1234 | CLIENTE | descuento Duoc, 100 puntos, código `LVLPEDRO` |

Son datos ficticios del enunciado. No deben reutilizarse en ningún sistema real.

### 11.8 JSON de ejemplo para Swagger
**Registro con referido** (`POST /api/v1/auth/registro`): Pedro sube de 100 a 200 puntos.
```json
{
  "nombre": "Valentina",
  "apellidos": "Rojas",
  "correo": "valentina.rojas@duoc.cl",
  "password": "valen1234",
  "fechaNacimiento": "2003-04-12",
  "codigoReferido": "LVLPEDRO"
}
```
Variantes: `"fechaNacimiento": "2012-01-01"` → 400 (menor de edad); `"codigoReferido": "LVLNOEXISTE"` → 400;
repetir el mismo correo → 409; `"password": "abcdefgh"` → 400 (sin números).

**Crear un vendedor** (`POST /api/v1/usuarios`, con token de ADMIN), con el rol en **mayúsculas**:
```json
{
  "nombre": "Sofía",
  "apellidos": "Vega",
  "correo": "sofia@levelup.cl",
  "password": "sofia1234",
  "fechaNacimiento": "1999-09-09",
  "rol": "VENDEDOR"
}
```
Para editarlo sin cambiar la contraseña, se usa `PUT /api/v1/usuarios/{id}` con el mismo JSON y `"password": ""`.

**Compra con descuento Duoc** (`POST /api/v1/boletas` con el token de Pedro, o con token de ADMIN y
`"usuarioId": 3`): con el Mousepad
(id 14, oferta 14.990) → descuento 2.998, total 11.992, neto 10.077, IVA 1.915.

### 11.9 Diferencia pendiente con el frontend: mayúsculas en los roles
El frontend usa `"admin"` y `"cliente"`, y el backend `ADMIN`, `VENDEDOR` y `CLIENTE` (enum `Rol`). Hoy, si se
envía `"rol": "cliente"`, la respuesta es 400 ("El cuerpo de la petición no es un JSON válido"). Al conectar
el frontend hay que enviar y comparar los roles en mayúsculas.

---

## 12. Ejemplos de modificaciones que podría pedir el profesor

### Agregar un campo a una entidad (ej: `marca` en Producto)
1. En `Producto.java`:
   ```java
   @Column(length = 60)
   private String marca;
   ```
2. Reiniciar: con `ddl-auto=update`, Hibernate agrega la columna `marca` a la tabla `producto`.
   No hace falta escribir getters/setters porque los genera Lombok (`@Data`).
3. (Opcional) Cargar la marca en `DataInitializer` con `producto.setMarca(...)`.
   Ojo: `DataInitializer` solo carga datos si `categoria` está vacía; los productos que ya existen
   quedan con `marca = NULL`.
4. Si el campo fuera `nullable = false`, MySQL fallaría con filas existentes sin valor:
   hay que darle un valor por defecto o vaciar las tablas.

### Agregar una consulta nueva al repositorio (ej: productos de una categoría con stock)
```java
List<Producto> findByCategoriaIdAndStockGreaterThan(Long categoriaId, Integer stock);
```

### Agregar un nuevo estado a la boleta (ej: `PENDIENTE`)
Agregarlo en `EstadoBoleta.java`. Como se guarda como texto (`EnumType.STRING`), los datos
existentes no se ven afectados.

### Cambiar la regla de stock crítico
Modificar la consulta `@Query` de `findCriticos()` en `ProductoRepository`.

### Agregar un campo al mantenedor de productos (ej: `marca`)
Además del cambio en la entidad (ver arriba):
1. `ProductoRequest`: agregar `@Size(max = 60) private String marca;`.
2. `ProductoService.copiarDatos`: agregar `producto.setMarca(request.getMarca());`.
Si se olvida el paso 2, el campo llega al backend pero nunca se guarda.

### Crear un endpoint nuevo (ej: productos sin stock, `GET /api/v1/productos/agotados`)
1. Repository: `List<Producto> findByStock(Integer stock);`
2. Service: `public List<Producto> listarAgotados() { return productoRepository.findByStock(0); }`
3. Controller:
   ```java
   @GetMapping("/agotados")
   @Operation(summary = "Listar productos agotados", description = "Productos con stock 0.")
   public List<Producto> listarAgotados() {
       return productoService.listarAgotados();
   }
   ```
4. Prueba en `ProductoServiceTest` con `when(productoRepository.findByStock(0)).thenReturn(...)`.

### Agregar una validación nueva (ej: precio máximo de $5.000.000)
- Si revisa un solo campo: anotación en el DTO, ej: `@Max(value = 5000000, message = "...")` en `precio`.
- Si compara varios campos o necesita la base de datos: en el service, lanzando
  `DatosInvalidosException` (400) o `ConflictoException` (409).

### Permitir eliminar una categoría con productos dejándola inactiva
En `CategoriaService.eliminar`, en lugar de lanzar `ConflictoException`:
`categoria.setActiva(false); categoriaRepository.save(categoria);` (eliminación lógica).
Actualizar la prueba `eliminar_conProductos_lanzaConflictoYNoElimina`.

### Cambiar el formato o el mensaje de los errores
Todo está en `GlobalExceptionHandler` (mensajes) y `ErrorResponse` (campos del JSON).

### Cambiar el porcentaje del descuento Duoc (ej: 15%)
En `BoletaService`: `PORCENTAJE_DESCUENTO_DUOC = 0.15`. Actualizar los valores esperados en
`crear_usuarioDuoc_aplica20PorCientoYCalculaNetoEIva` (y el nombre de la prueba).

### Agregar costo de despacho a la boleta (ej: $3.990 fijo)
1. `Boleta`: `@Column(nullable = false) private Integer despacho = 0;`
2. `BoletaService.crear`: `int total = subtotal - descuento + DESPACHO;` y `boleta.setDespacho(DESPACHO);`
3. El neto y el IVA se siguen calculando desde `total`, sin cambios.

### Agregar un estado nuevo (ej: `ANULADA`) con un endpoint para anular
1. `EstadoBoleta`: agregar `ANULADA`.
2. `BoletaService.anular(id)`: buscar la boleta; si ya está anulada → `ConflictoException`; si estaba
   `PAGADA`, devolver el stock (`producto.setStock(stock + cantidad)`); cambiar el estado y guardar.
3. `BoletaController`: `@PatchMapping("/{id}/anular")` con `@Operation`.

### Cambiar la regla del pago simulado
Está en `BoletaService.simularPago(correo)`. Ej: rechazar también si el total supera $2.000.000.

### Cambiar los puntos por referido (ej: 150)
`UsuarioService.PUNTOS_POR_REFERIDO = 150`. Actualizar los valores esperados en
`registrar_conReferidoValido_sumaPuntosAAmbos` y avisar al frontend, que tiene su propia constante.

### Cambiar la edad mínima (ej: 16 años)
`UsuarioService.EDAD_MINIMA = 16`. La prueba `registrar_menorDeEdad...` usa 17 años: hay que cambiarla a 15.

### Agregar un campo al usuario (ej: `telefono`)
1. `Usuario`: `@Column(length = 15) private String telefono;`
2. `RegistroRequest` y `UsuarioRequest`: el campo con `@Pattern(regexp = "^\\+?\\d{8,12}$", message = "...")`.
3. `UsuarioResponse`: el campo y `response.setTelefono(usuario.getTelefono())` en `desde(...)`.
4. `UsuarioService`: `usuario.setTelefono(...)` en `registrar` y en `copiarDatos`.
Si se olvida el paso 3, el teléfono se guarda pero nunca aparece en las respuestas.

### Agregar otro dominio con descuento (ej: `@profesor.duoc.cl`)
En `UsuarioService.esCorreoDuoc`, agregar `|| correo.endsWith("@profesor.duoc.cl")`, y una prueba nueva.

### Endpoint para canjear puntos (ej: `PATCH /api/v1/usuarios/{id}/canjear?puntos=50`)
Service: buscar al usuario; si `puntos > usuario.getPuntos()` → `DatosInvalidosException`; si no, restar y
guardar. Controller: `@PatchMapping("/{id}/canjear")` con `@RequestParam Integer puntos` y `@Operation`.

### Cambiar los permisos de un rol (ej: que el VENDEDOR también pueda editar productos)
Todo está en `security/SecurityConfig`. Antes de la regla `.requestMatchers("/api/v1/productos/**", ...).hasRole("ADMIN")`:
```java
.requestMatchers(HttpMethod.PUT, "/api/v1/productos/**").hasAnyRole("ADMIN", "VENDEDOR")
```
Las reglas se leen **de arriba hacia abajo** y gana la primera que calza: si se pone después de la regla de
ADMIN, nunca se usa. Actualizar la prueba `vendedor_noPuedeCrearProductos...` si cambia lo que se prueba y el
"**Acceso:**" del `@Operation`.

### Hacer pública una ruta (ej: que cualquiera vea el stock crítico)
Cambiar `.hasAnyRole("ADMIN", "VENDEDOR")` por `.permitAll()` en la regla de `/api/v1/productos/criticos`
(o borrarla: la siguiente regla, `GET /api/v1/productos/**`, ya es `permitAll`).

### Agregar un rol nuevo (ej: `BODEGUERO`, que solo ve el stock crítico)
1. `Rol.java`: agregar `BODEGUERO`. Se guarda como texto, así que no afecta a los datos existentes.
2. `SecurityConfig`: `.hasAnyRole("ADMIN", "VENDEDOR", "BODEGUERO")` en `/productos/criticos`.
3. `UsuarioDetailsService` no cambia: arma `"ROLE_" + rol` solo.

### Cambiar la duración del token (ej: 1 hora)
`application.properties`: `jwt.expiracion=3600000` (1 × 60 × 60 × 1000). No hay que tocar código.

### Agregar un dato al token (ej: el nombre)
En `JwtService.generarToken`: `.claim("nombre", usuario.getNombre())`. Recordar que el payload **no está cifrado**:
nunca poner la contraseña ni datos sensibles.

### Exigir sesión para comprar
`SecurityConfig`: cambiar `.requestMatchers(HttpMethod.POST, "/api/v1/boletas").permitAll()` por
`.hasRole("CLIENTE")` (o `.authenticated()`), y actualizar la prueba `visitante_puedeComprarSinCuenta`.

---

## 13. Comprobación del arranque

Al ejecutar `./mvnw spring-boot:run` (con `show-sql=true`) se ve en la consola:
- Los `create table` de `categoria`, `producto`, `usuario`, `boleta` y `detalle_boleta`.
- Los `insert` de las 6 categorías y los 15 productos (solo la primera vez).

Para verificar en MySQL:
```sql
USE levelup_db;
SHOW TABLES;
SELECT COUNT(*) FROM categoria;  -- 6
SELECT COUNT(*) FROM producto;   -- 15
SELECT correo, rol, LEFT(password, 7) FROM usuario;  -- 3 usuarios iniciales, hash $2a$10$
```

---

## 14. Seguridad: Spring Security + JWT y permisos por rol

Archivos (paquete `security/`):

| Archivo | Qué hace |
|---|---|
| `JwtService` | Genera y valida los tokens (firma HMAC-SHA con `jwt.secret`, vencen en `jwt.expiracion` = 8 h). |
| `JwtAuthenticationFilter` | Filtro (`OncePerRequestFilter`) que lee `Authorization: Bearer <token>` en cada petición. |
| `UsuarioDetailsService` | Busca al usuario por correo y le da la authority `ROLE_` + rol (ej: `ROLE_ADMIN`). |
| `SecurityConfig` | Reglas: qué rol puede usar cada ruta. Stateless, sin CSRF, con CORS. |
| `SeguridadErrorHandler` | Responde los 401 y 403 de Spring Security con el mismo JSON `ErrorResponse`. |

Más `controller/AuthController`, `service/AuthService`, los DTOs `LoginRequest`, `LoginResponse`, `PerfilRequest`
y las excepciones `CredencialesInvalidasException` (401) y `AccesoDenegadoException` (403).

### 14.1 Tabla de permisos (pauta: ADMIN acceso total; VENDEDOR solo productos y órdenes; CLIENTE solo la tienda)

✅ = permitido · **401** = sin token o token inválido · **403** = con sesión, pero el rol no tiene permiso.

| Endpoint | Visitante | CLIENTE | VENDEDOR | ADMIN |
|---|---|---|---|---|
| Swagger (`/swagger-ui/**`, `/v3/api-docs/**`) | ✅ | ✅ | ✅ | ✅ |
| `POST /auth/login`, `POST /auth/registro` | ✅ | ✅ | ✅ | ✅ |
| `GET /auth/me`, `GET` y `PUT /auth/perfil` | 401 | ✅ | ✅ | ✅ |
| `GET /productos`, `/productos/ofertas`, `/productos/{id}` | ✅ | ✅ | ✅ | ✅ |
| `GET /productos/criticos` | 401 | 403 | ✅ | ✅ |
| `POST`, `PUT`, `DELETE /productos/**` | 401 | 403 | 403 | ✅ |
| `GET /categorias/**` | ✅ | ✅ | ✅ | ✅ |
| `POST`, `PUT`, `DELETE /categorias/**` | 401 | 403 | 403 | ✅ |
| `POST /boletas` | ✅ sin usuario | ✅ a su nombre | ✅ sin usuario | ✅ con el `usuarioId` del JSON |
| `GET /boletas`, `/boletas/{id}`, `/boletas/numero/{n}` | 401 | 403 | ✅ | ✅ |
| `GET /boletas/usuario/{id}` | 401 | solo el suyo (otro → 403) | 403 | ✅ |
| `GET /boletas/mis-compras` | 401 | ✅ | 403 | ✅ |
| `/usuarios/**` (mantenedor) | 401 | 403 | 403 | ✅ |

(Todas las rutas empiezan con `/api/v1`.)

### 14.2 Flujo del token: login → token → filtro → controller

```
1. LOGIN
   POST /api/v1/auth/login {"correo":"pedro@duocuc.cl","password":"pedro1234"}
   AuthController.login → AuthService.login
     → UsuarioRepository.findByCorreoIgnoreCase      (¿existe?  si no → 401)
     → passwordEncoder.matches(password, hash)        (¿coincide? si no → 401)
     → JwtService.generarToken(usuario)                (sub = correo, rol = CLIENTE, exp = +8 h)
   ← 200 {"token":"eyJhbGciOiJIUzUxMiJ9.eyJzdWIi...","tipo":"Bearer","usuario":{...,"rol":"CLIENTE"}}

2. EL FRONTEND GUARDA EL TOKEN y lo envía en cada petición:
   GET /api/v1/boletas/mis-compras
   Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...

3. FILTRO (JwtAuthenticationFilter, antes de cualquier controller)
   ¿Hay "Bearer ..."?        no → sigue como visitante
   JwtService.esTokenValido  no → sigue como visitante y anota "token inválido o vencido"
   JwtService.obtenerCorreo  → UsuarioDetailsService.loadUserByUsername(correo)  (rol ACTUAL desde MySQL)
   → SecurityContextHolder: usuario autenticado con ROLE_CLIENTE

4. REGLAS (SecurityConfig.authorizeHttpRequests), de arriba hacia abajo
   GET /api/v1/boletas/mis-compras → hasAnyRole("CLIENTE","ADMIN") → ✅
   (sin sesión → SeguridadErrorHandler 401; rol incorrecto → SeguridadErrorHandler 403)

5. CONTROLLER
   BoletaController.misCompras(Authentication authentication)
     authentication.getName() = "pedro@duocuc.cl"
   → BoletaService.misCompras(correo) → BoletaRepository → MySQL
```

¿Qué tiene el token? Tres partes en Base64 separadas por puntos: `header.payload.firma`. El payload de Pedro es
`{"sub":"pedro@duocuc.cl","rol":"CLIENTE","iat":1794226023,"exp":1794254823}` (se puede ver en jwt.io).
**No está cifrado, está firmado**: cualquiera lo lee, pero si alguien cambia `"rol"` a `"ADMIN"` la firma deja de
coincidir y `esTokenValido` responde `false` (lo demuestra la prueba `esTokenValido_payloadModificadoParaSerAdmin_esFalso`).
Para firmar hace falta `JWT_SECRET`, que solo tiene el servidor.

¿Por qué el rol sale de la base de datos y no del token? `UsuarioDetailsService` vuelve a buscar al usuario en cada
petición. Si el ADMIN cambia el rol de alguien (o lo elimina), el cambio aplica de inmediato aunque su token siga
vigente. El claim `rol` del token es para el frontend (para mostrar u ocultar menús).

### 14.3 SecurityConfig, línea por línea
- `csrf.disable()`: CSRF aprovecha las **cookies** que el navegador envía solo. Aquí no hay cookies de sesión:
  el token va en un header que el navegador nunca agrega por su cuenta.
- `sessionCreationPolicy(STATELESS)`: el servidor no guarda sesiones; cada petición se valida con su token.
  Por eso no existe un "logout" en el backend: el frontend borra el token.
- `cors(Customizer.withDefaults())`: usa el bean `corsConfigurationSource` de `CorsConfig`. Tiene que ir dentro de
  Spring Security: el navegador hace una consulta previa `OPTIONS` **sin token**, y si CORS se aplicara después
  de Spring Security, esa consulta recibiría 401.
- `httpBasic` y `formLogin` desactivados: el único login es `POST /api/v1/auth/login`.
- `exceptionHandling(...)`: 401 y 403 con nuestro JSON.
- `addFilterBefore(new JwtAuthenticationFilter(...), UsernamePasswordAuthenticationFilter.class)`: nuestro filtro
  corre antes que el de usuario y contraseña de Spring. Se crea con `new` y no es `@Component` para que
  Spring Boot no lo registre una segunda vez como filtro general.
- `hasRole("ADMIN")` busca la authority `"ROLE_ADMIN"`; `hasAnyRole("ADMIN","VENDEDOR")`, cualquiera de las dos.
- `/**` incluye la ruta base: `/api/v1/productos/**` también calza con `/api/v1/productos`.
- El orden importa: `GET /productos/criticos` va antes que `GET /productos/**` (permitAll); si no, sería público.
- `.anyRequest().authenticated()`: cualquier ruta no listada exige sesión (por defecto, cerrado).

### 14.4 Permisos por rol vs. permisos por dueño
- **Por rol y ruta** → `SecurityConfig` (ej: solo ADMIN y VENDEDOR ven `/boletas`).
- **Por dueño del dato** → service, porque depende del id pedido: `BoletaService.historialUsuario(usuarioId, correo)`
  busca al usuario del token; si no es ADMIN y su id no es `usuarioId` → `AccesoDenegadoException` → 403
  ("Solo puedes ver tu propio historial de compras").
- `/mis-compras` y `/auth/perfil` no reciben ningún id: el usuario sale del token, así es imposible pedir los datos de otro.

### 14.5 Errores 401 y 403 con el mismo JSON
`GlobalExceptionHandler` (`@RestControllerAdvice`) solo atrapa errores que ocurren **dentro** de un controller.
Las reglas de `SecurityConfig` rechazan la petición antes, en la cadena de filtros, así que
`SeguridadErrorHandler` implementa:
- `AuthenticationEntryPoint.commence` → **401** "Debes iniciar sesión para acceder a este recurso", o
  "El token no es válido o expiró. Inicia sesión nuevamente" si el filtro anotó un token malo.
- `AccessDeniedHandler.handle` → **403** "No tienes permiso para realizar esta acción".

Escribe el `ErrorResponse` con el `ObjectMapper` de Spring (Jackson 3), así el JSON es idéntico:
```json
{ "status": 403, "error": "Forbidden", "mensaje": "No tienes permiso para realizar esta acción",
  "fecha": "2026-11-09T09:07:06.34" }
```
El 401 del login (clave incorrecta) y el 403 del historial ajeno sí pasan por `GlobalExceptionHandler`, porque
los lanza un service (`CredencialesInvalidasException`, `AccesoDenegadoException`).

**Token vencido en una ruta pública** (ej: ver productos con un token viejo guardado en el navegador): se atiende
como visitante, para que la tienda no se rompa. En una ruta protegida responde 401.

### 14.6 Quién compra en `POST /api/v1/boletas`
`BoletaController.crear` recibe `Authentication` (null si no hay token) y llama a
`BoletaService.crear(request, correoSesion)`:

| Quién compra | `usuarioId` que se usa | Descuento Duoc |
|---|---|---|
| Visitante (sin token) | `null`, aunque el JSON traiga otro | no |
| CLIENTE | el del token (se ignora el del JSON) | sí, si su correo es Duoc |
| VENDEDOR | `null` (según la pauta, no compra en la tienda) | no |
| ADMIN | el del JSON (acceso total, ej: registrar una venta para un cliente) | el del cliente elegido |

Si se respetara el `usuarioId` de un visitante, cualquiera podría comprar "como Pedro" y usar su 20% de descuento.

### 14.7 Probar en Swagger
1. `http://localhost:8080/swagger-ui/index.html` → **Autenticación** → `POST /api/v1/auth/login` → Try it out:
   `{"correo":"admin@levelup.cl","password":"admin1234"}`.
2. Copiar el valor de `token` (sin comillas).
3. Botón **Authorize** (arriba a la derecha) → pegar el token **sin escribir "Bearer"** → Authorize.
4. Los endpoints con candado ahora envían el token. Para cambiar de usuario: Authorize → Logout → otro token.

Prueba rápida de la tabla: con el token de `vendedor@levelup.cl` / `vendedor1234`, `GET /api/v1/boletas` → 200 y
`POST /api/v1/productos` → 403; con el de `pedro@duocuc.cl` / `pedro1234`, `GET /api/v1/boletas/usuario/1` → 403
y `GET /api/v1/boletas/mis-compras` → 200.

### 14.8 Pruebas de seguridad
- `JwtServiceTest` (6): se crea con `new JwtService(clave, duración)`. Comprueba el subject y el claim `rol`, y que se
  rechacen un token con el payload modificado, uno firmado con otra clave, uno vencido (duración negativa) y un
  texto que no es token. También, que una clave de menos de 32 caracteres impida arrancar.
- `AuthServiceTest` (3, Mockito): login correcto (token + `"Bearer"` + usuario), contraseña incorrecta y correo
  inexistente (mismo mensaje en los dos casos, y no se genera token).
- `SeguridadTest` (23, MockMvc): `@WebMvcTest` carga solo la capa web + nuestra seguridad (`@Import`), con los
  services como `@MockitoBean`. Por eso **no necesita MySQL**: prueba quién puede entrar a cada ruta, no la lógica.
  - `@WithMockUser(username = "...", roles = "VENDEDOR")` simula una sesión sin token.
  - Las pruebas "con token real" generan un JWT con `JwtService` y lo envían en el header: prueban el camino
    completo filtro → `UsuarioDetailsService` → reglas → controller.
  - `jsonPath("$.mensaje")` revisa que los 401 y 403 tengan el JSON `ErrorResponse`.

### 14.9 Dependencias (pom.xml)
- `spring-boot-starter-security` (reemplaza a `spring-security-crypto`; incluye BCrypt) y, para pruebas,
  `spring-boot-starter-security-test` (`@WithMockUser` y la integración con MockMvc).
- JJWT 0.13.0: `jjwt-api` (lo que usa nuestro código) y, solo al ejecutar, `jjwt-impl` y **`jjwt-gson`**.
  ¿Por qué Gson y no `jjwt-jackson`? `jjwt-jackson` depende de **Jackson 2** (`com.fasterxml.jackson.databind`),
  pero Spring Boot 4 usa **Jackson 3** (`tools.jackson`) y JJWT no tiene un módulo para Jackson 3. No llegan a
  chocar (son paquetes distintos), pero el JWT quedaría atado a la versión antigua. Con Gson se evita la mezcla, y
  su versión la administra Spring Boot. Las respuestas de la API siguen usando Jackson 3.

### 14.10 Preguntas posibles de la defensa
- **¿Dónde se guarda la sesión?** En ningún lado del servidor (stateless): en el token, que guarda el frontend.
- **¿Cómo se cierra sesión?** El frontend borra el token. El token sigue siendo válido hasta que vence (8 h);
  para invalidarlo antes habría que mantener una "lista negra" en el servidor.
- **¿Por qué el login dice "Correo o contraseña incorrectos" en ambos casos?** Para no revelar qué correos
  están registrados.
- **¿Qué pasa si roban `JWT_SECRET`?** Podrían fabricar tokens de ADMIN. Por eso va en `local.properties`
  (fuera de GitHub). Si se filtra, se cambia y todos los tokens anteriores dejan de servir.
- **¿Diferencia entre 401 y 403?** 401 = no sé quién eres (sin token o token malo). 403 = sé quién eres, pero
  tu rol no puede hacer eso.
- **¿Por qué no hay CSRF?** Ver 14.3: no hay cookies de sesión.
