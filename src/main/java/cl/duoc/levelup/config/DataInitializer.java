package cl.duoc.levelup.config;

import cl.duoc.levelup.model.Categoria;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.repository.CategoriaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Carga los datos iniciales de la tienda (los mismos que tenía el frontend en src/data)
 * la primera vez que se levanta el backend. Si la tabla categoria ya tiene datos, no hace nada.
 * Los usuarios iniciales (admin, vendedor y cliente) se agregan en el paso de seguridad,
 * porque sus contraseñas deben guardarse cifradas.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    public DataInitializer(CategoriaRepository categoriaRepository, ProductoRepository productoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
    }

    @Override
    public void run(String... args) {
        if (categoriaRepository.count() > 0) {
            return;
        }

        Categoria consolas = categoria("Consolas", "Consolas de última generación y retro.");
        Categoria perifericos = categoria("Periféricos", "Teclados, mouse, audífonos y controles.");
        Categoria sillas = categoria("Sillas", "Sillas gamer ergonómicas para largas sesiones.");
        Categoria juegosMesa = categoria("Juegos de Mesa", "Juegos de estrategia y para compartir.");
        Categoria computadores = categoria("Computadores Gamer", "Torres, notebooks y monitores gaming.");
        Categoria accesorios = categoria("Accesorios", "Mousepads, soportes y accesorios para tu setup.");

        List<Producto> productos = new ArrayList<>();
        productos.add(producto("PlayStation 5 Slim 1TB", consolas, 499990, null, 8, 3,
                "/img/ps5.jpg",
                "Consola Sony PlayStation 5 con lector de discos y 1TB SSD ultra rápido."));
        productos.add(producto("Silla Gamer Ergonómica Pro", sillas, 159990, 129990, 4, 5,
                "/img/silla.jpg",
                "Silla gamer reclinable 180° con soporte lumbar y apoya brazos 4D."));
        productos.add(producto("Catan El Juego", juegosMesa, 34990, 27990, 10, 3,
                "/img/catan.jpg",
                "Juego de mesa clásico de comercio y colonización de islas para toda la familia."));
        productos.add(producto("Teclado Mecánico RGB Switch Blue", perifericos, 45990, 34990, 15, 5,
                "https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=500&auto=format&fit=crop",
                "Teclado mecánico con retroiluminación RGB configurable y switches táctiles."));
        productos.add(producto("Mouse Gamer 16000 DPI Sensor Óptico", perifericos, 29990, null, 2, 4,
                "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=500&auto=format&fit=crop",
                "Mouse ultra liviano con 6 botones programables y sensor de alta precisión."));
        productos.add(producto("Nintendo Switch OLED Mario Edition", consolas, 349990, 319990, 2, 4,
                "https://images.unsplash.com/photo-1578303512597-81e6cc155b3e?w=500&auto=format&fit=crop",
                "Pantalla OLED de 7 pulgadas con soporte ajustable y audio optimizado."));
        productos.add(producto("Xbox Series X 1TB Black", consolas, 529990, 489990, 6, 2,
                "https://images.unsplash.com/photo-1605901309584-818e25960a8f?w=500&auto=format&fit=crop",
                "La Xbox más potente de la historia con resolución 4K nativa a 120 FPS."));
        productos.add(producto("Silla Gamer RGB Lumina Edition", sillas, 189990, null, 7, 3,
                "https://media.falabella.com/falabellaCL/148583525_01/w=1500,h=1500,fit=cover",
                "Iluminación LED RGB perimetral con control remoto y cuero sintético transpirable."));
        productos.add(producto("Carcassonne Edición Plus", juegosMesa, 38990, null, 3, 4,
                "https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?w=500&auto=format&fit=crop",
                "Estrategia medieval construyendo caminos, monasterios y castillos por losas."));
        productos.add(producto("Dixit Odyssey Juego de Mesa", juegosMesa, 29990, 24990, 9, 2,
                "https://images.unsplash.com/photo-1632501641765-e568d28b0015?w=500&auto=format&fit=crop",
                "Juego poético y visual de deducción mediante cartas con ilustraciones únicas."));
        productos.add(producto("Audífonos Gamer 7.1 Surround", perifericos, 59990, 49990, 11, 3,
                "https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=500&auto=format&fit=crop",
                "Audio espacial 7.1 con drivers de 50mm y micrófono con cancelación de ruido."));
        productos.add(producto("PC Gamer RTX 4060 Ryzen 5 16GB", computadores, 849990, 799990, 5, 2,
                "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=500&auto=format&fit=crop",
                "Equipo armado de alto desempeño con 1TB NVMe, refrigeración líquida y vidrio templado."));
        productos.add(producto("Notebook Gamer i7 RTX 4070 144Hz", computadores, 1299990, null, 1, 3,
                "https://images.unsplash.com/photo-1603302576837-37561b2e2302?w=500&auto=format&fit=crop",
                "Pantalla IPS de 15.6 pulgadas a 144Hz, 32GB RAM DDR5 y chasis de aluminio."));
        productos.add(producto("Mousepad XXL Speed 900x400mm", accesorios, 18990, 14990, 20, 5,
                "https://images.unsplash.com/photo-1616440347437-b1c73416efc2?w=500&auto=format&fit=crop",
                "Superficie de microfibra de baja fricción, bordes cosidos y base antideslizante."));
        productos.add(producto("Brazo Hidráulico para Micrófono", accesorios, 25990, null, 8, 3,
                "https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=500&auto=format&fit=crop",
                "Brazo articulado resistente con gestión interna de cables para streamers y podcast."));
        productoRepository.saveAll(productos);
    }

    private Categoria categoria(String nombre, String descripcion) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setDescripcion(descripcion);
        categoria.setActiva(true);
        return categoriaRepository.save(categoria);
    }

    private Producto producto(String nombre, Categoria categoria, Integer precio, Integer precioOferta,
                              Integer stock, Integer stockCritico, String imagen, String descripcion) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setCategoria(categoria);
        producto.setPrecio(precio);
        producto.setPrecioOferta(precioOferta);
        producto.setStock(stock);
        producto.setStockCritico(stockCritico);
        producto.setImagen(imagen);
        producto.setDescripcion(descripcion);
        return producto;
    }
}
