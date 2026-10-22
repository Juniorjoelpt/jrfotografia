import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ErroApi, sessaoAtual } from '../api';
import type { Comissao, Freelancer, Pessoa, StatusTarefa, Tarefa, TrabalhoResumo } from '../tipos';
import { data, linkWhatsapp, moeda, NOMES_TAREFA, telefone } from '../util';
import { Erro, useDados } from './comum';

type Aba = 'tarefas' | 'freelancers';

/** Equipe: tarefas de edição (todos) e freelancers com comissões (administrador). */
export function Equipe() {
  const admin = sessaoAtual()?.perfil === 'ADMIN';
  const [aba, setAba] = useState<Aba>(() => (admin && window.location.hash === '#freelancers' ? 'freelancers' : 'tarefas'));
  const trocar = (a: Aba) => { setAba(a); history.replaceState(null, '', `#${a}`); };
  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Equipe</span><h1 className="titulo">{aba === 'tarefas' ? 'Tarefas' : 'Freelancers'}</h1></div></div>
      {admin && (
        <div className="abas" role="group" aria-label="Seções">
          <button className="botao pequeno" aria-pressed={aba === 'tarefas'} onClick={() => trocar('tarefas')}>Tarefas</button>
          <button className="botao pequeno" aria-pressed={aba === 'freelancers'} onClick={() => trocar('freelancers')}>Freelancers e comissões</button>
        </div>
      )}
      {aba === 'tarefas' ? <Tarefas /> : <Freelancers />}
    </>
  );
}

// ====================================================================== tarefas

export function FormTarefa({ trabalhoId, tarefa, aoSalvar, aoCancelar }: {
  trabalhoId?: number; tarefa?: Tarefa | null; aoSalvar: () => void; aoCancelar: () => void;
}) {
  const { dados: pessoas } = useDados<Pessoa[]>('/equipe/pessoas');
  const { dados: trabalhos } = useDados<TrabalhoResumo[]>(trabalhoId ? null : '/trabalhos');
  const [erro, setErro] = useState('');
  const respAtual = tarefa?.responsavelUsuarioId ? `U${tarefa.responsavelUsuarioId}` : tarefa?.responsavelFreelancerId ? `F${tarefa.responsavelFreelancerId}` : '';

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const resp = String(f.get('resp') || '');
    const corpo = {
      trabalhoId: trabalhoId ?? (f.get('trabalho') ? Number(f.get('trabalho')) : null),
      titulo: f.get('titulo'), descricao: f.get('descricao') || null, prazo: f.get('prazo') || null,
      responsavelUsuarioId: resp.startsWith('U') ? Number(resp.slice(1)) : null,
      responsavelFreelancerId: resp.startsWith('F') ? Number(resp.slice(1)) : null,
    };
    try {
      if (tarefa) await api(`/tarefas/${tarefa.id}`, { metodo: 'PUT', corpo });
      else await api('/tarefas', { corpo });
      aoSalvar();
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }

  return (
    <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar}>
      <label className="campo inteira"><span>Tarefa</span><input id="tf-titulo" name="titulo" required maxLength={160} defaultValue={tarefa?.titulo} placeholder="Editar fotos, montar álbum, entregar pendrive…" /></label>
      {!trabalhoId && (
        <label className="campo"><span>Trabalho (opcional)</span>
          <select id="tf-trabalho" name="trabalho" defaultValue={tarefa?.trabalhoId ?? ''} key={trabalhos ? 'lista' : 'carregando'}>
            <option value="">Nenhum</option>
            {trabalhos?.filter((t) => t.status !== 'CANCELADO' && (t.status !== 'ENTREGUE' || t.id === tarefa?.trabalhoId))
              .map((t) => <option key={t.id} value={t.id}>{t.titulo}</option>)}
          </select>
        </label>
      )}
      <label className="campo"><span>Responsável</span>
        <select id="tf-resp" name="resp" defaultValue={respAtual} key={pessoas ? 'lista' : 'carregando'}>
          <option value="">Sem responsável</option>
          {pessoas?.map((p) => <option key={`${p.tipo}${p.id}`} value={`${p.tipo === 'USUARIO' ? 'U' : 'F'}${p.id}`}>{p.nome}{p.tipo === 'FREELANCER' ? ' (freelancer)' : ''}</option>)}
        </select>
      </label>
      <label className="campo"><span>Prazo</span><input id="tf-prazo" name="prazo" type="date" defaultValue={tarefa?.prazo ?? ''} /></label>
      <label className="campo inteira"><span>Detalhes (opcional)</span><textarea id="tf-desc" name="descricao" maxLength={2000} rows={2} defaultValue={tarefa?.descricao ?? ''} /></label>
      <Erro msg={erro} />
      <div className="acoes-linha inteira">
        <button className="botao principal">{tarefa ? 'Salvar' : 'Criar tarefa'}</button>
        <button type="button" className="botao" onClick={aoCancelar}>Cancelar</button>
      </div>
    </form>
  );
}

export function CartaoTarefa({ t, aoMudar, aoEditar, mostrarTrabalho = true }: { t: Tarefa; aoMudar: () => void; aoEditar: () => void; mostrarTrabalho?: boolean }) {
  const mudar = async (status: StatusTarefa) => { await api(`/tarefas/${t.id}/status`, { metodo: 'PATCH', corpo: { status } }); aoMudar(); };
  const apagar = async () => { await api(`/tarefas/${t.id}`, { metodo: 'DELETE' }); aoMudar(); };
  return (
    <article className={`tf-cartao${t.atrasada ? ' atrasada' : ''}${t.status === 'FEITA' ? ' feita' : ''}`}>
      <strong>{t.titulo}</strong>
      {mostrarTrabalho && t.trabalho && <Link className="legenda" to={`/painel/trabalhos/${t.trabalhoId}`}>{t.trabalho.toUpperCase()}</Link>}
      {t.descricao && <p className="suave" style={{ margin: 0, whiteSpace: 'pre-line' }}>{t.descricao}</p>}
      <span className="legenda">
        {t.responsavel ? t.responsavel.toUpperCase() : 'SEM RESPONSÁVEL'}
        {t.prazo ? <> · <span style={t.atrasada ? { color: 'var(--erro)' } : undefined}>{t.atrasada ? 'ATRASADA, ' : ''}PRAZO {data(t.prazo).slice(0, 5)}</span></> : ''}
        {t.status === 'FEITA' && t.concluidaEm ? ` · FEITA ${data(t.concluidaEm).slice(0, 5)}` : ''}
      </span>
      <div className="acoes-linha">
        {t.status === 'A_FAZER' && <button className="botao pequeno" onClick={() => mudar('FAZENDO')}>Começar</button>}
        {t.status !== 'FEITA' && <button className="botao pequeno principal" onClick={() => mudar('FEITA')}>Concluir</button>}
        {t.status === 'FEITA' && <button className="botao pequeno" onClick={() => mudar('A_FAZER')}>Reabrir</button>}
        <button className="botao-link legenda" onClick={aoEditar}>Editar</button>
        {!t.automatica && <button className="botao-link legenda" onClick={apagar}>Apagar</button>}
      </div>
    </article>
  );
}

function Tarefas() {
  const { dados, erro, recarregar } = useDados<Tarefa[]>('/tarefas');
  const [editando, setEditando] = useState<Tarefa | 'nova' | null>(null);
  const [minhas, setMinhas] = useState(false);
  const eu = sessaoAtual()?.nome;
  const lista = (dados ?? []).filter((t) => !minhas || t.responsavel === eu);
  const colunas: StatusTarefa[] = ['A_FAZER', 'FAZENDO', 'FEITA'];
  return (
    <section className="secao">
      <div className="acoes-linha">
        <button className="botao principal pequeno" onClick={() => setEditando('nova')}>Nova tarefa</button>
        <button className="botao pequeno" aria-pressed={minhas} onClick={() => setMinhas(!minhas)}>Só as minhas</button>
      </div>
      {editando && <FormTarefa tarefa={editando === 'nova' ? null : editando} aoSalvar={() => { setEditando(null); recarregar(); }} aoCancelar={() => setEditando(null)} />}
      <Erro msg={erro} />
      <div className="tf-quadro">
        {colunas.map((c) => {
          const daColuna = lista.filter((t) => t.status === c);
          const mostrar = c === 'FEITA' ? daColuna.slice(0, 12) : daColuna;
          return (
            <div key={c} className="tf-coluna">
              <h2 className="legenda">{NOMES_TAREFA[c].toUpperCase()} · {daColuna.length}</h2>
              {mostrar.length === 0 && <p className="vazio" style={{ margin: 0, padding: 'var(--espaco-4)' }}>Nada aqui.</p>}
              {mostrar.map((t) => <CartaoTarefa key={t.id} t={t} aoMudar={recarregar} aoEditar={() => setEditando(t)} />)}
            </div>
          );
        })}
      </div>
    </section>
  );
}

// ====================================================================== freelancers

function Freelancers() {
  const { dados, erro, recarregar } = useDados<Freelancer[]>('/freelancers');
  const [editando, setEditando] = useState<Freelancer | 'novo' | null>(null);
  const [aberto, setAberto] = useState<number | null>(null);
  const [falha, setFalha] = useState('');

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const corpo = { nome: f.get('nome'), telefone: f.get('telefone') || null, funcao: f.get('funcao') || null,
      chavePix: f.get('pix') || null, observacoes: f.get('obs') || null, ativo: f.get('ativo') === 'on' };
    try {
      if (editando === 'novo') await api('/freelancers', { corpo });
      else if (editando) await api(`/freelancers/${editando.id}`, { metodo: 'PUT', corpo });
      setEditando(null);
      recarregar();
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }

  const fr = editando === 'novo' ? null : editando;
  const aPagar = (dados ?? []).reduce((s, f) => s + f.aPagar, 0);
  return (
    <section className="secao">
      <div className="acoes-linha">
        <button className="botao principal pequeno" onClick={() => setEditando('novo')}>Novo freelancer</button>
        {aPagar > 0 && <span className="etiqueta alerta">A pagar: {moeda(aPagar)}</span>}
      </div>
      {editando && (
        <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar} key={fr?.id ?? 'novo'}>
          <label className="campo"><span>Nome</span><input id="fr-nome" name="nome" required maxLength={120} defaultValue={fr?.nome} /></label>
          <label className="campo"><span>Função</span><input id="fr-funcao" name="funcao" maxLength={60} defaultValue={fr?.funcao} placeholder="Segundo fotógrafo, maquiadora, editor…" /></label>
          <label className="campo"><span>WhatsApp</span><input id="fr-tel" name="telefone" inputMode="tel" maxLength={20} defaultValue={fr?.telefone} /></label>
          <label className="campo"><span>Chave Pix</span><input id="fr-pix" name="pix" maxLength={120} defaultValue={fr?.chavePix} /></label>
          <label className="campo inteira"><span>Observações</span><textarea id="fr-obs" name="obs" rows={2} maxLength={1000} defaultValue={fr?.observacoes} /></label>
          <label className="marcar"><input type="checkbox" name="ativo" defaultChecked={fr?.ativo ?? true} /> Ativo</label>
          <Erro msg={falha} />
          <div className="acoes-linha inteira">
            <button className="botao principal">Salvar</button>
            <button type="button" className="botao" onClick={() => setEditando(null)}>Cancelar</button>
          </div>
        </form>
      )}
      <Erro msg={erro} />
      {dados && (dados.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>Nenhum freelancer cadastrado. Cadastre quem trabalha com você para lançar as comissões nos trabalhos.</p>
        : (
          <div className="linhas">
            {dados.map((f) => (
              <div key={f.id} className="linha-item">
                <button className="botao-link principal" style={{ color: 'var(--tinta)', textAlign: 'left' }} aria-expanded={aberto === f.id}
                  onClick={() => setAberto(aberto === f.id ? null : f.id)}>{f.nome}{!f.ativo && <span className="legenda"> · INATIVO</span>}</button>
                <span className="lado num">{f.aPagar > 0 ? <span style={{ color: 'var(--alerta)' }}>{moeda(f.aPagar)} a pagar</span> : moeda(f.pago)}</span>
                <span className="legenda">{[f.funcao?.toUpperCase(), f.telefone && telefone(f.telefone), `${f.trabalhos} ${f.trabalhos === 1 ? 'TRABALHO' : 'TRABALHOS'}`].filter(Boolean).join(' · ')}</span>
                <span className="lado"><button className="botao-link legenda" onClick={() => setEditando(f)}>Editar</button></span>
                {aberto === f.id && <Extrato f={f} aoMudar={recarregar} />}
              </div>
            ))}
          </div>
        ))}
    </section>
  );
}

function Extrato({ f, aoMudar }: { f: Freelancer; aoMudar: () => void }) {
  const { dados, recarregar } = useDados<Comissao[]>(`/freelancers/${f.id}/extrato`);
  const mudou = () => { recarregar(); aoMudar(); };
  return (
    <div style={{ gridColumn: '1 / -1', display: 'grid', gap: 'var(--espaco-2)', paddingTop: 'var(--espaco-3)' }}>
      {f.chavePix && <span className="legenda">PIX: {f.chavePix}</span>}
      {dados?.length === 0 && <p className="vazio" style={{ margin: 0 }}>Ainda não participou de trabalhos.</p>}
      {dados?.map((c) => <LinhaComissao key={c.id} c={c} aoMudar={mudou} mostrar="trabalho" />)}
    </div>
  );
}

export function LinhaComissao({ c, aoMudar, mostrar }: { c: Comissao; aoMudar: () => void; mostrar: 'trabalho' | 'freelancer' }) {
  const [erro, setErro] = useState('');
  const agir = async (caminho: string, metodo: string) => {
    setErro('');
    try { await api(caminho, { metodo }); aoMudar(); } catch (e) { setErro(e instanceof ErroApi ? e.message : 'Não foi possível.'); }
  };
  return (
    <div className="linha-item" style={{ borderTop: '1px solid var(--linha)' }}>
      <span className="principal">
        {mostrar === 'trabalho' ? <Link to={`/painel/trabalhos/${c.trabalhoId}`} style={{ color: 'var(--tinta)' }}>{c.trabalho}</Link> : c.freelancer}
      </span>
      <span className="lado num">{moeda(c.valor)}</span>
      <span className="legenda">
        {c.funcao?.toUpperCase()}{c.percentual ? ` · ${String(c.percentual).replace('.', ',')}% DO TRABALHO` : ''}
        {c.pagoEm ? <span style={{ color: 'var(--sucesso)' }}> · PAGO EM {data(c.pagoEm)}</span> : ' · A PAGAR'}
      </span>
      <span className="lado acoes-linha">
        {!c.pagoEm && c.telefone && c.chavePix && (
          <a className="botao-link legenda" href={linkWhatsapp(c.telefone, `Oi! Vou fazer o Pix de ${moeda(c.valor)} referente a ${c.trabalho}.`)} target="_blank" rel="noreferrer">Avisar</a>
        )}
        {c.pagoEm
          ? <button className="botao-link legenda" onClick={() => agir(`/comissoes/${c.id}/pagamento`, 'DELETE')}>Desfazer</button>
          : <>
            <button className="botao pequeno" onClick={() => agir(`/comissoes/${c.id}/pagamento`, 'POST')}>Marcar pago</button>
            {mostrar === 'freelancer' && <button className="botao-link legenda" onClick={() => agir(`/comissoes/${c.id}`, 'DELETE')}>Remover</button>}
          </>}
      </span>
      {erro && <p className="aviso erro" style={{ gridColumn: '1 / -1', margin: 0 }}>{erro}</p>}
    </div>
  );
}
