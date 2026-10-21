import { useEffect, useState } from 'react';
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api, salvarSessao, sessaoAtual } from '../api';
import { Monograma } from '../componentes/Logo';
import { Ambiente, Numero, useCasca } from '../componentes/Movimento';
import type { Painel, PortfolioFoto } from '../tipos';
import { primeiroNome } from '../util';
import './painel.css';

const icone = (d: string) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} aria-hidden="true"><path d={d} /></svg>
);

const ITENS = [
  { para: '/painel', fim: true, nome: 'Início', d: 'M4 11 12 4l8 7v9h-5v-6H9v6H4z', admin: false, base: true },
  { para: '/painel/agenda', nome: 'Agenda', d: 'M4 6h16v14H4zM4 10h16M8 3v5M16 3v5M8 14h3v3H8z', admin: false, base: true },
  { para: '/painel/trabalhos', nome: 'Trabalhos', d: 'M4 7h16v12H4zM9 7V5h6v2M12 11a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5', admin: false, base: true },
  { para: '/painel/vendas', nome: 'Vendas', d: 'M3 12V4h8l10 10-8 8zM7.5 6.5a1 1 0 1 0 0 2 1 1 0 0 0 0-2', admin: false, base: true },
  { para: '/painel/clientes', nome: 'Clientes', d: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c1-4 4.5-6 8-6s7 2 8 6', admin: false },
  { para: '/painel/relacionamento', nome: 'Relacionamento', d: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z', admin: true },
  { para: '/painel/equipe', nome: 'Equipe', d: 'M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 20c.6-3.4 3-5 6-5s5.4 1.6 6 5M16 5.5a3 3 0 0 1 0 5.5M18 15c1.8.6 2.8 2.2 3 5', admin: false },
  { para: '/painel/financeiro', nome: 'Financeiro', d: 'M3 7h18v10H3zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5M6 7v10M18 7v10', admin: true },
  { para: '/painel/relatorios', nome: 'Relatórios', d: 'M3 20h18M6 20v-7M11 20V5M16 20v-10M21 20v-4', admin: true },
  { para: '/painel/ajustes', nome: 'Ajustes', d: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM19 12l2-1-1-3-2 .3-1.4-1.4.3-2-3-1-1 2h-2l-1-2-3 1 .3 2L6 7.3 4 7 3 10l2 1v2l-2 1 1 3 2-.3 1.4 1.4-.3 2 3 1 1-2h2l1 2 3-1-.3-2 1.4-1.4 2 .3 1-3-2-1z', admin: true },
];
const MAIS = 'M5 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3M12 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3M19 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3';

/** Foto em destaque do portfólio, usada como fundo desfocado do painel. */
function useFundo() {
  const [fundo, setFundo] = useState<string | null>(null);
  useEffect(() => {
    api<PortfolioFoto[]>('/publico/portfolio', { token: null })
      .then((l) => { const f = l.find((x) => x.destaque) ?? l[0]; if (f) setFundo(`/api/publico/portfolio/${f.id}/imagem`); })
      .catch(() => {});
  }, []);
  return fundo;
}

export function LayoutPainel() {
  const sessao = sessaoAtual();
  const navegar = useNavigate();
  const fundo = useFundo();
  useCasca();
  const local = useLocation();
  const [resumo, setResumo] = useState<Painel | null>(null);
  const [mais, setMais] = useState(false);
  useEffect(() => { setMais(false); }, [local.pathname]);
  useEffect(() => {
    if (!mais) return;
    const fechar = (e: KeyboardEvent) => { if (e.key === 'Escape') setMais(false); };
    window.addEventListener('keydown', fechar);
    return () => window.removeEventListener('keydown', fechar);
  }, [mais]);
  useEffect(() => { if (sessao) api<Painel>('/painel').then(setResumo).catch(() => {}); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  if (!sessao) return <Navigate to="/painel/entrar" replace />;
  const admin = sessao.perfil === 'ADMIN';
  const sair = () => { salvarSessao(null); navegar('/painel/entrar'); };
  const itens = ITENS.filter((i) => admin || !i.admin);
  const base = itens.filter((i) => i.base);
  const extras = itens.filter((i) => !i.base);
  const extraAtivo = extras.some((i) => local.pathname.startsWith(i.para)) || local.pathname.startsWith('/painel/conta');

  return (
    <>
      <Ambiente src={fundo} />
      <div className="painel">
        <header className="painel-topo vidro">
          <span style={{ color: 'var(--tinta)' }}><Monograma tamanho={32} /></span>
          <span className="rotulo">Estúdio</span>
          <div className="quem">
            <span style={{ fontSize: 14 }}>{sessao.nome}</span>
            <button className="botao-link" style={{ fontSize: 12, justifySelf: 'end' }} onClick={sair}>Sair</button>
          </div>
        </header>

        <aside className="painel-lateral lateral">
          <div className="marca">
            <span style={{ color: 'var(--tinta)' }}><Monograma tamanho={38} /></span>
            <div><small>{sessao.estudio}</small><b>{primeiroNome(sessao.nome)}</b></div>
          </div>
          {resumo && (
            <dl>
              <div><dt>Pedidos novos</dt><dd style={resumo.pedidosNovos ? { color: 'var(--acento-texto)' } : undefined}><Numero valor={resumo.pedidosNovos} /></dd></div>
              <div><dt>Em seleção</dt><dd><Numero valor={resumo.aguardandoSelecao} /></dd></div>
              <div><dt>Em edição</dt><dd><Numero valor={resumo.emEdicao} /></dd></div>
              {resumo.atrasados.length > 0 && <div><dt>Atrasados</dt><dd style={{ color: 'var(--erro)' }}>{resumo.atrasados.length}</dd></div>}
            </dl>
          )}
          <div>
            <h3>Menu</h3>
            <nav className="opcoes" aria-label="Painel">
              {itens.map((i) => (
                <NavLink key={i.para} to={i.para} end={i.fim}>{icone(i.d)}<span>{i.nome}</span><span className="ok">✓</span></NavLink>
              ))}
            </nav>
          </div>
          <div className="acoes-conta">
            <NavLink className="botao-link legenda" to="/painel/conta">Minha conta</NavLink>
            <button className="botao-link legenda" onClick={sair}>Sair</button>
          </div>
        </aside>

        <nav className="painel-nav vidro" aria-label="Painel">
          {base.map((i) => (
            <NavLink key={i.para} to={i.para} end={i.fim} aria-label={i.nome}>{icone(i.d)}<span>{i.nome}</span></NavLink>
          ))}
          <button className={extraAtivo || mais ? 'active' : ''} aria-expanded={mais} aria-controls="menu-mais" onClick={() => setMais((m) => !m)}>
            {icone(MAIS)}<span>Mais</span>
          </button>
        </nav>
        {mais && (
          <>
            <div className="mais-fundo" onClick={() => setMais(false)} aria-hidden="true" />
            <div id="menu-mais" className="mais-menu" role="menu">
              {extras.map((i) => (
                <NavLink key={i.para} to={i.para} role="menuitem">{icone(i.d)}<span>{i.nome}</span></NavLink>
              ))}
              <NavLink to="/painel/conta" role="menuitem">{icone('M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c1-4 4.5-6 8-6s7 2 8 6M17 4l3 3')}<span>Minha conta</span></NavLink>
              <button role="menuitem" onClick={sair}>{icone('M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10')}<span>Sair</span></button>
            </div>
          </>
        )}
        <main className="painel-corpo" key={local.pathname}><div className="anima-pagina" style={{ display: 'grid', gap: 'var(--espaco-6)', minWidth: 0 }}><Outlet /></div></main>
      </div>
    </>
  );
}
