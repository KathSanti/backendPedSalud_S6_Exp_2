package com.petsalud.backendpetsalud.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.petsalud.backendpetsalud.entity.Mascota;

// Valida los datos obligatorios de una mascota nombre, especie, raza, fecha de nacimiento y sexo.
public final class ValidadorMascota {

    private ValidadorMascota() {
        // Clase utilitaria: no se instancia
    }

    /** Devuelve los campos obligatorios que faltan. Lista vacía = mascota válida. */
    public static List<String> camposFaltantes(Mascota mascota) {
        List<String> faltantes = new ArrayList<>();
        if (estaVacio(mascota.getNombre())) faltantes.add("nombre");
        if (estaVacio(mascota.getEspecie())) faltantes.add("especie");
        if (estaVacio(mascota.getRaza())) faltantes.add("raza");
        if (mascota.getPropietario() == null) faltantes.add("propietario");
        if (mascota.getFechaNacimiento() == null) faltantes.add("fechaNacimiento");
        if (estaVacio(mascota.getSexo())) faltantes.add("sexo");
        return faltantes;
    }

    // Lanza IllegalArgumentException (400) si la mascota es nula o le faltan datos. 
    public static void validarDatos(Mascota mascota) {
        if (mascota == null) {
            throw new IllegalArgumentException("Los datos de la mascota son obligatorios");
        }
        List<String> faltantes = camposFaltantes(mascota);
        if (!faltantes.isEmpty()) {
            throw new IllegalArgumentException(
                    "La mascota no tiene sus datos obligatorios completos. Faltan: " + String.join(", ", faltantes));
        }
        if (mascota.getFechaNacimiento().isAfter(LocalDate.now())){
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser posterior a la fecha actual");
        }
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}