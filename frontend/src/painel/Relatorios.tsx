import { useState } from 'react';
import { BarrasMensais, Ranking } from '../componentes/Graficos';
import { Numero } from '../componentes/Movimento';
import type { Relatorio } from '../tipos';
import { moeda } from '../util';
import { Erro, useDados } from './comum';

const pct = (v: number | null) => (v == null ? '—' : `${v.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`);
const trabalhos = (q: number) => `${q} ${q === 1 ? 'trabalho' : 'trabalhos'}`;

/** Relatórios de vendas do ano: faturamento, tipos de ensaio, ticket médio, conversão e origem dos clientes. */
export function Relatorios() {
  const atual = new Date().getFullYear();
  const [ano, setAno] = useState(atual);
  const { dados: r, erro, carregando } = useDados<Relatorio>(`/relatorios?ano=${ano}`);

  return (
    <>
      <div className="cabeca-pagina">
        <div><span className="rotulo">Relatórios</span><h1 className="titulo">Como foi {ano}</h1></div>
        <div className="acoes-linha" style={{ alignItems: 'center' }}>
          <button className="botao pequeno" aria-label="Ano anterior" onClick={() => setAno(ano - 1)}>‹</button>
          <strong className="num" style={{ minWidth: 48, textAlign: 'center' }}>{ano}</strong>
          <button className="botao pequeno" aria-label="Próximo ano" disabled={ano >= atual + 1} onClick={() => setAno(ano + 1)}>›</button>
        </div>
      </div>
      <Erro msg={erro} />
      {!r && carregando && <div className="esqueleto" style={{ height: 320 }} />}
      {r && (
        <>
          <div className="numeros escalonado">
            <div className="numero"><span className="legenda">FATURAMENTO</span><strong><Numero valor={r.faturamentoAno} formatar={moeda} /></strong></div>
            <div className="numero"><span className="legenda">TRABALHOS</span><strong><Numero valor={r.trabalhos} /></strong></div>
            <div className="numero"><span className="legenda">TICKET MÉDIO</span><strong><Numero valor={r.ticketMedio} formatar={moeda} /></strong></div>
            <div className="numero" title={`${r.orcamentosAceitos} de ${r.orcamentosEnviados} orçamentos enviados`}>
              <span className="legenda">ORÇAMENTOS ACEITOS</span><strong>{pct(r.conversaoOrcamentos)}</strong>
              <span className="legenda">{r.orcamentosAceitos} DE {r.orcamentosEnviados}</span>
            </div>
            <div className="numero" title={`${r.pedidosFechados} de ${r.pedidos} pedidos do site`}>
              <span className="legenda">PEDIDOS QUE FECHARAM</span><strong>{pct(r.conversaoPedidos)}</strong>
              <span className="legenda">{r.pedidosFechados} DE {r.pedidos}</span>
            </div>
          </div>

          <BarrasMensais titulo="Faturamento por mês (recebido)" formatar={moeda} mesAtual={ano === atual ? new Date().getMonth() + 1 : undefined}
            series={[{ nome: 'Recebido', classe: 'a', valores: r.faturamentoMensal }]} />

          <div className="graficos-duplos">
            <Ranking titulo="Por tipo de ensaio" itens={r.porTipo} formatar={moeda} detalhe={trabalhos} vazio="Sem trabalhos neste ano." />
            <Ranking titulo="De onde vêm os clientes" itens={r.porOrigem} formatar={moeda} detalhe={trabalhos} vazio="Sem trabalhos neste ano." />
          </div>
          <Ranking titulo="Despesas por categoria" itens={r.despesasPorCategoria} formatar={moeda}
            detalhe={(q) => `${q} ${q === 1 ? 'lançamento' : 'lançamentos'}`} vazio="Nenhuma despesa lançada neste ano." />
          <p className="legenda" style={{ margin: 0 }}>
            FATURAMENTO É O QUE ENTROU NO CAIXA. TIPOS, ORIGEM E TICKET MÉDIO CONSIDERAM OS TRABALHOS COM DATA NO ANO (VALOR FECHADO, SEM CANCELADOS).
          </p>
        </>
      )}
    </>
  );
}
