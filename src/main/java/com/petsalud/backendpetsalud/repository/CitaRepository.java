package com.petsalud.backendpetsalud.repository;

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.entity.EstadoCita;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;


public interface CitaRepository extends JpaRepository<Cita, Long> {
    List<Cita> findByMascotaId(Long mascotaId);
    List<Cita> findByVeterinarioId(Long veterinarioId);
    List<Cita> findByFechaHoraBetween(LocalDateTime inicio, LocalDateTime fin);

    // ¿El veterinario tiene una cita (no cancelada) que empiece estrictamente entre "desde" y "hasta"?
    boolean existsByVeterinarioIdAndEstadoNotAndFechaHoraAfterAndFechaHoraBefore(
            Long veterinarioId, EstadoCita estado, LocalDateTime desde, LocalDateTime hasta);

    // Cuántas citas (no canceladas) tiene el veterinario entre "inicio" y "fin" (ambos incluidos).
    long countByVeterinarioIdAndEstadoNotAndFechaHoraBetween(
            Long veterinarioId, EstadoCita estado, LocalDateTime inicio, LocalDateTime fin);
}