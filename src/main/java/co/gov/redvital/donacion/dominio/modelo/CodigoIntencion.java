package co.gov.redvital.donacion.dominio.modelo;

import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Código que el donante anónimo presenta en el banco. Doce caracteres de un
 * alfabeto de 31 símbolos sin caracteres ambiguos (sin 0, O, 1, I ni L).
 */
public final class CodigoIntencion {

    public static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final int LONGITUD = 12;

    private CodigoIntencion() {
    }

    public static String generar(RandomGenerator azar) {
        StringBuilder codigo = new StringBuilder(LONGITUD);
        for (int i = 0; i < LONGITUD; i++) {
            codigo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /** Acepta el código con guiones o espacios y en minúsculas, como lo escribe una persona. */
    public static Optional<String> normalizar(String entrada) {
        if (entrada == null) {
            return Optional.empty();
        }
        String limpio = entrada.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (limpio.length() != LONGITUD) {
            return Optional.empty();
        }
        for (char c : limpio.toCharArray()) {
            if (ALFABETO.indexOf(c) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(limpio);
    }
}
