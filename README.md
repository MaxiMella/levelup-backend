# Level-Up Gamer – Backend (API REST)

API REST de **Level-Up Gamer**, una tienda online de productos gamer en Chile. Proyecto académico de
**DSY1104 Desarrollo Fullstack II** (Duoc UC), Evaluación Parcial 3. Todos los datos son ficticios.

La API administra el catálogo (categorías y productos), los usuarios (registro, programa de referidos y
descuento Duoc), las compras (boletas con IVA, descuento y control de stock) y los reportes del panel de
administración. Está protegida con **Spring Security + JWT** y tiene tres roles:

| Rol | Qué puede hacer |
|---|---|
| `ADMIN` | Acceso total: mantenedores de productos, categorías y usuarios, órdenes y reportes. |
| `VENDEDOR` | Ver productos (incluido el stock crítico) y las órdenes con su detalle. |
| `CLIENTE` | Usar la tienda: comprar, ver su historial de compras y editar su perfil. |

**Frontend (React + Vite):** https://github.com/AmaroLC8/proyect-levelup2
(se ejecuta en `http://localhost:5173`, el único origen permitido por CORS).

---

## Tecnologías y versiones

Versiones tomadas de `pom.xml` (las que no aparecen ahí las administra `spring-boot-starter-parent`).

| Tecnología | Versión | Para qué se usa |
|---|---|---|
| Java | 21 | Lenguaje |
| Spring Boot | 4.1.2 | Framework base (Spring Framework 7.0.10, Tomcat 11) |
| Maven (wrapper `./mvnw`) | 3.10.0 | Compilar, ejecutar y probar |
| Spring Web MVC (`spring-boot-starter-webmvc`) | 4.1.2 | Controllers REST |
| Spring Data JPA + Hibernate | 4.1.2 / 7.4.13 | Acceso a datos y creación de tablas |
| MySQL Connector/J | 9.7.0 | Conexión a MySQL |
| Bean Validation (`spring-boot-starter-validation`) | 4.1.2 | `@Valid`, `@NotBlank`, `@Pattern`... |
| Spring Security | 7.1.2 | Permisos por rol y contraseñas con BCrypt |
| JJWT (`jjwt-api`, `jjwt-impl`, `jjwt-gson`) | 0.13.0 | Generar y validar los tokens JWT |
| springdoc-openapi (Swagger UI) | 3.2.0 | Documentación interactiva de la API |
| Lombok | 1.18.48 | Getters, setters y constructores |
| Spring Boot DevTools | 4.1.2 | Reinicio automático al desarrollar |
| JUnit 5 (Jupiter) + Mockito | 6.0.3 / 5.23.0 | Pruebas unitarias |

---

## Requisitos

- **Java 21** (JDK). Comprobar con `java -version`.
- **MySQL 8 o superior** corriendo en `localhost:3306` con el usuario `root`.
  La base `levelup_db` **se crea sola** la primera vez (`createDatabaseIfNotExist=true`).
- No hace falta instalar Maven: el proyecto trae el wrapper `./mvnw` (o `mvnw.cmd` en Windows).
- (Opcional) IntelliJ IDEA.

---

## Instalación paso a paso

### 1. Clonar el repositorio

```bash
git clone https://github.com/MaxiMella/levelup-backend.git
cd levelup-backend
```

### 2. Crear `local.properties`

Las contraseñas **no se suben a GitHub**: cada integrante las escribe en `local.properties`, que está en
`.gitignore`. `application.properties` lo importa con `spring.config.import=optional:file:./local.properties`.

Copiar el ejemplo en la raíz del proyecto:

```bash
# Linux / macOS / Git Bash
cp local.properties.example local.properties
```

```powershell
# Windows PowerShell
Copy-Item local.properties.example local.properties
```

Editar `local.properties` con los valores propios:

```properties
# Contraseña de tu MySQL local (usuario root)
DB_PASSWORD=tu_clave_de_mysql
# Clave para firmar los tokens JWT: cualquier texto de al menos 32 caracteres
JWT_SECRET=cambia_esta_clave_por_una_propia_de_al_menos_32_caracteres
```

- Si falta `JWT_SECRET` o tiene menos de 32 caracteres, la aplicación **no arranca** y `JwtService` explica qué hacer.
- También se pueden definir `DB_PASSWORD` y `JWT_SECRET` como variables de entorno en lugar del archivo.

### 3. Ejecutar

**Con Maven (terminal):**

```bash
./mvnw spring-boot:run        # Linux / macOS / Git Bash
.\mvnw.cmd spring-boot:run    # Windows (PowerShell o CMD)
```

**Desde IntelliJ IDEA:**

1. *File → Open* y elegir la carpeta del proyecto (IntelliJ lo detecta como proyecto Maven).
2. Comprobar en *File → Project Structure → Project* que el SDK sea **Java 21**.
3. Abrir `src/main/java/cl/duoc/levelup/LevelupBackendApplication.java` y presionar ▶ *Run*.
   La carpeta de trabajo debe ser la raíz del proyecto (la opción por defecto), para que encuentre `local.properties`.

La API queda en **http://localhost:8080**. Al arrancar por primera vez, Hibernate crea las tablas
(`ddl-auto=update`) y `DataInitializer` carga **6 categorías, 15 productos y 3 usuarios de prueba**
(solo si las tablas están vacías, así no se duplican datos).

---

## Documentación con Swagger

- **Swagger UI:** http://localhost:8080/swagger-ui/index.html
- JSON OpenAPI: http://localhost:8080/v3/api-docs

Cada endpoint tiene su descripción en español y el rol que puede usarlo ("Acceso: ...").
Para probar los endpoints protegidos:

1. `POST /api/v1/auth/login` → *Try it out* → `{"correo":"admin@levelup.cl","password":"admin1234"}`.
2. Copiar el valor de `token` de la respuesta (sin comillas).
3. Botón **Authorize** (arriba a la derecha) → pegar el token **sin escribir "Bearer"** → *Authorize*.

---

## Modelo de datos

Cinco tablas en MySQL, generadas desde las entidades JPA del paquete `model`:

```
 CATEGORIA 1 ──────< N PRODUCTO 1 ──────< N DETALLE_BOLETA N >────── 1 BOLETA N >- - - - 0..1 USUARIO
```

`──<` uno a muchos obligatorio · `- - -` relación opcional (una boleta puede ser de un visitante sin cuenta).

| Tabla | Campos principales | Relaciones |
|---|---|---|
| `categoria` | `id`, `nombre` (único), `descripcion`, `activa` | Una categoría tiene muchos productos. |
| `producto` | `id`, `nombre`, `descripcion`, `precio`, `precio_oferta`, `stock`, `stock_critico`, `imagen` | `categoria_id` → `categoria` (obligatoria). |
| `usuario` | `id`, `nombre`, `apellidos`, `correo` (único), `password` (BCrypt), `fecha_nacimiento`, `rol`, `descuento_duoc`, `puntos`, `codigo_referido`, `referido_por` | Un usuario puede tener muchas boletas. |
| `boleta` | `id`, `numero_orden` (único), `fecha`, `estado` (`PAGADA`/`RECHAZADA`), datos del cliente y de envío, `subtotal`, `descuento`, `neto`, `iva`, `total` | `usuario_id` → `usuario` (puede ser `NULL`). |
| `detalle_boleta` | `id`, `cantidad`, `precio_unitario`, `subtotal` | `boleta_id` → `boleta` y `producto_id` → `producto`. |

- `detalle_boleta` resuelve la relación muchos a muchos entre boletas y productos, y guarda el precio
  **al momento de la compra**.
- Los precios están en pesos chilenos (sin decimales) e **incluyen IVA**:
  `neto = round(total / 1.19)` e `iva = total - neto`. Ejemplo: total 119.000 → neto 100.000 → IVA 19.000.
- Los usuarios con correo `@duoc.cl` o `@duocuc.cl` tienen 20% de descuento en sus compras.

---

## Endpoints

Todas las rutas están bajo `/api/v1`. **Público** = sin iniciar sesión. **Con sesión** = cualquier rol con token.
Sin token, una ruta protegida responde **401**; con un rol sin permiso, **403**.

### Autenticación y perfil (`/api/v1/auth`)

| Método | Ruta | Quién puede usarlo |
|---|---|---|
| POST | `/api/v1/auth/registro` | Público (siempre crea un CLIENTE) |
| POST | `/api/v1/auth/login` | Público (devuelve el token JWT) |
| GET | `/api/v1/auth/me` | Con sesión |
| GET | `/api/v1/auth/perfil` | Con sesión |
| PUT | `/api/v1/auth/perfil` | Con sesión (solo su propio perfil) |

### Categorías (`/api/v1/categorias`)

| Método | Ruta | Quién puede usarlo |
|---|---|---|
| GET | `/api/v1/categorias` | Público |
| GET | `/api/v1/categorias/activas` | Público |
| GET | `/api/v1/categorias/{id}` | Público |
| POST | `/api/v1/categorias` | ADMIN |
| PUT | `/api/v1/categorias/{id}` | ADMIN |
| DELETE | `/api/v1/categorias/{id}` | ADMIN |

### Productos (`/api/v1/productos`)

| Método | Ruta | Quién puede usarlo |
|---|---|---|
| GET | `/api/v1/productos?categoriaId=&buscar=` | Público |
| GET | `/api/v1/productos/ofertas` | Público |
| GET | `/api/v1/productos/{id}` | Público |
| GET | `/api/v1/productos/criticos` | ADMIN, VENDEDOR |
| POST | `/api/v1/productos` | ADMIN |
| PUT | `/api/v1/productos/{id}` | ADMIN |
| DELETE | `/api/v1/productos/{id}` | ADMIN |

### Boletas / órdenes de compra (`/api/v1/boletas`)

| Método | Ruta | Quién puede usarlo |
|---|---|---|
| POST | `/api/v1/boletas` | Público (visitante); con token de CLIENTE queda a su nombre |
| GET | `/api/v1/boletas` | ADMIN, VENDEDOR |
| GET | `/api/v1/boletas/{id}` | ADMIN, VENDEDOR |
| GET | `/api/v1/boletas/numero/{numeroOrden}` | ADMIN, VENDEDOR |
| GET | `/api/v1/boletas/mis-compras` | CLIENTE, ADMIN |
| GET | `/api/v1/boletas/usuario/{usuarioId}` | ADMIN, o el CLIENTE dueño del historial |

### Usuarios – mantenedor (`/api/v1/usuarios`)

| Método | Ruta | Quién puede usarlo |
|---|---|---|
| GET | `/api/v1/usuarios?rol=` | ADMIN |
| GET | `/api/v1/usuarios/{id}` | ADMIN |
| POST | `/api/v1/usuarios` | ADMIN |
| PUT | `/api/v1/usuarios/{id}` | ADMIN |
| DELETE | `/api/v1/usuarios/{id}` | ADMIN |

### Reportes del panel (`/api/v1/reportes`)

| Método | Ruta | Quién puede usarlo |
|---|---|---|
| GET | `/api/v1/reportes/dashboard` | ADMIN |
| GET | `/api/v1/reportes/ventas` | ADMIN |

Los errores siempre responden un JSON con el mismo formato, por ejemplo:

```json
{ "status": 409, "error": "Conflict", "mensaje": "Ya existe una categoría con el nombre 'Consolas'", "fecha": "2026-11-06T15:23:25.72" }
```

---

## Usuarios de prueba

Los crea `DataInitializer` al arrancar (solo si la tabla `usuario` está vacía). Contraseñas guardadas con BCrypt.

| Correo | Contraseña | Rol | Detalle |
|---|---|---|---|
| `admin@levelup.cl` | `admin1234` | ADMIN | Acceso total |
| `vendedor@levelup.cl` | `vendedor1234` | VENDEDOR | Productos y órdenes |
| `pedro@duocuc.cl` | `pedro1234` | CLIENTE | Descuento Duoc 20%, 100 puntos, código de referido `LVLPEDRO` |

Son datos ficticios: no reutilizar estas contraseñas en ningún sistema real.

---

## Pruebas

```bash
./mvnw test          # Linux / macOS / Git Bash
.\mvnw.cmd test      # Windows
```

107 pruebas con **JUnit 5 + Mockito** (y MockMvc para la seguridad):

| Clase | Pruebas | Qué revisa |
|---|---|---|
| `CategoriaServiceTest` | 11 | CRUD, nombre repetido, categoría con productos |
| `ProductoServiceTest` | 14 | Filtros, oferta menor al precio, producto vendido |
| `BoletaServiceTest` | 19 | IVA, descuento Duoc, stock, pago simulado, quién compra |
| `UsuarioServiceTest` | 18 | Registro, mayoría de edad, referidos, BCrypt, perfil |
| `AuthServiceTest` | 3 | Login correcto e incorrecto |
| `ReporteServiceTest` | 8 | Dashboard y métricas de ventas |
| `JwtServiceTest` | 6 | Generación y validación del token |
| `SeguridadTest` | 27 | Qué rol puede entrar a cada ruta (401 / 403) |
| `LevelupBackendApplicationTests` | 1 | Que la aplicación completa arranque |

Las pruebas de services, JWT y seguridad **no usan MySQL** (repositorios simulados con Mockito).
`LevelupBackendApplicationTests` levanta la aplicación completa, así que necesita **MySQL encendido** y
`local.properties` con `DB_PASSWORD` y `JWT_SECRET`.

---

## Estructura del proyecto

```
src/main/java/cl/duoc/levelup/
├── model/        Entidades JPA y enums (Rol, EstadoBoleta)
├── repository/   Interfaces JpaRepository
├── service/      Lógica de negocio y validaciones
├── controller/   Endpoints REST (/api/v1/...)
├── dto/          Datos de entrada y salida (login, registro, boleta, reportes)
├── exception/    Excepciones propias y GlobalExceptionHandler
├── config/       CORS, Swagger, BCrypt y DataInitializer
└── security/     Spring Security, JWT y filtros
```

Flujo de una petición: `Frontend → Spring Security (JWT + rol) → Controller → Service → Repository → MySQL`.

La explicación detallada de cada parte está en [`docs/ESTUDIO.md`](docs/ESTUDIO.md).

---

## Integrantes

- Lukas Meza
- Maximiliano Mella
- Amaro López
