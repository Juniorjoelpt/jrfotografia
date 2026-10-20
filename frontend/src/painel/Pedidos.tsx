import { useNavigate } from 'react-router-dom';
import { api, sessaoAtual } from '../api';
import type { Cliente, Pacote, Pedido, StatusPedido } from '../tipos';
import { data, dataHora, linkWhatsapp, NOMES_ORIGEM, NOMES_PEDIDO, primeiroNome, telefone } from '../util';
import { Erro, Linhas, useDados } from './comum';

const CLASSE: Record<StatusPedido, string> = { NOVO: 'acento', CONTATADO: 'alerta', FECHADO: 'sucesso', PERDIDO: '' };

export function Pedidos() {
  const navegar = useNavigate();
  const { dados, erro, recarregar } = useDados<Pedido[]>('/pedidos');
  const { dados: pacotes } = useDados<Pacote[]>('/pacotes');
  const admin = sessaoAtual()?.perfil === 'ADMIN';

  const mudar = async (p: Pedido, status: StatusPedido) => {
    await api(`/pedidos/${p.id}`, { metodo: 'PATCH', corpo: { status } });
    recarregar();
  };
  const virarTrabalho = async (p: Pedido) => {
    const c = await api<Cliente>(`/pedidos/${p.id}/cliente`, { corpo: {} });
    navegar(`/painel/trabalhos/novo?cliente=${c.id}`);
  };

  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Pedidos</span><h1 className="titulo">Pedidos de orçamento</h1></div></div>
      <p className="suave" style={{ margin: 0 }}>Chegam pelo formulário do site. Responda pelo WhatsApp e acompanhe até fechar.</p>
      <Erro msg={erro} />
      {dados && (
        <Linhas vazio="Nenhum pedido ainda. Divulgue o link do site no Instagram."
          itens={dados.map((p) => {
            const pacote = pacotes?.find((x) => x.id === p.pacoteId);
            const msg = `Olá, ${primeiroNome(p.nome)}! Aqui é o Joel, da JR Fotografia. Recebi seu pedido de orçamento${p.tipoTrabalho ? ` para ${p.tipoTrabalho.toLowerCase()}` : ''}${pacote ? ` (${pacote.nome})` : ''}.`;
            return (
              <div key={p.id} className="linha-item">
                <span className="principal">{p.nome}</span>
                <span className="lado"><span className={`etiqueta ${CLASSE[p.status]}`}>{NOMES_PEDIDO[p.status]}</span></span>
                <span className="legenda">{[p.tipoTrabalho, pacote?.nome, p.dataDesejada && `PARA ${data(p.dataDesejada)}`].filter(Boolean).join(' · ').toUpperCase()}</span>
                <span className="legenda lado">{dataHora(p.criadoEm)}</span>
                {p.mensagem && <p className="suave" style={{ gridColumn: '1 / -1', margin: 0 }}>“{p.mensagem}”</p>}
                <div className="acoes-linha" style={{ gridColumn: '1 / -1' }}>
                  <a className="botao principal pequeno" href={linkWhatsapp(p.telefone, msg)} target="_blank" rel="noreferrer"
                    onClick={() => p.status === 'NOVO' && mudar(p, 'CONTATADO')}>Responder · {telefone(p.telefone)}</a>
                  {admin && p.status !== 'PERDIDO' && <button className="botao pequeno" onClick={() => virarTrabalho(p)}>Fechou: criar trabalho</button>}
                  <select aria-label="Situação do pedido" className="botao pequeno" value={p.status} onChange={(e) => mudar(p, e.target.value as StatusPedido)}>
                    {Object.entries(NOMES_PEDIDO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                  </select>
                  {p.origem && <span className="legenda" style={{ alignSelf: 'center' }}>VIA {NOMES_ORIGEM[p.origem].toUpperCase()}</span>}
                </div>
              </div>
            );
          })} />
      )}
    </>
  );
}
