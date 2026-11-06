package cl.duoc.levelup.service;

import cl.duoc.levelup.dto.CategoriaRequest;
import cl.duoc.levelup.exception.ConflictoException;
import cl.duoc.levelup.exception.RecursoNoEncontradoException;
import cl.duoc.levelup.model.Categoria;
import cl.duoc.levelup.repository.CategoriaRepository;
import cl.duoc.levelup.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de CategoriaService.
 * Los repositorios son "mocks" (objetos falsos de Mockito): no se usa MySQL,
 * solo se prueba la lógica del service decidiendo qué devuelve cada repositorio.
 */
@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ProductoRepository productoRepository;

    /** Mockito crea el service y le inyecta los dos mocks de arriba por el constructor. */
    @InjectMocks
    private CategoriaService categoriaService;

    /** Crea una categoría de prueba con id. */
    private Categoria categoria(Long id, String nombre) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNombre(nombre);
        categoria.setActiva(true);
        return categoria;
    }

    @Test
    void listarActivas_devuelveLasCategoriasDelRepositorio() {
        when(categoriaRepository.findByActivaTrue()).thenReturn(List.of(categoria(1L, "Consolas")));

        List<Categoria> resultado = categoriaService.listarActivas();

        assertEquals(1, resultado.size());
        assertEquals("Consolas", resultado.get(0).getNombre());
    }

    @Test
    void obtenerPorId_existente_devuelveLaCategoria() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria(1L, "Consolas")));

        Categoria resultado = categoriaService.obtenerPorId(1L);

        assertEquals("Consolas", resultado.getNombre());
    }

    @Test
    void obtenerPorId_inexistente_lanzaNoEncontrado() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> categoriaService.obtenerPorId(99L));
    }

    @Test
    void crear_conNombreNuevo_guardaLaCategoria() {
        CategoriaRequest request = new CategoriaRequest("  Streaming  ", "Equipos para streamers", null);
        when(categoriaRepository.existsByNombreIgnoreCase("Streaming")).thenReturn(false);
        // save devuelve la misma categoría que recibe
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Categoria creada = categoriaService.crear(request);

        assertEquals("Streaming", creada.getNombre()); // se quitan los espacios
        assertTrue(creada.isActiva());                   // sin "activa" queda activa
        verify(categoriaRepository).save(any(Categoria.class));
    }

    @Test
    void crear_conNombreRepetido_lanzaConflictoYNoGuarda() {
        CategoriaRequest request = new CategoriaRequest("consolas", null, true);
        when(categoriaRepository.existsByNombreIgnoreCase("consolas")).thenReturn(true);

        assertThrows(ConflictoException.class, () -> categoriaService.crear(request));
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    @Test
    void actualizar_conservandoSuMismoNombre_guardaLosCambios() {
        Categoria existente = categoria(1L, "Consolas");
        CategoriaRequest request = new CategoriaRequest("Consolas", "Nueva descripción", false);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(categoriaRepository.findByNombreIgnoreCase("Consolas")).thenReturn(Optional.of(existente));
        when(categoriaRepository.save(existente)).thenReturn(existente);

        Categoria actualizada = categoriaService.actualizar(1L, request);

        assertEquals("Nueva descripción", actualizada.getDescripcion());
        assertFalse(actualizada.isActiva());
    }

    @Test
    void actualizar_conNombreDeOtraCategoria_lanzaConflicto() {
        CategoriaRequest request = new CategoriaRequest("Sillas", null, true);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria(1L, "Consolas")));
        when(categoriaRepository.findByNombreIgnoreCase("Sillas")).thenReturn(Optional.of(categoria(3L, "Sillas")));

        assertThrows(ConflictoException.class, () -> categoriaService.actualizar(1L, request));
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    @Test
    void actualizar_inexistente_lanzaNoEncontrado() {
        CategoriaRequest request = new CategoriaRequest("Sillas", null, true);
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> categoriaService.actualizar(99L, request));
    }

    @Test
    void eliminar_sinProductos_eliminaLaCategoria() {
        Categoria existente = categoria(1L, "Consolas");
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(productoRepository.countByCategoriaId(1L)).thenReturn(0L);

        categoriaService.eliminar(1L);

        verify(categoriaRepository).delete(existente);
    }

    @Test
    void eliminar_conProductos_lanzaConflictoYNoElimina() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria(1L, "Consolas")));
        when(productoRepository.countByCategoriaId(1L)).thenReturn(3L);

        assertThrows(ConflictoException.class, () -> categoriaService.eliminar(1L));
        verify(categoriaRepository, never()).delete(any(Categoria.class));
    }

    @Test
    void eliminar_inexistente_lanzaNoEncontrado() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> categoriaService.eliminar(99L));
    }
}
