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

Flujo general de una petición (cuando existan controllers y services):

```
Frontend React → Controller (/api/v1/...) → Service (lógica y validaciones)
              → Repository (JpaRepository) → Hibernate → MySQL
```

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

---

## 4. Ejemplos de modificaciones que podría pedir el profesor

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

---

## 5. Comprobación del arranque

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
