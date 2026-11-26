package com.petsalud.backendpetsalud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.entity.ExpedienteMedico;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.exception.RecursoNoEncontradoException;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;

@ExtendWith (MockitoExtension.class)
public class MascotaServiceImplTest {

    @Mock private MascotaRepository mascotaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private MascotaServiceImpl mascotaService;

    @Captor private ArgumentCaptor<Mascota> mascotaCaptor;

    private Usuario carla;

    @BeforeEach
    void setUp() {
        carla = Usuario.builder().id(1L).nombre("Carla").email("carla@petsalud.test").rol(Rol.CLIENTE).build();
    }

    //asiganamos propietario

    @Test
    @DisplayName("Bug corregido: registrar una mascota sin dueño en el JSON la asigna al propietario indicado")
    void registrarMascota_sinDuenoEnElJson_asignaPropietario() {
        // Arrange
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(carla));
        when(mascotaRepository.save(any(Mascota.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        // Act
        Mascota registrada = mascotaService.registrarMascota(mascotaNueva("Bambi", "Perro"), 1L);

        // Assert
        assertSame(carla, registrada.getPropietario());
        assertEquals("Bambi", registrada.getNombre());
    }

    @Test
    @DisplayName("Se ignoran el id y el dueño que vengan en el JSON (no se puede sobrescribir ni robar una mascota)")
    void registrarMascota_ignoraIdYDuenoDelJson() {
        // Arrange
        Mascota conTrampa = mascotaNueva("Bambi", "Perro");
        conTrampa.setId(5L);
        conTrampa.setPropietario(Usuario.builder().id(2L).rol(Rol.CLIENTE).build());
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(carla));
        when(mascotaRepository.save(any(Mascota.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        // Act
        mascotaService.registrarMascota(conTrampa, 1L);

        // Assert
        verify(mascotaRepository).save(mascotaCaptor.capture());
        assertNull(mascotaCaptor.getValue().getId());
        assertSame(carla, mascotaCaptor.getValue().getPropietario());
    }

    @Test
    @DisplayName("Si el propietario no existe: 'no encontrado' y no se guarda la mascota")
    void registrarMascota_propietarioInexistente_lanzaNoEncontrado() {
        // Arrange
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> mascotaService.registrarMascota(mascotaNueva("Bambi", "Perro"), 99L));

        // Assert
        assertEquals("Propietario no encontrado", error.getMessage());
        verify(mascotaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Solo un CLIENTE puede ser propietario de una mascota")
    void registrarMascota_propietarioNoCliente_seRechaza() {
        // Arrange
        Usuario esteban = Usuario.builder().id(20L).nombre("Esteban").rol(Rol.VETERINARIO).build();
        when(usuarioRepository.findById(20L)).thenReturn(Optional.of(esteban));

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> mascotaService.registrarMascota(mascotaNueva("Firulais", "Perro"), 20L));

        // Assert
        assertEquals("El usuario indicado como propietario debe tener el rol CLIENTE", error.getMessage());
        verify(mascotaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con datos incompletos se rechaza antes de consultar la BD")
    void registrarMascota_datosIncompletos_noConsultaLaBD() {
        // Arrange
        Mascota sinEspecie = mascotaNueva("Firulais", null);

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> mascotaService.registrarMascota(sinEspecie, 1L));

        // Assert
        assertTrue(error.getMessage().endsWith("Faltan: especie"));
        verifyNoInteractions(usuarioRepository, mascotaRepository);
    }

    @Test
    @DisplayName("Relación propietario–mascotas un mismo dueño puede tener varias mascotas")
    void registrarMascota_variasDelMismoDueno_todasLeQuedanAsignadas() {
        // Arrange
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(carla));
        when(mascotaRepository.save(any(Mascota.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        Mascota perro = mascotaService.registrarMascota(mascotaNueva("Firulais", "Perro"), 1L);
        Mascota gato = mascotaService.registrarMascota(mascotaNueva("Michi", "Gato"), 1L);
        when(mascotaRepository.findByPropietarioId(1L)).thenReturn(List.of(perro, gato));

        // Act
        List<Mascota> deCarla = mascotaService.listarMascotasPorPropietario(1L);

        // Assert
        assertEquals(2, deCarla.size());
        assertTrue(deCarla.stream().allMatch(m -> m.getPropietario() == carla));
    }

    // Edición y consistencia con citas y fichas 

    @Test
    @DisplayName("Bug corregido: editar cambia los datos de la mascota, pero nunca su dueño")
    void actualizarMascota_cambiaDatosPeroNoElDueno() {
        // Arrange
        Mascota existente = mascotaNueva("Firulais", "Perro");
        existente.setId(5L);
        existente.setPropietario(carla);
        Mascota cambios = mascotaNueva("Firulais Jr.", "Perro");   // sin propietario, como llega en el PUT
        cambios.setRaza("Labrador");
        when(mascotaRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(mascotaRepository.save(existente)).thenReturn(existente);

        // Act
        Mascota actualizada = mascotaService.actualizarMascota(5L, cambios);

        // Assert
        assertEquals("Firulais Jr.", actualizada.getNombre());
        assertEquals("Labrador", actualizada.getRaza());
        assertSame(carla, actualizada.getPropietario());
    }

    @Test
    @DisplayName("Consistencia: editar una mascota no le quita sus citas ni su expediente médico")
    void actualizarMascota_mantieneSusCitasYExpedientes() {
        // Arrange
        List<Cita> citas = List.of(Cita.builder().id(50L).build());
        List<ExpedienteMedico> expedientes = List.of(ExpedienteMedico.builder().id(7L).build());
        Mascota existente = mascotaNueva("Firulais", "Perro");
        existente.setId(5L);
        existente.setPropietario(carla);
        existente.setCitas(citas);
        existente.setExpedientesMedicos(expedientes);
        when(mascotaRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(mascotaRepository.save(existente)).thenReturn(existente);

        // Act
        Mascota actualizada = mascotaService.actualizarMascota(5L, mascotaNueva("Firulais", "Perro"));

        // Assert
        assertSame(citas, actualizada.getCitas());
        assertSame(expedientes, actualizada.getExpedientesMedicos());
    }

    @Test
    @DisplayName("No se puede editar una mascota que no existe - 404")
    void actualizarMascota_inexistente_lanzaNoEncontrado() {
        // Arrange
        when(mascotaRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> mascotaService.actualizarMascota(99L, mascotaNueva("Firulais", "Perro")));

        // Assert
        assertEquals("Mascota no encontrada", error.getMessage());
        verify(mascotaRepository, never()).save(any());
    }

    // Consultas 

    @Test
    @DisplayName("Buscar y eliminar mascotas se delega al repositorio")
    void obtenerYEliminar_delegaEnElRepositorio() {
        // Arrange
        Mascota firulais = mascotaNueva("Firulais", "Perro");
        when(mascotaRepository.findById(5L)).thenReturn(Optional.of(firulais));

        // Act
        Optional<Mascota> encontrada = mascotaService.obtenerMascotaPorId(5L);
        mascotaService.eliminarMascota(5L);

        // Assert
        assertEquals(Optional.of(firulais), encontrada);
        verify(mascotaRepository).deleteById(5L);
    }


   //Datos de prueba 
    private static Mascota mascotaNueva(String nombre, String especie) {
        return Mascota.builder()
                .nombre(nombre).especie(especie).raza("Mestizo")
                .fechaNacimiento(LocalDate.now().minusYears(3)).sexo("Macho")
                .build();
    }





    
}
