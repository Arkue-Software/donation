package co.gov.redvital.donacion.dominio;

import static co.gov.redvital.donacion.dominio.modelo.ActorTipo.SISTEMA;
import static co.gov.redvital.donacion.dominio.modelo.ActorTipo.USUARIO;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.CAPTADA;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.DESECHADA;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.DESPACHADA;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.DISPONIBLE;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.EN_TAMIZAJE;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.FRACCIONADA;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.NO_APTA;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.RESERVADA;
import static co.gov.redvital.donacion.dominio.modelo.CodigoEstado.VENCIDA;

import co.gov.redvital.donacion.dominio.modelo.MatrizTransiciones;
import co.gov.redvital.donacion.dominio.modelo.Transicion;
import java.util.List;

/** Tabla 20 del DD V3.0, transcrita literalmente. Es el oráculo de las pruebas de la matriz (EC-36). */
public final class MatrizDelDD {

    public static final List<Transicion> TABLA_20 = List.of(
            new Transicion(1, CAPTADA, FRACCIONADA, USUARIO),
            new Transicion(2, FRACCIONADA, EN_TAMIZAJE, USUARIO),
            new Transicion(3, EN_TAMIZAJE, DISPONIBLE, USUARIO),
            new Transicion(4, EN_TAMIZAJE, NO_APTA, USUARIO),
            new Transicion(5, DISPONIBLE, RESERVADA, USUARIO),
            new Transicion(6, RESERVADA, DISPONIBLE, USUARIO),
            new Transicion(7, RESERVADA, DESPACHADA, USUARIO),
            new Transicion(8, DISPONIBLE, VENCIDA, SISTEMA),
            new Transicion(9, RESERVADA, VENCIDA, SISTEMA),
            new Transicion(10, FRACCIONADA, VENCIDA, SISTEMA),
            new Transicion(11, EN_TAMIZAJE, VENCIDA, SISTEMA),
            new Transicion(12, NO_APTA, DESECHADA, USUARIO),
            new Transicion(13, VENCIDA, DESECHADA, USUARIO));

    public static MatrizTransiciones matriz() {
        return new MatrizTransiciones(TABLA_20);
    }

    private MatrizDelDD() {
    }
}
