package br.com.jrfotografia.servico;

import br.com.jrfotografia.dominio.Enums.StatusTrabalho;
import br.com.jrfotografia.dominio.Estudio;
import br.com.jrfotografia.dominio.Parcela;
import br.com.jrfotografia.dominio.Repositorios.EstudioRepo;
import br.com.jrfotografia.dominio.Repositorios.ParcelaRepo;
import br.com.jrfotografia.dominio.Repositorios.TrabalhoRepo;
import br.com.jrfotografia.dominio.Trabalho;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Lembretes automáticos por e-mail, todo dia às 8h (fuso do estúdio):
 * ensaio amanhã; parcela que vence em 2 dias; parcela vencida há 1 dia. Cada um vai uma vez só.
 */
@Component
public class Lembretes {

    private final EstudioRepo estudios;
    private final TrabalhoRepo trabalhos;
    private final ParcelaRepo parcelas;
    private final EmailAvisos avisos;

    public Lembretes(EstudioRepo estudios, TrabalhoRepo trabalhos, ParcelaRepo parcelas, EmailAvisos avisos) {
        this.estudios = estudios;
        this.trabalhos = trabalhos;
        this.parcelas = parcelas;
        this.avisos = avisos;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void todoDia() {
        executar(LocalDate.now());
    }

    /** Devolve quantos lembretes foram para a fila. */
    @Transactional
    public int executar(LocalDate hoje) {
        int n = 0;
        for (Estudio e : estudios.findAll()) {
            if (!e.isEmailAtivo() || !e.isEmailLembretes()) continue;
            LocalDate amanha = hoje.plusDays(1);
            for (Trabalho t : trabalhos.findByEstudioIdAndDataTrabalhoBetweenOrderByDataTrabalho(e.getId(), amanha.atStartOfDay(), amanha.plusDays(1).atStartOfDay())) {
                if (t.getStatus() != StatusTrabalho.AGENDADO || t.getCliente().getEmail() == null) continue;
                avisos.lembreteEnsaio(t);
                n++;
            }
            for (Parcela p : parcelas.findByEstudioIdAndPagoEmIsNullOrderByVencimento(e.getId())) {
                if (p.getVencimento() == null) continue;
                boolean vence = p.getVencimento().equals(hoje.plusDays(2));
                boolean venceu = p.getVencimento().equals(hoje.minusDays(1));
                if (!vence && !venceu) continue;
                Trabalho t = trabalhos.findById(p.getTrabalhoId()).orElse(null);
                if (t == null || t.getStatus() == StatusTrabalho.CANCELADO || t.getCliente().getEmail() == null) continue;
                avisos.lembreteParcela(p, t, venceu);
                n++;
            }
        }
        return n;
    }
}
