package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.ProductoRequest;
import cl.duoc.levelup.exception.ConflictoException;
import cl.duoc.levelup.exception.DatosInvalidosException;
import cl.duoc.levelup.exception.RecursoNoEncontradoException;
import cl.duoc.levelup.model.Categoria;
import cl.duoc.levelup.model.Producto;
import cl.duoc.levelup.repository.CategoriaRepository;
import cl.duoc.levelup.repository.DetalleBoletaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de ProductoService con Mockito (sin base de datos).
 * Se prueban los filtros del listado, las reglas de negocio y los casos de error.
 */
@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private DetalleBoletaRepository detalleBoletaRepository;

    @InjectMocks
    private ProductoService productoService;

    private Categoria consolas() {
        Categoria categoria = new Categoria();
        categoria.setId(1L);
        categoria.setNombre("Consolas");
        return categoria;
    }

    private Producto producto(Long id, String nombre) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setPrecio(499990);
        producto.setStock(8);
        producto.setStockCritico(3);
        producto.setCategoria(consolas());
        return producto;
    }

    /** Request válido: precio 100.000, oferta 90.000, categoría 1. */
    private ProductoRequest requestValido() {
        return new ProductoRequest("Steam Deck OLED", "Consola portátil", 100000, 90000,
                5, 2, "/img/steamdeck.jpg", 1L);
    }

    // ----- listar con filtros -----

    @Test
    void listar_sinFiltros_usaFindAll() {
        when(productoRepository.findAll()).thenReturn(List.of(producto(1L, "PS5")));

        List<Producto> resultado = productoService.listar(null, null);

        assertEquals(1, resultado.size());
        verify(productoRepository).findAll();
    }

    @Test
    void listar_soloCategoria_filtraPorCategoria() {
        when(productoRepository.findByCategoriaId(1L)).thenReturn(List.of(producto(1L, "PS5")));

        productoService.listar(1L, null);

        verify(productoRepository).findByCategoriaId(1L);
    }

    @Test
    void listar_soloBusqueda_buscaPorNombreSinEspacios() {
        when(productoRepository.findByNombreContainingIgnoreCase("mouse")).thenReturn(List.of());

        productoService.listar(null, "  mouse ");

        verify(productoRepository).findByNombreContainingIgnoreCase("mouse");
    }

    @Test
    void listar_categoriaYBusqueda_usaElFiltroCombinado() {
        when(productoRepository.findByCategoriaIdAndNombreContainingIgnoreCase(1L, "xbox")).thenReturn(List.of());

        productoService.listar(1L, "xbox");

        verify(productoRepository).findByCategoriaIdAndNombreContainingIgnoreCase(1L, "xbox");
    }

    @Test
    void listar_busquedaEnBlanco_seIgnoraYUsaFindAll() {
        when(productoRepository.findAll()).thenReturn(List.of());

        productoService.listar(null, "   ");

        verify(productoRepository).findAll();
    }

    // ----- obtener -----

    @Test
    void obtenerPorId_inexistente_lanzaNoEncontrado() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> productoService.obtenerPorId(99L));
    }

    // ----- crear -----

    @Test
    void crear_conDatosValidos_guardaElProductoConSuCategoria() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(consolas()));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Producto creado = productoService.crear(requestValido());

        assertEquals("Steam Deck OLED", creado.getNombre());
        assertEquals(90000, creado.getPrecioOferta());
        assertEquals("Consolas", creado.getCategoria().getNombre());
    }

    @Test
    void crear_sinPrecioOferta_esValido() {
        ProductoRequest request = requestValido();
        request.setPrecioOferta(null);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(consolas()));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Producto creado = productoService.crear(request);

        assertNull(creado.getPrecioOferta());
    }

    @Test
    void crear_conOfertaMayorOIgualAlPrecio_lanzaDatosInvalidosYNoGuarda() {
        ProductoRequest request = requestValido();
        request.setPrecioOferta(100000); // igual al precio: no es una oferta

        assertThrows(DatosInvalidosException.class, () -> productoService.crear(request));
        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void crear_conCategoriaInexistente_lanzaNoEncontradoYNoGuarda() {
        ProductoRequest request = requestValido();
        request.setCategoriaId(99L);
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> productoService.crear(request));
        verify(productoRepository, never()).save(any(Producto.class));
    }

    // ----- actualizar -----

    @Test
    void actualizar_existente_reemplazaLosDatos() {
        Producto existente = producto(5L, "PS5");
        when(productoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(consolas()));
        when(productoRepository.save(existente)).thenReturn(existente);

        Producto actualizado = productoService.actualizar(5L, requestValido());

        assertEquals("Steam Deck OLED", actualizado.getNombre());
        assertEquals(100000, actualizado.getPrecio());
        assertEquals(5, actualizado.getStock());
    }

    @Test
    void actualizar_inexistente_lanzaNoEncontrado() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> productoService.actualizar(99L, requestValido()));
        verify(productoRepository, never()).save(any(Producto.class));
    }

    // ----- eliminar -----

    @Test
    void eliminar_productoNoVendido_loElimina() {
        Producto existente = producto(5L, "PS5");
        when(productoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(detalleBoletaRepository.existsByProductoId(5L)).thenReturn(false);

        productoService.eliminar(5L);

        verify(productoRepository).delete(existente);
    }

    @Test
    void eliminar_productoYaVendido_lanzaConflictoYNoElimina() {
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto(5L, "PS5")));
        when(detalleBoletaRepository.existsByProductoId(5L)).thenReturn(true);

        assertThrows(ConflictoException.class, () -> productoService.eliminar(5L));
        verify(productoRepository, never()).delete(any(Producto.class));
    }
}
