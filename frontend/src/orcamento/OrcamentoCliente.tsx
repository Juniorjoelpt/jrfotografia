import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, ErroApi, publico } from '../api';
import { Logo } from '../componentes/Logo';
import { Ambiente } from '../componentes/Movimento';
import type { OrcamentoPublico, PortfolioFoto } from '../tipos';
import { data, dataHora, linkWhatsapp, moeda, primeiroNome } from '../util';
import './orcamento.css';

/** Página que o cliente abre pelo link do WhatsApp: vê o orçamento e aceita ou recusa. */
export function OrcamentoCliente() {
  const { token = '' } = useParams();
  const [o, setO] = useState<OrcamentoPublico | null>(null);
  const [erro, setErro] = useState('');
  const [fundo, setFundo] = useState<string | null>(null);
  const [confirmar, setConfirmar] = useState<'aceite' | 'recusa' | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [recemAceito, setRecemAceito] = useState(false);

  useEffect(() => {
    publico<OrcamentoPublico>(`/publico/orcamentos/${token}`)
      .then((r) => { setO(r); document.title = `Orçamento nº ${r.numero} · ${r.estudioNome}`; })
      .catch((e) => setErro(e instanceof ErroApi && e.status === 404 ? 'Orçamento não encontrado. Confira o link que você recebeu.' : 'Não foi possível abrir o orçamento agora.'));
    api<PortfolioFoto[]>('/publico/portfolio', { token: null })
      .then((l) => { const f = l.find((x) => x.destaque) ?? l[0]; if (f) setFundo(`/api/publico/portfolio/${f.id}/imagem`); })
      .catch(() => {});
  }, [token]);

  async function responder(acao: 'aceite' | 'recusa') {
    setEnviando(true);
    setErro('');
    try {
      const r = await api<OrcamentoPublico>(`/publico/orcamentos/${token}/${acao}`, { metodo: 'POST', token: null });
      setO(r);
      setRecemAceito(acao === 'aceite');
      setConfirmar(null);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível registrar. Tente de novo.');
    } finally {
      setEnviando(false);
    }
  }

  if (!o) {
    return (
      <>
        <Ambiente src={fundo} />
        <main className="oc-pagina"><div className="oc-cartao vidro">{erro ? <p className="aviso erro" style={{ margin: 0 }}>{erro}</p> : <div className="esqueleto" style={{ height: 280 }} />}</div></main>
      </>
    );
  }

  const aberto = o.status === 'ENVIADO' || o.status === 'RASCUNHO';
  const zap = (msg: string) => linkWhatsapp(o.estudioWhatsapp, msg);
  const pdf = `/api/publico/orcamentos/${token}/pdf`;

  return (
    <>
      <Ambiente src={fundo} />
      <main className="oc-pagina">
        <article className="oc-cartao vidro anima-pagina">
          <header className="oc-topo">
            <span style={{ color: 'var(--tinta)' }}><Logo largura={120} titulo={o.estudioNome} /></span>
            <div className="oc-num"><span className="rotulo">Orçamento</span><strong>Nº {o.numero}</strong></div>
          </header>

          {o.status === 'ACEITO' && (
            <div className="aviso sucesso oc-estado" role="status">
              <strong>{recemAceito ? 'Pronto, data reservada!' : 'Orçamento aceito.'}</strong>
              <span>{recemAceito ? 'Obrigado pela confiança. ' : ''}O próximo passo é o pagamento {o.condicoes.startsWith('sinal') ? 'do sinal' : 'combinado'}; o fotógrafo vai te chamar no WhatsApp com os dados.</span>
            </div>
          )}
          {o.status === 'RECUSADO' && <div className="aviso oc-estado">Você recusou este orçamento. Se mudar de ideia, é só chamar no WhatsApp.</div>}
          {o.status === 'EXPIRADO' && <div className="aviso oc-estado">Este orçamento venceu em {data(o.validade)}. Chame no WhatsApp para receber um atualizado.</div>}

          <section className="oc-bloco">
            <p className="oc-saudacao">Olá, {primeiroNome(o.nomeCliente)}! Este é o orçamento {o.tipo ? <>para <b>{o.tipo.toLowerCase()}</b></> : 'do seu ensaio'}.</p>
            {(o.dataPrevista || o.localPrevisto) && (
              <p className="legenda" style={{ margin: 0 }}>{[o.dataPrevista && dataHora(o.dataPrevista).toUpperCase(), o.localPrevisto?.toUpperCase()].filter(Boolean).join(' · ')}</p>
            )}
          </section>

          {o.incluso.length > 0 && (
            <section className="oc-bloco">
              <h2 className="chamada">Incluso no pacote {o.pacoteNome}</h2>
              <ul className="oc-incluso">{o.incluso.map((i) => <li key={i}>{i}</li>)}</ul>
              <p className="legenda" style={{ margin: 0 }}>
                {o.prazoEntregaDias ? `ENTREGA EM ${o.prazoEntregaDias} DIAS ÚTEIS` : ''}{o.precoFotoExtra ? ` · FOTO EXTRA ${moeda(o.precoFotoExtra)}` : ''}
              </p>
            </section>
          )}

          <section className="oc-bloco">
            <h2 className="chamada">Investimento</h2>
            <dl className="oc-itens">
              {o.itens.map((i, n) => (
                <div key={n}><dt>{i.descricao}{i.quantidade > 1 ? <span className="suave"> × {i.quantidade}</span> : null}</dt><dd className="num">{moeda(i.total)}</dd></div>
              ))}
              {o.desconto > 0 && <div><dt>Desconto</dt><dd className="num">- {moeda(o.desconto)}</dd></div>}
            </dl>
            <div className="oc-total"><span className="rotulo">Total</span><strong className="num">{moeda(o.valorTotal)}</strong></div>
            <p className="oc-condicoes">Pagamento: {o.condicoes.replace(/R\$ /g, "R$\u00a0")}.</p>
            {o.observacoes && <p className="suave" style={{ margin: 0, whiteSpace: 'pre-line' }}>{o.observacoes}</p>}
            {aberto && <p className="legenda" style={{ margin: 0 }}>VÁLIDO ATÉ {data(o.validade)}</p>}
          </section>

          <Erro msg={erro} />

          {aberto && (confirmar ? (
            <div className="oc-confirmar anima-entrada" role="alertdialog" aria-label="Confirmação">
              {confirmar === 'aceite'
                ? <p style={{ margin: 0 }}>Ao aceitar, sua data fica pré-reservada e o contrato é gerado com estes valores. Confirma?</p>
                : <p style={{ margin: 0 }}>Quer mesmo recusar este orçamento?</p>}
              <div className="acoes-linha">
                <button className={`botao ${confirmar === 'aceite' ? 'principal' : ''}`} disabled={enviando} onClick={() => responder(confirmar)}>
                  {enviando ? 'Enviando…' : confirmar === 'aceite' ? 'Sim, aceito' : 'Sim, recusar'}
                </button>
                <button className="botao" onClick={() => setConfirmar(null)}>Voltar</button>
              </div>
            </div>
          ) : (
            <div className="oc-acoes">
              <button className="botao principal" onClick={() => setConfirmar('aceite')}>Aceitar orçamento</button>
              {o.estudioWhatsapp && <a className="botao" href={zap(`Olá! Tenho uma dúvida sobre o orçamento nº ${o.numero}.`)} target="_blank" rel="noreferrer">Tirar dúvida</a>}
              <button className="botao-link oc-recusar" onClick={() => setConfirmar('recusa')}>Recusar</button>
            </div>
          ))}

          <footer className="oc-rodape">
            {o.contratoDisponivel && <a className="botao" href={`/api/publico/orcamentos/${token}/contrato.pdf`} target="_blank" rel="noreferrer">Baixar contrato</a>}
            <a className="botao" href={pdf} target="_blank" rel="noreferrer">Baixar PDF</a>
            {o.status === 'ACEITO' && o.estudioWhatsapp && (
              <a className="botao" href={zap(`Olá! Aceitei o orçamento nº ${o.numero}. Como faço o pagamento?`)} target="_blank" rel="noreferrer">Falar no WhatsApp</a>
            )}
          </footer>
        </article>
        <p className="legenda oc-assina">{o.estudioNome.toUpperCase()}{o.estudioCidade ? ` · ${o.estudioCidade.toUpperCase()}` : ''}</p>
      </main>
    </>
  );
}

function Erro({ msg }: { msg: string }) {
  return msg ? <p className="aviso erro" role="alert" style={{ margin: 0 }}>{msg}</p> : null;
}
