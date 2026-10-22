import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, ErroApi } from '../api';
import type { CobrancaPainel, ConfigPagBank } from '../tipos';
import { dataHora, moeda } from '../util';
import { Erro, useDados } from './comum';

const STATUS: Record<CobrancaPainel['status'], { nome: string; classe: string }> = {
  AGUARDANDO: { nome: 'Aguardando', classe: '' },
  PAGA: { nome: 'Pago', classe: ' sucesso' },
  EXPIRADA: { nome: 'Venceu', classe: '' },
  CANCELADA: { nome: 'Baixa manual', classe: '' },
};

/** Ajustes › Pagamentos: Pix com QR Code e confirmação automática pelo PagBank. */
export function AjustesPagamentos() {
  const { dados, erro, setDados } = useDados<ConfigPagBank>('/estudio/pagbank');
  const { dados: lista, recarregar } = useDados<CobrancaPainel[]>('/estudio/pagbank/cobrancas');
  const [falha, setFalha] = useState('');
  const [salvo, setSalvo] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [teste, setTeste] = useState<{ ok: boolean; mensagem: string } | null>(null);
  const [verificando, setVerificando] = useState<number | null>(null);
  const recarregarRef = useRef(recarregar);
  recarregarRef.current = recarregar;

  // A lista se atualiza sozinha (um Pix pago aparece sem recarregar a página).
  useEffect(() => {
    const i = setInterval(() => { if (document.visibilityState === 'visible') recarregarRef.current(); }, 20000);
    return () => clearInterval(i);
  }, []);

  if (!dados) return <section className="secao"><Erro msg={erro} /></section>;

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const f = new FormData(form);
    setFalha('');
    setSalvo(false);
    setTeste(null);
    setSalvando(true);
    try {
      setDados(await api<ConfigPagBank>('/estudio/pagbank', {
        metodo: 'PUT', corpo: { ativo: f.get('ativo') === 'on', token: f.get('token') || null, sandbox: f.get('ambiente') === 'sandbox', chavePix: String(f.get('chavePix') ?? '') },
      }));
      setSalvo(true);
      form.querySelector<HTMLInputElement>('input[name=token]')!.value = '';
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  async function testar() {
    setTeste(null);
    try { setTeste(await api<{ ok: boolean; mensagem: string }>('/estudio/pagbank/teste', { corpo: {} })); } catch (ex) {
      setTeste({ ok: false, mensagem: ex instanceof ErroApi ? ex.message : 'Não foi possível testar.' });
    }
  }

  async function verificar(c: CobrancaPainel) {
    setVerificando(c.id);
    try { await api(`/estudio/pagbank/cobrancas/${c.id}/verificacao`, { corpo: {} }); recarregar(); } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível consultar.');
    } finally { setVerificando(null); }
  }

  return (
    <section className="secao">
      <form className="painel-caixa grade-form duas" onSubmit={salvar} onChange={() => setSalvo(false)}>
        <label className="campo inteira"><span>Chave Pix (aleatória ou e-mail)</span>
          <input id="pg-pix" name="chavePix" maxLength={120} defaultValue={dados.chavePix ?? ''} /></label>
        <p className="legenda inteira" style={{ margin: 0 }}>
          SEM O PIX AUTOMÁTICO, A CHAVE VAI NAS COBRANÇAS E A CLIENTE MANDA O COMPROVANTE. PREFIRA UMA CHAVE ALEATÓRIA OU DE E-MAIL: NÃO EXPÕE O SEU CPF.
        </p>
        <label className="marcar inteira"><input id="pg-ativo" type="checkbox" name="ativo" defaultChecked={dados.ativo} /> <strong>Pix automático pelo PagBank</strong></label>
        <p className="suave inteira" style={{ margin: 0 }}>
          A cliente paga o sinal (e as parcelas, na área dela) por um QR Code com o valor exato. Quando o banco confirma,
          a reserva é confirmada sozinha, o pagamento é baixado no financeiro e ela recebe o link da área. Sem conferir comprovante.
        </p>
        <details className="aviso inteira">
          <summary style={{ cursor: 'pointer' }}><strong>Onde pegar o token</strong></summary>
          <ol style={{ margin: 'var(--espaco-2) 0 0', paddingLeft: 20, display: 'grid', gap: 4 }}>
            <li><b>Produção (dinheiro de verdade):</b> entre no PagBank pelo computador › menu <b>Vendas</b> › <b>Integrações</b> › <b>Gerar token</b>. O token chega no e-mail da conta.</li>
            <li><b>Testes (sandbox):</b> crie uma conta no Portal do Desenvolvedor do PagBank › aba <b>Tokens</b> › copie o token. Marque “Testes” abaixo.</li>
            <li>A conta PagBank precisa ter uma <b>chave Pix cadastrada</b> para gerar QR Code.</li>
          </ol>
        </details>
        <label className="campo inteira"><span>Token do PagBank {dados.temToken ? '(guardado; cole só para trocar)' : ''}</span>
          <input id="pg-token" name="token" type="password" autoComplete="off" maxLength={400} placeholder={dados.temToken ? '••••••••••••••••' : 'cole o token aqui'} /></label>
        <fieldset className="inteira" style={{ border: 0, padding: 0, margin: 0, display: 'flex', gap: 'var(--espaco-4)', flexWrap: 'wrap' }}>
          <legend className="legenda" style={{ marginBottom: 6 }}>AMBIENTE</legend>
          <label className="marcar"><input type="radio" name="ambiente" value="producao" defaultChecked={!dados.sandbox} /> Produção (pagamentos reais)</label>
          <label className="marcar"><input type="radio" name="ambiente" value="sandbox" defaultChecked={dados.sandbox} /> Testes (sandbox)</label>
        </fieldset>
        <p className="legenda inteira" style={{ margin: 0 }}>
          AVISO DE PAGAMENTO: {dados.notificacaoPublica
            ? <>O PAGBANK AVISA EM {dados.urlNotificacao.toUpperCase()}</>
            : <>NO COMPUTADOR LOCAL O PAGBANK NÃO CONSEGUE AVISAR; O SISTEMA CONSULTA SOZINHO (NA HORA, COM A PÁGINA DA CLIENTE ABERTA, E A CADA 2 MINUTOS).</>}
        </p>
        <Erro msg={falha} />
        {salvo && <p className="aviso sucesso inteira" style={{ margin: 0 }}>Configuração salva.</p>}
        <div className="acoes-linha inteira">
          <button className="botao principal" disabled={salvando}>{salvando ? 'Conferindo o token…' : 'Salvar'}</button>
          <button type="button" className="botao" disabled={!dados.temToken} onClick={testar}>Testar conexão</button>
        </div>
        {teste && <p className={`aviso ${teste.ok ? 'sucesso' : 'erro'} inteira`} role="status" style={{ margin: 0 }}>{teste.mensagem}</p>}
      </form>

      <h2 className="subtitulo">Últimos Pix gerados</h2>
      {lista && (lista.length === 0 ? <p className="vazio" style={{ margin: 0 }}>Nenhum Pix gerado ainda.</p> : (
        <div className="linhas">
          {lista.map((c) => (
            <div key={c.id} className="linha-item">
              <span className="principal">{c.nome ? `${c.nome} · ` : ''}{c.descricao}</span>
              <span className="lado"><span className="num">{moeda(c.valor)}</span> <span className={`etiqueta${STATUS[c.status].classe}`}>{STATUS[c.status].nome}</span></span>
              <span className="legenda">
                {(c.pagoEm ? `PAGO ${dataHora(c.pagoEm)}` : `GERADO ${dataHora(c.criadoEm)}`).toUpperCase()} · {c.pedidoId}{c.sandbox ? ' · TESTE' : ''}
              </span>
              {c.status === 'AGUARDANDO' && (
                <span className="lado"><button className="botao-link" disabled={verificando === c.id} onClick={() => verificar(c)}>{verificando === c.id ? 'Consultando…' : 'Consultar agora'}</button></span>
              )}
              {c.erro && <span className="legenda" style={{ gridColumn: '1 / -1', color: 'var(--alerta)' }}>{c.erro}</span>}
            </div>
          ))}
        </div>
      ))}
    </section>
  );
}
