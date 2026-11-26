package com.petsalud.backendpetsalud.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "mascotas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mascota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank (message = "El nombre de la mascota es obligatorio")
    private String nombre;

    @NotBlank (message = "La especie de la mascota es obligatoria")
    private String especie;

    @NotBlank 
    private String raza;

    @NotBlank (message = "La Fecha de nacimiento de la mascota es obligatoria")
    private LocalDate fechaNacimiento;

    private String sexo;

    // Relaciones
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario propietario;

    @JsonIgnore
    @OneToMany(mappedBy = "mascota", cascade = CascadeType.ALL)
    private List<Cita> citas;

    @JsonIgnore
    @OneToMany(mappedBy = "mascota", cascade = CascadeType.ALL)
    private List<ExpedienteMedico> expedientesMedicos;

    // Calculo de la edad 
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer getEdad() {
        if (fechaNacimiento == null) {
            return null;
        }
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }
}
