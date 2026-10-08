package co.gov.redvital.donacion.aplicacion.puerto.salida;

/** Resumen HMAC-SHA256 del documento. El documento en claro nunca se guarda ni se registra. */
public interface ResumidorDocumento {

    String resumir(String documento);
}
