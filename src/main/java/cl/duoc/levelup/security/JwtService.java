package cl.duoc.levelup.security;

import cl.duoc.levelup.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Crea y valida los tokens JWT (JSON Web Token) del inicio de sesión.
 *
 * Un token tiene tres partes separadas por puntos: header.payload.firma
 * - payload: los datos ("claims"). Aquí: sub = correo del usuario, rol, iat (creado) y exp (vence).
 * - firma: se calcula con la clave secreta (HMAC-SHA). Si alguien cambia el payload (ej: su rol a ADMIN),
 *   la firma deja de calzar y el token se rechaza. Sin la clave nadie puede fabricar un token válido.
 * El payload NO está cifrado (cualquiera lo puede leer en jwt.io): por eso nunca lleva la contraseña.
 *
 * La clave (jwt.secret) y la duración (jwt.expiracion) vienen de application.properties.
 */
@Service
public class JwtService {

    /** Largo mínimo de la clave: HMAC-SHA256 exige al menos 256 bits = 32 bytes. */
    private static final int LARGO_MINIMO_CLAVE = 32;

    private final SecretKey clave;
    private final long expiracionMs;

    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiracion}") long expiracionMs) {
        // Sin una clave segura la API no puede proteger nada: es mejor que no arranque y diga por qué.
        if (secreto == null || secreto.length() < LARGO_MINIMO_CLAVE) {
            throw new IllegalStateException("Falta JWT_SECRET o tiene menos de " + LARGO_MINIMO_CLAVE
                    + " caracteres. Defínelo en local.properties (ver local.properties.example).");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMs = expiracionMs;
    }

    /** Genera el token del usuario que acaba de iniciar sesión. */
    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date vence = new Date(ahora.getTime() + expiracionMs);
        return Jwts.builder()
                .subject(usuario.getCorreo())          // "sub": a quién pertenece el token
                .claim("rol", usuario.getRol().name()) // dato extra para que el frontend sepa qué menú mostrar
                .issuedAt(ahora)                       // "iat"
                .expiration(vence)                     // "exp"
                .signWith(clave)                       // firma HMAC-SHA con la clave secreta
                .compact();
    }

    /** true si la firma es correcta y no ha vencido; false en cualquier otro caso (sin lanzar excepción). */
    public boolean esTokenValido(String token) {
        try {
            leerClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException: firma incorrecta, token vencido, mal formado...
            // IllegalArgumentException: token vacío.
            return false;
        }
    }

    /** Correo del usuario (el "sub" del token). Lanza JwtException si el token no es válido. */
    public String obtenerCorreo(String token) {
        return leerClaims(token).getSubject();
    }

    /** Rol guardado en el token (ej: "CLIENTE"). Lanza JwtException si el token no es válido. */
    public String obtenerRol(String token) {
        return leerClaims(token).get("rol", String.class);
    }

    /** Verifica la firma y la fecha de vencimiento, y devuelve el payload. */
    private Claims leerClaims(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
