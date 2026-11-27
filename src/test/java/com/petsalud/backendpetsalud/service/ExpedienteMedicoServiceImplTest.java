package com.petsalud.backendpetsalud.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.petsalud.backendpetsalud.entity.ExpedienteMedico;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.exception.RecursoNoEncontradoException;
import com.petsalud.backendpetsalud.repository.ExpedienteMedicoRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;

//Entidad ExpedienteMédico: registros completos llevan descripción, diagnóstico y tratamientos
//firmados por un veterinario y vinculados a la mascota.
//Los repositorios se simulan con @Mock y se inyectan en el service con @InjectMocks.

@ExtendWith(MockitoExtension.class)
class ExpedienteMedicoServiceImplTest {

    @Mock private ExpedienteMedicoRepository expedienteMedicoRepository;
    @Mock private MascotaRepository mascotaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private ExpedienteMedicoServiceImpl expedienteService;

    @Captor private ArgumentCaptor<ExpedienteMedico> registroCaptor;

    private Usuario duena;
    private Usuario veterinario;
    private Mascota firulais;

    @BeforeEach
    void setUp() {
        duena = Usuario.builder().id(1L).nombre("Carla").email("carla@petsalud.test").rol(Rol.CLIENTE).build();
        veterinario = Usuario.builder().id(20L).nombre("Esteban").email("esteban@petsalud.test").rol(Rol.VETERINARIO).build();
        firulais = Mascota.builder().id(10L).nombre("Firulais").especie("Perro").propietario(duena).build();
    }

    // Registro completo 

    @Test
    @DisplayName("Un registro completo queda vinculado a la mascota, firmado por el veterinario y con fecha")
    void agregarRegistro_completo_seGuardaVinculado() {
        // Arrange
        prepararMascotaYVeterinario();
        when(expedienteMedicoRepository.save(any(ExpedienteMedico.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        // Act
        ExpedienteMedico guardado = expedienteService.agregarRegistro(10L, 20L, registroCompleto());

        // Assert
        assertAll("registro médico completo",
                () -> assertSame(firulais, guardado.getMascota()),
                () -> assertSame(veterinario, guardado.getVeterinario()),
                () -> assertNotNull(guardado.getFecha()),
                () -> assertEquals("Se rasca la oreja derecha", guardado.getDescripcion()),
                () -> assertEquals("Otitis externa", guardado.getDiagnostico()),
                () -> assertEquals("Gotas oticas cada 12 h por 7 dias", guardado.getTratamiento()));
    }

    @Test
    @DisplayName("Se ignora el id que venga en el JSON: no se puede sobrescribir un registro existente")
    void agregarRegistro_conIdEnElJson_loIgnora() {
        // Arrange
        prepararMascotaYVeterinario();
        when(expedienteMedicoRepository.save(any(ExpedienteMedico.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        ExpedienteMedico conId = registroCompleto();
        conId.setId(7L);

        // Act
        expedienteService.agregarRegistro(10L, 20L, conId);

        // Assert
        verify(expedienteMedicoRepository).save(registroCaptor.capture());
        assertNull(registroCaptor.getValue().getId());
    }

    @ParameterizedTest(name = "Registro sin {0}: se rechaza")
    @MethodSource("registrosIncompletos")
    void agregarRegistro_incompleto_seRechazaSinConsultarLaBD(String campoFaltante, ExpedienteMedico incompleto) {
        // Arrange: el registro incompleto llega desde @MethodSource

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(10L, 20L, incompleto));

        // Assert
        assertTrue(error.getMessage().endsWith("Faltan: " + campoFaltante));
        verifyNoInteractions(mascotaRepository, usuarioRepository, expedienteMedicoRepository);
    }

    static Stream<Arguments> registrosIncompletos() {
        return Stream.of(
                Arguments.of("descripcion", registro(null,                "Otitis externa", "Gotas oticas")),
                Arguments.of("diagnostico", registro("Se rasca la oreja", "   ",            "Gotas oticas")),
                Arguments.of("tratamiento", registro("Se rasca la oreja", "Otitis externa", "")));
    }

    @Test
    @DisplayName("Sin datos del registro no se consulta ni guarda nada")
    void agregarRegistro_nulo_seRechaza() {
        // Arrange
        ExpedienteMedico nulo = null;

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(10L, 20L, nulo));

        // Assert
        assertEquals("Los datos del registro médico son obligatorios", error.getMessage());
        verifyNoInteractions(mascotaRepository, usuarioRepository, expedienteMedicoRepository);
    }

    // Mascota y veterinario 

    @Test
    @DisplayName("Si la mascota no existe: 'no encontrada' (404) y no se guarda nada")
    void agregarRegistro_mascotaInexistente_lanzaNoEncontrado() {
        // Arrange
        when(mascotaRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> expedienteService.agregarRegistro(99L, 20L, registroCompleto()));

        // Assert
        assertEquals("Mascota no encontrada", error.getMessage());
        verify(expedienteMedicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si el veterinario no existe: 'no encontrado' (404) y no se guarda nada")
    void agregarRegistro_veterinarioInexistente_lanzaNoEncontrado() {
        // Arrange
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(firulais));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> expedienteService.agregarRegistro(10L, 99L, registroCompleto()));

        // Assert
        assertEquals("Veterinario no encontrado", error.getMessage());
        verify(expedienteMedicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un CLIENTE no puede firmar un registro médico: debe ser veterinario (VETERINARIO)")
    void agregarRegistro_firmanteNoVeterinario_seRechaza() {
        // Arrange
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(firulais));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(duena));

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(10L, 1L, registroCompleto()));

        // Assert
        assertEquals("El usuario indicado como veterinario debe tener el rol VETERINARIO", error.getMessage());
        verify(expedienteMedicoRepository, never()).save(any());
    }

    // Historial 

    @Test
    @DisplayName("Historial detallado: devuelve todas las consultas previas de la mascota")
    void listarRegistrosPorMascota_devuelveElHistorialCompleto() {
        // Arrange
        ExpedienteMedico primeraConsulta = registroCompleto();
        primeraConsulta.setMascota(firulais);
        ExpedienteMedico control = registro("Control post tratamiento", "Alta medica", "Sin tratamiento");
        control.setMascota(firulais);
        when(expedienteMedicoRepository.findByMascotaId(10L)).thenReturn(List.of(primeraConsulta, control));

        // Act
        List<ExpedienteMedico> historial = expedienteService.listarRegistrosPorMascota(10L);

        // Assert
        assertEquals(2, historial.size());
        assertTrue(historial.stream().allMatch(r -> r.getMascota() == firulais));
    }

    @Test
    @DisplayName("Buscar y eliminar registros se delega al repositorio")
    void obtenerYEliminar_delegaEnElRepositorio() {
        // Arrange
        ExpedienteMedico registro = registroCompleto();
        when(expedienteMedicoRepository.findById(7L)).thenReturn(Optional.of(registro));

        // Act
        Optional<ExpedienteMedico> encontrado = expedienteService.obtenerRegistroPorId(7L);
        expedienteService.eliminarRegistro(7L);

        // Assert
        assertEquals(Optional.of(registro), encontrado);
        verify(expedienteMedicoRepository).deleteById(7L);
    }

    // Datos de prueba 

    private void prepararMascotaYVeterinario() {
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(firulais));
        when(usuarioRepository.findById(20L)).thenReturn(Optional.of(veterinario));
    }

    private static ExpedienteMedico registroCompleto() {
        return registro("Se rasca la oreja derecha", "Otitis externa", "Gotas oticas cada 12 h por 7 dias");
    }

    private static ExpedienteMedico registro(String descripcion, String diagnostico, String tratamiento) {
        return ExpedienteMedico.builder()
                .descripcion(descripcion).diagnostico(diagnostico).tratamiento(tratamiento)
                .build();
    }
}