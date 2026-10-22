import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ErroApi } from '../api';
import type { ConfigIa, IdeiaPost, OpcoesConteudo, ResultadoIdeias, TipoIdeia } from '../tipos';
import { copiar, dataHora } from '../util';
import { Erro, useDados } from './comum';

const PEDIDOS: { tipo: TipoIdeia; nome: string; ajuda: string }[] = [
  { tipo: 'SEMANA', nome: 'Ideias para a semana', ajuda: '7 posts, um por dia, com a agenda, as mini-sessões e as datas comemorativas.' },
  { tipo: 'TRABALHO', nome: 'Mostrar um ensaio', ajuda: 'Posts com um ensaio entregue. Só aparecem ensaios cuja cliente autorizou o uso de imagem no contrato.' },
  { tipo: 'CAMPANHA', nome: 'Divulgar mini-sessão', ajuda: 'Lançamento, carrossel, stories e últimos horários.' },
  { tipo: 'DEPOIMENTO', nome: 'Publicar depoimento', ajuda: 'Depoimentos da pesquisa que a cliente autorizou publicar.' },
  { tipo: 'LIVRE', nome: 'Pedido livre', ajuda: 'Peça o que quiser: legenda, ideia de Reels, texto para o Dia das Crianças…' },
];

/** Marketing › Conteúdo: o assistente de IA sugere posts para as redes com o que acontece no estúdio. */
export function ConteudoIA() {
  const { dados: config } = useDados<ConfigIa>('/estudio/ia');
  const { dados: opcoes } = useDados<OpcoesConteudo>('/conteudo/opcoes');
  const { dados: historico, recarregar } = useDados<ResultadoIdeias[]>('/conteudo/ideias');
  const [tipo, setTipo] = useState<TipoIdeia>('SEMANA');
  const [referencia, setReferencia] = useState('');
  const [pedido, setPedido] = useState('');
  const [gerando, setGerando] = useState(false);
  const [falha, setFalha] = useState('');
  const [aberto, setAberto] = useState<ResultadoIdeias | null>(null);

  const lista = tipo === 'TRABALHO' ? opcoes?.trabalhos : tipo === 'CAMPANHA' ? opcoes?.campanhas : tipo === 'DEPOIMENTO' ? opcoes?.depoimentos : null;
  const vazioLista = lista && lista.length === 0;
  const pronto = !!config?.ativo && (!lista || referencia !== '') && (tipo !== 'LIVRE' || pedido.trim() !== '');

  async function gerar() {
    setFalha(''); setGerando(true);
    try {
      const r = await api<ResultadoIdeias>('/conteudo/ideias', {
        corpo: { tipo, referencia: referencia ? Number(referencia) : null, pedido: pedido.trim() || null },
      });
      setAberto(r);
      recarregar();
      setTimeout(() => document.getElementById('ct-resultado')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível falar com o assistente.');
    } finally {
      setGerando(false);
    }
  }

  async function apagar(r: ResultadoIdeias) {
    await api(`/conteudo/ideias/${r.id}`, { metodo: 'DELETE' });
    if (aberto?.id === r.id) setAberto(null);
    recarregar();
  }

  return (
    <section className="secao">
      {config && !config.ativo && (
        <p className="aviso" role="status">
          O assistente está desligado. Escolha onde o modelo de IA roda e ligue em{' '}
          <Link to="/painel/ajustes#assistente">Ajustes › Assistente de conteúdo</Link>.
        </p>
      )}

      <div className="painel-caixa ct-pedir">
        <div className="ct-tipos" role="radiogroup" aria-label="O que você quer">
          {PEDIDOS.map((p) => (
            <button key={p.tipo} type="button" role="radio" aria-checked={tipo === p.tipo} className={`ct-tipo${tipo === p.tipo ? ' ativo' : ''}`}
              onClick={() => { setTipo(p.tipo); setReferencia(''); setFalha(''); }}>
              <strong>{p.nome}</strong><span>{p.ajuda}</span>
            </button>
          ))}
        </div>

        {lista && (
          vazioLista
            ? <p className="suave" style={{ margin: 0 }}>
                {tipo === 'TRABALHO' && 'Nenhum ensaio entregue com uso de imagem autorizado ainda. A autorização é marcada pela cliente ao assinar o contrato.'}
                {tipo === 'CAMPANHA' && <>Nenhuma mini-sessão ativa. Crie uma em <b>Mini-sessões</b>.</>}
                {tipo === 'DEPOIMENTO' && 'Nenhum depoimento com autorização para publicar ainda. Ele vem da pesquisa enviada depois da entrega.'}
              </p>
            : <label className="campo"><span>{tipo === 'TRABALHO' ? 'Ensaio' : tipo === 'CAMPANHA' ? 'Mini-sessão' : 'Depoimento'}</span>
                <select id="ct-referencia" value={referencia} onChange={(e) => setReferencia(e.target.value)}>
                  <option value="">Escolha…</option>
                  {lista.map((o) => <option key={o.id} value={o.id}>{o.rotulo}{o.detalhe ? ` · ${o.detalhe}` : ''}</option>)}
                </select>
              </label>
        )}

        <label className="campo"><span>{tipo === 'LIVRE' ? 'O que você quer' : 'Quer orientar? (opcional)'}</span>
          <textarea id="ct-pedido" rows={tipo === 'LIVRE' ? 4 : 2} maxLength={600} value={pedido} onChange={(e) => setPedido(e.target.value)}
            placeholder={tipo === 'LIVRE' ? 'Ex.: 3 ideias de Reels para mostrar os bastidores de um ensaio newborn' : 'Ex.: destacar a luz do fim de tarde; tom mais divertido; foco em gestantes'} />
        </label>

        <Erro msg={falha} />
        <div className="acoes-linha">
          <button id="ct-gerar" className="botao principal" disabled={!pronto || gerando} onClick={gerar}>{gerando ? 'Escrevendo…' : 'Pedir ideias'}</button>
          {gerando && <span className="suave" role="status">O assistente está escrevendo. Num modelo na própria VPS, sem placa de vídeo, pode levar até 2 minutos.</span>}
        </div>
        <p className="legenda" style={{ margin: 0 }}>
          O ASSISTENTE RECEBE OS DADOS DO ESTÚDIO E DOS ENSAIOS, MAS NUNCA NOME COMPLETO, TELEFONE, E-MAIL OU CPF DE CLIENTES. CONFIRA SEMPRE ANTES DE POSTAR.
        </p>
      </div>

      {aberto && <Resultado r={aberto} />}

      {historico && historico.length > 0 && (
        <>
          <h2 className="subtitulo">Ideias anteriores</h2>
          <div className="linhas">
            {historico.map((h) => (
              <div key={h.id} className="linha-item">
                <span className="principal">{h.titulo}</span>
                <span className="lado legenda">{h.ideias.length} IDEIA{h.ideias.length === 1 ? '' : 'S'}</span>
                <span className="legenda">{dataHora(h.criadoEm).toUpperCase()}{h.modelo ? ` · ${h.modelo.toUpperCase()}` : ''}</span>
                <span className="lado acoes-linha">
                  <button className="botao pequeno" onClick={() => { setAberto(h); setTimeout(() => document.getElementById('ct-resultado')?.scrollIntoView({ behavior: 'smooth' }), 50); }}>Abrir</button>
                  <button className="botao pequeno" onClick={() => apagar(h)}>Apagar</button>
                </span>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function Resultado({ r }: { r: ResultadoIdeias }) {
  return (
    <div id="ct-resultado" className="ct-resultado">
      <h2 className="subtitulo">{r.titulo}</h2>
      {r.pedido && <p className="suave" style={{ marginTop: 0 }}>Você pediu: “{r.pedido}”</p>}
      <div className="ct-ideias">
        {r.ideias.map((i, n) => <CartaoIdeia key={n} i={i} />)}
      </div>
      {r.usado.length > 0 && (
        <details className="ct-usado">
          <summary>O que o assistente usou</summary>
          <ul>{r.usado.map((u) => <li key={u}>{u}</li>)}</ul>
        </details>
      )}
    </div>
  );
}

function CartaoIdeia({ i }: { i: IdeiaPost }) {
  const [copiado, setCopiado] = useState(false);
  const texto = [i.legenda, i.hashtags.join(' ')].filter(Boolean).join('\n\n');
  return (
    <article className="painel-caixa ct-ideia">
      <div className="ct-ideia-topo">
        {i.dia && <span className="legenda">{i.dia.toUpperCase()}</span>}
        {i.formato && <span className="etiqueta">{i.formato}</span>}
      </div>
      {i.titulo && <h3>{i.titulo}</h3>}
      {i.roteiro && <div className="ct-bloco"><span className="legenda">O QUE MOSTRAR</span><p>{i.roteiro}</p></div>}
      {i.legenda && <div className="ct-bloco"><span className="legenda">LEGENDA</span><p>{i.legenda}</p></div>}
      {i.hashtags.length > 0 && <p className="ct-tags">{i.hashtags.join(' ')}</p>}
      {texto && (
        <button className="botao pequeno" onClick={async () => { setCopiado(await copiar(texto)); setTimeout(() => setCopiado(false), 2000); }}>
          {copiado ? 'Copiado!' : 'Copiar legenda'}
        </button>
      )}
    </article>
  );
}
