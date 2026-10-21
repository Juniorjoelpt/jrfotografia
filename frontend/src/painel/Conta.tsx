import { useState, type FormEvent } from 'react';
import { api, ErroApi, salvarSessao, sessaoAtual } from '../api';
import type { Perfil, UsuarioConta } from '../tipos';
import { Erro, useDados } from './comum';
import { DuasEtapas } from './DuasEtapas';

/** Minha conta: trocar a senha. Para o administrador, também quem tem acesso ao painel. */
export function Conta() {
  const sessao = sessaoAtual();
  const admin = sessao?.perfil === 'ADMIN';
  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Conta</span><h1 className="titulo">Minha conta</h1></div></div>
      {sessao?.senhaPadrao && (
        <p className="aviso" role="status" style={{ margin: 0, borderColor: 'var(--alerta)', color: 'var(--alerta)' }}>
          Você ainda usa a senha de fábrica. Troque agora: quem souber dela consegue entrar no painel.
        </p>
      )}
      <MinhaSenha />
      <DuasEtapas />
      {admin && <Pessoas />}
    </>
  );
}

function MinhaSenha() {
  const [erro, setErro] = useState('');
  const [feito, setFeito] = useState(false);
  const [enviando, setEnviando] = useState(false);

  async function salvar(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    const form = ev.currentTarget;
    const f = new FormData(form);
    setErro(''); setFeito(false);
    if (f.get('nova') !== f.get('confirma')) { setErro('A confirmação não é igual à nova senha.'); return; }
    setEnviando(true);
    try {
      await api('/conta/senha', { metodo: 'PUT', corpo: { senhaAtual: f.get('atual'), novaSenha: f.get('nova') } });
      const s = sessaoAtual();
      if (s) salvarSessao({ ...s, senhaPadrao: false });
      form.reset();
      setFeito(true);
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message.replace(/^novaSenha: /, 'Nova senha: ') : 'Não foi possível trocar a senha.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <section className="secao">
      <h2 className="chamada">Trocar senha</h2>
      <form className="painel-caixa grade-form duas" onSubmit={salvar}>
        <label className="campo inteira"><span>Senha atual</span><input id="cs-atual" name="atual" type="password" required autoComplete="current-password" /></label>
        <label className="campo"><span>Nova senha (mínimo 8 caracteres)</span><input id="cs-nova" name="nova" type="password" required minLength={8} maxLength={72} autoComplete="new-password" /></label>
        <label className="campo"><span>Repita a nova senha</span><input id="cs-confirma" name="confirma" type="password" required minLength={8} maxLength={72} autoComplete="new-password" /></label>
        <Erro msg={erro} />
        {feito && <p className="aviso sucesso inteira" role="status" style={{ margin: 0 }}>Senha trocada. Use a nova senha no próximo acesso.</p>}
        <button className="botao principal inteira" disabled={enviando}>{enviando ? 'Salvando…' : 'Trocar senha'}</button>
      </form>
    </section>
  );
}

const NOMES_PERFIL: Record<Perfil, string> = { ADMIN: 'Administrador', COLABORADOR: 'Colaborador' };

function Pessoas() {
  const { dados, erro, recarregar } = useDados<UsuarioConta[]>('/usuarios');
  const [editando, setEditando] = useState<UsuarioConta | 'novo' | null>(null);
  const [falha, setFalha] = useState('');

  async function salvar(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    const f = new FormData(ev.currentTarget);
    const corpo = {
      nome: f.get('nome'), email: f.get('email'), perfil: f.get('perfil'),
      ativo: f.get('ativo') === 'on', senha: f.get('senha') || null,
    };
    setFalha('');
    try {
      if (editando === 'novo') await api('/usuarios', { corpo });
      else if (editando) await api(`/usuarios/${editando.id}`, { metodo: 'PUT', corpo });
      setEditando(null);
      recarregar();
    } catch (e) {
      setFalha(e instanceof ErroApi ? e.message.replace(/^senha: /, 'Senha: ') : 'Não foi possível salvar.');
    }
  }

  const u = editando === 'novo' ? null : editando;
  return (
    <section className="secao">
      <div className="cabeca-pagina">
        <h2 className="chamada">Quem acessa o painel</h2>
        {!editando && <button className="botao pequeno" onClick={() => setEditando('novo')}>Adicionar pessoa</button>}
      </div>
      <p className="suave" style={{ margin: 0 }}>Colaboradores veem agenda, trabalhos, clientes e pedidos, mas não veem valores, orçamentos, financeiro nem relatórios.</p>
      <Erro msg={erro} />
      {editando && (
        <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar} key={u?.id ?? 'novo'}>
          <label className="campo"><span>Nome</span><input id="us-nome" name="nome" required maxLength={120} defaultValue={u?.nome} /></label>
          <label className="campo"><span>E-mail de acesso</span><input id="us-email" name="email" type="email" required defaultValue={u?.email} /></label>
          <label className="campo"><span>Perfil</span>
            <select id="us-perfil" name="perfil" defaultValue={u?.perfil ?? 'COLABORADOR'} disabled={u?.voce}>
              <option value="COLABORADOR">Colaborador</option><option value="ADMIN">Administrador</option>
            </select>
            {u?.voce && <input type="hidden" name="perfil" value="ADMIN" />}
          </label>
          <label className="campo"><span>{u ? 'Nova senha (deixe vazio para manter)' : 'Senha provisória (mínimo 8)'}</span>
            <input id="us-senha" name="senha" type="password" minLength={8} maxLength={72} required={!u} autoComplete="new-password" />
          </label>
          {!u?.voce && <label className="marcar inteira"><input id="us-ativo" type="checkbox" name="ativo" defaultChecked={u?.ativo ?? true} /> Pode entrar no painel</label>}
          {u?.voce && <input type="hidden" name="ativo" value="on" />}
          {u && !u.voce && u.doisFatores && (
            <p className="legenda inteira" style={{ margin: 0 }}>
              DUAS ETAPAS LIGADA.{' '}
              <button type="button" className="botao-link legenda" onClick={async () => {
                await api(`/usuarios/${u.id}/2fa/remover`, { metodo: 'POST' });
                setEditando(null);
                recarregar();
              }}>Remover (perdeu o celular)</button>
            </p>
          )}
          <Erro msg={falha} />
          <div className="acoes-linha inteira">
            <button className="botao principal">Salvar</button>
            <button type="button" className="botao" onClick={() => { setEditando(null); setFalha(''); }}>Cancelar</button>
          </div>
        </form>
      )}
      {dados && (
        <div className="linhas">
          {dados.map((x) => (
            <button key={x.id} className="linha-item" style={{ border: 0, textAlign: 'left', cursor: 'pointer', borderTop: '1px solid var(--linha)' }} onClick={() => setEditando(x)}>
              <span className="principal">{x.nome}{x.voce && <span className="legenda"> · VOCÊ</span>}</span>
              <span className="lado"><span className={`etiqueta${x.ativo ? '' : ' erro'}`}>{x.ativo ? NOMES_PERFIL[x.perfil] : 'Sem acesso'}</span></span>
              <span className="legenda" style={{ gridColumn: '1 / -1' }}>{x.email}{x.doisFatores ? ' · DUAS ETAPAS LIGADA' : ''}</span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
