package br.com.jrfotografia.servico;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Valor em reais por extenso, para recibos e contratos: 350,50 → "trezentos e cinquenta reais e cinquenta centavos". */
public final class Extenso {

    private Extenso() {}

    private static final String[] UNIDADES = {"", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove",
            "dez", "onze", "doze", "treze", "quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove"};
    private static final String[] DEZENAS = {"", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa"};
    private static final String[] CENTENAS = {"", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos",
            "setecentos", "oitocentos", "novecentos"};

    public static String reais(BigDecimal valor) {
        BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP);
        long inteiro = v.longValue();
        int centavos = v.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        StringBuilder sb = new StringBuilder();
        if (inteiro > 0) {
            sb.append(numero(inteiro)).append(inteiro == 1 ? " real" : (inteiro % 1_000_000 == 0 ? " de reais" : " reais"));
        }
        if (centavos > 0) {
            if (!sb.isEmpty()) sb.append(" e ");
            sb.append(numero(centavos)).append(centavos == 1 ? " centavo" : " centavos");
        }
        return sb.isEmpty() ? "zero real" : sb.toString();
    }

    static String numero(long n) {
        if (n == 0) return "zero";
        if (n >= 1_000_000) {
            long milhoes = n / 1_000_000, resto = n % 1_000_000;
            String m = numero(milhoes) + (milhoes == 1 ? " milhão" : " milhões");
            return resto == 0 ? m : m + (resto < 100 || resto % 100 == 0 && resto < 1000 ? " e " : ", ") + numero(resto);
        }
        if (n >= 1000) {
            long milhares = n / 1000, resto = n % 1000;
            String m = milhares == 1 ? "mil" : ate999((int) milhares) + " mil";
            if (resto == 0) return m;
            return m + (resto < 100 || resto % 100 == 0 ? " e " : " ") + ate999((int) resto);
        }
        return ate999((int) n);
    }

    private static String ate999(int n) {
        if (n == 100) return "cem";
        int c = n / 100, resto = n % 100;
        StringBuilder sb = new StringBuilder(CENTENAS[c]);
        if (resto > 0) {
            if (c > 0) sb.append(" e ");
            if (resto < 20) sb.append(UNIDADES[resto]);
            else {
                sb.append(DEZENAS[resto / 10]);
                if (resto % 10 > 0) sb.append(" e ").append(UNIDADES[resto % 10]);
            }
        }
        return sb.toString();
    }
}
