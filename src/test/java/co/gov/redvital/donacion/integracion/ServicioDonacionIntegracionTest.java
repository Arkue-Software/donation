package co.gov.redvital.donacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.gov.redvital.donacion.aplicacion.casodeuso.PublicarIndicadores;
import co.gov.redvital.donacion.aplicacion.casodeuso.ProcesarVencimientos;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ProyeccionCampaniasPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recorridos completos del servicio contra PostgreSQL y Kafka reales. Cada
 * prueba usa su propia institución para no depender del orden de ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServicioDonacionIntegracionTest {

    private static final String CAMPANIA_PUBLICADA = "redvital.campanias.campaign-published.v1";
    private static final String DONACION_COMPLETADA = "redvital.donacion.donation-completed.v1";
    private static final String AVISO_SOLICITADO = "redvital.donacion.notice-requested.v1";
    private static final String INSTITUCION_CAMBIADA = "redvital.institucional.institution-changed.v1";
    private static final String INDICADORES_ACTUALIZADOS = "redvital.donacion.indicators-updated.v1";
    private static final String AUDITORIA_REGISTRADA = "redvital.donacion.audit-recorded.v1";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", EntornoIntegracion::urlJdbc);
        r.add("spring.datasource.username", () -> "donacion_servicio");
        r.add("spring.datasource.password", () -> EntornoIntegracion.CLAVE_SERVICIO);
        r.add("spring.kafka.bootstrap-servers", EntornoIntegracion.KAFKA::getBootstrapServers);
        r.add("spring.kafka.security.protocol", () -> "PLAINTEXT");
        r.add("redvital.documento.clave-hmac", () -> "clave-hmac-solo-para-pruebas-de-integracion");
        r.add("redvital.jwt.llave-publica", EntornoIntegracion::archivoLlavePublica);
        // Esperas cortas para las pruebas de fallos del consumidor.
        r.add("redvital.kafka.espera-reintento", () -> "3000");
        r.add("redvital.kafka.espera-dependencia", () -> "500ms");
    }

    @TestConfiguration
    static class LlavesDePrueba {

        /** Mismas validaciones que producción, con la llave pública generada para las pruebas. */
        @Bean
        @Primary
        JwtDecoder decodificadorDePruebas() {
            NimbusJwtDecoder decodificador = NimbusJwtDecoder.withPublicKey(EntornoIntegracion.llavePublica()).build();
            decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(EntornoIntegracion.EMISOR)));
            return decodificador;
        }
    }

    /**
     * Fallos programados en la proyección de campañas, por evento, para probar
     * cómo reacciona el consumidor. Envuelve el adaptador real: lo que no está
     * programado llega a la base como siempre. Es seguro entre hilos, porque el
     * consumidor corre en el suyo.
     */
    @TestConfiguration
    static class FallasProgramadas {

        static final Map<UUID, ConcurrentLinkedDeque<RuntimeException>> PENDIENTES = new ConcurrentHashMap<>();
        static final Map<UUID, AtomicInteger> INTENTOS = new ConcurrentHashMap<>();

        static void programar(UUID eventoId, RuntimeException... fallas) {
            PENDIENTES.put(eventoId, new ConcurrentLinkedDeque<>(List.of(fallas)));
        }

        static int intentos(UUID eventoId) {
            return INTENTOS.getOrDefault(eventoId, new AtomicInteger()).get();
        }

        @Bean
        static BeanPostProcessor fallasEnLaProyeccion() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String nombre) {
                    if (!(bean instanceof ProyeccionCampaniasPort real)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(ProyeccionCampaniasPort.class.getClassLoader(),
                            new Class<?>[] {ProyeccionCampaniasPort.class}, (proxy, metodo, argumentos) -> {
                                if (metodo.getName().equals("guardar")) {
                                    UUID eventoId = (UUID) argumentos[1];
                                    INTENTOS.computeIfAbsent(eventoId, k -> new AtomicInteger()).incrementAndGet();
                                    var fallas = PENDIENTES.get(eventoId);
                                    RuntimeException falla = fallas == null ? null : fallas.poll();
                                    if (falla != null) {
                                        throw falla;
                                    }
                                }
                                try {
                                    return metodo.invoke(real, argumentos);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
                }
            };
        }
    }

    @Autowired
    PublicarIndicadores publicarIndicadores;

    @Autowired
    MeterRegistry metricas;

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ProcesarVencimientos vencimientos;


    // ------------------------------------------------------------------ M1

    @Test
    void registro_anonimo_es_idempotente_y_la_consulta_publica_no_revela_datos() throws Exception {
        String cuerpo = "{\"grupo_sanguineo\":\"O-\",\"municipio_ruta\":\"/00/05/05001\"}";
        JsonNode primero = cuerpo(enviar(post("/v1/intenciones"), null, "intencion-1", cuerpo), 201);
        String codigo = primero.path("codigo").asString();
        assertThat(codigo).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{12}");

        JsonNode reenvio = cuerpo(enviar(post("/v1/intenciones"), null, "intencion-1", cuerpo), 201);
        assertThat(reenvio.path("codigo").asString()).as("un reenvío devuelve el mismo código").isEqualTo(codigo);

        problema(enviar(post("/v1/intenciones"), null, "intencion-1", "{\"grupo_sanguineo\":\"A+\"}"), 422,
                "clave-idempotencia-reutilizada");

        JsonNode publica = cuerpo(enviar(get("/v1/intenciones/" + codigo.toLowerCase()), null, null, null), 200);
        assertThat(publica.path("estado").asString()).isEqualTo("pendiente");
        assertThat(publica.path("grupo_sanguineo").isNull()).as("sin token no se ve el grupo").isTrue();

        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador",
                "institucion:" + UUID.randomUUID());
        JsonNode delOperador = cuerpo(enviar(get("/v1/intenciones/" + codigo), operador, null, null), 200);
        assertThat(delOperador.path("grupo_sanguineo").asString()).isEqualTo("O-");

        problema(enviar(get("/v1/intenciones/ZZZZZZZZZZZZ"), null, null, null), 404, "no-encontrado");
    }

    @Test
    void perfiles_simultaneos_de_la_misma_cuenta_dejan_uno_y_los_demas_son_conflicto() throws Exception {
        String donante = EntornoIntegracion.token(UUID.randomUUID().toString(), "donante", "");
        String documento = documentoNuevo();
        var hilos = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var pendientes = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 8; i++) {
                String clave = "simultaneo-" + i + "-" + documento;
                pendientes.add(hilos.submit(() -> {
                    inicio.await();
                    return enviar(post("/v1/donantes"), donante, clave, registroDonante(documento, null, true))
                            .getResponse().getStatus();
                }));
            }
            inicio.countDown();
            var estados = new java.util.ArrayList<Integer>();
            for (var f : pendientes) {
                estados.add(f.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(estados).as("nunca un error interno").containsOnly(201, 409);
            assertThat(estados).filteredOn(e -> e == 201).hasSize(1);
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void crear_el_perfil_con_la_cuenta_del_token_y_consultarlo() throws Exception {
        UUID usuario = UUID.randomUUID();
        String donante = EntornoIntegracion.token(usuario.toString(), "donante", "");
        String documento = documentoNuevo();

        JsonNode creado = cuerpo(enviar(post("/v1/donantes"), donante, "registro-" + documento,
                registroDonante(documento, null, true)), 201);
        assertThat(creado.path("usuario_id").asString()).isEqualTo(usuario.toString());
        assertThat(creado.has("documento")).as("el documento nunca se devuelve").isFalse();
        JsonNode reenvio = cuerpo(enviar(post("/v1/donantes"), donante, "registro-" + documento,
                registroDonante(documento, null, true)), 201);
        assertThat(reenvio.path("id").asString()).as("el reenvío no crea otro perfil").isEqualTo(creado.path("id").asString());

        JsonNode perfil = cuerpo(enviar(get("/v1/donantes/me"), donante, null, null), 200);
        assertThat(perfil.path("nombre").asString()).isEqualTo("Laura Pérez");
        assertThat(perfil.path("grupo_sanguineo").isNull()).as("el grupo se tipifica en la primera donación").isTrue();

        JsonNode elegibilidad = cuerpo(enviar(get("/v1/donantes/me/elegibilidad"), donante, null, null), 200);
        assertThat(elegibilidad.path("elegible").asBoolean()).isTrue();

        JsonNode consentimientos = cuerpo(enviar(get("/v1/donantes/me/consentimientos"), donante, null, null), 200);
        assertThat(consentimientos).hasSize(2);

        JsonNode revocado = cuerpo(enviar(post("/v1/donantes/me/consentimientos"), donante, null,
                "{\"finalidad\":\"avisos_campanas\",\"otorgado\":false,\"version_aviso\":\"AV-2026.1\"}"), 200);
        assertThat(buscar(revocado, "finalidad", "avisos_campanas").path("otorgado").asBoolean()).isFalse();

        // Una cuenta tiene un perfil, y un documento una cuenta.
        problema(enviar(post("/v1/donantes"), donante, "segundo-perfil-" + documento,
                registroDonante(documentoNuevo(), null, true)), 409, "conflicto");
        String otraCuenta = EntornoIntegracion.token(UUID.randomUUID().toString(), "donante", "");
        problema(enviar(post("/v1/donantes"), otraCuenta, "repetido-" + documento,
                registroDonante(documento, null, true)), 409, "conflicto");
        problema(enviar(post("/v1/donantes"), otraCuenta, "sin-consentimiento-" + documento,
                registroDonante(documentoNuevo(), null, false)), 422, "consentimiento-requerido");
        // La donación de sangre exige 18 años cumplidos.
        problema(enviar(post("/v1/donantes"), otraCuenta, "menor-" + documento,
                registroDonante(documentoNuevo(), null, true).replace(
                        LocalDate.now().minusYears(30).toString(), LocalDate.now().minusYears(17).toString())),
                422, "menor-de-edad");
        // El perfil se crea con la cuenta del token: sin sesión, o con otro rol, no.
        problema(enviar(post("/v1/donantes"), null, "anonimo-" + documento, registroDonante(documentoNuevo(), null, true)),
                401, "sesion-invalida");
        problema(enviar(post("/v1/donantes"), EntornoIntegracion.token(UUID.randomUUID().toString(), "operador",
                "institucion:" + UUID.randomUUID()), "operador-" + documento, registroDonante(documentoNuevo(), null, true)),
                403, "operacion-no-permitida");
    }

    // ------------------------------------------------------------------ M3 y eventos

    @Test
    void el_aviso_de_cuenta_creada_lleva_el_destinatario_para_que_notificaciones_no_consulte_a_nadie()
            throws Exception {
        UUID usuario = UUID.randomUUID();
        String documento = documentoNuevo();
        String correo = "confirmacion-" + UUID.randomUUID().toString().substring(0, 8) + "@correo.test";

        JsonNode creado = cuerpo(enviar(post("/v1/donantes"), EntornoIntegracion.token(usuario.toString(), "donante", ""),
                "confirmacion-" + documento, registroDonante(documento, correo, true)), 201);
        String donanteId = creado.path("id").asString();

        // EV-02 cuenta_creada: el correo y el nombre para el saludo, nada más del donante.
        ConsumerRecord<String, String> aviso = esperarMensaje(AVISO_SOLICITADO, donanteId);
        assertThat(cabecera(aviso, "evento-tipo")).isEqualTo("notice.requested");
        JsonNode carga = json.readTree(aviso.value());
        assertThat(carga.path("tipo_aviso").asString()).isEqualTo("cuenta_creada");
        assertThat(carga.path("destinatario_id").asString()).isEqualTo(donanteId);
        assertThat(carga.path("correo").asString()).isEqualTo(correo);
        assertThat(carga.path("nombre").asString()).isEqualTo("Laura Pérez");
        assertThat(aviso.value()).doesNotContain(documento).doesNotContain("3001234567");
    }

    @Test
    void ciclo_de_vida_completo_con_campania_por_kafka_y_evento_de_donacion() throws Exception {
        UUID institucion = UUID.randomUUID();
        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador", "institucion:" + institucion);
        String admin = EntornoIntegracion.token(UUID.randomUUID().toString(), "admin_banco", "institucion:" + institucion);

        // La campaña llega solo por Kafka (EV-03): Donación nunca llama a Campañas.
        UUID campania = UUID.randomUUID();
        publicarCampania(campania, institucion, UUID.randomUUID(), "{"
                + "\"campania_id\":\"" + campania + "\",\"institucion_id\":\"" + institucion + "\","
                + "\"nombre\":\"Jornada de prueba\",\"sede\":\"Parque\",\"territorio_codigo\":\"05001\","
                + "\"territorio_ruta\":\"/00/05/05001\",\"inicia_en\":\"" + Instant.now().minusSeconds(3600) + "\","
                + "\"termina_en\":\"" + Instant.now().plusSeconds(8 * 3600) + "\","
                + "\"publicada_en\":\"" + Instant.now().minusSeconds(86_400) + "\",\"campo_futuro\":1}");
        esperar(() -> existeFila("SELECT 1 FROM campania_publicada WHERE campania_id = '" + campania + "'"));

        UUID usuario = UUID.randomUUID();
        String documento = documentoNuevo();
        cuerpo(enviar(post("/v1/donantes"), EntornoIntegracion.token(usuario.toString(), "donante", ""), "r-" + documento,
                registroDonante(documento, "d" + documento + "@correo.test", true)), 201);

        JsonNode encontrado = cuerpo(enviar(post("/v1/donantes/busqueda"), operador, null,
                "{\"documento\":\"" + documento + "\"}"), 200);
        assertThat(encontrado.path("elegibilidad").path("elegible").asBoolean()).isTrue();
        String donanteId = encontrado.path("id").asString();

        String registro = "{\"donante_id\":\"" + donanteId + "\",\"grupo_sanguineo\":\"A+\",\"campania_id\":\""
                + campania + "\",\"componentes\":[{\"componente\":\"globulos_rojos\",\"volumen_ml\":450},"
                + "{\"componente\":\"plasma\"}]}";
        JsonNode donacion = cuerpo(enviar(post("/v1/donaciones"), operador, "donacion-" + donanteId, registro), 201);
        String donacionId = donacion.path("id").asString();
        assertThat(donacion.path("campania_confirmada").asBoolean()).isTrue();
        assertThat(donacion.path("institucion_id").asString()).isEqualTo(institucion.toString());
        assertThat(donacion.path("unidades")).hasSize(2);
        donacion.path("unidades").forEach(u -> assertThat(u.path("estado").asString()).isEqualTo("captada"));

        JsonNode reenvio = cuerpo(enviar(post("/v1/donaciones"), operador, "donacion-" + donanteId, registro), 201);
        assertThat(reenvio.path("id").asString()).as("el reenvío no crea una segunda donación").isEqualTo(donacionId);

        problema(enviar(post("/v1/donaciones"), operador, "otra-" + donanteId, registro), 422, "donante-no-elegible");

        String donante = EntornoIntegracion.token(usuario.toString(), "donante", "");
        assertThat(cuerpo(enviar(get("/v1/donantes/me/donaciones"), donante, null, null), 200)).hasSize(1);
        JsonNode reconocimientos = cuerpo(enviar(get("/v1/donantes/me/reconocimientos"), donante, null, null), 200);
        assertThat(buscar(reconocimientos, "codigo", "primera_donacion").path("otorgado_en").isNull()).isFalse();

        // EV-01 sale por la bandeja hacia Kafka, sin datos personales.
        ConsumerRecord<String, String> evento = esperarMensaje(DONACION_COMPLETADA, donacionId);
        assertThat(cabecera(evento, "evento-tipo")).isEqualTo("donation.completed");
        assertThat(cabecera(evento, "productor")).isEqualTo("donacion");
        JsonNode carga = json.readTree(evento.value());
        assertThat(carga.path("campania_id").asString()).isEqualTo(campania.toString());
        assertThat(carga.path("unidades_generadas").asInt()).isEqualTo(2);
        assertThat(evento.value()).doesNotContain(documento).doesNotContain("Laura");

        JsonNode fraccionada = cuerpo(enviar(post("/v1/donaciones/" + donacionId + "/fraccionamiento"), operador,
                "fracc-" + donacionId, null), 200);
        fraccionada.path("unidades").forEach(u -> assertThat(u.path("estado").asString()).isEqualTo("fraccionada"));

        String globulos = idDe(fraccionada.path("unidades"), "globulos_rojos");
        String plasma = idDe(fraccionada.path("unidades"), "plasma");

        // Glóbulos rojos: apta, reservada y despachada con confirmación.
        transicion(post("/v1/unidades/" + globulos + "/ingreso-tamizaje"), operador, null, null, "en_tamizaje");
        transicion(post("/v1/unidades/" + globulos + "/tamizaje"), operador, null, "{\"apta\":true}", "disponible");
        JsonNode inventario = cuerpo(enviar(get("/v1/inventario"), admin, null, null), 200);
        assertThat(buscar(inventario, "componente", "globulos_rojos").path("unidades_disponibles").asLong()).isEqualTo(1);
        transicion(post("/v1/unidades/" + globulos + "/reserva"), operador, null, null, "reservada");
        problema(enviar(post("/v1/unidades/" + globulos + "/despacho"), operador, "desp-" + globulos,
                "{\"confirmacion\":\"si\"}"), 422, "confirmacion-invalida");
        transicion(post("/v1/unidades/" + globulos + "/despacho"), operador, "desp2-" + globulos,
                "{\"confirmacion\":\"" + globulos + "\"}", "despachada");

        // Plasma: el tamizaje no admite ningún campo de motivo (EC-02).
        transicion(post("/v1/unidades/" + plasma + "/ingreso-tamizaje"), operador, null, null, "en_tamizaje");
        problema(enviar(post("/v1/unidades/" + plasma + "/tamizaje"), operador, null,
                "{\"apta\":false,\"motivo\":\"cualquiera\"}"), 400, "validacion");
        transicion(post("/v1/unidades/" + plasma + "/tamizaje"), operador, null, "{\"apta\":false}", "no_apta");
        transicion(post("/v1/unidades/" + plasma + "/disposicion-final"), operador, "disp-" + plasma,
                "{\"confirmacion\":\"" + plasma + "\"}", "desechada");

        // El ciclo no retrocede, y el intento queda en la bitácora.
        problema(enviar(post("/v1/unidades/" + plasma + "/ingreso-tamizaje"), operador, null, null), 409,
                "conflicto-de-estado");
        assertThat(existeFila("SELECT 1 FROM registro_auditoria WHERE recurso_id = '" + plasma
                + "' AND resultado = 'denegado' AND operacion = 'iniciar_tamizaje'")).isTrue();

        JsonNode eventos = cuerpo(enviar(get("/v1/unidades/" + plasma + "/eventos"), admin, null, null), 200);
        assertThat(eventos).extracting(e -> e.path("estado_nuevo").asString())
                .containsExactly("captada", "fraccionada", "en_tamizaje", "no_apta", "desechada");
        JsonNode unidad = cuerpo(enviar(get("/v1/unidades/" + plasma), admin, null, null), 200);
        assertThat(unidad.has("motivo")).isFalse();
        assertThat(unidad.path("apta").asBoolean()).isFalse();
    }

    @Test
    void controles_de_acceso_por_rol_y_por_jurisdiccion() throws Exception {
        UUID institucion = UUID.randomUUID();
        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador", "institucion:" + institucion);

        problema(enviar(get("/v1/unidades"), null, null, null), 401, "sesion-invalida");
        problema(enviar(get("/v1/unidades"), "token.alterado.x", null, null), 401, "sesion-invalida");
        // U7 lee la trazabilidad de una unidad, pero no el listado (DD 15.5, grupos 4 y 5).
        problema(enviar(get("/v1/unidades"), EntornoIntegracion.token(UUID.randomUUID().toString(), "auditor",
                "territorio:/00"), null, null), 403, "operacion-no-permitida");
        problema(enviar(get("/v1/unidades"), EntornoIntegracion.token(UUID.randomUUID().toString(), "donante", ""),
                null, null), 403, "operacion-no-permitida");
        problema(enviar(post("/v1/donaciones"), EntornoIntegracion.token(UUID.randomUUID().toString(), "admin_banco",
                "institucion:" + institucion), "x", "{\"grupo_sanguineo\":\"O+\",\"componentes\":[{\"componente\":\"plasma\"}]}"),
                403, "operacion-no-permitida");
        problema(enviar(get("/v1/inventario?institucion_id=" + UUID.randomUUID()), operador, null, null), 403,
                "fuera-de-jurisdiccion");
        problema(enviar(get("/v1/unidades/" + UUID.randomUUID()), operador, null, null), 404, "no-encontrado");
        problema(enviar(get("/internal/v1/cualquier-cosa"), null, null, null), 404, "no-encontrado");
        assertThat(existeFila("SELECT 1 FROM registro_auditoria WHERE resultado = 'denegado' AND rol = 'auditor'"))
                .as("las denegaciones de rol quedan auditadas").isTrue();
    }

    // ------------------------------------------------------------------ M4 y procesos

    @Test
    void vencimiento_automatico_y_alertas_de_escasez() throws Exception {
        UUID institucion = UUID.randomUUID();
        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador", "institucion:" + institucion);
        String admin = EntornoIntegracion.token(UUID.randomUUID().toString(), "admin_banco", "institucion:" + institucion);

        JsonNode donacion = cuerpo(enviar(post("/v1/donaciones"), operador, "anon-" + institucion,
                "{\"grupo_sanguineo\":\"O+\",\"componentes\":[{\"componente\":\"plaquetas\"}]}"), 201);
        assertThat(donacion.path("donante_id").isNull()).as("donación sin identificar").isTrue();
        assertThat(donacion.path("campania_confirmada").asBoolean()).isFalse();
        String donacionId = donacion.path("id").asString();
        String plaquetas = donacion.path("unidades").get(0).path("id").asString();
        enviar(post("/v1/donaciones/" + donacionId + "/fraccionamiento"), operador, "f-" + donacionId, null);
        transicion(post("/v1/unidades/" + plaquetas + "/ingreso-tamizaje"), operador, null, null, "en_tamizaje");
        transicion(post("/v1/unidades/" + plaquetas + "/tamizaje"), operador, null, "{\"apta\":true}", "disponible");

        cuerpo(enviar(put("/v1/inventario/umbrales"), admin, null, "{\"umbrales\":[{\"componente\":\"plaquetas\","
                + "\"grupo_sanguineo\":\"O+\",\"minimo_unidades\":5,\"dias_previos_vencimiento\":7}]}"), 200);
        problema(enviar(put("/v1/inventario/umbrales"), operador, null, "{\"umbrales\":[]}"), 403,
                "operacion-no-permitida");

        // Guardar los umbrales reevalúa enseguida las alertas del banco: no hace falta esperar al proceso.
        JsonNode abiertas = cuerpo(enviar(get("/v1/alertas?estado=abierta"), operador, null, null), 200);
        assertThat(abiertas).extracting(a -> a.path("tipo").asString())
                .contains("escasez", "vencimiento_proximo");

        // Bajar el umbral por debajo de lo disponible cierra la escasez en la misma operación, y subirlo la reabre.
        cuerpo(enviar(put("/v1/inventario/umbrales"), admin, null, "{\"umbrales\":[{\"componente\":\"plaquetas\","
                + "\"grupo_sanguineo\":\"O+\",\"minimo_unidades\":1,\"dias_previos_vencimiento\":7}]}"), 200);
        assertThat(cuerpo(enviar(get("/v1/alertas?estado=abierta"), operador, null, null), 200))
                .extracting(a -> a.path("tipo").asString()).doesNotContain("escasez");
        cuerpo(enviar(put("/v1/inventario/umbrales"), admin, null, "{\"umbrales\":[{\"componente\":\"plaquetas\","
                + "\"grupo_sanguineo\":\"O+\",\"minimo_unidades\":5,\"dias_previos_vencimiento\":7}]}"), 200);
        abiertas = cuerpo(enviar(get("/v1/alertas?estado=abierta"), operador, null, null), 200);
        assertThat(abiertas).extracting(a -> a.path("tipo").asString()).contains("escasez");

        try (Connection c = EntornoIntegracion.superusuario()) {
            // Dos días: la base usa la fecha UTC y el proceso la de Bogotá, que de
            // 19:00 a 24:00 todavía es el día anterior.
            c.createStatement().execute("UPDATE unidad SET fecha_vencimiento = current_date - 2 WHERE id = '"
                    + plaquetas + "'");
        }
        int retiradas = vencimientos.ejecutar();
        assertThat(retiradas).isGreaterThanOrEqualTo(1);
        JsonNode vencida = cuerpo(enviar(get("/v1/unidades/" + plaquetas), operador, null, null), 200);
        assertThat(vencida.path("estado").asString()).isEqualTo("vencida");
        JsonNode eventos = cuerpo(enviar(get("/v1/unidades/" + plaquetas + "/eventos"), operador, null, null), 200);
        JsonNode ultimo = eventos.get(eventos.size() - 1);
        assertThat(ultimo.path("actor_tipo").asString()).as("el vencimiento es del sistema").isEqualTo("sistema");

        String escasez = buscar(abiertas, "tipo", "escasez").path("id").asString();
        JsonNode atendida = cuerpo(enviar(post("/v1/alertas/" + escasez + "/atencion"), admin, null, null), 200);
        assertThat(atendida.path("estado").asString()).isEqualTo("atendida");
        problema(enviar(post("/v1/alertas/" + escasez + "/atencion"), admin, null, null), 409, "conflicto");
    }

    @Test
    void el_rol_de_servicio_no_puede_reescribir_la_historia() throws Exception {
        try (Connection c = EntornoIntegracion.servicio()) {
            assertThatThrownBy(() -> c.createStatement().execute("UPDATE donacion SET campania_id = NULL"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> c.createStatement().execute("UPDATE unidad SET fecha_vencimiento = current_date"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> c.createStatement().execute("DELETE FROM evento_unidad"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> c.createStatement().execute("DELETE FROM registro_auditoria"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void un_evento_que_no_cumple_el_contrato_va_al_tema_de_mensajes_muertos() throws Exception {
        UUID eventoId = UUID.randomUUID();
        publicarCampania(UUID.randomUUID(), UUID.randomUUID(), eventoId, "{esto no es json");
        ConsumerRecord<String, String> muerto = esperarMensajePorCabecera(CAMPANIA_PUBLICADA + ".donacion.dlt",
                "evento-id", eventoId.toString());
        assertThat(muerto.value()).isEqualTo("{esto no es json");
        // No se reintenta: un mensaje mal formado fallaría igual las tres veces.
        assertThat(llegaMensajePorCabecera(CAMPANIA_PUBLICADA + ".donacion.retry-0", "evento-id",
                eventoId.toString(), Duration.ofSeconds(3))).isFalse();
        // Nunca se descarta en silencio: el manejador de mensajes muertos lo cuenta para la alerta.
        esperar(() -> {
            Counter contador = metricas.find("redvital_eventos_mensajes_muertos").counter();
            return contador != null && contador.count() >= 1;
        });
    }

    @Test
    void un_evento_que_falla_se_reintenta_aparte_sin_detener_a_los_que_vienen_detras() throws Exception {
        UUID institucion = UUID.randomUUID();
        UUID primera = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        UUID eventoPrimera = UUID.randomUUID();
        FallasProgramadas.programar(eventoPrimera, new IllegalStateException("falla simulada del mensaje"));

        // Misma clave: misma partición y en este orden.
        String clave = UUID.randomUUID().toString();
        publicarCampania(clave, eventoPrimera, cargaCampania(primera, institucion));
        publicarCampania(clave, UUID.randomUUID(), cargaCampania(segunda, institucion));

        // La segunda se aplica mientras la primera espera su turno en el tema de reintento.
        esperar(() -> existeCampania(segunda));
        assertThat(existeCampania(primera)).as("la primera aún espera su reintento").isFalse();
        assertThat(cabecera(esperarMensajePorCabecera(CAMPANIA_PUBLICADA + ".donacion.retry-0", "evento-id",
                eventoPrimera.toString()), "evento-id")).isEqualTo(eventoPrimera.toString());
        esperar(() -> existeCampania(primera));
        assertThat(FallasProgramadas.intentos(eventoPrimera)).isEqualTo(2);
    }

    @Test
    void con_la_base_caida_el_evento_espera_en_su_sitio_y_no_llena_los_temas_de_reintento() throws Exception {
        UUID campania = UUID.randomUUID();
        UUID evento = UUID.randomUUID();
        var baseCaida = new CannotGetJdbcConnectionException("base caída simulada");
        FallasProgramadas.programar(evento, baseCaida, baseCaida);

        publicarCampania(campania.toString(), evento, cargaCampania(campania, UUID.randomUUID()));

        esperar(() -> existeCampania(campania));
        assertThat(FallasProgramadas.intentos(evento)).as("dos intentos con la base caída y uno bueno").isEqualTo(3);
        assertThat(llegaMensajePorCabecera(CAMPANIA_PUBLICADA + ".donacion.retry-0", "evento-id",
                evento.toString(), Duration.ofSeconds(3))).isFalse();
    }

    // ------------------------------------------------------------------ utilidades

    private MvcResult enviar(MockHttpServletRequestBuilder peticion, String token, String clave, String cuerpo)
            throws Exception {
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        if (clave != null) {
            peticion.header("Idempotency-Key", clave);
        }
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion.header("X-Forwarded-For", "10.0.0.7")).andReturn();
    }

    // ------------------------------------------------- Institucional, solo por Kafka

    @Test
    void la_proyeccion_de_instituciones_se_alimenta_de_EV07_y_no_retrocede() throws Exception {
        UUID institucion = UUID.randomUUID();
        Instant ahora = Instant.now();
        publicarInstitucion(institucion, UUID.randomUUID(), "Banco Nuevo (ficticio)", "/00/05/05088", true, ahora);
        esperar(() -> existeFila("SELECT 1 FROM institucion_proyeccion WHERE institucion_id = '" + institucion
                + "' AND nombre = 'Banco Nuevo (ficticio)'"));
        // Un cambio más viejo que el guardado no lo pisa; uno más nuevo, sí.
        publicarInstitucion(institucion, UUID.randomUUID(), "Nombre viejo", "/00/05/05088", true, ahora.minusSeconds(60));
        publicarInstitucion(institucion, UUID.randomUUID(), "Banco Renombrado (ficticio)", "/00/05/05088", false,
                ahora.plusSeconds(1));
        esperar(() -> existeFila("SELECT 1 FROM institucion_proyeccion WHERE institucion_id = '" + institucion
                + "' AND nombre = 'Banco Renombrado (ficticio)' AND NOT vigente"));
        assertThat(existeFila("SELECT 1 FROM institucion_proyeccion WHERE nombre = 'Nombre viejo'")).isFalse();
    }

    @Test
    void el_coordinador_lee_su_territorio_con_la_proyeccion_local() throws Exception {
        UUID medellin = UUID.randomUUID();
        UUID bello = UUID.randomUUID();
        UUID bogota = UUID.randomUUID();
        proyectarInstitucion(medellin, "/00/05/05001");
        proyectarInstitucion(bello, "/00/05/05088");
        proyectarInstitucion(bogota, "/00/11/11001");
        UUID unidadMedellin = insertarDonacion(medellin, Instant.now(), 1, 0).getFirst();
        UUID unidadBello = insertarDonacion(bello, Instant.now(), 1, 0).getFirst();
        UUID unidadBogota = insertarDonacion(bogota, Instant.now(), 1, 0).getFirst();
        String coordinador = EntornoIntegracion.token(UUID.randomUUID().toString(), "coordinador", "territorio:/00/05");

        JsonNode pagina = cuerpo(enviar(get("/v1/unidades?tamano=100"), coordinador, null, null), 200);
        List<String> ids = new java.util.ArrayList<>();
        pagina.path("elementos").forEach(u -> ids.add(u.path("id").asString()));
        assertThat(ids).contains(unidadMedellin.toString(), unidadBello.toString()).doesNotContain(unidadBogota.toString());
        cuerpo(enviar(get("/v1/unidades/" + unidadBello), coordinador, null, null), 200);
        problema(enviar(get("/v1/unidades/" + unidadBogota), coordinador, null, null), 404, "no-encontrado");
        problema(enviar(get("/v1/inventario?institucion_id=" + bogota), coordinador, null, null), 403,
                "fuera-de-jurisdiccion");
        cuerpo(enviar(get("/v1/alertas"), coordinador, null, null), 200);
        // El coordinador consulta pero no opera ni configura (DD 15.5).
        problema(enviar(put("/v1/inventario/umbrales"), coordinador, null, "{\"umbrales\":[]}"), 403,
                "operacion-no-permitida");
    }

    @Test
    void el_auditor_lee_la_trazabilidad_de_una_unidad_de_su_ambito() throws Exception {
        UUID banco = UUID.randomUUID();
        proyectarInstitucion(banco, "/00/76/76001");
        UUID unidad = insertarDonacion(banco, Instant.now(), 1, 0).getFirst();
        String auditor = EntornoIntegracion.token(UUID.randomUUID().toString(), "auditor", "territorio:/00");
        cuerpo(enviar(get("/v1/unidades/" + unidad + "/eventos"), auditor, null, null), 200);
        problema(enviar(post("/v1/unidades/" + unidad + "/ingreso-tamizaje"), auditor, "x", "{}"), 403,
                "operacion-no-permitida");
    }

    @Test
    void los_indicadores_salen_por_EV09_con_la_captacion_por_dia_y_las_existencias() throws Exception {
        UUID banco = UUID.randomUUID();
        Instant ayer = Instant.now().minus(Duration.ofDays(1));
        for (int i = 0; i < 6; i++) {
            insertarDonacion(banco, ayer, 2, i == 0 ? 1 : 0);
        }
        publicarIndicadores.ejecutar();
        ConsumerRecord<String, String> evento = esperarMensaje(INDICADORES_ACTUALIZADOS, banco.toString());
        assertThat(cabecera(evento, "evento-tipo")).isEqualTo("indicators.updated");
        JsonNode carga = json.readTree(evento.value());
        assertThat(carga.path("institucion_id").asString()).isEqualTo(banco.toString());
        JsonNode dia = carga.path("captacion").get(0);
        assertThat(dia.path("donaciones").asLong()).isEqualTo(6);
        assertThat(dia.path("unidades_generadas").asLong()).isEqualTo(12);
        assertThat(dia.path("unidades_no_aptas").asLong()).isEqualTo(1);
        assertThat(carga.path("inventario")).isNotEmpty();
        assertThat(evento.value()).as("solo conteos: ni unidades ni donantes").doesNotContain("unidad_id")
                .doesNotContain("donante");
        // Sin cambios, la misma fotografía no se vuelve a publicar.
        assertThat(existeFila("SELECT 1 FROM indicador_publicado WHERE institucion_id = '" + banco + "'")).isTrue();
    }

    @Test
    void cada_registro_de_la_bitacora_sale_por_EV10_con_la_estructura_comun() throws Exception {
        UUID institucion = UUID.randomUUID();
        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador", "institucion:" + institucion);
        String correlacion = UUID.randomUUID().toString();
        // Un acceso denegado se audita en su propia transacción, y su evento con él.
        mvc.perform(get("/v1/inventario?institucion_id=" + UUID.randomUUID())
                .header("Authorization", "Bearer " + operador).header("X-Correlacion-Id", correlacion)).andReturn();
        ConsumerRecord<String, String> evento = esperarMensaje(AUDITORIA_REGISTRADA, correlacion);
        assertThat(cabecera(evento, "evento-tipo")).isEqualTo("audit.recorded");
        JsonNode carga = json.readTree(evento.value());
        assertThat(carga.path("operacion").asString()).isEqualTo("consultar_inventario");
        assertThat(carga.path("resultado").asString()).isEqualTo("denegado");
        assertThat(carga.path("correlacion_id").asString()).isEqualTo(correlacion);
        assertThat(existeFila("SELECT 1 FROM registro_auditoria WHERE id = '" + carga.path("id").asString() + "'"))
                .as("el evento lleva el mismo identificador que la fila").isTrue();
    }

    /**
     * Una donación con sus unidades disponibles, escrita directamente: las
     * pruebas de indicadores necesitan volumen, no el recorrido del operador.
     * Las primeras {@code noAptas} unidades pasan por no_apta.
     */
    private static List<UUID> insertarDonacion(UUID institucion, Instant fecha, int unidades, int noAptas) throws Exception {
        UUID donacion = UUID.randomUUID();
        List<UUID> ids = new java.util.ArrayList<>();
        String[] componentes = {"plasma", "globulos_rojos", "plaquetas"};
        try (Connection c = EntornoIntegracion.superusuario()) {
            c.createStatement().execute("INSERT INTO donacion (id, tipo_donacion_id, institucion_id, fecha_captacion, operador_id) "
                    + "VALUES ('" + donacion + "', (SELECT id FROM tipo_donacion ORDER BY codigo LIMIT 1), '" + institucion
                    + "', '" + fecha + "', gen_random_uuid())");
            for (int i = 0; i < unidades; i++) {
                UUID unidad = UUID.randomUUID();
                boolean noApta = i < noAptas;
                c.createStatement().execute("INSERT INTO unidad (id, donacion_id, componente_id, grupo_sanguineo_id, "
                        + "institucion_custodia_id, estado_id, apta, fecha_vencimiento) VALUES ('" + unidad + "', '" + donacion
                        + "', (SELECT id FROM componente WHERE codigo = '" + componentes[i] + "'), "
                        + "(SELECT id FROM grupo_sanguineo WHERE codigo = 'O+'), '" + institucion + "', "
                        + "(SELECT id FROM estado_unidad WHERE codigo = '" + (noApta ? "no_apta" : "disponible") + "'), "
                        + !noApta + ", current_date + 30)");
                c.createStatement().execute("INSERT INTO evento_unidad (id, unidad_id, estado_nuevo_id, actor_tipo, actor_id, "
                        + "institucion_id, correlacion_id) VALUES (gen_random_uuid(), '" + unidad + "', "
                        + "(SELECT id FROM estado_unidad WHERE codigo = '" + (noApta ? "no_apta" : "disponible") + "'), "
                        + "'usuario', 'prueba', '" + institucion + "', 'indicadores')");
                ids.add(unidad);
            }
        }
        return ids;
    }

    private JsonNode cuerpo(MvcResult resultado, int estado) throws Exception {
        String contenido = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(resultado.getResponse().getStatus()).as(contenido).isEqualTo(estado);
        return json.readTree(contenido);
    }

    private void problema(MvcResult resultado, int estado, String tipo) throws Exception {
        JsonNode problema = cuerpo(resultado, estado);
        assertThat(resultado.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(problema.path("tipo").asString()).isEqualTo(tipo);
        assertThat(problema.path("correlacion_id").asString()).isNotBlank();
    }

    private void transicion(MockHttpServletRequestBuilder peticion, String token, String clave, String cuerpo,
                            String estadoEsperado) throws Exception {
        JsonNode resultado = cuerpo(enviar(peticion, token, clave, cuerpo), 200);
        assertThat(resultado.path("estado_nuevo").asString()).isEqualTo(estadoEsperado);
    }

    private static JsonNode buscar(JsonNode lista, String campo, String valor) {
        for (JsonNode elemento : lista) {
            if (valor.equals(elemento.path(campo).asString())) {
                return elemento;
            }
        }
        throw new AssertionError("No hay elemento con " + campo + "=" + valor + " en " + lista);
    }

    private static String idDe(JsonNode unidades, String componente) {
        return buscar(unidades, "componente", componente).path("id").asString();
    }

    /** Cuerpo del perfil: la cuenta ya existe en Identidad, así que no lleva correo de acceso ni contraseña. */
    private static String registroDonante(String documento, String correo, boolean autoriza) {
        return "{\"documento\":\"" + documento + "\",\"nombre\":\"Laura Pérez\",\"fecha_nacimiento\":\""
                + LocalDate.now().minusYears(30) + "\",\"correo\":" + (correo == null ? "null" : "\"" + correo + "\"")
                + ",\"telefono\":\"3001234567\",\"municipio_ruta\":\"/00/05/05001\",\"autoriza_tratamiento_datos\":"
                + autoriza + ",\"autoriza_avisos_campanas\":true,\"version_aviso\":\"AV-2026.1\"}";
    }

    /** Una institución en la proyección local, como la deja el consumidor de EV-07. */
    private static void proyectarInstitucion(UUID id, String ruta) {
        try (Connection c = EntornoIntegracion.superusuario()) {
            c.createStatement().execute("INSERT INTO institucion_proyeccion (institucion_id, codigo, nombre, tipo, "
                    + "territorio_codigo, territorio_ruta, vigente, cambiado_en, evento_id) VALUES ('" + id + "', 'P-"
                    + id.toString().substring(0, 8) + "', 'Banco " + id.toString().substring(0, 8) + " (ficticio)', "
                    + "'banco_de_sangre', '" + ruta.substring(ruta.lastIndexOf('/') + 1) + "', '" + ruta + "', true, now(), "
                    + "gen_random_uuid())");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String documentoNuevo() {
        return String.valueOf(1_000_000_000L + (long) (Math.random() * 8_000_000_000L));
    }

    private static boolean existeFila(String consulta) {
        try (Connection c = EntornoIntegracion.superusuario()) {
            return c.createStatement().executeQuery(consulta).next();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(limite)) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("La condición no se cumplió en 30 segundos");
    }

    private static void publicarCampania(UUID campania, UUID institucion, UUID eventoId, String carga) {
        publicarCampania(campania.toString(), eventoId, carga);
    }

    private static String cargaCampania(UUID campania, UUID institucion) {
        return "{\"campania_id\":\"" + campania + "\",\"institucion_id\":\"" + institucion + "\","
                + "\"nombre\":\"Jornada de prueba\",\"sede\":\"Parque\",\"territorio_codigo\":\"05001\","
                + "\"territorio_ruta\":\"/00/05/05001\",\"inicia_en\":\"" + Instant.now().minusSeconds(3600) + "\","
                + "\"termina_en\":\"" + Instant.now().plusSeconds(8 * 3600) + "\","
                + "\"publicada_en\":\"" + Instant.now().minusSeconds(60) + "\"}";
    }

    private static boolean existeCampania(UUID campania) {
        return existeFila("SELECT 1 FROM campania_publicada WHERE campania_id = '" + campania + "'");
    }

    private static void publicarCampania(String clave, UUID eventoId, String carga) {
        try (var productor = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, EntornoIntegracion.KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            var registro = new ProducerRecord<>(CAMPANIA_PUBLICADA, clave, carga);
            registro.headers().add("evento-id", eventoId.toString().getBytes(StandardCharsets.UTF_8));
            registro.headers().add("evento-tipo", "campaign.published".getBytes(StandardCharsets.UTF_8));
            registro.headers().add("correlacion-id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            registro.headers().add("productor", "campanias".getBytes(StandardCharsets.UTF_8));
            productor.send(registro).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void publicarInstitucion(UUID id, UUID eventoId, String nombre, String ruta, boolean vigente,
                                            Instant cambiadoEn) {
        String carga = "{\"institucion_id\":\"" + id + "\",\"codigo\":\"QA-" + id.toString().substring(0, 8)
                + "\",\"nombre\":\"" + nombre + "\",\"tipo\":\"banco_de_sangre\",\"territorio_codigo\":\""
                + ruta.substring(ruta.lastIndexOf('/') + 1) + "\",\"territorio_ruta\":\"" + ruta + "\",\"vigente\":"
                + vigente + ",\"cambiado_en\":\"" + cambiadoEn + "\"}";
        try (var productor = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, EntornoIntegracion.KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            var registro = new ProducerRecord<>(INSTITUCION_CAMBIADA, id.toString(), carga);
            registro.headers().add("evento-id", eventoId.toString().getBytes(StandardCharsets.UTF_8));
            registro.headers().add("evento-tipo", "institution.changed".getBytes(StandardCharsets.UTF_8));
            registro.headers().add("productor", "institucional".getBytes(StandardCharsets.UTF_8));
            productor.send(registro).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ConsumerRecord<String, String> esperarMensaje(String tema, String clave) {
        return esperarEn(tema, r -> clave.equals(r.key()));
    }

    private static ConsumerRecord<String, String> esperarMensajePorCabecera(String tema, String cabecera, String valor) {
        return esperarEn(tema, r -> valor.equals(cabecera(r, cabecera)));
    }

    /** Lee el tema desde el principio durante la ventana dada y dice si apareció el mensaje. */
    private static boolean llegaMensajePorCabecera(String tema, String cabecera, String valor, Duration ventana) {
        try (var consumidor = consumidorDePrueba()) {
            consumidor.subscribe(List.of(tema));
            Instant limite = Instant.now().plus(ventana);
            while (Instant.now().isBefore(limite)) {
                for (ConsumerRecord<String, String> r : consumidor.poll(Duration.ofMillis(500))) {
                    if (valor.equals(cabecera(r, cabecera))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static KafkaConsumer<String, String> consumidorDePrueba() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, EntornoIntegracion.KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private static ConsumerRecord<String, String> esperarEn(String tema,
                                                            java.util.function.Predicate<ConsumerRecord<String, String>> filtro) {
        try (var consumidor = consumidorDePrueba()) {
            consumidor.subscribe(List.of(tema));
            Instant limite = Instant.now().plus(Duration.ofSeconds(45));
            while (Instant.now().isBefore(limite)) {
                for (ConsumerRecord<String, String> r : consumidor.poll(Duration.ofMillis(500))) {
                    if (filtro.test(r)) {
                        return r;
                    }
                }
            }
        }
        throw new AssertionError("No llegó el mensaje esperado al tema " + tema);
    }

    private static String cabecera(ConsumerRecord<String, String> r, String nombre) {
        var h = r.headers().lastHeader(nombre);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
