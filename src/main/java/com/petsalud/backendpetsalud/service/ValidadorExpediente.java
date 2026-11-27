package com.petsalud.backendpetsalud.service;

import java.util.ArrayList;
import java.util.List;

import com.petsalud.backendpetsalud.entity.ExpedienteMedico;

public class ValidadorExpediente {

    private ValidadorExpediente() {
        // Clase utilitaria: no se instancia
    }

    // Devuelve los campos que faltan 
    public static List<String> camposFaltantes(ExpedienteMedico registro) {
        List<String> faltantes = new ArrayList<>();
        if (estaVacio(registro.getDescripcion())) faltantes.add("descripcion");
        if (estaVacio(registro.getDiagnostico())) faltantes.add("diagnostico");
        if (estaVacio(registro.getTratamiento())) faltantes.add("tratamiento");
        return faltantes;
    }

    // Lanza IllegalArgumentException (400) si el registro es nulo o está incompleto. 
    public static void validarDatos(ExpedienteMedico registro) {
        if (registro == null) {
            throw new IllegalArgumentException("Los datos del registro médico son obligatorios");
        }
        List<String> faltantes = camposFaltantes(registro);
        if (!faltantes.isEmpty()) {
            throw new IllegalArgumentException(
                    "El registro médico no está completo. Faltan: " + String.join(", ", faltantes));
        }
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }


    
}
