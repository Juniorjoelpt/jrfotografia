import { useRef, useState, type FormEvent } from 'react';
import { api, ErroApi, urlPainel } from '../api';
import type { Campanha, ConfigIndicacao, Cupom, DiaCampanhaForm, Indicacao, TipoCupom } from '../tipos';
import { copiar as copiarTexto, data, linkWhatsapp, moeda, numeroBr, primeiroNome, urlCampanha } from '../util';
import { Erro, useDados } from './comum';

type Aba = 'campanhas' | 'cupons' | 'indicacao';

/** Marketing: campanhas e mini-sessões, cupons de desconto e programa de indicação. */
export function Marketing() {
  const [aba, setAba] = useState<Aba>(() => (['campanhas', 'cupons', 'indicacao'].includes(window.location.hash.slice(1)) ? window.location.hash.slice(1) as Aba : 'campanhas'));
  const trocar = (a: Aba) => { setAba(a); history.replaceState(null, '', `#${a}`); };
  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Marketing</span><h1 className="titulo">{aba === 'campanhas' ? 'Campanhas' : aba === 'cupons' ? 'Cupons' : 'Indicação'}</h1></div></div>
      <div className="abas" role="group" aria-label="Seções">
        <button className="botao pequeno" aria-pressed={aba === 'campanhas'} onClick={() => trocar('campanhas')}>Mini-sessões</button>
        <button className="botao pequeno" aria-pressed={aba === 'cupons'} onClick={() => trocar('cupons')}>Cupons</button>
        <button className="botao pequeno" aria-pressed={aba === 'indicacao'} onClick={() => trocar('indicacao')}>Indicação</button>
      </div>
      {aba === 'campanhas' && <Campanhas />}
      {aba === 'cupons' && <Cupons />}
      {aba === 'indicacao' && <Indicacoes />}
    </>
  );
}

// ====================================================================== campanhas

function Campanhas() {
  const { dados, erro, recarregar } = useDados<Campanha[]>('/campanhas');
  const [editando, setEditando] = useState<Campanha | 'nova' | null>(null);
  const [copiado, setCopiado] = useState<string | null>(null);
  const copiar = async (slug: string) => {
    if (await copiarTexto(urlCampanha(slug))) { setCopiado(slug); setTimeout(() => setCopiado(null), 2000); }
  };
  return (
    <section className="secao">
      <div className="acoes-linha"><button className="botao principal pequeno" onClick={() => setEditando('nova')}>Nova mini-sessão</button></div>
      {editando && <FormCampanha c={editando === 'nova' ? null : editando} aoSalvar={() => { setEditando(null); recarregar(); }} aoCancelar={() => setEditando(null)} />}
      <Erro msg={erro} />
      {dados && (dados.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>Crie uma mini-sessão (Dia das Mães, Natal, aniversário da cidade): ela ganha uma página própria com os horários do dia.</p>
        : (
          <div className="mk-campanhas">
            {dados.map((c) => {
              const ultimo = c.dias[c.dias.length - 1]?.data;
              const encerrada = !c.ativa || (ultimo && ultimo < new Date().toISOString().slice(0, 10));
              return (
                <article key={c.id} className={`painel-caixa mk-campanha${encerrada ? ' encerrada' : ''}`}>
                  {c.capa && <img src={urlPainel(`/campanhas/${c.id}/capa`) + `&v=${c.id}${c.capa}`} alt="" />}
                  <div className="mk-campanha-corpo">
                    <strong>{c.titulo}</strong>
                    <span className="legenda">
                      {c.dias.map((d) => `${data(d.data).slice(0, 5)} ${d.inicio.slice(0, 5)}–${d.fim.slice(0, 5)}`).join(' · ')}
                    </span>
                    <span className="legenda">{moeda(c.preco)} · {c.duracaoMin} MIN · {c.reservas} {c.reservas === 1 ? 'RESERVA' : 'RESERVAS'} · {c.confirmadas} CONFIRMADAS · {moeda(c.faturamento)}</span>
                    {encerrada ? <span className="etiqueta">{c.ativa ? 'Encerrada' : 'Pausada'}</span> : <code className="mk-link">{urlCampanha(c.slug)}</code>}
                    <div className="acoes-linha">
                      {!encerrada && <button className="botao pequeno principal" onClick={() => copiar(c.slug)}>{copiado === c.slug ? 'Copiado!' : 'Copiar link'}</button>}
                      {!encerrada && <a className="botao pequeno" href={linkWhatsapp(null, `📸 ${c.titulo}! Sessões de ${c.duracaoMin} min por ${moeda(c.preco)}. Garanta seu horário: ${urlCampanha(c.slug)}`)} target="_blank" rel="noreferrer">Divulgar no WhatsApp</a>}
                      <a className="botao pequeno" href={`/c/${c.slug}`} target="_blank" rel="noreferrer">Ver página</a>
                      <button className="botao-link legenda" onClick={() => setEditando(c)}>Editar</button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        ))}
    </section>
  );
}

function FormCampanha({ c, aoSalvar, aoCancelar }: { c: Campanha | null; aoSalvar: () => void; aoCancelar: () => void }) {
  const amanha = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  const [dias, setDias] = useState<DiaCampanhaForm[]>(c?.dias.map((d) => ({ ...d, inicio: d.inicio.slice(0, 5), fim: d.fim.slice(0, 5) }))
    ?? [{ data: amanha, inicio: '09:00', fim: '12:00', local: '' }]);
  const [erro, setErro] = useState('');
  const [salvando, setSalvando] = useState(false);
  const capa = useRef<HTMLInputElement>(null);

  const mudarDia = (i: number, p: Partial<DiaCampanhaForm>) => setDias(dias.map((d, j) => (j === i ? { ...d, ...p } : d)));

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const n = (k: string) => (f.get(k) ? Number(f.get(k)) : null);
    const corpo = {
      titulo: f.get('titulo'), slug: f.get('slug') || null, descricao: f.get('descricao') || null, preco: numeroBr(f.get('preco')),
      sinalPercentual: n('sinal'), duracaoMin: n('duracao'), intervaloMin: n('intervalo'), qtdFotos: n('fotos'),
      precoFotoExtra: numeroBr(f.get('extra')), prazoEntregaDias: n('prazo'), ativa: f.get('ativa') === 'on',
      dias: dias.map((d) => ({ ...d, local: d.local || null })),
    };
    setErro('');
    setSalvando(true);
    try {
      const salva = c ? await api<Campanha>(`/campanhas/${c.id}`, { metodo: 'PUT', corpo }) : await api<Campanha>('/campanhas', { corpo });
      const arq = capa.current?.files?.[0];
      if (arq) {
        const fd = new FormData();
        fd.append('arquivo', arq);
        await api(`/campanhas/${salva.id}/capa`, { arquivos: fd });
      }
      aoSalvar();
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar}>
      <label className="campo"><span>Título</span><input id="cp-titulo" name="titulo" required maxLength={120} defaultValue={c?.titulo} placeholder="Mini-sessão Dia das Mães" /></label>
      <label className="campo"><span>Endereço da página (opcional)</span><input id="cp-slug" name="slug" maxLength={80} defaultValue={c?.slug} placeholder="gerado a partir do título" /></label>
      <label className="campo inteira"><span>Descrição</span><textarea id="cp-desc" name="descricao" rows={3} maxLength={4000} defaultValue={c?.descricao ?? ''} placeholder="Cenário, o que levar, quantas pessoas podem participar…" /></label>
      <label className="campo"><span>Preço (R$)</span><input id="cp-preco" name="preco" required inputMode="decimal" defaultValue={c?.preco} /></label>
      <label className="campo"><span>Sinal (%)</span><input id="cp-sinal" name="sinal" type="number" min={0} max={100} defaultValue={c?.sinalPercentual ?? 50} /></label>
      <label className="campo"><span>Duração de cada sessão (min)</span><input id="cp-duracao" name="duracao" type="number" min={5} max={480} defaultValue={c?.duracaoMin ?? 20} /></label>
      <label className="campo"><span>Intervalo entre sessões (min)</span><input id="cp-intervalo" name="intervalo" type="number" min={0} max={120} defaultValue={c?.intervaloMin ?? 10} /></label>
      <label className="campo"><span>Fotos inclusas (vazio = todas)</span><input id="cp-fotos" name="fotos" type="number" min={1} defaultValue={c?.qtdFotos ?? ''} /></label>
      <label className="campo"><span>Foto extra (R$)</span><input id="cp-extra" name="extra" inputMode="decimal" defaultValue={c?.precoFotoExtra ?? ''} /></label>
      <label className="campo"><span>Prazo de entrega (dias úteis)</span><input id="cp-prazo" name="prazo" type="number" min={1} max={120} defaultValue={c?.prazoEntregaDias ?? 10} /></label>
      <label className="campo"><span>Foto de capa (opcional)</span><input ref={capa} id="cp-capa" type="file" accept="image/jpeg,image/png" /></label>
      <div className="campo inteira">
        <span>Dias e horários</span>
        <div className="mk-dias">
          {dias.map((d, i) => (
            <div key={i} className="mk-dia">
              <input type="date" aria-label="Data" required value={d.data} onChange={(e) => mudarDia(i, { data: e.target.value })} />
              <input type="time" aria-label="Início" required value={d.inicio} onChange={(e) => mudarDia(i, { inicio: e.target.value })} />
              <span>às</span>
              <input type="time" aria-label="Fim" required value={d.fim} onChange={(e) => mudarDia(i, { fim: e.target.value })} />
              <input aria-label="Local" placeholder="Local (opcional)" maxLength={160} value={d.local ?? ''} onChange={(e) => mudarDia(i, { local: e.target.value })} />
              {dias.length > 1 && <button type="button" className="botao-link" aria-label="Remover dia" onClick={() => setDias(dias.filter((_, j) => j !== i))}>×</button>}
            </div>
          ))}
          <button type="button" className="botao pequeno" style={{ justifySelf: 'start' }}
            onClick={() => setDias([...dias, { ...dias[dias.length - 1], data: dias[dias.length - 1]?.data ?? amanha }])}>+ dia</button>
        </div>
      </div>
      <label className="marcar inteira"><input type="checkbox" name="ativa" defaultChecked={c?.ativa ?? true} /> Página aberta para reservas</label>
      <Erro msg={erro} />
      <div className="acoes-linha inteira">
        <button className="botao principal" disabled={salvando}>{salvando ? 'Salvando…' : 'Salvar mini-sessão'}</button>
        <button type="button" className="botao" onClick={aoCancelar}>Cancelar</button>
      </div>
    </form>
  );
}

// ====================================================================== cupons

function Cupons() {
  const { dados, erro, recarregar } = useDados<Cupom[]>('/cupons');
  const { dados: campanhas } = useDados<Campanha[]>('/campanhas');
  const [editando, setEditando] = useState<Cupom | 'novo' | null>(null);
  const [tipo, setTipo] = useState<TipoCupom>('PERCENTUAL');
  const [falha, setFalha] = useState('');

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const corpo = { codigo: f.get('codigo'), descricao: f.get('descricao') || null, tipo, valor: numeroBr(f.get('valor')),
      validade: f.get('validade') || null, usosMax: f.get('usos') ? Number(f.get('usos')) : null,
      campanhaId: f.get('campanha') ? Number(f.get('campanha')) : null, ativo: f.get('ativo') === 'on' };
    setFalha('');
    try {
      if (editando === 'novo') await api('/cupons', { corpo });
      else if (editando) await api(`/cupons/${editando.id}`, { metodo: 'PUT', corpo });
      setEditando(null);
      recarregar();
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }

  const c = editando === 'novo' ? null : editando;
  const abrir = (x: Cupom | 'novo') => { setEditando(x); setTipo(x === 'novo' ? 'PERCENTUAL' : x.tipo); };
  return (
    <section className="secao">
      <div className="acoes-linha"><button className="botao principal pequeno" onClick={() => abrir('novo')}>Novo cupom</button></div>
      <p className="legenda" style={{ margin: 0 }}>A CLIENTE DIGITA O CUPOM AO AGENDAR PELO SITE OU NA PÁGINA DA MINI-SESSÃO. O USO SÓ CONTA QUANDO VOCÊ CONFIRMA A RESERVA.</p>
      {editando && (
        <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar} key={c?.id ?? 'novo'}>
          <label className="campo"><span>Código</span><input id="cu-codigo" name="codigo" required maxLength={30} defaultValue={c?.codigo} placeholder="MAES10" style={{ textTransform: 'uppercase' }} /></label>
          <label className="campo"><span>Descrição (só para você)</span><input id="cu-desc" name="descricao" maxLength={120} defaultValue={c?.descricao ?? ''} /></label>
          <div className="campo"><span>Desconto</span>
            <div className="abas" role="group">
              <button type="button" className="botao pequeno" aria-pressed={tipo === 'PERCENTUAL'} onClick={() => setTipo('PERCENTUAL')}>Percentual</button>
              <button type="button" className="botao pequeno" aria-pressed={tipo === 'VALOR'} onClick={() => setTipo('VALOR')}>Valor fixo</button>
            </div>
          </div>
          <label className="campo"><span>{tipo === 'PERCENTUAL' ? 'Percentual (%)' : 'Valor (R$)'}</span><input id="cu-valor" name="valor" required inputMode="decimal" defaultValue={c?.valor} /></label>
          <label className="campo"><span>Válido até (opcional)</span><input id="cu-validade" name="validade" type="date" defaultValue={c?.validade ?? ''} /></label>
          <label className="campo"><span>Limite de usos (opcional)</span><input id="cu-usos" name="usos" type="number" min={1} defaultValue={c?.usosMax ?? ''} /></label>
          <label className="campo inteira"><span>Vale para</span>
            <select id="cu-campanha" name="campanha" defaultValue={c?.campanhaId ?? ''} key={campanhas ? 'lista' : 'carregando'}>
              <option value="">Qualquer agendamento online</option>
              {campanhas?.map((x) => <option key={x.id} value={x.id}>Só a mini-sessão: {x.titulo}</option>)}
            </select>
          </label>
          <label className="marcar"><input type="checkbox" name="ativo" defaultChecked={c?.ativo ?? true} /> Ativo</label>
          <Erro msg={falha} />
          <div className="acoes-linha inteira">
            <button className="botao principal">Salvar</button>
            <button type="button" className="botao" onClick={() => setEditando(null)}>Cancelar</button>
          </div>
        </form>
      )}
      <Erro msg={erro} />
      {dados && (dados.length === 0 ? <p className="vazio" style={{ margin: 0 }}>Nenhum cupom ainda.</p> : (
        <div className="linhas">
          {dados.map((x) => (
            <button key={x.id} className="linha-item" style={{ border: 0, borderTop: '1px solid var(--linha)', textAlign: 'left', cursor: 'pointer' }} onClick={() => abrir(x)}>
              <span className="principal" style={{ letterSpacing: '.06em' }}>{x.codigo}</span>
              <span className="lado num">{x.tipo === 'PERCENTUAL' ? `${String(x.valor).replace('.', ',')}%` : moeda(x.valor)}</span>
              <span className="legenda">
                {x.usos} {x.usos === 1 ? 'USO' : 'USOS'}{x.usosMax ? ` DE ${x.usosMax}` : ''}{x.validade ? ` · ATÉ ${data(x.validade)}` : ''}
                {x.campanhaId ? ` · ${(campanhas?.find((k) => k.id === x.campanhaId)?.titulo ?? 'mini-sessão').toUpperCase()}` : ''}
                {x.descricao ? ` · ${x.descricao.toUpperCase()}` : ''}
              </span>
              <span className="lado">{!x.ativo ? <span className="etiqueta">Inativo</span> : x.vencido ? <span className="etiqueta">Vencido</span>
                : x.usosMax != null && x.usos >= x.usosMax ? <span className="etiqueta">Esgotado</span> : <span className="etiqueta sucesso">Ativo</span>}</span>
            </button>
          ))}
        </div>
      ))}
    </section>
  );
}

// ====================================================================== indicação

function Indicacoes() {
  const { dados: cfg, setDados: setCfg } = useDados<ConfigIndicacao>('/estudio/indicacao');
  const { dados, erro, recarregar } = useDados<Indicacao[]>('/indicacoes');
  const [salvo, setSalvo] = useState(false);
  const [falha, setFalha] = useState('');

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setFalha('');
    try {
      setCfg(await api<ConfigIndicacao>('/estudio/indicacao', { metodo: 'PUT', corpo: { ativa: f.get('ativa') === 'on', desconto: Number(f.get('desconto')), recompensa: numeroBr(f.get('recompensa')) } }));
      setSalvo(true);
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }
  const marcar = async (i: Indicacao, status: Indicacao['status']) => { await api(`/indicacoes/${i.id}`, { metodo: 'PATCH', corpo: { status } }); recarregar(); };
  const pendente = (dados ?? []).filter((i) => i.status === 'CONFIRMADA').reduce((s, i) => s + i.recompensa, 0);

  return (
    <section className="secao">
      {cfg && (
        <form className="painel-caixa grade-form duas" onSubmit={salvar} onChange={() => setSalvo(false)}>
          <label className="marcar inteira"><input id="in-ativa" type="checkbox" name="ativa" defaultChecked={cfg.ativa} /> <strong>Programa de indicação ligado</strong></label>
          <p className="suave inteira" style={{ margin: 0 }}>
            Cada cliente ganha um código na área da cliente. A amiga usa o código ao agendar pelo site e ganha desconto; quando você confirma a reserva,
            a indicação aparece aqui para você dar a recompensa (desconto no próximo ensaio, brinde ou Pix).
          </p>
          <label className="campo"><span>Desconto para a amiga (%)</span><input id="in-desconto" name="desconto" type="number" min={0} max={100} defaultValue={cfg.desconto} /></label>
          <label className="campo"><span>Recompensa para quem indicou (R$)</span><input id="in-recompensa" name="recompensa" inputMode="decimal" defaultValue={cfg.recompensa} /></label>
          <Erro msg={falha} />
          {salvo && <p className="aviso sucesso inteira" style={{ margin: 0 }}>Salvo.</p>}
          <div className="inteira"><button className="botao principal">Salvar</button></div>
        </form>
      )}
      <div className="cabeca-pagina">
        <h2 className="subtitulo">Indicações{pendente > 0 ? ` · ${moeda(pendente)} a recompensar` : ''}</h2>
      </div>
      <Erro msg={erro} />
      {dados && (dados.length === 0 ? <p className="vazio" style={{ margin: 0 }}>Nenhuma indicação confirmada ainda.</p> : (
        <div className="linhas">
          {dados.map((i) => (
            <div key={i.id} className="linha-item">
              <span className="principal">{i.indicador} indicou {i.indicado}</span>
              <span className="lado num">{moeda(i.recompensa)}</span>
              <span className="legenda">{data(i.criadoEm)}{i.recompensadaEm ? ` · RECOMPENSADA EM ${data(i.recompensadaEm)}` : ''}</span>
              <span className="lado acoes-linha">
                {i.status === 'CONFIRMADA' ? (
                  <>
                    {i.indicadorTelefone && (
                      <a className="botao-link legenda" target="_blank" rel="noreferrer" href={linkWhatsapp(i.indicadorTelefone,
                        `Oi, ${primeiroNome(i.indicador)}! A ${primeiroNome(i.indicado)} fechou o ensaio com o seu código 💛 Você ganhou ${moeda(i.recompensa)}. Obrigado pela indicação!`)}>Avisar</a>
                    )}
                    <button className="botao pequeno" onClick={() => marcar(i, 'RECOMPENSADA')}>Recompensa dada</button>
                  </>
                ) : i.status === 'RECOMPENSADA'
                  ? <span className="etiqueta sucesso">Recompensada</span>
                  : <span className="etiqueta">Cancelada</span>}
              </span>
            </div>
          ))}
        </div>
      ))}
    </section>
  );
}

