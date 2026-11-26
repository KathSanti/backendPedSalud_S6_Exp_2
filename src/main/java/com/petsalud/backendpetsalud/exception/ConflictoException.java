package com.petsalud.backendpetsalud.exception;

public class ConflictoException extends RuntimeException {

    // Recurso global para manejar excepciones de conflicto en la aplicación estado 409.
    public ConflictoException(String mensaje) {
        super(mensaje);
    }
    
}
