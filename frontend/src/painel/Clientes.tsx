import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ErroApi, sessaoAtual } from '../api';
import type { Cliente, DataEspecial, Origem, TrabalhoResumo } from '../tipos';
import { data, linkWhatsapp, NOMES_ORIGEM, telefone } from '../util';
import { Erro, LinhaTrabalho, Linhas, useDados } from './comum';

export function Clientes() {
  const [busca, setBusca] = useState('');
  const { dados, erro, recarregar } = useDados<Cliente[]>(`/clientes${busca ? `?busca=${encodeURIComponent(busca)}` : ''}`);
  const [editando, setEditando] = useState<Cliente | 'novo' | null>(null);

  return (
    <>
      <div className="cabeca-pagina">
        <div><span className="rotulo">Clientes</span><h1 className="titulo">Clientes</h1></div>
        <button className="botao principal" onClick={() => setEditando('novo')}>Novo cliente</button>
      </div>
      {editando && (
        <FormCliente cliente={editando === 'novo' ? null : editando}
          aoSalvar={() => { setEditando(null); recarregar(); }} aoCancelar={() => setEditando(null)} />
      )}
      <label className="campo"><span className="sr">Buscar</span>
        <input id="busca-cliente" type="search" placeholder="Buscar por nome ou telefone" value={busca} onChange={(e) => setBusca(e.target.value)} />
      </label>
      <Erro msg={erro} />
      {dados && (
        <Linhas vazio={busca ? 'Nenhum cliente encontrado.' : 'Nenhum cliente cadastrado ainda.'}
          itens={dados.map((c) => <LinhaCliente key={c.id} c={c} aoEditar={() => setEditando(c)} />)} />
      )}
    </>
  );
}

function LinhaCliente({ c, aoEditar }: { c: Cliente; aoEditar: () => void }) {
  const [aberto, setAberto] = useState(false);
  const { dados: trabalhos } = useDados<TrabalhoResumo[]>(aberto ? `/clientes/${c.id}/trabalhos` : null);
  return (
    <div className="linha-item" style={{ gridTemplateColumns: '1fr auto' }}>
      <button className="botao-link principal" style={{ color: 'var(--tinta)', textAlign: 'left' }} aria-expanded={aberto} onClick={() => setAberto(!aberto)}>{c.nome}</button>
      <span className="lado legenda">{c.origem ? NOMES_ORIGEM[c.origem].toUpperCase() : ''}</span>
      <span className="legenda">{telefone(c.telefone)}{c.dataNascimento ? ` · ANIVERSÁRIO ${data(c.dataNascimento).slice(0, 5)}` : ''}</span>
      {aberto && (
        <div style={{ gridColumn: '1 / -1', display: 'grid', gap: 'var(--espaco-3)', paddingTop: 'var(--espaco-3)' }}>
          <div className="acoes-linha">
            {c.telefone && <a className="botao pequeno" href={linkWhatsapp(c.telefone, `Olá, ${c.nome.split(' ')[0]}!`)} target="_blank" rel="noreferrer">WhatsApp</a>}
            <button className="botao pequeno" onClick={aoEditar}>Editar</button>
            {sessaoAtual()?.perfil === 'ADMIN' && <Link className="botao pequeno" to={`/painel/trabalhos/novo?cliente=${c.id}`}>Novo trabalho</Link>}
          </div>
          {c.observacoes && <p className="suave" style={{ margin: 0 }}>{c.observacoes}</p>}
          <DatasEspeciais clienteId={c.id} />
          {trabalhos && <Linhas itens={trabalhos.map((t) => <LinhaTrabalho key={t.id} t={t} />)} vazio="Nenhum trabalho com este cliente." />}
        </div>
      )}
    </div>
  );
}

export function FormCliente({ cliente, aoSalvar, aoCancelar }: { cliente: Cliente | null; aoSalvar: (c: Cliente) => void; aoCancelar: () => void }) {
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const corpo = {
      nome: f.get('nome'), telefone: f.get('telefone') || null, email: f.get('email') || null, cpf: f.get('cpf') || null,
      dataNascimento: f.get('nascimento') || null, origem: (f.get('origem') || null) as Origem | null, observacoes: f.get('obs') || null,
    };
    setEnviando(true);
    try {
      const c = cliente
        ? await api<Cliente>(`/clientes/${cliente.id}`, { metodo: 'PUT', corpo })
        : await api<Cliente>('/clientes', { corpo });
      aoSalvar(c);
    } catch (er) {
      setErro(er instanceof ErroApi ? er.message : 'Não foi possível salvar.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="painel-caixa grade-form duas" onSubmit={salvar}>
      <h2 className="subtitulo inteira">{cliente ? 'Editar cliente' : 'Novo cliente'}</h2>
      <label className="campo inteira"><span>Nome</span><input id="cli-nome" name="nome" required defaultValue={cliente?.nome} /></label>
      <label className="campo"><span>WhatsApp</span><input id="cli-tel" name="telefone" inputMode="tel" defaultValue={cliente?.telefone} /></label>
      <label className="campo"><span>E-mail</span><input id="cli-email" name="email" type="email" defaultValue={cliente?.email} /></label>
      <label className="campo"><span>Data de nascimento</span><input id="cli-nasc" name="nascimento" type="date" defaultValue={cliente?.dataNascimento} /></label>
      <label className="campo"><span>Como chegou</span>
        <select id="cli-origem" name="origem" defaultValue={cliente?.origem ?? 'INSTAGRAM'}>
          {Object.entries(NOMES_ORIGEM).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </label>
      <label className="campo"><span>CPF (opcional)</span><input id="cli-cpf" name="cpf" inputMode="numeric" defaultValue={cliente?.cpf} /></label>
      <label className="campo inteira"><span>Observações</span><textarea id="cli-obs" name="obs" defaultValue={cliente?.observacoes} /></label>
      <Erro msg={erro} />
      <div className="acoes-linha inteira">
        <button className="botao principal" disabled={enviando}>{enviando ? 'Salvando…' : 'Salvar'}</button>
        <button type="button" className="botao" onClick={aoCancelar}>Cancelar</button>
      </div>
    </form>
  );
}

/** Datas que a cliente quer lembradas (casamento, filhos…). Aparecem em Relacionamento › Aniversários e datas. */
function DatasEspeciais({ clienteId }: { clienteId: number }) {
  const { dados, recarregar } = useDados<DataEspecial[]>(`/clientes/${clienteId}/datas`);
  const [nova, setNova] = useState(false);
  const [erro, setErro] = useState('');
  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const [ano, mes, dia] = String(f.get('data')).split('-').map(Number);
    setErro('');
    try {
      await api(`/clientes/${clienteId}/datas`, { corpo: { descricao: f.get('descricao'), dia, mes, ano: f.get('comAno') === 'on' ? ano : null } });
      setNova(false);
      recarregar();
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }
  const apagar = async (id: number) => { await api(`/clientes/${clienteId}/datas/${id}`, { metodo: 'DELETE' }); recarregar(); };
  return (
    <div style={{ display: 'grid', gap: 'var(--espaco-2)' }}>
      <div className="acoes-linha">
        <span className="legenda">DATAS ESPECIAIS</span>
        {dados?.map((d) => (
          <span key={d.id} className="etiqueta">
            {d.descricao} · {String(d.dia).padStart(2, '0')}/{String(d.mes).padStart(2, '0')}{d.ano ? `/${d.ano}` : ''}
            <button className="botao-link" aria-label={`Apagar ${d.descricao}`} style={{ marginLeft: 6, fontSize: 14 }} onClick={() => apagar(d.id)}>×</button>
          </span>
        ))}
        {!nova && <button className="botao-link legenda" onClick={() => setNova(true)}>+ adicionar</button>}
      </div>
      {nova && (
        <form className="grade-form duas anima-entrada" onSubmit={salvar}>
          <label className="campo"><span>O quê</span><input name="descricao" required maxLength={80} placeholder="Casamento, aniversário do filho…" /></label>
          <label className="campo"><span>Data</span><input name="data" type="date" required /></label>
          <label className="marcar"><input type="checkbox" name="comAno" defaultChecked /> Contar os anos (ex.: 5 anos de casados)</label>
          <Erro msg={erro} />
          <div className="acoes-linha inteira">
            <button className="botao pequeno principal">Salvar data</button>
            <button type="button" className="botao pequeno" onClick={() => setNova(false)}>Cancelar</button>
          </div>
        </form>
      )}
    </div>
  );
}
