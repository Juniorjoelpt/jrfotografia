package br.com.jrfotografia.dominio;

import br.com.jrfotografia.dominio.Enums.StatusReserva;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Entidades do agendamento online: janelas de atendimento e pré-reservas feitas no site. */
public final class Agendamento {

    private Agendamento() {}

    @Entity(name = "HorarioAtendimento")
    @Table(name = "horario_atendimento")
    @Getter
    @Setter
    public static class HorarioAtendimento {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        /** 1 = segunda ... 7 = domingo (igual a DayOfWeek). */
        private int diaSemana;
        private LocalTime inicio;
        private LocalTime fim;
    }

    @Entity(name = "Reserva")
    @Table(name = "reserva")
    @Getter
    @Setter
    public static class Reserva {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long estudioId;
        private String token;

        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "pacote_id")
        private Pacote pacote;

        private LocalDateTime inicio;
        private LocalDateTime fim;
        private String nome;
        private String telefone;
        private String email;
        private String tipo;
        private String mensagem;
        private String origem;
        private BigDecimal valorTotal;
        private BigDecimal valorSinal;
        /** Mini-sessão de campanha (null = agenda normal). */
        private Long campanhaId;
        private String cupomCodigo;
        private BigDecimal desconto;
        private Long indicadorClienteId;

        @Enumerated(EnumType.STRING)
        private StatusReserva status = StatusReserva.PENDENTE;

        private LocalDateTime expiraEm;
        private String comprovante;
        private String comprovanteTipo;
        private LocalDateTime comprovanteEm;
        private String motivoRecusa;
        private Long clienteId;
        private Long trabalhoId;
        private LocalDateTime criadoEm = LocalDateTime.now();

        /** Sem comprovante, a pré-reserva vence e o horário volta a ficar livre. */
        public StatusReserva statusAtual() {
            if (status == StatusReserva.PENDENTE && comprovanteEm == null && expiraEm.isBefore(LocalDateTime.now())) {
                return StatusReserva.EXPIRADA;
            }
            return status;
        }

        /** Ocupa a agenda enquanto pendente (dentro do prazo ou com comprovante enviado). */
        public boolean ocupaHorario() {
            return statusAtual() == StatusReserva.PENDENTE;
        }
    }
}
