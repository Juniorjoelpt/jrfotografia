import { Link, useSearchParams } from 'react-router-dom';
import { sessaoAtual } from '../api';
import { Numero } from '../componentes/Movimento';
import type { Orcamento, Pedido } from '../tipos';
import { CLASSE_ORCAMENTO, data, moeda, NOMES_ORCAMENTO } from '../util';
import { Erro, Linhas, useDados } from './comum';
import { Pedidos } from './Pedidos';

/** Vendas: pedidos do site e orçamentos enviados, com o funil no topo. */
export function Vendas() {
  const [params, setParams] = useSearchParams();
  const admin = sessaoAtual()?.perfil === 'ADMIN';
  const aba = admin && params.get('aba') === 'orcamentos' ? 'orcamentos' : 'pedidos';
  const { dados: pedidos } = useDados<Pedido[]>('/pedidos');
  const { dados: orcamentos } = useDados<Orcamento[]>(admin ? '/orcamentos' : null);

  const novos = pedidos?.filter((p) => p.status === 'NOVO').length ?? 0;
  const conversa = pedidos?.filter((p) => p.status === 'CONTATADO').length ?? 0;
  const aguardando = orcamentos?.filter((o) => o.status === 'ENVIADO').length ?? 0;
  const aceitos = orcamentos?.filter((o) => o.status === 'ACEITO').length ?? 0;

  return (
    <>
      <div className="cabeca-pagina">
        <div><span className="rotulo">Vendas</span><h1 className="titulo">Pedidos e orçamentos</h1></div>
        {admin && <Link className="botao principal" to="/painel/orcamentos/novo">Novo orçamento</Link>}
      </div>
      <div className="numeros funil escalonado">
        <div className="numero"><span className="legenda">PEDIDOS NOVOS</span><strong style={novos ? { color: 'var(--acento-texto)' } : undefined}><Numero valor={novos} /></strong></div>
        <div className="numero"><span className="legenda">EM CONVERSA</span><strong><Numero valor={conversa} /></strong></div>
        {admin && <div className="numero"><span className="legenda">AGUARDANDO RESPOSTA</span><strong><Numero valor={aguardando} /></strong></div>}
        {admin && <div className="numero"><span className="legenda">ORÇAMENTOS ACEITOS</span><strong><Numero valor={aceitos} /></strong></div>}
      </div>
      {admin && (
        <div className="abas" role="group" aria-label="Seções">
          <button className="botao pequeno" aria-pressed={aba === 'pedidos'} onClick={() => setParams({}, { replace: true })}>Pedidos do site</button>
          <button className="botao pequeno" aria-pressed={aba === 'orcamentos'} onClick={() => setParams({ aba: 'orcamentos' }, { replace: true })}>Orçamentos</button>
        </div>
      )}
      {aba === 'pedidos' ? <Pedidos /> : <ListaOrcamentos />}
    </>
  );
}

function ListaOrcamentos() {
  const { dados, erro } = useDados<Orcamento[]>('/orcamentos');
  return (
    <>
      <Erro msg={erro} />
      {dados && (
        <Linhas vazio="Nenhum orçamento ainda. Monte o primeiro a partir de um pedido ou pelo botão Novo orçamento."
          itens={dados.map((o) => (
            <Link key={o.id} to={`/painel/orcamentos/${o.id}`} className="linha-item">
              <span className="principal">{o.nomeCliente}</span>
              <span className="lado num">{moeda(o.valorTotal)}</span>
              <span className="legenda">Nº {o.numero} · {(o.pacoteNome ?? o.tipo ?? o.itens[0]?.descricao ?? '').toUpperCase()}</span>
              <span className="lado"><span className={`etiqueta ${CLASSE_ORCAMENTO[o.status]}`}>{NOMES_ORCAMENTO[o.status]}</span></span>
              <span className="legenda" style={{ gridColumn: '1 / -1' }}>
                {o.status === 'ACEITO' ? `ACEITO EM ${data(o.respondidoEm)}` : o.status === 'RECUSADO' ? `RECUSADO EM ${data(o.respondidoEm)}`
                  : o.status === 'ENVIADO' ? `ENVIADO EM ${data(o.enviadoEm)} · VALE ATÉ ${data(o.validade)}` : `CRIADO EM ${data(o.criadoEm)} · VALE ATÉ ${data(o.validade)}`}
              </span>
            </Link>
          ))} />
      )}
    </>
  );
}
