import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, ErroApi } from '../api';
import type { Material, MovimentoMaterial, PedidoProduto, StatusPedidoProduto, TipoMovimento, TipoProduto, TrabalhoResumo } from '../tipos';
import { data, dataHora, moeda, NOMES_PEDIDO_PRODUTO, NOMES_PRODUTO, numeroBr, qtd } from '../util';
import { Erro, useDados } from './comum';

type Aba = 'pedidos' | 'estoque';

/** Produtos: pedidos de álbum, quadro e impressões (com o laboratório) e estoque de materiais. */
export function Produtos() {
  const [busca] = useSearchParams();
  const [aba, setAba] = useState<Aba>(() => (window.location.hash === '#estoque' ? 'estoque' : 'pedidos'));
  const trocar = (a: Aba) => { setAba(a); history.replaceState(null, '', `#${a}`); };
  const novoPara = busca.get('novo');
  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Produtos</span><h1 className="titulo">{aba === 'pedidos' ? 'Pedidos' : 'Estoque'}</h1></div></div>
      <div className="abas" role="group" aria-label="Seções">
        <button className="botao pequeno" aria-pressed={aba === 'pedidos'} onClick={() => trocar('pedidos')}>Pedidos</button>
        <button className="botao pequeno" aria-pressed={aba === 'estoque'} onClick={() => trocar('estoque')}>Estoque de materiais</button>
      </div>
      {aba === 'pedidos' ? <Pedidos novoPara={novoPara ? Number(novoPara) : null} /> : <Estoque />}
    </>
  );
}

const ABERTOS: StatusPedidoProduto[] = ['NOVO', 'DIAGRAMACAO', 'APROVACAO', 'APROVADO', 'LABORATORIO', 'PRONTO'];

export function EtiquetaPedido({ p }: { p: PedidoProduto }) {
  const cls = p.status === 'ENTREGUE' ? ' sucesso' : p.status === 'CANCELADO' ? '' : p.atrasado ? ' erro'
    : p.status === 'APROVACAO' || (p.aprovacao === 'AJUSTES' && p.status === 'DIAGRAMACAO') ? ' alerta' : '';
  const texto = p.aprovacao === 'AJUSTES' && p.status === 'DIAGRAMACAO' ? 'Ajustes pedidos' : NOMES_PEDIDO_PRODUTO[p.status];
  return <span className={`etiqueta${cls}`}>{p.atrasado && p.status !== 'ENTREGUE' ? 'Atrasado · ' : ''}{texto}</span>;
}

export function LinhaPedido({ p, mostrarCliente = true }: { p: PedidoProduto; mostrarCliente?: boolean }) {
  return (
    <Link to={`/painel/produtos/${p.id}`} className="linha-item">
      <span className="principal">{NOMES_PRODUTO[p.tipo]} · {p.descricao}{p.quantidade > 1 ? ` × ${p.quantidade}` : ''}</span>
      <span className="lado"><EtiquetaPedido p={p} /></span>
      <span className="legenda">
        {mostrarCliente ? `${p.cliente.toUpperCase()} · ` : ''}{p.laboratorio ? `${p.laboratorio.toUpperCase()} · ` : ''}
        {p.prazo ? `PRAZO ${data(p.prazo)}` : `CRIADO ${data(p.criadoEm)}`}
      </span>
      <span className="lado legenda num">{p.valorVenda > 0 ? moeda(p.valorVenda) : ''}</span>
    </Link>
  );
}

function Pedidos({ novoPara }: { novoPara: number | null }) {
  const { dados, erro } = useDados<PedidoProduto[]>('/produtos/pedidos');
  const [filtro, setFiltro] = useState<'abertos' | 'todos'>('abertos');
  const [criando, setCriando] = useState(novoPara != null);
  const navegar = useNavigate();
  const lista = (dados ?? []).filter((p) => filtro === 'todos' || ABERTOS.includes(p.status));
  return (
    <section className="secao">
      <div className="acoes-linha">
        <button className="botao principal pequeno" onClick={() => setCriando(true)}>Novo pedido</button>
        <div className="abas" role="group" aria-label="Filtro">
          <button className="botao pequeno" aria-pressed={filtro === 'abertos'} onClick={() => setFiltro('abertos')}>Em andamento</button>
          <button className="botao pequeno" aria-pressed={filtro === 'todos'} onClick={() => setFiltro('todos')}>Todos</button>
        </div>
      </div>
      {criando && <FormPedido trabalhoId={novoPara} aoSalvar={(p) => navegar(`/painel/produtos/${p.id}`)} aoCancelar={() => setCriando(false)} />}
      <Erro msg={erro} />
      {dados && (lista.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>{filtro === 'abertos' ? 'Nenhum pedido em andamento.' : 'Nenhum pedido ainda.'}</p>
        : <div className="linhas escalonado">{lista.map((p) => <LinhaPedido key={p.id} p={p} />)}</div>)}
    </section>
  );
}

export function FormPedido({ trabalhoId, pedido, aoSalvar, aoCancelar }: {
  trabalhoId?: number | null; pedido?: PedidoProduto; aoSalvar: (p: PedidoProduto) => void; aoCancelar: () => void;
}) {
  const { dados: trabalhos } = useDados<TrabalhoResumo[]>(pedido ? null : '/trabalhos');
  const [erro, setErro] = useState('');
  const [tipo, setTipo] = useState<TipoProduto>(pedido?.tipo ?? 'ALBUM');

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const corpo = {
      trabalhoId: pedido?.trabalhoId ?? Number(f.get('trabalho')), tipo, descricao: f.get('descricao'),
      quantidade: Number(f.get('quantidade') || 1), valorVenda: numeroBr(f.get('venda')) ?? 0, custo: numeroBr(f.get('custo')),
      laboratorio: f.get('lab') || null, prazo: f.get('prazo') || null, observacoes: f.get('obs') || null, cobrar: f.get('cobrar') === 'on',
    };
    setErro('');
    try {
      aoSalvar(pedido
        ? await api<PedidoProduto>(`/produtos/pedidos/${pedido.id}`, { metodo: 'PUT', corpo })
        : await api<PedidoProduto>('/produtos/pedidos', { corpo }));
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }

  const exemplos: Record<TipoProduto, string> = { ALBUM: '30x30, 20 lâminas, capa em couro', QUADRO: '60x90 canvas', IMPRESSAO: '20 fotos 15x21', OUTRO: 'Pendrive personalizado' };
  return (
    <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar}>
      {!pedido && (
        <label className="campo inteira"><span>Trabalho</span>
          <select id="pp-trabalho" name="trabalho" required defaultValue={trabalhoId ?? ''}>
            <option value="" disabled>Escolha o trabalho</option>
            {trabalhos?.filter((t) => t.status !== 'CANCELADO').map((t) => <option key={t.id} value={t.id}>{t.titulo}</option>)}
          </select>
        </label>
      )}
      <div className="campo inteira"><span>Produto</span>
        <div className="abas" role="group">
          {(Object.keys(NOMES_PRODUTO) as TipoProduto[]).map((k) => (
            <button key={k} type="button" className="botao pequeno" aria-pressed={tipo === k} disabled={!!pedido && pedido.tipo !== k && pedido.tipo === 'ALBUM'}
              onClick={() => setTipo(k)}>{NOMES_PRODUTO[k]}</button>
          ))}
        </div>
      </div>
      <label className="campo"><span>Descrição</span><input id="pp-desc" name="descricao" required maxLength={200} defaultValue={pedido?.descricao} placeholder={exemplos[tipo]} /></label>
      <label className="campo"><span>Quantidade</span><input id="pp-qtd" name="quantidade" type="number" min={1} max={1000} defaultValue={pedido?.quantidade ?? 1} /></label>
      <label className="campo"><span>Valor de venda (R$)</span><input id="pp-venda" name="venda" inputMode="decimal" defaultValue={pedido?.valorVenda || ''} /></label>
      <label className="campo"><span>Custo do laboratório (R$)</span><input id="pp-custo" name="custo" inputMode="decimal" defaultValue={pedido?.custo ?? ''} /></label>
      <label className="campo"><span>Laboratório</span><input id="pp-lab" name="lab" maxLength={120} defaultValue={pedido?.laboratorio ?? ''} /></label>
      <label className="campo"><span>Prazo para o cliente</span><input id="pp-prazo" name="prazo" type="date" defaultValue={pedido?.prazo ?? ''} /></label>
      <label className="campo inteira"><span>Observações</span><textarea id="pp-obs" name="obs" rows={2} maxLength={2000} defaultValue={pedido?.observacoes ?? ''} /></label>
      {!pedido && <label className="marcar inteira"><input id="pp-cobrar" type="checkbox" name="cobrar" defaultChecked /> Lançar o valor de venda como cobrança no trabalho</label>}
      <Erro msg={erro} />
      <div className="acoes-linha inteira">
        <button className="botao principal">{pedido ? 'Salvar' : 'Criar pedido'}</button>
        <button type="button" className="botao" onClick={aoCancelar}>Cancelar</button>
      </div>
    </form>
  );
}

// ====================================================================== estoque

function Estoque() {
  const { dados, erro, recarregar } = useDados<Material[]>('/estoque/materiais');
  const { dados: movs, recarregar: recarregarMovs } = useDados<MovimentoMaterial[]>('/estoque/movimentos');
  const [editando, setEditando] = useState<Material | 'novo' | null>(null);
  const [movendo, setMovendo] = useState<{ m: Material; tipo: TipoMovimento } | null>(null);
  const [falha, setFalha] = useState('');

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const corpo = { nome: f.get('nome'), unidade: f.get('unidade'), minimo: numeroBr(f.get('minimo')), custoUnitario: numeroBr(f.get('custo')),
      ativo: f.get('ativo') === 'on', quantidadeInicial: numeroBr(f.get('inicial')) };
    setFalha('');
    try {
      if (editando === 'novo') await api('/estoque/materiais', { corpo });
      else if (editando) await api(`/estoque/materiais/${editando.id}`, { metodo: 'PUT', corpo });
      setEditando(null);
      recarregar();
      recarregarMovs();
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }

  async function mover(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!movendo) return;
    const f = new FormData(e.currentTarget);
    setFalha('');
    try {
      await api('/estoque/movimentos', { corpo: { materialId: movendo.m.id, tipo: movendo.tipo, quantidade: numeroBr(f.get('qtd')), motivo: f.get('motivo') || null } });
      setMovendo(null);
      recarregar();
      recarregarMovs();
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível registrar.');
    }
  }

  const m = editando === 'novo' ? null : editando;
  const baixos = (dados ?? []).filter((x) => x.baixo);
  return (
    <section className="secao">
      <div className="acoes-linha">
        <button className="botao principal pequeno" onClick={() => setEditando('novo')}>Novo material</button>
        {baixos.length > 0 && <span className="etiqueta alerta">{baixos.length === 1 ? '1 material acabando' : `${baixos.length} materiais acabando`}</span>}
      </div>
      {editando && (
        <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar} key={m?.id ?? 'novo'}>
          <label className="campo"><span>Material</span><input id="mt-nome" name="nome" required maxLength={120} defaultValue={m?.nome} placeholder="Pendrive 32GB, caixa de álbum, papel fotográfico…" /></label>
          <label className="campo"><span>Unidade</span><input id="mt-un" name="unidade" required maxLength={20} defaultValue={m?.unidade ?? 'un'} /></label>
          <label className="campo"><span>Avisar quando chegar a</span><input id="mt-min" name="minimo" inputMode="decimal" defaultValue={m?.minimo ?? ''} /></label>
          <label className="campo"><span>Custo por unidade (R$)</span><input id="mt-custo" name="custo" inputMode="decimal" defaultValue={m?.custoUnitario ?? ''} /></label>
          {!m && <label className="campo"><span>Quantidade em estoque hoje</span><input id="mt-ini" name="inicial" inputMode="decimal" /></label>}
          <label className="marcar"><input type="checkbox" name="ativo" defaultChecked={m?.ativo ?? true} /> Ativo</label>
          <Erro msg={falha} />
          <div className="acoes-linha inteira">
            <button className="botao principal">Salvar</button>
            <button type="button" className="botao" onClick={() => setEditando(null)}>Cancelar</button>
          </div>
        </form>
      )}
      <Erro msg={erro} />
      {dados && (dados.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>Cadastre os materiais que você compra (pendrives, caixas, papel) para saber quando repor.</p>
        : (
          <div className="linhas">
            {dados.map((x) => (
              <div key={x.id} className="linha-item">
                <span className="principal">{x.nome}{!x.ativo && <span className="legenda"> · INATIVO</span>}</span>
                <span className="lado num" style={x.baixo ? { color: 'var(--alerta)' } : undefined}>{qtd(x.quantidade)} {x.unidade}</span>
                <span className="legenda">{x.baixo ? 'ACABANDO · ' : ''}MÍNIMO {qtd(x.minimo)}{x.custoUnitario ? ` · ${moeda(x.custoUnitario)}/${x.unidade}` : ''}</span>
                <span className="lado acoes-linha">
                  <button className="botao pequeno" onClick={() => setMovendo({ m: x, tipo: 'ENTRADA' })}>Entrada</button>
                  <button className="botao pequeno" onClick={() => setMovendo({ m: x, tipo: 'SAIDA' })}>Saída</button>
                  <button className="botao-link legenda" onClick={() => setMovendo({ m: x, tipo: 'AJUSTE' })}>Contagem</button>
                  <button className="botao-link legenda" onClick={() => setEditando(x)}>Editar</button>
                </span>
                {movendo?.m.id === x.id && (
                  <form className="grade-form duas anima-entrada" style={{ gridColumn: '1 / -1', paddingTop: 'var(--espaco-2)' }} onSubmit={mover}>
                    <label className="campo"><span>{movendo.tipo === 'AJUSTE' ? `Quantidade contada (${x.unidade})` : `Quantidade (${x.unidade})`}</span>
                      <input id="mv-qtd" name="qtd" required inputMode="decimal" autoFocus /></label>
                    <label className="campo"><span>Motivo (opcional)</span>
                      <input id="mv-motivo" name="motivo" maxLength={200} placeholder={movendo.tipo === 'ENTRADA' ? 'Compra' : movendo.tipo === 'SAIDA' ? 'Entrega do álbum da…' : 'Contagem do mês'} /></label>
                    <div className="acoes-linha inteira">
                      <button className="botao pequeno principal">{movendo.tipo === 'ENTRADA' ? 'Registrar entrada' : movendo.tipo === 'SAIDA' ? 'Registrar saída' : 'Ajustar estoque'}</button>
                      <button type="button" className="botao pequeno" onClick={() => setMovendo(null)}>Cancelar</button>
                    </div>
                  </form>
                )}
              </div>
            ))}
          </div>
        ))}
      {movs && movs.length > 0 && (
        <>
          <h2 className="subtitulo">Últimas movimentações</h2>
          <div className="linhas">
            {movs.slice(0, 20).map((v) => (
              <div key={v.id} className="linha-item">
                <span className="principal">{v.material}</span>
                <span className="lado num" style={{ color: v.tipo === 'ENTRADA' ? 'var(--sucesso)' : v.tipo === 'SAIDA' ? 'var(--alerta)' : undefined }}>
                  {v.tipo === 'ENTRADA' ? '+' : v.tipo === 'SAIDA' ? '−' : '='}{qtd(v.quantidade)} {v.unidade}
                </span>
                <span className="legenda">{dataHora(v.criadoEm).toUpperCase()}{v.motivo ? ` · ${v.motivo.toUpperCase()}` : ''}{v.usuario ? ` · ${v.usuario.toUpperCase()}` : ''}</span>
                <span className="lado legenda">SALDO {qtd(v.saldo)}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

/** Detalhe do trabalho: pedidos de produtos deste cliente/trabalho. */
export function ProdutosDoTrabalho({ trabalhoId }: { trabalhoId: number }) {
  const { dados } = useDados<PedidoProduto[]>(`/produtos/pedidos?trabalho=${trabalhoId}`);
  return (
    <section className="secao">
      <div className="cabeca-pagina">
        <h2 className="chamada" style={{ margin: 0 }}>Produtos{dados && dados.length > 0 ? ` (${dados.length})` : ''}</h2>
        <Link className="botao pequeno" to={`/painel/produtos?novo=${trabalhoId}`}>Novo pedido</Link>
      </div>
      {dados && dados.length > 0 && <div className="linhas">{dados.map((p) => <LinhaPedido key={p.id} p={p} mostrarCliente={false} />)}</div>}
      {dados && dados.length === 0 && <p className="legenda" style={{ margin: 0 }}>ÁLBUM, QUADRO OU IMPRESSÕES QUE O CLIENTE COMPRAR ENTRAM AQUI.</p>}
    </section>
  );
}
