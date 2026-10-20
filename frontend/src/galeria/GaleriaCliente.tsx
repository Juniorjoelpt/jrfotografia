import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { api, ErroApi } from '../api';
import { Monograma } from '../componentes/Logo';
import { Visualizador } from '../componentes/Visualizador';
import type { FotoGaleria, Galeria } from '../tipos';
import { data, diaMes, inclinacao, linkWhatsapp, moeda, primeiroNome } from '../util';
import './galeria.css';

const chave = (token: string) => `jr.galeria.${token}`;
function lerAcesso(token: string) { try { return sessionStorage.getItem(chave(token)); } catch { return null; } }
function gravarAcesso(token: string, v: string | null) {
  try { if (v) sessionStorage.setItem(chave(token), v); else sessionStorage.removeItem(chave(token)); } catch { /* sem armazenamento */ }
}
function lerVisao(): 'soltas' | 'grade' { try { return localStorage.getItem('jr.galeria.visao') === 'grade' ? 'grade' : 'soltas'; } catch { return 'soltas'; } }
function gravarVisao(v: string) { try { localStorage.setItem('jr.galeria.visao', v); } catch { /* sem armazenamento */ } }

export function GaleriaCliente() {
  const { token = '' } = useParams();
  const [acesso, setAcesso] = useState<string | null>(() => lerAcesso(token));
  const [galeria, setGaleria] = useState<Galeria | null>(null);
  const [erro, setErro] = useState('');
  const [aberta, setAberta] = useState<number | null>(null);
  const [confirmando, setConfirmando] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  const [visao, setVisao] = useState(lerVisao);
  const [soEscolhidas, setSoEscolhidas] = useState(false);

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
  const fotos = soEscolhidas ? g.fotos.filter((f) => f.selecionada) : g.fotos;
  const dm = diaMes(g.dataTrabalho);

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

  const trocarVisao = (v: 'soltas' | 'grade') => { setVisao(v); gravarVisao(v); };
  const foto = aberta !== null ? fotos[aberta] : null;
  const fundo = g.fotos[0] ? url(g.fotos[0].id, 'previa') : undefined;

  const cartao = (f: FotoGaleria, i: number) => (
    <div className={`moldura${f.selecionada && g.exigeSelecao ? ' escolhida' : ''}`}>
      <button className="abrir" onClick={() => setAberta(i)} aria-label={`Ampliar foto ${i + 1}`}>
        <img src={url(f.id, 'miniatura')} alt={`Foto ${i + 1}`} loading="lazy" />
      </button>
      {g.selecaoAberta && (
        <button className="coracao" aria-pressed={f.selecionada} aria-label={f.selecionada ? 'Tirar da seleção' : 'Escolher esta foto'}
          onClick={() => marcar(f.id, !f.selecionada)}><Coracao cheio={f.selecionada} /></button>
      )}
    </div>
  );

  return (
    <>
      <div className="ambiente" style={fundo ? { backgroundImage: `url(${fundo})` } : undefined} />
      <div className="ambiente-veu" />
      <div className="g-layout">
        <aside className="g-lateral lateral">
          <div className="marca">
            <span style={{ color: 'var(--tinta)' }}><Monograma tamanho={38} /></span>
            <div><small>{g.estudioNome}</small><b>{primeiroNome(g.clienteNome)}</b></div>
          </div>
          <dl>
            <div><dt>Total de fotos</dt><dd>{g.fotos.length}</dd></div>
            {g.exigeSelecao && <div><dt>Escolhidas</dt><dd>{g.selecionadas}{limite ? ` / ${limite}` : ''}</dd></div>}
            {g.exigeSelecao && extras > 0 && <div><dt>Extras</dt><dd style={{ color: 'var(--acento)' }}>{moeda(valorExtras)}</dd></div>}
            {g.expiraEm && <div><dt>Disponível até</dt><dd>{data(g.expiraEm).slice(0, 5)}</dd></div>}
          </dl>
          <div>
            <h3>Ver como</h3>
            <div className="opcoes">
              <button aria-pressed={visao === 'soltas'} onClick={() => trocarVisao('soltas')}><IconeSoltas /><span>Fotos soltas</span><span className="ok">✓</span></button>
              <button aria-pressed={visao === 'grade'} onClick={() => trocarVisao('grade')}><IconeGrade /><span>Grade</span><span className="ok">✓</span></button>
              {g.exigeSelecao && g.selecaoAberta && (
                <button aria-pressed={soEscolhidas} onClick={() => setSoEscolhidas(!soEscolhidas)}><Coracao cheio={false} /><span>Só escolhidas</span><span className="ok">✓</span></button>
              )}
            </div>
          </div>
          <button className="botao-link legenda" style={{ justifySelf: 'start' }}
            onClick={() => { gravarAcesso(token, null); setAcesso(null); setGaleria(null); }}>Sair da galeria</button>
        </aside>

        <main className="g-principal">
          <header className="g-situacao">
            <div className="numeral">
              {dm ? <strong>{dm.dia}</strong> : <strong>{g.fotos.length}</strong>}
              <div className="mes">
                <b>{dm ? dm.mes : 'fotos'}</b>
                <span>{g.fotos.length} fotos{soEscolhidas ? ` · ${fotos.length} escolhidas` : ''}</span>
              </div>
            </div>
            <Situacao g={g} extras={extras} valorExtras={valorExtras} />
            {g.status === 'ENTREGUE' && g.downloadLiberado && (
              <div><a className="botao principal" href={`/api/galeria/${token}/download.zip?t=${encodeURIComponent(acesso)}`}>Baixar todas as fotos ({g.fotos.length})</a></div>
            )}
            {erro && <p className="aviso erro" role="alert">{erro}</p>}
          </header>

          {fotos.length === 0
            ? <p className="vazio vidro">{soEscolhidas ? 'Nenhuma foto escolhida ainda.' : 'As fotos ainda não foram enviadas.'}</p>
            : visao === 'soltas'
              ? (
                <div className="soltas">
                  {fotos.map((f, i) => (
                    <div key={f.id} className={`copia t${(i % 4) + 1}`}
                      style={{ '--r': `${inclinacao(f.id)}deg`, '--ar': f.largura && f.altura ? `${f.largura} / ${f.altura}` : '4 / 3' } as React.CSSProperties}>
                      {cartao(f, i)}
                    </div>
                  ))}
                </div>
              )
              : <div className="grade-molduras">{fotos.map((f, i) => <div key={f.id}>{cartao(f, i)}</div>)}</div>}
        </main>
      </div>

      <BarraSelecao g={g} limite={limite} extras={extras} valorExtras={valorExtras}
        confirmando={confirmando} ocupado={ocupado} aoConfirmar={() => setConfirmando(true)}
        aoVoltar={() => setConfirmando(false)} aoConcluir={concluir} />

      {foto && aberta !== null && (
        <Visualizador src={url(foto.id, 'previa')} alt={`Foto ${aberta + 1} de ${fotos.length}`}
          aoFechar={() => setAberta(null)}
          aoAnterior={aberta > 0 ? () => setAberta(aberta - 1) : undefined}
          aoProxima={aberta < fotos.length - 1 ? () => setAberta(aberta + 1) : undefined}
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
    </>
  );
}

/** Barra inferior: na seleção, um trilho de 0 até o pacote com o marcador; depois, as etapas da entrega. */
function BarraSelecao({ g, limite, extras, valorExtras, confirmando, ocupado, aoConfirmar, aoVoltar, aoConcluir }: {
  g: Galeria; limite: number; extras: number; valorExtras: number; confirmando: boolean; ocupado: boolean;
  aoConfirmar: () => void; aoVoltar: () => void; aoConcluir: () => void;
}) {
  if (g.selecaoAberta) {
    // O trilho vai até o pacote + 30% de folga para mostrar as extras.
    const fim = Math.max(limite * 1.3, g.selecionadas + 1);
    const pos = (n: number) => `${Math.min(100, (n / fim) * 100)}%`;
    return (
      <div className="barra-tempo vidro">
        <div className="rotulo-barra"><small>SELEÇÃO</small><b className="num">{g.selecionadas} de {limite}</b></div>
        <div className="trilho" aria-hidden="true">
          <div className="linha" />
          <div className="feito" style={{ width: pos(Math.min(g.selecionadas, limite)) }} />
          {extras > 0 && <div className="excesso" style={{ left: pos(limite), width: `calc(${pos(g.selecionadas)} - ${pos(limite)})` }} />}
          <span className="marco marco-baixo" style={{ left: pos(limite) }}>pacote</span>
          <div className="botao-trilho" style={{ left: pos(g.selecionadas) }} />
        </div>
        {confirmando ? (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', justifyContent: 'flex-end' }}>
            <span className="legenda">{extras > 0 ? `${extras} extras · ${moeda(valorExtras)}. ` : ''}Depois de concluir, não dá para trocar.</span>
            <button className="botao pequeno" onClick={aoVoltar}>Voltar</button>
            <button className="botao principal pequeno" disabled={ocupado} onClick={aoConcluir}>Confirmar</button>
          </div>
        ) : (
          <button className="botao principal" disabled={g.selecionadas === 0} onClick={aoConfirmar}>
            {extras > 0 ? `Concluir · +${moeda(valorExtras)}` : 'Concluir seleção'}
          </button>
        )}
      </div>
    );
  }
  const etapas = g.exigeSelecao ? ['Ensaio', 'Seleção', 'Edição', 'Entrega'] : ['Ensaio', 'Edição', 'Entrega'];
  const atual = g.status === 'ENTREGUE' ? etapas.length - 1 : g.exigeSelecao && g.status !== 'EDICAO' ? 1 : etapas.length - 2;
  const pos = (i: number) => `${6 + (i / (etapas.length - 1)) * 88}%`;
  return (
    <div className="barra-tempo vidro">
      <div className="rotulo-barra"><small>ETAPA</small><b>{etapas[atual]}</b></div>
      <div className="trilho" aria-label={`Etapa ${atual + 1} de ${etapas.length}: ${etapas[atual]}`}>
        <div className="linha" />
        <div className="feito" style={{ width: pos(atual) }} />
        {etapas.map((e, i) => i !== atual && <span key={e} className="marco" style={{ left: pos(i) }}>{e}</span>)}
        <div className="botao-trilho" style={{ left: pos(atual) }} />
      </div>
      <span className="legenda">{g.status === 'ENTREGUE' ? (g.downloadLiberado ? 'PRONTAS PARA BAIXAR' : 'AGUARDANDO PAGAMENTO') : 'EM EDIÇÃO'}</span>
    </div>
  );
}

function Situacao({ g, extras, valorExtras }: { g: Galeria; extras: number; valorExtras: number }) {
  const zap = (msg: string) => g.estudioWhatsapp
    ? <a className="botao pequeno" href={linkWhatsapp(g.estudioWhatsapp, msg)} target="_blank" rel="noreferrer">Falar com o fotógrafo</a> : null;
  if (g.selecaoAberta) {
    return (
      <p>
        Olá, {primeiroNome(g.clienteNome)}! Toque no coração das fotos que você quer receber editadas.
        Seu pacote inclui {g.fotosNoPacote} fotos{g.precoFotoExtra ? `; cada foto a mais custa ${moeda(g.precoFotoExtra)}` : ''}.
        {extras > 0 ? ` Até agora: ${extras} extras, ${moeda(valorExtras)}.` : ''}
      </p>
    );
  }
  if (g.status === 'ENTREGUE' && !g.downloadLiberado) {
    return <><p>Suas fotos estão prontas. {g.motivoDownload}</p>
      <div>{zap(`Olá! Sou ${g.clienteNome}, quero acertar o pagamento para baixar minhas fotos.`)}</div></>;
  }
  if (g.status === 'ENTREGUE') return <p>Suas fotos estão prontas. Baixe todas de uma vez ou abra cada uma.</p>;
  return <><p>Seleção recebida. Suas {g.fotos.length} fotos estão em edição e aparecem aqui quando ficarem prontas.</p>
    <div>{zap(`Olá! Sou ${g.clienteNome}, queria saber das minhas fotos.`)}</div></>;
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
      <form onSubmit={entrar} className="vidro">
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
const IconeSoltas = () => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} aria-hidden="true"><path d="M4 7l9-2 2 9-9 2zM11 10l9 1-1 9-9-1" /></svg>;
const IconeGrade = () => <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} aria-hidden="true"><path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" /></svg>;
