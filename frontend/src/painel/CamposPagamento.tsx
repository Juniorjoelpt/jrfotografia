import { useEffect, useState } from 'react';
import { api } from '../api';
import type { FormaPagamento } from '../tipos';
import { moeda, numeroBr } from '../util';

/**
 * Pagamento e choque de agenda, iguais no Novo trabalho, no editor de orçamento e no Remarcar.
 * Os valores são lidos com numeroBr: "1.250,50", "1250,50" e "1250.50" dão o mesmo número.
 */

/** O que sobra para as parcelas depois do sinal. */
export function restoDepoisDoSinal(total: number, forma: FormaPagamento, sinal: string) {
  return Math.max(0, total - (forma === 'PARCELADO' ? numeroBr(sinal) ?? 0 : 0));
}

/** Avisos de choque com outro ensaio ou dia bloqueado, conferidos enquanto a data é digitada. */
export function useConflitosAgenda(quando: string, duracaoMin: number | string, ignorarTrabalhoId?: number) {
  const [avisos, setAvisos] = useState<string[]>([]);
  useEffect(() => {
    if (!quando) { setAvisos([]); return; }
    const h = setTimeout(() => {
      const ignorar = ignorarTrabalhoId ? `&ignorar=${ignorarTrabalhoId}` : '';
      api<{ avisos: string[] }>(`/agenda/conflitos?inicio=${encodeURIComponent(`${quando}:00`)}&duracaoMin=${duracaoMin}${ignorar}`)
        .then((x) => setAvisos(x.avisos)).catch(() => setAvisos([]));
    }, 300);
    return () => clearTimeout(h);
  }, [quando, duracaoMin, ignorarTrabalhoId]);
  return avisos;
}

export function AvisoConflitos({ avisos, prefixo = '' }: { avisos: string[]; prefixo?: string }) {
  if (avisos.length === 0) return null;
  return (
    <div className="aviso inteira" role="status" style={{ margin: 0, borderColor: 'var(--alerta)' }}>
      {avisos.map((a) => <div key={a}>{prefixo}{a}</div>)}
    </div>
  );
}

/** Forma de pagamento, sinal e parcelas. Os ids seguem o prefixo da tela (nt-forma, or-sinal…). */
export function CamposPagamento({ prefixo, total, forma, setForma, sinal, setSinal, parcelas, setParcelas, rotuloForma = 'Pagamento' }: {
  prefixo: string; total: number; rotuloForma?: string;
  forma: FormaPagamento; setForma: (f: FormaPagamento) => void;
  sinal: string; setSinal: (s: string) => void;
  parcelas: string; setParcelas: (p: string) => void;
}) {
  const resto = restoDepoisDoSinal(total, forma, sinal);
  return (
    <>
      <label className="campo"><span>{rotuloForma}</span>
        <select id={`${prefixo}-forma`} value={forma} onChange={(e) => setForma(e.target.value as FormaPagamento)}>
          <option value="AVISTA">À vista</option><option value="PARCELADO">Parcelado</option>
        </select>
      </label>
      {forma === 'PARCELADO' && (
        <>
          <label className="campo"><span>Sinal (R$)</span>
            <input id={`${prefixo}-sinal`} inputMode="decimal" value={sinal} onChange={(e) => setSinal(e.target.value)} /></label>
          <label className="campo"><span>Parcelas depois do sinal</span>
            <select id={`${prefixo}-parcelas`} value={parcelas} onChange={(e) => setParcelas(e.target.value)}>
              {[1, 2, 3, 4, 5, 6].map((i) => <option key={i} value={i}>{i}x de {moeda(resto / i)}</option>)}
            </select>
          </label>
        </>
      )}
    </>
  );
}
