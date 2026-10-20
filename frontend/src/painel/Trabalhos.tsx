import { Link, useSearchParams } from 'react-router-dom';
import { sessaoAtual } from '../api';
import type { StatusTrabalho, TrabalhoResumo } from '../tipos';
import { NOMES_STATUS } from '../util';
import { Erro, LinhaTrabalho, Linhas, useDados } from './comum';

const FILTROS: (StatusTrabalho | '')[] = ['', 'AGENDADO', 'FOTOGRAFADO', 'SELECAO', 'EDICAO', 'ENTREGUE', 'CANCELADO'];

export function Trabalhos() {
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as StatusTrabalho | '';
  const { dados, erro, carregando } = useDados<TrabalhoResumo[]>(`/trabalhos${status ? `?status=${status}` : ''}`);

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
      <Erro msg={erro} />
      {!carregando && dados && (
        <Linhas itens={dados.map((t) => <LinhaTrabalho key={t.id} t={t} />)}
          vazio={status ? `Nenhum trabalho ${NOMES_STATUS[status].toLowerCase()}.` : 'Nenhum trabalho ainda. Crie o primeiro.'} />
      )}
    </>
  );
}
