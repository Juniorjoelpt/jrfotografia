import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ErroApi, salvarSessao } from '../api';
import { Logo } from '../componentes/Logo';
import type { Sessao } from '../tipos';
import '../galeria/galeria.css';

export function Entrar() {
  const navegar = useNavigate();
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function entrar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setEnviando(true);
    setErro('');
    try {
      const s = await api<Sessao>('/auth/login', { token: null, corpo: { email: f.get('email'), senha: f.get('senha') } });
      salvarSessao(s);
      navegar('/painel', { replace: true });
    } catch (er) {
      setErro(er instanceof ErroApi ? er.message : 'Não foi possível entrar.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="entrada-galeria textura">
      <form onSubmit={entrar} style={{ textAlign: 'left', justifyItems: 'stretch' }}>
        <div style={{ justifySelf: 'center' }}><Logo largura={140} /></div>
        <label className="campo"><span>E-mail</span><input id="login-email" name="email" type="email" required autoComplete="username" /></label>
        <label className="campo"><span>Senha</span><input id="login-senha" name="senha" type="password" required autoComplete="current-password" /></label>
        {erro && <p className="aviso erro" role="alert" style={{ margin: 0 }}>{erro}</p>}
        <button className="botao principal" disabled={enviando}>{enviando ? 'Entrando…' : 'Entrar'}</button>
      </form>
    </main>
  );
}
