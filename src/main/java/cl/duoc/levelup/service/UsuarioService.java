package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.RegistroRequest;
import cl.duoc.levelup.dto.UsuarioRequest;
import cl.duoc.levelup.dto.UsuarioResponse;
import cl.duoc.levelup.exception.ConflictoException;
import cl.duoc.levelup.exception.DatosInvalidosException;
import cl.duoc.levelup.exception.RecursoNoEncontradoException;
import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import cl.duoc.levelup.repository.BoletaRepository;
import cl.duoc.levelup.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lógica de negocio de los usuarios: registro de clientes (con referidos) y mantenedor del administrador.
 * Replica las reglas del frontend (src/data/usuarios.js y src/utils/validaciones.js):
 * correo único, mayor de 18 años, descuento Duoc, código de referido y 100 puntos por referido.
 * Las contraseñas se guardan cifradas con BCrypt y nunca se devuelven (se responde UsuarioResponse).
 */
@Service
public class UsuarioService {

    /** Puntos LevelUp que ganan ambos usuarios por un referido exitoso (igual que PUNTOS_POR_REFERIDO del frontend). */
    public static final int PUNTOS_POR_REFERIDO = 100;

    private static final int EDAD_MINIMA = 18;

    /** Intentos con 2 dígitos antes de pasar a 4 dígitos al generar un código de referido único. */
    private static final int INTENTOS_CODIGO_REFERIDO = 20;

    private final UsuarioRepository usuarioRepository;
    private final BoletaRepository boletaRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, BoletaRepository boletaRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.boletaRepository = boletaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ===================== Registro (tienda) =====================

    /**
     * Registra un cliente nuevo. Siempre queda con rol CLIENTE.
     * @Transactional: si trae código de referido se guardan dos usuarios (el nuevo y quien lo refirió);
     * se guardan ambos o ninguno.
     */
    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        String correo = normalizarCorreo(request.getCorreo());
        validarCorreoDisponible(correo, null);
        validarMayorDeEdad(request.getFechaNacimiento());

        // El código de referido se revisa ANTES de guardar, para no crear el usuario si es inválido.
        Usuario referente = null;
        if (request.getCodigoReferido() != null && !request.getCodigoReferido().isBlank()) {
            String codigo = request.getCodigoReferido().trim().toUpperCase();
            referente = usuarioRepository.findByCodigoReferido(codigo)
                    .orElseThrow(() -> new DatosInvalidosException("El código de referido '" + codigo + "' no existe"));
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre().trim());
        usuario.setApellidos(request.getApellidos().trim());
        usuario.setCorreo(correo);
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setFechaNacimiento(request.getFechaNacimiento());
        usuario.setRol(Rol.CLIENTE);
        usuario.setDescuentoDuoc(esCorreoDuoc(correo));
        usuario.setPuntos(0);
        usuario.setCodigoReferido(generarCodigoReferido(usuario.getNombre()));

        // Referido válido: ambos ganan puntos y se guarda quién lo invitó.
        if (referente != null) {
            usuario.setReferidoPor(referente.getCodigoReferido());
            usuario.setPuntos(PUNTOS_POR_REFERIDO);
            referente.setPuntos(referente.getPuntos() + PUNTOS_POR_REFERIDO);
            usuarioRepository.save(referente);
        }

        Usuario guardado = usuarioRepository.save(usuario);
        return UsuarioResponse.desde(guardado);
    }

    // ===================== Mantenedor (administrador) =====================

    /** Todos los usuarios o solo los de un rol, si se indica. */
    public List<UsuarioResponse> listar(Rol rol) {
        List<Usuario> usuarios;
        if (rol == null) {
            usuarios = usuarioRepository.findAll();
        } else {
            usuarios = usuarioRepository.findByRol(rol);
        }

        List<UsuarioResponse> respuesta = new ArrayList<>();
        for (Usuario usuario : usuarios) {
            respuesta.add(UsuarioResponse.desde(usuario));
        }
        return respuesta;
    }

    public UsuarioResponse obtenerPorId(Long id) {
        return UsuarioResponse.desde(buscarPorId(id));
    }

    /** El administrador crea un usuario con cualquier rol. Aquí la contraseña sí es obligatoria. */
    public UsuarioResponse crear(UsuarioRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new DatosInvalidosException("La contraseña es obligatoria al crear un usuario");
        }
        String correo = normalizarCorreo(request.getCorreo());
        validarCorreoDisponible(correo, null);
        validarMayorDeEdad(request.getFechaNacimiento());

        Usuario usuario = new Usuario();
        copiarDatos(request, usuario);
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setPuntos(0);
        usuario.setCodigoReferido(generarCodigoReferido(usuario.getNombre()));

        return UsuarioResponse.desde(usuarioRepository.save(usuario));
    }

    /**
     * Edita un usuario. El descuento Duoc se recalcula según el correo (como el frontend)
     * y si la contraseña viene vacía se mantiene la actual. Puntos y código de referido no cambian.
     */
    public UsuarioResponse actualizar(Long id, UsuarioRequest request) {
        Usuario usuario = buscarPorId(id);
        String correo = normalizarCorreo(request.getCorreo());
        validarCorreoDisponible(correo, id);
        validarMayorDeEdad(request.getFechaNacimiento());

        copiarDatos(request, usuario);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return UsuarioResponse.desde(usuarioRepository.save(usuario));
    }

    /** No se puede eliminar un usuario con compras: sus boletas quedarían apuntando a un usuario inexistente. */
    public void eliminar(Long id) {
        Usuario usuario = buscarPorId(id);
        if (boletaRepository.existsByUsuarioId(id)) {
            throw new ConflictoException("No se puede eliminar al usuario '" + usuario.getCorreo()
                    + "' porque tiene boletas asociadas");
        }
        usuarioRepository.delete(usuario);
    }

    // ===================== Métodos de apoyo =====================

    private Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + id));
    }

    /** Los correos se guardan sin espacios y en minúsculas, así "Ana@Gmail.com" y "ana@gmail.com" son el mismo. */
    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase();
    }

    /**
     * El correo no puede pertenecer a otro usuario (409).
     * idActual es null al crear; al editar es el id del usuario, que sí puede conservar su propio correo.
     */
    private void validarCorreoDisponible(String correo, Long idActual) {
        Optional<Usuario> existente = usuarioRepository.findByCorreoIgnoreCase(correo);
        if (existente.isPresent() && !existente.get().getId().equals(idActual)) {
            throw new ConflictoException("Ya existe un usuario registrado con el correo " + correo);
        }
    }

    /** Edad cumplida a la fecha de hoy (Period considera si ya pasó el cumpleaños este año). */
    private void validarMayorDeEdad(LocalDate fechaNacimiento) {
        int edad = Period.between(fechaNacimiento, LocalDate.now()).getYears();
        if (edad < EDAD_MINIMA) {
            throw new DatosInvalidosException("Debes ser mayor de " + EDAD_MINIMA + " años para registrarte");
        }
    }

    /** Correos institucionales con 20% de descuento de por vida (misma regla que esCorreoDuoc del frontend). */
    private boolean esCorreoDuoc(String correo) {
        return correo.endsWith("@duoc.cl") || correo.endsWith("@duocuc.cl");
    }

    /** Pasa los datos del DTO del administrador a la entidad (crear y actualizar). */
    private void copiarDatos(UsuarioRequest request, Usuario usuario) {
        String correo = normalizarCorreo(request.getCorreo());
        usuario.setNombre(request.getNombre().trim());
        usuario.setApellidos(request.getApellidos().trim());
        usuario.setCorreo(correo);
        usuario.setFechaNacimiento(request.getFechaNacimiento());
        usuario.setRol(request.getRol());
        usuario.setDescuentoDuoc(esCorreoDuoc(correo));
    }

    /**
     * Misma lógica del frontend: "LVL" + hasta 5 letras del nombre (sin tildes ni espacios) en mayúsculas
     * + un número de 10 a 99. Ej: "María José" -> LVLMARAJ42 (las letras con tilde se descartan, igual que en JS).
     * A diferencia del frontend, se revisa que no exista; con 2 dígitos hay solo 90 opciones por nombre,
     * así que si choca 20 veces se usan 4 dígitos.
     */
    private String generarCodigoReferido(String nombre) {
        String letras = nombre.replaceAll("[^a-zA-Z]", "").toUpperCase();
        String prefijo = "LVL" + letras.substring(0, Math.min(5, letras.length()));

        for (int intento = 0; intento < INTENTOS_CODIGO_REFERIDO; intento++) {
            String codigo = prefijo + numeroAleatorio(10, 99);
            if (usuarioRepository.findByCodigoReferido(codigo).isEmpty()) {
                return codigo;
            }
        }

        String codigo;
        do {
            codigo = prefijo + numeroAleatorio(1000, 9999);
        } while (usuarioRepository.findByCodigoReferido(codigo).isPresent());
        return codigo;
    }

    /** Número entero al azar entre minimo y maximo (ambos incluidos). */
    private int numeroAleatorio(int minimo, int maximo) {
        return (int) (Math.random() * (maximo - minimo + 1)) + minimo;
    }
}
