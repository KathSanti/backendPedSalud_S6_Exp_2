package com.petsalud.backendpetsalud.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.entity.EstadoCita;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.exception.ConflictoException;
import com.petsalud.backendpetsalud.exception.RecursoNoEncontradoException;
import com.petsalud.backendpetsalud.repository.CitaRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;

/**
 * Entidad Cita: programación, control de horarios (cruces y sobrecarga), cambio de estado
 * y relación con usuarios. Los repositorios se simulan con @Mock y se inyectan con @InjectMocks.
 */
@ExtendWith(MockitoExtension.class)
class CitaServiceImplTest {

    @Mock private CitaRepository citaRepository;
    @Mock private MascotaRepository mascotaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private CitaServiceImpl citaService;

    @Captor private ArgumentCaptor<Cita> citaCaptor;

    private Usuario duena;
    private Usuario veterinario;
    private Mascota bambina;
    private LocalDateTime enTresDias;

    @BeforeEach
    void setUp() {
        duena = usuario(1L, "Carla", "carla@petsalud.test", Rol.CLIENTE);
        veterinario = usuario(20L, "Esteban", "esteban@petsalud.test", Rol.VETERINARIO);
        bambina = Mascota.builder().id(10L).nombre("Bambina").especie("Gato").propietario(duena).build();
        enTresDias = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    // ---------- Programación correcta ----------

    @Test
    @DisplayName("Una cita válida se guarda AGENDADA, con su mascota, veterinario, fecha y motivo")
    void agendarCita_datosValidos_quedaAgendada() {
        // Arrange
        prepararAgendaLibre();

        // Act
        Cita cita = citaService.agendarCita(10L, 20L, enTresDias, "Vacuna anual");

        // Assert
        verify(citaRepository).save(citaCaptor.capture());
        Cita guardada = citaCaptor.getValue();
        assertAll("cita agendada",
                () -> assertEquals(EstadoCita.AGENDADA, guardada.getEstado()),
                () -> assertSame(bambina, guardada.getMascota()),
                () -> assertSame(veterinario, guardada.getVeterinario()),
                () -> assertEquals(enTresDias, guardada.getFechaHora()),
                () -> assertEquals("Vacuna anual", guardada.getMotivo()));
        assertSame(guardada, cita);
    }

    @Test
    @DisplayName("Relación con usuarios: la cita conecta al CLIENTE dueño con la mascota y con un veterinario")
    void agendarCita_relacionConUsuarios() {
        // Arrange
        prepararAgendaLibre();

        // Act
        Cita cita = citaService.agendarCita(10L, 20L, enTresDias, "Control");

        // Assert
        assertEquals(1L, cita.getMascota().getPropietario().getId());
        assertEquals(Rol.CLIENTE, cita.getMascota().getPropietario().getRol());
        assertEquals(Rol.VETERINARIO, cita.getVeterinario().getRol());
    }

    // ---------- Fecha: casos límite ----------

    @ParameterizedTest(name = "{0}: se rechaza")
    @MethodSource("fechasInvalidas")
    @DisplayName("Fechas inválidas se rechazan antes de consultar la BD")
    void agendarCita_fechaInvalida_seRechazaSinConsultarLaBD(String caso, LocalDateTime fecha, String mensaje) {
        // Arrange: la fecha inválida llega desde @MethodSource

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 20L, fecha, "Control"));

        // Assert
        assertTrue(error.getMessage().contains(mensaje));
        verifyNoInteractions(mascotaRepository, usuarioRepository, citaRepository);
    }

    static Stream<Arguments> fechasInvalidas() {
        LocalDateTime ahora = LocalDateTime.now();
        int max = CitaServiceImpl.DIAS_MAXIMOS_ANTICIPACION;
        return Stream.of(
                Arguments.of("Sin fecha", null, "obligatoria"),
                Arguments.of("Hace 1 minuto", ahora.minusMinutes(1), "fecha pasada"),
                Arguments.of("A " + max + " días y 1 minuto", ahora.plusDays(max).plusMinutes(1), max + " días"));
    }


    // Mascota y veterinario 

    @Test
    @DisplayName("Si la mascota no existe: 'no encontrada' y no se guarda nada")
    void agendarCita_mascotaInexistente_lanzaNoEncontrado() {
        // Arrange
        when(mascotaRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> citaService.agendarCita(99L, 20L, enTresDias, "Control"));

        // Assert
        assertEquals("Mascota no encontrada", error.getMessage());
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una mascota sin dueño registrado no puede tener citas")
    void agendarCita_mascotaSinPropietario_seRechaza() {
        // Arrange
        bambina.setPropietario(null);
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(bambina));

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 20L, enTresDias, "Control"));

        // Assert
        assertEquals("La mascota no tiene un propietario registrado", error.getMessage());
        verify(usuarioRepository, never()).findById(any());
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si el veterinario no existe: 'no encontrado' y no se guarda nada")
    void agendarCita_veterinarioInexistente_lanzaNoEncontrado() {
        // Arrange
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(bambina));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> citaService.agendarCita(10L, 99L, enTresDias, "Control"));

        // Assert
        assertEquals("Veterinario no encontrado", error.getMessage());
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un CLIENTE no puede ser asignado como veterinario de una cita")
    void agendarCita_veterinarioSinRolEmpleado_seRechaza() {
        // Arrange
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(bambina));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(duena));

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 1L, enTresDias, "Control"));

        // Assert
        assertEquals("El usuario indicado como veterinario debe tener el rol VETERINARIO", error.getMessage());
        verify(citaRepository, never()).save(any());
    }

    // Control de horarios: cruces 

    @Test
    @DisplayName("Control de horarios: una cita que se cruza con otra del mismo veterinario se rechaza (409)")
    void agendarCita_horarioQueSeCruza_lanzaConflicto() {
        // Arrange
        prepararMascotaYVeterinario();
        when(citaRepository.existsByVeterinarioIdAndEstadoNotAndFechaHoraAfterAndFechaHoraBefore(
                20L, EstadoCita.CANCELADA, enTresDias.minusMinutes(30), enTresDias.plusMinutes(30)))
                .thenReturn(true);

        // Act
        ConflictoException error = assertThrows(ConflictoException.class,
                () -> citaService.agendarCita(10L, 20L, enTresDias, "Control"));

        // Assert
        assertEquals("El veterinario ya tiene una cita que se cruza con ese horario", error.getMessage());
        verify(citaRepository, never()).save(any());

    }

    @Test
    @DisplayName("Se buscan cruces 30 min antes y 30 min después, para evitar sobrecarga sin contar las citas CANCELADAS")
    void agendarCita_buscaCrucesEnLaVentanaDeLaCita() {
        // Arrange
        prepararAgendaLibre();

        // Act
        citaService.agendarCita(10L, 20L, enTresDias, "Control");

        // Assert
        verify(citaRepository).existsByVeterinarioIdAndEstadoNotAndFechaHoraAfterAndFechaHoraBefore(
                20L,
                EstadoCita.CANCELADA,
                enTresDias.minusMinutes(CitaServiceImpl.DURACION_MINUTOS),
                enTresDias.plusMinutes(CitaServiceImpl.DURACION_MINUTOS));
    }

    // Control de horarios: sobrecarga 

    @Test
    @DisplayName("Sobrecarga: con la agenda del día llena (8 citas) no se agenda otra (409)")
    void agendarCita_agendaDelDiaLlena_lanzaConflicto() {
        // Arrange
        prepararMascotaYVeterinario();
        when(citaRepository.countByVeterinarioIdAndEstadoNotAndFechaHoraBetween(
                20L, EstadoCita.CANCELADA,
                enTresDias.toLocalDate().atStartOfDay(), enTresDias.toLocalDate().atTime(23, 59, 59)))
                .thenReturn((long) CitaServiceImpl.MAX_CITAS_POR_DIA);

        // Act
        ConflictoException error = assertThrows(ConflictoException.class,
                () -> citaService.agendarCita(10L, 20L, enTresDias, "Control"));

        // Assert
        assertTrue(error.getMessage().contains("agenda completa"));
        verify(citaRepository, never()).save(any());
    }

 
    //Gestión de estado

    @Test
    @DisplayName("Una cita AGENDADA puede pasar a COMPLETADA")
    void actualizarEstado_agendadaACompletada_seGuarda() {
        // Arrange
        Cita cita = citaEnEstado(EstadoCita.AGENDADA);
        when(citaRepository.findById(50L)).thenReturn(Optional.of(cita));
        when(citaRepository.save(cita)).thenReturn(cita);

        // Act
        Cita actualizada = citaService.actualizarEstadoCita(50L, EstadoCita.COMPLETADA);

        // Assert
        assertEquals(EstadoCita.COMPLETADA, actualizada.getEstado());
    }

    @Test
    @DisplayName("Una cita COMPLETADA no se puede cancelar (409) y su estado no cambia")
    void actualizarEstado_completadaACancelada_lanzaConflicto() {
        // Arrange
        Cita cita = citaEnEstado(EstadoCita.COMPLETADA);
        when(citaRepository.findById(50L)).thenReturn(Optional.of(cita));

        // Act
        ConflictoException error = assertThrows(ConflictoException.class,
                () -> citaService.actualizarEstadoCita(50L, EstadoCita.CANCELADA));

        // Assert
        assertEquals("No se puede cambiar una cita COMPLETADA a CANCELADA", error.getMessage());
        assertEquals(EstadoCita.COMPLETADA, cita.getEstado());
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("El nuevo estado es obligatorio")
    void actualizarEstado_nulo_seRechaza() {
        // Arrange
        EstadoCita sinEstado = null;

        // Act
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> citaService.actualizarEstadoCita(50L, sinEstado));

        // Assert
        assertEquals("El nuevo estado es obligatorio", error.getMessage());
        verifyNoInteractions(citaRepository);
    }

    @Test
    @DisplayName("No se puede cambiar el estado de una cita que no existe (404)")
    void actualizarEstado_citaInexistente_lanzaNoEncontrado() {
        // Arrange
        when(citaRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> citaService.actualizarEstadoCita(99L, EstadoCita.COMPLETADA));

        // Assert
        assertEquals("Cita no encontrada", error.getMessage());
    }

    @Test
    @DisplayName("Cancelar no borra la cita la deja CANCELADA para conservar el historial")
    void cancelarCita_marcaCanceladaSinBorrar() {
        // Arrange
        Cita cita = citaEnEstado(EstadoCita.AGENDADA);
        when(citaRepository.findById(50L)).thenReturn(Optional.of(cita));
        when(citaRepository.save(cita)).thenReturn(cita);

        // Act
        citaService.cancelarCita(50L);

        // Assert
        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
        verify(citaRepository, never()).deleteById(any());
        verify(citaRepository, never()).delete(any());
    }

    // Consultas 

    @Test
    @DisplayName("4 métodos de consulta del service le pidan los datos al método correcto del repository y devuelvan lo mismo que el repository")
    void consultas_delegaEnElRepositorio() {
        // Arrange
        Cita cita = citaEnEstado(EstadoCita.AGENDADA);
        LocalDateTime inicio = enTresDias.minusDays(1);
        LocalDateTime fin = enTresDias.plusDays(1);
        when(citaRepository.findById(50L)).thenReturn(Optional.of(cita));
        when(citaRepository.findByMascotaId(10L)).thenReturn(List.of(cita));
        when(citaRepository.findByVeterinarioId(20L)).thenReturn(List.of(cita));
        when(citaRepository.findByFechaHoraBetween(inicio, fin)).thenReturn(List.of(cita));

        // Act
        Optional<Cita> porId = citaService.obtenerCitaPorId(50L);
        List<Cita> porMascota = citaService.listarCitasPorMascota(10L);
        List<Cita> porVeterinario = citaService.listarCitasPorVeterinario(20L);
        List<Cita> entreFechas = citaService.listarCitasEntreFechas(inicio, fin);

        // Assert
        assertEquals(Optional.of(cita), porId);
        assertEquals(List.of(cita), porMascota);
        assertEquals(List.of(cita), porVeterinario);
        assertEquals(List.of(cita), entreFechas);
    }

    // Datos de prueba 

    // La mascota (con dueña) y el veterinario existen en la BD simulada.
    private void prepararMascotaYVeterinario() {
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(bambina));
        when(usuarioRepository.findById(20L)).thenReturn(Optional.of(veterinario));
    }

    // Si no hay cruces de horario, save() devuelve la cita que recibe.
    private void prepararAgendaLibre() {
        prepararMascotaYVeterinario();
        when(citaRepository.existsByVeterinarioIdAndEstadoNotAndFechaHoraAfterAndFechaHoraBefore(
                anyLong(), eq(EstadoCita.CANCELADA), any(), any()))
                .thenReturn(false);
        when(citaRepository.save(any(Cita.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private Cita citaEnEstado(EstadoCita estado) {
        return Cita.builder().id(50L).mascota(bambina).veterinario(veterinario)
                .fechaHora(enTresDias).motivo("Control").estado(estado).build();
    }

    private static Usuario usuario(Long id, String nombre, String email, Rol rol) {
        return Usuario.builder().id(id).nombre(nombre).email(email).rol(rol).build();
    }
}