import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ErroApi, publico } from '../api';
import { Logo, Monograma } from '../componentes/Logo';
import { Visualizador } from '../componentes/Visualizador';
import { Imagem, movimentoReduzido, useRevelar } from '../componentes/Movimento';

/**
 * Rola até a seção sem mudar o endereço (#...). Alguns navegadores de celular reprocessam a página
 * quando o endereço muda; assim o "Pedir orçamento" só desliza até o formulário.
 */
const irPara = (id: string) => (e: React.MouseEvent) => {
  e.preventDefault();
  const alvo = document.getElementById(id);
  if (!alvo) return;
  // Mostra de imediato as seções do caminho, para não "piscarem" durante a rolagem.
  document.querySelectorAll('[data-revelar]').forEach((el) => el.classList.add('visivel'));
  alvo.scrollIntoView({ behavior: movimentoReduzido() ? 'auto' : 'smooth', block: 'start' });
};
import type { Estudio, Pacote, PortfolioFoto } from '../tipos';
import { linkWhatsapp, moeda, telefone } from '../util';
import './site.css';

const img = (f: PortfolioFoto, mini = false) => `/api/publico/portfolio/${f.id}/${mini ? 'miniatura' : 'imagem'}`;

export function Site() {
  const [estudio, setEstudio] = useState<Estudio | null>(null);
  const [pacotes, setPacotes] = useState<Pacote[]>([]);
  const [fotos, setFotos] = useState<PortfolioFoto[]>([]);
  const [aberta, setAberta] = useState<number | null>(null);
  const [pacoteEscolhido, setPacoteEscolhido] = useState<number | ''>('');

  useEffect(() => {
    // Se o servidor acabou de reiniciar, a primeira chamada pode falhar: tenta de novo antes de desistir.
    publico<Estudio>('/publico/estudio').then(setEstudio).catch(() => {});
    publico<Pacote[]>('/publico/pacotes').then(setPacotes).catch(() => {});
    publico<PortfolioFoto[]>('/publico/portfolio').then(setFotos).catch(() => {});
  }, []);

  useRevelar([fotos.length, pacotes.length, estudio]);
  const destaque = useMemo(() => fotos.find((f) => f.destaque) ?? fotos[0], [fotos]);
  const copias = fotos.filter((f) => f !== destaque).slice(0, 3);

  const escolherPacote = (id: number) => {
    setPacoteEscolhido(id);
    document.querySelectorAll('[data-revelar]').forEach((el) => el.classList.add('visivel'));
    document.getElementById('orcamento')?.scrollIntoView({ behavior: movimentoReduzido() ? 'auto' : 'smooth', block: 'start' });
  };

  return (
    <>
      <header className="site-topo">
        <div className="dentro">
          <a href="#inicio" onClick={irPara('inicio')} aria-label="JR Fotografia, início" style={{ color: 'var(--tinta)' }}><Monograma tamanho={40} /></a>
          <nav aria-label="Seções">
            <a href="#portfolio" onClick={irPara('portfolio')}>Portfólio</a>
            <a href="#sobre" onClick={irPara('sobre')}>Sobre</a>
            <a href="#pacotes" onClick={irPara('pacotes')}>Pacotes</a>
            <a href="#orcamento" onClick={irPara('orcamento')}>Contato</a>
          </nav>
          <a className="botao principal pequeno" href="#orcamento" onClick={irPara('orcamento')}>Pedir orçamento</a>
        </div>
      </header>

      <main id="inicio">
        <section className="abertura">
          <div className="painel-texto textura">
            <Logo largura={150} />
            <h1 className="display">Olá!</h1>
            <p>Seja bem-vindo. Ensaios femininos, gestante, família, infantil e eventos{estudio?.cidade ? ` em ${estudio.cidade}` : ''}, com edição completa e entrega digital.</p>
            <div className="acoes">
              <a className="botao principal" href="#orcamento" onClick={irPara('orcamento')}>Pedir orçamento</a>
              <a className="botao" href="#pacotes" onClick={irPara('pacotes')}>Ver pacotes</a>
            </div>
          </div>
          <div className="foto">
            {destaque
              ? <Imagem src={img(destaque)} alt="Ensaio feminino da JR Fotografia" className="capa" />
              : <div className="fantasma"><Monograma tamanho={520} titulo="" /></div>}
          </div>
        </section>

        {fotos.length > 0 && (
          <section id="portfolio" className="portfolio" aria-label="Portfólio">
            <div className="faixa" aria-hidden="true">Portfólio</div>
            <div className="mosaico">
              {fotos.map((f, i) => (
                <button key={f.id} onClick={() => setAberta(i)} aria-label={`Ampliar foto ${i + 1}`} data-revelar style={{ '--i': i % 3 } as React.CSSProperties}>
                  <Imagem src={img(f, true)} alt={f.titulo ?? `Ensaio ${f.categoria}`} loading="lazy"
                    width={f.largura} height={f.altura} />
                </button>
              ))}
            </div>
          </section>
        )}

        <section id="sobre" className="dentro sobre">
          <div style={{ display: 'grid', gap: 'var(--espaco-4)' }} data-revelar>
            <span className="rotulo">Sobre</span>
            <h2 className="titulo">Joel Júnior, fotógrafo</h2>
            <p>{estudio?.apresentacao}</p>
          </div>
          {copias.length > 0 && (
            <div className="copias" aria-hidden="true" data-revelar style={{ '--i': 2 } as React.CSSProperties}>
              {copias.map((f) => <figure key={f.id}><img src={img(f, true)} alt="" loading="lazy" /></figure>)}
            </div>
          )}
        </section>

        <section id="pacotes" className="dentro pacotes">
          <div className="cabeca-secao" data-revelar>
            <span className="rotulo">Pacotes</span>
            <h2 className="titulo">Escolha o seu ensaio</h2>
          </div>
          <div className="lista-pacotes">
            {pacotes.map((p, i) => (
              <article key={p.id} className="pacote" data-revelar style={{ '--i': i % 2 } as React.CSSProperties}>
                <div className="texto">
                  <h3 className="titulo">Fotografia / {p.nome}</h3>
                  <ul>{p.itens.map((it) => <li key={it}>{it}</li>)}</ul>
                  <div style={{ display: 'grid', gap: 'var(--espaco-2)' }}>
                    <p className="chamada">Material entregue:</p>
                    <span>{p.materialEntregue}</span>
                  </div>
                  <div style={{ display: 'grid', gap: 'var(--espaco-1)' }}>
                    <p className="chamada">Investimento:</p>
                    <span className="preco">{moeda(p.preco)}</span>
                    <span className="legenda">
                      ENTREGA: {p.prazoEntregaDias} DIAS ÚTEIS
                      {p.precoFotoExtra ? ` · FOTO EXTRA ${moeda(p.precoFotoExtra)}` : ''}
                    </span>
                  </div>
                  <button className="botao" onClick={() => escolherPacote(p.id)}>Quero este pacote</button>
                </div>
                <div className="foto" style={fotos.length ? { backgroundImage: `url(${img(fotos[(i + 1) % fotos.length], true)})` } : undefined} />
              </article>
            ))}
          </div>
        </section>

        <section id="orcamento" className="dentro orcamento">
          <div style={{ display: 'grid', gap: 'var(--espaco-4)', alignContent: 'start' }} data-revelar>
            <span className="rotulo">Contato</span>
            <h2 className="titulo">Vamos marcar o seu ensaio?</h2>
            <p className="suave" style={{ margin: 0 }}>Conte o que você imagina. Eu respondo pelo WhatsApp com datas e condições.</p>
            {estudio?.whatsapp && (
              <a className="botao" href={linkWhatsapp(estudio.whatsapp, 'Olá, Joel! Vim pelo site e quero saber mais sobre os ensaios.')}
                target="_blank" rel="noreferrer">Chamar no WhatsApp · {telefone(estudio.whatsapp)}</a>
            )}
          </div>
          <FormOrcamento pacotes={pacotes} pacoteId={pacoteEscolhido} setPacoteId={setPacoteEscolhido} whatsapp={estudio?.whatsapp} />
        </section>
      </main>

      <footer className="rodape">
        <div className="dentro">
          <Logo largura={110} />
          <div className="legenda" style={{ display: 'grid', gap: 4 }}>
            {estudio?.cidade && <span>{estudio.cidade.toUpperCase()}</span>}
            {estudio?.instagram && <a href={`https://instagram.com/${estudio.instagram}`} target="_blank" rel="noreferrer">@{estudio.instagram}</a>}
          </div>
          <Link to="/painel" className="legenda">Área do fotógrafo</Link>
        </div>
      </footer>

      {aberta !== null && fotos[aberta] && (
        <Visualizador src={img(fotos[aberta])} alt={`Foto ${aberta + 1} de ${fotos.length}`}
          aoFechar={() => setAberta(null)}
          aoAnterior={aberta > 0 ? () => setAberta(aberta - 1) : undefined}
          aoProxima={aberta < fotos.length - 1 ? () => setAberta(aberta + 1) : undefined} />
      )}
    </>
  );
}

function FormOrcamento({ pacotes, pacoteId, setPacoteId, whatsapp }: {
  pacotes: Pacote[]; pacoteId: number | ''; setPacoteId: (v: number | '') => void; whatsapp?: string;
}) {
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState('');
  const [enviado, setEnviado] = useState<string | null>(null);

  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setEnviando(true);
    setErro('');
    try {
      await api('/publico/orcamento', {
        token: null,
        corpo: {
          nome: f.get('nome'), telefone: f.get('telefone'), email: f.get('email') || null,
          tipoTrabalho: f.get('tipo') || null, pacoteId: pacoteId || null,
          dataDesejada: f.get('data') || null, mensagem: f.get('mensagem') || null,
          origem: f.get('origem') || 'SITE', consentimento: f.get('consentimento') === 'on',
        },
      });
      setEnviado(String(f.get('nome')));
    } catch (er) {
      setErro(er instanceof ErroApi ? er.message : 'Não foi possível enviar. Tente pelo WhatsApp.');
    } finally {
      setEnviando(false);
    }
  }

  if (enviado) {
    return (
      <div className="painel-caixa" style={{ display: 'grid', gap: 'var(--espaco-4)', alignContent: 'start' }}>
        <h3 className="subtitulo">Pedido recebido, {enviado.split(' ')[0]}.</h3>
        <p className="suave" style={{ margin: 0 }}>Respondo pelo WhatsApp em breve. Se preferir adiantar a conversa, me chame agora.</p>
        {whatsapp && <a className="botao principal" href={linkWhatsapp(whatsapp, 'Olá, Joel! Acabei de pedir um orçamento pelo site.')} target="_blank" rel="noreferrer">Abrir WhatsApp</a>}
      </div>
    );
  }

  return (
    <form className="painel-caixa grade-form duas" onSubmit={enviar}>
      <label className="campo inteira"><span>Seu nome</span><input id="orc-nome" name="nome" required maxLength={120} autoComplete="name" /></label>
      <label className="campo"><span>WhatsApp com DDD</span><input id="orc-tel" name="telefone" required inputMode="tel" autoComplete="tel" placeholder="(98) 9 0000-0000" /></label>
      <label className="campo"><span>E-mail (opcional)</span><input id="orc-email" name="email" type="email" autoComplete="email" /></label>
      <label className="campo"><span>Tipo de ensaio</span>
        <select id="orc-tipo" name="tipo" defaultValue="Ensaio feminino">
          {['Ensaio feminino', 'Gestante', 'Família', 'Infantil / smash the cake', 'Newborn', '15 anos', 'Casal', 'Casamento', 'Formatura', 'Corporativo', 'Evento', 'Outro'].map((t) => <option key={t}>{t}</option>)}
        </select>
      </label>
      <label className="campo"><span>Pacote</span>
        <select id="orc-pacote" value={pacoteId} onChange={(e) => setPacoteId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Ainda não sei</option>
          {pacotes.map((p) => <option key={p.id} value={p.id}>{p.nome} · {moeda(p.preco)}</option>)}
        </select>
      </label>
      <label className="campo"><span>Data que você imagina</span><input id="orc-data" name="data" type="date" /></label>
      <label className="campo"><span>Como conheceu meu trabalho?</span>
        <select id="orc-origem" name="origem" defaultValue="INSTAGRAM">
          <option value="INSTAGRAM">Instagram</option><option value="INDICACAO">Indicação</option>
          <option value="WHATSAPP">WhatsApp</option><option value="FEIRA">Feira ou evento</option><option value="SITE">Google ou site</option>
        </select>
      </label>
      <label className="campo inteira"><span>Mensagem</span><textarea id="orc-msg" name="mensagem" maxLength={2000} placeholder="Ideia de ensaio, local, quantas pessoas..." /></label>
      <label className="marcar inteira"><input id="orc-lgpd" type="checkbox" name="consentimento" required />
        Autorizo o uso destes dados só para responder ao meu pedido de orçamento.</label>
      {erro && <p className="aviso erro inteira" role="alert" style={{ margin: 0 }}>{erro}</p>}
      <button className="botao principal inteira" disabled={enviando}>{enviando ? 'Enviando…' : 'Enviar pedido'}</button>
    </form>
  );
}
