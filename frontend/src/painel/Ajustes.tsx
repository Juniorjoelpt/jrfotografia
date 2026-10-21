import { useRef, useState, type FormEvent } from 'react';
import { api, ErroApi } from '../api';
import type { ContratoModelo, Estudio, ItemAvulso, Pacote, PortfolioFoto } from '../tipos';
import { DURACOES, duracaoTexto, moeda, numeroBr } from '../util';
import { AgendaOnlineAjustes } from './AjustesAgenda';
import { Erro, useDados } from './comum';

export function Ajustes() {
  const [aba, setAba] = useState<'estudio' | 'portfolio' | 'pacotes' | 'itens' | 'contrato' | 'agenda'>('estudio');
  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Ajustes</span><h1 className="titulo">Ajustes do estúdio</h1></div></div>
      <div className="abas" role="group" aria-label="Seções">
        <button className="botao pequeno" aria-pressed={aba === 'estudio'} onClick={() => setAba('estudio')}>Dados e contato</button>
        <button className="botao pequeno" aria-pressed={aba === 'portfolio'} onClick={() => setAba('portfolio')}>Portfólio do site</button>
        <button className="botao pequeno" aria-pressed={aba === 'pacotes'} onClick={() => setAba('pacotes')}>Pacotes</button>
        <button className="botao pequeno" aria-pressed={aba === 'itens'} onClick={() => setAba('itens')}>Itens avulsos</button>
        <button className="botao pequeno" aria-pressed={aba === 'contrato'} onClick={() => setAba('contrato')}>Contrato</button>
        <button className="botao pequeno" aria-pressed={aba === 'agenda'} onClick={() => setAba('agenda')}>Agenda online</button>
      </div>
      {aba === 'estudio' && <DadosEstudio />}
      {aba === 'portfolio' && <Portfolio />}
      {aba === 'pacotes' && <Pacotes />}
      {aba === 'itens' && <ItensAvulsos />}
      {aba === 'contrato' && <ModeloContrato />}
      {aba === 'agenda' && <AgendaOnlineAjustes />}
    </>
  );
}

function DadosEstudio() {
  const { dados: e, erro: erroCarga } = useDados<Estudio>('/estudio');
  const [erro, setErro] = useState('');
  const [salvo, setSalvo] = useState(false);
  if (!e) return <Erro msg={erroCarga} />;

  async function salvar(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    const f = new FormData(ev.currentTarget);
    setErro('');
    setSalvo(false);
    try {
      await api('/estudio', { metodo: 'PUT', corpo: Object.fromEntries(f.entries()) });
      setSalvo(true);
    } catch (er) {
      setErro(er instanceof ErroApi ? er.message : 'Não foi possível salvar.');
    }
  }

  return (
    <form className="painel-caixa grade-form duas" onSubmit={salvar}>
      <label className="campo"><span>Nome do estúdio</span><input id="es-nome" name="nome" required defaultValue={e.nome} /></label>
      <label className="campo"><span>Cidade</span><input id="es-cidade" name="cidade" defaultValue={e.cidade} /></label>
      <label className="campo"><span>WhatsApp do estúdio</span><input id="es-zap" name="whatsapp" inputMode="tel" defaultValue={e.whatsapp} placeholder="(98) 9 0000-0000" /></label>
      <label className="campo"><span>Instagram</span><input id="es-insta" name="instagram" defaultValue={e.instagram} placeholder="@jrfotografia" /></label>
      <label className="campo"><span>E-mail</span><input id="es-email" name="email" type="email" defaultValue={e.email} /></label>
      <label className="campo"><span>Chave Pix (aleatória ou e-mail)</span><input id="es-pix" name="chavePix" defaultValue={e.chavePix} /></label>
      <label className="campo inteira"><span>Apresentação no site</span><textarea id="es-apres" name="apresentacao" rows={5} defaultValue={e.apresentacao} /></label>
      <p className="legenda inteira" style={{ margin: 0 }}>PREFIRA UMA CHAVE PIX ALEATÓRIA OU DE E-MAIL: ELA VAI NAS MENSAGENS DE COBRANÇA E NÃO EXPÕE O SEU CPF.</p>
      <Erro msg={erro} />
      {salvo && <p className="aviso sucesso inteira" style={{ margin: 0 }}>Dados salvos.</p>}
      <button className="botao principal inteira">Salvar</button>
    </form>
  );
}

function Portfolio() {
  const { dados, erro, recarregar } = useDados<PortfolioFoto[]>('/portfolio');
  const [ocupado, setOcupado] = useState('');
  const [falha, setFalha] = useState('');
  const ref = useRef<HTMLInputElement>(null);

  async function enviar(arquivos: FileList | null) {
    if (!arquivos?.length) return;
    const lista = Array.from(arquivos);
    setFalha('');
    try {
      for (let i = 0; i < lista.length; i += 6) {
        setOcupado(`Enviando ${Math.min(i + 6, lista.length)} de ${lista.length}…`);
        const fd = new FormData();
        lista.slice(i, i + 6).forEach((a) => fd.append('arquivos', a));
        fd.append('categoria', 'feminino');
        await api('/portfolio', { arquivos: fd });
      }
    } catch (e) {
      setFalha(e instanceof ErroApi ? e.message : 'Falha no envio.');
    } finally {
      setOcupado('');
      recarregar();
    }
  }

  const destacar = async (f: PortfolioFoto) => {
    await Promise.all((dados ?? []).filter((x) => x.destaque && x.id !== f.id)
      .map((x) => api(`/portfolio/${x.id}`, { metodo: 'PUT', corpo: { destaque: false } })));
    await api(`/portfolio/${f.id}`, { metodo: 'PUT', corpo: { destaque: true } });
    recarregar();
  };
  const apagar = async (f: PortfolioFoto) => { await api(`/portfolio/${f.id}`, { metodo: 'DELETE' }); recarregar(); };

  return (
    <section className="secao">
      <div className="envio">
        <button className="botao" disabled={!!ocupado} onClick={() => ref.current?.click()}>{ocupado || 'Adicionar fotos de ensaio feminino'}</button>
        <span className="legenda">AS FOTOS DO PORTFÓLIO APARECEM NO SITE SEM MARCA D'ÁGUA. A FOTO EM DESTAQUE ABRE A PÁGINA.</span>
        <input ref={ref} type="file" accept="image/jpeg,image/png" multiple hidden onChange={(e) => { enviar(e.target.files); e.target.value = ''; }} />
      </div>
      <Erro msg={erro || falha} />
      {dados && dados.length === 0 && <p className="vazio" style={{ margin: 0 }}>Nenhuma foto ainda. O site mostra só o logo até você adicionar.</p>}
      <div className="miniaturas" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))' }}>
        {dados?.map((f) => (
          <figure key={f.id} className={f.destaque ? 'escolhida' : ''}>
            <img src={`/api/publico/portfolio/${f.id}/miniatura`} alt="" loading="lazy" />
            {f.destaque
              ? <span className="marca">destaque</span>
              : <button className="marca" style={{ border: 0, color: 'var(--tinta)', cursor: 'pointer' }} onClick={() => destacar(f)}>destacar</button>}
            <button className="apagar" aria-label="Tirar do portfólio" onClick={() => apagar(f)}>×</button>
          </figure>
        ))}
      </div>
    </section>
  );
}

function Pacotes() {
  const { dados, erro, recarregar } = useDados<Pacote[]>('/pacotes');
  const [editando, setEditando] = useState<Pacote | 'novo' | null>(null);
  const [falha, setFalha] = useState('');

  async function salvar(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    const f = new FormData(ev.currentTarget);
    const num = (k: string) => (f.get(k) ? Number(String(f.get(k)).replace(',', '.')) : null);
    const corpo = {
      nome: f.get('nome'), qtdFotos: num('qtd'), preco: num('preco'), precoFotoExtra: num('extra'),
      prazoEntregaDias: num('prazo'), itens: String(f.get('itens') ?? '').split('\n').map((s) => s.trim()).filter(Boolean),
      materialEntregue: f.get('material') || null, publico: f.get('publico') === 'on', ativo: f.get('ativo') === 'on', ordem: num('ordem'),
      duracaoMin: num('duracao'), agendaOnline: f.get('agendaOnline') === 'on',
    };
    try {
      if (editando === 'novo') await api('/pacotes', { corpo });
      else if (editando) await api(`/pacotes/${editando.id}`, { metodo: 'PUT', corpo });
      setEditando(null);
      recarregar();
    } catch (er) {
      setFalha(er instanceof ErroApi ? er.message : 'Não foi possível salvar.');
    }
  }

  const p = editando === 'novo' ? null : editando;
  return (
    <section className="secao">
      <Erro msg={erro} />
      {editando ? (
        <form className="painel-caixa grade-form duas" onSubmit={salvar}>
          <label className="campo"><span>Nome</span><input id="pc-nome" name="nome" required defaultValue={p?.nome} /></label>
          <label className="campo"><span>Fotos inclusas (vazio = todas, sem seleção)</span><input id="pc-qtd" name="qtd" inputMode="numeric" defaultValue={p?.qtdFotos ?? ''} /></label>
          <label className="campo"><span>Preço (R$)</span><input id="pc-preco" name="preco" required inputMode="decimal" defaultValue={p?.preco} /></label>
          <label className="campo"><span>Foto extra (R$)</span><input id="pc-extra" name="extra" inputMode="decimal" defaultValue={p?.precoFotoExtra ?? ''} /></label>
          <label className="campo"><span>Prazo de entrega (dias úteis)</span><input id="pc-prazo" name="prazo" required inputMode="numeric" defaultValue={p?.prazoEntregaDias} /></label>
          <label className="campo"><span>Ordem no site</span><input id="pc-ordem" name="ordem" inputMode="numeric" defaultValue={p?.ordem} /></label>
          <label className="campo"><span>Duração do ensaio</span>
            <select id="pc-duracao" name="duracao" defaultValue={p?.duracaoMin ?? 120}>
              {[...new Set([...DURACOES, p?.duracaoMin ?? 120])].sort((a, b) => a - b).map((d) => <option key={d} value={d}>{duracaoTexto(d)}</option>)}
            </select>
          </label>
          <label className="marcar" style={{ alignSelf: 'end' }}><input id="pc-agenda" type="checkbox" name="agendaOnline" defaultChecked={p?.agendaOnline ?? true} /> Disponível na agenda online</label>
          <label className="campo inteira"><span>Itens (um por linha)</span><textarea id="pc-itens" name="itens" defaultValue={p?.itens.join('\n')} /></label>
          <label className="campo inteira"><span>Material entregue</span><input id="pc-material" name="material" defaultValue={p?.materialEntregue} /></label>
          <label className="marcar"><input id="pc-publico" type="checkbox" name="publico" defaultChecked={p?.publico ?? true} /> Mostrar no site</label>
          <label className="marcar"><input id="pc-ativo" type="checkbox" name="ativo" defaultChecked={p?.ativo ?? true} /> Ativo</label>
          <Erro msg={falha} />
          <div className="acoes-linha inteira">
            <button className="botao principal">Salvar</button>
            <button type="button" className="botao" onClick={() => setEditando(null)}>Cancelar</button>
          </div>
        </form>
      ) : <div><button className="botao" onClick={() => setEditando('novo')}>Novo pacote</button></div>}
      <div className="linhas">
        {dados?.map((x) => (
          <button key={x.id} className="linha-item" style={{ border: 0, textAlign: 'left', cursor: 'pointer', borderTop: '1px solid var(--linha)' }} onClick={() => setEditando(x)}>
            <span className="principal">{x.nome}{!x.ativo && <span className="legenda"> · INATIVO</span>}</span>
            <span className="lado num">{moeda(x.preco)}</span>
            <span className="legenda">{x.qtdFotos ? `${x.qtdFotos} FOTOS · EXTRA ${moeda(x.precoFotoExtra)}` : 'TODAS AS FOTOS'} · {x.prazoEntregaDias} DIAS ÚTEIS · {duracaoTexto(x.duracaoMin).toUpperCase()}</span>
            <span className="legenda lado">{x.publico ? 'NO SITE' : 'OCULTO'}{x.agendaOnline ? ' · AGENDA' : ''}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

function ItensAvulsos() {
  const { dados, erro, recarregar } = useDados<ItemAvulso[]>('/itens-avulsos');
  const [editando, setEditando] = useState<ItemAvulso | 'novo' | null>(null);
  const [falha, setFalha] = useState('');

  async function salvar(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    const f = new FormData(ev.currentTarget);
    const corpo = { nome: f.get('nome'), preco: numeroBr(f.get('preco')), ativo: f.get('ativo') === 'on' };
    setFalha('');
    try {
      if (editando === 'novo') await api('/itens-avulsos', { corpo });
      else if (editando) await api(`/itens-avulsos/${editando.id}`, { metodo: 'PUT', corpo });
      setEditando(null);
      recarregar();
    } catch (er) {
      setFalha(er instanceof ErroApi ? er.message : 'Não foi possível salvar.');
    }
  }
  const apagar = async (i: ItemAvulso) => { await api(`/itens-avulsos/${i.id}`, { metodo: 'DELETE' }); setEditando(null); recarregar(); };

  const i = editando === 'novo' ? null : editando;
  return (
    <section className="secao">
      <p className="suave" style={{ margin: 0 }}>Itens que você soma aos pacotes nos orçamentos: foto extra, álbum, impressões, deslocamento.</p>
      <Erro msg={erro} />
      {editando ? (
        <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar} key={i?.id ?? 'novo'}>
          <label className="campo"><span>Nome</span><input id="ia-nome" name="nome" required maxLength={80} defaultValue={i?.nome} /></label>
          <label className="campo"><span>Preço (R$)</span><input id="ia-preco" name="preco" required inputMode="decimal" defaultValue={i?.preco} /></label>
          <label className="marcar inteira"><input id="ia-ativo" type="checkbox" name="ativo" defaultChecked={i?.ativo ?? true} /> Disponível nos orçamentos</label>
          <Erro msg={falha} />
          <div className="acoes-linha inteira">
            <button className="botao principal">Salvar</button>
            <button type="button" className="botao" onClick={() => setEditando(null)}>Cancelar</button>
            {i && <button type="button" className="botao" style={{ marginLeft: 'auto', color: 'var(--erro)' }} onClick={() => apagar(i)}>Apagar</button>}
          </div>
        </form>
      ) : <div><button className="botao" onClick={() => setEditando('novo')}>Novo item</button></div>}
      <div className="linhas">
        {dados?.map((x) => (
          <button key={x.id} className="linha-item" style={{ border: 0, textAlign: 'left', cursor: 'pointer', borderTop: '1px solid var(--linha)' }} onClick={() => setEditando(x)}>
            <span className="principal">{x.nome}{!x.ativo && <span className="legenda"> · INATIVO</span>}</span>
            <span className="lado num">{moeda(x.preco)}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

function ModeloContrato() {
  const { dados, erro, setDados } = useDados<ContratoModelo>('/estudio/contrato');
  const [texto, setTexto] = useState<string | null>(null);
  const [falha, setFalha] = useState('');
  const [salvo, setSalvo] = useState(false);
  const area = useRef<HTMLTextAreaElement>(null);
  if (!dados) return <Erro msg={erro} />;
  const atual = texto ?? dados.texto;

  const inserir = (campo: string) => {
    const el = area.current;
    if (!el) return;
    const ini = el.selectionStart, fim = el.selectionEnd;
    const novo = atual.slice(0, ini) + campo + atual.slice(fim);
    setTexto(novo);
    requestAnimationFrame(() => { el.focus(); el.setSelectionRange(ini + campo.length, ini + campo.length); });
  };

  async function salvar(ev: FormEvent<HTMLFormElement>, padrao = false) {
    ev.preventDefault();
    const f = new FormData(ev.currentTarget);
    setFalha('');
    setSalvo(false);
    try {
      const r = await api<ContratoModelo>('/estudio/contrato', {
        metodo: 'PUT', corpo: { texto: padrao ? '' : atual, responsavel: f.get('responsavel'), documento: f.get('documento') },
      });
      setDados(r);
      setTexto(null);
      setSalvo(true);
    } catch (er) {
      setFalha(er instanceof ErroApi ? er.message : 'Não foi possível salvar.');
    }
  }

  return (
    <form className="painel-caixa grade-form duas" onSubmit={(e) => salvar(e)}>
      <label className="campo"><span>Responsável que assina</span><input id="ct-resp" name="responsavel" maxLength={120} defaultValue={dados.responsavel} placeholder="Seu nome completo" /></label>
      <label className="campo"><span>CPF ou CNPJ do estúdio</span><input id="ct-doc" name="documento" maxLength={20} defaultValue={dados.documento} /></label>
      <div className="inteira" style={{ display: 'grid', gap: 8 }}>
        <span className="legenda">TOQUE PARA INSERIR UM CAMPO; ELE É PREENCHIDO COM OS DADOS DO TRABALHO NO PDF.</span>
        <div className="campos-contrato">{dados.campos.split(/\s+/).filter(Boolean).map((c) => <button type="button" key={c} className="etiqueta" onClick={() => inserir(c)}>{c}</button>)}</div>
      </div>
      <label className="campo inteira"><span>Texto do contrato {dados.padrao && texto == null ? '(modelo padrão)' : ''}</span>
        <textarea id="ct-texto" ref={area} value={atual} onChange={(e) => setTexto(e.target.value)} rows={18} style={{ fontFamily: 'ui-monospace, Menlo, Consolas, monospace', fontSize: 13, lineHeight: '20px' }} />
      </label>
      <p className="legenda inteira" style={{ margin: 0 }}>
        TÍTULOS NUMERADOS EM MAIÚSCULAS (“1. OBJETO”) VIRAM CLÁUSULAS. DEIXE UMA LINHA EM BRANCO ENTRE PARÁGRAFOS. O MODELO PADRÃO É UM PONTO DE PARTIDA: REVISE COM UM ADVOGADO.
      </p>
      <Erro msg={falha} />
      {salvo && <p className="aviso sucesso inteira" style={{ margin: 0 }}>Modelo salvo. Os próximos contratos já saem com este texto.</p>}
      <div className="acoes-linha inteira">
        <button className="botao principal">Salvar modelo</button>
        {!dados.padrao && (
          <button type="button" className="botao" onClick={(e) => { const form = e.currentTarget.form; if (form) salvar({ preventDefault() {}, currentTarget: form } as unknown as FormEvent<HTMLFormElement>, true); }}>
            Voltar ao modelo padrão
          </button>
        )}
      </div>
    </form>
  );
}
