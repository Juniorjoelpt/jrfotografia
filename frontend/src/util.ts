import type { StatusTrabalho, Origem, StatusPedido, TipoParcela, MeioPagamento } from './tipos';

const moedaFmt = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
export const moeda = (v: number | null | undefined) => (v == null ? '—' : moedaFmt.format(v));

export function data(iso?: string | null) {
  if (!iso) return '—';
  const [a, m, d] = iso.slice(0, 10).split('-');
  return `${d}/${m}/${a}`;
}

export function dataHora(iso?: string | null) {
  if (!iso) return '—';
  const hora = iso.length > 10 ? iso.slice(11, 16) : '';
  return hora ? `${data(iso)} às ${hora}` : data(iso);
}

export function diaSemana(iso?: string | null) {
  if (!iso) return '';
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number);
  return ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'][new Date(a, m - 1, d).getDay()];
}

export function telefone(t?: string | null) {
  if (!t) return '';
  const d = t.replace(/\D/g, '');
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return t;
}

/** Link do WhatsApp com a mensagem pronta. Números brasileiros ganham o 55 na frente. */
export function linkWhatsapp(numero: string | null | undefined, mensagem: string) {
  const d = (numero ?? '').replace(/\D/g, '');
  const completo = d.length <= 11 ? `55${d}` : d;
  return `https://wa.me/${completo}?text=${encodeURIComponent(mensagem)}`;
}

export const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

/** Dia e mês por extenso a partir de uma data ISO: { dia: '24', mes: 'Outubro' }. */
export function diaMes(iso?: string | null) {
  if (!iso) return null;
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number);
  return { dia: String(d), mes: MESES[m - 1], ano: a };
}

/** Inclinação estável de -3° a 3° por foto, para o efeito de cópias soltas. */
export const inclinacao = (id: number) => (((id * 7919) % 13) - 6) / 2;

export const primeiroNome =(nome: string) => nome.trim().split(/\s+/)[0];

export const NOMES_STATUS: Record<StatusTrabalho, string> = {
  AGENDADO: 'Agendado', FOTOGRAFADO: 'Fotografado', SELECAO: 'Em seleção', EDICAO: 'Em edição',
  ENTREGUE: 'Entregue', CANCELADO: 'Cancelado',
};

export const CLASSE_STATUS: Record<StatusTrabalho, string> = {
  AGENDADO: '', FOTOGRAFADO: '', SELECAO: 'cheia', EDICAO: 'alerta', ENTREGUE: 'sucesso', CANCELADO: 'erro',
};

export const ETAPAS: StatusTrabalho[] = ['AGENDADO', 'FOTOGRAFADO', 'SELECAO', 'EDICAO', 'ENTREGUE'];

export const NOMES_ORIGEM: Record<Origem, string> = {
  INSTAGRAM: 'Instagram', WHATSAPP: 'WhatsApp', INDICACAO: 'Indicação', FEIRA: 'Feira ou evento', SITE: 'Site', OUTRO: 'Outro',
};

export const NOMES_PEDIDO: Record<StatusPedido, string> = {
  NOVO: 'Novo', CONTATADO: 'Em conversa', FECHADO: 'Fechado', PERDIDO: 'Perdido',
};

export const NOMES_PARCELA: Record<TipoParcela, string> = { AVISTA: 'À vista', SINAL: 'Sinal', PARCELA: 'Parcela', EXTRA: 'Fotos extras' };

export const NOMES_MEIO: Record<MeioPagamento, string> = { PIX: 'Pix', DINHEIRO: 'Dinheiro', TRANSFERENCIA: 'Transferência', BOLETO: 'Boleto' };

export const urlGaleria = (token: string) => `${window.location.origin}/g/${token}`;
