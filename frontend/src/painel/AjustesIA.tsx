import { useState, type FormEvent } from 'react';
import { api, ErroApi } from '../api';
import type { ConfigIa } from '../tipos';
import { Erro, useDados } from './comum';

/** Serviços que falam o formato OpenAI. O endereço vem preenchido; o modelo é escolhido na lista de cada serviço. */
const SERVICOS = [
  { id: 'ollama', nome: 'Ollama na própria VPS (gratuito)', url: 'http://localhost:11434/v1', modelo: 'qwen3:4b', chave: false },
  { id: 'groq', nome: 'Groq (modelos abertos hospedados, rápido)', url: 'https://api.groq.com/openai/v1', modelo: '', chave: true },
  { id: 'openrouter', nome: 'OpenRouter (vários modelos abertos)', url: 'https://openrouter.ai/api/v1', modelo: '', chave: true },
  { id: 'outro', nome: 'Outro serviço compatível', url: '', modelo: '', chave: true },
] as const;

function servicoDe(url?: string | null) {
  return SERVICOS.find((s) => s.url && url?.startsWith(s.url.replace('/v1', '')))?.id ?? (url ? 'outro' : 'ollama');
}

/** Ajustes › Assistente de conteúdo: qual modelo de IA dá as ideias de post. */
export function AjustesIA() {
  const { dados, erro, setDados } = useDados<ConfigIa>('/estudio/ia');
  const [servico, setServico] = useState<string | null>(null);
  const [url, setUrl] = useState<string | null>(null);
  const [modelo, setModelo] = useState<string | null>(null);
  const [falha, setFalha] = useState('');
  const [salvo, setSalvo] = useState(false);
  const [testando, setTestando] = useState(false);
  const [teste, setTeste] = useState<{ ok: boolean; mensagem: string } | null>(null);

  if (!dados) return <section className="secao"><Erro msg={erro} /></section>;
  const atual = servico ?? servicoDe(dados.url);
  const s = SERVICOS.find((x) => x.id === atual)!;
  const valorUrl = url ?? dados.url ?? s.url;
  const valorModelo = modelo ?? dados.modelo ?? s.modelo;

  function escolher(id: string) {
    const n = SERVICOS.find((x) => x.id === id)!;
    setServico(id); setUrl(n.url); setModelo(n.modelo); setSalvo(false); setTeste(null);
  }

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const f = new FormData(form);
    setFalha(''); setSalvo(false); setTeste(null);
    try {
      setDados(await api<ConfigIa>('/estudio/ia', {
        metodo: 'PUT',
        corpo: { ativo: f.get('ativo') === 'on', url: valorUrl, modelo: valorModelo, chave: f.get('chave') || null, apagarChave: !s.chave },
      }));
      setSalvo(true);
      const c = form.querySelector<HTMLInputElement>('input[name=chave]');
      if (c) c.value = '';
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }

  async function testar() {
    setTeste(null); setTestando(true);
    try { setTeste(await api<{ ok: boolean; mensagem: string }>('/estudio/ia/teste', { corpo: {} })); } catch (ex) {
      setTeste({ ok: false, mensagem: ex instanceof ErroApi ? ex.message : 'Não foi possível testar.' });
    } finally { setTestando(false); }
  }

  return (
    <section className="secao">
      <form className="painel-caixa grade-form duas" onSubmit={salvar} onChange={() => setSalvo(false)}>
        <label className="marcar inteira"><input id="ia-ativo" type="checkbox" name="ativo" defaultChecked={dados.ativo} /> <strong>Assistente de conteúdo ligado</strong></label>
        <p className="suave inteira" style={{ margin: 0 }}>
          Em <b>Marketing › Conteúdo</b>, o assistente sugere posts, legendas e hashtags a partir do que acontece no estúdio:
          mini-sessões, agenda, ensaios entregues, depoimentos e datas comemorativas.
        </p>
        <label className="campo inteira"><span>Onde o modelo roda</span>
          <select id="ia-servico" value={atual} onChange={(e) => escolher(e.target.value)}>
            {SERVICOS.map((x) => <option key={x.id} value={x.id}>{x.nome}</option>)}
          </select>
        </label>
        <details className="aviso inteira" open={!dados.url}>
          <summary style={{ cursor: 'pointer' }}><strong>Como preparar</strong></summary>
          {atual === 'ollama' && (
            <ol style={{ margin: 'var(--espaco-2) 0 0', paddingLeft: 20, display: 'grid', gap: 4 }}>
              <li>Na VPS, instale o Ollama (ollama.com › Download › Linux, um comando só).</li>
              <li>Baixe um modelo pequeno, que roda sem placa de vídeo: <code>ollama pull qwen3:4b</code>. A VPS precisa de uns <b>8 GB de memória</b> livres.</li>
              <li>Deixe o endereço <code>http://localhost:11434/v1</code> se o sistema e o Ollama estão na mesma VPS. Não precisa de chave.</li>
              <li>Sem placa de vídeo, cada pedido leva de 30 segundos a 2 minutos. Nada sai da sua VPS.</li>
            </ol>
          )}
          {(atual === 'groq' || atual === 'openrouter') && (
            <ol style={{ margin: 'var(--espaco-2) 0 0', paddingLeft: 20, display: 'grid', gap: 4 }}>
              <li>Crie uma conta em {atual === 'groq' ? 'console.groq.com' : 'openrouter.ai'} e gere uma <b>chave de API</b>.</li>
              <li>Na lista de modelos do serviço, copie o nome de um modelo aberto (Llama, Qwen, Gemma, Mistral) e cole abaixo.</li>
              <li>A resposta vem em segundos. O texto do pedido sai da sua VPS, mas o sistema não manda nome, telefone, e-mail ou CPF de clientes.</li>
            </ol>
          )}
          {atual === 'outro' && <p style={{ margin: 'var(--espaco-2) 0 0' }}>Qualquer serviço com a API no formato OpenAI (<code>/v1/chat/completions</code>) funciona: informe o endereço base, o modelo e a chave.</p>}
        </details>
        <label className="campo inteira"><span>Endereço da API</span>
          <input id="ia-url" value={valorUrl} onChange={(e) => setUrl(e.target.value)} placeholder="https://…/v1" maxLength={300} /></label>
        <label className="campo"><span>Modelo</span>
          <input id="ia-modelo" value={valorModelo} onChange={(e) => setModelo(e.target.value)} placeholder={atual === 'ollama' ? 'qwen3:4b' : 'nome do modelo no serviço'} maxLength={120} /></label>
        {s.chave
          ? <label className="campo"><span>Chave da API {dados.temChave ? '(guardada; cole só para trocar)' : ''}</span>
              <input id="ia-chave" name="chave" type="password" autoComplete="off" maxLength={600} placeholder={dados.temChave ? '••••••••••••' : 'cole a chave aqui'} /></label>
          : <p className="legenda" style={{ margin: 0, alignSelf: 'end' }}>O OLLAMA NA PRÓPRIA VPS NÃO USA CHAVE.</p>}
        <Erro msg={falha} />
        {salvo && <p className="aviso sucesso inteira" style={{ margin: 0 }}>Configuração salva.</p>}
        <div className="acoes-linha inteira">
          <button className="botao principal">Salvar</button>
          <button type="button" className="botao" disabled={!dados.url || testando} onClick={testar}>{testando ? 'Perguntando ao modelo…' : 'Testar conexão'}</button>
        </div>
        {teste && <p className={`aviso ${teste.ok ? 'sucesso' : 'erro'} inteira`} role="status" style={{ margin: 0 }}>{teste.mensagem}</p>}
      </form>
    </section>
  );
}
