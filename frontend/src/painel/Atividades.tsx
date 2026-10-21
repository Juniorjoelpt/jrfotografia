import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Atividade, UsuarioConta } from '../tipos';
import { dataHora } from '../util';
import { Erro, useDados } from './comum';

const LINKS: Record<string, (id: number) => string> = {
  TRABALHO: (id) => `/painel/trabalhos/${id}`,
  PEDIDO_PRODUTO: (id) => `/painel/produtos/${id}`,
  ORCAMENTO: (id) => `/painel/orcamentos/${id}`,
};

/** Ajustes › Atividades: quem fez o quê e quando (últimos 300 registros, com filtros). */
export function Atividades() {
  const [usuario, setUsuario] = useState('');
  const [de, setDe] = useState('');
  const [ate, setAte] = useState('');
  const q = new URLSearchParams({ ...(usuario ? { usuario } : {}), ...(de ? { de } : {}), ...(ate ? { ate } : {}) }).toString();
  const { dados, erro } = useDados<Atividade[]>(`/auditoria${q ? `?${q}` : ''}`);
  const { dados: pessoas } = useDados<UsuarioConta[]>('/usuarios');
  const alerta = (a: Atividade) => /errad|Removeu verificação|Desligou/.test(a.acao);
  return (
    <section className="secao">
      <p className="suave" style={{ margin: 0 }}>Tudo o que muda no painel fica registrado aqui: entradas, pagamentos, etapas dos trabalhos, cadastros, configurações.</p>
      <div className="grade-form tres">
        <label className="campo"><span>Pessoa</span>
          <select id="at-usuario" value={usuario} onChange={(e) => setUsuario(e.target.value)}>
            <option value="">Todas</option>
            {pessoas?.map((p) => <option key={p.id} value={p.id}>{p.nome}</option>)}
          </select>
        </label>
        <label className="campo"><span>De</span><input id="at-de" type="date" value={de} onChange={(e) => setDe(e.target.value)} /></label>
        <label className="campo"><span>Até</span><input id="at-ate" type="date" value={ate} onChange={(e) => setAte(e.target.value)} /></label>
      </div>
      <Erro msg={erro} />
      {dados && (dados.length === 0 ? <p className="vazio" style={{ margin: 0 }}>Nada registrado neste filtro.</p> : (
        <div className="linhas">
          {dados.map((a) => {
            const link = a.entidade && a.entidadeId && LINKS[a.entidade] ? LINKS[a.entidade](a.entidadeId) : null;
            return (
              <div key={a.id} className="linha-item">
                <span className="principal" style={alerta(a) ? { color: 'var(--alerta)' } : undefined}>
                  {link ? <Link to={link} style={{ color: 'inherit' }}>{a.acao}</Link> : a.acao}
                </span>
                <span className="lado legenda">{dataHora(a.criadoEm).toUpperCase()}</span>
                <span className="legenda" style={{ gridColumn: '1 / -1' }}>
                  {(a.usuario ?? 'SISTEMA').toUpperCase()}{a.detalhe ? ` · ${a.detalhe}` : ''}{a.ip ? ` · IP ${a.ip}` : ''}
                </span>
              </div>
            );
          })}
        </div>
      ))}
    </section>
  );
}
