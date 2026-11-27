package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.ExpedienteMedico;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.ExpedienteMedicoRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor 
public class ExpedienteMedicoServiceImpl implements ExpedienteMedicoService {

    private ExpedienteMedicoRepository expedienteMedicoRepository;

    private MascotaRepository mascotaRepository;

    private UsuarioRepository usuarioRepository;

    @Override
    public ExpedienteMedico agregarRegistro(Long mascotaId, Long veterinarioId, ExpedienteMedico registro) {

        //traer descripción, diagnostico y tratamiento
        ValidadorExpediente.validarDatos(registro);

        // Validar que la mascota existe
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        // Validar que el veterinario existe y tiene rol de VETERINARIO
        Usuario veterinario = usuarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new IllegalArgumentException("Veterinario no encontrado"));
            ValidadorUsuario.validarRol(veterinario, Rol.VETERINARIO, "veterinario");
            ValidadorUsuario.validarDatosBasicos(veterinario, "veterinario");  

        //id ignorado en json 
        registro.setId(null);    
        registro.setMascota(mascota);
        registro.setVeterinario(veterinario);
        registro.setFecha(LocalDateTime.now());

        return expedienteMedicoRepository.save(registro);
    }

    @Override
    public Optional<ExpedienteMedico> obtenerRegistroPorId(Long id) {
        return expedienteMedicoRepository.findById(id);
    }

    @Override
    public List<ExpedienteMedico> listarRegistrosPorMascota(Long mascotaId) {
        return expedienteMedicoRepository.findByMascotaId(mascotaId);
    }

    @Override
    public void eliminarRegistro(Long id) {
        expedienteMedicoRepository.deleteById(id);
    }
}
