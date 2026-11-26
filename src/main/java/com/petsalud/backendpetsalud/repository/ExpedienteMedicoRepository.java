package com.petsalud.backendpetsalud.repository;

import com.petsalud.backendpetsalud.entity.ExpedienteMedico;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;


public interface ExpedienteMedicoRepository extends JpaRepository<ExpedienteMedico, Long> {
    List<ExpedienteMedico> findByMascotaId(Long mascotaId);
}
