import { useEffect, useState, type FormEvent } from 'react';
import { api, ErroApi } from '../api';
import type { Comissao, Freelancer, PesquisaPainel, Tarefa } from '../tipos';
import { linkWhatsapp, moeda, numeroBr, primeiroNome, urlPesquisa } from '../util';
import { Erro, useDados } from './comum';
import { CartaoTarefa, FormTarefa, LinhaComissao } from './Equipe';

/** Detalhe do trabalho: tarefas (todos) e freelancers com comissão (administrador). */
export function TrabalhoEquipe({ trabalhoId, admin, valorTotal }: { trabalhoId: number; admin: boolean; valorTotal: number }) {
  const { dados: tarefas, recarregar: recarregarTarefas } = useDados<Tarefa[]>(`/tarefas?trabalho=${trabalhoId}`);
  const { dados: comissoes, recarregar: recarregarComissoes } = useDados<Comissao[]>(admin ? `/trabalhos/${trabalhoId}/freelancers` : null);
  const { dados: freelancers } = useDados<Freelancer[]>(admin ? '/freelancers' : null);
  const [tarefa, setTarefa] = useState<Tarefa | 'nova' | null>(null);
  const [vinculando, setVinculando] = useState(false);
  const [tipo, setTipo] = useState<'valor' | 'percentual'>('valor');
  const [erro, setErro] = useState('');

  async function vincular(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const n = numeroBr(f.get('quanto'));
    setErro('');
    try {
      await api(`/trabalhos/${trabalhoId}/freelancers`, {
        corpo: { freelancerId: Number(f.get('freelancer')), funcao: f.get('funcao') || null,
          valor: tipo === 'valor' ? n : null, percentual: tipo === 'percentual' ? n : null },
      });
      setVinculando(false);
      recarregarComissoes();
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível adicionar.');
    }
  }

  const ativos = (freelancers ?? []).filter((f) => f.ativo);
  const abertas = (tarefas ?? []).filter((t) => t.status !== 'FEITA');
  const custo = (comissoes ?? []).reduce((s, c) => s + c.valor, 0);

  return (
    <>
      <section className="secao">
        <div className="cabeca-pagina">
          <h2 className="chamada" style={{ margin: 0 }}>Tarefas{tarefas && tarefas.length > 0 ? ` (${abertas.length} abertas)` : ''}</h2>
          <button className="botao pequeno" onClick={() => setTarefa('nova')}>Nova tarefa</button>
        </div>
        {tarefa && (
          <FormTarefa trabalhoId={trabalhoId} tarefa={tarefa === 'nova' ? null : tarefa}
            aoSalvar={() => { setTarefa(null); recarregarTarefas(); }} aoCancelar={() => setTarefa(null)} />
        )}
        {tarefas && tarefas.length === 0 && !tarefa && <p className="legenda" style={{ margin: 0 }}>A TAREFA DE EDIÇÃO É CRIADA SOZINHA QUANDO O CLIENTE CONCLUI A SELEÇÃO.</p>}
        {tarefas && tarefas.length > 0 && (
          <div className="tf-lista">
            {tarefas.map((t) => <CartaoTarefa key={t.id} t={t} mostrarTrabalho={false} aoMudar={recarregarTarefas} aoEditar={() => setTarefa(t)} />)}
          </div>
        )}
      </section>

      {admin && (
        <section className="secao">
          <div className="cabeca-pagina">
            <h2 className="chamada" style={{ margin: 0 }}>Freelancers{custo > 0 ? ` · ${moeda(custo)}` : ''}</h2>
            {ativos.length > 0 && <button className="botao pequeno" onClick={() => setVinculando(!vinculando)} aria-expanded={vinculando}>Adicionar freelancer</button>}
          </div>
          {freelancers && ativos.length === 0 && <p className="legenda" style={{ margin: 0 }}>CADASTRE SEUS FREELANCERS EM EQUIPE › FREELANCERS PARA LANÇAR COMISSÕES AQUI.</p>}
          {vinculando && (
            <form className="painel-caixa grade-form duas anima-entrada" onSubmit={vincular}>
              <label className="campo"><span>Freelancer</span>
                <select id="tv-free" name="freelancer" required>{ativos.map((f) => <option key={f.id} value={f.id}>{f.nome}{f.funcao ? ` · ${f.funcao}` : ''}</option>)}</select>
              </label>
              <label className="campo"><span>Função neste trabalho (opcional)</span><input id="tv-funcao" name="funcao" maxLength={60} /></label>
              <div className="campo"><span>Comissão</span>
                <div className="abas" role="group">
                  <button type="button" className="botao pequeno" aria-pressed={tipo === 'valor'} onClick={() => setTipo('valor')}>Valor fixo</button>
                  <button type="button" className="botao pequeno" aria-pressed={tipo === 'percentual'} onClick={() => setTipo('percentual')}>% do trabalho</button>
                </div>
              </div>
              <label className="campo"><span>{tipo === 'valor' ? 'Valor (R$)' : `Percentual (sobre ${moeda(valorTotal)})`}</span>
                <input id="tv-quanto" name="quanto" required inputMode="decimal" placeholder={tipo === 'valor' ? '150,00' : '10'} /></label>
              <Erro msg={erro} />
              <div className="acoes-linha inteira">
                <button className="botao principal">Adicionar</button>
                <button type="button" className="botao" onClick={() => setVinculando(false)}>Cancelar</button>
              </div>
            </form>
          )}
          {comissoes && comissoes.length > 0 && (
            <div className="linhas">{comissoes.map((c) => <LinhaComissao key={c.id} c={c} mostrar="freelancer" aoMudar={recarregarComissoes} />)}</div>
          )}
        </section>
      )}
    </>
  );
}

/** Na entrega: link da pesquisa de satisfação para mandar ao cliente (ou a resposta, se já veio). */
export function PesquisaTrabalho({ trabalhoId, cliente, telefoneCliente }: { trabalhoId: number; cliente: string; telefoneCliente?: string }) {
  const [p, setP] = useState<PesquisaPainel | null>(null);
  useEffect(() => {
    api<PesquisaPainel>(`/trabalhos/${trabalhoId}/pesquisa`, { metodo: 'POST' }).then(setP).catch(() => {});
  }, [trabalhoId]);
  if (!p) return null;
  const msg = `Oi, ${primeiroNome(cliente)}! Foi um prazer fotografar você 💛 Se puder, me conta em 1 minuto como foi a experiência:\n${urlPesquisa(p.token)}`;
  return (
    <section className="secao">
      <h2 className="chamada">Pesquisa de satisfação</h2>
      <div className="painel-caixa" style={{ display: 'grid', gap: 'var(--espaco-2)' }}>
        {p.respondidaEm ? (
          <>
            <p style={{ margin: 0 }}><span className={`etiqueta${(p.nota ?? 0) >= 9 ? ' sucesso' : (p.nota ?? 0) <= 6 ? ' erro' : ''}`}>Nota {p.nota}</span>{' '}
              <span className="legenda">ATENDIMENTO {p.atendimento ?? '—'} · FOTOS {p.fotos ?? '—'} · PRAZO {p.prazo ?? '—'}</span></p>
            {p.comentario && <p className="suave" style={{ margin: 0 }}>“{p.comentario}”{p.autorizaDepoimento ? ' · pode publicar' : ''}</p>}
          </>
        ) : (
          <>
            <p className="suave" style={{ margin: 0 }}>Aguardando resposta. A pesquisa também aparece na galeria do cliente.</p>
            <div className="acoes-linha">
              {telefoneCliente && <a className="botao pequeno principal" href={linkWhatsapp(telefoneCliente, msg)} target="_blank" rel="noreferrer">Enviar pelo WhatsApp</a>}
              <a className="botao pequeno" href={urlPesquisa(p.token)} target="_blank" rel="noreferrer">Abrir pesquisa</a>
            </div>
          </>
        )}
      </div>
    </section>
  );
}
