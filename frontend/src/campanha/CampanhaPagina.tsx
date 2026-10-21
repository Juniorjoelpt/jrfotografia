import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, ErroApi, publico } from '../api';
import { Logo } from '../componentes/Logo';
import { Ambiente } from '../componentes/Movimento';
import { CampoCodigo } from '../agendar/CampoCodigo';
import type { CampanhaPublica, Desconto, ReservaPublica } from '../tipos';
import { diaPorExtenso, duracaoTexto, linkWhatsapp, moeda } from '../util';
import '../orcamento/orcamento.css';
import '../agendar/agendar.css';
import './campanha.css';
import '../componentes/seletor-horario.css';

/** Página da campanha (mini-sessão): capa, o que inclui, horários do dia e reserva com sinal no Pix. */
export function CampanhaPagina() {
  const { slug = '' } = useParams();
  const [busca] = useSearchParams();
  const navegar = useNavigate();
  const [c, setC] = useState<CampanhaPublica | null>(null);
  const [erro, setErro] = useState('');
  const [escolha, setEscolha] = useState<string | null>(null);
  const [desconto, setDesconto] = useState<Desconto | null>(null);
  const [fone, setFone] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    publico<CampanhaPublica>(`/publico/campanhas/${slug}`)
      .then((r) => { setC(r); document.title = `${r.titulo} · ${r.estudioNome}`; })
      .catch((e) => setErro(e instanceof ErroApi && e.status === 404 ? e.message : 'Não foi possível abrir a página agora.'));
  }, [slug]);

  async function reservar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!escolha) return;
    const f = new FormData(e.currentTarget);
    setEnviando(true);
    setErro('');
    try {
      const r = await api<ReservaPublica>(`/publico/campanhas/${slug}/reservas`, {
        token: null,
        corpo: { inicio: escolha, nome: f.get('nome'), telefone: fone, email: f.get('email') || null, mensagem: f.get('mensagem') || null,
          origem: busca.get('origem') ?? 'SITE', consentimento: f.get('consentimento') === 'on', codigo: desconto?.codigo ?? null },
      });
      navegar(`/reserva/${r.token}`);
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível reservar. Tente de novo.');
      if (ex instanceof ErroApi && ex.message.includes('ocupado')) {
        setEscolha(null);
        publico<CampanhaPublica>(`/publico/campanhas/${slug}`).then(setC).catch(() => {});
      }
    } finally {
      setEnviando(false);
    }
  }

  const capa = c?.capa ? `/api/publico/campanhas/${slug}/capa` : null;
  if (!c) {
    return (
      <>
        <Ambiente src={null} />
        <main className="oc-pagina"><div className="oc-cartao vidro">{erro ? <p className="aviso erro" style={{ margin: 0 }}>{erro}</p> : <div className="esqueleto" style={{ height: 320 }} />}</div></main>
      </>
    );
  }

  const total = desconto?.valorFinal ?? c.preco;
  const sinal = Math.round(total * c.sinalPercentual) / 100;
  return (
    <>
      <Ambiente src={capa} />
      <main className="oc-pagina">
        <article className="oc-cartao vidro anima-pagina cp-cartao">
          {capa && <img className="cp-capa" src={capa} alt="" />}
          <header className="oc-topo">
            <Link to="/" style={{ color: 'var(--tinta)' }} aria-label="Ir para o site"><Logo largura={110} titulo={c.estudioNome} /></Link>
            <div className="oc-num"><span className="rotulo">Mini-sessão</span><strong className="num">{moeda(c.preco)}</strong></div>
          </header>
          <section className="oc-bloco">
            <h1 className="cp-titulo">{c.titulo}</h1>
            {c.descricao && <p className="cp-descricao">{c.descricao}</p>}
            <ul className="oc-incluso">
              <li>Sessão de {duracaoTexto(c.duracaoMin)}</li>
              <li>{c.qtdFotos ? `${c.qtdFotos} fotos editadas${c.precoFotoExtra ? ` (extra ${moeda(c.precoFotoExtra)} cada)` : ''}` : 'Todas as fotos editadas'}</li>
              <li>Entrega em até {c.prazoEntregaDias} dias úteis, em galeria online</li>
              <li>Sinal de {moeda(c.sinal)} ({c.sinalPercentual}%) no Pix para garantir o horário</li>
            </ul>
          </section>

          <section className="oc-bloco">
            <h2 className="chamada">Escolha seu horário</h2>
            {c.dias.length === 0
              ? <p className="aviso" style={{ margin: 0 }}>{c.esgotada ? 'Todos os horários foram preenchidos. ' : 'Não há horários abertos. '}
                {c.estudioWhatsapp && <a href={linkWhatsapp(c.estudioWhatsapp, `Oi! Quero entrar na lista de espera da ${c.titulo}.`)} target="_blank" rel="noreferrer">Entrar na lista de espera</a>}</p>
              : c.dias.map((d) => (
                <div key={d.data} className="cp-dia">
                  <strong className="cp-dia-nome">{diaPorExtenso(d.data)}{d.local ? <span className="legenda"> · {d.local.toUpperCase()}</span> : null}</strong>
                  <div className="seletor-horas" role="group" aria-label={`Horários de ${diaPorExtenso(d.data)}`}>
                    {d.horarios.map((h) => {
                      const ini = `${d.data}T${h.slice(0, 5)}:00`;
                      return <button key={h} type="button" className="botao pequeno" aria-pressed={escolha === ini} onClick={() => setEscolha(ini)}>{h.slice(0, 5)}</button>;
                    })}
                  </div>
                </div>
              ))}
          </section>

          {escolha && (
            <form className="oc-bloco grade-form duas anima-entrada" onSubmit={reservar}>
              <h2 className="chamada inteira">Seus dados · {diaPorExtenso(escolha)} às {escolha.slice(11, 16)}</h2>
              <label className="campo inteira"><span>Seu nome</span><input name="nome" required maxLength={120} autoComplete="name" /></label>
              <label className="campo"><span>WhatsApp com DDD</span><input name="telefone" required inputMode="tel" autoComplete="tel" value={fone} onChange={(e) => setFone(e.target.value)} placeholder="(98) 9 0000-0000" /></label>
              <label className="campo"><span>E-mail (opcional)</span><input name="email" type="email" autoComplete="email" /></label>
              <label className="campo inteira"><span>Quem vai participar? (opcional)</span><textarea name="mensagem" maxLength={2000} rows={2} placeholder="Ex.: eu e meus dois filhos" /></label>
              <CampoCodigo campanha={slug} telefone={fone} inicial={busca.get('codigo')} aoMudar={setDesconto} />
              <div className="ag-resumo inteira">
                <div><span>Mini-sessão</span><b className="num">{moeda(c.preco)}</b></div>
                {desconto && <div className="desconto"><span>Desconto ({desconto.codigo})</span><b className="num">−{moeda(desconto.desconto)}</b></div>}
                <div><span>Sinal agora ({c.sinalPercentual}%)</span><b className="num">{moeda(sinal)}</b></div>
                <p className="legenda" style={{ margin: 0 }}>O HORÁRIO FICA SEGURO POR {c.reservaHoras}H ENQUANTO VOCÊ ENVIA O COMPROVANTE DO PIX.</p>
              </div>
              <label className="marcar inteira"><input type="checkbox" name="consentimento" required /> Autorizo o uso destes dados só para o agendamento e o contato sobre a sessão.</label>
              {erro && <p className="aviso erro inteira" role="alert" style={{ margin: 0 }}>{erro}</p>}
              <button className="botao principal inteira" disabled={enviando}>{enviando ? 'Reservando…' : 'Reservar meu horário'}</button>
            </form>
          )}
          {!escolha && erro && <p className="aviso erro" role="alert" style={{ margin: 0 }}>{erro}</p>}
        </article>
        <p className="legenda oc-assina">{c.estudioNome.toUpperCase()}</p>
      </main>
    </>
  );
}
