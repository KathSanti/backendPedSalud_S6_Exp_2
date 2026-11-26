package com.petsalud.backendpetsalud.entity;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MascotaTest {

    @Test 
    @DisplayName ("Test Mascota con 3 años de edad devuelve 3")
    void getEdad_nacidoHace3Anios_devuelve3() {
        // Arrange
        Mascota bambina = Mascota.builder().fechaNacimiento(LocalDate.now().minusYears(3)).build();

        // Act
        Integer edad = bambina.getEdad();

        // Assert
        assertEquals(3, edad);
        
    }

    @Test
    @DisplayName("Caso límite: si mañana cumple 1 año, hoy todavía tiene 0")
    void getEdad_unDiaAntesDelCumpleanos_noRedondeaHaciaArriba() {
        // Arrange
        Mascota cachorro = Mascota.builder()
                .fechaNacimiento(LocalDate.now().minusYears(1).plusDays(1)).build();

        // Act
        Integer edad = cachorro.getEdad();

        // Assert
        assertEquals(0, edad);
    }

    @Test
    @DisplayName("Sin fecha de nacimiento no se puede calcular la edad")
    void getEdad_sinFechaNacimiento_devuelveNull() {
        // Arrange
        Mascota sinFecha = new Mascota();

        // Act
        Integer edad = sinFecha.getEdad();

        // Assert
        assertNull(edad);
    }
    
}
