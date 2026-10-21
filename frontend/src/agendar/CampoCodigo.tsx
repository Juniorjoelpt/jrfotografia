import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Desconto } from '../tipos';
import { moeda } from '../util';

/** "Tem cupom ou código de indicação?": confere o código e mostra o desconto antes de reservar. */
export function CampoCodigo({ pacoteId, campanha, telefone, inicial, aoMudar }: {
  pacoteId?: number; campanha?: string; telefone?: string; inicial?: string | null; aoMudar: (d: Desconto | null) => void;
}) {
  const [aberto, setAberto] = useState(!!inicial);
  const [codigo, setCodigo] = useState(inicial ?? '');
  const [d, setD] = useState<Desconto | null>(null);
  const [conferindo, setConferindo] = useState(false);

  async function conferir(c = codigo) {
    if (!c.trim()) return;
    setConferindo(true);
    try {
      const q = new URLSearchParams({ codigo: c.trim(), ...(pacoteId ? { pacote: String(pacoteId) } : {}), ...(campanha ? { campanha } : {}),
        ...(telefone ? { telefone } : {}) });
      const r = await api<Desconto>(`/publico/descontos?${q}`, { token: null });
      setD(r);
      aoMudar(r.valido ? r : null);
    } catch {
      setD({ valido: false, mensagem: 'Não foi possível conferir agora.', desconto: 0 });
      aoMudar(null);
    } finally {
      setConferindo(false);
    }
  }

  // Código que veio no link (?codigo=): confere sozinho quando o pacote/campanha estiver definido.
  useEffect(() => { if (inicial && (pacoteId || campanha)) conferir(inicial); }, [pacoteId, campanha]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!aberto) return <button type="button" className="botao-link legenda inteira" style={{ justifySelf: 'start' }} onClick={() => setAberto(true)}>TEM CUPOM OU CÓDIGO DE INDICAÇÃO?</button>;
  return (
    <div className="campo inteira cod-campo">
      <span>Cupom ou código de indicação</span>
      <div className="cod-linha">
        <input name="codigo" value={codigo} maxLength={30} autoCapitalize="characters" spellCheck={false}
          onChange={(e) => { setCodigo(e.target.value.toUpperCase()); if (d) { setD(null); aoMudar(null); } }} />
        <button type="button" className="botao pequeno" disabled={conferindo || !codigo.trim()} onClick={() => conferir()}>{conferindo ? '…' : 'Aplicar'}</button>
      </div>
      {d && (d.valido
        ? <span className="cod-ok">✓ {d.mensagem} · −{moeda(d.desconto)}</span>
        : <span className="cod-erro">{d.mensagem}</span>)}
    </div>
  );
}
