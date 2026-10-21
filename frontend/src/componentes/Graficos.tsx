import { MESES, MESES_CURTOS } from '../util';
import './graficos.css';

export type Serie = { nome: string; classe: 'a' | 'b'; valores: number[] };

/** Arredonda o topo do eixo para um número "redondo" (1, 2, 5 × 10ⁿ). */
function topo(max: number) {
  if (max <= 0) return 1;
  const p = Math.pow(10, Math.floor(Math.log10(max)));
  const m = max / p;
  return (m <= 1 ? 1 : m <= 2 ? 2 : m <= 5 ? 5 : 10) * p;
}

const curto = (v: number) => (v >= 1000 ? `${(v / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil` : v.toLocaleString('pt-BR', { maximumFractionDigits: 0 }));

/**
 * Barras por mês (uma ou duas séries lado a lado), com linhas de grade discretas,
 * dica ao passar o dedo/mouse e tabela para leitor de tela.
 */
export function BarrasMensais({ series, formatar, titulo, mesAtual }: {
  series: Serie[]; formatar: (v: number) => string; titulo: string; mesAtual?: number;
}) {
  const max = topo(Math.max(0, ...series.flatMap((s) => s.valores)));
  const linhas = [0, 0.5, 1].map((f) => f * max);
  return (
    <figure className="grafico">
      <figcaption className="grafico-cab">
        <span className="chamada">{titulo}</span>
        {series.length > 1 && (
          <span className="legenda-serie">
            {series.map((s) => <span key={s.nome}><i className={`cor-${s.classe}`} />{s.nome}</span>)}
          </span>
        )}
      </figcaption>
      <div className="barras" aria-hidden="true">
        <div className="barras-eixo">{linhas.slice().reverse().map((v) => <span key={v}>{curto(v)}</span>)}</div>
        <div className="barras-area">
          {linhas.map((v) => <span key={v} className="grade" style={{ bottom: `calc(22px + ${v / max} * (100% - 22px))` }} />)}
          {MESES_CURTOS.map((m, i) => (
            <div key={m} className={`barras-mes${mesAtual === i + 1 ? ' atual' : ''}`} tabIndex={-1}>
              <div className="barras-par">
                {series.map((s) => (
                  <span key={s.nome} className={`barra cor-${s.classe}`} style={{ height: `${(s.valores[i] / max) * 100}%`, animationDelay: `${i * 30}ms` }} />
                ))}
              </div>
              <span className="barras-rotulo">{m}</span>
              <span className="dica" role="tooltip">
                <b>{MESES[i]}</b>
                {series.map((s) => <span key={s.nome}>{series.length > 1 && <i className={`cor-${s.classe}`} />}{series.length > 1 ? `${s.nome}: ` : ''}{formatar(s.valores[i])}</span>)}
              </span>
            </div>
          ))}
        </div>
      </div>
      <table className="sr">
        <caption>{titulo}</caption>
        <thead><tr><th>Mês</th>{series.map((s) => <th key={s.nome}>{s.nome}</th>)}</tr></thead>
        <tbody>{MESES.map((m, i) => <tr key={m}><th>{m}</th>{series.map((s) => <td key={s.nome}>{formatar(s.valores[i])}</td>)}</tr>)}</tbody>
      </table>
    </figure>
  );
}

/** Ranking em barras horizontais: rótulo, barra proporcional e valor em texto. */
export function Ranking({ titulo, itens, formatar, detalhe, vazio }: {
  titulo: string; itens: { nome: string; valor: number; quantidade?: number }[];
  formatar: (v: number) => string; detalhe?: (q: number) => string; vazio: string;
}) {
  const max = Math.max(0, ...itens.map((i) => i.valor));
  return (
    <figure className="grafico">
      <figcaption className="grafico-cab"><span className="chamada">{titulo}</span></figcaption>
      {itens.length === 0 ? <p className="legenda" style={{ margin: 0 }}>{vazio}</p> : (
        <ul className="ranking">
          {itens.map((i, n) => (
            <li key={i.nome}>
              <span className="ranking-nome">{i.nome}{detalhe && i.quantidade != null && <span className="legenda"> · {detalhe(i.quantidade)}</span>}</span>
              <span className="ranking-valor num">{formatar(i.valor)}</span>
              <span className="ranking-trilho"><span className="barra cor-a" style={{ width: `${max ? (i.valor / max) * 100 : 0}%`, animationDelay: `${n * 60}ms` }} /></span>
            </li>
          ))}
        </ul>
      )}
    </figure>
  );
}
