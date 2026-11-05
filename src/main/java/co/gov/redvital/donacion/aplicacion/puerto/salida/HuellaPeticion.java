package co.gov.redvital.donacion.aplicacion.puerto.salida;

/** Resumen SHA-256 de la forma canónica de un cuerpo de petición. */
public interface HuellaPeticion {

    String calcular(Object cuerpo);
}
