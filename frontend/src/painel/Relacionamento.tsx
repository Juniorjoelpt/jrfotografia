import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import type { Estudio, Inativa, PesquisaPainel, ProximaData, ResumoPesquisas } from '../tipos';
import { data, linkWhatsapp, moeda, primeiroNome, telefone } from '../util';
import { Erro, useDados } from './comum';

type Aba = 'datas' | 'inativas' | 'pesquisas';

/** Relacionamento: aniversários e datas especiais, clientes sumidas e pesquisa de satisfação. */
export function Relacionamento() {
  const [aba, setAba] = useState<Aba>(() => (['datas', 'inativas', 'pesquisas'].includes(window.location.hash.slice(1)) ? window.location.hash.slice(1) as Aba : 'datas'));
  const trocar = (a: Aba) => { setAba(a); history.replaceState(null, '', `#${a}`); };
  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Relacionamento</span><h1 className="titulo">Clientes por perto</h1></div></div>
      <div className="abas" role="group" aria-label="Seções">
        <button className="botao pequeno" aria-pressed={aba === 'datas'} onClick={() => trocar('datas')}>Aniversários</button>
        <button className="botao pequeno" aria-pressed={aba === 'inativas'} onClick={() => trocar('inativas')}>Sem ensaio</button>
        <button className="botao pequeno" aria-pressed={aba === 'pesquisas'} onClick={() => trocar('pesquisas')}>Pesquisa</button>
      </div>
      {aba === 'datas' && <Datas />}
      {aba === 'inativas' && <Inativas />}
      {aba === 'pesquisas' && <Pesquisas />}
    </>
  );
}

function quandoTexto(d: ProximaData) {
  if (d.diasFaltando === 0) return 'HOJE';
  if (d.diasFaltando === 1) return 'AMANHÃ';
  return `EM ${d.diasFaltando} DIAS · ${data(d.data).slice(0, 5)}`;
}

function mensagemData(d: ProximaData, estudio?: Estudio | null) {
  const nome = primeiroNome(d.nome);
  if (d.tipo === 'ANIVERSARIO') {
    return d.diasFaltando === 0
      ? `Feliz aniversário, ${nome}! 🎉 Que seu novo ano seja lindo. Um abraço${estudio ? ` da ${estudio.nome}` : ''}!`
      : `Oi, ${nome}! Seu aniversário está chegando 🎉 Que tal registrar esse momento com um ensaio especial? Tenho horários livres.`;
  }
  return `Oi, ${nome}! Lembrei que ${d.descricao.toLowerCase()} está chegando${d.anos ? ` (${d.anos} anos!)` : ''}. Que tal eternizar essa data com um ensaio?`;
}

function Datas() {
  const [dias, setDias] = useState(30);
  const { dados, erro } = useDados<ProximaData[]>(`/relacionamento/datas?dias=${dias}`);
  const { dados: estudio } = useDados<Estudio>('/estudio');
  return (
    <section className="secao">
      <div className="acoes-linha">
        <span className="legenda">PRÓXIMOS</span>
        {[7, 30, 60].map((d) => <button key={d} className="botao pequeno" aria-pressed={dias === d} onClick={() => setDias(d)}>{d} dias</button>)}
      </div>
      <Erro msg={erro} />
      <p className="legenda" style={{ margin: 0 }}>ANIVERSÁRIO VEM DO CADASTRO DA CLIENTE. OUTRAS DATAS (CASAMENTO, FILHOS) VOCÊ ADICIONA EM CLIENTES › ABRIR A CLIENTE.</p>
      {dados && (dados.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>Nenhuma data nos próximos {dias} dias.</p>
        : (
          <div className="linhas escalonado">
            {dados.map((d) => (
              <div key={`${d.tipo}${d.clienteId}${d.dataId ?? ''}`} className="linha-item">
                <span className="principal">{d.nome}</span>
                <span className="lado"><span className={`etiqueta${d.diasFaltando === 0 ? ' alerta' : ''}`}>{quandoTexto(d)}</span></span>
                <span className="legenda">{d.descricao.toUpperCase()}{d.anos ? ` · ${d.anos} ANOS` : ''}{d.telefone ? ` · ${telefone(d.telefone)}` : ''}</span>
                <span className="lado">
                  {d.telefone && <a className="botao pequeno principal" href={linkWhatsapp(d.telefone, mensagemData(d, estudio))} target="_blank" rel="noreferrer">Mandar mensagem</a>}
                </span>
              </div>
            ))}
          </div>
        ))}
    </section>
  );
}

function Inativas() {
  const { dados, erro, recarregar } = useDados<Inativa[]>('/relacionamento/inativos?meses=12');
  const convidar = async (i: Inativa) => {
    await api(`/clientes/${i.clienteId}/contato`, { metodo: 'POST' });
    recarregar();
  };
  return (
    <section className="secao">
      <p className="suave" style={{ margin: 0 }}>Clientes cujo último ensaio foi há mais de 12 meses. Quem gastou mais e ainda não foi chamada aparece primeiro.</p>
      <Erro msg={erro} />
      {dados && (dados.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>Nenhuma cliente sumida. Ótimo sinal!</p>
        : (
          <div className="linhas escalonado">
            {dados.map((i) => {
              const msg = `Oi, ${primeiroNome(i.nome)}! Quanto tempo! Já faz ${i.mesesSemEnsaio} meses do seu último ensaio (${i.ultimoTrabalho.split(' · ')[0]}). Estou com horários novos e adoraria fotografar você de novo. Quer que eu te mande as opções?`;
              return (
                <div key={i.clienteId} className="linha-item">
                  <span className="principal">{i.nome}</span>
                  <span className="lado num">{moeda(i.totalPago)}</span>
                  <span className="legenda">
                    ÚLTIMO ENSAIO {data(i.ultimoEnsaio)} · {i.totalTrabalhos} {i.totalTrabalhos === 1 ? 'TRABALHO' : 'TRABALHOS'}
                    {i.ultimoContato ? ` · CHAMADA EM ${data(i.ultimoContato)}` : ''}
                  </span>
                  <span className="lado">
                    {i.telefone && (
                      <a className={`botao pequeno${i.ultimoContato ? '' : ' principal'}`} href={linkWhatsapp(i.telefone, msg)} target="_blank" rel="noreferrer"
                        onClick={() => convidar(i)}>Convidar</a>
                    )}
                  </span>
                </div>
              );
            })}
          </div>
        ))}
    </section>
  );
}

const estrelas = (n?: number | null) => (n ? '★'.repeat(n) + '☆'.repeat(5 - n) : '—');

function Pesquisas() {
  const { dados, erro } = useDados<{ resumo: ResumoPesquisas; lista: PesquisaPainel[] }>('/relacionamento/pesquisas');
  if (!dados) return <Erro msg={erro} />;
  const { resumo: r, lista } = dados;
  const respondidas = lista.filter((p) => p.respondidaEm);
  return (
    <section className="secao">
      <div className="numeros">
        <div className="numero"><span className="legenda">NPS</span><strong>{r.nps ?? '—'}</strong></div>
        <div className="numero"><span className="legenda">RESPONDIDAS</span><strong>{r.respondidas}<small className="suave" style={{ fontSize: 14 }}> / {r.enviadas}</small></strong></div>
        <div className="numero"><span className="legenda">ATENDIMENTO</span><strong>{r.mediaAtendimento ?? '—'}</strong></div>
        <div className="numero"><span className="legenda">FOTOS</span><strong>{r.mediaFotos ?? '—'}</strong></div>
        <div className="numero"><span className="legenda">PRAZO</span><strong>{r.mediaPrazo ?? '—'}</strong></div>
      </div>
      <p className="legenda" style={{ margin: 0 }}>
        NPS = % QUE DARIA 9 OU 10 MENOS % QUE DARIA 0 A 6 ({r.promotores} PROMOTORES · {r.neutros} NEUTROS · {r.detratores} DETRATORES).
        A PESQUISA É CRIADA NA ENTREGA E APARECE NA GALERIA DA CLIENTE.
      </p>
      {respondidas.length === 0
        ? <p className="vazio" style={{ margin: 0 }}>Nenhuma resposta ainda.</p>
        : (
          <div className="linhas escalonado">
            {respondidas.map((p) => (
              <div key={p.id} className="linha-item">
                <Link className="principal" to={`/painel/trabalhos/${p.trabalhoId}`} style={{ color: 'var(--tinta)' }}>{p.cliente}</Link>
                <span className="lado"><span className={`etiqueta${(p.nota ?? 0) >= 9 ? ' sucesso' : (p.nota ?? 0) <= 6 ? ' erro' : ''}`}>Nota {p.nota}</span></span>
                <span className="legenda">ATENDIMENTO {estrelas(p.atendimento)} · FOTOS {estrelas(p.fotos)} · PRAZO {estrelas(p.prazo)} · {data(p.respondidaEm)}</span>
                <span className="lado legenda">{p.autorizaDepoimento ? 'PODE PUBLICAR' : ''}</span>
                {p.comentario && <p className="suave" style={{ gridColumn: '1 / -1', margin: 0, whiteSpace: 'pre-line' }}>“{p.comentario}”</p>}
              </div>
            ))}
          </div>
        )}
    </section>
  );
}
