package com.petsalud.backendpetsalud.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.petsalud.backendpetsalud.entity.Mascota;

// Validadción clase utilitaria para validar datos obligatorios de una mascota
public class ValidadorMascotaTest {

    private static final LocalDate HACE_3_ANOS = LocalDate.now().minusYears(3);

    @Test
    @DisplayName("Mascota con nombre, especie, raza, fecha de nacimiento y sexo: está completa")
    void validarDatos_mascotaCompleta_noLanzaError() {
        // Arrange
        Mascota completa = mascota("Bambina", "Perro", "Mestizo", HACE_3_ANOS, "Hembra");

        // Act
        List<String> faltantes = ValidadorMascota.camposFaltantes(completa);

        // Assert
        assertTrue(faltantes.isEmpty());
        assertDoesNotThrow(() -> ValidadorMascota.validarDatos(completa));
    }

    @Test
    @DisplayName("Bug corregido: una mascota sin propietario en el JSON es válida (el dueño lo asigna el service)")
    void validarDatos_sinPropietario_esValida() {
        // Arrange
        Mascota sinDueno = mascota("Firulais", "Perro", "Mestizo", HACE_3_ANOS, "Macho");
        sinDueno.setPropietario(null);

        // Act + Assert
        assertDoesNotThrow(() -> ValidadorMascota.validarDatos(sinDueno));
    }


    @ParameterizedTest(name = "Mascota sin {0}: se detecta")
    @MethodSource("mascotasIncompletas")
    void camposFaltantes_faltaUnDato_loIdentifica(String campoFaltante, Mascota incompleta) {
        // Arrange: la mascota llega desde @MethodSource

        // Act
        List<String> faltantes = ValidadorMascota.camposFaltantes(incompleta);

        // Assert
        assertEquals(List.of(campoFaltante), faltantes);
    }

    static Stream<Arguments> mascotasIncompletas() {
        return Stream.of(
                Arguments.of("nombre",          mascota(null,       "Perro", "Mestizo", HACE_3_ANOS, "Macho")),
                Arguments.of("especie",         mascota("Firulais", "",      "Mestizo", HACE_3_ANOS, "Macho")),
                Arguments.of("raza",            mascota("Firulais", "Perro", "   ",     HACE_3_ANOS, "Macho")),
                Arguments.of("fechaNacimiento", mascota("Firulais", "Perro", "Mestizo", null,        "Macho")),
                Arguments.of("sexo",            mascota("Firulais", "Perro", "Mestizo", HACE_3_ANOS, null)));
    }



    @Test
    @DisplayName("Con varios datos faltantes, el error los nombra todos")
    void validarDatos_variosFaltantes_mensajeConDetalle() {
        // Arrange
        Mascota vacia = new Mascota();

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ValidadorMascota.validarDatos(vacia));

        // Assert
        assertEquals("La mascota no tiene sus datos obligatorios completos. "
                + "Faltan: nombre, especie, raza, fechaNacimiento, sexo", error.getMessage());
    }

    @Test
    @DisplayName("Caso límite: una mascota nacida hoy es válida (edad 0)")
    void validarDatos_nacidaHoy_esValida() {
        // Arrange
        Mascota recienNacida = mascota("Michi", "Gato", "Siamés", LocalDate.now(), "Hembra");

        // Act + Assert
        assertDoesNotThrow(() -> ValidadorMascota.validarDatos(recienNacida));
    }

    @Test
    @DisplayName("Caso límite: una fecha de nacimiento futura se rechaza (edad negativa)")
    void validarDatos_fechaFutura_lanzaError() {
        // Arrange
        Mascota delFuturo = mascota("Michi", "Gato", "Siamés", LocalDate.now().plusDays(1), "Hembra");

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ValidadorMascota.validarDatos(delFuturo));

        // Assert
        assertEquals("La fecha de nacimiento no puede ser posterior a la fecha actual", error.getMessage());
    }

    @Test
    @DisplayName("Una mascota nula no es válida")
    void validarDatos_mascotaNula_lanzaError() {
        // Arrange
        Mascota nula = null;

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ValidadorMascota.validarDatos(nula));

        // Assert
        assertEquals("Los datos de la mascota son obligatorios", error.getMessage());
    }

    // Datos de prueba

    private static Mascota mascota(String nombre, String especie, String raza, LocalDate nacimiento, String sexo) {
        return Mascota.builder()
                .nombre(nombre).especie(especie).raza(raza).fechaNacimiento(nacimiento).sexo(sexo)
                .build();
    }
    
}
