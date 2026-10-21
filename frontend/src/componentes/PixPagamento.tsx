import { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import type { PixCobranca } from '../tipos';
import { moeda } from '../util';
import './pix.css';

/** CPF com pontos e traço enquanto digita. */
function mascaraCpf(v: string) {
  const d = v.replace(/\D/g, '').slice(0, 11);
  return d.replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/(\d{3})\.(\d{3})\.(\d{3})(\d)/, '$1.$2.$3-$4');
}

function useRelogio(ativo: boolean) {
  const [agora, setAgora] = useState(Date.now());
  useEffect(() => {
    if (!ativo) return;
    const i = setInterval(() => setAgora(Date.now()), 1000);
    return () => clearInterval(i);
  }, [ativo]);
  return agora;
}

function resta(ate: string, agora: number) {
  const ms = new Date(ate).getTime() - agora;
  if (ms <= 0) return null;
  const h = Math.floor(ms / 3600000), m = Math.floor((ms % 3600000) / 60000), s = Math.floor((ms % 60000) / 1000);
  if (h >= 24) return `${Math.floor(h / 24)}d ${h % 24}h`;
  return h ? `${h}h ${String(m).padStart(2, '0')}min` : `${m}:${String(s).padStart(2, '0')}`;
}

interface Props {
  /** Cobrança atual (ou nenhuma ainda). */
  pix: PixCobranca | null | undefined;
  valor: number;
  /** Pedir o CPF (o PagBank exige para gerar o Pix). */
  pedirCpf: boolean;
  gerar: (cpf: string) => Promise<void>;
  /** Pergunta ao servidor se já foi pago (chamado a cada poucos segundos enquanto aguarda). */
  verificar: () => Promise<void>;
  rotuloBotao?: string;
}

/**
 * Pix com valor exato pelo PagBank: QR Code para ler no app do banco, código copia e cola,
 * e a página fica de olho sozinha: pagou, a confirmação aparece sem precisar enviar comprovante.
 */
export function PixPagamento({ pix, valor, pedirCpf, gerar, verificar, rotuloBotao }: Props) {
  const [cpf, setCpf] = useState('');
  const [qr, setQr] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState('');
  const [copiado, setCopiado] = useState(false);
  const aguardando = pix?.status === 'AGUARDANDO' && !!pix.texto;
  const agora = useRelogio(aguardando);
  const tempo = aguardando ? resta(pix!.expiraEm, agora) : null;
  const verificarRef = useRef(verificar);
  verificarRef.current = verificar;

  useEffect(() => {
    if (!aguardando || !pix?.texto) { setQr(''); return; }
    QRCode.toDataURL(pix.texto, { margin: 1, width: 560, errorCorrectionLevel: 'M', color: { dark: '#171716', light: '#ffffff' } })
      .then(setQr).catch(() => setQr(''));
  }, [aguardando, pix?.texto]);

  // Enquanto aguarda: pergunta a cada 5 s (só com a página à vista) e logo que ela volta a aparecer.
  useEffect(() => {
    if (!aguardando) return;
    let parado = false;
    const tick = () => { if (!parado && document.visibilityState === 'visible') verificarRef.current().catch(() => undefined); };
    const i = setInterval(tick, 5000);
    const aoVoltar = () => { if (document.visibilityState === 'visible') tick(); };
    document.addEventListener('visibilitychange', aoVoltar);
    return () => { parado = true; clearInterval(i); document.removeEventListener('visibilitychange', aoVoltar); };
  }, [aguardando, pix?.id]);

  const cpfDigitos = cpf.replace(/\D/g, '');
  const podeGerar = !pedirCpf || cpfDigitos.length === 11;

  async function gerarPix() {
    if (!podeGerar) { setErro('Informe o CPF de quem vai pagar (11 números).'); return; }
    setEnviando(true);
    setErro('');
    try { await gerar(cpfDigitos); } catch (e) { setErro(e instanceof Error ? e.message : 'Não foi possível gerar o Pix agora.'); } finally { setEnviando(false); }
  }

  async function copiar() {
    if (!pix?.texto) return;
    try { await navigator.clipboard.writeText(pix.texto); } catch {
      const t = document.createElement('textarea'); t.value = pix.texto; document.body.appendChild(t); t.select();
      try { document.execCommand('copy'); } catch { /* sem área de transferência */ } t.remove();
    }
    setCopiado(true);
    setTimeout(() => setCopiado(false), 2500);
  }

  if (pix?.status === 'PAGA') {
    return (
      <div className="pix-pago anima-entrada" role="status">
        <span className="pix-check" aria-hidden="true">✓</span>
        <div><strong>Pix recebido!</strong><span>{moeda(pix.valor)} confirmado pelo banco.</span></div>
      </div>
    );
  }

  if (!aguardando || !tempo) {
    return (
      <div className="pix-gerar">
        {pix && (pix.status === 'EXPIRADA' || !tempo) && <p className="suave" style={{ margin: 0 }}>O código anterior venceu. Gere um novo, é rapidinho.</p>}
        {pedirCpf && (
          <label className="campo">
            <span>CPF de quem vai pagar</span>
            <input inputMode="numeric" autoComplete="off" placeholder="000.000.000-00" value={cpf}
              onChange={(e) => setCpf(mascaraCpf(e.target.value))} onKeyDown={(e) => { if (e.key === 'Enter') gerarPix(); }} />
            <small className="suave">O PagBank pede o CPF para gerar o Pix. Não fica visível para ninguém.</small>
          </label>
        )}
        <button type="button" className="botao principal pix-botao" disabled={enviando || !podeGerar} onClick={gerarPix}>
          {enviando ? 'Gerando o Pix…' : rotuloBotao ?? `Gerar Pix de ${moeda(valor)}`}
        </button>
        {erro && <p className="pix-erro" role="alert">{erro}</p>}
      </div>
    );
  }

  return (
    <div className="pix-caixa anima-entrada">
      {pix!.sandbox && <p className="pix-teste">Ambiente de testes do PagBank: este Pix não cobra de verdade.</p>}
      <div className="pix-grade">
        <figure className="pix-qr">
          {qr ? <img src={qr} alt="QR Code do Pix" width={280} height={280} /> : <div className="esqueleto" style={{ width: 220, height: 220 }} />}
        </figure>
        <div className="pix-lado">
          <p className="pix-valor"><span className="rotulo">Valor exato</span><b className="num">{moeda(pix!.valor)}</b></p>
          <ol className="pix-passos">
            <li>Abra o app do seu banco e entre em <b>Pix</b>.</li>
            <li>Escolha <b>Ler QR Code</b> ou <b>Pix copia e cola</b>.</li>
            <li>Confira o valor e pague. Pronto!</li>
          </ol>
          <button type="button" className="botao principal pix-botao" onClick={copiar}>{copiado ? 'Código copiado!' : 'Copiar código Pix'}</button>
          <details className="pix-codigo">
            <summary>Ver o código</summary>
            <code>{pix!.texto}</code>
          </details>
        </div>
      </div>
      <p className="pix-espera" role="status" aria-live="polite">
        <span className="pix-pulso" aria-hidden="true" />
        <span className="pix-espera-texto">Aguardando o pagamento. A confirmação aparece aqui sozinha, sem precisar enviar comprovante.</span>
        <span className="pix-tempo num">Vale por {tempo}</span>
      </p>
    </div>
  );
}
