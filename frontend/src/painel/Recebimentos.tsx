import { Link } from 'react-router-dom';
import { api } from '../api';
import type { ContaReceber, Estudio } from '../tipos';
import { data, linkWhatsapp, moeda, primeiroNome } from '../util';
import { Erro, Linhas, useDados } from './comum';

export function Recebimentos() {
  const { dados, erro, recarregar } = useDados<ContaReceber[]>('/financeiro/a-receber');
  const { dados: estudio } = useDados<Estudio>('/estudio');
  const total = dados?.reduce((s, c) => s + c.parcela.valor, 0) ?? 0;
  const vencido = dados?.filter((c) => c.vencida).reduce((s, c) => s + c.parcela.valor, 0) ?? 0;

  const pagar = async (c: ContaReceber) => {
    await api(`/parcelas/${c.parcela.id}/pagamento`, { corpo: { forma: 'PIX' } });
    recarregar();
  };

  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Recebimentos</span><h1 className="titulo">A receber</h1></div></div>
      <div className="numeros">
        <div className="numero"><span className="legenda">EM ABERTO</span><strong>{moeda(total)}</strong></div>
        <div className={`numero${vencido ? ' critico' : ''}`}><span className="legenda">VENCIDO</span><strong>{moeda(vencido)}</strong></div>
      </div>
      {!estudio?.chavePix && <p className="aviso" style={{ margin: 0 }}>Cadastre sua chave Pix em <Link to="/painel/ajustes">Ajustes</Link> para ela ir junto nas cobranças.</p>}
      <Erro msg={erro} />
      {dados && (
        <Linhas vazio="Nada em aberto."
          itens={dados.map((c) => (
            <div key={c.parcela.id} className="linha-item">
              <Link className="principal" style={{ color: 'var(--tinta)' }} to={`/painel/trabalhos/${c.parcela.trabalhoId}`}>{c.clienteNome}</Link>
              <span className="lado num">{moeda(c.parcela.valor)}</span>
              <span className="legenda" style={{ gridColumn: '1 / -1' }}>{c.parcela.descricao.toUpperCase()} · {c.vencida ? <span style={{ color: 'var(--erro)' }}>VENCEU {data(c.parcela.vencimento)}</span> : `VENCE ${data(c.parcela.vencimento)}`}</span>
              <span className="acoes-linha" style={{ gridColumn: '1 / -1' }}>
                {c.clienteTelefone && (
                  <a className="botao pequeno" target="_blank" rel="noreferrer" href={linkWhatsapp(c.clienteTelefone,
                    `Olá, ${primeiroNome(c.clienteNome)}! ${c.parcela.descricao} de ${moeda(c.parcela.valor)}${c.parcela.vencimento ? `, com vencimento em ${data(c.parcela.vencimento)}` : ''}.${estudio?.chavePix ? `\n\nChave Pix: ${estudio.chavePix}` : ''}\n\nAssim que pagar, me mande o comprovante por aqui.`)}>Cobrar</a>
                )}
                <button className="botao pequeno" onClick={() => pagar(c)}>Pago no Pix</button>
              </span>
            </div>
          ))} />
      )}
    </>
  );
}
