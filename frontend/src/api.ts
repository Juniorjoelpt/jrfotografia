import type { Sessao } from './tipos';

const CHAVE_SESSAO = 'jr.sessao';

export class ErroApi extends Error {
  constructor(public status: number, mensagem: string) { super(mensagem); }
}

export function sessaoAtual(): Sessao | null {
  try {
    const s = localStorage.getItem(CHAVE_SESSAO);
    return s ? (JSON.parse(s) as Sessao) : null;
  } catch {
    return null;
  }
}

export function salvarSessao(s: Sessao | null) {
  try {
    if (s) localStorage.setItem(CHAVE_SESSAO, JSON.stringify(s));
    else localStorage.removeItem(CHAVE_SESSAO);
  } catch { /* navegador sem armazenamento: a sessão dura até fechar a aba */ }
}

type Opcoes = { metodo?: string; corpo?: unknown; token?: string | null; arquivos?: FormData };

/** Chamada à API. Sem token explícito, usa o do painel. */
export async function api<T>(caminho: string, op: Opcoes = {}): Promise<T> {
  const token = op.token !== undefined ? op.token : sessaoAtual()?.token;
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let body: BodyInit | undefined;
  if (op.arquivos) body = op.arquivos;
  else if (op.corpo !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(op.corpo);
  }
  const r = await fetch(`/api${caminho}`, { method: op.metodo ?? (body ? 'POST' : 'GET'), headers, body });
  if (r.status === 401 && op.token === undefined && sessaoAtual()) {
    salvarSessao(null);
    window.location.href = '/painel/entrar';
  }
  if (!r.ok) {
    let msg = 'Não foi possível concluir. Tente de novo.';
    try { msg = (await r.json()).erro ?? msg; } catch { /* resposta sem corpo */ }
    throw new ErroApi(r.status, msg);
  }
  if (r.status === 204) return undefined as T;
  const tipo = r.headers.get('Content-Type') ?? '';
  return (tipo.includes('json') ? r.json() : undefined) as Promise<T>;
}

/** URL de imagem do painel: <img> não envia cabeçalho, então o token vai como parâmetro. */
export function imagemPainel(trabalhoId: number, fotoId: number, tipo: 'miniatura' | 'previa') {
  return `/api/trabalhos/${trabalhoId}/fotos/${fotoId}/${tipo}?t=${encodeURIComponent(sessaoAtual()?.token ?? '')}`;
}

/** Abre um PDF do painel numa aba nova (o token vai na URL porque a aba não envia cabeçalho). */
export function urlPainel(caminho: string) {
  const sep = caminho.includes('?') ? '&' : '?';
  return `/api${caminho}${sep}t=${encodeURIComponent(sessaoAtual()?.token ?? '')}`;
}

/** Leitura pública com até 3 tentativas (falha de rede ou erro 5xx), para o site não abrir vazio. */
export async function publico<T>(caminho: string, tentativas = 3): Promise<T> {
  for (let i = 1; ; i++) {
    try {
      return await api<T>(caminho, { token: null });
    } catch (e) {
      const temporario = !(e instanceof ErroApi) || e.status >= 500;
      if (!temporario || i >= tentativas) throw e;
      await new Promise((r) => setTimeout(r, 800 * i));
    }
  }
}
