import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ErroApi, urlPainel } from '../api';
import type { Reserva, StatusReserva } from '../tipos';
import { dataHora, diaPorExtenso, hora, linkWhatsapp, moeda, primeiroNome, telefone } from '../util';
import { Erro, useDados } from './comum';

export const NOMES_RESERVA: Record<StatusReserva, string> = {
  PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada', RECUSADA: 'Recusada', EXPIRADA: 'Expirada', CANCELADA: 'Cancelada',
};

/** Pré-reservas feitas pelo site: ver comprovante, confirmar (vira trabalho) ou recusar. */
export function PreReservas({ aoMudar }: { aoMudar?: () => void }) {
  const { dados, erro, recarregar } = useDados<Reserva[]>('/reservas');
  const [todas, setTodas] = useState(false);
  const temDados = !!dados;
  useEffect(() => {
    if (temDados && window.location.hash === '#pre-reservas') {
      setTimeout(() => document.getElementById('pre-reservas')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
    }
  }, [temDados]);
  if (!dados) return <Erro msg={erro} />;
  const pendentes = dados.filter((r) => r.status === 'PENDENTE');
  const lista = todas ? dados.slice(0, 30) : pendentes;
  if (dados.length === 0) return null;
  const mudou = () => { recarregar(); aoMudar?.(); };
  return (
    <section className="secao" id="pre-reservas">
      <div className="cabeca-pagina">
        <h2 className="subtitulo">Pré-reservas do site{pendentes.length ? ` · ${pendentes.length}` : ''}</h2>
        <button className="botao pequeno" aria-pressed={todas} onClick={() => setTodas((t) => !t)}>{todas ? 'Só pendentes' : 'Ver histórico'}</button>
      </div>
      {lista.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>Nenhuma pré-reserva esperando você.</p>
        : <div className="pr-lista escalonado">{lista.map((r) => <CartaoReserva key={r.id} r={r} aoMudar={mudou} />)}</div>}
    </section>
  );
}

function CartaoReserva({ r, aoMudar }: { r: Reserva; aoMudar: () => void }) {
  const [recusando, setRecusando] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState('');
  const pendente = r.status === 'PENDENTE';

  async function agir(caminho: string, corpo?: unknown) {
    setEnviando(true);
    setErro('');
    try {
      await api(`/reservas/${r.id}/${caminho}`, { metodo: 'POST', corpo });
      aoMudar();
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível concluir.');
    } finally {
      setEnviando(false);
    }
  }

  const zap = linkWhatsapp(r.telefone, pendente && !r.comprovante
    ? `Olá, ${primeiroNome(r.nome)}! Vi sua pré-reserva para ${diaPorExtenso(r.inicio)} às ${hora(r.inicio)}. Para garantir, falta o comprovante do Pix do sinal (${moeda(r.valorSinal)}).`
    : `Olá, ${primeiroNome(r.nome)}! Sobre sua reserva para ${diaPorExtenso(r.inicio)} às ${hora(r.inicio)}.`);

  return (
    <article className={`pr-cartao painel-caixa${pendente ? (r.comprovante ? ' pronto' : ' aguardando') : ' encerrado'}`}>
      <div className="pr-topo">
        <div>
          <strong>{r.nome}</strong>
          <span className="legenda">{telefone(r.telefone)}{r.tipo ? ` · ${r.tipo.toUpperCase()}` : ''}</span>
        </div>
        <span className="etiqueta">{pendente ? (r.comprovante ? 'Comprovante enviado' : 'Aguardando Pix') : NOMES_RESERVA[r.status]}</span>
      </div>
      <p className="pr-quando">{diaPorExtenso(r.inicio)} · {hora(r.inicio)}–{hora(r.fim)}</p>
      <p className="legenda" style={{ margin: 0 }}>
        {r.campanha ? 'MINI-SESSÃO · ' : ''}{r.pacote.replace(/^Mini-sessão · /, '').toUpperCase()} · {moeda(r.valorTotal)}{r.cupomCodigo ? ` (CÓDIGO ${r.cupomCodigo}, −${moeda(r.desconto)})` : ''} · SINAL {moeda(r.valorSinal)}
        {pendente && !r.comprovante ? ` · SEGURA ATÉ ${dataHora(r.expiraEm).toUpperCase()}` : ''}
      </p>
      {r.mensagem && <p className="suave" style={{ margin: 0, whiteSpace: 'pre-line' }}>“{r.mensagem}”</p>}
      {r.motivoRecusa && <p className="suave" style={{ margin: 0 }}>Motivo: {r.motivoRecusa}</p>}
      <Erro msg={erro} />
      {recusando ? (
        <div className="grade-form anima-entrada">
          <label className="campo"><span>Motivo (a cliente vê)</span>
            <input value={motivo} maxLength={300} onChange={(e) => setMotivo(e.target.value)} placeholder="Pix não identificado, horário indisponível..." /></label>
          <div className="acoes-linha">
            <button className="botao" disabled={enviando} onClick={() => agir('recusa', { motivo: motivo || null })}>Recusar reserva</button>
            <button className="botao-link" onClick={() => setRecusando(false)}>Voltar</button>
          </div>
        </div>
      ) : (
        <div className="acoes-linha">
          {r.comprovante && <a className="botao pequeno" href={urlPainel(`/reservas/${r.id}/comprovante`)} target="_blank" rel="noreferrer">Ver comprovante</a>}
          {pendente && <button className="botao pequeno principal" disabled={enviando} onClick={() => agir('confirmacao')}>{enviando ? 'Confirmando…' : 'Confirmar ensaio'}</button>}
          {pendente && <button className="botao pequeno" onClick={() => setRecusando(true)}>Recusar</button>}
          <a className="botao pequeno" href={zap} target="_blank" rel="noreferrer">WhatsApp</a>
          {r.trabalhoId && <Link className="botao pequeno" to={`/painel/trabalhos/${r.trabalhoId}`}>Abrir trabalho</Link>}
        </div>
      )}
    </article>
  );
}
