package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.BoletaRequest;
import cl.duoc.levelup.dto.DetalleRequest;
import cl.duoc.levelup.exception.AccesoDenegadoException;
import cl.duoc.levelup.exception.ConflictoException;
import cl.duoc.levelup.exception.DatosInvalidosException;
import cl.duoc.levelup.exception.RecursoNoEncontradoException;
import cl.duoc.levelup.model.Boleta;
import cl.duoc.levelup.model.DetalleBoleta;
import cl.duoc.levelup.model.EstadoBoleta;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import cl.duoc.levelup.repository.BoletaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import cl.duoc.levelup.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lógica de negocio de las boletas (órdenes de compra). Reemplaza el checkout simulado del frontend.
 * Al crear una boleta: valida productos y stock, calcula precios y montos con IVA incluido,
 * simula el pago, descuenta stock (solo si quedó pagada) y genera el número de orden.
 */
@Service
public class BoletaService {

    /** Los precios incluyen IVA (19%): neto = total / 1.19. */
    private static final double FACTOR_IVA = 1.19;

    /** Descuento de por vida para usuarios con correo Duoc. */
    private static final double PORCENTAJE_DESCUENTO_DUOC = 0.20;

    /** Primera parte del número de orden, ej: 20261106. */
    private static final DateTimeFormatter FORMATO_FECHA_ORDEN = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BoletaRepository boletaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public BoletaService(BoletaRepository boletaRepository, ProductoRepository productoRepository,
                         UsuarioRepository usuarioRepository) {
        this.boletaRepository = boletaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Crea una boleta a partir del carrito.
     * @Transactional: todo se guarda junto o no se guarda nada. Si falla algo a mitad de camino
     * (ej: falta stock del tercer producto), se deshacen los cambios ya hechos en la base.
     */
    @Transactional
    public Boleta crear(BoletaRequest request) {
        // 1. Usuario opcional: si viene el id, debe existir.
        Usuario usuario = null;
        if (request.getUsuarioId() != null) {
            usuario = usuarioRepository.findById(request.getUsuarioId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe el usuario con id " + request.getUsuarioId()));
        }

        Boleta boleta = new Boleta();
        boleta.setUsuario(usuario);
        copiarDatosCliente(request, boleta);

        // 2. Detalles: un DetalleBoleta por cada item del carrito.
        Set<Long> productosAgregados = new HashSet<>();
        int subtotal = 0;
        for (DetalleRequest item : request.getItems()) {
            // Si el mismo producto viniera en dos líneas, cada una podría pasar la revisión de stock
            // por separado y aun así superar el stock en total. Por eso se rechaza.
            if (!productosAgregados.add(item.getProductoId())) {
                throw new DatosInvalidosException("El producto con id " + item.getProductoId()
                        + " está repetido en la orden; envíalo una sola vez con la cantidad total");
            }

            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe el producto con id " + item.getProductoId()));

            if (producto.getStock() < item.getCantidad()) {
                throw new ConflictoException("Stock insuficiente para '" + producto.getNombre()
                        + "': disponible " + producto.getStock() + ", solicitado " + item.getCantidad());
            }

            int precioUnitario = calcularPrecioUnitario(producto);
            DetalleBoleta detalle = new DetalleBoleta();
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setSubtotal(precioUnitario * item.getCantidad());
            boleta.agregarDetalle(detalle); // deja la relación boleta <-> detalle en ambos sentidos

            subtotal += detalle.getSubtotal();
        }

        // 3. Montos. Los precios ya incluyen IVA, así que el IVA se "extrae" del total.
        int descuento = calcularDescuento(subtotal, usuario);
        int total = subtotal - descuento;
        int neto = (int) Math.round(total / FACTOR_IVA);
        int iva = total - neto;
        boleta.setSubtotal(subtotal);
        boleta.setDescuento(descuento);
        boleta.setTotal(total);
        boleta.setNeto(neto);
        boleta.setIva(iva);

        // 4. Pago simulado: solo si queda pagada se descuenta el stock.
        EstadoBoleta estado = simularPago(request.getCorreo());
        boleta.setEstado(estado);
        if (estado == EstadoBoleta.PAGADA) {
            descontarStock(boleta);
        }

        // 5. Fecha y número de orden del día.
        LocalDateTime ahora = LocalDateTime.now();
        boleta.setFecha(ahora);
        boleta.setNumeroOrden(generarNumeroOrden(ahora.toLocalDate()));

        // 6. Se guarda la boleta y, por cascade = ALL, también sus detalles.
        return boletaRepository.save(boleta);
    }

    /**
     * Crea la boleta según quién está comprando (lo usa BoletaController).
     * correoSesion es el correo del token, o null si compra un visitante.
     * - ADMIN: se respeta el usuarioId del JSON (acceso total, ej: registrar una venta para un cliente).
     * - CLIENTE: el usuario es el del token y se ignora el usuarioId del JSON.
     * - Visitante o VENDEDOR: usuarioId = null (compra sin cuenta).
     * Así nadie puede comprar a nombre de otro ni usar su descuento Duoc enviando otro usuarioId.
     */
    @Transactional
    public Boleta crear(BoletaRequest request, String correoSesion) {
        Usuario sesion = null;
        if (correoSesion != null) {
            sesion = buscarUsuarioPorCorreo(correoSesion);
        }

        boolean esAdmin = sesion != null && sesion.getRol() == Rol.ADMIN;
        boolean esCliente = sesion != null && sesion.getRol() == Rol.CLIENTE;

        if (esCliente) {
            request.setUsuarioId(sesion.getId());
        } else if (!esAdmin) {
            request.setUsuarioId(null);
        }
        // Si es ADMIN, el usuarioId queda tal como vino en el JSON.
        return crear(request);
    }

    /** Todas las boletas, de la más reciente a la más antigua (panel admin y vendedor). */
    public List<Boleta> listarTodas() {
        return boletaRepository.findAllByOrderByFechaDesc();
    }

    public Boleta obtenerPorId(Long id) {
        return boletaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la boleta con id " + id));
    }

    /** Búsqueda por el número que ve el cliente, ej: 20261106-001. */
    public Boleta obtenerPorNumeroOrden(String numeroOrden) {
        return boletaRepository.findByNumeroOrden(numeroOrden)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la boleta con número de orden " + numeroOrden));
    }

    /** Historial de compras de un usuario; 404 si el usuario no existe, lista vacía si no ha comprado. */
    public List<Boleta> historialUsuario(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new RecursoNoEncontradoException("No existe el usuario con id " + usuarioId);
        }
        return boletaRepository.findByUsuarioIdOrderByFechaDesc(usuarioId);
    }

    /**
     * Historial revisando quién lo pide: el ADMIN ve el de cualquiera y el cliente solo el suyo (si no, 403).
     * SecurityConfig ya dejó pasar solo a ADMIN y CLIENTE; aquí se revisa que sea el dueño,
     * porque eso depende del id pedido y no solo del rol.
     */
    public List<Boleta> historialUsuario(Long usuarioId, String correoSesion) {
        Usuario sesion = buscarUsuarioPorCorreo(correoSesion);
        if (sesion.getRol() != Rol.ADMIN && !sesion.getId().equals(usuarioId)) {
            throw new AccesoDenegadoException("Solo puedes ver tu propio historial de compras");
        }
        return historialUsuario(usuarioId);
    }

    /** Compras del usuario que inició sesión (GET /api/v1/boletas/mis-compras). */
    public List<Boleta> misCompras(String correoSesion) {
        Usuario sesion = buscarUsuarioPorCorreo(correoSesion);
        return boletaRepository.findByUsuarioIdOrderByFechaDesc(sesion.getId());
    }

    /** Usuario del token. Debería existir siempre (el filtro JWT ya lo buscó), pero si no, 404. */
    private Usuario buscarUsuarioPorCorreo(String correo) {
        return usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con correo " + correo));
    }

    /** Precio de oferta si el producto está en oferta (misma regla que findEnOferta); si no, el normal. */
    private int calcularPrecioUnitario(Producto producto) {
        Integer oferta = producto.getPrecioOferta();
        if (oferta != null && oferta < producto.getPrecio()) {
            return oferta;
        }
        return producto.getPrecio();
    }

    /** 20% del subtotal (redondeado) si el usuario tiene descuento Duoc; 0 para visitantes y el resto. */
    private int calcularDescuento(int subtotal, Usuario usuario) {
        if (usuario != null && usuario.isDescuentoDuoc()) {
            return (int) Math.round(subtotal * PORCENTAJE_DESCUENTO_DUOC);
        }
        return 0;
    }

    /** Igual que el frontend: si el correo contiene "error" el pago se rechaza. */
    private EstadoBoleta simularPago(String correo) {
        if (correo.toLowerCase().contains("error")) {
            return EstadoBoleta.RECHAZADA;
        }
        return EstadoBoleta.PAGADA;
    }

    /** Resta las unidades vendidas al stock de cada producto. */
    private void descontarStock(Boleta boleta) {
        for (DetalleBoleta detalle : boleta.getDetalles()) {
            Producto producto = detalle.getProducto();
            producto.setStock(producto.getStock() - detalle.getCantidad());
            productoRepository.save(producto);
        }
    }

    /**
     * Número de orden: fecha + correlativo del día con 3 dígitos, ej: 20261106-001.
     * Se cuentan las boletas que ya empiezan con el prefijo de hoy y se suma 1.
     */
    private String generarNumeroOrden(LocalDate fecha) {
        String prefijo = fecha.format(FORMATO_FECHA_ORDEN) + "-";
        long boletasDelDia = boletaRepository.countByNumeroOrdenStartingWith(prefijo);
        return prefijo + String.format("%03d", boletasDelDia + 1);
    }

    /** Pasa los datos del cliente y la dirección del DTO a la boleta. */
    private void copiarDatosCliente(BoletaRequest request, Boleta boleta) {
        boleta.setNombreCliente(request.getNombre().trim());
        boleta.setApellidosCliente(request.getApellidos().trim());
        boleta.setCorreoCliente(request.getCorreo().trim());
        boleta.setCalle(request.getCalle().trim());
        boleta.setDepto(request.getDepto());
        boleta.setRegion(request.getRegion().trim());
        boleta.setComuna(request.getComuna().trim());
        boleta.setIndicaciones(request.getIndicaciones());
    }
}
