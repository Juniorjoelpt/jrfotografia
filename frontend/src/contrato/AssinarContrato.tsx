import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ErroApi, publico } from '../api';
import { Casca, Erro } from '../agendar/AgendarPublico';
import { QuadroAssinatura, type QuadroRef } from '../componentes/QuadroAssinatura';
import type { AssinaturaPublica, Verificacao } from '../tipos';
import { cpfValido, dataHora, linkWhatsapp, mascaraCpf, primeiroNome } from '../util';
import './contrato.css';

/** Texto do contrato em parágrafos, com os títulos das cláusulas destacados (igual ao PDF). */
export function TextoContrato({ texto }: { texto: string }) {
  return (
    <div className="ct-texto">
      {texto.split(/\n\s*\n/).map((b, i) => {
        const bloco = b.trim();
        if (!bloco) return null;
        const [primeira, ...resto] = bloco.split('\n');
        if (/^\d+\.\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ ,/]+$/.test(primeira)) {
          return <p key={i}><strong>{primeira}</strong>{resto.length ? <><br />{resto.join('\n')}</> : null}</p>;
        }
        return <p key={i}>{bloco}</p>;
      })}
    </div>
  );
}

// ====================================================================== /assinar/:token

export function AssinarContrato() {
  const { token = '' } = useParams();
  const [a, setA] = useState<AssinaturaPublica | null>(null);
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [cpf, setCpf] = useState('');
  const [quadroVazio, setQuadroVazio] = useState(true);
  const quadro = useRef<QuadroRef>(null);

  useEffect(() => {
    document.title = 'Assinar contrato';
    publico<AssinaturaPublica>(`/publico/assinaturas/${token}`)
      .then((r) => { setA(r); document.title = r.status === 'ASSINADO' ? 'Contrato assinado' : 'Assinar contrato'; })
      .catch((e) => setErro(e instanceof ErroApi && e.status === 404 ? e.message : 'Não foi possível abrir o contrato agora.'));
  }, [token]);

  async function assinar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setErro('');
    if (!cpfValido(cpf)) { setErro('CPF inválido. Confira os números.'); return; }
    const assinatura = quadro.current?.imagem();
    if (!assinatura) { setErro('Desenhe sua assinatura no quadro.'); return; }
    setEnviando(true);
    try {
      const r = await api<AssinaturaPublica>(`/publico/assinaturas/${token}`, {
        token: null,
        corpo: { nome: f.get('nome'), cpf, assinatura, aceite: f.get('aceite') === 'on', autorizaImagem: f.get('imagem') == null ? null : f.get('imagem') === 'sim' },
      });
      setA(r);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (ex) {
      setErro(ex instanceof ErroApi ? ex.message : 'Não foi possível assinar. Tente de novo.');
    } finally {
      setEnviando(false);
    }
  }

  if (!a) return <Casca rotulo="Contrato">{erro ? <Erro msg={erro} /> : <div className="esqueleto" style={{ height: 320 }} />}</Casca>;

  const pdf = `/api/publico/assinaturas/${token}/contrato.pdf`;

  if (a.status === 'ASSINADO') {
    return (
      <Casca titulo={a.estudioNome} rotulo="Contrato">
        <div className="aviso sucesso oc-estado" role="status">
          <strong>Contrato assinado</strong>
          <span>Assinado por {a.nomeAssinante} em {dataHora(a.assinadoEm)}. Guarde o PDF: ele tem a página de registro da assinatura.</span>
        </div>
        <section className="oc-bloco">
          <p className="legenda" style={{ margin: 0 }}>CÓDIGO DE VERIFICAÇÃO</p>
          <p className="ct-codigo">{a.codigo}</p>
          <p className="suave" style={{ margin: 0 }}>Qualquer pessoa confere a autenticidade em <Link to={`/verificar/${a.codigo}`}>{window.location.host}/verificar</Link>.</p>
        </section>
        <footer className="oc-rodape">
          <a className="botao principal" href={pdf} target="_blank" rel="noreferrer">Baixar contrato assinado</a>
          {a.estudioWhatsapp && <a className="botao" href={linkWhatsapp(a.estudioWhatsapp, `Olá! Assinei o contrato (${a.titulo}).`)} target="_blank" rel="noreferrer">Avisar no WhatsApp</a>}
        </footer>
      </Casca>
    );
  }

  return (
    <Casca titulo={a.estudioNome} rotulo="Contrato">
      <section className="oc-bloco">
        <p className="oc-saudacao">Olá, {primeiroNome(a.contratante)}! Leia o contrato com calma e assine no fim da página.</p>
        <p className="legenda" style={{ margin: 0 }}>{a.titulo.toUpperCase()} · ENVIADO EM {dataHora(a.enviadoEm).toUpperCase()}</p>
      </section>

      <TextoContrato texto={a.texto} />

      <form className="oc-bloco ct-assinar" onSubmit={assinar}>
        <h2 className="chamada">Assinatura</h2>
        <div className="grade-form duas">
          <label className="campo"><span>Nome completo</span>
            <input name="nome" required maxLength={120} autoComplete="name" defaultValue={a.contratante} /></label>
          <label className="campo"><span>CPF</span>
            <input name="cpf" required inputMode="numeric" autoComplete="off" placeholder="000.000.000-00" value={cpf}
              onChange={(e) => setCpf(mascaraCpf(e.target.value))} aria-invalid={cpf.length === 14 && !cpfValido(cpf)} /></label>
        </div>
        {cpf.length === 14 && !cpfValido(cpf) && <p className="aviso erro" style={{ margin: 0 }}>CPF inválido. Confira os números.</p>}
        {a.perguntaUsoImagem && (
          <fieldset className="ct-imagem">
            <legend>Posso usar as fotos no meu portfólio, site e redes sociais?</legend>
            <label className="marcar"><input type="radio" name="imagem" value="sim" required /> Sim, autorizo</label>
            <label className="marcar"><input type="radio" name="imagem" value="nao" required /> Não autorizo</label>
          </fieldset>
        )}
        <QuadroAssinatura ref={quadro} rotulo="Assine aqui com o dedo" aoMudar={setQuadroVazio} />
        <label className="marcar"><input type="checkbox" name="aceite" required />
          <span>Li e concordo com todas as cláusulas. Reconheço esta assinatura eletrônica como válida (Lei nº 14.063/2020).</span></label>
        <p className="legenda" style={{ margin: 0 }}>
          JUNTO COM A ASSINATURA FICAM REGISTRADOS A DATA E A HORA, O ENDEREÇO IP E O NAVEGADOR, PARA COMPROVAR A AUTORIA.
        </p>
        <Erro msg={erro} />
        <button className="botao principal" disabled={enviando || quadroVazio}>{enviando ? 'Assinando…' : 'Assinar contrato'}</button>
      </form>
    </Casca>
  );
}

// ====================================================================== /verificar/:codigo?

export function VerificarContrato() {
  const { codigo: doLink = '' } = useParams();
  const [codigo, setCodigo] = useState(doLink);
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [r, setR] = useState<Verificacao | null>(null);
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    document.title = 'Verificar contrato';
    if (doLink) consultar(doLink, null);
  }, [doLink]); // eslint-disable-line react-hooks/exhaustive-deps

  async function consultar(c: string, f: File | null) {
    setEnviando(true);
    setErro('');
    setR(null);
    try {
      if (f) {
        const fd = new FormData();
        fd.append('arquivo', f);
        if (c.trim()) fd.append('codigo', c.trim());
        setR(await api<Verificacao>('/publico/verificar', { token: null, arquivos: fd }));
      } else {
        setR(await api<Verificacao>(`/publico/verificar/${encodeURIComponent(c.trim())}`, { token: null }));
      }
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : 'Não foi possível consultar agora.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Casca rotulo="Verificar">
      <section className="oc-bloco">
        <h1 className="chamada" style={{ margin: 0 }}>Verificar contrato assinado</h1>
        <p className="suave" style={{ margin: 0 }}>Digite o código que aparece no rodapé do contrato. Se quiser, envie também o PDF: o sistema confere se o arquivo é exatamente o que foi assinado.</p>
      </section>
      <form className="oc-bloco" onSubmit={(e) => { e.preventDefault(); consultar(codigo, arquivo); }}>
        <label className="campo"><span>Código de verificação</span>
          <input value={codigo} onChange={(e) => setCodigo(e.target.value.toUpperCase())} placeholder="ABCD-EFGH-JK" maxLength={14}
            autoCapitalize="characters" spellCheck={false} required={!arquivo} /></label>
        <label className="campo"><span>PDF do contrato (opcional)</span>
          <input type="file" accept="application/pdf" onChange={(e) => setArquivo(e.target.files?.[0] ?? null)} /></label>
        <button className="botao principal" disabled={enviando || (!codigo.trim() && !arquivo)}>{enviando ? 'Consultando…' : 'Verificar'}</button>
      </form>
      <Erro msg={erro} />
      {r && (r.valido ? (
        <div className={`aviso ${r.arquivoConfere === false ? 'erro' : 'sucesso'} oc-estado`} role="status">
          <strong>{r.arquivoConfere === false ? 'O código é válido, mas o PDF enviado foi alterado ou não é este contrato.' : 'Contrato autêntico'}</strong>
          {r.arquivoConfere === true && <span>O arquivo enviado é idêntico ao contrato assinado.</span>}
          <dl className="ct-dados">
            <div><dt>Código</dt><dd>{r.codigo}</dd></div>
            <div><dt>Contratado</dt><dd>{r.estudio}</dd></div>
            <div><dt>Assinado por</dt><dd>{r.contratante} · <span style={{ whiteSpace: 'nowrap' }}>CPF {r.cpf}</span></dd></div>
            <div><dt>Assinado em</dt><dd>{dataHora(r.assinadoEm)}</dd></div>
            <div><dt>Hash do texto</dt><dd className="ct-hash">{r.hashTexto}</dd></div>
          </dl>
        </div>
      ) : (
        <div className="aviso erro oc-estado" role="status">
          <strong>{r.arquivoConfere === false || (!r.codigo && arquivo) ? 'Este PDF não corresponde a nenhum contrato assinado.' : 'Código não encontrado.'}</strong>
          <span>Confira se digitou o código exatamente como está no rodapé do contrato.</span>
        </div>
      ))}
    </Casca>
  );
}
