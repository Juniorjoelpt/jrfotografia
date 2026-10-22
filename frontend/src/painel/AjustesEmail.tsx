import { useEffect, useState, type FormEvent } from 'react';
import { api, ErroApi } from '../api';
import { dataHora } from '../util';
import { Erro, useDados } from './comum';

interface ConfigEmail {
  ativo: boolean; host?: string | null; porta?: number | null; usuario?: string | null; temSenha: boolean; seguranca: 'STARTTLS' | 'SSL' | 'NENHUMA';
  remetente?: string | null; avisos: boolean; lembretes: boolean; emailEstudio?: string | null;
}
interface EmailEnviado { id: number; para: string; assunto: string; tipo: string; status: 'PENDENTE' | 'ENVIADO' | 'FALHOU' | 'CANCELADO'; erro?: string | null; criadoEm: string }

const PROVEDORES = [
  { nome: 'Gmail', host: 'smtp.gmail.com', porta: 587, seguranca: 'STARTTLS',
    dica: 'No Gmail, use uma SENHA DE APP (não a senha normal): Conta Google › Segurança › Verificação em duas etapas (ligada) › Senhas de app › criar “JR Fotografia”. Copie os 16 caracteres.' },
  { nome: 'Outlook / Hotmail', host: 'smtp-mail.outlook.com', porta: 587, seguranca: 'STARTTLS',
    dica: 'Use o e-mail e a senha da conta Microsoft. Se a conta tiver verificação em duas etapas, crie uma senha de app em account.microsoft.com › Segurança.' },
  { nome: 'Brevo', host: 'smtp-relay.brevo.com', porta: 587, seguranca: 'STARTTLS',
    dica: 'No Brevo: SMTP e API › Chaves SMTP. O usuário é o “login SMTP” mostrado lá e a senha é a chave SMTP. Use como remetente um e-mail verificado no Brevo.' },
] as const;

const NOMES_TIPO: Record<string, string> = {
  TESTE: 'Teste', PRE_RESERVA: 'Pré-reserva', AVISO_PRE_RESERVA: 'Aviso: nova pré-reserva', AVISO_COMPROVANTE: 'Aviso: comprovante',
  RESERVA_CONFIRMADA: 'Ensaio confirmado', RESERVA_RECUSADA: 'Reserva recusada', REMARCACAO: 'Remarcação', AVISO_REMARCACAO: 'Aviso: remarcação',
  CANCELAMENTO: 'Cancelamento', AVISO_CANCELAMENTO: 'Aviso: cancelamento', CONTRATO: 'Contrato para assinar', CONTRATO_ASSINADO: 'Contrato assinado',
  AVISO_CONTRATO_ASSINADO: 'Aviso: contrato assinado', GALERIA_SELECAO: 'Galeria para escolher', GALERIA_ENTREGA: 'Galeria entregue',
  AVISO_SELECAO: 'Aviso: seleção concluída', ENTREGA: 'Fotos prontas', AVISO_PEDIDO: 'Aviso: pedido de orçamento', ORCAMENTO: 'Orçamento',
  AVISO_ORCAMENTO: 'Aviso: orçamento respondido', ALBUM: 'Álbum para aprovar', AVISO_ALBUM: 'Aviso: álbum', LEMBRETE_ENSAIO: 'Lembrete do ensaio',
  COBRANCA_LEMBRETE: 'Lembrete de pagamento', COBRANCA_VENCIDA: 'Pagamento em aberto', AVISO_PIX: 'Aviso: Pix recebido',
};

/** Ajustes › E-mail: servidor SMTP, o que mandar, teste e histórico dos envios. */
export function AjustesEmail() {
  const { dados, erro, setDados } = useDados<ConfigEmail>('/estudio/email');
  const { dados: historico, recarregar } = useDados<EmailEnviado[]>('/estudio/email/historico');
  const [host, setHost] = useState('');
  const [porta, setPorta] = useState('587');
  const [seguranca, setSeguranca] = useState<'STARTTLS' | 'SSL' | 'NENHUMA'>('STARTTLS');
  const [dica, setDica] = useState('');
  const [falha, setFalha] = useState('');
  const [salvo, setSalvo] = useState(false);
  const [para, setPara] = useState('');
  const [teste, setTeste] = useState<{ ok: boolean; msg: string } | null>(null);
  const [testando, setTestando] = useState(false);

  useEffect(() => {
    if (!dados) return;
    setHost(dados.host ?? '');
    setPorta(String(dados.porta ?? 587));
    setSeguranca(dados.seguranca);
    setPara((atual) => atual || dados.emailEstudio || dados.usuario || '');
    setDica(PROVEDORES.find((p) => p.host === dados.host)?.dica ?? '');
  }, [dados]);

  if (!dados) return <section className="secao"><Erro msg={erro} /></section>;

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setFalha('');
    setSalvo(false);
    try {
      setDados(await api<ConfigEmail>('/estudio/email', {
        metodo: 'PUT',
        corpo: { ativo: f.get('ativo') === 'on', host: host || null, porta: Number(porta) || null, usuario: f.get('usuario') || null,
          senha: f.get('senha') || null, seguranca, remetente: f.get('remetente') || null, avisos: f.get('avisos') === 'on', lembretes: f.get('lembretes') === 'on' },
      }));
      setSalvo(true);
      (e.target as HTMLFormElement).querySelector<HTMLInputElement>('input[name=senha]')!.value = '';
    } catch (ex) {
      setFalha(ex instanceof ErroApi ? ex.message : 'Não foi possível salvar.');
    }
  }

  async function testar() {
    setTestando(true);
    setTeste(null);
    try {
      await api('/estudio/email/teste', { corpo: { para } });
      setTeste({ ok: true, msg: `Enviado para ${para}. Confira a caixa de entrada (e o spam).` });
      recarregar();
    } catch (ex) {
      setTeste({ ok: false, msg: ex instanceof ErroApi ? ex.message : 'Não foi possível enviar.' });
    } finally {
      setTestando(false);
    }
  }

  return (
    <section className="secao">
      <form className="painel-caixa grade-form duas" onSubmit={salvar} onChange={() => setSalvo(false)}>
        <label className="marcar inteira"><input id="em-ativo" type="checkbox" name="ativo" defaultChecked={dados.ativo} /> <strong>Enviar e-mails automáticos</strong></label>
        <p className="suave inteira" style={{ margin: 0 }}>
          O sistema manda e-mail para clientes que informaram e-mail (pré-reserva, confirmação, contrato, fotos prontas, lembretes) e avisos para você.
          O WhatsApp continua como está. Use a conta de e-mail do estúdio:
        </p>
        <div className="abas inteira" role="group" aria-label="Provedor">
          {PROVEDORES.map((p) => (
            <button key={p.nome} type="button" className="botao pequeno" aria-pressed={host === p.host}
              onClick={() => { setHost(p.host); setPorta(String(p.porta)); setSeguranca(p.seguranca); setDica(p.dica); setSalvo(false); }}>{p.nome}</button>
          ))}
          <button type="button" className="botao pequeno" aria-pressed={!!host && !PROVEDORES.some((p) => p.host === host)}
            onClick={() => { setHost(''); setDica('Use os dados de SMTP do seu provedor (servidor, porta e segurança).'); }}>Outro</button>
        </div>
        {dica && <p className="aviso inteira" style={{ margin: 0 }}>{dica}</p>}
        <label className="campo"><span>Servidor SMTP</span><input id="em-host" value={host} onChange={(e) => setHost(e.target.value)} placeholder="smtp.gmail.com" maxLength={120} /></label>
        <div className="grade-form duas" style={{ gap: 'var(--espaco-2)' }}>
          <label className="campo"><span>Porta</span><input id="em-porta" value={porta} onChange={(e) => setPorta(e.target.value)} inputMode="numeric" maxLength={5} /></label>
          <label className="campo"><span>Segurança</span>
            <select id="em-seg" value={seguranca} onChange={(e) => setSeguranca(e.target.value as typeof seguranca)}>
              <option value="STARTTLS">STARTTLS (587)</option><option value="SSL">SSL (465)</option><option value="NENHUMA">Nenhuma</option>
            </select>
          </label>
        </div>
        <label className="campo"><span>Usuário (e-mail da conta)</span><input id="em-usuario" name="usuario" defaultValue={dados.usuario ?? ''} autoComplete="off" maxLength={160} /></label>
        <label className="campo"><span>Senha {dados.temSenha ? '(guardada; preencha só para trocar)' : '(senha de app)'}</span>
          <input id="em-senha" name="senha" type="password" autoComplete="new-password" maxLength={200} placeholder={dados.temSenha ? '••••••••••••' : ''} /></label>
        <label className="campo inteira"><span>Remetente (opcional; padrão = usuário)</span><input id="em-remetente" name="remetente" defaultValue={dados.remetente ?? ''} maxLength={160} placeholder="contato@jrfotografiagnf.com" /></label>
        <label className="marcar"><input type="checkbox" name="avisos" defaultChecked={dados.avisos} /> Me avisar por e-mail (nova pré-reserva, comprovante, contrato assinado…)</label>
        <label className="marcar"><input type="checkbox" name="lembretes" defaultChecked={dados.lembretes} /> Lembretes para clientes (ensaio amanhã, pagamento vencendo)</label>
        <p className="legenda inteira" style={{ margin: 0 }}>
          OS AVISOS VÃO PARA {dados.emailEstudio ? dados.emailEstudio.toUpperCase() : 'O E-MAIL DO ESTÚDIO (PREENCHA EM DADOS E CONTATO)'}. AS RESPOSTAS DAS CLIENTES TAMBÉM CHEGAM NESSE E-MAIL.
        </p>
        <Erro msg={falha} />
        {salvo && <p className="aviso sucesso inteira" style={{ margin: 0 }}>Configuração salva.</p>}
        <div className="inteira"><button className="botao principal">Salvar</button></div>
      </form>

      <div className="painel-caixa" style={{ display: 'grid', gap: 'var(--espaco-2)' }}>
        <h2 className="chamada">Testar</h2>
        <p className="suave" style={{ margin: 0 }}>Salve antes de testar. O teste funciona mesmo com o envio automático desligado.</p>
        <div className="acoes-linha">
          <input id="em-para" type="email" value={para} onChange={(e) => setPara(e.target.value)} placeholder="seu@email.com" style={{ flex: '1 1 220px', minHeight: 40 }} />
          <button className="botao" disabled={testando || !para || !dados.temSenha} onClick={testar}>{testando ? 'Enviando…' : 'Enviar e-mail de teste'}</button>
        </div>
        {teste && <p className={`aviso ${teste.ok ? 'sucesso' : 'erro'}`} role="status" style={{ margin: 0 }}>{teste.msg}</p>}
      </div>

      <h2 className="subtitulo">Últimos e-mails</h2>
      {historico && (historico.length === 0 ? <p className="vazio" style={{ margin: 0 }}>Nenhum e-mail enviado ainda.</p> : (
        <div className="linhas">
          {historico.map((m) => (
            <div key={m.id} className="linha-item">
              <span className="principal">{m.assunto}</span>
              <span className="lado"><span className={`etiqueta${m.status === 'ENVIADO' ? ' sucesso' : m.status === 'FALHOU' ? ' erro' : ''}`}>
                {m.status === 'ENVIADO' ? 'Enviado' : m.status === 'FALHOU' ? 'Falhou' : m.status === 'CANCELADO' ? 'Cancelado' : 'Na fila'}</span></span>
              <span className="legenda">{(NOMES_TIPO[m.tipo] ?? m.tipo).toUpperCase()} · {m.para} · {dataHora(m.criadoEm).toUpperCase()}</span>
              {m.erro && <span className="legenda" style={{ gridColumn: '1 / -1', color: 'var(--erro)' }}>{m.erro}</span>}
            </div>
          ))}
        </div>
      ))}
    </section>
  );
}
