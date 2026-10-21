import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ErroApi, salvarSessao } from '../api';
import { Logo } from '../componentes/Logo';
import type { Sessao } from '../tipos';
import '../galeria/galeria.css';

/** Entrar no painel: e-mail e senha e, com a verificação em duas etapas ligada, o código do app. */
export function Entrar() {
  const navegar = useNavigate();
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [desafio, setDesafio] = useState<{ token: string; nome: string } | null>(null);
  const [recuperacao, setRecuperacao] = useState(false);

  function concluir(s: Sessao) {
    salvarSessao(s);
    navegar('/painel', { replace: true });
  }

  async function entrar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setEnviando(true);
    setErro('');
    try {
      const s = await api<Sessao>('/auth/login', { token: null, corpo: { email: f.get('email'), senha: f.get('senha') } });
      if (s.precisa2fa && s.desafio) setDesafio({ token: s.desafio, nome: s.nome });
      else concluir(s);
    } catch (er) {
      setErro(er instanceof ErroApi ? er.message : 'Não foi possível entrar.');
    } finally {
      setEnviando(false);
    }
  }

  async function confirmar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!desafio) return;
    const f = new FormData(e.currentTarget);
    setEnviando(true);
    setErro('');
    try {
      concluir(await api<Sessao>('/auth/2fa', { token: null, corpo: { desafio: desafio.token, codigo: f.get('codigo') } }));
    } catch (er) {
      const msg = er instanceof ErroApi ? er.message : 'Não foi possível entrar.';
      setErro(msg);
      if (msg.includes('tempo')) setDesafio(null);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="entrada-galeria textura">
      {desafio ? (
        <form onSubmit={confirmar} className="vidro" style={{ textAlign: 'left', justifyItems: 'stretch' }} key="2fa">
          <div style={{ justifySelf: 'center' }}><Logo largura={120} /></div>
          <p style={{ margin: 0 }}>Olá, {desafio.nome.split(' ')[0]}! {recuperacao
            ? 'Digite um dos seus códigos de recuperação (cada um vale uma vez).'
            : 'Abra o app autenticador no celular e digite o código de 6 dígitos.'}</p>
          <label className="campo"><span>{recuperacao ? 'Código de recuperação' : 'Código do app'}</span>
            {recuperacao
              ? <input id="login-codigo" name="codigo" required autoFocus autoComplete="off" autoCapitalize="characters" placeholder="ABCD-EFGH" maxLength={12} />
              : <input id="login-codigo" name="codigo" required autoFocus inputMode="numeric" autoComplete="one-time-code" pattern="[0-9 ]{6,7}" maxLength={7} placeholder="000000"
                style={{ fontSize: 24, letterSpacing: '.3em', textAlign: 'center' }} />}
          </label>
          {erro && <p className="aviso erro" role="alert" style={{ margin: 0 }}>{erro}</p>}
          <button className="botao principal" disabled={enviando}>{enviando ? 'Conferindo…' : 'Entrar'}</button>
          <button type="button" className="botao-link legenda" style={{ justifySelf: 'center' }} onClick={() => { setRecuperacao(!recuperacao); setErro(''); }}>
            {recuperacao ? 'USAR O CÓDIGO DO APP' : 'PERDI O CELULAR: USAR CÓDIGO DE RECUPERAÇÃO'}
          </button>
          <button type="button" className="botao-link legenda" style={{ justifySelf: 'center' }} onClick={() => { setDesafio(null); setErro(''); }}>VOLTAR</button>
        </form>
      ) : (
        <form onSubmit={entrar} className="vidro" style={{ textAlign: 'left', justifyItems: 'stretch' }} key="senha">
          <div style={{ justifySelf: 'center' }}><Logo largura={140} /></div>
          <label className="campo"><span>E-mail</span><input id="login-email" name="email" type="email" required autoComplete="username" /></label>
          <label className="campo"><span>Senha</span><input id="login-senha" name="senha" type="password" required autoComplete="current-password" /></label>
          {erro && <p className="aviso erro" role="alert" style={{ margin: 0 }}>{erro}</p>}
          <button className="botao principal" disabled={enviando}>{enviando ? 'Entrando…' : 'Entrar'}</button>
        </form>
      )}
    </main>
  );
}
