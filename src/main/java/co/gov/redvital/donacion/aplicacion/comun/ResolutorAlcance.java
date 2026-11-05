package co.gov.redvital.donacion.aplicacion.comun;

import co.gov.redvital.donacion.aplicacion.puerto.salida.InstitucionesPort;
import co.gov.redvital.donacion.dominio.excepcion.FueraDeJurisdiccion;
import co.gov.redvital.donacion.dominio.excepcion.OperacionNoPermitida;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Alcance;

/**
 * Convierte la jurisdicción del token en el conjunto de instituciones que el
 * usuario puede leer. Las de una ruta salen de la proyección local de
 * instituciones (EV-07): ninguna consulta depende de otro servicio.
 */
public final class ResolutorAlcance {

    private final InstitucionesPort instituciones;

    public ResolutorAlcance(InstitucionesPort instituciones) {
        this.instituciones = instituciones;
    }

    public Alcance de(Actor actor) {
        if (actor.institucionId() != null) {
            return Alcance.de(actor.institucionId());
        }
        String ruta = actor.rutaTerritorial();
        if (ruta == null) {
            throw new OperacionNoPermitida();
        }
        return new Alcance(instituciones.dentroDe(ruta));
    }

    /**
     * Alcance de una ruta pedida explícitamente, que tiene que estar dentro de
     * la del usuario. Un usuario de ámbito institucional no pide rutas.
     */
    public Alcance deRuta(Actor actor, String rutaPedida) {
        String propia = actor.rutaTerritorial();
        if (propia == null || !(rutaPedida.equals(propia) || rutaPedida.startsWith(propia + "/"))) {
            throw new FueraDeJurisdiccion();
        }
        return new Alcance(instituciones.dentroDe(rutaPedida));
    }
}
