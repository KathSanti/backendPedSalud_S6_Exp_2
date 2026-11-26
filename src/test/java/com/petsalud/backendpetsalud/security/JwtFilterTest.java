package com.petsalud.backendpetsalud.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.monitoreo.SuspiciousActivityService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;

/**
 * Identidad del usuario con JWT: JwtFilter lee el token "Bearer", lo valida y deja
 * al usuario autenticado con su rol. JwtService, la BD (UsuarioDetailsService) y el
 * monitoreo se simulan con Mockito.
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    private static final String TOKEN = "header.payload.firma";
    private static final String EMAIL = "ana@petsalud.test";
    // 32 bytes en Base64, solo para pruebas
    private static final String CLAVE_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Mock private JwtService jwtService;
    @Mock private UsuarioDetailsService usuarioDetailsService;
    @Mock private SuspiciousActivityService monitoreo;
    @Mock private FilterChain filterChain;
    @Mock private Claims claims;

    @InjectMocks private JwtFilter jwtFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest("GET", "/api/ventas");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "Token válido de un {0} → autenticado con ROLE_{0}")
    @EnumSource(Rol.class)
    void tokenValido_autenticaConElRolDelUsuario(Rol rol) throws Exception {
        // Arrange
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        when(jwtService.validationToken(TOKEN)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(EMAIL);
        when(usuarioDetailsService.loadUserByUsername(EMAIL)).thenReturn(detallesCon(rol));

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.isAuthenticated());
        assertEquals(EMAIL, auth.getName());
        assertEquals(1, auth.getAuthorities().size());
        assertEquals("ROLE_" + rol.name(), auth.getAuthorities().iterator().next().getAuthority());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("El rol sale de la BD y no del token: un token emitido como GERENTE no sirve si hoy es CLIENTE")
    void tokenConRolAntiguo_mandaElRolActualDeLaBaseDeDatos() throws Exception {
        // Arrange: token REAL, firmado cuando el usuario era GERENTE
        JwtService jwtReal = new JwtService(CLAVE_BASE64, "backendpetsalud", 30);
        String tokenGerente = jwtReal.generateToken(
                User.withUsername(EMAIL).password("x").roles("GERENTE").build());
        JwtFilter filtro = new JwtFilter(jwtReal, usuarioDetailsService, monitoreo);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente);
        // pero en la base de datos (simulada) hoy es CLIENTE
        when(usuarioDetailsService.loadUserByUsername(EMAIL)).thenReturn(detallesCon(Rol.CLIENTE));

        // Act
        filtro.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("ROLE_CLIENTE", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("Token inválido o manipulado: no autentica, registra el evento y la petición sigue, terminará en 401")
    void tokenInvalido_noAutenticaYRegistraEvento() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        when(jwtService.validationToken(TOKEN)).thenThrow(new JwtException("firma inválida"));

        jwtFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(monitoreo).registrarTokenInvalido(anyString(), eq("/api/ventas"), eq("firma inválida"));
        verify(usuarioDetailsService, never()).loadUserByUsername(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Token de un usuario que ya no existe en la BD: no autentica")
    void tokenDeUsuarioEliminado_noAutentica() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        when(jwtService.validationToken(TOKEN)).thenReturn(claims);
        when(claims.getSubject()).thenReturn(EMAIL);
        when(usuarioDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new UsernameNotFoundException("Usuario no encontrado con email: " + EMAIL));

        jwtFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(monitoreo).registrarTokenInvalido(anyString(), anyString(), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Sin cabecera Authorization: no valida nada y deja pasar como anónimo")
    void sinToken_noValidaNada() throws Exception {
        jwtFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService, usuarioDetailsService, monitoreo);
        verify(filterChain).doFilter(request, response);
    }

    private static UsuarioDetails detallesCon(Rol rol) {
        return new UsuarioDetails(Usuario.builder()
                .id(1L).email(EMAIL).password("$2a$10$hash").rol(rol).build());
    }
}