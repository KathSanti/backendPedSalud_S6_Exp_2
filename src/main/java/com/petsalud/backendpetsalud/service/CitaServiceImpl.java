package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.entity.EstadoCita;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.exception.ConflictoException;
import com.petsalud.backendpetsalud.exception.RecursoNoEncontradoException;
import com.petsalud.backendpetsalud.repository.CitaRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;

import lombok.AllArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class CitaServiceImpl implements CitaService {

    // Política de la clínica: las citas se agendan con hasta 30 días de anticipación.
    static final int DIAS_MAXIMOS_ANTICIPACION = 30;
    static final int DURACION_MINUTOS = 30;
    static final int MAX_CITAS_POR_DIA = 8;

    private CitaRepository citaRepository;

    private MascotaRepository mascotaRepository;

    private UsuarioRepository usuarioRepository;

    @Override
    public Cita agendarCita(Long mascotaId, Long veterinarioId, LocalDateTime fechaHora, String motivo) {
        // La fecha debe ser futura y dentro del máximo de anticipación (no consulta la BD)
        validarFecha(fechaHora);

        // La mascota existe y tiene un propietario registrado con sus datos básicos
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada"));
        if (mascota.getPropietario() == null) {
            throw new IllegalArgumentException("La mascota no tiene un propietario registrado");
        }
        ValidadorUsuario.validarDatosBasicos(mascota.getPropietario(), "propietario");

        // El veterinario existe, tiene el rol EMPLEADO y sus datos básicos
        Usuario veterinario = usuarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Veterinario no encontrado"));
        ValidadorUsuario.validarRol(veterinario, Rol.VETERINARIO,"veterinario");
        ValidadorUsuario.validarDatosBasicos(veterinario, "veterinario");

        // El veterinario no tiene otra cita a esa hora (las canceladas no cuentan)
        validarHorarioDisponible(veterinarioId, fechaHora);

        // la cita nace en estado AGENDADA
        Cita cita = Cita.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaHora(fechaHora)
                .motivo(motivo)
                .estado(EstadoCita.AGENDADA)
                .build();

        return citaRepository.save(cita);
    }

    @Override
    public Optional<Cita> obtenerCitaPorId(Long id) {
        return citaRepository.findById(id);
    }

    @Override
    public Cita actualizarEstadoCita(Long id, EstadoCita nuevoEstado) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("El nuevo estado es obligatorio");
        }
        Cita cita = citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada"));

        if (!cita.getEstado().puedeCambiarA(nuevoEstado)) {
            throw new ConflictoException(
                    "No se puede cambiar una cita " + cita.getEstado() + " a " + nuevoEstado);
        }
        cita.setEstado(nuevoEstado);
        return citaRepository.save(cita);
    }

    //Cancelar ya no borra la cita: la marca como CANCELADA para mantener el historial y liberar el horario.
    @Override
    public void cancelarCita(Long id) {
        actualizarEstadoCita(id, EstadoCita.CANCELADA);
    }

    @Override
    public List<Cita> listarCitasPorMascota(Long mascotaId) {
        return citaRepository.findByMascotaId(mascotaId);
    }

    @Override
    public List<Cita> listarCitasPorVeterinario(Long veterinarioId) {
        return citaRepository.findByVeterinarioId(veterinarioId);
    }

    @Override
    public List<Cita> listarCitasEntreFechas(LocalDateTime inicio, LocalDateTime fin) {
        return citaRepository.findByFechaHoraBetween(inicio, fin);
    }

    private void validarFecha(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            throw new IllegalArgumentException("La fecha y hora de la cita es obligatoria");
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (!fechaHora.isAfter(ahora)) {
            throw new IllegalArgumentException("No se puede agendar una cita en una fecha pasada");
        }
        if (fechaHora.isAfter(ahora.plusDays(DIAS_MAXIMOS_ANTICIPACION))) {
            throw new IllegalArgumentException("Solo se pueden agendar citas con hasta "
                    + DIAS_MAXIMOS_ANTICIPACION + " días de anticipación");
        }
    }

    private void validarHorarioDisponible(Long veterinarioId, LocalDateTime fechaHora) {
        LocalDateTime desde = fechaHora.minusMinutes(DURACION_MINUTOS);
        LocalDateTime hasta = fechaHora.plusMinutes(DURACION_MINUTOS);
        if (citaRepository.existsByVeterinarioIdAndEstadoNotAndFechaHoraAfterAndFechaHoraBefore(
                veterinarioId, EstadoCita.CANCELADA, desde, hasta)) {
            throw new ConflictoException("El veterinario ya tiene una cita que se cruza con ese horario");
        }

        LocalDate dia = fechaHora.toLocalDate();
        long citasDelDia = citaRepository.countByVeterinarioIdAndEstadoNotAndFechaHoraBetween(
                veterinarioId, EstadoCita.CANCELADA, dia.atStartOfDay(), dia.atTime(23, 59, 59));
        if (citasDelDia >= MAX_CITAS_POR_DIA) {
            throw new ConflictoException("El veterinario ya tiene su agenda completa ese día (máximo "
                    + MAX_CITAS_POR_DIA + " citas)");
        }
}
}