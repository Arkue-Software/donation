package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonaciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Donacion;

/** Consulta de una donación (U3, U4), filtrada por la institución del token. */
public final class ConsultasDeDonaciones {

    private final RepositorioDonaciones donaciones;
    private final RepositorioUnidades unidades;

    public ConsultasDeDonaciones(RepositorioDonaciones donaciones, RepositorioUnidades unidades) {
        this.donaciones = donaciones;
        this.unidades = unidades;
    }

    public DonacionRegistrada consultar(Actor actor, java.util.UUID id) {
        Donacion d = donaciones.buscar(id)
                .filter(x -> x.institucionId().equals(actor.institucion()))
                .orElseThrow(NoEncontrado::new);
        return new DonacionRegistrada(d, d.campaniaId() != null, unidades.deDonacion(id));
    }

    DonacionRegistrada reconstruir(java.util.UUID id) {
        Donacion d = donaciones.buscar(id).orElseThrow(NoEncontrado::new);
        return new DonacionRegistrada(d, d.campaniaId() != null, unidades.deDonacion(id));
    }
}
