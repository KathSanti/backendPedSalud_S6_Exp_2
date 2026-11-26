package com.petsalud.backendpetsalud.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;

/**
 * Gestión de usuarios con el repositorio simulado.
 * Complementa a UsuarioServiceImplTest (que ya prueba el registro).
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplGestionTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UsuarioServiceImpl usuarioService;

    @Test
    @DisplayName("Actualizar el perfil cambia los datos personales, pero nunca el email, la contraseña ni el rol")
    void actualizarUsuario_existente_noPermiteEscalarRol() {
        // Arrange
        Usuario enBaseDeDatos = Usuario.builder()
                .id(1L).nombre("Ana").apellido("Pérez").email("ana@petsalud.test")
                .password("$2a$10$hashOriginal").direccion("Calle Antigua 1").rol(Rol.CLIENTE)
                .build();
        Usuario cambios = Usuario.builder()
                .nombre("Ana María").apellido("Pérez Soto").telefono("+56987654321").direccion("Av. Nueva 456")
                // intento de modificar datos protegidos:
                .email("otro@correo.test").password("otraClave").rol(Rol.ADMIN)
                .build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(enBaseDeDatos));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        // Act
        Usuario actualizado = usuarioService.actualizarUsuario(1L, cambios);

        // Assert
        assertAll(
                () -> assertEquals("Ana María", actualizado.getNombre()),
                () -> assertEquals("Pérez Soto", actualizado.getApellido()),
                () -> assertEquals("+56987654321", actualizado.getTelefono()),
                () -> assertEquals("Av. Nueva 456", actualizado.getDireccion()),
                () -> assertEquals("ana@petsalud.test", actualizado.getEmail()),
                () -> assertEquals("$2a$10$hashOriginal", actualizado.getPassword()),
                () -> assertEquals(Rol.CLIENTE, actualizado.getRol()));
    }

    @Test
    @DisplayName("No se puede actualizar un usuario que no existe")
    void actualizarUsuario_inexistente_lanzaError() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> usuarioService.actualizarUsuario(99L, new Usuario()));

        assertEquals("Usuario no encontrado", error.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Buscar, listar y eliminar usuarios usan el repositorio")
    void consultasYEliminacion_delegaEnElRepositorio() {
        Usuario ana = Usuario.builder().id(1L).email("ana@petsalud.test").rol(Rol.CLIENTE).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findByEmail("ana@petsalud.test")).thenReturn(Optional.of(ana));
        when(usuarioRepository.findAll()).thenReturn(List.of(ana));

        assertEquals(Optional.of(ana), usuarioService.obtenerUsuarioPorId(1L));
        assertEquals(Optional.of(ana), usuarioService.obtenerUsuarioPorEmail("ana@petsalud.test"));
        assertEquals(List.of(ana), usuarioService.listarUsuarios());

        usuarioService.eliminarUsuario(1L);
        verify(usuarioRepository).deleteById(1L);
    }
    
}