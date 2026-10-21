import { useState } from 'react';
import { api, ErroApi } from '../api';

/** "Enviar por e-mail" ao lado do WhatsApp. Mostra para quem foi ou o motivo de não ter ido. */
export function BotaoEmail({ caminho, corpo, para, rotulo = 'Enviar por e-mail', aoEnviar }: {
  caminho: string; corpo?: unknown; para: string; rotulo?: string; aoEnviar?: () => void;
}) {
  const [estado, setEstado] = useState<{ ok: boolean; msg: string } | null>(null);
  const [enviando, setEnviando] = useState(false);
  async function enviar() {
    setEnviando(true);
    setEstado(null);
    try {
      const r = await api<{ enviado: boolean }>(caminho, { metodo: 'POST', corpo: corpo ?? {} });
      setEstado(r.enviado ? { ok: true, msg: `E-mail enviado para ${para}` } : { ok: false, msg: 'Este e-mail já tinha sido enviado.' });
      if (r.enviado) aoEnviar?.();
    } catch (e) {
      setEstado({ ok: false, msg: e instanceof ErroApi ? e.message : 'Não foi possível enviar.' });
    } finally {
      setEnviando(false);
    }
  }
  return (
    <>
      <button type="button" className="botao pequeno" disabled={enviando} onClick={enviar} title={`Enviar para ${para}`}>{enviando ? 'Enviando…' : rotulo}</button>
      {estado && <span className="legenda" role="status" style={{ color: estado.ok ? 'var(--sucesso)' : 'var(--erro)', alignSelf: 'center' }}>{estado.msg.toUpperCase()}</span>}
    </>
  );
}
