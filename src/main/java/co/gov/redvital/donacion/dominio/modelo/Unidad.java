package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Ejemplar individual de un componente, trazable de forma única. Su estado
 * solo cambia aplicando un evento que produjo el motor de transiciones: no hay
 * otro camino para escribirlo (DD, nota sobre estado_id).
 */
public final class Unidad {

    private final UUID id;
    private final UUID donacionId;
    private final String componente;
    private final String grupoSanguineo;
    private final UUID institucionCustodiaId;
    private final LocalDate fechaVencimiento;
    private final Integer volumenMl;
    private final Instant fechaCaptacion;
    private CodigoEstado estado;
    private Boolean apta;

    public Unidad(UUID id, UUID donacionId, String componente, String grupoSanguineo,
                  UUID institucionCustodiaId, CodigoEstado estado, Boolean apta,
                  LocalDate fechaVencimiento, Integer volumenMl, Instant fechaCaptacion) {
        this.id = Objects.requireNonNull(id);
        this.donacionId = Objects.requireNonNull(donacionId);
        this.componente = Objects.requireNonNull(componente);
        this.grupoSanguineo = Objects.requireNonNull(grupoSanguineo);
        this.institucionCustodiaId = Objects.requireNonNull(institucionCustodiaId);
        this.estado = estado;
        this.apta = apta;
        this.fechaVencimiento = Objects.requireNonNull(fechaVencimiento);
        this.volumenMl = volumenMl;
        this.fechaCaptacion = fechaCaptacion;
    }

    /** Unidad recién creada: todavía sin estado hasta aplicar el evento de alta. */
    public static Unidad nueva(UUID donacionId, String componente, String grupoSanguineo,
                               UUID institucionId, LocalDate fechaVencimiento, Integer volumenMl,
                               Instant fechaCaptacion) {
        return new Unidad(UUID.randomUUID(), donacionId, componente, grupoSanguineo, institucionId,
                null, null, fechaVencimiento, volumenMl, fechaCaptacion);
    }

    /**
     * Aplica un evento del motor. El veredicto {@code apta} solo se fija en las
     * transiciones de tamizaje (IN-08) y nunca se borra después.
     */
    public void aplicar(EventoUnidad evento) {
        if (!evento.unidadId().equals(id) || evento.estadoAnterior() != estado) {
            throw new IllegalStateException("El evento no corresponde al estado vigente de la unidad.");
        }
        if (evento.estadoAnterior() == CodigoEstado.EN_TAMIZAJE) {
            if (evento.estadoNuevo() == CodigoEstado.DISPONIBLE) {
                apta = Boolean.TRUE;
            } else if (evento.estadoNuevo() == CodigoEstado.NO_APTA) {
                apta = Boolean.FALSE;
            }
        }
        estado = evento.estadoNuevo();
    }

    public boolean venceAntesDe(LocalDate fecha) {
        return fechaVencimiento.isBefore(fecha);
    }

    public boolean custodiadaPor(UUID institucion) {
        return institucionCustodiaId.equals(institucion);
    }

    public UUID id() { return id; }
    public UUID donacionId() { return donacionId; }
    public String componente() { return componente; }
    public String grupoSanguineo() { return grupoSanguineo; }
    public UUID institucionCustodiaId() { return institucionCustodiaId; }
    public CodigoEstado estado() { return estado; }
    public Boolean apta() { return apta; }
    public LocalDate fechaVencimiento() { return fechaVencimiento; }
    public Integer volumenMl() { return volumenMl; }
    public Instant fechaCaptacion() { return fechaCaptacion; }
}
