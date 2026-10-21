import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ErroApi, urlPainel } from '../api';
import type { Material, PedidoProduto, StatusPedidoProduto } from '../tipos';
import { data, dataHora, linkWhatsapp, moeda, NOMES_PRODUTO, numeroBr, primeiroNome, qtd, urlAlbum } from '../util';
import { Erro, useDados } from './comum';
import { EtiquetaPedido, FormPedido } from './Produtos';

/** Próximos passos possíveis de um pedido, conforme o tipo e a etapa. */
function proximos(p: PedidoProduto): { status: StatusPedidoProduto; rotulo: string; principal?: boolean }[] {
  const album = p.tipo === 'ALBUM';
  switch (p.status) {
    case 'NOVO': return album ? [] : [{ status: 'LABORATORIO', rotulo: 'Enviei ao laboratório', principal: true }, { status: 'PRONTO', rotulo: 'Já está pronto' }];
    case 'DIAGRAMACAO': return album && p.laminas.length > 0 && p.aprovacao == null ? [{ status: 'APROVACAO', rotulo: 'Enviar para o cliente aprovar', principal: true }] : [];
    case 'APROVADO': return [{ status: 'LABORATORIO', rotulo: 'Enviei ao laboratório', principal: true }];
    case 'LABORATORIO': return [{ status: 'PRONTO', rotulo: 'Chegou do laboratório', principal: true }];
    case 'PRONTO': return [{ status: 'ENTREGUE', rotulo: 'Entreguei ao cliente', principal: true }];
    default: return [];
  }
}

export function DetalhePedido() {
  const { id = '' } = useParams();
  const { dados: p, setDados, erro: erroCarga } = useDados<PedidoProduto>(`/produtos/pedidos/${id}`);
  const [editando, setEditando] = useState(false);
  const [erro, setErro] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [previsao, setPrevisao] = useState('');
  const [cancelar, setCancelar] = useState(false);
  const arquivos = useRef<HTMLInputElement>(null);

  if (!p) return <Erro msg={erroCarga} />;

  async function executar(acao: () => Promise<PedidoProduto>) {
    setOcupado(true);
    setErro('');
    try { setDados(await acao()); } catch (e) { setErro(e instanceof ErroApi ? e.message : 'Não foi possível concluir.'); } finally { setOcupado(false); }
  }
  const mudar = (status: StatusPedidoProduto) => executar(() => api<PedidoProduto>(`/produtos/pedidos/${p.id}/status`, {
    metodo: 'PATCH', corpo: { status, previsaoLab: status === 'LABORATORIO' && previsao ? previsao : null },
  }));
  const enviarLaminas = (lista: FileList | null) => {
    if (!lista?.length) return;
    const fd = new FormData();
    Array.from(lista).forEach((a) => fd.append('arquivos', a));
    executar(() => api<PedidoProduto>(`/produtos/pedidos/${p.id}/laminas`, { arquivos: fd }));
  };
  const apagarLamina = (lid: number) => executar(() => api<PedidoProduto>(`/produtos/pedidos/${p.id}/laminas/${lid}`, { metodo: 'DELETE' }));

  const link = p.tokenAprovacao ? urlAlbum(p.tokenAprovacao) : '';
  const msgAprovacao = `Oi, ${primeiroNome(p.cliente)}! A diagramação do seu álbum está pronta 📖 Veja as páginas e me diga se aprova ou se quer mudar algo:\n${link}`;
  const passos = proximos(p);
  const fechado = p.status === 'ENTREGUE' || p.status === 'CANCELADO';
  const podeEnviarLaminas = p.tipo === 'ALBUM' && !fechado && p.aprovacao !== 'APROVADO' && p.status !== 'APROVACAO';

  return (
    <>
      <div className="cabeca-pagina">
        <div>
          <span className="rotulo">{NOMES_PRODUTO[p.tipo]}</span>
          <h1 className="titulo">{p.descricao}{p.quantidade > 1 ? ` × ${p.quantidade}` : ''}</h1>
          <Link className="suave" to={`/painel/trabalhos/${p.trabalhoId}`}>{p.trabalho}</Link>
        </div>
        <div className="cabeca-lado"><EtiquetaPedido p={p} />{p.prazo && !fechado && <span className="legenda">PRAZO {data(p.prazo)}</span>}</div>
      </div>

      <section className="painel-caixa secao">
        <dl className="pd-dados">
          <div><dt>Venda</dt><dd>{moeda(p.valorVenda)}{p.cobrado ? <span className="legenda"> · {p.recebido ? 'RECEBIDO' : 'COBRANÇA LANÇADA'}</span> : ''}</dd></div>
          <div><dt>Custo</dt><dd>{p.custo != null ? moeda(p.custo) : '—'}{p.custoLancado ? <span className="legenda"> · {p.custoPago ? 'PAGO' : 'NO FINANCEIRO'}</span> : ''}</dd></div>
          <div><dt>Laboratório</dt><dd>{p.laboratorio ?? '—'}</dd></div>
          {p.enviadoLabEm && <div><dt>Enviado</dt><dd>{data(p.enviadoLabEm)}{p.previsaoLab ? ` · previsto ${data(p.previsaoLab)}` : ''}</dd></div>}
          {p.recebidoEm && <div><dt>Chegou</dt><dd>{data(p.recebidoEm)}</dd></div>}
          {p.entregueEm && <div><dt>Entregue</dt><dd>{data(p.entregueEm)}</dd></div>}
        </dl>
        {p.observacoes && <p className="suave" style={{ margin: 0, whiteSpace: 'pre-line' }}>{p.observacoes}</p>}
        <Erro msg={erro} />
        {!fechado && (
          <div className="acoes-linha">
            {passos.some((x) => x.status === 'LABORATORIO') && (
              <label className="campo" style={{ minWidth: 180 }}><span>Previsão do laboratório</span>
                <input id="pd-previsao" type="date" value={previsao} onChange={(e) => setPrevisao(e.target.value)} /></label>
            )}
            {passos.map((x) => (
              <button key={x.status} className={`botao pequeno${x.principal ? ' principal' : ''}`} disabled={ocupado} onClick={() => mudar(x.status)}>{x.rotulo}</button>
            ))}
            <button className="botao pequeno" onClick={() => setEditando(!editando)}>Editar</button>
            {cancelar
              ? <><button className="botao pequeno" style={{ color: 'var(--erro)', borderColor: 'var(--erro)' }} onClick={() => mudar('CANCELADO')}>Confirmar cancelamento</button>
                <button className="botao-link legenda" onClick={() => setCancelar(false)}>Voltar</button></>
              : <button className="botao-link legenda" onClick={() => setCancelar(true)}>Cancelar pedido</button>}
          </div>
        )}
        {p.tipo === 'ALBUM' && p.status === 'NOVO' && <p className="legenda" style={{ margin: 0 }}>PRÓXIMO PASSO: ENVIE AS LÂMINAS DA DIAGRAMAÇÃO (JPEG) LOGO ABAIXO.</p>}
      </section>
      {editando && <FormPedido pedido={p} aoSalvar={(n) => { setDados(n); setEditando(false); }} aoCancelar={() => setEditando(false)} />}

      {p.tipo === 'ALBUM' && (
        <section className="secao">
          <div className="cabeca-pagina">
            <h2 className="chamada" style={{ margin: 0 }}>Diagramação{p.versaoLayout > 0 ? ` · versão ${p.versaoLayout}` : ''}</h2>
            {podeEnviarLaminas && (
              <>
                <button className="botao pequeno" disabled={ocupado} onClick={() => arquivos.current?.click()}>
                  {ocupado ? 'Enviando…' : p.aprovacao === 'AJUSTES' ? 'Enviar nova versão' : 'Enviar lâminas'}
                </button>
                <input ref={arquivos} type="file" accept="image/jpeg,image/png" multiple hidden onChange={(e) => { enviarLaminas(e.target.files); e.target.value = ''; }} />
              </>
            )}
          </div>
          {p.status === 'APROVACAO' && (
            <div className="painel-caixa" style={{ display: 'grid', gap: 'var(--espaco-2)' }}>
              <p style={{ margin: 0 }}><span className="etiqueta alerta">Aguardando o cliente</span></p>
              <code style={{ overflowWrap: 'anywhere', fontSize: 13 }}>{link}</code>
              <div className="acoes-linha">
                {p.clienteTelefone && <a className="botao pequeno principal" href={linkWhatsapp(p.clienteTelefone, msgAprovacao)} target="_blank" rel="noreferrer">Enviar pelo WhatsApp</a>}
                <a className="botao pequeno" href={link} target="_blank" rel="noreferrer">Ver como o cliente</a>
              </div>
            </div>
          )}
          {p.aprovacao === 'APROVADO' && <p className="aviso sucesso" style={{ margin: 0 }}>Aprovado pelo cliente em {dataHora(p.aprovacaoEm)}.{p.comentarioCliente ? ` “${p.comentarioCliente}”` : ''}</p>}
          {p.aprovacao === 'AJUSTES' && (
            <p className="aviso" style={{ margin: 0, borderColor: 'var(--alerta)' }}>
              O cliente pediu ajustes em {dataHora(p.aprovacaoEm)}.{p.comentarioCliente ? ` “${p.comentarioCliente}”` : ''} Veja os comentários nas lâminas e envie a nova versão.
            </p>
          )}
          {p.laminas.length === 0
            ? <p className="vazio" style={{ margin: 0 }}>Envie as lâminas exportadas do seu programa de diagramação (uma imagem por lâmina, na ordem do nome do arquivo).</p>
            : (
              <div className="pd-laminas">
                {p.laminas.map((l) => (
                  <figure key={l.id} className={l.comentario ? 'com-comentario' : ''}>
                    <img src={urlPainel(`/produtos/pedidos/${p.id}/laminas/${l.id}/mini`)} alt={`Lâmina ${l.ordem}`} loading="lazy" />
                    <figcaption>
                      <span className="legenda">LÂMINA {l.ordem}{l.nome ? ` · ${l.nome}` : ''}</span>
                      {l.comentario && <span className="pd-comentario">“{l.comentario}”</span>}
                      {podeEnviarLaminas && p.aprovacao == null && <button className="botao-link legenda" onClick={() => apagarLamina(l.id)}>Tirar</button>}
                    </figcaption>
                  </figure>
                ))}
              </div>
            )}
          {p.versoesAnteriores.length > 0 && (
            <details className="pd-historico">
              <summary className="legenda">COMENTÁRIOS DAS VERSÕES ANTERIORES ({p.versoesAnteriores.length})</summary>
              <ul className="lista-simples">
                {p.versoesAnteriores.map((l) => <li key={l.id}><b>V{l.versao} · lâmina {l.ordem}:</b> {l.comentario}</li>)}
              </ul>
            </details>
          )}
        </section>
      )}

      <BaixaMaterial pedidoId={p.id} />
    </>
  );
}

/** Dar baixa de material usado neste pedido (caixa, pendrive…). */
function BaixaMaterial({ pedidoId }: { pedidoId: number }) {
  const { dados: materiais, recarregar } = useDados<Material[]>('/estoque/materiais');
  const [aberto, setAberto] = useState(false);
  const [erro, setErro] = useState('');
  const [ok, setOk] = useState('');
  const ativos = (materiais ?? []).filter((m) => m.ativo);
  if (!materiais || ativos.length === 0) return null;
  async function baixar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setErro('');
    try {
      const m = await api<Material>('/estoque/movimentos', {
        corpo: { materialId: Number(f.get('material')), tipo: 'SAIDA', quantidade: numeroBr(f.get('qtd')), motivo: `Pedido ${pedidoId}`, pedidoId },
      });
      setOk(`Baixa feita. Restam ${qtd(m.quantidade)} ${m.unidade} de ${m.nome}.`);
      setAberto(false);
      recarregar();
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível dar baixa.');
    }
  }
  return (
    <section className="secao">
      <div className="acoes-linha">
        <button className="botao pequeno" onClick={() => { setAberto(!aberto); setOk(''); }} aria-expanded={aberto}>Usar material do estoque</button>
        {ok && <span className="legenda" style={{ color: 'var(--sucesso)' }}>{ok.toUpperCase()}</span>}
      </div>
      {aberto && (
        <form className="painel-caixa grade-form duas anima-entrada" onSubmit={baixar}>
          <label className="campo"><span>Material</span>
            <select id="bm-material" name="material">{ativos.map((m) => <option key={m.id} value={m.id}>{m.nome} ({qtd(m.quantidade)} {m.unidade})</option>)}</select>
          </label>
          <label className="campo"><span>Quantidade</span><input id="bm-qtd" name="qtd" required inputMode="decimal" defaultValue="1" /></label>
          <Erro msg={erro} />
          <div className="acoes-linha inteira"><button className="botao pequeno principal">Dar baixa</button></div>
        </form>
      )}
    </section>
  );
}
