import { useEffect, useState } from 'react';
import { api, ErroApi } from '../api';
import type { ConfigAgenda, HorarioAtendimento } from '../tipos';
import { DIAS_SEMANA, urlAgendar } from '../util';
import { Erro, useDados } from './comum';

type Num = Exclude<keyof ConfigAgenda, 'ativo' | 'horarios'>;

const CAMPOS: { k: Num; rotulo: string; dica: string }[] = [
  { k: 'sinalPercentual', rotulo: 'Sinal (% do pacote)', dica: 'Valor que a cliente paga no Pix para garantir a data.' },
  { k: 'reservaHoras', rotulo: 'Pré-reserva segura por (horas)', dica: 'Sem comprovante nesse prazo, o horário volta a ficar livre.' },
  { k: 'antecedenciaHoras', rotulo: 'Antecedência mínima (horas)', dica: 'Ninguém agenda para daqui a menos que isso.' },
  { k: 'horizonteDias', rotulo: 'Agenda aberta até (dias à frente)', dica: '' },
  { k: 'passoMin', rotulo: 'Horários a cada (min)', dica: 'Ex.: 60 oferece 08:00, 09:00, 10:00…' },
  { k: 'intervaloMin', rotulo: 'Folga entre ensaios (min)', dica: 'Tempo livre antes e depois de cada ensaio.' },
  { k: 'remarcacaoHoras', rotulo: 'Remarcar até (horas antes)', dica: '' },
  { k: 'remarcacoesMax', rotulo: 'Remarcações permitidas', dica: '' },
  { k: 'reembolsoDias', rotulo: 'Devolve o sinal se cancelar até (dias antes)', dica: '' },
];

/** Ajustes > Agenda online: liga a agenda do site, horários de atendimento e regras do contrato. */
export function AgendaOnlineAjustes() {
  const { dados, erro } = useDados<ConfigAgenda>('/estudio/agenda-online');
  const [c, setC] = useState<ConfigAgenda | null>(null);
  const [falha, setFalha] = useState('');
  const [salvo, setSalvo] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [copiado, setCopiado] = useState(false);

  useEffect(() => {
    if (dados) setC({ ...dados, horarios: dados.horarios.map((h) => ({ ...h, inicio: h.inicio.slice(0, 5), fim: h.fim.slice(0, 5) })) });
  }, [dados]);

  if (!c) return <section className="secao"><Erro msg={erro} />{!erro && <div className="esqueleto" style={{ height: 200 }} />}</section>;

  const mudar = (p: Partial<ConfigAgenda>) => { setC({ ...c, ...p }); setSalvo(false); };
  const doDia = (d: number) => c.horarios.map((h, i) => ({ h, i })).filter((x) => x.h.diaSemana === d);
  const trocar = (i: number, p: Partial<HorarioAtendimento>) => mudar({ horarios: c.horarios.map((h, j) => (j === i ? { ...h, ...p } : h)) });
  const tirar = (i: number) => mudar({ horarios: c.horarios.filter((_, j) => j !== i) });
  const adicionar = (d: number) => {
    const ult = doDia(d).length > 0;
    mudar({ horarios: [...c.horarios, ult ? { diaSemana: d, inicio: '14:00', fim: '18:00' } : { diaSemana: d, inicio: '08:00', fim: '12:00' }] });
  };

  async function salvar() {
    setSalvando(true);
    setFalha('');
    try {
      const r = await api<ConfigAgenda>('/estudio/agenda-online', { metodo: 'PUT', corpo: c });
      setC({ ...r, horarios: r.horarios.map((h) => ({ ...h, inicio: h.inicio.slice(0, 5), fim: h.fim.slice(0, 5) })) });
      setSalvo(true);
    } catch (e) {
      setFalha(e instanceof ErroApi ? e.message : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  async function copiar() {
    try { await navigator.clipboard.writeText(urlAgendar()); setCopiado(true); setTimeout(() => setCopiado(false), 2000); } catch { /* sem permissão */ }
  }

  return (
    <section className="secao">
      <div className="painel-caixa ao-liga">
        <label className="marcar"><input id="ao-ativo" type="checkbox" checked={c.ativo} onChange={(e) => mudar({ ativo: e.target.checked })} />
          <strong>Agenda online ligada</strong></label>
        <p className="suave" style={{ margin: 0 }}>
          Com a agenda ligada, a cliente escolhe pacote, data e horário no site, paga o sinal no Pix e envia o comprovante.
          O horário fica pré-reservado e você confirma na Agenda. Só aparecem pacotes marcados como “Disponível na agenda online”.
        </p>
        <div className="acoes-linha">
          <code className="ao-link">{urlAgendar()}</code>
          <button type="button" className="botao pequeno" onClick={copiar}>{copiado ? 'Copiado!' : 'Copiar link'}</button>
          <a className="botao pequeno" href="/agendar" target="_blank" rel="noreferrer">Abrir</a>
        </div>
      </div>

      <h2 className="subtitulo">Horários de atendimento</h2>
      <div className="ao-semana">
        {[1, 2, 3, 4, 5, 6, 7].map((d) => (
          <div key={d} className="ao-dia">
            <strong>{DIAS_SEMANA[d]}</strong>
            <div className="ao-faixas">
              {doDia(d).length === 0 && <span className="legenda">FECHADO</span>}
              {doDia(d).map(({ h, i }) => (
                <span key={i} className="ao-faixa">
                  <input type="time" aria-label={`${DIAS_SEMANA[d]} início`} value={h.inicio} onChange={(e) => trocar(i, { inicio: e.target.value })} />
                  <span>às</span>
                  <input type="time" aria-label={`${DIAS_SEMANA[d]} fim`} value={h.fim} onChange={(e) => trocar(i, { fim: e.target.value })} />
                  <button type="button" className="botao-link" aria-label="Remover faixa" onClick={() => tirar(i)}>×</button>
                </span>
              ))}
              <button type="button" className="botao pequeno" onClick={() => adicionar(d)}>+ faixa</button>
            </div>
          </div>
        ))}
      </div>

      <h2 className="subtitulo">Regras</h2>
      <div className="painel-caixa grade-form duas">
        {CAMPOS.map(({ k, rotulo, dica }) => (
          <label key={k} className="campo"><span>{rotulo}</span>
            <input id={`ao-${k}`} type="number" min={0} inputMode="numeric" value={c[k]} onChange={(e) => mudar({ [k]: Number(e.target.value) } as Partial<ConfigAgenda>)} />
            {dica && <small className="legenda" style={{ textTransform: 'none', letterSpacing: 0 }}>{dica}</small>}
          </label>
        ))}
      </div>

      <Erro msg={falha} />
      {salvo && <p className="aviso sucesso" role="status" style={{ margin: 0 }}>Agenda online salva.</p>}
      <div><button className="botao principal" disabled={salvando} onClick={salvar}>{salvando ? 'Salvando…' : 'Salvar agenda online'}</button></div>
    </section>
  );
}
