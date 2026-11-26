package com.petsalud.backendpetsalud.entity;

public enum EstadoCita {
    AGENDADA,
    COMPLETADA,
    CANCELADA;

    // Método para verificar si se puede cambiar de estado y los estados siguientes son finales 
    public boolean puedeCambiarA(EstadoCita nuevoEstado) {
       return switch (this) {
            case AGENDADA -> nuevoEstado == COMPLETADA || nuevoEstado == CANCELADA;
            case COMPLETADA, CANCELADA -> false;
         
        };

    }

    
}
