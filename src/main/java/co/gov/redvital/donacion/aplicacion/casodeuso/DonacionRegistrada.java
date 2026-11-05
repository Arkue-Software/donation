package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.dominio.modelo.Donacion;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import java.util.List;

/**
 * Una donación con sus unidades. {@code campaniaConfirmada} es falso cuando la
 * campaña indicada no pudo confirmarse con la proyección local: la interfaz lo
 * declara como dato no disponible, sin inventar un valor (EC-08).
 */
public record DonacionRegistrada(Donacion donacion, boolean campaniaConfirmada, List<Unidad> unidades) {
}
