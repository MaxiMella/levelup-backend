package cl.duoc.levelup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cifrado de contraseñas con BCrypt.
 * - encode("pedro1234") genera un hash distinto cada vez (lleva una "sal" aleatoria), ej: $2a$10$...
 * - matches("pedro1234", hash) comprueba si la contraseña corresponde al hash (se usará en el login).
 * El hash no se puede revertir: ni siquiera el administrador puede ver la contraseña original.
 * Se declara como bean para inyectarlo por constructor en UsuarioService, DataInitializer y,
 * más adelante, en la configuración de Spring Security.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
