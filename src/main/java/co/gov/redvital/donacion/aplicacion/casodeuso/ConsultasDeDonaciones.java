package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.ResolutorAlcance;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonaciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Donacion;

/** Consulta de una donación (U3 a U6, DD 15.5 grupo 4), filtrada por el alcance del token. */
public final class ConsultasDeDonaciones {

    private final RepositorioDonaciones donaciones;
    private final RepositorioUnidades unidades;
    private final ResolutorAlcance alcance;

    public ConsultasDeDonaciones(RepositorioDonaciones donaciones, RepositorioUnidades unidades,
                                 ResolutorAlcance alcance) {
        this.donaciones = donaciones;
        this.unidades = unidades;
        this.alcance = alcance;
    }

    public DonacionRegistrada consultar(Actor actor, java.util.UUID id) {
        Donacion d = donaciones.buscar(id).orElseThrow(NoEncontrado::new);
        if (!alcance.de(actor).incluye(d.institucionId())) {
            throw new NoEncontrado();
        }
        return new DonacionRegistrada(d, d.campaniaId() != null, unidades.deDonacion(id));
    }

    DonacionRegistrada reconstruir(java.util.UUID id) {
        Donacion d = donaciones.buscar(id).orElseThrow(NoEncontrado::new);
        return new DonacionRegistrada(d, d.campaniaId() != null, unidades.deDonacion(id));
    }
}
