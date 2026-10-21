import { useEffect, useState, type FormEvent } from 'react';
import QRCode from 'qrcode';
import { api, ErroApi, salvarSessao, sessaoAtual } from '../api';
import type { Situacao2fa } from '../tipos';
import { Erro, useDados } from './comum';

/** Minha conta › Verificação em duas etapas (app autenticador + códigos de recuperação). */
export function DuasEtapas() {
  const { dados: sit, recarregar } = useDados<Situacao2fa>('/conta/2fa');
  const [config, setConfig] = useState<{ segredo: string; otpauth: string } | null>(null);
  const [qr, setQr] = useState('');
  const [codigos, setCodigos] = useState<string[] | null>(null);
  const [modo, setModo] = useState<'desligar' | 'codigos' | null>(null);
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (config) QRCode.toDataURL(config.otpauth, { margin: 1, width: 220, color: { dark: '#171716', light: '#f4f3ef' } }).then(setQr).catch(() => setQr(''));
  }, [config]);

  const marcarSessao = (ativo: boolean) => { const s = sessaoAtual(); if (s) salvarSessao({ ...s, doisFatores: ativo }); };

  async function executar(acao: () => Promise<void>) {
    setEnviando(true);
    setErro('');
    try { await acao(); } catch (e) { setErro(e instanceof ErroApi ? e.message : 'Não foi possível concluir.'); } finally { setEnviando(false); }
  }

  const iniciar = () => executar(async () => setConfig(await api('/conta/2fa/iniciar', { metodo: 'POST' })));

  const ativar = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const codigo = new FormData(e.currentTarget).get('codigo');
    executar(async () => {
      const r = await api<{ codigos: string[] }>('/conta/2fa/ativar', { corpo: { codigo } });
      setCodigos(r.codigos);
      setConfig(null);
      marcarSessao(true);
      recarregar();
    });
  };

  const desligarOuGerar = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    executar(async () => {
      if (modo === 'desligar') {
        await api('/conta/2fa/desativar', { corpo: { senha: f.get('senha'), codigo: f.get('codigo') } });
        marcarSessao(false);
        setCodigos(null);
      } else {
        setCodigos((await api<{ codigos: string[] }>('/conta/2fa/codigos', { corpo: { codigo: f.get('codigo') } })).codigos);
      }
      setModo(null);
      recarregar();
    });
  };

  const baixar = () => {
    if (!codigos) return;
    const texto = `Códigos de recuperação · ${sessaoAtual()?.estudio ?? ''} · ${sessaoAtual()?.nome ?? ''}\nCada código vale uma vez, para entrar se você perder o celular.\n\n${codigos.join('\n')}\n`;
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([texto], { type: 'text/plain' }));
    a.download = 'codigos-de-recuperacao.txt';
    a.click();
    URL.revokeObjectURL(a.href);
  };

  if (!sit) return null;
  return (
    <section className="secao">
      <h2 className="chamada">Verificação em duas etapas</h2>
      <div className="painel-caixa" style={{ display: 'grid', gap: 'var(--espaco-3)' }}>
        {codigos && (
          <div className="de-codigos anima-entrada" role="status">
            <strong>Guarde estes códigos de recuperação.</strong>
            <span className="suave">Se perder o celular, cada código deixa você entrar uma vez. Eles não aparecem de novo.</span>
            <ol>{codigos.map((c) => <li key={c}><code>{c}</code></li>)}</ol>
            <div className="acoes-linha">
              <button className="botao pequeno principal" onClick={baixar}>Baixar arquivo</button>
              <button className="botao pequeno" onClick={() => setCodigos(null)}>Já guardei</button>
            </div>
          </div>
        )}

        {!sit.ativo && !config && (
          <>
            <p style={{ margin: 0 }}><span className="etiqueta">Desligada</span></p>
            <p className="suave" style={{ margin: 0 }}>
              Além da senha, o painel pede um código de 6 dígitos que muda a cada 30 segundos no seu celular
              (Google Authenticator, Microsoft Authenticator ou similar). Mesmo que alguém descubra a senha, não entra.
            </p>
            <Erro msg={erro} />
            <div><button className="botao principal" disabled={enviando} onClick={iniciar}>Ligar verificação em duas etapas</button></div>
          </>
        )}

        {config && (
          <form className="de-config anima-entrada" onSubmit={ativar}>
            <ol className="de-passos">
              <li>Instale um app autenticador no celular (Google Authenticator, Microsoft Authenticator, Authy…).</li>
              <li>No app, toque em <b>adicionar</b> e escaneie o QR code. Se preferir, digite a chave.</li>
              <li>Digite o código de 6 dígitos que aparece no app.</li>
            </ol>
            <div className="de-qr">
              {qr ? <img src={qr} alt="QR code para o app autenticador" width={220} height={220} /> : <div className="esqueleto" style={{ width: 220, height: 220 }} />}
              <div style={{ display: 'grid', gap: 6 }}>
                <span className="legenda">CHAVE</span>
                <code className="de-chave">{config.segredo}</code>
                <a className="botao pequeno" href={config.otpauth}>Abrir no app (neste celular)</a>
              </div>
            </div>
            <label className="campo"><span>Código do app</span>
              <input id="de-codigo" name="codigo" required inputMode="numeric" autoComplete="one-time-code" maxLength={7} placeholder="000000"
                style={{ fontSize: 22, letterSpacing: '.3em', maxWidth: 220 }} /></label>
            <Erro msg={erro} />
            <div className="acoes-linha">
              <button className="botao principal" disabled={enviando}>{enviando ? 'Conferindo…' : 'Confirmar e ligar'}</button>
              <button type="button" className="botao" onClick={() => { setConfig(null); setErro(''); }}>Cancelar</button>
            </div>
          </form>
        )}

        {sit.ativo && (
          <>
            <p style={{ margin: 0 }}><span className="etiqueta sucesso">Ligada</span>{' '}
              <span className="legenda">{sit.codigosRestantes} {sit.codigosRestantes === 1 ? 'CÓDIGO' : 'CÓDIGOS'} DE RECUPERAÇÃO DISPONÍVEIS</span></p>
            {sit.codigosRestantes <= 3 && <p className="aviso" style={{ margin: 0, borderColor: 'var(--alerta)' }}>Restam poucos códigos de recuperação. Gere novos.</p>}
            {modo ? (
              <form className="grade-form duas anima-entrada" onSubmit={desligarOuGerar}>
                {modo === 'desligar' && <label className="campo"><span>Sua senha</span><input id="de-senha" name="senha" type="password" required autoComplete="current-password" /></label>}
                <label className="campo"><span>Código do app (ou de recuperação)</span><input id="de-confirma" name="codigo" required autoComplete="one-time-code" maxLength={12} /></label>
                <Erro msg={erro} />
                <div className="acoes-linha inteira">
                  <button className="botao principal" disabled={enviando}>{modo === 'desligar' ? 'Desligar' : 'Gerar novos códigos'}</button>
                  <button type="button" className="botao" onClick={() => { setModo(null); setErro(''); }}>Cancelar</button>
                </div>
              </form>
            ) : (
              <div className="acoes-linha">
                <button className="botao pequeno" onClick={() => setModo('codigos')}>Gerar novos códigos de recuperação</button>
                <button className="botao-link legenda" onClick={() => setModo('desligar')}>Desligar</button>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  );
}
