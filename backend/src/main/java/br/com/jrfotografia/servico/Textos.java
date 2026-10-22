package br.com.jrfotografia.servico;

/** Apoio para textos vindos de formulários (uma versão só para o sistema todo). */
public final class Textos {

    private Textos() {}

    /** Nulo ou só espaços. */
    public static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    /** O texto sem espaços nas pontas, ou null se vier vazio. */
    public static String vazioNulo(String s) {
        return vazio(s) ? null : s.trim();
    }
}
