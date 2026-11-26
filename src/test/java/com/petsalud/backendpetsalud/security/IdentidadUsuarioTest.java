package com.petsalud.backendpetsalud.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;

/**
 * Identidad del usuario: UsuarioDetailsService lo busca por email (BD simulada con Mockito)
 * y UsuarioDetails lo adapta a Spring Security con su rol (prefijo ROLE_).
 */
@ExtendWith(MockitoExtension.class)
class IdentidadUsuarioTest {

    private static final String EMAIL = "ana@petsalud.test";

    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private UsuarioDetailsService usuarioDetailsService;

    @ParameterizedTest(name = "Usuario {0} → autoridad ROLE_{0}")
    @EnumSource(Rol.class)
    void loadUserByUsername_usuarioExistente_cargaSuRol(Rol rol) {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(rol)));

        UserDetails detalles = usuarioDetailsService.loadUserByUsername(EMAIL);

        assertEquals(EMAIL, detalles.getUsername());
        assertEquals(1, detalles.getAuthorities().size());
        assertEquals("ROLE_" + rol.name(), detalles.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("Un email que no está registrado no tiene identidad en el sistema")
    void loadUserByUsername_emailInexistente_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("nadie@petsalud.test")).thenReturn(Optional.empty());

        UsernameNotFoundException error = assertThrows(UsernameNotFoundException.class,
                () -> usuarioDetailsService.loadUserByUsername("nadie@petsalud.test"));

        assertTrue(error.getMessage().contains("nadie@petsalud.test"));
    }

    @Test
    @DisplayName("UsuarioDetails expone id, email y hash, y la cuenta queda habilitada")
    void usuarioDetails_exponeDatosDeIdentidad() {
        UsuarioDetails detalles = new UsuarioDetails(usuario(Rol.CLIENTE));

        assertAll(
                () -> assertEquals(1L, detalles.getId()),
                () -> assertEquals(EMAIL, detalles.getUsername()),
                () -> assertEquals("$2a$10$hash", detalles.getPassword()),
                () -> assertTrue(detalles.isEnabled()),
                () -> assertTrue(detalles.isAccountNonLocked()),
                () -> assertTrue(detalles.isAccountNonExpired()),
                () -> assertTrue(detalles.isCredentialsNonExpired()));
    }

    private static Usuario usuario(Rol rol) {
        return Usuario.builder().id(1L).email(EMAIL).password("$2a$10$hash").rol(rol).build();
    }
    
}