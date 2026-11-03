package co.gov.redvital.donacion.aplicacion.casodeuso;

import java.util.UUID;

/**
 * Reevalúa las alertas de una institución en cuanto cambian sus existencias o
 * sus umbrales, sin esperar al proceso programado de cada cinco minutos.
 */
@FunctionalInterface
public interface ReevaluadorAlertas {

    void reevaluar(UUID institucion);
}
