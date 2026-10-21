import { useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ErroApi, sessaoAtual } from '../api';
import type { EventoAgenda } from '../tipos';
import { hora, isoDia, MESES, NOMES_STATUS } from '../util';
import { Erro, useDados } from './comum';

const DIAS = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom'];

/** Segunda-feira da semana de uma data. */
function segunda(d: Date) {
  const r = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  r.setDate(r.getDate() - ((r.getDay() + 6) % 7));
  return r;
}
const somar = (d: Date, dias: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + dias);

/** Eventos que tocam o dia (bloqueios de vários dias aparecem em todos). */
function doDia(eventos: EventoAgenda[], dia: string) {
  return eventos.filter((e) => e.inicio.slice(0, 10) <= dia && e.fim.slice(0, 10) >= dia
    && !(e.fim.slice(0, 10) === dia && e.fim.slice(11, 16) === '00:00' && e.inicio.slice(0, 10) < dia));
}

export function Agenda() {
  const hoje = isoDia(new Date());
  const [visao, setVisao] = useState<'mes' | 'semana'>(() => (window.matchMedia('(max-width: 640px)').matches ? 'semana' : 'mes'));
  const [ref, setRef] = useState(() => new Date());
  const [dia, setDia] = useState(hoje);
  const [bloqueando, setBloqueando] = useState(false);

  const dias = useMemo(() => {
    if (visao === 'semana') {
      const ini = segunda(ref);
      return Array.from({ length: 7 }, (_, i) => somar(ini, i));
    }
    const ini = segunda(new Date(ref.getFullYear(), ref.getMonth(), 1));
    const ultimo = new Date(ref.getFullYear(), ref.getMonth() + 1, 0);
    const fim = somar(segunda(ultimo), 6);
    const lista: Date[] = [];
    for (let d = ini; d <= fim; d = somar(d, 1)) lista.push(d);
    return lista;
  }, [visao, ref]);

  const de = isoDia(dias[0]), ate = isoDia(dias[dias.length - 1]);
  const { dados: eventos, erro, recarregar } = useDados<EventoAgenda[]>(`/agenda?de=${de}&ate=${ate}`);

  const mover = (n: number) => {
    const d = visao === 'semana' ? somar(ref, 7 * n) : new Date(ref.getFullYear(), ref.getMonth() + n, 1);
    setRef(d);
    setDia(isoDia(visao === 'semana' ? segunda(d) : d));
  };
  const irHoje = () => { setRef(new Date()); setDia(hoje); };

  const titulo = visao === 'mes'
    ? `${MESES[ref.getMonth()]} ${ref.getFullYear()}`
    : (() => {
      const a = dias[0], b = dias[6];
      return a.getMonth() === b.getMonth()
        ? `${a.getDate()} a ${b.getDate()} de ${MESES[b.getMonth()].toLowerCase()}`
        : `${a.getDate()} ${MESES[a.getMonth()].slice(0, 3).toLowerCase()} a ${b.getDate()} ${MESES[b.getMonth()].slice(0, 3).toLowerCase()}`;
    })();

  const lista = eventos ?? [];
  const doSelecionado = doDia(lista, dia);

  return (
    <>
      <div className="cabeca-pagina">
        <div><span className="rotulo">Agenda</span><h1 className="titulo">{titulo}</h1></div>
        <div className="acoes-linha">
          <div className="abas" role="group" aria-label="Visão">
            <button className="botao pequeno" aria-pressed={visao === 'mes'} onClick={() => setVisao('mes')}>Mês</button>
            <button className="botao pequeno" aria-pressed={visao === 'semana'} onClick={() => setVisao('semana')}>Semana</button>
          </div>
          <div className="acoes-linha" style={{ gap: 4 }}>
            <button className="botao pequeno" aria-label="Anterior" onClick={() => mover(-1)}>‹</button>
            <button className="botao pequeno" onClick={irHoje}>Hoje</button>
            <button className="botao pequeno" aria-label="Próximo" onClick={() => mover(1)}>›</button>
          </div>
        </div>
      </div>
      <Erro msg={erro} />

      {visao === 'mes' ? (
        <div className="calendario" role="grid" aria-label={titulo}>
          {DIAS.map((d) => <div key={d} className="cal-cab" role="columnheader">{d}</div>)}
          {dias.map((d) => {
            const iso = isoDia(d);
            const ev = doDia(lista, iso);
            const fora = d.getMonth() !== ref.getMonth();
            const bloqueado = ev.some((e) => e.tipo === 'BLOQUEIO');
            return (
              <button key={iso} role="gridcell" aria-selected={iso === dia}
                className={`cal-dia${fora ? ' fora' : ''}${iso === hoje ? ' hoje' : ''}${bloqueado ? ' bloqueado' : ''}`}
                onClick={() => setDia(iso)} aria-label={`${d.getDate()} de ${MESES[d.getMonth()]}: ${ev.length ? `${ev.length} na agenda` : 'livre'}`}>
                <span className="cal-num">{d.getDate()}</span>
                <span className="cal-eventos">
                  {ev.slice(0, 3).map((e) => (
                    <span key={`${e.tipo}${e.id}`} className={`cal-ev ${e.tipo === 'BLOQUEIO' ? 'bloq' : ''}`}>
                      {e.tipo === 'TRABALHO' && <b>{hora(e.inicio)}</b>} {e.titulo}
                    </span>
                  ))}
                  {ev.length > 3 && <span className="cal-mais">+{ev.length - 3}</span>}
                </span>
                {ev.length > 0 && <span className="cal-pontos" aria-hidden="true">{ev.slice(0, 4).map((e) => <i key={`${e.tipo}${e.id}`} className={e.tipo === 'BLOQUEIO' ? 'bloq' : ''} />)}</span>}
              </button>
            );
          })}
        </div>
      ) : (
        <div className="semana escalonado">
          {dias.map((d) => {
            const iso = isoDia(d);
            const ev = doDia(lista, iso);
            return (
              <section key={iso} className={`semana-dia${iso === hoje ? ' hoje' : ''}`}>
                <button className="semana-data" onClick={() => setDia(iso)} aria-pressed={iso === dia}>
                  <span className="legenda">{DIAS[(d.getDay() + 6) % 7].toUpperCase()}</span>
                  <strong>{d.getDate()}</strong>
                </button>
                <div className="semana-eventos">
                  {ev.length === 0 ? <span className="legenda" style={{ alignSelf: 'center' }}>LIVRE</span> : ev.map((e) => <ItemEvento key={`${e.tipo}${e.id}`} e={e} dia={iso} />)}
                </div>
              </section>
            );
          })}
        </div>
      )}

      <section className="secao">
        <div className="cabeca-pagina">
          <h2 className="subtitulo">{(() => { const [a, m, d] = dia.split('-').map(Number); return `${d} de ${MESES[m - 1].toLowerCase()}${a !== new Date().getFullYear() ? ` de ${a}` : ''}`; })()}</h2>
          <div className="acoes-linha">
            {sessaoAtual()?.perfil === 'ADMIN' && <Link className="botao pequeno" to={`/painel/trabalhos/novo?data=${dia}`}>Agendar ensaio</Link>}
            <button className="botao pequeno" onClick={() => setBloqueando((b) => !b)} aria-expanded={bloqueando}>Bloquear data</button>
          </div>
        </div>
        {bloqueando && <NovoBloqueio dia={dia} aoSalvar={() => { setBloqueando(false); recarregar(); }} />}
        {doSelecionado.length === 0
          ? <p className="vazio" style={{ margin: 0 }}>Nada marcado neste dia.</p>
          : <div className="linhas escalonado">{doSelecionado.map((e) => <LinhaEvento key={`${e.tipo}${e.id}`} e={e} aoMudar={recarregar} />)}</div>}
      </section>
    </>
  );
}

function ItemEvento({ e, dia }: { e: EventoAgenda; dia: string }) {
  const diaInteiro = e.inicio.slice(0, 10) < dia || (e.fim.slice(0, 10) > dia && e.inicio.slice(11, 16) === '00:00');
  const conteudo = (
    <>
      <span className="legenda">{diaInteiro ? 'DIA TODO' : `${hora(e.inicio)}–${hora(e.fim)}`}</span>
      <span>{e.titulo}</span>
    </>
  );
  return e.tipo === 'TRABALHO'
    ? <Link to={`/painel/trabalhos/${e.id}`} className="ev-cartao">{conteudo}</Link>
    : <span className="ev-cartao bloq">{conteudo}</span>;
}

function LinhaEvento({ e, aoMudar }: { e: EventoAgenda; aoMudar: () => void }) {
  const dm = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
  // Bloqueio de dia inteiro vai de 00:00 a 00:00 do dia seguinte ao último dia.
  const diasInteiros = hora(e.inicio) === '00:00' && hora(e.fim) === '00:00';
  const ultimoDia = diasInteiros ? (() => { const d = new Date(`${e.fim.slice(0, 10)}T12:00:00`); d.setDate(d.getDate() - 1); return isoDia(d); })() : '';
  const periodo = diasInteiros
    ? (ultimoDia === e.inicio.slice(0, 10) ? 'Dia inteiro' : `Dias inteiros, de ${dm(e.inicio)} a ${dm(ultimoDia)}`)
    : e.inicio.slice(0, 10) === e.fim.slice(0, 10)
      ? `${hora(e.inicio)} às ${hora(e.fim)}`
      : `${dm(e.inicio)} ${hora(e.inicio)} até ${dm(e.fim)} ${hora(e.fim)}`;
  if (e.tipo === 'BLOQUEIO') {
    const liberar = async () => { await api(`/agenda/bloqueios/${e.id}`, { metodo: 'DELETE' }); aoMudar(); };
    return (
      <div className="linha-item">
        <span className="principal">{e.titulo}</span>
        <span className="lado"><span className="etiqueta">Bloqueado</span></span>
        <span className="legenda">{periodo}</span>
        <span className="lado"><button className="botao-link" style={{ fontSize: 13 }} onClick={liberar}>Liberar</button></span>
      </div>
    );
  }
  return (
    <Link to={`/painel/trabalhos/${e.id}`} className="linha-item">
      <span className="principal">{e.titulo}</span>
      <span className="lado">{e.status && <span className="etiqueta">{NOMES_STATUS[e.status]}</span>}</span>
      <span className="legenda">{e.detalhe}</span>
      <span className="legenda lado">{periodo}</span>
    </Link>
  );
}

function NovoBloqueio({ dia, aoSalvar }: { dia: string; aoSalvar: () => void }) {
  const [inteiro, setInteiro] = useState(true);
  const [erro, setErro] = useState('');
  async function salvar(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    const f = new FormData(ev.currentTarget);
    const de = String(f.get('de')), ate = String(f.get('ate') || f.get('de'));
    const inicio = inteiro ? `${de}T00:00:00` : `${de}:00`;
    const fim = inteiro ? `${isoDia(somar(new Date(`${ate}T12:00:00`), 1))}T00:00:00` : `${ate}:00`;
    try {
      await api('/agenda/bloqueios', { corpo: { inicio, fim, motivo: f.get('motivo') } });
      aoSalvar();
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível bloquear.');
    }
  }
  return (
    <form className="painel-caixa grade-form duas anima-entrada" onSubmit={salvar}>
      <label className="marcar inteira"><input id="bl-inteiro" type="checkbox" checked={inteiro} onChange={(e) => setInteiro(e.target.checked)} /> Dia inteiro</label>
      <label className="campo"><span>De</span><input id="bl-de" name="de" required type={inteiro ? 'date' : 'datetime-local'} key={`de${inteiro}`} defaultValue={inteiro ? dia : `${dia}T08:00`} /></label>
      <label className="campo"><span>Até</span><input id="bl-ate" name="ate" type={inteiro ? 'date' : 'datetime-local'} key={`ate${inteiro}`} defaultValue={inteiro ? dia : `${dia}T18:00`} /></label>
      <label className="campo inteira"><span>Motivo</span><input id="bl-motivo" name="motivo" required maxLength={120} placeholder="Viagem, folga, outro compromisso" /></label>
      <Erro msg={erro} />
      <button className="botao principal inteira">Bloquear</button>
    </form>
  );
}
