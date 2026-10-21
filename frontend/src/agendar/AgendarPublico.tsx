import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, ErroApi, publico } from '../api';
import { Logo } from '../componentes/Logo';
import { Ambiente } from '../componentes/Movimento';
import { SeletorHorario } from '../componentes/SeletorHorario';
import type { AgendaPublica, AreaCliente, PacoteOnline, PortfolioFoto, ReservaPublica } from '../tipos';
import { data, diaPorExtenso, duracaoTexto, hora, linkWhatsapp, moeda, primeiroNome, TIPOS_ENSAIO } from '../util';
import '../orcamento/orcamento.css';
import './agendar.css';

/** Foto de destaque do portfólio como fundo das páginas públicas. */
function useFundo() {
  const [fundo, setFundo] = useState<string | null>(null);
  useEffect(() => {
    api<PortfolioFoto[]>('/publico/portfolio', { token: null })
      .then((l) => { const f = l.find((x) => x.destaque) ?? l[0]; if (f) setFundo(`/api/publico/portfolio/${f.id}/imagem`); })
      .catch(() => {});
  }, []);
  return fundo;
}

function Casca({ children, titulo, rotulo }: { children: React.ReactNode; titulo?: string; rotulo?: string }) {
  const fundo = useFundo();
  return (
    <>
      <Ambiente src={fundo} />
      <main className="oc-pagina">
        <article className="oc-cartao vidro anima-pagina">
          <header className="oc-topo">
            <Link to="/" style={{ color: 'var(--tinta)' }} aria-label="Voltar ao site"><Logo largura={120} titulo={titulo ?? 'JR Fotografia'} /></Link>
            {rotulo && <div className="oc-num"><span className="rotulo">{rotulo}</span></div>}
          </header>
          {children}
        </article>
      </main>
    </>
  );
}

const Erro = ({ msg }: { msg: string }) => (msg ? <p className="aviso erro" role="alert" style={{ margin: 0 }}>{msg}</p> : null);
const quando = (inicio: string, fim?: string) => `${diaPorExtenso(inicio)} · ${hora(inicio)}${fim ? ` às ${hora(fim)}` : ''}`;

// ====================================================================== /agendar

export function Agendar() {
  const [busca] = useSearchParams();
  const navegar = useNavigate();
  const [agenda, setAgenda] = useState<AgendaPublica | null>(null);
  const [erro, setErro] = useState('');
  const [pacote, setPacote] = useState<PacoteOnline | null>(null);
  const [inicio, setInicio] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    document.title = 'Agendar ensaio';
    publico<AgendaPublica>('/publico/agenda-online')
      .then((a) => {
        setAgenda(a);
        const id = Number(busca.get('pacote'));
        const p = a.pacotes.find((x) => x.id === id) ?? (a.pacotes.length === 1 ? a.pacotes[0] : null);
        if (p) setPacote(p);
      })
      .catch(() => setErro('Não foi possível abrir a agenda agora. Tente de novo em instantes.'));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!pacote || !inicio) return;
    const f = new FormData(e.currentTarget);
    setEnviando(true);
    setErro('');
    try {
      const r = await api<ReservaPublica>('/publico/reservas', {
        token: null,
        corpo: {
          pacoteId: pacote.id, inicio, nome: f.get('nome'), telefone: f.get('telefone'), email: f.get('email') || null,
          tipo: f.get('tipo'), mensagem: f.get('mensagem') || null, origem: f.get('origem'), consentimento: f.get('consentimento') === 'on',
        },
      });
      navegar(`/reserva/${r.token}`);
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível reservar. Tente de novo.');
      if (ex instanceof ErroApi && ex.status === 409) setInicio(null);
    } finally {
      setEnviando(false);
    }
  }

  if (!agenda) return <Casca rotulo="Agendar">{erro ? <Erro msg={erro} /> : <div className="esqueleto" style={{ height: 280 }} />}</Casca>;

  if (!agenda.ativo || agenda.pacotes.length === 0) {
    return (
      <Casca rotulo="Agendar">
        <p className="oc-saudacao">A agenda online está fechada no momento.</p>
        <p className="suave" style={{ margin: 0 }}>Peça um orçamento pelo site e eu te respondo com as datas livres.</p>
        <div className="oc-acoes"><Link className="botao principal" to="/#orcamento">Pedir orçamento</Link></div>
      </Casca>
    );
  }

  const etapa = !pacote ? 1 : !inicio ? 2 : 3;

  return (
    <Casca rotulo="Agendar">
      <ol className="ag-etapas" aria-label="Etapas">
        {['Pacote', 'Data e horário', 'Seus dados'].map((n, i) => (
          <li key={n} aria-current={etapa === i + 1 ? 'step' : undefined} className={etapa > i + 1 ? 'feita' : ''}>{n}</li>
        ))}
      </ol>

      <section className="oc-bloco">
        <h2 className="chamada">1. Pacote</h2>
        <div className="ag-pacotes" role="radiogroup" aria-label="Pacotes">
          {agenda.pacotes.map((p) => (
            <button key={p.id} type="button" role="radio" aria-checked={pacote?.id === p.id} className="ag-pacote"
              onClick={() => { setPacote(p); setInicio(null); }}>
              <strong>{p.nome}</strong>
              <span className="num">{moeda(p.preco)}</span>
              <small>{duracaoTexto(p.duracaoMin)}{p.qtdFotos ? ` · ${p.qtdFotos} fotos` : ''} · sinal {moeda(p.sinal)}</small>
            </button>
          ))}
        </div>
      </section>

      {pacote && (
        <section className="oc-bloco">
          <h2 className="chamada">2. Data e horário</h2>
          <SeletorHorario key={pacote.id} valor={inicio} aoEscolher={setInicio}
            carregar={(de, ate) => publico(`/publico/agenda-online/vagas?pacote=${pacote.id}&de=${de}&ate=${ate}`)} />
          {inicio && <p className="ag-escolha" role="status">{quando(inicio)}</p>}
        </section>
      )}

      {pacote && inicio && (
        <form className="oc-bloco grade-form duas" onSubmit={enviar}>
          <h2 className="chamada inteira">3. Seus dados</h2>
          <label className="campo inteira"><span>Seu nome</span><input name="nome" required maxLength={120} autoComplete="name" /></label>
          <label className="campo"><span>WhatsApp com DDD</span><input name="telefone" required inputMode="tel" autoComplete="tel" placeholder="(98) 9 0000-0000" /></label>
          <label className="campo"><span>E-mail (opcional)</span><input name="email" type="email" autoComplete="email" /></label>
          <label className="campo"><span>Tipo de ensaio</span>
            <select name="tipo" defaultValue="Ensaio feminino">{TIPOS_ENSAIO.map((t) => <option key={t}>{t}</option>)}</select>
          </label>
          <label className="campo"><span>Como conheceu meu trabalho?</span>
            <select name="origem" defaultValue="SITE">
              <option value="INSTAGRAM">Instagram</option><option value="INDICACAO">Indicação</option>
              <option value="WHATSAPP">WhatsApp</option><option value="FEIRA">Feira ou evento</option><option value="SITE">Google ou site</option>
            </select>
          </label>
          <label className="campo inteira"><span>Mensagem (opcional)</span><textarea name="mensagem" maxLength={2000} placeholder="Ideia de ensaio, local, quantas pessoas..." /></label>
          <div className="ag-resumo inteira">
            <div><span>{pacote.nome}</span><b className="num">{moeda(pacote.preco)}</b></div>
            <div><span>Sinal para garantir a data ({agenda.sinalPercentual}%)</span><b className="num">{moeda(pacote.sinal)}</b></div>
            <p className="legenda" style={{ margin: 0 }}>
              O HORÁRIO FICA SEGURO POR {agenda.reservaHoras}H ENQUANTO VOCÊ ENVIA O COMPROVANTE DO PIX DO SINAL.
              REMARCAÇÃO ATÉ {agenda.remarcacaoHoras}H ANTES. CANCELAMENTO COM DEVOLUÇÃO DO SINAL ATÉ {agenda.reembolsoDias} DIAS ANTES.
            </p>
          </div>
          <label className="marcar inteira"><input type="checkbox" name="consentimento" required />
            Autorizo o uso destes dados só para o agendamento e o contato sobre o ensaio.</label>
          {erro && <p className="aviso erro inteira" role="alert" style={{ margin: 0 }}>{erro}</p>}
          <button className="botao principal inteira" disabled={enviando}>{enviando ? 'Reservando…' : 'Reservar horário'}</button>
        </form>
      )}
      {!(pacote && inicio) && <Erro msg={erro} />}
    </Casca>
  );
}

// ====================================================================== /reserva/:token

function useContagem(ate?: string) {
  const [agora, setAgora] = useState(Date.now());
  useEffect(() => { const i = setInterval(() => setAgora(Date.now()), 30000); return () => clearInterval(i); }, []);
  if (!ate) return '';
  const ms = new Date(ate).getTime() - agora;
  if (ms <= 0) return 'prazo encerrado';
  const h = Math.floor(ms / 3600000), m = Math.floor((ms % 3600000) / 60000);
  return h ? `${h}h ${String(m).padStart(2, '0')}min` : `${m} min`;
}

export function ReservaCliente() {
  const { token = '' } = useParams();
  const [r, setR] = useState<ReservaPublica | null>(null);
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [copiado, setCopiado] = useState(false);
  const resta = useContagem(r?.expiraEm);

  useEffect(() => {
    document.title = 'Sua reserva';
    publico<ReservaPublica>(`/publico/reservas/${token}`)
      .then(setR)
      .catch((e) => setErro(e instanceof ErroApi && e.status === 404 ? 'Reserva não encontrada. Confira o link.' : 'Não foi possível abrir a reserva agora.'));
  }, [token]);

  async function enviarComprovante(arquivo: File | undefined) {
    if (!arquivo) return;
    const fd = new FormData();
    fd.append('arquivo', arquivo);
    setEnviando(true);
    setErro('');
    try {
      setR(await api<ReservaPublica>(`/publico/reservas/${token}/comprovante`, { token: null, arquivos: fd }));
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível enviar. Tente de novo.');
    } finally {
      setEnviando(false);
    }
  }

  async function copiar(texto: string) {
    try { await navigator.clipboard.writeText(texto); } catch {
      const t = document.createElement('textarea'); t.value = texto; document.body.appendChild(t); t.select();
      try { document.execCommand('copy'); } catch { /* sem área de transferência */ } t.remove();
    }
    setCopiado(true);
    setTimeout(() => setCopiado(false), 2000);
  }

  if (!r) return <Casca rotulo="Reserva">{erro ? <Erro msg={erro} /> : <div className="esqueleto" style={{ height: 280 }} />}</Casca>;

  const zap = (m: string) => linkWhatsapp(r.estudioWhatsapp, m);
  const pendente = r.status === 'PENDENTE';

  return (
    <Casca titulo={r.estudioNome} rotulo="Reserva">
      {pendente && !r.comprovanteEnviado && (
        <div className="aviso oc-estado" role="status">
          <strong>Horário pré-reservado</strong>
          <span>Envie o comprovante do Pix do sinal em até <b>{resta}</b> para garantir. Depois disso o horário volta a ficar livre.</span>
        </div>
      )}
      {pendente && r.comprovanteEnviado && (
        <div className="aviso sucesso oc-estado" role="status">
          <strong>Comprovante recebido</strong>
          <span>Agora é comigo: confiro o Pix e confirmo seu ensaio. Você recebe a confirmação no WhatsApp.</span>
        </div>
      )}
      {r.status === 'CONFIRMADA' && (
        <div className="aviso sucesso oc-estado" role="status">
          <strong>Ensaio confirmado!</strong>
          <span>Pelo link da sua área você acompanha pagamentos, remarca ou cancela dentro das regras.</span>
        </div>
      )}
      {r.status === 'RECUSADA' && (
        <div className="aviso erro oc-estado" role="status">
          <strong>Não foi possível confirmar esta reserva.</strong>
          {r.motivoRecusa && <span>{r.motivoRecusa}</span>}
        </div>
      )}
      {r.status === 'EXPIRADA' && <div className="aviso oc-estado"><strong>O prazo para enviar o comprovante acabou.</strong><span>Se ainda quiser este ensaio, escolha um novo horário.</span></div>}
      {r.status === 'CANCELADA' && <div className="aviso oc-estado"><strong>Reserva cancelada.</strong></div>}

      <section className="oc-bloco">
        <p className="oc-saudacao">Olá, {primeiroNome(r.nome)}! Seu ensaio <b>{r.pacote}</b>:</p>
        <p className="ag-escolha" style={{ margin: 0 }}>{quando(r.inicio, r.fim)}</p>
        <dl className="oc-itens">
          <div><dt>Valor do pacote</dt><dd className="num">{moeda(r.valorTotal)}</dd></div>
          <div><dt>Sinal para garantir a data</dt><dd className="num">{moeda(r.valorSinal)}</dd></div>
          <div><dt>Restante, no dia do ensaio</dt><dd className="num">{moeda(r.valorTotal - r.valorSinal)}</dd></div>
        </dl>
      </section>

      {pendente && !r.comprovanteEnviado && (
        <section className="oc-bloco">
          <h2 className="chamada">Pague o sinal pelo Pix</h2>
          {r.chavePix ? (
            <div className="ag-pix">
              <span className="rotulo">Chave Pix</span>
              <code>{r.chavePix}</code>
              <button type="button" className="botao pequeno" onClick={() => copiar(r.chavePix!)}>{copiado ? 'Copiado!' : 'Copiar chave'}</button>
            </div>
          ) : <p className="suave" style={{ margin: 0 }}>Peça a chave Pix pelo WhatsApp.</p>}
          <p className="suave" style={{ margin: 0 }}>Valor: <b className="num">{moeda(r.valorSinal)}</b>. Depois, envie aqui a foto ou o PDF do comprovante.</p>
          <label className={`botao principal ag-enviar${enviando ? ' desativado' : ''}`}>
            {enviando ? 'Enviando…' : 'Enviar comprovante'}
            <input type="file" accept="image/jpeg,image/png,image/webp,application/pdf" hidden disabled={enviando}
              onChange={(e) => { enviarComprovante(e.target.files?.[0]); e.target.value = ''; }} />
          </label>
        </section>
      )}

      <Erro msg={r ? erro : ''} />

      <footer className="oc-rodape">
        {r.status === 'CONFIRMADA' && r.areaCliente && <Link className="botao principal" to={`/meu/${r.areaCliente}`}>Abrir minha área</Link>}
        {(r.status === 'EXPIRADA' || r.status === 'RECUSADA' || r.status === 'CANCELADA') && <Link className="botao principal" to="/agendar">Escolher outro horário</Link>}
        {r.estudioWhatsapp && (
          <a className="botao" href={zap(`Olá! Sou ${r.nome}, fiz uma reserva para ${diaPorExtenso(r.inicio)} às ${hora(r.inicio)}.`)} target="_blank" rel="noreferrer">Falar no WhatsApp</a>
        )}
      </footer>
    </Casca>
  );
}

// ====================================================================== /meu/:token

export function AreaClientePagina() {
  const { token = '' } = useParams();
  const [a, setA] = useState<AreaCliente | null>(null);
  const [erro, setErro] = useState('');
  const [modo, setModo] = useState<'remarcar' | 'cancelar' | null>(null);
  const [novo, setNovo] = useState<string | null>(null);
  const [motivo, setMotivo] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [aviso, setAviso] = useState('');

  useEffect(() => {
    document.title = 'Meu ensaio';
    publico<AreaCliente>(`/publico/cliente/${token}`)
      .then(setA)
      .catch((e) => setErro(e instanceof ErroApi && e.status === 404 ? 'Link não encontrado. Confira o link que você recebeu.' : 'Não foi possível abrir agora.'));
  }, [token]);

  async function agir(acao: 'remarcar' | 'cancelar') {
    setEnviando(true);
    setErro('');
    try {
      const r = await api<AreaCliente>(`/publico/cliente/${token}/${acao}`, {
        token: null, corpo: acao === 'remarcar' ? { inicio: novo } : { motivo: motivo || null },
      });
      setA(r);
      setModo(null);
      setNovo(null);
      setAviso(acao === 'remarcar' ? 'Ensaio remarcado. A nova data já está na minha agenda.' : '');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível concluir. Tente de novo.');
    } finally {
      setEnviando(false);
    }
  }

  if (!a) return <Casca rotulo="Meu ensaio">{erro ? <Erro msg={erro} /> : <div className="esqueleto" style={{ height: 280 }} />}</Casca>;

  const zap = (m: string) => linkWhatsapp(a.estudioWhatsapp, m);
  const cancelado = a.status === 'CANCELADO';
  const aPagar = a.parcelas.filter((p) => !p.paga);

  return (
    <Casca titulo={a.estudioNome} rotulo="Meu ensaio">
      {aviso && <div className="aviso sucesso oc-estado" role="status">{aviso}</div>}
      {cancelado && <div className="aviso oc-estado"><strong>Este ensaio foi cancelado{a.canceladoEm ? ` em ${data(a.canceladoEm)}` : ''}.</strong></div>}

      <section className="oc-bloco">
        <p className="oc-saudacao">Olá, {primeiroNome(a.nome)}!</p>
        <h2 className="chamada" style={{ margin: 0 }}>{a.titulo}</h2>
        {a.data && <p className="ag-escolha" style={{ margin: 0, textDecoration: cancelado ? 'line-through' : undefined, opacity: cancelado ? .6 : 1 }}>{quando(a.data)} · {duracaoTexto(a.duracaoMin)}</p>}
        {a.local && <p className="legenda" style={{ margin: 0 }}>{a.local.toUpperCase()}</p>}
      </section>

      {a.parcelas.length > 0 && (
        <section className="oc-bloco">
          <h2 className="chamada">Pagamentos</h2>
          <dl className="oc-itens">
            {a.parcelas.map((p, i) => (
              <div key={i}>
                <dt>{p.descricao}{p.vencimento && !p.paga && !cancelado ? <span className="suave"> · vence {data(p.vencimento)}</span> : null}</dt>
                <dd className="num">{moeda(p.valor)} <span className={`ag-selo${p.paga ? ' pago' : ''}`}>{p.paga ? 'pago' : cancelado ? 'cancelada' : 'a pagar'}</span></dd>
              </div>
            ))}
          </dl>
          {aPagar.length > 0 && a.chavePix && !cancelado && <p className="suave" style={{ margin: 0 }}>Chave Pix: <code>{a.chavePix}</code></p>}
        </section>
      )}

      {a.galeriaToken && a.galeriaAberta && (
        <div className="oc-acoes"><Link className="botao principal" to={`/g/${a.galeriaToken}`}>Ver minhas fotos</Link></div>
      )}

      {!cancelado && (a.regras.podeRemarcar || a.regras.podeCancelar || a.regras.motivoRemarcar) && (
        <section className="oc-bloco">
          <h2 className="chamada">Mudou algo?</h2>
          {!a.regras.podeRemarcar && a.regras.motivoRemarcar && <p className="suave" style={{ margin: 0 }}>{a.regras.motivoRemarcar}</p>}
          {modo === null && (
            <div className="oc-acoes">
              {a.regras.podeRemarcar && <button className="botao" onClick={() => setModo('remarcar')}>Remarcar</button>}
              {a.regras.podeCancelar && <button className="botao-link oc-recusar" onClick={() => setModo('cancelar')}>Cancelar ensaio</button>}
            </div>
          )}
          {modo === 'remarcar' && (
            <div className="oc-bloco anima-entrada">
              <p className="suave" style={{ margin: 0 }}>Escolha a nova data. Só aparecem horários livres.</p>
              <SeletorHorario valor={novo} aoEscolher={setNovo}
                carregar={(de, ate) => publico(`/publico/cliente/${token}/vagas?de=${de}&ate=${ate}`)} />
              {novo && <p className="ag-escolha" role="status">{quando(novo)}</p>}
              <div className="acoes-linha">
                <button className="botao principal" disabled={!novo || enviando} onClick={() => agir('remarcar')}>{enviando ? 'Remarcando…' : 'Confirmar nova data'}</button>
                <button className="botao" onClick={() => { setModo(null); setNovo(null); }}>Voltar</button>
              </div>
            </div>
          )}
          {modo === 'cancelar' && (
            <div className="oc-confirmar anima-entrada" role="alertdialog" aria-label="Cancelar ensaio">
              <p style={{ margin: 0 }}>{a.regras.textoCancelamento}</p>
              <label className="campo"><span>Motivo (opcional)</span>
                <textarea value={motivo} maxLength={300} onChange={(e) => setMotivo(e.target.value)} /></label>
              <div className="acoes-linha">
                <button className="botao" disabled={enviando} onClick={() => agir('cancelar')}>{enviando ? 'Cancelando…' : 'Sim, cancelar'}</button>
                <button className="botao principal" onClick={() => setModo(null)}>Manter ensaio</button>
              </div>
            </div>
          )}
        </section>
      )}

      <Erro msg={erro} />

      {a.estudioWhatsapp && (
        <footer className="oc-rodape">
          <a className="botao" href={zap(`Olá! Sou ${a.nome}, tenho uma dúvida sobre o meu ensaio.`)} target="_blank" rel="noreferrer">Falar no WhatsApp</a>
        </footer>
      )}
    </Casca>
  );
}
