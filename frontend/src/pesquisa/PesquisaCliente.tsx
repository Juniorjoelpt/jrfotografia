import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { api, ErroApi, publico } from '../api';
import { Casca, Erro } from '../agendar/AgendarPublico';
import type { PesquisaPublica } from '../tipos';
import './pesquisa.css';

function Estrelas({ nome, rotulo }: { nome: string; rotulo: string }) {
  const [v, setV] = useState(0);
  return (
    <fieldset className="ps-estrelas">
      <legend>{rotulo}</legend>
      <div role="radiogroup" aria-label={rotulo}>
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className={n <= v ? 'cheia' : ''}>
            <input type="radio" name={nome} value={n} onChange={() => setV(n)} />
            <span aria-hidden="true">★</span><span className="sr">{n} de 5</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

/** Pesquisa de satisfação que o cliente recebe depois da entrega (NPS + 3 notas + comentário). */
export function PesquisaCliente() {
  const { token = '' } = useParams();
  const [p, setP] = useState<PesquisaPublica | null>(null);
  const [nota, setNota] = useState<number | null>(null);
  const [comentario, setComentario] = useState('');
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    document.title = 'Como foi sua experiência?';
    publico<PesquisaPublica>(`/publico/pesquisas/${token}`).then(setP)
      .catch((e) => setErro(e instanceof ErroApi && e.status === 404 ? 'Pesquisa não encontrada.' : 'Não foi possível abrir agora.'));
  }, [token]);

  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (nota == null) { setErro('Escolha uma nota de 0 a 10.'); return; }
    const f = new FormData(e.currentTarget);
    const num = (k: string) => (f.get(k) ? Number(f.get(k)) : null);
    setEnviando(true);
    setErro('');
    try {
      setP(await api<PesquisaPublica>(`/publico/pesquisas/${token}`, {
        token: null,
        corpo: { nota, atendimento: num('atendimento'), fotos: num('fotos'), prazo: num('prazo'), comentario: comentario || null,
          autorizaDepoimento: f.get('autoriza') === 'on' },
      }));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível enviar. Tente de novo.');
    } finally {
      setEnviando(false);
    }
  }

  if (!p) return <Casca rotulo="Pesquisa">{erro ? <Erro msg={erro} /> : <div className="esqueleto" style={{ height: 280 }} />}</Casca>;

  if (p.respondida) {
    return (
      <Casca titulo={p.estudioNome} rotulo="Pesquisa">
        <div className="aviso sucesso oc-estado" role="status">
          <strong>Obrigado, {p.cliente}! 💛</strong>
          <span>Sua opinião me ajuda a melhorar cada ensaio.</span>
        </div>
        {(p.nota ?? 0) >= 9 && p.estudioInstagram && (
          <p className="suave" style={{ margin: 0 }}>Se puder, marque <b>@{p.estudioInstagram.replace(/^@/, '')}</b> quando postar suas fotos. Isso ajuda demais!</p>
        )}
      </Casca>
    );
  }

  return (
    <Casca titulo={p.estudioNome} rotulo="Pesquisa">
      <form className="oc-bloco ps-form" onSubmit={enviar}>
        <p className="oc-saudacao" style={{ margin: 0 }}>Oi, {p.cliente}! Como foi sua experiência? Leva menos de 1 minuto.</p>
        <fieldset className="ps-nps">
          <legend>De 0 a 10, quanto você indicaria o {p.estudioNome} para uma amiga?</legend>
          <div role="radiogroup" aria-label="Nota de 0 a 10">
            {Array.from({ length: 11 }, (_, n) => (
              <button key={n} type="button" role="radio" aria-checked={nota === n} className={`ps-n${nota === n ? ' marcada' : ''}`} onClick={() => setNota(n)}>{n}</button>
            ))}
          </div>
          <div className="ps-escala"><span>Nada provável</span><span>Com certeza</span></div>
        </fieldset>
        <Estrelas nome="atendimento" rotulo="Atendimento" />
        <Estrelas nome="fotos" rotulo="Qualidade das fotos" />
        <Estrelas nome="prazo" rotulo="Prazo de entrega" />
        <label className="campo"><span>Quer deixar um comentário? (opcional)</span>
          <textarea value={comentario} onChange={(e) => setComentario(e.target.value)} maxLength={2000} rows={3} placeholder="O que você mais gostou? O que posso melhorar?" />
        </label>
        {comentario.trim() && <label className="marcar"><input type="checkbox" name="autoriza" /> Pode publicar meu comentário como depoimento</label>}
        <Erro msg={erro} />
        <button className="botao principal" disabled={enviando || nota == null}>{enviando ? 'Enviando…' : 'Enviar avaliação'}</button>
      </form>
    </Casca>
  );
}
