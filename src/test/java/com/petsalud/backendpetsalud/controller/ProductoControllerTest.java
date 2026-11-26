package com.petsalud.backendpetsalud.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.petsalud.backendpetsalud.entity.Producto;
import com.petsalud.backendpetsalud.exception.GlobalExceptionHandler;
import com.petsalud.backendpetsalud.monitoreo.SuspiciousActivityService;
import com.petsalud.backendpetsalud.service.ProductoService;

/**
 * Prueba de ProductoController con MockMvc en modo standalone.
 * No levanta Spring: se construye el MockMvc a mano, solo con el controlador
 * y el manejador de errores. El servicio se simula con Mockito.
 * La seguridad (@PreAuthorize) no aplica aquí; los permisos se prueban en AutorizacionEndpointsTest.
 */
@ExtendWith(MockitoExtension.class)
class ProductoControllerTest {

    @Mock private ProductoService productoService;
    @Mock private SuspiciousActivityService monitoreo;

    @InjectMocks private ProductoController productoController;

    @Captor private ArgumentCaptor<Producto> productoCaptor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(productoController)                         // solo este controlador
                .setControllerAdvice(new GlobalExceptionHandler(monitoreo))  // errores → JSON 400/404
                .build();
    }

    // GET 

    @Test
    @DisplayName("GET /api/productos lista el catálogo en JSON (200)")
    void listarProductos_200() throws Exception {
        when(productoService.listarProductos()).thenReturn(List.of(alimento(), collar()));

        mockMvc.perform(get("/api/productos"))
                .andDo(print())   // muestra en consola la petición y la respuesta completas
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].nombre").value("Alimento gato 3kg"))
                .andExpect(jsonPath("$[1].nombre").value("Collar antipulgas"));
    }

    @Test
    @DisplayName("GET /api/productos/{id} devuelve el producto si existe (200)")
    void obtenerPorId_existente_200() throws Exception {
        when(productoService.obtenerProductoPorId(1L)).thenReturn(Optional.of(alimento()));

        mockMvc.perform(get("/api/productos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.precio").value(12990))
                .andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    @DisplayName("GET /api/productos/{id} responde 404 si el producto no existe")
    void obtenerPorId_inexistente_404() throws Exception {
        when(productoService.obtenerProductoPorId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/productos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/productos/abc responde 400: el id debe ser numérico")
    void obtenerPorId_idNoNumerico_400() throws Exception {
        mockMvc.perform(get("/api/productos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La solicitud tiene un formato inválido"));

        verify(productoService, never()).obtenerProductoPorId(any());
    }

    @Test
    @DisplayName("GET /api/productos/categoria filtra por la categoría recibida")
    void listarPorCategoria_200() throws Exception {
        when(productoService.listarProductosPorCategoria("Accesorios")).thenReturn(List.of(collar()));

        mockMvc.perform(get("/api/productos/categoria").param("categoria", "Accesorios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("Accesorios"));
    }

    @Test
    @DisplayName("GET /api/productos/stock-bajo devuelve los productos bajo la cantidad indicada")
    void listarStockBajo_200() throws Exception {
        when(productoService.listarProductosConStockBajo(10)).thenReturn(List.of(alimento()));

        mockMvc.perform(get("/api/productos/stock-bajo").param("cantidad", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stock").value(5));
    }

    @Test
    @DisplayName("GET /api/productos/stock-bajo sin el parámetro 'cantidad' responde 400")
    void listarStockBajo_sinParametro_400() throws Exception {
        mockMvc.perform(get("/api/productos/stock-bajo"))
                .andExpect(status().isBadRequest());

        verify(productoService, never()).listarProductosConStockBajo(any());
    }

    //POST / PUT / DELETE 

    @Test
    @DisplayName("POST /api/productos convierte el JSON en Producto y devuelve el creado (200)")
    void agregarProducto_200() throws Exception {
        when(productoService.agregarProducto(any(Producto.class))).thenAnswer(invocacion -> {
            Producto recibido = invocacion.getArgument(0);
            recibido.setId(5L);   // simula el id que asignaría la base de datos
            return recibido;
        });

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Collar antipulgas","precio":8990,"stock":10,"categoria":"Accesorios"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.nombre").value("Collar antipulgas"));

        // El controlador le pasó al servicio exactamente lo que venía en el JSON
        verify(productoService).agregarProducto(productoCaptor.capture());
        assertEquals("Collar antipulgas", productoCaptor.getValue().getNombre());
        assertEquals(new BigDecimal("8990"), productoCaptor.getValue().getPrecio());
        assertEquals(10, productoCaptor.getValue().getStock());
    }

    @Test
    @DisplayName("POST /api/productos con un nombre repetido responde 400 con el motivo")
    void agregarProducto_duplicado_400() throws Exception {
        when(productoService.agregarProducto(any(Producto.class)))
                .thenThrow(new IllegalArgumentException("El producto ya existe"));

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Alimento gato 3kg\",\"precio\":12990,\"stock\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("El producto ya existe"))
                .andExpect(jsonPath("$.ruta").value("/api/productos"));
    }

    @Test
    @DisplayName("PUT /api/productos/{id} actualiza y devuelve el producto (200)")
    void actualizarProducto_200() throws Exception {
        Producto actualizado = alimento();
        actualizado.setStock(20);
        when(productoService.actualizarProducto(eq(1L), any(Producto.class))).thenReturn(actualizado);

        mockMvc.perform(put("/api/productos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Alimento gato 3kg\",\"precio\":12990,\"stock\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(20));
    }

    @Test
    @DisplayName("DELETE /api/productos/{id} elimina y responde 204 sin cuerpo")
    void eliminarProducto_204() throws Exception {
        mockMvc.perform(delete("/api/productos/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(productoService).eliminarProducto(1L);
    }

    // Datos de prueba 

    private static Producto alimento() {
        return Producto.builder().id(1L).nombre("Alimento gato 3kg").precio(new BigDecimal("12990"))
                .stock(5).categoria("Alimentos").build();
    }

    private static Producto collar() {
        return Producto.builder().id(2L).nombre("Collar antipulgas").precio(new BigDecimal("8990"))
                .stock(10).categoria("Accesorios").build();
    }
}