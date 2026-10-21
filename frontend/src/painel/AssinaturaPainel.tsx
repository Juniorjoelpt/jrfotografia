import { useRef, useState } from 'react';
import { api, ErroApi, urlPainel } from '../api';
import { QuadroAssinatura, type QuadroRef } from '../componentes/QuadroAssinatura';
import type { Assinatura } from '../tipos';
import { dataHora, linkWhatsapp, primeiroNome, urlAssinar } from '../util';
import { Erro, useDados } from './comum';
import '../contrato/contrato.css';

/** Ajustes > Contrato: a assinatura desenhada do fotógrafo, que vai no PDF dos contratos assinados. */
export function AssinaturaDoEstudio() {
  const { dados, recarregar } = useDados<{ tem: boolean }>('/estudio/assinatura');
  const [desenhando, setDesenhando] = useState(false);
  const [vazio, setVazio] = useState(true);
  const [erro, setErro] = useState('');
  const [versao, setVersao] = useState(0);
  const quadro = useRef<QuadroRef>(null);

  async function salvar() {
    const assinatura = quadro.current?.imagem();
    if (!assinatura) return;
    setErro('');
    try {
      await api('/estudio/assinatura', { metodo: 'PUT', corpo: { assinatura } });
      setDesenhando(false);
      setVersao((v) => v + 1);
      recarregar();
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível salvar.');
    }
  }

  async function apagar() {
    await api('/estudio/assinatura', { metodo: 'DELETE' });
    recarregar();
  }

  return (
    <section className="painel-caixa secao" style={{ marginTop: 'var(--espaco-4)' }}>
      <h2 className="chamada">Sua assinatura nos contratos</h2>
      <p className="suave" style={{ margin: 0 }}>
        Desenhe sua assinatura uma vez. Ela aparece sobre a linha do CONTRATADO em todo contrato que o cliente assinar pelo link.
      </p>
      {dados?.tem && !desenhando && (
        <div className="ass-estudio">
          <img src={urlPainel(`/estudio/assinatura.png`) + `&v=${versao}`} alt="Sua assinatura" />
        </div>
      )}
      {desenhando ? (
        <>
          <QuadroAssinatura ref={quadro} rotulo="Desenhe sua assinatura" aoMudar={setVazio} />
          <Erro msg={erro} />
          <div className="acoes-linha">
            <button className="botao principal" disabled={vazio} onClick={salvar}>Salvar assinatura</button>
            <button className="botao" onClick={() => setDesenhando(false)}>Cancelar</button>
          </div>
        </>
      ) : (
        <div className="acoes-linha">
          <button className="botao" onClick={() => setDesenhando(true)}>{dados?.tem ? 'Desenhar de novo' : 'Desenhar assinatura'}</button>
          {dados?.tem && <button className="botao-link" onClick={apagar}>Remover</button>}
        </div>
      )}
    </section>
  );
}

/** Detalhe do trabalho: enviar o contrato para assinatura e acompanhar. */
export function ContratoTrabalho({ trabalhoId, cliente, telefoneCliente, cancelado }: {
  trabalhoId: number; cliente: string; telefoneCliente?: string; cancelado: boolean;
}) {
  const { dados: a, recarregar } = useDados<Assinatura | undefined>(`/trabalhos/${trabalhoId}/assinatura`);
  const [erro, setErro] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [copiado, setCopiado] = useState(false);

  async function gerar() {
    setOcupado(true);
    setErro('');
    try {
      await api(`/trabalhos/${trabalhoId}/assinatura`, { metodo: 'POST' });
      recarregar();
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível gerar o link.');
    } finally {
      setOcupado(false);
    }
  }

  async function cancelar() {
    await api(`/trabalhos/${trabalhoId}/assinatura`, { metodo: 'DELETE' });
    recarregar();
  }

  async function copiar(link: string) {
    try { await navigator.clipboard.writeText(link); setCopiado(true); setTimeout(() => setCopiado(false), 2000); } catch { /* sem permissão */ }
  }

  if (cancelado && a?.status !== 'ASSINADO') return null;
  const link = a ? urlAssinar(a.token) : '';
  const msg = `Olá, ${primeiroNome(cliente)}! Seu contrato está pronto. Leia e assine pelo celular neste link (leva um minuto):\n${link}`;

  return (
    <section className="secao">
      <h2 className="chamada">Contrato</h2>
      <div className="painel-caixa" style={{ display: 'grid', gap: 'var(--espaco-3)' }}>
        {a === undefined && <p className="suave" style={{ margin: 0 }}>Contrato ainda não enviado para assinatura. O cliente assina pelo celular, com nome, CPF e assinatura desenhada.</p>}
        {a?.status === 'PENDENTE' && (
          <>
            <p style={{ margin: 0 }}><span className="etiqueta alerta">Aguardando assinatura</span> <span className="legenda">LINK GERADO EM {dataHora(a.criadoEm).toUpperCase()}</span></p>
            {a.desatualizado && (
              <p className="aviso" style={{ margin: 0, borderColor: 'var(--alerta)' }}>
                Os dados do trabalho mudaram depois que o link foi gerado. Gere um link novo para o contrato sair atualizado (o antigo deixa de valer).
              </p>
            )}
            <code style={{ overflowWrap: 'anywhere', fontSize: 13 }}>{link}</code>
          </>
        )}
        {a?.status === 'ASSINADO' && (
          <>
            <p style={{ margin: 0 }}><span className="etiqueta sucesso">Assinado</span>{' '}
              {a.nomeAssinante} · CPF {a.cpfAssinante} · {dataHora(a.assinadoEm)}</p>
            <p className="legenda" style={{ margin: 0 }}>CÓDIGO {a.codigo} · IP {a.ip}</p>
          </>
        )}
        <Erro msg={erro} />
        <div className="acoes-linha">
          {a === undefined && <button className="botao principal pequeno" disabled={ocupado} onClick={gerar}>{ocupado ? 'Gerando…' : 'Gerar link de assinatura'}</button>}
          {a?.status === 'PENDENTE' && a.desatualizado && <button className="botao principal pequeno" disabled={ocupado} onClick={gerar}>Gerar link atualizado</button>}
          {a?.status === 'PENDENTE' && telefoneCliente && (
            <a className={`botao pequeno${a.desatualizado ? '' : ' principal'}`} href={linkWhatsapp(telefoneCliente, msg)} target="_blank" rel="noreferrer">Enviar pelo WhatsApp</a>
          )}
          {a?.status === 'PENDENTE' && <button className="botao pequeno" onClick={() => copiar(link)}>{copiado ? 'Copiado!' : 'Copiar link'}</button>}
          {a?.status === 'PENDENTE' && <button className="botao-link" style={{ fontSize: 13 }} onClick={cancelar}>Cancelar link</button>}
          {a?.status === 'ASSINADO' && (
            <a className="botao principal pequeno" href={urlPainel(`/trabalhos/${trabalhoId}/assinatura/contrato.pdf`)} target="_blank" rel="noreferrer">Contrato assinado (PDF)</a>
          )}
        </div>
      </div>
    </section>
  );
}
