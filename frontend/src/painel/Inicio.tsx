import { Link } from 'react-router-dom';
import { sessaoAtual } from '../api';
import type { Painel, Reserva } from '../tipos';
import { MESES, moeda, primeiroNome } from '../util';
import { Erro, LinhaTrabalho, Linhas, useDados } from './comum';
import { Numero } from '../componentes/Movimento';

export function Inicio() {
  const { dados: p, erro } = useDados<Painel>('/painel');
  const admin = sessaoAtual()?.perfil === 'ADMIN';
  const hoje = new Date();
  const { dados: reservas } = useDados<Reserva[]>(admin ? '/reservas' : null);
  const pendentes = (reservas ?? []).filter((r) => r.status === 'PENDENTE');
  const comComprovante = pendentes.filter((r) => r.comprovante).length;

  return (
    <>
      <div className="cabeca-pagina">
        <div style={{ gap: 'var(--espaco-3)' }}>
          <span className="rotulo">Olá, {primeiroNome(sessaoAtual()?.nome ?? '')}</span>
          <div className="numeral">
            <strong>{hoje.getDate()}</strong>
            <div className="mes">
              <b>{MESES[hoje.getMonth()]}</b>
              <span>{p ? `${p.proximos.length} ${p.proximos.length === 1 ? 'ensaio' : 'ensaios'} nos próximos 7 dias` : ' '}</span>
            </div>
          </div>
        </div>
        {admin && <Link to="/painel/trabalhos/novo" className="botao principal">Novo trabalho</Link>}
      </div>
      {sessaoAtual()?.senhaPadrao && (
        <p className="aviso" role="status" style={{ margin: 0, borderColor: 'var(--alerta)' }}>
          <span style={{ color: 'var(--alerta)' }}>Você ainda usa a senha de fábrica.</span> Quem souber dela entra no painel. <Link to="/painel/conta">Trocar agora</Link>
        </p>
      )}
      {pendentes.length > 0 && (
        <Link to="/painel/agenda#pre-reservas" className="aviso pr-banner" role="status">
          <strong>{pendentes.length === 1 ? '1 pré-reserva do site' : `${pendentes.length} pré-reservas do site`}</strong>
          <span>{comComprovante ? `${comComprovante} com comprovante do Pix para conferir.` : 'Aguardando o Pix do sinal.'} Ver na agenda →</span>
        </Link>
      )}
      <Erro msg={erro} />
      {p && (
        <>
          <div className="numeros escalonado">
            <Link to="/painel/vendas" className={`numero${p.pedidosNovos ? ' atencao' : ''}`}>
              <span className="legenda">PEDIDOS NOVOS</span><strong><Numero valor={p.pedidosNovos} /></strong>
            </Link>
            <Link to="/painel/trabalhos?status=SELECAO" className="numero">
              <span className="legenda">AGUARDANDO SELEÇÃO</span><strong><Numero valor={p.aguardandoSelecao} /></strong>
            </Link>
            <Link to="/painel/trabalhos?status=EDICAO" className="numero">
              <span className="legenda">EM EDIÇÃO</span><strong><Numero valor={p.emEdicao} /></strong>
            </Link>
            {admin && (
              <Link to="/painel/financeiro" className={`numero${p.vencido ? ' critico' : ''}`}>
                <span className="legenda">A RECEBER</span><strong><Numero valor={p.aReceber ?? 0} formatar={moeda} /></strong>
                {!!p.vencido && <span className="legenda" style={{ color: 'var(--erro)' }}>{moeda(p.vencido)} VENCIDO</span>}
              </Link>
            )}
            {admin && (
              <div className="numero">
                <span className="legenda">RECEBIDO NO MÊS</span><strong><Numero valor={p.recebidoNoMes ?? 0} formatar={moeda} /></strong>
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
            <div className="cabeca-pagina"><h2 className="chamada">Próximos 7 dias</h2><Link to="/painel/agenda" className="botao-link" style={{ fontSize: 13 }}>Abrir agenda</Link></div>
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
