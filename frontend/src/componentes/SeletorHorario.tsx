import { useEffect, useState } from 'react';
import type { DiaVago } from '../tipos';
import { isoDia, MESES } from '../util';
import './seletor-horario.css';

const SEMANA = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom'];

/**
 * Calendário do mês com os dias que têm horário livre e, ao tocar no dia, os horários.
 * `carregar` busca as vagas do período (o servidor já aplica antecedência, horizonte e folga).
 */
export function SeletorHorario({ carregar, valor, aoEscolher }: {
  carregar: (de: string, ate: string) => Promise<DiaVago[]>;
  valor: string | null;
  aoEscolher: (inicio: string) => void;
}) {
  const hoje = new Date();
  const [mes, setMes] = useState(() => new Date(hoje.getFullYear(), hoje.getMonth(), 1));
  const [vagas, setVagas] = useState<DiaVago[] | null>(null);
  const [dia, setDia] = useState<string | null>(valor ? valor.slice(0, 10) : null);
  const [erro, setErro] = useState('');

  useEffect(() => {
    let vivo = true;
    setVagas(null);
    setErro('');
    const de = isoDia(mes);
    const ate = isoDia(new Date(mes.getFullYear(), mes.getMonth() + 1, 0));
    carregar(de, ate)
      .then((v) => { if (!vivo) return; setVagas(v); if (!dia || !v.some((x) => x.data === dia)) setDia(v[0]?.data ?? null); })
      .catch(() => vivo && setErro('Não foi possível carregar os horários. Tente de novo.'));
    return () => { vivo = false; };
  }, [mes]); // eslint-disable-line react-hooks/exhaustive-deps

  const primeiroDia = (new Date(mes.getFullYear(), mes.getMonth(), 1).getDay() + 6) % 7;
  const totalDias = new Date(mes.getFullYear(), mes.getMonth() + 1, 0).getDate();
  const livres = new Set(vagas?.map((v) => v.data));
  const doDia = vagas?.find((v) => v.data === dia);
  const noMesAtual = mes.getFullYear() === hoje.getFullYear() && mes.getMonth() === hoje.getMonth();

  return (
    <div className="seletor">
      <div className="seletor-cab">
        <button type="button" className="botao pequeno" aria-label="Mês anterior" disabled={noMesAtual}
          onClick={() => setMes(new Date(mes.getFullYear(), mes.getMonth() - 1, 1))}>‹</button>
        <strong>{MESES[mes.getMonth()]} {mes.getFullYear()}</strong>
        <button type="button" className="botao pequeno" aria-label="Próximo mês"
          onClick={() => setMes(new Date(mes.getFullYear(), mes.getMonth() + 1, 1))}>›</button>
      </div>
      <div className="seletor-grade" role="grid" aria-label={`Dias de ${MESES[mes.getMonth()]}`}>
        {SEMANA.map((s) => <span key={s} className="seletor-sem" role="columnheader">{s}</span>)}
        {Array.from({ length: primeiroDia }, (_, i) => <span key={`v${i}`} />)}
        {Array.from({ length: totalDias }, (_, i) => {
          const iso = isoDia(new Date(mes.getFullYear(), mes.getMonth(), i + 1));
          const tem = livres.has(iso);
          return (
            <button key={iso} type="button" role="gridcell" disabled={!tem} aria-selected={iso === dia}
              className={`seletor-dia${tem ? ' livre' : ''}`} onClick={() => setDia(iso)}
              aria-label={`${i + 1} de ${MESES[mes.getMonth()]}${tem ? ', com horários' : ', sem horários'}`}>
              {i + 1}
            </button>
          );
        })}
      </div>
      {erro && <p className="aviso erro" role="alert" style={{ margin: 0 }}>{erro}</p>}
      {vagas === null && !erro && <div className="esqueleto" style={{ height: 48 }} />}
      {vagas && vagas.length === 0 && <p className="legenda" style={{ margin: 0 }}>SEM HORÁRIOS LIVRES NESTE MÊS. VEJA O PRÓXIMO.</p>}
      {doDia && (
        <div className="seletor-horas" role="group" aria-label="Horários livres">
          {doDia.horarios.map((h) => {
            const inicio = `${doDia.data}T${h.slice(0, 5)}:00`;
            return (
              <button key={h} type="button" className="botao pequeno" aria-pressed={valor === inicio} onClick={() => aoEscolher(inicio)}>
                {h.slice(0, 5)}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
