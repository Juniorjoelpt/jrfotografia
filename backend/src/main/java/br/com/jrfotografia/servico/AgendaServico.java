package br.com.jrfotografia.servico;

import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Fase2.BloqueioAgenda;
import br.com.jrfotografia.dominio.Repositorios.BloqueioRepo;
import br.com.jrfotografia.dominio.Repositorios.TrabalhoRepo;
import br.com.jrfotografia.dominio.Trabalho;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Agenda: ensaios e bloqueios no mesmo calendário, e aviso de choque de horário. */
@Service
public class AgendaServico {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final TrabalhoRepo trabalhos;
    private final BloqueioRepo bloqueios;

    public AgendaServico(TrabalhoRepo trabalhos, BloqueioRepo bloqueios) {
        this.trabalhos = trabalhos;
        this.bloqueios = bloqueios;
    }

    public enum TipoEvento { TRABALHO, BLOQUEIO }

    public record Evento(TipoEvento tipo, Long id, String titulo, String detalhe, LocalDateTime inicio,
                         LocalDateTime fim, String status) {}

    public List<Evento> eventos(Long estudioId, LocalDateTime de, LocalDateTime ate) {
        List<Evento> lista = new ArrayList<>();
        // Ensaios que começam até 1 dia antes podem atravessar o início do período.
        for (Trabalho t : trabalhos.findByEstudioIdAndDataTrabalhoBetweenOrderByDataTrabalho(estudioId, de.minusDays(1), ate)) {
            if (t.getStatus() == StatusTrabalho.CANCELADO || !t.fim().isAfter(de)) continue;
            lista.add(new Evento(TipoEvento.TRABALHO, t.getId(), t.getCliente().getNome(),
                    (t.getPacote() != null ? t.getPacote().getNome() : t.getTipo() != null ? t.getTipo() : "Ensaio")
                            + (t.getLocalTrabalho() != null ? " · " + t.getLocalTrabalho() : ""),
                    t.getDataTrabalho(), t.fim(), t.getStatus().name()));
        }
        for (BloqueioAgenda b : bloqueios.noPeriodo(estudioId, de, ate)) {
            lista.add(new Evento(TipoEvento.BLOQUEIO, b.getId(), b.getMotivo(), null, b.getInicio(), b.getFim(), null));
        }
        lista.sort(Comparator.comparing(Evento::inicio));
        return lista;
    }

    /** Frases curtas sobre o que já ocupa o horário pedido. Lista vazia = horário livre. */
    public List<String> conflitos(Long estudioId, LocalDateTime inicio, int duracaoMin, Long ignorarTrabalhoId) {
        if (inicio == null) return List.of();
        LocalDateTime fim = inicio.plusMinutes(Math.max(duracaoMin, 30));
        List<String> avisos = new ArrayList<>();
        for (Evento e : eventos(estudioId, inicio, fim)) {
            boolean choca = e.inicio().isBefore(fim) && e.fim().isAfter(inicio);
            if (!choca) continue;
            if (e.tipo() == TipoEvento.TRABALHO && e.id().equals(ignorarTrabalhoId)) continue;
            avisos.add(e.tipo() == TipoEvento.BLOQUEIO
                    ? "Agenda bloqueada: " + e.titulo() + " (" + HORA.format(e.inicio()) + ")"
                    : "Já há ensaio com " + e.titulo() + " em " + HORA.format(e.inicio()));
        }
        return avisos;
    }
}
