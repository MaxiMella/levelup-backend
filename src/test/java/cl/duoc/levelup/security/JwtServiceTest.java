package cl.duoc.levelup.security;

import cl.duoc.levelup.model.Rol;
import cl.duoc.levelup.model.Usuario;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de JwtService. No necesita Spring ni Mockito: se crea con "new"
 * pasando una clave y una duración, igual que lo haría Spring con los valores de application.properties.
 */
class JwtServiceTest {

    private static final String CLAVE = "clave-de-prueba-para-los-tests-de-jwt-123456";
    private static final long SESENTA_MINUTOS = 60 * 60 * 1000L; // igual que jwt.expiracion=3600000

    private final JwtService jwtService = new JwtService(CLAVE, SESENTA_MINUTOS);

    private Usuario usuario(String correo, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setCorreo(correo);
        usuario.setRol(rol);
        return usuario;
    }

    @Test
    void generarToken_guardaCorreoComoSubjectYRolComoClaim() {
        String token = jwtService.generarToken(usuario("pedro@duocuc.cl", Rol.CLIENTE));

        assertTrue(jwtService.esTokenValido(token));
        assertEquals("pedro@duocuc.cl", jwtService.obtenerCorreo(token));
        assertEquals("CLIENTE", jwtService.obtenerRol(token));
        assertEquals(3, token.split("\\.").length); // header.payload.firma
    }

    @Test
    void obtenerVencimiento_esLaHoraActualMasSesentaMinutos() {
        long antes = System.currentTimeMillis();
        String token = jwtService.generarToken(usuario("pedro@duocuc.cl", Rol.CLIENTE));
        long despues = System.currentTimeMillis();

        long vence = jwtService.obtenerVencimiento(token).getTime();

        // El "exp" del token se guarda en segundos (se pierden los milisegundos), por eso se compara con 1 s de margen.
        assertTrue(vence >= antes + SESENTA_MINUTOS - 1000);
        assertTrue(vence <= despues + SESENTA_MINUTOS);
    }

    @Test
    void esTokenValido_payloadModificadoParaSerAdmin_esFalso() {
        String token = jwtService.generarToken(usuario("pedro@duocuc.cl", Rol.CLIENTE));
        String[] partes = token.split("\\.");
        // Un atacante cambia el payload para decir que es ADMIN, pero no puede recalcular la firma sin la clave.
        String payloadFalso = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"pedro@duocuc.cl\",\"rol\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String alterado = partes[0] + "." + payloadFalso + "." + partes[2];

        assertFalse(jwtService.esTokenValido(alterado));
    }

    @Test
    void esTokenValido_firmadoConOtraClave_esFalso() {
        JwtService otroServidor = new JwtService("otra-clave-distinta-de-al-menos-32-caracteres", SESENTA_MINUTOS);
        String tokenAjeno = otroServidor.generarToken(usuario("admin@levelup.cl", Rol.ADMIN));

        assertFalse(jwtService.esTokenValido(tokenAjeno));
    }

    @Test
    void esTokenValido_tokenVencido_esFalso() {
        // Duración negativa: el token nace ya vencido.
        JwtService conTokensVencidos = new JwtService(CLAVE, -1000);
        String vencido = conTokensVencidos.generarToken(usuario("pedro@duocuc.cl", Rol.CLIENTE));

        assertFalse(jwtService.esTokenValido(vencido));
    }

    @Test
    void esTokenValido_textoQueNoEsToken_esFalso() {
        assertFalse(jwtService.esTokenValido("esto-no-es-un-token"));
        assertFalse(jwtService.esTokenValido(""));
    }

    @Test
    void constructor_claveCorta_lanzaError() {
        // Menos de 32 caracteres: la aplicación no debe arrancar con una clave insegura.
        assertThrows(IllegalStateException.class, () -> new JwtService("corta", SESENTA_MINUTOS));
        assertThrows(IllegalStateException.class, () -> new JwtService("", SESENTA_MINUTOS));
    }
}
