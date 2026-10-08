package co.gov.redvital.donacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.redvital.donacion.dominio.excepcion.TransicionNoPermitida;
import co.gov.redvital.donacion.dominio.modelo.ActorTipo;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.Transicion;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import co.gov.redvital.donacion.dominio.servicio.MotorTransiciones;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * EC-36: la matriz se prueba de forma exhaustiva, no por muestreo. Nueve por
 * nueve pares ordenados, cada uno con los dos tipos de actor, más el caso de
 * creación.
 */
class MotorTransicionesTest {

    private static final Instant AHORA = Instant.parse("2026-10-07T15:00:00Z");
    private final MotorTransiciones motor = new MotorTransiciones(MatrizDelDD.matriz());

    static Stream<Arguments> todosLosPares() {
        List<Arguments> casos = new ArrayList<>();
        for (CodigoEstado origen : CodigoEstado.values()) {
            for (CodigoEstado destino : CodigoEstado.values()) {
                for (ActorTipo actor : ActorTipo.values()) {
                    Optional<Transicion> esperada = MatrizDelDD.TABLA_20.stream()
                            .filter(t -> t.origen() == origen && t.destino() == destino && t.actorTipo() == actor)
                            .findFirst();
                    casos.add(Arguments.of(origen, destino, actor, esperada.isPresent()));
                }
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} por {2}: permitida={3}")
    @MethodSource("todosLosPares")
    void la_matriz_admite_exactamente_las_trece_transiciones(CodigoEstado origen, CodigoEstado destino,
                                                               ActorTipo actor, boolean permitida) {
        Unidad unidad = unidadEn(origen, origen == CodigoEstado.DISPONIBLE || origen == CodigoEstado.RESERVADA ? true : null);
        if (permitida) {
            var evento = motor.transitar(unidad, destino, actor, "actor", null, "c", AHORA);
            assertThat(unidad.estado()).isEqualTo(destino);
            assertThat(evento.estadoAnterior()).isEqualTo(origen);
            assertThat(evento.estadoNuevo()).isEqualTo(destino);
            assertThat(evento.actorTipo()).isEqualTo(actor);
        } else {
            assertThatThrownBy(() -> motor.transitar(unidad, destino, actor, "actor", null, "c", AHORA))
                    .isInstanceOf(TransicionNoPermitida.class);
            assertThat(unidad.estado()).as("un rechazo no cambia el estado").isEqualTo(origen);
        }
    }

    @Test
    void son_ochenta_y_un_pares_y_trece_validos() {
        long pares = todosLosPares().map(a -> List.of(a.get()[0], a.get()[1])).distinct().count();
        long validas = todosLosPares().filter(a -> (boolean) a.get()[3]).count();
        assertThat(pares).isEqualTo(81);
        assertThat(validas).isEqualTo(13);
    }

    @Test
    void la_creacion_produce_el_evento_de_alta_en_captada() {
        Unidad nueva = Unidad.nueva(UUID.randomUUID(), "plasma", "O+", UUID.randomUUID(), LocalDate.of(2027, 10, 7),
                null, AHORA);
        var evento = motor.crear(nueva, "operador", "c", AHORA);
        assertThat(evento.estadoAnterior()).isNull();
        assertThat(evento.estadoNuevo()).isEqualTo(CodigoEstado.CAPTADA);
        assertThat(nueva.estado()).isEqualTo(CodigoEstado.CAPTADA);
        assertThatThrownBy(() -> motor.crear(nueva, "operador", "c", AHORA)).isInstanceOf(TransicionNoPermitida.class);
    }

    @Test
    void el_veredicto_apta_solo_lo_fijan_las_transiciones_de_tamizaje() {
        Unidad apta = unidadEn(CodigoEstado.EN_TAMIZAJE, null);
        motor.transitar(apta, CodigoEstado.DISPONIBLE, ActorTipo.USUARIO, "op", null, "c", AHORA);
        assertThat(apta.apta()).isTrue();

        Unidad noApta = unidadEn(CodigoEstado.EN_TAMIZAJE, null);
        motor.transitar(noApta, CodigoEstado.NO_APTA, ActorTipo.USUARIO, "op", null, "c", AHORA);
        assertThat(noApta.apta()).isFalse();

        Unidad sinTamizar = unidadEn(CodigoEstado.FRACCIONADA, null);
        motor.transitar(sinTamizar, CodigoEstado.VENCIDA, ActorTipo.SISTEMA, "proceso", null, "c", AHORA);
        assertThat(sinTamizar.apta()).as("una unidad que vence sin tamizar queda sin veredicto").isNull();
    }

    @Test
    void el_sistema_nunca_desecha_una_unidad() {
        Unidad vencida = unidadEn(CodigoEstado.VENCIDA, null);
        assertThatThrownBy(() -> motor.transitar(vencida, CodigoEstado.DESECHADA, ActorTipo.SISTEMA, "proceso",
                null, "c", AHORA)).isInstanceOf(TransicionNoPermitida.class);
    }

    @Test
    void no_hay_retorno_desde_no_apta() {
        Unidad noApta = unidadEn(CodigoEstado.NO_APTA, false);
        for (CodigoEstado destino : List.of(CodigoEstado.DISPONIBLE, CodigoEstado.EN_TAMIZAJE)) {
            assertThatThrownBy(() -> motor.transitar(noApta, destino, ActorTipo.USUARIO, "op", null, "c", AHORA))
                    .isInstanceOf(TransicionNoPermitida.class);
        }
    }

    private static Unidad unidadEn(CodigoEstado estado, Boolean apta) {
        return new Unidad(UUID.randomUUID(), UUID.randomUUID(), "globulos_rojos", "O+", UUID.randomUUID(), estado,
                apta, LocalDate.of(2026, 11, 18), 450, AHORA);
    }
}
