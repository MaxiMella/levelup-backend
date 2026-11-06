# Guía de estudio – Level-Up Gamer Backend

Este documento explica cada parte del backend para poder defenderlo y modificarlo en vivo.
Se irá completando a medida que se agreguen services, controllers, seguridad y Swagger.

---

## 1. Cómo arranca el proyecto

1. `LevelupBackendApplication` (con `@SpringBootApplication`) inicia Spring Boot.
2. Spring lee `src/main/resources/application.properties`, que importa `local.properties`
   (con `spring.config.import`). Ahí está `DB_PASSWORD`, la contraseña de MySQL de cada integrante.
   `local.properties` está en `.gitignore`, así que nunca se sube a GitHub
   (`local.properties.example` muestra el formato).
3. Con `createDatabaseIfNotExist=true` en la URL, MySQL crea la base `levelup_db` si no existe.
4. Con `spring.jpa.hibernate.ddl-auto=update`, Hibernate revisa las clases `@Entity` del paquete
   `model` y crea (o actualiza) las tablas. No borra datos.
5. Cuando el contexto termina de cargar, `DataInitializer` (un `CommandLineRunner`) inserta
   6 categorías y 15 productos, pero solo si la tabla `categoria` está vacía.
6. `CorsConfig` permite que el frontend React (`http://localhost:5173`) llame a `/api/**`.
7. `OpenApiConfig` + springdoc generan la documentación en Swagger UI:
   `http://localhost:8080/swagger-ui/index.html`.

Flujo general de una petición:

```
Frontend React → Controller (/api/v1/...) → Service (lógica y validaciones)
              → Repository (JpaRepository) → Hibernate → MySQL

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

`CategoriaRequest`, `ProductoRequest` y `BoletaRequest` (con su lista de `DetalleRequest`) son los datos
que **envía** el frontend al crear o actualizar. Las respuestas devuelven directamente las entidades.

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
los errores los responde `GlobalExceptionHandler`.

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
| `MethodArgumentNotValidException` | Spring, cuando falla `@Valid` | 400 + `campos` |
| `HttpMessageNotReadableException` | Spring, JSON mal escrito | 400 |
| `MethodArgumentTypeMismatchException` | Spring, ej: `/productos/abc` | 400 |
| `NoResourceFoundException` | Spring, ruta que no existe | 404 |
| `HttpRequestMethodNotSupportedException` | Spring, ej: PATCH donde no existe | 405 |
| `DataIntegrityViolationException` | MySQL rechaza por UNIQUE o clave foránea (respaldo) | 409 |
| `Exception` (cualquier otra) | Error inesperado: se registra en consola y al cliente se le envía un mensaje genérico | 500 |

Las excepciones propias extienden `RuntimeException`, así no hay que declarar `throws` en cada método.

---

## 8. Swagger / OpenAPI

- Dependencia `springdoc-openapi-starter-webmvc-ui` (versión 3.2.0 en `pom.xml`, la línea 3.x es la de Spring Boot 4).
- `OpenApiConfig` define el título, la versión y la descripción.
- En los controllers: `@Tag` agrupa los endpoints (Categorías, Productos) y `@Operation` describe cada uno.
  `@Parameter` describe los filtros `categoriaId` y `buscar`.
- Swagger UI: `http://localhost:8080/swagger-ui/index.html` (se puede probar cada endpoint con "Try it out").
- JSON OpenAPI: `http://localhost:8080/v3/api-docs`.

---

## 9. Pruebas unitarias (`src/test/.../service/`)

`CategoriaServiceTest` (11 pruebas), `ProductoServiceTest` (14 pruebas) y `BoletaServiceTest` (12 pruebas)
usan **JUnit 5 + Mockito**:

- `@ExtendWith(MockitoExtension.class)`: activa Mockito en la clase de prueba.
- `@Mock`: crea un repositorio falso; **no se usa MySQL**.
- `@InjectMocks`: crea el service real y le pasa los mocks por el constructor.
- `when(repo.metodo(...)).thenReturn(...)`: decide qué devuelve el mock.
- `assertEquals`, `assertThrows`: comprueban el resultado o que se lance la excepción esperada.
- `verify(repo).save(...)` / `verify(repo, never()).save(...)`: comprueban si se llamó (o no) a un método.

Patrón de cada prueba: **preparar** (`when`) → **ejecutar** (llamar al service) → **comprobar** (`assert`/`verify`).

Se ejecutan con `./mvnw test`. `LevelupBackendApplicationTests` levanta la aplicación completa,
por eso sí necesita MySQL encendido.

---

## 10. Boletas (órdenes de compra)

Reemplazan el checkout simulado del frontend. Archivos:
`BoletaController` → `BoletaService` → `BoletaRepository`, `ProductoRepository`, `UsuarioRepository` → MySQL.

### 10.1 Entrada: `BoletaRequest` y `DetalleRequest`
- Datos del cliente: `nombre`, `apellidos`, `correo` (`@Email`), `calle`, `region` y `comuna` son obligatorios
  (`@NotBlank`); `depto` e `indicaciones` son opcionales.
- `usuarioId` es opcional (`null` = visitante). Por ahora viene en el JSON; con JWT se tomará del token.
- `items`: `@NotEmpty` (al menos 1) y `List<@Valid DetalleRequest>`, así se valida cada item
  (`productoId` obligatorio, `cantidad` `@Min(1)`). Los errores aparecen como `"items[0].cantidad"`.
- **El precio no viene del frontend**: se toma de la base de datos, así nadie puede comprar más barato
  modificando el JSON.

### 10.2 Qué hace `BoletaService.crear` (paso a paso)
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
| Método | Ruta | OK | Errores |
|---|---|---|---|
| POST | `/api/v1/boletas` | 201 (PAGADA o RECHAZADA) | 400, 404, 409 |
| GET | `/api/v1/boletas` | 200, de la más reciente a la más antigua | – |
| GET | `/api/v1/boletas/{id}` | 200 | 404 |
| GET | `/api/v1/boletas/numero/{numeroOrden}` | 200 | 404 |
| GET | `/api/v1/boletas/usuario/{usuarioId}` | 200 (lista vacía si no ha comprado) | 404 si el usuario no existe |

### 10.6 JSON de ejemplo para probar `POST /api/v1/boletas` en Swagger
En Swagger UI → **Boletas** → `POST /api/v1/boletas` → **Try it out** → pegar:

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
- La contraseña nunca aparece: `Usuario.password` tiene `@JsonProperty(access = WRITE_ONLY)`.
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

## 11. Ejemplos de modificaciones que podría pedir el profesor

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

---

## 12. Comprobación del arranque

Al ejecutar `./mvnw spring-boot:run` (con `show-sql=true`) se ve en la consola:
- Los `create table` de `categoria`, `producto`, `usuario`, `boleta` y `detalle_boleta`.
- Los `insert` de las 6 categorías y los 15 productos (solo la primera vez).

Para verificar en MySQL:
```sql
USE levelup_db;
SHOW TABLES;
SELECT COUNT(*) FROM categoria;  -- 6
SELECT COUNT(*) FROM producto;   -- 15
```
