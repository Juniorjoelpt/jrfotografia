import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { api, ErroApi } from '../api';
import { Logo, Monograma } from '../componentes/Logo';
import { Visualizador } from '../componentes/Visualizador';
import type { Galeria } from '../tipos';
import { data, linkWhatsapp, moeda, primeiroNome } from '../util';
import '../site/site.css';
import './galeria.css';

const chave = (token: string) => `jr.galeria.${token}`;
function lerAcesso(token: string) { try { return sessionStorage.getItem(chave(token)); } catch { return null; } }
function gravarAcesso(token: string, v: string | null) {
  try { if (v) sessionStorage.setItem(chave(token), v); else sessionStorage.removeItem(chave(token)); } catch { /* sem armazenamento */ }
}

export function GaleriaCliente() {
  const { token = '' } = useParams();
  const [acesso, setAcesso] = useState<string | null>(() => lerAcesso(token));
  const [galeria, setGaleria] = useState<Galeria | null>(null);
  const [erro, setErro] = useState('');
  const [aberta, setAberta] = useState<number | null>(null);
  const [confirmando, setConfirmando] = useState(false);
  const [ocupado, setOcupado] = useState(false);

  const carregar = useCallback(async (a: string) => {
    try {
      setGaleria(await api<Galeria>(`/galeria/${token}`, { token: a }));
      setErro('');
    } catch (e) {
      if (e instanceof ErroApi && e.status === 401) { gravarAcesso(token, null); setAcesso(null); }
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível abrir a galeria.');
    }
  }, [token]);

  useEffect(() => { if (acesso) carregar(acesso); }, [acesso, carregar]);

  if (!acesso || !galeria) {
    return <Entrada token={token} erroExterno={acesso ? erro : ''} aoEntrar={(a) => { gravarAcesso(token, a); setAcesso(a); }} />;
  }

  const url = (id: number, tipo: 'miniatura' | 'previa' | 'download') =>
    `/api/galeria/${token}/fotos/${id}/${tipo}?t=${encodeURIComponent(acesso)}`;

  const g = galeria;
  const limite = g.fotosNoPacote ?? 0;
  const extras = g.exigeSelecao ? Math.max(0, g.selecionadas - limite) : 0;
  const valorExtras = extras * (g.precoFotoExtra ?? 0);

  async function marcar(id: number, selecionada: boolean) {
    if (!g.selecaoAberta) return;
    setGaleria({ ...g, selecionadas: g.selecionadas + (selecionada ? 1 : -1), fotos: g.fotos.map((f) => (f.id === id ? { ...f, selecionada } : f)) });
    try {
      setGaleria(await api<Galeria>(`/galeria/${token}/fotos/${id}/selecao`, { metodo: 'PUT', token: acesso, corpo: { selecionada } }));
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível salvar a escolha.');
      carregar(acesso!);
    }
  }

  async function concluir() {
    setOcupado(true);
    try {
      await api(`/galeria/${token}/fechar-selecao`, { token: acesso });
      setConfirmando(false);
      await carregar(acesso!);
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível concluir a seleção.');
    } finally {
      setOcupado(false);
    }
  }

  const foto = aberta !== null ? g.fotos[aberta] : null;

  return (
    <div className="dentro">
      <header className="galeria-topo">
        <Logo largura={84} />
        <button className="botao pequeno" onClick={() => { gravarAcesso(token, null); setAcesso(null); setGaleria(null); }}>Sair</button>
      </header>

      <section className="galeria-cabeca">
        <span className="rotulo">Sua galeria</span>
        <h1 className="titulo">Olá, {primeiroNome(g.clienteNome)}!</h1>
        <Situacao g={g} extras={extras} valorExtras={valorExtras} />
        {g.status === 'ENTREGUE' && g.downloadLiberado && (
          <div><a className="botao principal" href={`/api/galeria/${token}/download.zip?t=${encodeURIComponent(acesso)}`}>Baixar todas as fotos ({g.fotos.length})</a></div>
        )}
        {g.expiraEm && <span className="legenda">GALERIA DISPONÍVEL ATÉ {data(g.expiraEm)}</span>}
        {erro && <p className="aviso erro" role="alert" style={{ margin: 0 }}>{erro}</p>}
      </section>

      {g.fotos.length === 0
        ? <p className="vazio">As fotos ainda não foram enviadas.</p>
        : (
          <div className="galeria-grade">
            {g.fotos.map((f, i) => (
              <div key={f.id} className={`galeria-foto${f.selecionada && g.exigeSelecao ? ' escolhida' : ''}`}>
                <button className="abrir" onClick={() => setAberta(i)} aria-label={`Ampliar foto ${i + 1}`}>
                  <img src={url(f.id, 'miniatura')} alt={`Foto ${i + 1}`} loading="lazy" />
                </button>
                {g.selecaoAberta && (
                  <button className="coracao" aria-pressed={f.selecionada} aria-label={f.selecionada ? 'Tirar da seleção' : 'Escolher esta foto'}
                    onClick={() => marcar(f.id, !f.selecionada)}><Coracao cheio={f.selecionada} /></button>
                )}
              </div>
            ))}
          </div>
        )}

      {g.selecaoAberta && (
        <div className="barra-selecao">
          <div className="dentro">
            <div style={{ display: 'grid' }}>
              <strong className="num">{g.selecionadas} de {limite} escolhidas</strong>
              <span className="legenda">{extras > 0 ? `${extras} ${extras === 1 ? 'FOTO EXTRA' : 'FOTOS EXTRAS'} · ${moeda(valorExtras)}` : g.selecionadas < limite ? `FALTAM ${limite - g.selecionadas}` : 'PACOTE COMPLETO'}</span>
            </div>
            {confirmando ? (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                <span className="legenda">Depois de concluir, não dá para trocar.</span>
                <button className="botao pequeno" onClick={() => setConfirmando(false)}>Voltar</button>
                <button className="botao principal pequeno" disabled={ocupado} onClick={concluir}>Confirmar seleção</button>
              </div>
            ) : (
              <button className="botao principal" disabled={g.selecionadas === 0} onClick={() => setConfirmando(true)}>Concluir seleção</button>
            )}
          </div>
        </div>
      )}

      {foto && aberta !== null && (
        <Visualizador src={url(foto.id, 'previa')} alt={`Foto ${aberta + 1} de ${g.fotos.length}`}
          aoFechar={() => setAberta(null)}
          aoAnterior={aberta > 0 ? () => setAberta(aberta - 1) : undefined}
          aoProxima={aberta < g.fotos.length - 1 ? () => setAberta(aberta + 1) : undefined}
          rodape={
            <div style={{ display: 'flex', gap: 8 }}>
              {g.selecaoAberta && (
                <button className={`botao pequeno${foto.selecionada ? ' principal' : ''}`} onClick={() => marcar(foto.id, !foto.selecionada)}>
                  {foto.selecionada ? 'Escolhida' : 'Escolher esta foto'}
                </button>
              )}
              {g.status === 'ENTREGUE' && g.downloadLiberado && <a className="botao pequeno" href={url(foto.id, 'download')}>Baixar em alta</a>}
            </div>
          } />
      )}
    </div>
  );
}

function Situacao({ g, extras, valorExtras }: { g: Galeria; extras: number; valorExtras: number }) {
  const zap = (msg: string) => g.estudioWhatsapp
    ? <a className="botao pequeno" href={linkWhatsapp(g.estudioWhatsapp, msg)} target="_blank" rel="noreferrer">Falar com o fotógrafo</a> : null;
  if (g.selecaoAberta) {
    return (
      <>
        <p className="suave" style={{ margin: 0 }}>
          Toque no coração das fotos que você quer receber editadas. Seu pacote inclui {g.fotosNoPacote} fotos
          {g.precoFotoExtra ? `; cada foto a mais custa ${moeda(g.precoFotoExtra)}` : ''}.
        </p>
        {extras > 0 && <span className="etiqueta cheia">{extras} extras · {moeda(valorExtras)}</span>}
      </>
    );
  }
  if (g.status === 'EDICAO' || g.status === 'FOTOGRAFADO' || g.status === 'SELECAO') {
    return <>
      <p className="suave" style={{ margin: 0 }}>Seleção recebida. Suas {g.fotos.length} fotos estão em edição e aparecem aqui quando ficarem prontas.</p>
      <div>{zap(`Olá! Sou ${g.clienteNome}, queria saber das minhas fotos.`)}</div>
    </>;
  }
  if (g.status === 'ENTREGUE' && !g.downloadLiberado) {
    return <>
      <p className="suave" style={{ margin: 0 }}>Suas fotos estão prontas. {g.motivoDownload}</p>
      <div>{zap(`Olá! Sou ${g.clienteNome}, quero acertar o pagamento para baixar minhas fotos.`)}</div>
    </>;
  }
  return <p className="suave" style={{ margin: 0 }}>Suas fotos estão prontas. Baixe todas de uma vez ou abra cada uma.</p>;
}

function Entrada({ token, erroExterno, aoEntrar }: { token: string; erroExterno: string; aoEntrar: (acesso: string) => void }) {
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function entrar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const senha = String(new FormData(e.currentTarget).get('senha') ?? '');
    setEnviando(true);
    setErro('');
    try {
      const r = await api<{ acesso: string }>(`/galeria/${token}/acesso`, { token: null, corpo: { senha } });
      aoEntrar(r.acesso);
    } catch (er) {
      setErro(er instanceof ErroApi ? er.message : 'Não foi possível entrar.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="entrada-galeria textura">
      <form onSubmit={entrar}>
        <Monograma tamanho={72} />
        <div style={{ display: 'grid', gap: 8 }}>
          <span className="rotulo">Sua galeria</span>
          <h1 className="titulo">Digite a senha que você recebeu</h1>
        </div>
        <label className="campo" style={{ width: '100%' }}>
          <span className="sr">Senha da galeria</span>
          <input id="galeria-senha" name="senha" required inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="000000" />
        </label>
        {(erro || erroExterno) && <p className="aviso erro" role="alert" style={{ margin: 0, width: '100%' }}>{erro || erroExterno}</p>}
        <button className="botao principal" style={{ width: '100%' }} disabled={enviando}>{enviando ? 'Abrindo…' : 'Abrir galeria'}</button>
      </form>
    </main>
  );
}

function Coracao({ cheio }: { cheio: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" fill={cheio ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={1.8}>
      <path d="M12 20.5s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.6a4.3 4.3 0 0 1 7.5 2.7c0 5.6-7.5 10.2-7.5 10.2z" />
    </svg>
  );
}
