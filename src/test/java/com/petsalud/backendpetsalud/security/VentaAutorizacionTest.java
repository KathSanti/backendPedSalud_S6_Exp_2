package com.petsalud.backendpetsalud.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.petsalud.backendpetsalud.controller.VentaController;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.entity.Venta;
import com.petsalud.backendpetsalud.monitoreo.SuspiciousActivityService;
import com.petsalud.backendpetsalud.service.VentaService;

/**
 * Permisos por rol sobre las ventas, probando las dos capas de autorización del proyecto:
 *  - Capa 1 (rol):       @PreAuthorize("hasAnyRole('EMPLEADO','GERENTE')") en VentaController.
 *  - Capa 2 (propiedad): @accesoRecursos.venta(...) / .usuario(...): un CLIENTE solo ve lo suyo.
 * prueba de seguridad real + servicios simulados con Mockito (@MockBean). No usa base de datos.
 */
@WebMvcTest(controllers = VentaController.class)
@Import({SecurityConfig.class, SuspiciousActivityService.class})
class VentaAutorizacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private VentaService ventaService;
    @MockBean private JwtService jwtService;
    @MockBean private UsuarioDetailsService usuarioDetailsService;
    @MockBean(name = "accesoRecursos") private AccesoRecursos accesoRecursos;

    // Identidad: sin JWT no hay acceso

    @Test
    @DisplayName("Sin token no se puede registrar una venta (401)")
    void registrarVenta_sinToken_401() throws Exception {
        mockMvc.perform(registrarVenta())
                .andExpect(status().isUnauthorized());
        verify(ventaService, never()).realizarVenta(any(), any());
    }

    //Capa 1: rol

    @Test
    @WithMockUser(roles = "CLIENTE")
    @DisplayName("Capa 1 - Un CLIENTE no puede registrar ventas (403)")
    void registrarVenta_cliente_403() throws Exception {
        mockMvc.perform(registrarVenta())
                .andExpect(status().isForbidden());
        verify(ventaService, never()).realizarVenta(any(), any());
    }

    @Test
    @WithMockUser(roles = "VETERINARIO")
    @DisplayName("Capa 1 - Un EMPLEADO puede registrar ventas (200)")
    void registrarVenta_empleado_200() throws Exception {
        when(ventaService.realizarVenta(1L, List.of(1L, 2L))).thenReturn(ventaDe(1L));

        mockMvc.perform(registrarVenta())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.usuario.email").value("ana@petsalud.test"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Capa 1 - Un GERENTE puede registrar ventas (200)")
    void registrarVenta_gerente_200() throws Exception {
        when(ventaService.realizarVenta(1L, List.of(1L, 2L))).thenReturn(ventaDe(1L));

        mockMvc.perform(registrarVenta())
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VETERINARIO")
    @DisplayName("Venta a un usuario con datos incompletos: la API responde 400 indicando qué falta")
    void registrarVenta_usuarioIncompleto_400() throws Exception {
        when(ventaService.realizarVenta(eq(1L), anyList())).thenThrow(new IllegalArgumentException(
                "El usuario no tiene sus datos obligatorios completos. Faltan: direccion"));

        mockMvc.perform(registrarVenta())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("direccion")));
    }

    @Test
    @WithMockUser(roles = "VETERINARIO")
    @DisplayName("Capa 1 - Un VETERINARIO no puede listar todas las ventas (403)")
    void listarTodas_empleado_403() throws Exception {
        mockMvc.perform(get("/api/ventas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Capa 1 - El ADMIN puede listar todas las ventas (200)")
    void listarTodas_gerente_200() throws Exception {
        when(ventaService.listarTodasLasVentas()).thenReturn(List.of(ventaDe(1L)));

        mockMvc.perform(get("/api/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(30));
    }

    // Capa 2: propiedad del recurso 

    @Test
    @WithMockUser(roles = "CLIENTE")
    @DisplayName("Capa 2 - Un CLIENTE ve su propia venta (200)")
    void verVenta_clienteDueno_200() throws Exception {
        when(accesoRecursos.venta(any(), eq(30L))).thenReturn(true);
        when(ventaService.obtenerVentaPorId(30L)).thenReturn(Optional.of(ventaDe(1L)));

        mockMvc.perform(get("/api/ventas/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30));
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    @DisplayName("Capa 2 - Un CLIENTE no puede ver la venta de otro cliente (403)")
    void verVenta_clienteAjeno_403() throws Exception {
        when(accesoRecursos.venta(any(), eq(30L))).thenReturn(false);

        mockMvc.perform(get("/api/ventas/30"))
                .andExpect(status().isForbidden());
        verify(ventaService, never()).obtenerVentaPorId(any());
    }

    @Test
    @WithMockUser(roles = "VETERINARIO")
    @DisplayName("Capa 2 -El personal no pasa por el control de propiedad; venta inexistente → 404")
    void verVenta_empleado_noRevisaPropiedad() throws Exception {
        when(ventaService.obtenerVentaPorId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/ventas/99"))
                .andExpect(status().isNotFound());
        verify(accesoRecursos, never()).venta(any(), any());
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    @DisplayName("Capa 2 - Un CLIENTE lista sus ventas, pero no las de otro usuario")
    void listarPorUsuario_cliente_soloLasSuyas() throws Exception {
        when(accesoRecursos.usuario(any(), eq(1L))).thenReturn(true);
        when(ventaService.listarVentasPorUsuario(1L)).thenReturn(List.of(ventaDe(1L)));

        mockMvc.perform(get("/api/ventas/usuario/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuario.id").value(1));

        mockMvc.perform(get("/api/ventas/usuario/2"))
                .andExpect(status().isForbidden());
    }

    // ---------- Datos de prueba ----------

    private static MockHttpServletRequestBuilder registrarVenta() {
        return post("/api/ventas")
                .param("usuarioId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[1, 2]");
    }

    private static Venta ventaDe(Long usuarioId) {
        Usuario ana = Usuario.builder()
                .id(usuarioId).nombre("Ana").apellido("Pérez").email("ana@petsalud.test")
                .direccion("Av. Siempre Viva 123").rol(Rol.CLIENTE)
                .build();
        return Venta.builder().id(30L).usuario(ana).total(new BigDecimal("21980")).build();
    }
}