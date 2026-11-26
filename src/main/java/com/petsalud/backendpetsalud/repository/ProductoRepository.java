package com.petsalud.backendpetsalud.repository;

import com.petsalud.backendpetsalud.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;


public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Optional<Producto> findByNombre(String nombre);
    List<Producto> findByCategoria(String categoria);
    List<Producto> findByStockLessThan(Integer cantidad);
}
