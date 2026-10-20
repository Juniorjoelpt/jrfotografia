import { Link } from 'react-router-dom';
import { sessaoAtual } from '../api';
import type { Painel } from '../tipos';
import { moeda, primeiroNome } from '../util';
import { Erro, LinhaTrabalho, Linhas, useDados } from './comum';

export function Inicio() {
  const { dados: p, erro } = useDados<Painel>('/painel');
  const admin = sessaoAtual()?.perfil === 'ADMIN';

  return (
    <>
      <div className="cabeca-pagina">
        <div>
          <span className="rotulo">Início</span>
          <h1 className="titulo">Olá, {primeiroNome(sessaoAtual()?.nome ?? '')}</h1>
        </div>
        {admin && <Link to="/painel/trabalhos/novo" className="botao principal">Novo trabalho</Link>}
      </div>
      <Erro msg={erro} />
      {p && (
        <>
          <div className="numeros">
            <Link to="/painel/pedidos" className={`numero${p.pedidosNovos ? ' atencao' : ''}`}>
              <span className="legenda">PEDIDOS NOVOS</span><strong>{p.pedidosNovos}</strong>
            </Link>
            <Link to="/painel/trabalhos?status=SELECAO" className="numero">
              <span className="legenda">AGUARDANDO SELEÇÃO</span><strong>{p.aguardandoSelecao}</strong>
            </Link>
            <Link to="/painel/trabalhos?status=EDICAO" className="numero">
              <span className="legenda">EM EDIÇÃO</span><strong>{p.emEdicao}</strong>
            </Link>
            {admin && (
              <Link to="/painel/recebimentos" className={`numero${p.vencido ? ' critico' : ''}`}>
                <span className="legenda">A RECEBER</span><strong>{moeda(p.aReceber)}</strong>
                {!!p.vencido && <span className="legenda" style={{ color: 'var(--erro)' }}>{moeda(p.vencido)} VENCIDO</span>}
              </Link>
            )}
            {admin && (
              <div className="numero">
                <span className="legenda">RECEBIDO NO MÊS</span><strong>{moeda(p.recebidoNoMes)}</strong>
              </div>
            )}
          </div>

          {p.atrasados.length > 0 && (
            <section className="secao">
              <h2 className="chamada" style={{ color: 'var(--erro)' }}>Entregas atrasadas</h2>
              <Linhas itens={p.atrasados.map((t) => <LinhaTrabalho key={t.id} t={t} mostrar="prazo" />)} vazio="" />
            </section>
          )}

          <section className="secao">
            <h2 className="chamada">Próximos 7 dias</h2>
            <Linhas itens={p.proximos.map((t) => <LinhaTrabalho key={t.id} t={t} />)} vazio="Nenhum ensaio marcado para esta semana." />
          </section>

          <section className="secao">
            <h2 className="chamada">Entregas nos próximos 3 dias</h2>
            <Linhas itens={p.entregasProximas.map((t) => <LinhaTrabalho key={t.id} t={t} mostrar="prazo" />)} vazio="Nenhuma entrega vencendo." />
          </section>
        </>
      )}
    </>
  );
}
