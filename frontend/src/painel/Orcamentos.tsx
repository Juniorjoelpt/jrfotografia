import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, ErroApi, urlPainel } from '../api';
import type { Cliente, FormaPagamento, ItemAvulso, Orcamento, Pacote, Pedido } from '../tipos';
import {
  CLASSE_ORCAMENTO, data, dataHora, isoDia, linkWhatsapp, moeda, NOMES_ORCAMENTO, numeroBr, primeiroNome, telefone, urlOrcamento,
} from '../util';
import { Erro, useDados } from './comum';

type Linha = { chave: number; descricao: string; quantidade: string; valorUnit: string };
let proximaChave = 1;
const linha = (descricao = '', quantidade = '1', valorUnit = ''): Linha => ({ chave: proximaChave++, descricao, quantidade, valorUnit });

/** Monta ou edita um orçamento: pacote + itens avulsos, desconto e forma de pagamento. */
export function EditorOrcamento() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navegar = useNavigate();
  const { dados: clientes } = useDados<Cliente[]>('/clientes');
  const { dados: pacotes } = useDados<Pacote[]>('/pacotes');
  const { dados: catalogo } = useDados<ItemAvulso[]>('/itens-avulsos');

  const [pronto, setPronto] = useState(!id && !params.get('pedido'));
  const [pedidoId, setPedidoId] = useState<number | null>(null);
  const [dica, setDica] = useState('');
  const [clienteId, setClienteId] = useState(params.get('cliente') ?? '');
  const [nome, setNome] = useState('');
  const [fone, setFone] = useState('');
  const [email, setEmail] = useState('');
  const [pacoteId, setPacoteId] = useState('');
  const [tipo, setTipo] = useState('');
  const [quando, setQuando] = useState('');
  const [local, setLocal] = useState('');
  const [itens, setItens] = useState<Linha[]>([]);
  const [desconto, setDesconto] = useState('');
  const [forma, setForma] = useState<FormaPagamento>('AVISTA');
  const [sinal, setSinal] = useState('');
  const [parcelas, setParcelas] = useState('2');
  const [validade, setValidade] = useState(() => { const d = new Date(); d.setDate(d.getDate() + 7); return isoDia(d); });
  const [obs, setObs] = useState('');
  const [conflitos, setConflitos] = useState<string[]>([]);
  const [erro, setErro] = useState('');
  const [salvando, setSalvando] = useState(false);

  // Edição: carrega o orçamento. O item do pacote é refeito pelo servidor, então sai da lista.
  useEffect(() => {
    if (!id) return;
    api<Orcamento>(`/orcamentos/${id}`).then((o) => {
      setPedidoId(o.pedidoId ?? null);
      setClienteId(o.clienteId ? String(o.clienteId) : '');
      setNome(o.nomeCliente); setFone(o.telefone ?? ''); setEmail(o.email ?? '');
      setPacoteId(o.pacoteId ? String(o.pacoteId) : '');
      setTipo(o.tipo ?? ''); setQuando(o.dataPrevista?.slice(0, 16) ?? ''); setLocal(o.localPrevisto ?? '');
      setItens((o.pacoteId ? o.itens.slice(1) : o.itens).map((i) => linha(i.descricao, String(i.quantidade), String(i.valorUnit))));
      setDesconto(o.desconto ? String(o.desconto) : '');
      setForma(o.formaPagamento); setSinal(o.valorSinal ? String(o.valorSinal) : ''); setParcelas(String(o.numParcelas ?? 2));
      setValidade(o.validade); setObs(o.observacoes ?? '');
      setPronto(true);
    }).catch((e) => setErro(e instanceof ErroApi ? e.message : 'Não foi possível carregar.'));
  }, [id]);

  // Vindo de um pedido do site: já preenche quem pediu e o que pediu.
  useEffect(() => {
    const pid = params.get('pedido');
    if (id || !pid) return;
    api<Pedido[]>('/pedidos').then((lista) => {
      const p = lista.find((x) => String(x.id) === pid);
      if (p) {
        setPedidoId(p.id);
        setNome(p.nome); setFone(telefone(p.telefone)); setEmail(p.email ?? '');
        setTipo(p.tipoTrabalho ?? ''); if (p.pacoteId) setPacoteId(String(p.pacoteId));
        if (p.clienteId) setClienteId(String(p.clienteId));
        setDica([p.dataDesejada && `Data desejada: ${data(p.dataDesejada)}`, p.mensagem && `“${p.mensagem}”`].filter(Boolean).join(' · '));
      }
      setPronto(true);
    }).catch(() => setPronto(true));
  }, [id, params]);

  // Aviso de conflito na agenda para a data prevista.
  useEffect(() => {
    if (!quando) { setConflitos([]); return; }
    const t = setTimeout(() => {
      api<{ avisos: string[] }>(`/agenda/conflitos?inicio=${encodeURIComponent(`${quando}:00`)}&duracaoMin=240`)
        .then((r) => setConflitos(r.avisos)).catch(() => setConflitos([]));
    }, 350);
    return () => clearTimeout(t);
  }, [quando]);

  const pacote = useMemo(() => pacotes?.find((p) => String(p.id) === pacoteId), [pacotes, pacoteId]);
  const cliente = clientes?.find((c) => String(c.id) === clienteId);
  const subtotal = (pacote?.preco ?? 0) + itens.reduce((s, i) => s + (numeroBr(i.quantidade) ?? 0) * (numeroBr(i.valorUnit) ?? 0), 0);
  const total = Math.max(0, subtotal - (numeroBr(desconto) ?? 0));
  const n = Math.max(1, Number(parcelas) || 1);
  const resto = Math.max(0, total - (forma === 'PARCELADO' ? numeroBr(sinal) ?? 0 : 0));

  const mudarItem = (chave: number, campo: keyof Linha, valor: string) =>
    setItens((l) => l.map((i) => (i.chave === chave ? { ...i, [campo]: valor } : i)));
  const doCatalogo = (idItem: string) => {
    const i = catalogo?.find((x) => String(x.id) === idItem);
    if (i) setItens((l) => [...l, linha(i.nome, '1', String(i.preco))]);
  };

  async function salvar(ev: FormEvent) {
    ev.preventDefault();
    setErro('');
    setSalvando(true);
    const corpo = {
      clienteId: clienteId ? Number(clienteId) : null, pedidoId,
      nomeCliente: clienteId ? null : nome, telefone: clienteId ? null : fone, email: clienteId ? null : email || null,
      pacoteId: pacoteId ? Number(pacoteId) : null, tipo: tipo || null,
      dataPrevista: quando ? `${quando}:00` : null, localPrevisto: local || null,
      itens: itens.filter((i) => i.descricao.trim()).map((i) => ({
        descricao: i.descricao, quantidade: Math.max(1, Math.round(numeroBr(i.quantidade) ?? 1)), valorUnit: numeroBr(i.valorUnit) ?? 0,
      })),
      desconto: numeroBr(desconto) ?? 0, formaPagamento: forma,
      valorSinal: forma === 'PARCELADO' ? numeroBr(sinal) : null, numParcelas: forma === 'PARCELADO' ? n : null,
      validade, observacoes: obs || null,
    };
    try {
      const o = id
        ? await api<Orcamento>(`/orcamentos/${id}`, { metodo: 'PUT', corpo })
        : await api<Orcamento>('/orcamentos', { corpo });
      navegar(`/painel/orcamentos/${o.id}`, { replace: true });
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  if (!pronto) return <>{erro ? <Erro msg={erro} /> : <div className="esqueleto" style={{ height: 320 }} />}</>;

  return (
    <>
      <div className="cabeca-pagina">
        <div><span className="rotulo">Vendas</span><h1 className="titulo">{id ? 'Editar orçamento' : 'Novo orçamento'}</h1></div>
      </div>
      <form className="editor-orcamento" onSubmit={salvar}>
        <div className="grade-form" style={{ alignContent: 'start' }}>
          <fieldset className="painel-caixa grade-form duas">
            <legend className="chamada">Cliente</legend>
            {dica && <p className="aviso inteira" style={{ margin: 0 }}>{dica}</p>}
            <label className="campo inteira"><span>Cliente já cadastrado</span>
              <select id="or-cliente" value={clienteId} onChange={(e) => setClienteId(e.target.value)}>
                <option value="">Ainda não é cliente</option>
                {clientes?.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </label>
            {!clienteId && (
              <>
                <label className="campo"><span>Nome</span><input id="or-nome" required value={nome} onChange={(e) => setNome(e.target.value)} /></label>
                <label className="campo"><span>WhatsApp</span><input id="or-fone" inputMode="tel" value={fone} onChange={(e) => setFone(e.target.value)} placeholder="(98) 9 0000-0000" /></label>
                <label className="campo inteira"><span>E-mail (opcional)</span><input id="or-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></label>
                <p className="legenda inteira" style={{ margin: 0 }}>SE O CLIENTE ACEITAR, O CADASTRO É CRIADO SOZINHO.</p>
              </>
            )}
          </fieldset>

          <fieldset className="painel-caixa grade-form duas">
            <legend className="chamada">Ensaio</legend>
            <label className="campo"><span>Pacote</span>
              <select id="or-pacote" value={pacoteId} onChange={(e) => setPacoteId(e.target.value)}>
                <option value="">Sem pacote (só itens)</option>
                {pacotes?.filter((p) => p.ativo || String(p.id) === pacoteId).map((p) => <option key={p.id} value={p.id}>{p.nome} · {moeda(p.preco)}</option>)}
              </select>
            </label>
            <label className="campo"><span>Tipo de ensaio</span><input id="or-tipo" value={tipo} onChange={(e) => setTipo(e.target.value)} placeholder="Ensaio feminino" /></label>
            <label className="campo"><span>Data prevista</span><input id="or-data" type="datetime-local" value={quando} onChange={(e) => setQuando(e.target.value)} /></label>
            <label className="campo"><span>Local</span><input id="or-local" value={local} onChange={(e) => setLocal(e.target.value)} placeholder="Estúdio, praça, endereço" /></label>
            {conflitos.length > 0 && <div className="aviso inteira" role="status" style={{ margin: 0, borderColor: 'var(--alerta)' }}>{conflitos.map((c) => <div key={c}>{c}</div>)}</div>}
          </fieldset>

          <fieldset className="painel-caixa grade-form">
            <legend className="chamada">Itens avulsos</legend>
            {itens.length === 0 && <p className="legenda" style={{ margin: 0 }}>FOTOS EXTRAS, ÁLBUM, IMPRESSÕES, DESLOCAMENTO…</p>}
            {itens.map((i) => (
              <div key={i.chave} className="item-linha anima-entrada">
                <label className="campo"><span>Descrição</span><input value={i.descricao} onChange={(e) => mudarItem(i.chave, 'descricao', e.target.value)} required /></label>
                <label className="campo"><span>Qtd.</span><input inputMode="numeric" value={i.quantidade} onChange={(e) => mudarItem(i.chave, 'quantidade', e.target.value)} /></label>
                <label className="campo"><span>Valor (R$)</span><input inputMode="decimal" value={i.valorUnit} onChange={(e) => mudarItem(i.chave, 'valorUnit', e.target.value)} required /></label>
                <button type="button" className="botao pequeno" aria-label={`Tirar ${i.descricao || 'item'}`} onClick={() => setItens((l) => l.filter((x) => x.chave !== i.chave))}>×</button>
              </div>
            ))}
            <div className="acoes-linha">
              {!!catalogo?.filter((c) => c.ativo).length && (
                <select aria-label="Adicionar item do catálogo" className="botao pequeno" value="" onChange={(e) => doCatalogo(e.target.value)}>
                  <option value="">+ Do catálogo</option>
                  {catalogo.filter((c) => c.ativo).map((c) => <option key={c.id} value={c.id}>{c.nome} · {moeda(c.preco)}</option>)}
                </select>
              )}
              <button type="button" className="botao pequeno" onClick={() => setItens((l) => [...l, linha()])}>+ Item livre</button>
            </div>
          </fieldset>

          <fieldset className="painel-caixa grade-form duas">
            <legend className="chamada">Pagamento</legend>
            <label className="campo"><span>Desconto (R$)</span><input id="or-desconto" inputMode="decimal" value={desconto} onChange={(e) => setDesconto(e.target.value)} /></label>
            <label className="campo"><span>Forma</span>
              <select id="or-forma" value={forma} onChange={(e) => setForma(e.target.value as FormaPagamento)}>
                <option value="AVISTA">À vista</option><option value="PARCELADO">Parcelado</option>
              </select>
            </label>
            {forma === 'PARCELADO' && (
              <>
                <label className="campo"><span>Sinal (R$)</span><input id="or-sinal" inputMode="decimal" value={sinal} onChange={(e) => setSinal(e.target.value)} /></label>
                <label className="campo"><span>Parcelas depois do sinal</span>
                  <select id="or-parcelas" value={parcelas} onChange={(e) => setParcelas(e.target.value)}>
                    {[1, 2, 3, 4, 5, 6].map((i) => <option key={i} value={i}>{i}x de {moeda(resto / i)}</option>)}
                  </select>
                </label>
              </>
            )}
            <label className="campo"><span>Válido até</span><input id="or-validade" type="date" required value={validade} onChange={(e) => setValidade(e.target.value)} /></label>
            <label className="campo inteira"><span>Observações para o cliente</span><textarea id="or-obs" value={obs} onChange={(e) => setObs(e.target.value)} rows={3} /></label>
          </fieldset>
        </div>

        <aside className="painel-caixa resumo-orcamento">
          <span className="rotulo">Resumo</span>
          <strong className="subtitulo">{cliente?.nome ?? (nome || 'Cliente')}</strong>
          <dl>
            {pacote && <div><dt>Pacote {pacote.nome}</dt><dd className="num">{moeda(pacote.preco)}</dd></div>}
            {itens.filter((i) => i.descricao).map((i) => (
              <div key={i.chave}><dt>{i.descricao}{(numeroBr(i.quantidade) ?? 1) > 1 ? ` × ${i.quantidade}` : ''}</dt>
                <dd className="num">{moeda((numeroBr(i.quantidade) ?? 0) * (numeroBr(i.valorUnit) ?? 0))}</dd></div>
            ))}
            {(numeroBr(desconto) ?? 0) > 0 && <div><dt>Desconto</dt><dd className="num">- {moeda(numeroBr(desconto))}</dd></div>}
          </dl>
          <div className="total"><span className="legenda">TOTAL</span><strong className="num">{moeda(total)}</strong></div>
          <p className="legenda" style={{ margin: 0 }}>
            {forma === 'AVISTA' ? 'À VISTA' : `${(numeroBr(sinal) ?? 0) > 0 ? `SINAL ${moeda(numeroBr(sinal))} + ` : ''}${n}X DE ${moeda(resto / n)}`} · VALE ATÉ {data(validade)}
          </p>
          <Erro msg={erro} />
          <button className="botao principal" disabled={salvando || (!pacote && !itens.some((i) => i.descricao.trim()))}>{salvando ? 'Salvando…' : 'Salvar orçamento'}</button>
          <button type="button" className="botao" onClick={() => navegar(-1)}>Cancelar</button>
        </aside>
      </form>
    </>
  );
}

/** Orçamento pronto: resumo e ações de envio. */
export function DetalheOrcamento() {
  const { id } = useParams();
  const navegar = useNavigate();
  const { dados: o, setDados, erro } = useDados<Orcamento>(`/orcamentos/${id}`);
  const [copiado, setCopiado] = useState(false);
  const [apagando, setApagando] = useState(false);
  if (!o) return <>{erro ? <Erro msg={erro} /> : <div className="esqueleto" style={{ height: 320 }} />}</>;

  const link = urlOrcamento(o.token);
  const mensagem = `Olá, ${primeiroNome(o.nomeCliente)}! Aqui é o Joel, da JR Fotografia. Segue o seu orçamento${o.tipo ? ` de ${o.tipo.toLowerCase()}` : ''}: ${moeda(o.valorTotal)}.\n\nVeja os detalhes e confirme por aqui:\n${link}\n\nO orçamento vale até ${data(o.validade)}. Qualquer dúvida, é só me chamar.`;
  const marcarEnviado = () => { api<Orcamento>(`/orcamentos/${o.id}/enviado`, { corpo: {} }).then(setDados).catch(() => {}); };
  const copiar = async () => {
    try { await navigator.clipboard.writeText(link); setCopiado(true); setTimeout(() => setCopiado(false), 2000); marcarEnviado(); } catch { /* sem permissão */ }
  };
  const apagar = async () => { await api(`/orcamentos/${o.id}`, { metodo: 'DELETE' }); navegar('/painel/vendas?aba=orcamentos', { replace: true }); };
  const fechado = o.status === 'ACEITO';

  return (
    <>
      <div className="cabeca-pagina">
        <div><span className="rotulo">Orçamento nº {o.numero}</span><h1 className="titulo">{o.nomeCliente}</h1></div>
        <div className="cabeca-lado">
          <span className={`etiqueta ${CLASSE_ORCAMENTO[o.status]}`}>{NOMES_ORCAMENTO[o.status]}</span>
          <span className="legenda">VALE ATÉ {data(o.validade)}</span>
        </div>
      </div>

      <div className="acoes-linha">
        {fechado && o.trabalhoId && <Link className="botao principal" to={`/painel/trabalhos/${o.trabalhoId}`}>Abrir trabalho</Link>}
        {!fechado && o.telefone && (
          <a className="botao principal" href={linkWhatsapp(o.telefone, mensagem)} target="_blank" rel="noreferrer" onClick={marcarEnviado}>
            Enviar pelo WhatsApp
          </a>
        )}
        {!fechado && <button className="botao" onClick={copiar}>{copiado ? 'Link copiado' : 'Copiar link'}</button>}
        <a className="botao" href={urlPainel(`/orcamentos/${o.id}/pdf`)} target="_blank" rel="noreferrer">PDF</a>
        <a className="botao" href={link} target="_blank" rel="noreferrer">Ver como o cliente</a>
        {!fechado && <Link className="botao" to={`/painel/orcamentos/${o.id}/editar`}>Editar</Link>}
      </div>
      {!fechado && !o.telefone && <p className="aviso" style={{ margin: 0 }}>Sem WhatsApp cadastrado: copie o link e envie por onde preferir.</p>}
      {o.status === 'ACEITO' && <p className="aviso sucesso" style={{ margin: 0 }}>Aceito em {dataHora(o.respondidoEm)}. O trabalho e as parcelas já foram criados.</p>}
      {o.status === 'RECUSADO' && <p className="aviso erro" style={{ margin: 0 }}>O cliente recusou em {dataHora(o.respondidoEm)}. Você pode editar e reenviar.</p>}

      <section className="painel-caixa resumo-orcamento largo">
        <dl>
          {o.itens.map((i, n) => (
            <div key={n}><dt>{i.descricao}{i.quantidade > 1 ? ` × ${i.quantidade}` : ''}</dt><dd className="num">{moeda(i.total)}</dd></div>
          ))}
          {o.desconto > 0 && <div><dt>Desconto</dt><dd className="num">- {moeda(o.desconto)}</dd></div>}
        </dl>
        <div className="total"><span className="legenda">TOTAL</span><strong className="num">{moeda(o.valorTotal)}</strong></div>
        <p className="legenda" style={{ margin: 0 }}>
          {o.formaPagamento === 'AVISTA' ? 'À VISTA' : `${o.valorSinal ? `SINAL ${moeda(o.valorSinal)} + ` : ''}${o.numParcelas ?? 1} PARCELAS`}
          {o.dataPrevista ? ` · ENSAIO ${dataHora(o.dataPrevista)}` : ''}{o.localPrevisto ? ` · ${o.localPrevisto.toUpperCase()}` : ''}
        </p>
        {o.observacoes && <p className="suave" style={{ margin: 0, whiteSpace: 'pre-line' }}>{o.observacoes}</p>}
      </section>

      {!fechado && (
        <div>
          {apagando
            ? <span className="acoes-linha"><button className="botao pequeno" style={{ borderColor: 'var(--erro)', color: 'var(--erro)' }} onClick={apagar}>Apagar de vez</button><button className="botao pequeno" onClick={() => setApagando(false)}>Manter</button></span>
            : <button className="botao-link" style={{ color: 'var(--tinta-suave)', fontSize: 13 }} onClick={() => setApagando(true)}>Apagar orçamento</button>}
        </div>
      )}
    </>
  );
}
