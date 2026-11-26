package com.petsalud.backendpetsalud.repository;

import com.petsalud.backendpetsalud.entity.Recompensa;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;


public interface RecompensaRepository extends JpaRepository<Recompensa, Long> {
    List<Recompensa> findByUsuarioId(Long usuarioId);
}
