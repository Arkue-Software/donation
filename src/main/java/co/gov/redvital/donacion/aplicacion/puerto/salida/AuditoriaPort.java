package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.RegistroAuditoria;

/** Serie de auditoría del contexto de donación (ADR-016). Solo anexado. */
public interface AuditoriaPort {

    void registrar(RegistroAuditoria registro);
}
