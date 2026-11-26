package com.petsalud.backendpetsalud.service;

import java.util.ArrayList;
import java.util.List;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;

/**
 * Valida los datos obligatorios de un usuario antes de que participe en una acción
 * del sistema (una venta, una cita, el registro de una mascota).
 */
public final class ValidadorUsuario {

    private ValidadorUsuario() {
        // Clase utilitaria: no se instancia
    }

    // ===================== Semana 4: datos completos para vender =====================

    /** Devuelve los campos obligatorios que faltan. Lista vacía = usuario completo. */
    public static List<String> camposFaltantes(Usuario usuario) {
        List<String> faltantes = new ArrayList<>();
        if (estaVacio(usuario.getNombre())) faltantes.add("nombre");
        if (estaVacio(usuario.getApellido())) faltantes.add("apellido");
        if (estaVacio(usuario.getEmail())) faltantes.add("email");
        if (estaVacio(usuario.getDireccion())) faltantes.add("direccion");
        return faltantes;
    }

    /** Lanza IllegalArgumentException (el GlobalExceptionHandler la convierte en 400) si faltan datos. */
    public static void validarDatosCompletos(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("El usuario es obligatorio");
        }
        List<String> faltantes = camposFaltantes(usuario);
        if (!faltantes.isEmpty()) {
            throw new IllegalArgumentException(
                    "El usuario no tiene sus datos obligatorios completos. Faltan: " + String.join(", ", faltantes));
        }
    }

    // ===================== Semana 5: datos básicos y rol =====================

    /** Campos básicos de cualquier usuario: nombre, email y rol. Lista vacía = datos básicos completos. */
    public static List<String> camposBasicosFaltantes(Usuario usuario) {
        List<String> faltantes = new ArrayList<>();
        if (estaVacio(usuario.getNombre())) faltantes.add("nombre");
        if (estaVacio(usuario.getEmail())) faltantes.add("email");
        if (usuario.getRol() == null) faltantes.add("rol");
        return faltantes;
    }

    /**
     * Revisa nombre, email y rol. "tipo" indica quién es en el mensaje de error
     * (por ejemplo "veterinario" o "propietario").
     */
    public static void validarDatosBasicos(Usuario usuario, String tipo) {
        if (usuario == null) {
            throw new IllegalArgumentException("El " + tipo + " es obligatorio");
        }
        List<String> faltantes = camposBasicosFaltantes(usuario);
        if (!faltantes.isEmpty()) {
            throw new IllegalArgumentException(
                    "El " + tipo + " no tiene sus datos obligatorios completos. Faltan: " + String.join(", ", faltantes));
        }
    }

    /** Revisa que el usuario tenga el rol que corresponde a su función (por ejemplo, EMPLEADO para el veterinario). */
    public static void validarRol(Usuario usuario, Rol rolEsperado, String tipo) {
        if (usuario.getRol() != rolEsperado) {
            throw new IllegalArgumentException(
                    "El usuario indicado como " + tipo + " debe tener el rol " + rolEsperado);
        }
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}