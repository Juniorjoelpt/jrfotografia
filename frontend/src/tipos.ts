// Tipos espelhando os formatos da API (backend/src/main/java/.../api/Dtos.java).

export type Origem = 'INSTAGRAM' | 'WHATSAPP' | 'INDICACAO' | 'FEIRA' | 'SITE' | 'OUTRO';
export type StatusTrabalho = 'AGENDADO' | 'FOTOGRAFADO' | 'SELECAO' | 'EDICAO' | 'ENTREGUE' | 'CANCELADO';
export type StatusPedido = 'NOVO' | 'CONTATADO' | 'FECHADO' | 'PERDIDO';
export type FormaPagamento = 'AVISTA' | 'PARCELADO';
export type TipoParcela = 'AVISTA' | 'SINAL' | 'PARCELA' | 'EXTRA';
export type MeioPagamento = 'PIX' | 'DINHEIRO' | 'TRANSFERENCIA' | 'BOLETO';

export interface Sessao { token: string; nome: string; perfil: 'ADMIN' | 'COLABORADOR'; estudio: string }

export interface Estudio {
  nome: string; slug: string; cidade?: string; whatsapp?: string; instagram?: string; email?: string;
  chavePix?: string; apresentacao?: string; dominio?: string;
}

export interface Cliente {
  id: number; nome: string; telefone?: string; email?: string; cpf?: string; dataNascimento?: string;
  origem?: Origem; observacoes?: string; criadoEm: string;
}

export interface Pacote {
  id: number; nome: string; qtdFotos: number | null; preco: number; precoFotoExtra: number | null;
  prazoEntregaDias: number; itens: string[]; materialEntregue?: string; publico: boolean; ativo: boolean; ordem: number;
}

export interface Pedido {
  id: number; nome: string; telefone: string; email?: string; tipoTrabalho?: string; pacoteId?: number;
  dataDesejada?: string; mensagem?: string; origem?: Origem; status: StatusPedido; clienteId?: number; criadoEm: string;
}

export interface TrabalhoResumo {
  id: number; titulo: string; clienteNome: string; clienteTelefone?: string; pacoteNome?: string; tipo?: string;
  dataTrabalho?: string; localTrabalho?: string; status: StatusTrabalho; prazoEntrega?: string; valorTotal: number;
  atrasado: boolean;
}

export interface Parcela {
  id: number; trabalhoId: number; tipo: TipoParcela; descricao: string; valor: number; vencimento?: string;
  pagoEm?: string; forma?: MeioPagamento;
}

export interface TrabalhoDetalhe {
  resumo: TrabalhoResumo; cliente: Cliente; pacote?: Pacote; formaPagamento: FormaPagamento; galeriaToken: string;
  galeriaComSenha: boolean; galeriaExpiraEm?: string; selecaoFechadaEm?: string; entregueEm?: string;
  observacoes?: string; totalFotos: number; selecionadas: number; editadas: number; parcelas: Parcela[];
  downloadLiberado: boolean; motivoDownload?: string;
}

export interface Foto {
  id: number; nomeOriginal: string; largura?: number; altura?: number; ordem: number; selecionada: boolean;
  editada: boolean; comentario?: string;
}

export interface PortfolioFoto { id: number; categoria: string; titulo?: string; largura?: number; altura?: number; ordem: number; destaque: boolean }

export interface Painel {
  proximos: TrabalhoResumo[]; entregasProximas: TrabalhoResumo[]; atrasados: TrabalhoResumo[];
  aguardandoSelecao: number; emEdicao: number; pedidosNovos: number;
  aReceber: number | null; vencido: number | null; recebidoNoMes: number | null;
}

export interface ContaReceber { parcela: Parcela; trabalhoTitulo: string; clienteNome: string; clienteTelefone?: string; vencida: boolean }

export interface FotoGaleria { id: number; largura?: number; altura?: number; selecionada: boolean; comentario?: string }

export interface Galeria {
  titulo: string; clienteNome: string; estudioNome: string; estudioWhatsapp?: string; status: StatusTrabalho;
  exigeSelecao: boolean; fotosNoPacote: number | null; precoFotoExtra: number | null; selecaoAberta: boolean;
  selecionadas: number; downloadLiberado: boolean; motivoDownload?: string; expiraEm?: string; fotos: FotoGaleria[];
}
