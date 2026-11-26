package com.petsalud.backendpetsalud.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.petsalud.backendpetsalud.entity.Producto;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.entity.Venta;
import com.petsalud.backendpetsalud.repository.ProductoRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import com.petsalud.backendpetsalud.repository.VentaRepository;

/**
 *  Entidad Venta. Los tres repositorios se simulan con Mockito:
 * la prueba no toca MySQL y controla exactamente qué "devuelve la base de datos".
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceImplTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ProductoRepository productoRepository;

    @InjectMocks private VentaServiceImpl ventaService;

    @Captor private ArgumentCaptor<Venta> ventaCaptor;

    private Producto alimento;
    private Producto antiparasitario;

    @BeforeEach
    void setUp() {
        alimento = producto(1L, "Alimento gato 3kg", "12990", 5);
        antiparasitario = producto(2L, "Antiparasitario", "8990", 2);
    }

    //  Usuario asociado a la venta 

    @Test
    @DisplayName("Usuario con datos completos: la venta se registra vinculada a ese usuario")
    void realizarVenta_usuarioCompleto_quedaVinculadoALaVenta() {
        // Arrange
        Usuario ana = usuarioCompleto();
        prepararVentaExitosa(ana);

        // Act
        ventaService.realizarVenta(1L, List.of(1L, 2L));

        // Assert: capturamos la venta que se intentó guardar en la BD simulada
        verify(ventaRepository).save(ventaCaptor.capture());
        Usuario usuarioDeLaVenta = ventaCaptor.getValue().getUsuario();
        assertSame(ana, usuarioDeLaVenta);
        assertAll("datos obligatorios del usuario de la venta",
                () -> assertEquals("Ana", usuarioDeLaVenta.getNombre()),
                () -> assertEquals("Pérez", usuarioDeLaVenta.getApellido()),
                () -> assertEquals("ana@petsalud.test", usuarioDeLaVenta.getEmail()),
                () -> assertEquals("Av. Siempre Viva 123", usuarioDeLaVenta.getDireccion()));
    }

    @ParameterizedTest(name = "Usuario sin {0}: la venta se rechaza")
    @MethodSource("usuariosIncompletos")
    void realizarVenta_usuarioIncompleto_lanzaErrorYNoRegistra(String campoFaltante, Usuario incompleto) {
        // Arrange: la "base de datos" devuelve un usuario al que le falta un dato
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(incompleto));

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, List.of(1L, 2L)));

        // Assert
        assertTrue(error.getMessage().endsWith("Faltan: " + campoFaltante));
        // La validación ocurre ANTES de tocar productos, stock o ventas
        verify(productoRepository, never()).findAllById(any());
        verify(productoRepository, never()).save(any());
        verify(ventaRepository, never()).save(any());
    }

    static Stream<Arguments> usuariosIncompletos() {
        return Stream.of(
                Arguments.of("nombre",    usuario(null,  "Pérez", "ana@petsalud.test", "Av. Siempre Viva 123")),
                Arguments.of("apellido",  usuario("Ana", "",      "ana@petsalud.test", "Av. Siempre Viva 123")),
                Arguments.of("email",     usuario("Ana", "Pérez", "   ",               "Av. Siempre Viva 123")),
                Arguments.of("direccion", usuario("Ana", "Pérez", "ana@petsalud.test", null)));
    }

    @Test
    @DisplayName("Si el usuario no existe, no se registra la venta")
    void realizarVenta_usuarioInexistente_lanzaError() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(99L, List.of(1L)));

        assertEquals("Usuario no encontrado", error.getMessage());
        verify(ventaRepository, never()).save(any());
    }

    //  Stock y total 

    @Test
    @DisplayName("Sin stock disponible no se registra la venta")
    void realizarVenta_productoSinStock_lanzaErrorYNoRegistra() {
        Producto agotado = producto(3L, "Shampoo antipulgas", "6990", 0);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioCompleto()));
        when(productoRepository.findAllById(List.of(3L))).thenReturn(List.of(agotado));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, List.of(3L)));

        assertEquals("El producto Shampoo antipulgas no tiene stock disponible", error.getMessage());
        assertEquals(0, agotado.getStock());   // el stock no queda negativo
        verify(ventaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con stock disponible, cada producto vendido descuenta 1 unidad")
    void realizarVenta_conStock_descuentaUnaUnidad() {
        prepararVentaExitosa(usuarioCompleto());

        ventaService.realizarVenta(1L, List.of(1L, 2L));

        assertEquals(4, alimento.getStock());
        assertEquals(1, antiparasitario.getStock());
        verify(productoRepository).save(alimento);
        verify(productoRepository).save(antiparasitario);
    }

    @Test
    @DisplayName("El total es la suma exacta de los precios de los productos")
    void realizarVenta_variosProductos_calculaTotalCorrecto() {
        prepararVentaExitosa(usuarioCompleto());

        Venta venta = ventaService.realizarVenta(1L, List.of(1L, 2L));

        assertEquals(new BigDecimal("21980"), venta.getTotal());   // 12.990 + 8.990
        assertNotNull(venta.getFecha());
    }

    @Test
    @DisplayName("Si algún producto no existe, no se registra la venta ni se toca el stock")
    void realizarVenta_productoInexistente_lanzaError() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioCompleto()));
        when(productoRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(alimento));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, List.of(1L, 99L)));

        assertEquals("Algunos productos no fueron encontrados", error.getMessage());
        assertEquals(5, alimento.getStock());
        verify(ventaRepository, never()).save(any());
    }

    //  Relación Venta – Producto 

    @Test
    @DisplayName("La venta queda vinculada exactamente a los productos vendidos")
    void realizarVenta_vinculaLosProductosVendidos() {
        prepararVentaExitosa(usuarioCompleto());

        Venta venta = ventaService.realizarVenta(1L, List.of(1L, 2L));

        assertEquals(List.of(alimento, antiparasitario), venta.getProductos());
    }

    //Consultas 

    @Test
    @DisplayName("Las consultas de ventas se delegan al repositorio")
    void consultas_delegaEnElRepositorio() {
        Venta venta = Venta.builder().id(30L).build();
        when(ventaRepository.findById(30L)).thenReturn(Optional.of(venta));
        when(ventaRepository.findByUsuarioId(1L)).thenReturn(List.of(venta));
        when(ventaRepository.findAll()).thenReturn(List.of(venta));

        assertEquals(Optional.of(venta), ventaService.obtenerVentaPorId(30L));
        assertEquals(List.of(venta), ventaService.listarVentasPorUsuario(1L));
        assertEquals(List.of(venta), ventaService.listarTodasLasVentas());
    }

    // Datos de prueba 

    private void prepararVentaExitosa(Usuario usuario) {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(productoRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(alimento, antiparasitario));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static Usuario usuarioCompleto() {
        return usuario("Ana", "Pérez", "ana@petsalud.test", "Av. Siempre Viva 123");
    }

    private static Usuario usuario(String nombre, String apellido, String email, String direccion) {
        return Usuario.builder()
                .id(1L).nombre(nombre).apellido(apellido).email(email)
                .direccion(direccion).rol(Rol.CLIENTE)
                .build();
    }

    private static Producto producto(Long id, String nombre, String precio, int stock) {
        return Producto.builder()
                .id(id).nombre(nombre).precio(new BigDecimal(precio)).stock(stock)
                .build();
    }
}