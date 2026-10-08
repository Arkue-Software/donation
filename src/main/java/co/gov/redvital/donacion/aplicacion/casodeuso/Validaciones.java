package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import java.util.regex.Pattern;

/** Validaciones de forma compartidas. Los mensajes nombran el campo, nunca el valor recibido. */
final class Validaciones {

    private static final Pattern RUTA_MUNICIPIO = Pattern.compile("^/00/\\d{2}/\\d{5}$");
    private static final Pattern RUTA_TERRITORIO = Pattern.compile("^/00(/\\d{2}(/\\d{5})?)?$");

    private Validaciones() {
    }

    static void rutaMunicipio(String ruta, String campo) {
        if (ruta != null && !RUTA_MUNICIPIO.matcher(ruta).matches()) {
            throw new DatosInvalidos("El campo " + campo + " no es una ruta de municipio válida.");
        }
    }

    static void rutaTerritorio(String ruta, String campo) {
        if (ruta == null || !RUTA_TERRITORIO.matcher(ruta).matches()) {
            throw new DatosInvalidos("El campo " + campo + " no es una ruta territorial válida.");
        }
    }

    static String requerido(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new DatosInvalidos("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new DatosInvalidos("El campo " + campo + " supera la longitud permitida.");
        }
        return limpio;
    }

    static String opcional(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return requerido(valor, campo, maximo);
    }
}
