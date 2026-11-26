package com.petsalud.backendpetsalud.repository;

import com.petsalud.backendpetsalud.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;


public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioId(Long usuarioId);
    List<Notificacion> findByLeidaFalseAndUsuarioId(Long usuarioId);
}
