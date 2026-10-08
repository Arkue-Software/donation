package co.gov.redvital.donacion.infraestructura.programacion;

import co.gov.redvital.donacion.aplicacion.casodeuso.DepurarRegistrosTecnicos;
import co.gov.redvital.donacion.aplicacion.casodeuso.EvaluarAlertas;
import co.gov.redvital.donacion.aplicacion.casodeuso.ProcesarVencimientos;
import co.gov.redvital.donacion.aplicacion.casodeuso.PublicarBandeja;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Procesos del sistema. Cada uno toma un bloqueo consultivo dentro de su caso
 * de uso, así que con varias instancias del servicio corre en una sola a la vez.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "redvital.procesos.habilitados", havingValue = "true", matchIfMissing = true)
class ProcesosProgramados {

    private static final Logger LOG = LoggerFactory.getLogger(ProcesosProgramados.class);

    private final PublicarBandeja publicar;
    private final ProcesarVencimientos vencimientos;
    private final EvaluarAlertas alertas;
    private final DepurarRegistrosTecnicos depurar;

    ProcesosProgramados(PublicarBandeja publicar, ProcesarVencimientos vencimientos, EvaluarAlertas alertas,
                        DepurarRegistrosTecnicos depurar, MeterRegistry metricas) {
        this.publicar = publicar;
        this.vencimientos = vencimientos;
        this.alertas = alertas;
        this.depurar = depurar;
        Gauge.builder("redvital_bandeja_pendiente_mas_viejo_segundos", publicar,
                        PublicarBandeja::antiguedadPendienteMasViejo)
                .description("Antigüedad del evento pendiente más viejo de la bandeja de salida")
                .register(metricas);
    }

    /** Cada segundo, hasta cien eventos (DD 5.3.6). */
    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    void publicarBandeja() {
        ejecutarSeguro("publicación de la bandeja", publicar::ejecutar);
    }

    /** Cada cinco minutos: ninguna unidad vencida figura como disponible más de quince minutos (EC-13). */
    @Scheduled(cron = "0 */5 * * * *")
    void procesarVencimientos() {
        ejecutarSeguro("vencimiento de unidades", vencimientos::ejecutar);
    }

    @Scheduled(cron = "30 */5 * * * *")
    void evaluarAlertas() {
        ejecutarSeguro("evaluación de alertas", alertas::ejecutar);
    }

    @Scheduled(cron = "0 17 * * * *")
    void depurarRegistrosTecnicos() {
        ejecutarSeguro("depuración de registros técnicos", depurar::ejecutar);
    }

    private static void ejecutarSeguro(String nombre, Runnable proceso) {
        try {
            proceso.run();
        } catch (RuntimeException e) {
            LOG.warn("Falló el ciclo de {}; se reintentará en el siguiente: {}", nombre, e.getMessage());
        }
    }
}
