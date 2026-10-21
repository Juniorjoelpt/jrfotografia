package br.com.jrfotografia.dominio;

/** Enumerações do domínio, reunidas num só lugar para facilitar a leitura. */
public final class Enums {

    private Enums() {}

    public enum Perfil { ADMIN, COLABORADOR }

    public enum Origem { INSTAGRAM, WHATSAPP, INDICACAO, FEIRA, SITE, OUTRO }

    public enum StatusPedido { NOVO, CONTATADO, FECHADO, PERDIDO }

    /** Etapas do trabalho: Agendado → Fotografado → Seleção → Edição → Entregue. */
    public enum StatusTrabalho { AGENDADO, FOTOGRAFADO, SELECAO, EDICAO, ENTREGUE, CANCELADO }

    public enum FormaPagamento { AVISTA, PARCELADO }

    public enum TipoParcela { AVISTA, SINAL, PARCELA, EXTRA }

    public enum MeioPagamento { PIX, DINHEIRO, TRANSFERENCIA, BOLETO }

    /** Rascunho → Enviado → Aceito (vira trabalho) ou Recusado; vencida a validade, Expirado. */
    public enum StatusOrcamento { RASCUNHO, ENVIADO, ACEITO, RECUSADO, EXPIRADO }
    /** Pré-reserva do site: pendente até o fotógrafo confirmar o sinal. */
    public enum StatusReserva { PENDENTE, CONFIRMADA, RECUSADA, EXPIRADA, CANCELADA }
}
