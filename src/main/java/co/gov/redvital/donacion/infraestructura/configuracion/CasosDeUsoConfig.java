package co.gov.redvital.donacion.infraestructura.configuracion;

import co.gov.redvital.donacion.aplicacion.casodeuso.ActualizarPerfilDonante;
import co.gov.redvital.donacion.aplicacion.casodeuso.AplicarCampaniaPublicada;
import co.gov.redvital.donacion.aplicacion.casodeuso.BuscarDonantePresente;
import co.gov.redvital.donacion.aplicacion.casodeuso.ConfirmarFraccionamiento;
import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultarIntencion;
import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultarInventario;
import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultasDeDonaciones;
import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultasDeUnidades;
import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultasDelDonante;
import co.gov.redvital.donacion.aplicacion.casodeuso.DepurarRegistrosTecnicos;
import co.gov.redvital.donacion.aplicacion.casodeuso.EvaluarAlertas;
import co.gov.redvital.donacion.aplicacion.casodeuso.GestionarAlertas;
import co.gov.redvital.donacion.aplicacion.casodeuso.GestionarConsentimientos;
import co.gov.redvital.donacion.aplicacion.casodeuso.GestionarUmbrales;
import co.gov.redvital.donacion.aplicacion.casodeuso.OperarUnidad;
import co.gov.redvital.donacion.aplicacion.casodeuso.ProcesarVencimientos;
import co.gov.redvital.donacion.aplicacion.casodeuso.PublicarBandeja;
import co.gov.redvital.donacion.aplicacion.casodeuso.RegistrarDonacion;
import co.gov.redvital.donacion.aplicacion.casodeuso.RegistrarDonante;
import co.gov.redvital.donacion.aplicacion.casodeuso.RegistrarIntencion;
import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.EjecutorIdempotente;
import co.gov.redvital.donacion.aplicacion.puerto.salida.AuditoriaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BloqueoConsultivoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ConsultaInventarioPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CuentasIdentidadPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.EventosProcesadosPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.HuellaPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.IdempotenciaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ProyeccionCampaniasPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.PublicadorMensajesPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioAlertas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioConsentimientos;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonaciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioEventosUnidad;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioIntenciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioReconocimientos;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUmbrales;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ResumidorDocumento;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.servicio.CalculadoraVencimiento;
import co.gov.redvital.donacion.dominio.servicio.OtorgadorReconocimientos;
import co.gov.redvital.donacion.dominio.servicio.PoliticaCampania;
import co.gov.redvital.donacion.dominio.servicio.PoliticaElegibilidad;
import co.gov.redvital.donacion.dominio.servicio.ReglaConfirmacion;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composición del núcleo. El dominio y los casos de uso no conocen Spring: se
 * construyen aquí con sus puertos, de modo que el núcleo se prueba con
 * implementaciones en memoria y se cambia un adaptador sin tocarlo.
 */
@Configuration
class CasosDeUsoConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId zonaHoraria(PropiedadesRedVital propiedades) {
        return ZoneId.of(propiedades.zonaHoraria());
    }

    @Bean
    PoliticaElegibilidad politicaElegibilidad() {
        return new PoliticaElegibilidad();
    }

    @Bean
    Auditor auditor(AuditoriaPort auditoria, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new Auditor(auditoria, unidadDeTrabajo, reloj);
    }

    @Bean
    EjecutorIdempotente ejecutorIdempotente(IdempotenciaPort idempotencia, HuellaPeticion huellas,
                                            UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new EjecutorIdempotente(idempotencia, huellas, unidadDeTrabajo, reloj);
    }

    @Bean
    RegistrarIntencion registrarIntencion(RepositorioIntenciones intenciones, CatalogoPort catalogo,
                                          EjecutorIdempotente idempotente, Auditor auditor, Clock reloj) {
        return new RegistrarIntencion(intenciones, catalogo, idempotente, auditor, new SecureRandom(), reloj);
    }

    @Bean
    ConsultarIntencion consultarIntencion(RepositorioIntenciones intenciones, UnidadDeTrabajo unidadDeTrabajo,
                                          Clock reloj) {
        return new ConsultarIntencion(intenciones, unidadDeTrabajo, reloj);
    }

    @Bean
    RegistrarDonante registrarDonante(RepositorioDonantes donantes, RepositorioConsentimientos consentimientos,
                                      CuentasIdentidadPort cuentas, ResumidorDocumento resumidor,
                                      IdempotenciaPort idempotencia, HuellaPeticion huellas,
                                      UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, Clock reloj) {
        return new RegistrarDonante(donantes, consentimientos, cuentas, resumidor, idempotencia, huellas,
                unidadDeTrabajo, auditor, reloj);
    }

    @Bean
    ConsultasDelDonante consultasDelDonante(RepositorioDonantes donantes, RepositorioDonaciones donaciones,
                                            RepositorioReconocimientos reconocimientos, CatalogoPort catalogo,
                                            PoliticaElegibilidad politica, ZoneId zona, Clock reloj) {
        return new ConsultasDelDonante(donantes, donaciones, reconocimientos, catalogo, politica, reloj.withZone(zona));
    }

    @Bean
    ActualizarPerfilDonante actualizarPerfilDonante(ConsultasDelDonante consultas, RepositorioDonantes donantes,
                                                    UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, Clock reloj) {
        return new ActualizarPerfilDonante(consultas, donantes, unidadDeTrabajo, auditor, reloj);
    }

    @Bean
    GestionarConsentimientos gestionarConsentimientos(ConsultasDelDonante consultas,
                                                      RepositorioConsentimientos consentimientos,
                                                      UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, Clock reloj) {
        return new GestionarConsentimientos(consultas, consentimientos, unidadDeTrabajo, auditor, reloj);
    }

    @Bean
    BuscarDonantePresente buscarDonantePresente(RepositorioDonantes donantes, ResumidorDocumento resumidor,
                                                PoliticaElegibilidad politica, UnidadDeTrabajo unidadDeTrabajo,
                                                Auditor auditor, ZoneId zona, Clock reloj) {
        return new BuscarDonantePresente(donantes, resumidor, politica, unidadDeTrabajo, auditor, reloj.withZone(zona));
    }

    @Bean
    ConsultasDeDonaciones consultasDeDonaciones(RepositorioDonaciones donaciones, RepositorioUnidades unidades) {
        return new ConsultasDeDonaciones(donaciones, unidades);
    }

    @Bean
    RegistrarDonacion registrarDonacion(RepositorioDonaciones donaciones, RepositorioUnidades unidades,
                                        RepositorioEventosUnidad eventos, RepositorioDonantes donantes,
                                        RepositorioIntenciones intenciones, RepositorioReconocimientos reconocimientos,
                                        ProyeccionCampaniasPort campanias, BandejaSalidaPort bandeja,
                                        CatalogoPort catalogo, EjecutorIdempotente idempotente, Auditor auditor,
                                        PoliticaElegibilidad elegibilidad, ConsultasDeDonaciones consultas,
                                        ZoneId zona, Clock reloj) {
        return new RegistrarDonacion(donaciones, unidades, eventos, donantes, intenciones, reconocimientos, campanias,
                bandeja, catalogo, idempotente, auditor, elegibilidad, new PoliticaCampania(),
                new OtorgadorReconocimientos(), new CalculadoraVencimiento(zona), consultas, zona, reloj);
    }

    @Bean
    ConfirmarFraccionamiento confirmarFraccionamiento(RepositorioDonaciones donaciones, RepositorioUnidades unidades,
                                                      RepositorioEventosUnidad eventos, CatalogoPort catalogo,
                                                      EjecutorIdempotente idempotente, ConsultasDeDonaciones consultas,
                                                      Auditor auditor, Clock reloj) {
        return new ConfirmarFraccionamiento(donaciones, unidades, eventos, catalogo, idempotente, consultas, auditor,
                reloj);
    }

    @Bean
    ConsultasDeUnidades consultasDeUnidades(RepositorioUnidades unidades, RepositorioEventosUnidad eventos,
                                            CatalogoPort catalogo) {
        return new ConsultasDeUnidades(unidades, eventos, catalogo);
    }

    @Bean
    OperarUnidad operarUnidad(RepositorioUnidades unidades, RepositorioEventosUnidad eventos, CatalogoPort catalogo,
                              UnidadDeTrabajo unidadDeTrabajo, EjecutorIdempotente idempotente, Auditor auditor,
                              ZoneId zona, Clock reloj) {
        return new OperarUnidad(unidades, eventos, catalogo, unidadDeTrabajo, idempotente, new ReglaConfirmacion(),
                auditor, zona, reloj);
    }

    @Bean
    ConsultarInventario consultarInventario(ConsultaInventarioPort inventario, CatalogoPort catalogo, Auditor auditor,
                                            ZoneId zona, Clock reloj) {
        return new ConsultarInventario(inventario, catalogo, auditor, zona, reloj);
    }

    @Bean
    GestionarUmbrales gestionarUmbrales(RepositorioUmbrales umbrales, CatalogoPort catalogo,
                                        UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, Clock reloj) {
        return new GestionarUmbrales(umbrales, catalogo, unidadDeTrabajo, auditor, reloj);
    }

    @Bean
    GestionarAlertas gestionarAlertas(RepositorioAlertas alertas, UnidadDeTrabajo unidadDeTrabajo, Auditor auditor,
                                      Clock reloj) {
        return new GestionarAlertas(alertas, unidadDeTrabajo, auditor, reloj);
    }

    @Bean
    EvaluarAlertas evaluarAlertas(RepositorioUmbrales umbrales, RepositorioAlertas alertas, RepositorioUnidades unidades,
                                  ConsultaInventarioPort inventario, BloqueoConsultivoPort bloqueo,
                                  UnidadDeTrabajo unidadDeTrabajo, ZoneId zona, Clock reloj) {
        return new EvaluarAlertas(umbrales, alertas, unidades, inventario, bloqueo, unidadDeTrabajo, zona, reloj);
    }

    @Bean
    ProcesarVencimientos procesarVencimientos(RepositorioUnidades unidades, RepositorioEventosUnidad eventos,
                                              CatalogoPort catalogo, BloqueoConsultivoPort bloqueo,
                                              UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, ZoneId zona,
                                              Clock reloj) {
        return new ProcesarVencimientos(unidades, eventos, catalogo, bloqueo, unidadDeTrabajo, auditor, zona, reloj);
    }

    @Bean
    PublicarBandeja publicarBandeja(BandejaSalidaPort bandeja, PublicadorMensajesPort publicador,
                                    BloqueoConsultivoPort bloqueo, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new PublicarBandeja(bandeja, publicador, bloqueo, unidadDeTrabajo, reloj);
    }

    @Bean
    AplicarCampaniaPublicada aplicarCampaniaPublicada(EventosProcesadosPort procesados,
                                                      ProyeccionCampaniasPort proyeccion, RepositorioDonantes donantes,
                                                      BandejaSalidaPort bandeja, UnidadDeTrabajo unidadDeTrabajo,
                                                      PropiedadesRedVital propiedades, Clock reloj) {
        return new AplicarCampaniaPublicada(procesados, proyeccion, donantes, bandeja, unidadDeTrabajo,
                propiedades.avisos().habilitados(), reloj);
    }

    @Bean
    DepurarRegistrosTecnicos depurarRegistrosTecnicos(BandejaSalidaPort bandeja, EventosProcesadosPort procesados,
                                                      IdempotenciaPort idempotencia, UnidadDeTrabajo unidadDeTrabajo,
                                                      Clock reloj) {
        return new DepurarRegistrosTecnicos(bandeja, procesados, idempotencia, unidadDeTrabajo, reloj);
    }
}
