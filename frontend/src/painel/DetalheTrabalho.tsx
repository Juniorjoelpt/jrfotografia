import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ErroApi, imagemPainel, sessaoAtual, urlPainel } from '../api';
import type { Estudio, Foto, MeioPagamento, Parcela, StatusTrabalho, TrabalhoDetalhe } from '../tipos';
import { data, dataHora, DURACOES, duracaoTexto, ETAPAS, linkWhatsapp, moeda, NOMES_MEIO, NOMES_PARCELA, primeiroNome, telefone, urlAreaCliente, urlGaleria } from '../util';
import { ContratoTrabalho } from './AssinaturaPainel';
import { PesquisaTrabalho, TrabalhoEquipe } from './TrabalhoEquipe';
import { ProdutosDoTrabalho } from './Produtos';
import { Erro, Status, useDados } from './comum';
import { Imagem } from '../componentes/Movimento';

export function DetalheTrabalho() {
  const { id } = useParams();
  const admin = sessaoAtual()?.perfil === 'ADMIN';
  const { dados: t, setDados, erro: erroCarga, recarregar } = useDados<TrabalhoDetalhe>(`/trabalhos/${id}`);
  const { dados: fotos, recarregar: recarregarFotos } = useDados<Foto[]>(`/trabalhos/${id}/fotos`);
  const { dados: estudio } = useDados<Estudio>('/estudio');
  const [erro, setErro] = useState('');
  const [aviso, setAviso] = useState('');
  const [ocupado, setOcupado] = useState('');
  const [senha, setSenha] = useState<string | null>(null);
  const [confirmarCancelar, setConfirmarCancelar] = useState(false);

  const [remarcando, setRemarcando] = useState(false);
  const [soEscolhidas, setSoEscolhidas] = useState(true);
  const [copiado, setCopiado] = useState(false);
  const [confirmarReabrir, setConfirmarReabrir] = useState(false);
  if (!t) return <Erro msg={erroCarga} />;
  const r = t.resumo;
  const nome = primeiroNome(t.cliente.nome);
  const exigeSelecao = !!t.pacote?.qtdFotos;
  const etapa = ETAPAS.indexOf(r.status);

  async function executar(rotulo: string, acao: () => Promise<void>) {
    setOcupado(rotulo);
    setErro('');
    setAviso('');
    try { await acao(); } catch (e) { setErro(e instanceof ErroApi ? e.message : 'Não foi possível concluir.'); } finally { setOcupado(''); }
  }

  const mudarStatus = (status: StatusTrabalho) => executar(status, async () => {
    setDados(await api<TrabalhoDetalhe>(`/trabalhos/${id}/status`, { metodo: 'PATCH', corpo: { status } }));
  });

  const enviar = (tipo: 'fotos' | 'editadas', arquivos: FileList | null) => {
    if (!arquivos?.length) return;
    const lista = Array.from(arquivos);
    executar(tipo, async () => {
      // Lotes de 10 para não estourar o limite de envio e mostrar o progresso.
      let recebidas = 0;
      const semPar: string[] = [];
      for (let i = 0; i < lista.length; i += 10) {
        setAviso(`Enviando ${Math.min(i + 10, lista.length)} de ${lista.length}…`);
        const fd = new FormData();
        lista.slice(i, i + 10).forEach((a) => fd.append('arquivos', a));
        const res = await api<{ recebidas: number; semCorrespondencia: string[] } | Foto[]>(`/trabalhos/${id}/${tipo}`, { arquivos: fd });
        if (Array.isArray(res)) recebidas += res.length;
        else { recebidas += res.recebidas; semPar.push(...res.semCorrespondencia); }
      }
      setAviso(tipo === 'editadas'
        ? `${recebidas} editadas recebidas.${semPar.length ? ` Sem foto de mesmo nome: ${semPar.join(', ')}.` : ''}`
        : `${recebidas} ${recebidas === 1 ? 'foto enviada' : 'fotos enviadas'}.`);
      await Promise.all([recarregar(), recarregarFotos()]);
    });
  };

  const reabrir = () => executar('reabrir', async () => {
    setDados(await api<TrabalhoDetalhe>(`/trabalhos/${id}/reabrir-selecao`, { metodo: 'POST' }));
    setConfirmarReabrir(false);
    setAviso('Seleção reaberta. As fotos já escolhidas continuam marcadas; avise o cliente para escolher as novas.');
    await recarregarFotos();
  });

  const gerarSenha = () => executar('senha', async () => {
    const s = await api<{ senha: string }>(`/trabalhos/${id}/senha-galeria`, { corpo: {} });
    setSenha(s.senha);
    await recarregar();
  });

  const pagar = (p: Parcela, forma: MeioPagamento) => executar(`p${p.id}`, async () => {
    await api(`/parcelas/${p.id}/pagamento`, { corpo: { forma } });
    await recarregar();
  });
  const desfazer = (p: Parcela) => executar(`p${p.id}`, async () => {
    await api(`/parcelas/${p.id}/pagamento`, { metodo: 'DELETE' });
    await recarregar();
  });
  const apagarFoto = (f: Foto) => executar(`f${f.id}`, async () => {
    await api(`/trabalhos/${id}/fotos/${f.id}`, { metodo: 'DELETE' });
    await Promise.all([recarregar(), recarregarFotos()]);
  });

  const link = urlGaleria(t.galeriaToken);
  const msgGaleria = r.status === 'ENTREGUE'
    ? `Olá, ${nome}! Suas fotos editadas estão prontas 📸\n\nGaleria: ${link}\nSenha: ${senha}\n\n${t.galeriaExpiraEm ? `Ela fica disponível até ${data(t.galeriaExpiraEm)}.` : ''}`
    : `Olá, ${nome}! As fotos do seu ensaio já estão na galeria para você escolher as suas favoritas.\n\nGaleria: ${link}\nSenha: ${senha}\n\n${t.pacote?.qtdFotos ? `Seu pacote inclui ${t.pacote.qtdFotos} fotos${t.pacote.precoFotoExtra ? `; cada foto extra custa ${moeda(t.pacote.precoFotoExtra)}` : ''}.` : ''}`;
  const msgLembrete = `Olá, ${nome}! Passando para lembrar do seu ensaio ${r.dataTrabalho ? `em ${dataHora(r.dataTrabalho)}` : ''}${r.localTrabalho ? `, em ${r.localTrabalho}` : ''}. Qualquer dúvida, é só chamar.`;

  return (
    <>
      <div className="cabeca-pagina">
        <div>
          <span className="rotulo">{t.pacote?.nome ?? r.tipo ?? 'Trabalho'}</span>
          <h1 className="titulo">{t.cliente.nome}</h1>
          <span className="suave">{dataHora(r.dataTrabalho)}{r.dataTrabalho ? ` · ${duracaoTexto(t.duracaoMin)}` : ''}{r.localTrabalho ? ` · ${r.localTrabalho}` : ''}</span>
        </div>
        <div className="cabeca-lado">
          {r.atrasado ? <span className="etiqueta erro">Atrasado</span> : <Status s={r.status} />}
          {r.prazoEntrega && r.status !== 'ENTREGUE' && <span className="legenda">ENTREGA ATÉ {data(r.prazoEntrega)}</span>}
        </div>
      </div>

      {r.status !== 'CANCELADO' && (
        <div className="etapas" aria-label={`Etapa ${etapa + 1} de ${ETAPAS.length}`}>
          {ETAPAS.filter((e) => exigeSelecao || (e !== 'SELECAO' && e !== 'EDICAO')).map((e) => (
            <span key={e} className={ETAPAS.indexOf(e) <= etapa ? 'feita' : ''} />
          ))}
        </div>
      )}

      <div className="acoes-linha">
        {t.cliente.telefone && (
          <a className="botao pequeno" href={linkWhatsapp(t.cliente.telefone, `Olá, ${nome}!`)} target="_blank" rel="noreferrer">
            WhatsApp · {telefone(t.cliente.telefone)}
          </a>
        )}
        {t.cliente.telefone && r.status === 'AGENDADO' && (
          <a className="botao pequeno" href={linkWhatsapp(t.cliente.telefone, msgLembrete)} target="_blank" rel="noreferrer">Enviar lembrete do ensaio</a>
        )}
        {(r.status === 'AGENDADO' || r.status === 'FOTOGRAFADO') && (
          <button className="botao pequeno" aria-expanded={remarcando} onClick={() => setRemarcando((v) => !v)}>{r.dataTrabalho ? 'Remarcar' : 'Marcar data'}</button>
        )}
        {t.areaCliente && t.cliente.telefone && r.status !== 'CANCELADO' && (
          <a className="botao pequeno" target="_blank" rel="noreferrer" href={linkWhatsapp(t.cliente.telefone,
            `Olá, ${nome}! Pelo link abaixo você acompanha seu ensaio, vê os pagamentos e, se precisar, remarca ou cancela dentro das regras do contrato:\n${urlAreaCliente(t.areaCliente)}`)}>
            Enviar área da cliente
          </a>
        )}
        {admin && <a className="botao pequeno" href={urlPainel(`/trabalhos/${id}/contrato.pdf`)} target="_blank" rel="noreferrer">Contrato (PDF)</a>}
        {admin && t.orcamentoId && <Link className="botao pequeno" to={`/painel/orcamentos/${t.orcamentoId}`}>Orçamento</Link>}
      </div>
      {t.conflitos.length > 0 && (
        <div className="aviso" role="status" style={{ margin: 0, borderColor: 'var(--alerta)', color: 'var(--alerta)' }}>
          {t.conflitos.map((c) => <div key={c}>Choque de horário: {c}</div>)}
        </div>
      )}
      {remarcando && <Remarcar t={t} aoSalvar={(n) => { setDados(n); setRemarcando(false); }} />}

      <Erro msg={erro} />
      {aviso && <p className="aviso sucesso" role="status" style={{ margin: 0 }}>{aviso}</p>}

      {/* Próximo passo, conforme a etapa */}
      <section className="painel-caixa secao">
        <h2 className="chamada">Próximo passo</h2>
        {(r.status === 'AGENDADO' || r.status === 'FOTOGRAFADO') && (
          <>
            <Envio rotulo={exigeSelecao ? 'Enviar fotos para a seleção' : 'Enviar as fotos editadas'}
              ajuda={exigeSelecao
                ? 'Exporte do Lightroom em JPEG. O sistema cria prévias com a marca d\'água JR para o cliente escolher.'
                : 'Este pacote entrega todas as fotos. Envie as editadas em JPEG e depois marque como entregue.'}
              ocupado={ocupado === 'fotos'} aoEscolher={(f) => enviar('fotos', f)} />
            {t.totalFotos > 0 && (exigeSelecao
              ? <button className="botao principal" disabled={!!ocupado} onClick={() => mudarStatus('SELECAO')}>Abrir seleção para o cliente</button>
              : <button className="botao principal" disabled={!!ocupado} onClick={() => mudarStatus('ENTREGUE')}>Entregar ao cliente</button>)}
          </>
        )}
        {r.status === 'SELECAO' && (
          <p style={{ margin: 0 }}>Aguardando {nome} escolher. {t.selecionadas} de {t.pacote?.qtdFotos} fotos marcadas até agora.</p>
        )}
        {r.status === 'EDICAO' && (
          <>
            <p style={{ margin: 0 }}>Seleção recebida: <strong>{t.selecionadas} fotos</strong>. {t.editadas} de {t.selecionadas} editadas enviadas.</p>
            <Envio rotulo="Enviar fotos editadas" ajuda="Use o mesmo nome de arquivo da foto original (ex.: IMG_0231.jpg) para o sistema associar."
              ocupado={ocupado === 'editadas'} aoEscolher={(f) => enviar('editadas', f)} />
            <button className="botao principal" disabled={!!ocupado || t.editadas < t.selecionadas} onClick={() => mudarStatus('ENTREGUE')}>
              Entregar ao cliente
            </button>
          </>
        )}
        {r.status === 'ENTREGUE' && (
          <p style={{ margin: 0 }}>Entregue em {data(t.entregueEm)}. Galeria disponível até {data(t.galeriaExpiraEm)}.
            {' '}{t.downloadLiberado ? 'Download liberado.' : t.motivoDownload}</p>
        )}
        {exigeSelecao && (r.status === 'EDICAO' || r.status === 'ENTREGUE') && (confirmarReabrir ? (
          <div className="aviso anima-entrada" role="alertdialog" style={{ margin: 0, display: 'grid', gap: 'var(--espaco-2)', borderColor: 'var(--alerta)' }}>
            <span>
              Reabrir a seleção? O cliente volta a escolher na galeria, com as fotos já escolhidas marcadas.
              {r.status === 'ENTREGUE' ? ' A entrega é desfeita até ele concluir de novo e você reenviar as editadas.' : ''}
              {' '}Ao concluir, as fotos extras são recalculadas (o que já foi pago não é cobrado de novo).
            </span>
            <div className="acoes-linha">
              <button className="botao principal pequeno" disabled={!!ocupado} onClick={reabrir}>{ocupado === 'reabrir' ? 'Reabrindo…' : 'Sim, reabrir seleção'}</button>
              <button className="botao pequeno" onClick={() => setConfirmarReabrir(false)}>Voltar</button>
            </div>
          </div>
        ) : (
          <div><button className="botao pequeno" onClick={() => setConfirmarReabrir(true)}>Reabrir seleção</button></div>
        ))}
        {r.status === 'CANCELADO' && (
          <p className="suave" style={{ margin: 0 }}>
            Trabalho cancelado{t.canceladoEm ? ` em ${dataHora(t.canceladoEm)}` : ''}{t.canceladoPor === 'CLIENTE' ? ' pela cliente, pela área da cliente' : ''}.
            {t.motivoCancelamento ? ` Motivo: ${t.motivoCancelamento}` : ''}
          </p>
        )}
      </section>

      {admin && <ContratoTrabalho trabalhoId={Number(id)} cliente={t.cliente.nome} telefoneCliente={t.cliente.telefone ?? undefined} cancelado={r.status === 'CANCELADO'} />}

      {/* Galeria do cliente */}
      {(r.status === 'SELECAO' || r.status === 'EDICAO' || r.status === 'ENTREGUE') && (
        <section className="secao">
          <h2 className="chamada">Galeria do cliente</h2>
          <div className="painel-caixa" style={{ display: 'grid', gap: 'var(--espaco-3)' }}>
            <code style={{ overflowWrap: 'anywhere', fontSize: 13 }}>{link}</code>
            {senha
              ? <p style={{ margin: 0 }}>Senha nova: <strong className="num" style={{ fontSize: 20, letterSpacing: '.2em' }}>{senha}</strong></p>
              : <p className="legenda" style={{ margin: 0 }}>{t.galeriaComSenha ? 'A SENHA SÓ APARECE QUANDO É GERADA. GERE UMA NOVA PARA REENVIAR.' : 'GERE A SENHA PARA ENVIAR AO CLIENTE.'}</p>}
            <div className="acoes-linha">
              <button className="botao pequeno" disabled={!!ocupado} onClick={gerarSenha}>{senha ? 'Gerar outra senha' : 'Gerar senha'}</button>
              {senha && t.cliente.telefone && (
                <a className="botao principal pequeno" href={linkWhatsapp(t.cliente.telefone, msgGaleria)} target="_blank" rel="noreferrer">Enviar pelo WhatsApp</a>
              )}
              <a className="botao pequeno" href={link} target="_blank" rel="noreferrer">Abrir galeria</a>
            </div>
          </div>
        </section>
      )}

      {/* Fotos */}
      {fotos && fotos.length > 0 && (() => {
        const escolhidas = fotos.filter((f) => f.selecionada);
        const filtrar = exigeSelecao && escolhidas.length > 0 && soEscolhidas;
        const lista = filtrar ? escolhidas : fotos;
        // Nome sem extensão: casa com o RAW e com o JPEG no filtro do Lightroom.
        const semExtensao = (n: string) => n.replace(/\.[^.]+$/, '');
        const copiarNomes = async () => {
          const texto = lista.map((f) => semExtensao(f.nomeOriginal)).join(', ');
          try { await navigator.clipboard.writeText(texto); } catch {
            const ta = document.createElement('textarea'); ta.value = texto; document.body.appendChild(ta); ta.select();
            try { document.execCommand('copy'); } catch { /* sem área de transferência */ } ta.remove();
          }
          setCopiado(true);
          setTimeout(() => setCopiado(false), 2500);
        };
        return (
          <section className="secao">
            <div className="cabeca-pagina">
              <h2 className="chamada" style={{ margin: 0 }}>Fotos ({fotos.length}{exigeSelecao ? ` · ${escolhidas.length} escolhidas` : ''})</h2>
              <div className="acoes-linha">
                {exigeSelecao && escolhidas.length > 0 && (
                  <div className="abas" role="group" aria-label="Mostrar">
                    <button className="botao pequeno" aria-pressed={soEscolhidas} onClick={() => { setSoEscolhidas(true); setCopiado(false); }}>Escolhidas</button>
                    <button className="botao pequeno" aria-pressed={!soEscolhidas} onClick={() => { setSoEscolhidas(false); setCopiado(false); }}>Todas</button>
                  </div>
                )}
                <button className="botao pequeno principal" onClick={copiarNomes}>
                  {copiado ? `${lista.length} nomes copiados!` : `Copiar nomes ${filtrar ? 'das escolhidas' : ''} p/ Lightroom`}
                </button>
              </div>
            </div>
            <p className="legenda" style={{ margin: 0 }}>
              NO LIGHTROOM: BIBLIOTECA › FILTRO DE BIBLIOTECA › TEXTO › NOME DO ARQUIVO › CONTÉM, E COLE OS NOMES. DEPOIS SELECIONE TUDO (CTRL+A).
            </p>
            <div className="miniaturas com-nome escalonado">
              {lista.map((f) => (
                <figure key={f.id} className={f.selecionada && exigeSelecao ? 'escolhida' : ''} title={f.nomeOriginal}>
                  <div className="mini-foto">
                    <Imagem src={imagemPainel(t.resumo.id, f.id, 'miniatura', f.editada)} alt={f.nomeOriginal} loading="lazy" />
                    {(f.editada || f.comentario) && <span className="marca">{f.editada ? 'editada' : ''}{f.comentario ? ' 💬' : ''}</span>}
                    {(r.status === 'AGENDADO' || r.status === 'FOTOGRAFADO') && (
                      <button className="apagar" aria-label={`Apagar ${f.nomeOriginal}`} disabled={!!ocupado} onClick={() => apagarFoto(f)}>×</button>
                    )}
                  </div>
                  <figcaption>{f.nomeOriginal}</figcaption>
                </figure>
              ))}
            </div>
            {lista.some((f) => f.comentario) && (
              <ul className="lista-simples legenda" style={{ display: 'grid', gap: 4 }}>
                {lista.filter((f) => f.comentario).map((f) => <li key={f.id}>{f.nomeOriginal}: {f.comentario}</li>)}
              </ul>
            )}
          </section>
        );
      })()}

      {admin && r.status === 'ENTREGUE' && <PesquisaTrabalho trabalhoId={r.id} cliente={t.cliente.nome} telefoneCliente={t.cliente.telefone ?? undefined} />}

      {admin && r.status !== 'CANCELADO' && <ProdutosDoTrabalho trabalhoId={r.id} />}

      {r.status !== 'CANCELADO' && <TrabalhoEquipe trabalhoId={r.id} admin={admin} valorTotal={r.valorTotal} />}

      {/* Pagamentos */}
      {admin && (
        <section className="secao">
          <h2 className="chamada">Pagamentos · {moeda(r.valorTotal)} · {t.formaPagamento === 'AVISTA' ? 'à vista' : 'parcelado'}</h2>
          <div className="linhas">
            {t.parcelas.map((p) => (
              <div key={p.id} className="linha-item">
                <span className="principal">{p.descricao}{p.descricao !== NOMES_PARCELA[p.tipo] && p.tipo !== 'PARCELA' ? <span className="legenda"> · {NOMES_PARCELA[p.tipo]}</span> : null}</span>
                <span className="lado num">{moeda(p.valor)}</span>
                <span className="legenda" style={{ gridColumn: '1 / -1', color: p.pagoEm ? 'var(--sucesso)' : undefined }}>
                  {p.pagoEm ? `PAGO EM ${data(p.pagoEm)}${p.forma ? ` · ${NOMES_MEIO[p.forma].toUpperCase()}` : ''}` : `VENCE ${data(p.vencimento)}`}
                </span>
                <span className="acoes-linha" style={{ gridColumn: '1 / -1' }}>
                  {p.pagoEm
                    ? <>
                      <a className="botao pequeno" href={urlPainel(`/parcelas/${p.id}/recibo.pdf`)} target="_blank" rel="noreferrer">Recibo</a>
                      <button className="botao-link legenda" disabled={!!ocupado} onClick={() => desfazer(p)}>Desfazer</button>
                    </>
                    : <>
                      {t.cliente.telefone && (
                        <a className="botao pequeno" target="_blank" rel="noreferrer" href={linkWhatsapp(t.cliente.telefone,
                          `Olá, ${nome}! ${p.descricao} de ${moeda(p.valor)}${p.vencimento ? `, com vencimento em ${data(p.vencimento)}` : ''}.${estudio?.chavePix ? `\n\nChave Pix: ${estudio.chavePix}` : ''}\n\nAssim que pagar, me mande o comprovante por aqui.`)}>Cobrar</a>
                      )}
                      <button className="botao pequeno" disabled={!!ocupado} onClick={() => pagar(p, 'PIX')}>Pago no Pix</button>
                      <button className="botao pequeno" disabled={!!ocupado} onClick={() => pagar(p, 'DINHEIRO')}>Dinheiro</button>
                    </>}
                </span>
              </div>
            ))}
            {t.parcelas.length === 0 && <p className="vazio" style={{ margin: 0 }}>Sem parcelas.</p>}
          </div>
        </section>
      )}

      {admin && r.status !== 'ENTREGUE' && r.status !== 'CANCELADO' && (
        <div className="acoes-linha">
          {confirmarCancelar
            ? <>
              <span className="suave">Cancelar este trabalho? A galeria deixa de abrir.</span>
              <button className="botao pequeno" onClick={() => setConfirmarCancelar(false)}>Voltar</button>
              <button className="botao pequeno" style={{ borderColor: 'var(--erro)', color: 'var(--erro)' }} onClick={() => mudarStatus('CANCELADO')}>Cancelar trabalho</button>
            </>
            : <button className="botao-link legenda" style={{ color: 'var(--tinta-suave)' }} onClick={() => setConfirmarCancelar(true)}>Cancelar trabalho</button>}
        </div>
      )}
    </>
  );
}

function Envio({ rotulo, ajuda, ocupado, aoEscolher }: { rotulo: string; ajuda: string; ocupado: boolean; aoEscolher: (f: FileList | null) => void }) {
  const ref = useRef<HTMLInputElement>(null);
  return (
    <div className="envio">
      <button className="botao" disabled={ocupado} onClick={() => ref.current?.click()}>{ocupado ? 'Enviando… mantenha a tela aberta' : rotulo}</button>
      <span className="legenda">{ajuda.toUpperCase()}</span>
      <input ref={ref} type="file" accept="image/jpeg,image/png" multiple hidden
        onChange={(e) => { aoEscolher(e.target.files); e.target.value = ''; }} />
    </div>
  );
}

/** Muda data, hora, duração e local, avisando se bate com outro ensaio ou bloqueio. */
function Remarcar({ t, aoSalvar }: { t: TrabalhoDetalhe; aoSalvar: (t: TrabalhoDetalhe) => void }) {
  const r = t.resumo;
  const [quando, setQuando] = useState(r.dataTrabalho?.slice(0, 16) ?? '');
  const [duracao, setDuracao] = useState(String(t.duracaoMin));
  const [local, setLocal] = useState(r.localTrabalho ?? '');
  const [avisos, setAvisos] = useState<string[]>([]);
  const [erro, setErro] = useState('');

  useEffect(() => {
    if (!quando) { setAvisos([]); return; }
    const h = setTimeout(() => {
      api<{ avisos: string[] }>(`/agenda/conflitos?inicio=${encodeURIComponent(`${quando}:00`)}&duracaoMin=${duracao}&ignorar=${r.id}`)
        .then((x) => setAvisos(x.avisos)).catch(() => setAvisos([]));
    }, 300);
    return () => clearTimeout(h);
  }, [quando, duracao, r.id]);

  async function salvar(ev: FormEvent) {
    ev.preventDefault();
    try {
      aoSalvar(await api<TrabalhoDetalhe>(`/trabalhos/${r.id}`, {
        metodo: 'PUT',
        corpo: { titulo: r.titulo, tipo: r.tipo, dataTrabalho: quando ? `${quando}:00` : null, localTrabalho: local || null,
          prazoEntrega: null, observacoes: t.observacoes ?? null, duracaoMin: Number(duracao) },
      }));
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível salvar.');
    }
  }

  return (
    <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar}>
      <label className="campo"><span>Data e hora</span><input id="rm-data" type="datetime-local" value={quando} onChange={(e) => setQuando(e.target.value)} /></label>
      <label className="campo"><span>Duração</span>
        <select id="rm-dur" value={duracao} onChange={(e) => setDuracao(e.target.value)}>
          {DURACOES.map((d) => <option key={d} value={d}>{duracaoTexto(d)}</option>)}
        </select>
      </label>
      <label className="campo inteira"><span>Local</span><input id="rm-local" value={local} onChange={(e) => setLocal(e.target.value)} /></label>
      {avisos.length > 0 && <div className="aviso inteira" role="status" style={{ margin: 0, borderColor: 'var(--alerta)' }}>{avisos.map((a) => <div key={a}>{a}</div>)}</div>}
      <Erro msg={erro} />
      <button className="botao principal inteira">Salvar</button>
    </form>
  );
}
