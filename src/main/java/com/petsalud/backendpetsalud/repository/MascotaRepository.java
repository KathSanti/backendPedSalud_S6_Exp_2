package com.petsalud.backendpetsalud.repository;

import com.petsalud.backendpetsalud.entity.Mascota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface MascotaRepository extends JpaRepository<Mascota, Long> {
    List<Mascota> findByPropietarioId(Long propietarioId);
}
