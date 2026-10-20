import { useRef, useState, type FormEvent } from 'react';
import { api, ErroApi } from '../api';
import type { Estudio, Pacote, PortfolioFoto } from '../tipos';
import { moeda } from '../util';
import { Erro, useDados } from './comum';

export function Ajustes() {
  const [aba, setAba] = useState<'estudio' | 'portfolio' | 'pacotes'>('estudio');
  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Ajustes</span><h1 className="titulo">Ajustes do estúdio</h1></div></div>
      <div className="abas" role="group" aria-label="Seções">
        <button className="botao pequeno" aria-pressed={aba === 'estudio'} onClick={() => setAba('estudio')}>Dados e contato</button>
        <button className="botao pequeno" aria-pressed={aba === 'portfolio'} onClick={() => setAba('portfolio')}>Portfólio do site</button>
        <button className="botao pequeno" aria-pressed={aba === 'pacotes'} onClick={() => setAba('pacotes')}>Pacotes</button>
      </div>
      {aba === 'estudio' && <DadosEstudio />}
      {aba === 'portfolio' && <Portfolio />}
      {aba === 'pacotes' && <Pacotes />}
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
            <span className="legenda">{x.qtdFotos ? `${x.qtdFotos} FOTOS · EXTRA ${moeda(x.precoFotoExtra)}` : 'TODAS AS FOTOS'} · {x.prazoEntregaDias} DIAS ÚTEIS</span>
            <span className="legenda lado">{x.publico ? 'NO SITE' : 'OCULTO'}</span>
          </button>
        ))}
      </div>
    </section>
  );
}
