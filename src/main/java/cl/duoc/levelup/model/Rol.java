package cl.duoc.levelup.model;

/**
 * Roles del sistema según la pauta de la Evaluación 3:
 * ADMIN    -> acceso total.
 * VENDEDOR -> solo ve productos y órdenes (con su detalle).
 * CLIENTE  -> solo accede a la tienda.
 */
public enum Rol {
    ADMIN,
    VENDEDOR,
    CLIENTE
}
