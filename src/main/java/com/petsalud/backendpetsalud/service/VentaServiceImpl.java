package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Producto;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.entity.Venta;
import com.petsalud.backendpetsalud.repository.ProductoRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import com.petsalud.backendpetsalud.repository.VentaRepository;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor 
public class VentaServiceImpl implements VentaService {


    private VentaRepository ventaRepository;
    private UsuarioRepository usuarioRepository;
    private ProductoRepository productoRepository;

    @Override
    @Transactional
    public Venta realizarVenta(Long usuarioId, List<Long> productosIds) {

        if (productosIds == null || productosIds.isEmpty()) {
            throw new IllegalArgumentException("La venta debe incluir al menos un producto");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
                
        ValidadorUsuario.validarDatosCompletos(usuario);
        //asociar un cliente en cada venta 
        ValidadorUsuario.validarRol(usuario, Rol.CLIENTE, "cliente");

        Map<Long, Long> unidadesPorProducto = productosIds.stream()
                .collect(Collectors.groupingBy(id -> id, LinkedHashMap::new, Collectors.counting()));

        List<Producto> encontrados = productoRepository.findAllById(new ArrayList<>(unidadesPorProducto.keySet()));
        if (encontrados.size() != unidadesPorProducto.size()) {
            throw new IllegalArgumentException("Algunos productos no fueron encontrados");
        }

        Map<Long, Producto> productoPorId = encontrados.stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        //Revisar el stock de TODOS los productos, sin modificar ninguno        
        for (Map.Entry<Long, Long> pedido : unidadesPorProducto.entrySet()) {
            Producto producto = productoPorId.get(pedido.getKey());
            if (producto.getStock() == null || producto.getStock() < pedido.getValue()) {
                throw new IllegalArgumentException("El producto " + producto.getNombre() + " no tiene stock disponible");
            }
        }

        // Ahora descuenta el stock
        for (Map.Entry<Long, Long> pedido : unidadesPorProducto.entrySet()) {
            Producto producto = productoPorId.get(pedido.getKey());
            producto.setStock(producto.getStock() - pedido.getValue().intValue());
            productoRepository.save(producto);
        }

        // Arma la venta y un producto repetido aparece y se cobra tantas veces como se pidió
        List<Producto> vendidos = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : productosIds) {
            Producto producto = productoPorId.get(id);
            vendidos.add(producto);
            total = total.add(producto.getPrecio());
        }

        Venta venta = Venta.builder()
                .usuario(usuario)
                .productos(vendidos)
                .total(total)
                .fecha(LocalDateTime.now())
                .build();

        return ventaRepository.save(venta);
    }

    @Override
    public Optional<Venta> obtenerVentaPorId(Long id) {
        return ventaRepository.findById(id);
    }

    @Override
    public List<Venta> listarVentasPorUsuario(Long usuarioId) {
        return ventaRepository.findByUsuarioId(usuarioId);
    }

    @Override
    public List<Venta> listarTodasLasVentas() {
        return ventaRepository.findAll();
    }
}

