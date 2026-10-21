import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ErroApi, publico } from '../api';
import { Casca, Erro } from '../agendar/AgendarPublico';
import type { AlbumPublico } from '../tipos';
import { dataHora, linkWhatsapp } from '../util';
import './album.css';

/** O cliente vê as lâminas do álbum, comenta onde quer mudar e aprova (ou pede ajustes). */
export function AprovarAlbum() {
  const { token = '' } = useParams();
  const [a, setA] = useState<AlbumPublico | null>(null);
  const [erro, setErro] = useState('');
  const [comentarios, setComentarios] = useState<Record<number, string>>({});
  const [geral, setGeral] = useState('');
  const [aberta, setAberta] = useState<number | null>(null);
  const [confirmar, setConfirmar] = useState<'aprovar' | 'ajustes' | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    document.title = 'Aprovar álbum';
    publico<AlbumPublico>(`/publico/albuns/${token}`).then(setA)
      .catch((e) => setErro(e instanceof ErroApi && e.status === 404 ? e.message : 'Não foi possível abrir o álbum agora.'));
  }, [token]);

  async function responder(aprova: boolean) {
    setEnviando(true);
    setErro('');
    try {
      const limpos = Object.fromEntries(Object.entries(comentarios).filter(([, v]) => v.trim()));
      setA(await api<AlbumPublico>(`/publico/albuns/${token}`, { token: null, corpo: { aprova, comentario: geral || null, comentarios: limpos } }));
      setConfirmar(null);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível enviar.');
    } finally {
      setEnviando(false);
    }
  }

  if (!a) return <Casca rotulo="Álbum">{erro ? <Erro msg={erro} /> : <div className="esqueleto" style={{ height: 320 }} />}</Casca>;

  const img = (id: number, t: 'mini' | 'grande') => `/api/publico/albuns/${token}/laminas/${id}/${t}`;
  const qtdComentarios = Object.values(comentarios).filter((v) => v.trim()).length;
  const pendente = a.aprovacao === 'PENDENTE';
  const lamina = aberta != null ? a.laminas[aberta] : null;

  return (
    <Casca titulo={a.estudioNome} rotulo="Álbum">
      {a.aprovacao === 'APROVADO' && (
        <div className="aviso sucesso oc-estado" role="status"><strong>Álbum aprovado!</strong><span>Recebido em {dataHora(a.aprovacaoEm)}. Agora ele vai para a produção.</span></div>
      )}
      {a.aprovacao === 'AJUSTES' && (
        <div className="aviso oc-estado" role="status"><strong>Pedido de ajustes enviado.</strong><span>Assim que a nova versão ficar pronta, você recebe o link de novo.</span></div>
      )}
      <section className="oc-bloco">
        <p className="oc-saudacao" style={{ margin: 0 }}>Oi, {a.cliente}! Esta é a diagramação do seu {a.descricao.toLowerCase()}{a.versao > 1 ? ` (versão ${a.versao})` : ''}.</p>
        {pendente && <p className="suave" style={{ margin: 0 }}>Toque numa lâmina para ver grande e deixar um comentário onde quiser mudar algo. No fim, aprove ou peça os ajustes.</p>}
      </section>

      <div className="ab-laminas">
        {a.laminas.map((l, i) => (
          <button key={l.id} type="button" className={`ab-lamina${comentarios[l.id]?.trim() || l.comentario ? ' comentada' : ''}`} onClick={() => setAberta(i)}>
            <img src={img(l.id, 'mini')} alt={`Lâmina ${l.ordem}`} loading="lazy" />
            <span>Lâmina {l.ordem}{comentarios[l.id]?.trim() || l.comentario ? ' · 💬' : ''}</span>
          </button>
        ))}
      </div>

      {lamina && (
        <div className="ab-zoom" role="dialog" aria-label={`Lâmina ${lamina.ordem}`}>
          <img src={img(lamina.id, 'grande')} alt={`Lâmina ${lamina.ordem}`} />
          <div className="ab-zoom-barra">
            <strong>Lâmina {lamina.ordem} de {a.laminas.length}</strong>
            {pendente ? (
              <textarea placeholder="O que quer mudar nesta lâmina? (opcional)" maxLength={1000} rows={2}
                value={comentarios[lamina.id] ?? ''} onChange={(e) => setComentarios({ ...comentarios, [lamina.id]: e.target.value })} />
            ) : lamina.comentario ? <p className="suave" style={{ margin: 0 }}>“{lamina.comentario}”</p> : null}
            <div className="acoes-linha">
              <button className="botao pequeno" disabled={aberta === 0} onClick={() => setAberta((aberta ?? 0) - 1)}>‹ Anterior</button>
              <button className="botao pequeno" disabled={aberta === a.laminas.length - 1} onClick={() => setAberta((aberta ?? 0) + 1)}>Próxima ›</button>
              <button className="botao pequeno principal" onClick={() => setAberta(null)}>Fechar</button>
            </div>
          </div>
        </div>
      )}

      {pendente && (
        <section className="oc-bloco">
          <label className="campo"><span>Comentário geral (opcional)</span>
            <textarea value={geral} onChange={(e) => setGeral(e.target.value)} maxLength={2000} rows={2} placeholder="Algo sobre o álbum todo: capa, ordem das fotos…" /></label>
          <Erro msg={erro} />
          {confirmar ? (
            <div className="oc-confirmar anima-entrada" role="alertdialog">
              <p style={{ margin: 0 }}>{confirmar === 'aprovar'
                ? 'Ao aprovar, o álbum vai para a produção do jeito que está. Confirma?'
                : `Enviar ${qtdComentarios ? `${qtdComentarios} ${qtdComentarios === 1 ? 'comentário' : 'comentários'}` : 'o comentário'} para ajuste?`}</p>
              <div className="acoes-linha">
                <button className="botao principal" disabled={enviando} onClick={() => responder(confirmar === 'aprovar')}>{enviando ? 'Enviando…' : 'Sim, confirmar'}</button>
                <button className="botao" onClick={() => setConfirmar(null)}>Voltar</button>
              </div>
            </div>
          ) : (
            <div className="oc-acoes">
              <button className="botao principal" onClick={() => setConfirmar('aprovar')}>Aprovar álbum</button>
              <button className="botao" disabled={!qtdComentarios && !geral.trim()} onClick={() => setConfirmar('ajustes')}>
                Pedir ajustes{qtdComentarios ? ` (${qtdComentarios})` : ''}
              </button>
            </div>
          )}
          {!qtdComentarios && !geral.trim() && <p className="legenda" style={{ margin: 0 }}>PARA PEDIR AJUSTES, ESCREVA EM ALGUMA LÂMINA OU NO COMENTÁRIO GERAL.</p>}
        </section>
      )}
      {a.estudioWhatsapp && (
        <footer className="oc-rodape">
          <a className="botao" href={linkWhatsapp(a.estudioWhatsapp, `Oi! Tenho uma dúvida sobre a diagramação do meu álbum.`)} target="_blank" rel="noreferrer">Falar no WhatsApp</a>
        </footer>
      )}
    </Casca>
  );
}
