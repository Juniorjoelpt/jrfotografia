import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ErroApi } from '../api';
import type { TrabalhoResumo } from '../tipos';
import { CLASSE_STATUS, dataHora, NOMES_STATUS, data } from '../util';

/** Carrega dados da API e expõe recarregar, carregando e erro. */
export function useDados<T>(caminho: string | null) {
  const [dados, setDados] = useState<T | null>(null);
  const [erro, setErro] = useState('');
  const [carregando, setCarregando] = useState(true);
  const recarregar = useCallback(async () => {
    if (!caminho) return;
    setCarregando(true);
    try {
      setDados(await api<T>(caminho));
      setErro('');
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível carregar.');
    } finally {
      setCarregando(false);
    }
  }, [caminho]);
  useEffect(() => { recarregar(); }, [recarregar]);
  return { dados, setDados, erro, carregando, recarregar };
}

export function Erro({ msg }: { msg: string }) {
  return msg ? <p className="aviso erro" role="alert" style={{ margin: 0 }}>{msg}</p> : null;
}

export function Status({ s }: { s: TrabalhoResumo['status'] }) {
  return <span className={`etiqueta ${CLASSE_STATUS[s]}`}>{NOMES_STATUS[s]}</span>;
}

export function LinhaTrabalho({ t, mostrar = 'data' }: { t: TrabalhoResumo; mostrar?: 'data' | 'prazo' }) {
  return (
    <Link to={`/painel/trabalhos/${t.id}`} className="linha-item">
      <span className="principal">{t.clienteNome}</span>
      <span className="lado">{t.atrasado ? <span className="etiqueta erro">Atrasado</span> : <Status s={t.status} />}</span>
      <span className="legenda">{t.pacoteNome ?? t.tipo ?? t.titulo}{t.localTrabalho ? ` · ${t.localTrabalho}` : ''}</span>
      <span className="legenda lado">{mostrar === 'prazo' ? `ENTREGA ${data(t.prazoEntrega)}` : dataHora(t.dataTrabalho)}</span>
    </Link>
  );
}

export function Linhas({ itens, vazio }: { itens: React.ReactNode[]; vazio: string }) {
  return itens.length ? <div className="linhas escalonado">{itens}</div> : <p className="vazio" style={{ margin: 0 }}>{vazio}</p>;
}
