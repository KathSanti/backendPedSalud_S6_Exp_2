package com.petsalud.backendpetsalud.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;

/**
 * Paso 2 – Entidad Usuario: datos obligatorios completos (nombre, apellido, email y dirección).
 * Prueba unitaria pura: no levanta Spring ni usa base de datos.
 */
class ValidadorUsuarioTest {

    @Test
    @DisplayName("Usuario con nombre, apellido, email y dirección: está completo")
    void camposFaltantes_usuarioCompleto_listaVacia() {
        // Arrange
        Usuario ana = usuario("Ana", "Pérez", "ana@petsalud.test", "Av. Siempre Viva 123");

        // Act
        List<String> faltantes = ValidadorUsuario.camposFaltantes(ana);

        // Assert
        assertTrue(faltantes.isEmpty());
        assertDoesNotThrow(() -> ValidadorUsuario.validarDatosCompletos(ana));
    }

    @ParameterizedTest(name = "Si falta {0}, el usuario está incompleto")
    @MethodSource("usuariosConUnDatoFaltante")
    void camposFaltantes_faltaUnDato_loIdentifica(String campoFaltante, Usuario usuario) {
        assertEquals(List.of(campoFaltante), ValidadorUsuario.camposFaltantes(usuario));
    }

    static Stream<Arguments> usuariosConUnDatoFaltante() {
        return Stream.of(
                Arguments.of("nombre",    usuario(null,  "Pérez", "ana@petsalud.test", "Av. Siempre Viva 123")),
                Arguments.of("apellido",  usuario("Ana", null,    "ana@petsalud.test", "Av. Siempre Viva 123")),
                Arguments.of("email",     usuario("Ana", "Pérez", null,                "Av. Siempre Viva 123")),
                Arguments.of("direccion", usuario("Ana", "Pérez", "ana@petsalud.test", null)));
    }

    @Test
    @DisplayName("Textos vacíos o con solo espacios también cuentan como datos faltantes")
    void camposFaltantes_textosEnBlanco_cuentanComoFaltantes() {
        Usuario usuario = usuario("", "   ", "ana@petsalud.test", "\t");

        assertEquals(List.of("nombre", "apellido", "direccion"), ValidadorUsuario.camposFaltantes(usuario));
    }

    @Test
    @DisplayName("Con datos incompletos se lanza un error que nombra todos los campos faltantes")
    void validarDatosCompletos_sinDatos_lanzaErrorConDetalle() {
        Usuario sinDatos = Usuario.builder().id(1L).rol(Rol.CLIENTE).build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ValidadorUsuario.validarDatosCompletos(sinDatos));

        assertEquals("El usuario no tiene sus datos obligatorios completos. Faltan: nombre, apellido, email, direccion",
                error.getMessage());
    }

    @Test
    @DisplayName("Un usuario nulo no es válido")
    void validarDatosCompletos_usuarioNulo_lanzaError() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ValidadorUsuario.validarDatosCompletos(null));

        assertEquals("El usuario es obligatorio", error.getMessage());
    }

    private static Usuario usuario(String nombre, String apellido, String email, String direccion) {
        return Usuario.builder()
                .id(1L).nombre(nombre).apellido(apellido).email(email)
                .direccion(direccion).rol(Rol.CLIENTE)
                .build();
    }
}