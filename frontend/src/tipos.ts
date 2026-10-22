// Tipos espelhando os formatos da API (backend/src/main/java/.../api/Dtos.java).

export type Origem = 'INSTAGRAM' | 'WHATSAPP' | 'INDICACAO' | 'FEIRA' | 'SITE' | 'OUTRO';
export type StatusTrabalho = 'AGENDADO' | 'FOTOGRAFADO' | 'SELECAO' | 'EDICAO' | 'ENTREGUE' | 'CANCELADO';
export type StatusPedido = 'NOVO' | 'CONTATADO' | 'FECHADO' | 'PERDIDO';
export type FormaPagamento = 'AVISTA' | 'PARCELADO';
export type TipoParcela = 'AVISTA' | 'SINAL' | 'PARCELA' | 'EXTRA' | 'PRODUTO';
export type MeioPagamento = 'PIX' | 'DINHEIRO' | 'TRANSFERENCIA' | 'BOLETO';

export type Perfil = 'ADMIN' | 'COLABORADOR';
export interface Sessao { token: string; nome: string; perfil: Perfil; estudio: string; senhaPadrao?: boolean; precisa2fa?: boolean; desafio?: string | null; doisFatores?: boolean }
export interface UsuarioConta { id: number; nome: string; email: string; perfil: Perfil; ativo: boolean; voce: boolean; doisFatores: boolean }

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
  duracaoMin: number; agendaOnline: boolean;
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
  areaCliente: string; remarcacoes: number; canceladoEm?: string; canceladoPor?: 'CLIENTE' | 'ESTUDIO'; motivoCancelamento?: string;
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
  pesquisaToken?: string | null; areaCliente?: string | null; pixAutomatico?: boolean;
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
  areaCliente?: string | null; contratoToken?: string | null; contratoStatus?: StatusAssinatura | null; pixAutomatico: boolean;
}

export interface EventoAgenda {
  tipo: 'TRABALHO' | 'BLOQUEIO' | 'RESERVA'; id: number; titulo: string; detalhe?: string; inicio: string; fim: string; status?: StatusTrabalho | 'COMPROVANTE' | 'AGUARDANDO';
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

// ---------- fase 3: agendamento online

export type StatusReserva = 'PENDENTE' | 'CONFIRMADA' | 'RECUSADA' | 'EXPIRADA' | 'CANCELADA';
export interface PacoteOnline { id: number; nome: string; preco: number; duracaoMin: number; sinal: number; qtdFotos?: number | null }
export interface AgendaPublica { ativo: boolean; sinalPercentual: number; reservaHoras: number; remarcacaoHoras: number; reembolsoDias: number; pacotes: PacoteOnline[] }
export interface DiaVago { data: string; horarios: string[] }
export interface ReservaPublica {
  token: string; nome: string; pacote: string; inicio: string; fim: string; valorTotal: number; valorSinal: number; status: StatusReserva;
  expiraEm: string; comprovanteEnviado: boolean; chavePix?: string; estudioNome: string; estudioWhatsapp?: string;
  motivoRecusa?: string; areaCliente?: string; cupomCodigo?: string | null; desconto?: number | null; campanhaSlug?: string | null;
  pixAutomatico: boolean; pix?: PixCobranca | null;
}
/** QR Code Pix do PagBank (texto = copia e cola, só enquanto aguarda). */
export interface PixCobranca {
  id: number; texto?: string | null; valor: number; expiraEm: string; status: 'AGUARDANDO' | 'PAGA' | 'EXPIRADA'; sandbox: boolean;
}
export interface ConfigPagBank { ativo: boolean; temToken: boolean; sandbox: boolean; urlNotificacao: string; notificacaoPublica: boolean }
export interface CobrancaPainel {
  id: number; descricao: string; nome?: string; valor: number; status: 'AGUARDANDO' | 'PAGA' | 'EXPIRADA'; sandbox: boolean; pedidoId: string;
  criadoEm: string; expiraEm: string; pagoEm?: string; erro?: string; reservaId?: number; parcelaId?: number;
}
export interface Reserva {
  id: number; token: string; nome: string; telefone: string; email?: string; pacoteId: number; pacote: string; tipo?: string;
  mensagem?: string; origem?: string; inicio: string; fim: string; valorTotal: number; valorSinal: number; status: StatusReserva;
  expiraEm: string; comprovante: boolean; comprovanteEm?: string; comprovanteTipo?: string; motivoRecusa?: string;
  trabalhoId?: number; criadoEm: string; cupomCodigo?: string | null; desconto?: number | null; campanha: boolean;
}
export interface RegrasCliente {
  podeRemarcar: boolean; motivoRemarcar?: string; podeCancelar: boolean; reembolsaSinal: boolean; textoCancelamento?: string; sinalPago: number;
}
export interface AreaCliente {
  nome: string; titulo: string; pacote?: string; tipo?: string; data?: string; duracaoMin: number; local?: string;
  status: StatusTrabalho; canceladoEm?: string; regras: RegrasCliente;
  parcelas: { id: number; descricao: string; valor: number; vencimento?: string; paga: boolean }[];
  galeriaToken?: string; galeriaAberta: boolean; estudioNome: string; estudioWhatsapp?: string; chavePix?: string;
  remarcacaoHoras: number; reembolsoDias: number; contratoToken?: string; contratoStatus?: StatusAssinatura;
  indicacao?: { codigo: string; descontoAmiga: number; recompensa: number; indicacoes: number } | null;
  pixAutomatico: boolean; cpfSalvo: boolean;
}
export interface HorarioAtendimento { diaSemana: number; inicio: string; fim: string }
export interface ConfigAgenda {
  ativo: boolean; antecedenciaHoras: number; horizonteDias: number; intervaloMin: number; passoMin: number; sinalPercentual: number;
  reservaHoras: number; remarcacaoHoras: number; remarcacoesMax: number; reembolsoDias: number; horarios: HorarioAtendimento[];
}

// ---------- Fase 3 · assinatura eletrônica do contrato
export type StatusAssinatura = 'PENDENTE' | 'ASSINADO' | 'CANCELADO';
export interface Assinatura {
  id: number; token: string; codigo: string; status: StatusAssinatura; criadoEm: string; assinadoEm?: string;
  nomeAssinante?: string; cpfAssinante?: string; ip?: string; desatualizado: boolean;
}
export interface AssinaturaPublica {
  status: StatusAssinatura; titulo: string; texto: string; contratante: string; estudioNome: string; estudioWhatsapp?: string;
  responsavel?: string; enviadoEm: string; assinadoEm?: string; nomeAssinante?: string; codigo?: string; hashTexto: string;
  perguntaUsoImagem: boolean; autorizaImagem?: boolean | null; areaCliente?: string | null;
}
export interface Verificacao {
  valido: boolean; codigo?: string; estudio?: string; contratante?: string; cpf?: string; assinadoEm?: string; enviadoEm?: string;
  hashTexto?: string; hashPdf?: string; arquivoConfere?: boolean | null;
}

// ---------- Fase 3 · bloco 3: relacionamento e equipe
export interface ProximaData {
  clienteId: number; nome: string; telefone?: string; tipo: 'ANIVERSARIO' | 'DATA_ESPECIAL'; descricao: string; data: string;
  diasFaltando: number; anos?: number | null; dataId?: number | null;
}
export interface DataEspecial { id: number; descricao: string; dia: number; mes: number; ano?: number | null }
export interface Inativa {
  clienteId: number; nome: string; telefone?: string; ultimoEnsaio: string; ultimoTrabalho: string; totalTrabalhos: number;
  totalPago: number; mesesSemEnsaio: number; ultimoContato?: string | null;
}
export interface PesquisaPainel {
  id: number; trabalhoId: number; trabalho: string; cliente: string; token: string; nota?: number | null; atendimento?: number | null;
  fotos?: number | null; prazo?: number | null; comentario?: string | null; autorizaDepoimento?: boolean | null; criadoEm: string; respondidaEm?: string | null;
}
export interface ResumoPesquisas {
  enviadas: number; respondidas: number; nps?: number | null; promotores: number; neutros: number; detratores: number;
  mediaAtendimento?: number | null; mediaFotos?: number | null; mediaPrazo?: number | null;
}
export interface PesquisaPublica { cliente: string; estudioNome: string; estudioInstagram?: string; respondida: boolean; nota?: number | null }
export interface Freelancer {
  id: number; nome: string; telefone?: string; funcao?: string; chavePix?: string; observacoes?: string; ativo: boolean;
  aPagar: number; pago: number; trabalhos: number;
}
export interface Comissao {
  id: number; trabalhoId: number; trabalho: string; freelancerId: number; freelancer: string; telefone?: string; chavePix?: string;
  funcao?: string; percentual?: number | null; valor: number; pagoEm?: string | null;
}
export type StatusTarefa = 'A_FAZER' | 'FAZENDO' | 'FEITA';
export interface Tarefa {
  id: number; trabalhoId?: number | null; trabalho?: string | null; titulo: string; descricao?: string | null;
  responsavelUsuarioId?: number | null; responsavelFreelancerId?: number | null; responsavel?: string | null; prazo?: string | null;
  status: StatusTarefa; automatica: boolean; atrasada: boolean; criadoEm: string; concluidaEm?: string | null;
}
export interface Pessoa { id: number; nome: string; tipo: 'USUARIO' | 'FREELANCER' }

// ---------- Fase 3 · bloco 4: produtos e estoque
export type TipoProduto = 'ALBUM' | 'QUADRO' | 'IMPRESSAO' | 'OUTRO';
export type StatusPedidoProduto = 'NOVO' | 'DIAGRAMACAO' | 'APROVACAO' | 'APROVADO' | 'LABORATORIO' | 'PRONTO' | 'ENTREGUE' | 'CANCELADO';
export type Aprovacao = 'PENDENTE' | 'APROVADO' | 'AJUSTES';
export interface Lamina { id: number; versao: number; ordem: number; nome?: string; largura?: number; altura?: number; comentario?: string | null }
export interface PedidoProduto {
  id: number; trabalhoId: number; trabalho: string; cliente: string; clienteTelefone?: string; tipo: TipoProduto; descricao: string;
  quantidade: number; valorVenda: number; custo?: number | null; laboratorio?: string | null; status: StatusPedidoProduto;
  prazo?: string | null; previsaoLab?: string | null; enviadoLabEm?: string | null; recebidoEm?: string | null; entregueEm?: string | null;
  observacoes?: string | null; cobrado: boolean; recebido: boolean; custoLancado: boolean; custoPago: boolean; tokenAprovacao?: string | null;
  versaoLayout: number; aprovacao?: Aprovacao | null; aprovacaoEm?: string | null; comentarioCliente?: string | null; atrasado: boolean;
  laminas: Lamina[]; versoesAnteriores: Lamina[]; criadoEm: string;
}
export interface AlbumPublico {
  cliente: string; estudioNome: string; estudioWhatsapp?: string; descricao: string; versao: number; aprovacao?: Aprovacao | null;
  aprovacaoEm?: string | null; comentarioCliente?: string | null; laminas: Lamina[];
}
export interface Material {
  id: number; nome: string; unidade: string; quantidade: number; minimo: number; custoUnitario?: number | null; ativo: boolean; baixo: boolean;
}
export type TipoMovimento = 'ENTRADA' | 'SAIDA' | 'AJUSTE';
export interface MovimentoMaterial {
  id: number; materialId: number; material: string; unidade: string; tipo: TipoMovimento; quantidade: number; saldo: number;
  motivo?: string | null; pedidoId?: number | null; usuario?: string | null; criadoEm: string;
}

// ---------- Fase 3 · bloco 5: marketing
export interface DiaCampanhaForm { data: string; inicio: string; fim: string; local?: string | null }
export interface Campanha {
  id: number; slug: string; titulo: string; descricao?: string | null; preco: number; sinalPercentual: number; duracaoMin: number;
  intervaloMin: number; qtdFotos?: number | null; precoFotoExtra?: number | null; prazoEntregaDias: number; capa: boolean; ativa: boolean;
  dias: DiaCampanhaForm[]; reservas: number; confirmadas: number; faturamento: number;
}
export interface CampanhaPublica {
  slug: string; titulo: string; descricao?: string | null; preco: number; sinal: number; sinalPercentual: number; duracaoMin: number;
  qtdFotos?: number | null; precoFotoExtra?: number | null; prazoEntregaDias: number; capa: boolean; estudioNome: string;
  estudioWhatsapp?: string; reservaHoras: number; dias: { data: string; local?: string | null; horarios: string[] }[]; esgotada: boolean;
}
export interface CampanhaResumo { slug: string; titulo: string; preco: number; capa: boolean; primeiroDia: string }
export type TipoCupom = 'PERCENTUAL' | 'VALOR';
export interface Cupom {
  id: number; codigo: string; descricao?: string | null; tipo: TipoCupom; valor: number; validade?: string | null; usosMax?: number | null;
  usos: number; campanhaId?: number | null; ativo: boolean; vencido: boolean;
}
export interface Indicacao {
  id: number; indicadorId: number; indicador: string; indicadorTelefone?: string; indicado: string; trabalhoId?: number | null;
  recompensa: number; status: 'CONFIRMADA' | 'RECOMPENSADA' | 'CANCELADA'; criadoEm: string; recompensadaEm?: string | null;
}
export interface ConfigIndicacao { ativa: boolean; desconto: number; recompensa: number }
export interface Desconto { valido: boolean; mensagem: string; origem?: 'CUPOM' | 'INDICACAO'; codigo?: string; desconto: number; valorFinal?: number }

// ---------- Fase 3 · bloco 6: segurança
export interface Situacao2fa { ativo: boolean; codigosRestantes: number; configurando: boolean }
export interface Atividade {
  id: number; usuarioId?: number | null; usuario?: string | null; acao: string; detalhe?: string | null; entidade?: string | null;
  entidadeId?: number | null; ip?: string | null; criadoEm: string;
}
