package br.com.jrfotografia.servico;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Prazos de entrega em dias úteis (segunda a sexta), como na proposta comercial. */
public final class Prazos {

    private Prazos() {}

    public static LocalDate somarDiasUteis(LocalDate inicio, int dias) {
        LocalDate d = inicio;
        int contados = 0;
        while (contados < dias) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) contados++;
        }
        return d;
    }
}
