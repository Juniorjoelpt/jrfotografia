import { Link } from 'react-router-dom';
import { api } from '../api';
import type { ContaReceber, Estudio, MeioPagamento } from '../tipos';
import { data, moeda } from '../util';
import { AcoesParcela } from './AcoesParcela';
import { Erro, Linhas, useDados } from './comum';

/** Parcelas em aberto (aba da tela Financeiro). */
export function Recebimentos() {
  const { dados, erro, recarregar } = useDados<ContaReceber[]>('/financeiro/a-receber');
  const { dados: estudio } = useDados<Estudio>('/estudio');
  const total = dados?.reduce((s, c) => s + c.parcela.valor, 0) ?? 0;
  const vencido = dados?.filter((c) => c.vencida).reduce((s, c) => s + c.parcela.valor, 0) ?? 0;

  const pagar = async (c: ContaReceber, forma: MeioPagamento) => {
    await api(`/parcelas/${c.parcela.id}/pagamento`, { corpo: { forma } });
    recarregar();
  };

  return (
    <>
      <div className="numeros">
        <div className="numero"><span className="legenda">EM ABERTO</span><strong>{moeda(total)}</strong></div>
        <div className={`numero${vencido ? ' critico' : ''}`}><span className="legenda">VENCIDO</span><strong>{moeda(vencido)}</strong></div>
      </div>
      {!estudio?.chavePix && !estudio?.pixAutomatico && <p className="aviso" style={{ margin: 0 }}>Cadastre sua chave Pix ou ligue o Pix automático em <Link to="/painel/ajustes#pagamentos">Ajustes › Pagamentos</Link> para ir junto nas cobranças.</p>}
      <Erro msg={erro} />
      {dados && (
        <Linhas vazio="Nada em aberto."
          itens={dados.map((c) => (
            <div key={c.parcela.id} className="linha-item">
              <Link className="principal" style={{ color: 'var(--tinta)' }} to={`/painel/trabalhos/${c.parcela.trabalhoId}`}>{c.clienteNome}</Link>
              <span className="lado num">{moeda(c.parcela.valor)}</span>
              <span className="legenda" style={{ gridColumn: '1 / -1' }}>{c.parcela.descricao.toUpperCase()} · {c.vencida ? <span style={{ color: 'var(--erro)' }}>VENCEU {data(c.parcela.vencimento)}</span> : `VENCE ${data(c.parcela.vencimento)}`}</span>
              <span className="acoes-linha" style={{ gridColumn: '1 / -1' }}>
                <AcoesParcela parcela={c.parcela} clienteNome={c.clienteNome} telefone={c.clienteTelefone} areaToken={c.areaCliente}
                  estudio={estudio} pixAberto={c.pixAberto} aoPagar={(forma) => pagar(c, forma)} />
              </span>
            </div>
          ))} />
      )}
    </>
  );
}
