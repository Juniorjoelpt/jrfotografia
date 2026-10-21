// Tipos espelhando os formatos da API (backend/src/main/java/.../api/Dtos.java).

export type Origem = 'INSTAGRAM' | 'WHATSAPP' | 'INDICACAO' | 'FEIRA' | 'SITE' | 'OUTRO';
export type StatusTrabalho = 'AGENDADO' | 'FOTOGRAFADO' | 'SELECAO' | 'EDICAO' | 'ENTREGUE' | 'CANCELADO';
export type StatusPedido = 'NOVO' | 'CONTATADO' | 'FECHADO' | 'PERDIDO';
export type FormaPagamento = 'AVISTA' | 'PARCELADO';
export type TipoParcela = 'AVISTA' | 'SINAL' | 'PARCELA' | 'EXTRA';
export type MeioPagamento = 'PIX' | 'DINHEIRO' | 'TRANSFERENCIA' | 'BOLETO';

export type Perfil = 'ADMIN' | 'COLABORADOR';
export interface Sessao { token: string; nome: string; perfil: Perfil; estudio: string; senhaPadrao?: boolean }
export interface UsuarioConta { id: number; nome: string; email: string; perfil: Perfil; ativo: boolean; voce: boolean }

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
  atrasado: boolean; capas?: number[] | null; totalFotos?: number | null;
}

export interface Parcela {
  id: number; trabalhoId: number; tipo: TipoParcela; descricao: string; valor: number; vencimento?: string;
  pagoEm?: string; forma?: MeioPagamento;
}

export interface TrabalhoDetalhe {
  resumo: TrabalhoResumo; cliente: Cliente; pacote?: Pacote; formaPagamento: FormaPagamento; galeriaToken: string;
  galeriaComSenha: boolean; galeriaExpiraEm?: string; selecaoFechadaEm?: string; entregueEm?: string;
  observacoes?: string; totalFotos: number; selecionadas: number; editadas: number; parcelas: Parcela[];
  downloadLiberado: boolean; motivoDownload?: string; duracaoMin: number; orcamentoId?: number; conflitos: string[];
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

export interface FotoGaleria { id: number; largura?: number; altura?: number; selecionada: boolean; comentario?: string; editada?: boolean }

export interface Galeria {
  titulo: string; clienteNome: string; dataTrabalho?: string; estudioNome: string; estudioWhatsapp?: string; status: StatusTrabalho;
  exigeSelecao: boolean; fotosNoPacote: number | null; precoFotoExtra: number | null; selecaoAberta: boolean;
  selecionadas: number; downloadLiberado: boolean; motivoDownload?: string; expiraEm?: string; fotos: FotoGaleria[];
}

// ---------- fase 2: agenda, orçamentos, financeiro e relatórios

export type StatusOrcamento = 'RASCUNHO' | 'ENVIADO' | 'ACEITO' | 'RECUSADO' | 'EXPIRADO';

export interface ItemOrcamento { descricao: string; quantidade: number; valorUnit: number; total: number }

export interface Orcamento {
  id: number; numero: number; token: string; clienteId?: number; pedidoId?: number; nomeCliente: string; telefone?: string;
  email?: string; pacoteId?: number; pacoteNome?: string; tipo?: string; dataPrevista?: string; localPrevisto?: string;
  itens: ItemOrcamento[]; subtotal: number; desconto: number; valorTotal: number; formaPagamento: FormaPagamento;
  valorSinal?: number; numParcelas?: number; validade: string; observacoes?: string; status: StatusOrcamento;
  enviadoEm?: string; respondidoEm?: string; trabalhoId?: number; criadoEm: string;
}

export interface OrcamentoPublico {
  numero: number; nomeCliente: string; estudioNome: string; estudioWhatsapp?: string; estudioCidade?: string;
  pacoteNome?: string; incluso: string[]; prazoEntregaDias?: number; precoFotoExtra?: number; tipo?: string;
  dataPrevista?: string; localPrevisto?: string; itens: ItemOrcamento[]; subtotal: number; desconto: number;
  valorTotal: number; condicoes: string; validade: string; observacoes?: string; status: StatusOrcamento;
  contratoDisponivel: boolean;
}

export interface EventoAgenda {
  tipo: 'TRABALHO' | 'BLOQUEIO'; id: number; titulo: string; detalhe?: string; inicio: string; fim: string; status?: StatusTrabalho;
}

export interface Despesa {
  id: number; descricao: string; categoria: string; valor: number; vencimento: string; pagoEm?: string; trabalhoId?: number; vencida: boolean;
}

export interface MesCaixa { mes: number; entrou: number; saiu: number; aEntrar: number; aSair: number; saldo: number }
export interface Fluxo { ano: number; meses: MesCaixa[]; entrouAno: number; saiuAno: number; saldoAno: number; aEntrarAno: number; aSairAno: number }

export interface Fatia { nome: string; quantidade: number; valor: number }
export interface Relatorio {
  ano: number; faturamentoMensal: number[]; faturamentoAno: number; trabalhos: number; ticketMedio: number;
  porTipo: Fatia[]; porOrigem: Fatia[]; pedidos: number; pedidosFechados: number; orcamentosEnviados: number;
  orcamentosAceitos: number; conversaoOrcamentos: number | null; conversaoPedidos: number | null; despesasPorCategoria: Fatia[];
}

export interface ItemAvulso { id: number; nome: string; preco: number; ativo: boolean }
export interface ContratoModelo { texto: string; padrao: boolean; campos: string; responsavel?: string; documento?: string }
