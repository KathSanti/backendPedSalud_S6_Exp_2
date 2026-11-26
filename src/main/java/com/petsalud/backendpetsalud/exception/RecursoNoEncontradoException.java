package com.petsalud.backendpetsalud.exception;

public class RecursoNoEncontradoException extends RuntimeException {

   // Recurso global para manejar excepciones de recursos no encontrados en la aplicación.
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
    
} 
