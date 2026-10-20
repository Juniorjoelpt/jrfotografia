import { Navigate, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { salvarSessao, sessaoAtual } from '../api';
import { Monograma } from '../componentes/Logo';
import './painel.css';

const icone = (d: string) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} aria-hidden="true"><path d={d} /></svg>
);

const ITENS = [
  { para: '/painel', fim: true, nome: 'Início', d: 'M4 11 12 4l8 7v9h-5v-6H9v6H4z', admin: false },
  { para: '/painel/trabalhos', nome: 'Trabalhos', d: 'M4 7h16v12H4zM9 7V5h6v2M12 11a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5', admin: false },
  { para: '/painel/clientes', nome: 'Clientes', d: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c1-4 4.5-6 8-6s7 2 8 6', admin: false },
  { para: '/painel/pedidos', nome: 'Pedidos', d: 'M4 5h16v11H8l-4 4zM8 9h8M8 12h5', admin: false },
  { para: '/painel/recebimentos', nome: 'Recebimentos', d: 'M3 7h18v10H3zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5M6 7v10M18 7v10', admin: true },
  { para: '/painel/ajustes', nome: 'Ajustes', d: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM19 12l2-1-1-3-2 .3-1.4-1.4.3-2-3-1-1 2h-2l-1-2-3 1 .3 2L6 7.3 4 7 3 10l2 1v2l-2 1 1 3 2-.3 1.4 1.4-.3 2 3 1 1-2h2l1 2 3-1-.3-2 1.4-1.4 2 .3 1-3-2-1z', admin: true },
];

export function LayoutPainel() {
  const sessao = sessaoAtual();
  const navegar = useNavigate();
  if (!sessao) return <Navigate to="/painel/entrar" replace />;
  const admin = sessao.perfil === 'ADMIN';

  return (
    <div className="painel">
      <header className="painel-topo">
        <span style={{ color: 'var(--tinta)' }}><Monograma tamanho={34} /></span>
        <span className="rotulo">Estúdio</span>
        <div className="quem">
          <span style={{ fontSize: 14 }}>{sessao.nome}</span>
          <button className="botao-link" style={{ fontSize: 12, justifySelf: 'end' }}
            onClick={() => { salvarSessao(null); navegar('/painel/entrar'); }}>Sair</button>
        </div>
      </header>
      <nav className="painel-nav" aria-label="Painel">
        {ITENS.filter((i) => admin || !i.admin).map((i) => (
          <NavLink key={i.para} to={i.para} end={i.fim}>{icone(i.d)}<span>{i.nome}</span></NavLink>
        ))}
      </nav>
      <main className="painel-corpo"><Outlet /></main>
    </div>
  );
}
