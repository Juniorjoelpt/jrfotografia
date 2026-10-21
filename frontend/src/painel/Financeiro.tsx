import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ErroApi } from '../api';
import { BarrasMensais } from '../componentes/Graficos';
import { Numero } from '../componentes/Movimento';
import type { Despesa, Fluxo } from '../tipos';
import { data, isoDia, MESES, moeda, numeroBr } from '../util';
import { Erro, Linhas, useDados } from './comum';
import { Recebimentos } from './Recebimentos';

type Aba = 'receber' | 'despesas' | 'fluxo';
const NOMES: Record<Aba, string> = { receber: 'A receber', despesas: 'Contas a pagar', fluxo: 'Fluxo de caixa' };

/** Financeiro: o que entra (parcelas), o que sai (despesas) e o saldo mês a mês. */
export function Financeiro() {
  const [params, setParams] = useSearchParams();
  const aba = (['receber', 'despesas', 'fluxo'] as Aba[]).find((a) => a === params.get('aba')) ?? 'receber';
  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Financeiro</span><h1 className="titulo">{NOMES[aba]}</h1></div></div>
      <div className="abas" role="group" aria-label="Seções">
        {(Object.keys(NOMES) as Aba[]).map((a) => (
          <button key={a} className="botao pequeno" aria-pressed={aba === a} onClick={() => setParams(a === 'receber' ? {} : { aba: a }, { replace: true })}>{NOMES[a]}</button>
        ))}
      </div>
      {aba === 'receber' && <Recebimentos />}
      {aba === 'despesas' && <Despesas />}
      {aba === 'fluxo' && <FluxoCaixa />}
    </>
  );
}

const CATEGORIAS = ['Equipamento', 'Aluguel', 'Contas', 'Transporte', 'Marketing', 'Impressão e álbuns', 'Software', 'Impostos', 'Outros'];

function Despesas() {
  const { dados, erro, recarregar } = useDados<Despesa[]>('/despesas');
  const [editando, setEditando] = useState<Despesa | 'nova' | null>(null);
  const [falha, setFalha] = useState('');
  const [filtro, setFiltro] = useState<'abertas' | 'todas'>('abertas');

  const abertas = dados?.filter((d) => !d.pagoEm) ?? [];
  const totalAberto = abertas.reduce((s, d) => s + d.valor, 0);
  const vencido = abertas.filter((d) => d.vencida).reduce((s, d) => s + d.valor, 0);
  const mes = isoDia(new Date()).slice(0, 7);
  const pagoMes = dados?.filter((d) => d.pagoEm?.startsWith(mes)).reduce((s, d) => s + d.valor, 0) ?? 0;
  const lista = filtro === 'abertas'
    ? abertas.slice().sort((a, b) => a.vencimento.localeCompare(b.vencimento))
    : dados ?? [];

  async function salvar(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    const f = new FormData(ev.currentTarget);
    const corpo = {
      descricao: f.get('descricao'), categoria: f.get('categoria'), valor: numeroBr(f.get('valor')),
      vencimento: f.get('vencimento'), pagoEm: f.get('paga') === 'on' ? (f.get('pagoEm') || isoDia(new Date())) : null,
    };
    setFalha('');
    try {
      if (editando === 'nova') await api('/despesas', { corpo });
      else if (editando) await api(`/despesas/${editando.id}`, { metodo: 'PUT', corpo });
      setEditando(null);
      recarregar();
    } catch (e) {
      setFalha(e instanceof ErroApi ? e.message : 'Não foi possível salvar.');
    }
  }
  const alternarPago = async (d: Despesa) => { await api(`/despesas/${d.id}/pagamento`, { corpo: {} }); recarregar(); };
  const apagar = async (d: Despesa) => { await api(`/despesas/${d.id}`, { metodo: 'DELETE' }); setEditando(null); recarregar(); };

  const e = editando === 'nova' ? null : editando;
  return (
    <>
      <div className="numeros escalonado">
        <div className="numero"><span className="legenda">A PAGAR</span><strong><Numero valor={totalAberto} formatar={moeda} /></strong></div>
        <div className={`numero${vencido ? ' critico' : ''}`}><span className="legenda">VENCIDO</span><strong><Numero valor={vencido} formatar={moeda} /></strong></div>
        <div className="numero"><span className="legenda">PAGO NESTE MÊS</span><strong><Numero valor={pagoMes} formatar={moeda} /></strong></div>
      </div>
      {editando ? (
        <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar} key={e?.id ?? 'nova'}>
          <label className="campo inteira"><span>Descrição</span><input id="dp-desc" name="descricao" required maxLength={120} defaultValue={e?.descricao} placeholder="Ex.: parcela da lente 50mm" /></label>
          <label className="campo"><span>Categoria</span>
            <input id="dp-cat" name="categoria" required list="categorias-despesa" maxLength={40} defaultValue={e?.categoria} />
            <datalist id="categorias-despesa">{CATEGORIAS.map((c) => <option key={c} value={c} />)}</datalist>
          </label>
          <label className="campo"><span>Valor (R$)</span><input id="dp-valor" name="valor" required inputMode="decimal" defaultValue={e?.valor} /></label>
          <label className="campo"><span>Vencimento</span><input id="dp-venc" name="vencimento" type="date" required defaultValue={e?.vencimento ?? isoDia(new Date())} /></label>
          <label className="campo"><span>Pago em</span><input id="dp-pago" name="pagoEm" type="date" defaultValue={e?.pagoEm ?? ''} /></label>
          <label className="marcar inteira"><input id="dp-paga" type="checkbox" name="paga" defaultChecked={!!e?.pagoEm} /> Já está paga</label>
          <Erro msg={falha} />
          <div className="acoes-linha inteira">
            <button className="botao principal">Salvar</button>
            <button type="button" className="botao" onClick={() => setEditando(null)}>Cancelar</button>
            {e && <button type="button" className="botao" style={{ marginLeft: 'auto', color: 'var(--erro)' }} onClick={() => apagar(e)}>Apagar</button>}
          </div>
        </form>
      ) : (
        <div className="acoes-linha">
          <button className="botao principal" onClick={() => setEditando('nova')}>Nova despesa</button>
          <div className="abas" role="group" aria-label="Filtro">
            <button className="botao pequeno" aria-pressed={filtro === 'abertas'} onClick={() => setFiltro('abertas')}>Em aberto</button>
            <button className="botao pequeno" aria-pressed={filtro === 'todas'} onClick={() => setFiltro('todas')}>Todas</button>
          </div>
        </div>
      )}
      <Erro msg={erro} />
      {dados && (
        <Linhas vazio={filtro === 'abertas' ? 'Nenhuma conta em aberto.' : 'Nenhuma despesa lançada ainda.'}
          itens={lista.map((d) => (
            <div key={d.id} className="linha-item">
              <button className="principal botao-texto" onClick={() => setEditando(d)}>{d.descricao}</button>
              <span className="lado num">{moeda(d.valor)}</span>
              <span className="legenda">{d.categoria.toUpperCase()} · {d.pagoEm ? `PAGO ${data(d.pagoEm)}` : d.vencida ? <span style={{ color: 'var(--erro)' }}>VENCEU {data(d.vencimento)}</span> : `VENCE ${data(d.vencimento)}`}</span>
              <span className="lado">
                <button className="botao pequeno" onClick={() => alternarPago(d)}>{d.pagoEm ? 'Desfazer' : 'Paguei'}</button>
              </span>
            </div>
          ))} />
      )}
    </>
  );
}

function FluxoCaixa() {
  const atual = new Date().getFullYear();
  const [ano, setAno] = useState(atual);
  const { dados: f, erro } = useDados<Fluxo>(`/financeiro/fluxo?ano=${ano}`);
  return (
    <>
      <div className="acoes-linha" style={{ alignItems: 'center' }}>
        <button className="botao pequeno" aria-label="Ano anterior" onClick={() => setAno(ano - 1)}>‹</button>
        <strong className="num" style={{ minWidth: 48, textAlign: 'center' }}>{ano}</strong>
        <button className="botao pequeno" aria-label="Próximo ano" onClick={() => setAno(ano + 1)}>›</button>
      </div>
      <Erro msg={erro} />
      {f && (
        <>
          <div className="numeros escalonado">
            <div className="numero"><span className="legenda">ENTROU</span><strong><Numero valor={f.entrouAno} formatar={moeda} /></strong></div>
            <div className="numero"><span className="legenda">SAIU</span><strong><Numero valor={f.saiuAno} formatar={moeda} /></strong></div>
            <div className={`numero${f.saldoAno < 0 ? ' critico' : ''}`}><span className="legenda">SALDO</span><strong><Numero valor={f.saldoAno} formatar={moeda} /></strong></div>
            <div className="numero"><span className="legenda">PREVISTO A ENTRAR</span><strong><Numero valor={f.aEntrarAno} formatar={moeda} /></strong></div>
          </div>
          <BarrasMensais titulo="Entradas e saídas por mês" formatar={moeda} mesAtual={ano === atual ? new Date().getMonth() + 1 : undefined}
            series={[{ nome: 'Entrou', classe: 'a', valores: f.meses.map((m) => m.entrou) }, { nome: 'Saiu', classe: 'b', valores: f.meses.map((m) => m.saiu) }]} />
          <div className="tabela-rolagem painel-caixa" style={{ padding: 0 }}>
            <table className="tabela">
              <thead><tr><th>Mês</th><th>Entrou</th><th>Saiu</th><th>Saldo</th><th>A entrar</th><th>A pagar</th></tr></thead>
              <tbody>
                {f.meses.map((m) => (
                  <tr key={m.mes} className={ano === atual && m.mes === new Date().getMonth() + 1 ? 'atual' : ''}>
                    <th>{MESES[m.mes - 1]}</th>
                    <td>{m.entrou ? moeda(m.entrou) : '—'}</td>
                    <td>{m.saiu ? moeda(m.saiu) : '—'}</td>
                    <td style={m.saldo < 0 ? { color: 'var(--erro)' } : undefined}>{m.entrou || m.saiu ? moeda(m.saldo) : '—'}</td>
                    <td className="suave">{m.aEntrar ? moeda(m.aEntrar) : '—'}</td>
                    <td className="suave">{m.aSair ? moeda(m.aSair) : '—'}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot><tr><th>Ano</th><td>{moeda(f.entrouAno)}</td><td>{moeda(f.saiuAno)}</td><td>{moeda(f.saldoAno)}</td><td>{moeda(f.aEntrarAno)}</td><td>{moeda(f.aSairAno)}</td></tr></tfoot>
            </table>
          </div>
          <p className="legenda" style={{ margin: 0 }}>ENTROU E SAIU CONTAM PELA DATA DO PAGAMENTO. “A ENTRAR” E “A PAGAR” SÃO PARCELAS E CONTAS AINDA EM ABERTO, PELO VENCIMENTO.</p>
        </>
      )}
    </>
  );
}
