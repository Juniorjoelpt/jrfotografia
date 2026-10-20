import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { imagemPainel, sessaoAtual } from '../api';
import { Monograma } from '../componentes/Logo';
import type { StatusTrabalho, TrabalhoResumo } from '../tipos';
import { CLASSE_STATUS, MESES, NOMES_STATUS, diaSemana } from '../util';
import { Erro, useDados } from './comum';

const FILTROS: (StatusTrabalho | '')[] = ['', 'AGENDADO', 'FOTOGRAFADO', 'SELECAO', 'EDICAO', 'ENTREGUE', 'CANCELADO'];
const mesDe = (t: TrabalhoResumo) => (t.dataTrabalho ? t.dataTrabalho.slice(0, 7) : 'sem-data');

export function Trabalhos() {
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as StatusTrabalho | '';
  const { dados, erro, carregando } = useDados<TrabalhoResumo[]>(`/trabalhos${status ? `?status=${status}` : ''}`);
  const [mes, setMes] = useState<string>('todos');

  // Meses com trabalhos, do mais antigo ao mais recente, como a linha do tempo da referência.
  const meses = useMemo(() => {
    const m = new Map<string, number>();
    dados?.forEach((t) => m.set(mesDe(t), (m.get(mesDe(t)) ?? 0) + 1));
    return [...m.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [dados]);
  const lista = (dados ?? []).filter((t) => mes === 'todos' || mesDe(t) === mes);

  return (
    <>
      <div className="cabeca-pagina">
        <div><span className="rotulo">Trabalhos</span><h1 className="titulo">Ensaios e eventos</h1></div>
        {sessaoAtual()?.perfil === 'ADMIN' && <Link to="/painel/trabalhos/novo" className="botao principal">Novo trabalho</Link>}
      </div>

      <div className="abas" role="group" aria-label="Filtrar por etapa">
        {FILTROS.map((f) => (
          <button key={f || 'todos'} className="botao pequeno" aria-pressed={status === f}
            onClick={() => setParams(f ? { status: f } : {})}>{f ? NOMES_STATUS[f] : 'Todos'}</button>
        ))}
      </div>

      {meses.length > 1 && (
        <div className="linha-meses" role="group" aria-label="Filtrar por mês">
          <button aria-pressed={mes === 'todos'} onClick={() => setMes('todos')}>Todos<small>{dados?.length}</small></button>
          {meses.map(([m, n]) => (
            <button key={m} aria-pressed={mes === m} onClick={() => setMes(m)}>
              {m === 'sem-data' ? 'Sem data' : MESES[Number(m.slice(5, 7)) - 1]}
              <small>{m === 'sem-data' ? n : `${m.slice(0, 4)} · ${n}`}</small>
            </button>
          ))}
        </div>
      )}

      <Erro msg={erro} />
      {!carregando && dados && (lista.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>{status ? `Nenhum trabalho ${NOMES_STATUS[status].toLowerCase()}.` : 'Nenhum trabalho ainda. Crie o primeiro.'}</p>
        : <div className="albuns">{lista.map((t) => <Album key={t.id} t={t} />)}</div>)}
    </>
  );
}

function Album({ t }: { t: TrabalhoResumo }) {
  const capas = t.capas ?? [];
  const dia = t.dataTrabalho ? `${diaSemana(t.dataTrabalho)} ${t.dataTrabalho.slice(8, 10)}/${t.dataTrabalho.slice(5, 7)}` : 'sem data';
  return (
    <Link to={`/painel/trabalhos/${t.id}`} className="album">
      <div className="topo"><span>{t.clienteNome}</span><span className="num">{t.totalFotos ?? 0}</span></div>
      <div className={`mosaico${capas.length === 1 ? ' unica' : ''}`}>
        {capas.length === 0
          ? <div className="vazia" style={{ gridColumn: '1 / -1', gridRow: '1 / -1' }}><Monograma tamanho={56} titulo="" /></div>
          : (capas.length === 1 ? capas : [...capas, ...Array(Math.max(0, 4 - capas.length)).fill(null)].slice(0, 4)).map((id, i) =>
            id ? <img key={id} src={imagemPainel(t.id, id, 'miniatura')} alt="" loading="lazy" /> : <div key={`v${i}`} className="vazia" />)}
      </div>
      <div className="rodape-album">
        <span className="legenda">{(t.pacoteNome ?? t.tipo ?? '').toUpperCase()} · {dia.toUpperCase()}</span>
      </div>
      {t.atrasado ? <span className="etiqueta erro">Atrasado</span> : <span className={`etiqueta ${CLASSE_STATUS[t.status]}`} style={{ justifySelf: 'start' }}>{NOMES_STATUS[t.status]}</span>}
    </Link>
  );
}
