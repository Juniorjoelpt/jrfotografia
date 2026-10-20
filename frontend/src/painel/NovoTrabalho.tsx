import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, ErroApi } from '../api';
import type { Cliente, FormaPagamento, Pacote, TrabalhoDetalhe } from '../tipos';
import { moeda } from '../util';
import { Erro, useDados } from './comum';

export function NovoTrabalho() {
  const navegar = useNavigate();
  const [params] = useSearchParams();
  const { dados: clientes } = useDados<Cliente[]>('/clientes');
  const { dados: pacotes } = useDados<Pacote[]>('/pacotes');
  const [clienteId, setClienteId] = useState(params.get('cliente') ?? '');
  const [pacoteId, setPacoteId] = useState('');
  const [valor, setValor] = useState('');
  const [forma, setForma] = useState<FormaPagamento>('AVISTA');
  const [sinal, setSinal] = useState('');
  const [parcelas, setParcelas] = useState('2');
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);

  const pacote = useMemo(() => pacotes?.find((p) => String(p.id) === pacoteId), [pacotes, pacoteId]);
  useEffect(() => { if (pacote) setValor(String(pacote.preco)); }, [pacote]);

  const total = Number(valor || 0);
  const resto = Math.max(0, total - Number(sinal || 0));
  const n = Math.max(1, Number(parcelas || 1));

  async function salvar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setEnviando(true);
    setErro('');
    try {
      const t = await api<TrabalhoDetalhe>('/trabalhos', {
        corpo: {
          clienteId: Number(clienteId), pacoteId: pacoteId ? Number(pacoteId) : null,
          tipo: f.get('tipo') || null, titulo: f.get('titulo') || null,
          dataTrabalho: f.get('data') ? `${f.get('data')}:00` : null, localTrabalho: f.get('local') || null,
          valorTotal: total, formaPagamento: forma,
          valorSinal: forma === 'PARCELADO' && sinal ? Number(sinal) : null,
          numParcelas: forma === 'PARCELADO' ? n : null,
          primeiroVencimento: f.get('vencimento') || null, observacoes: f.get('obs') || null,
        },
      });
      navegar(`/painel/trabalhos/${t.resumo.id}`, { replace: true });
    } catch (er) {
      setErro(er instanceof ErroApi ? er.message : 'Não foi possível salvar.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <div className="cabeca-pagina"><div><span className="rotulo">Trabalhos</span><h1 className="titulo">Novo trabalho</h1></div></div>
      <form className="painel-caixa grade-form duas" onSubmit={salvar}>
        <label className="campo inteira"><span>Cliente</span>
          <select id="nt-cliente" required value={clienteId} onChange={(e) => setClienteId(e.target.value)}>
            <option value="">Escolha o cliente</option>
            {clientes?.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
        </label>
        {clientes && clientes.length === 0 && <p className="aviso inteira" style={{ margin: 0 }}>Cadastre o cliente primeiro, na aba Clientes.</p>}
        <label className="campo"><span>Pacote</span>
          <select id="nt-pacote" value={pacoteId} onChange={(e) => setPacoteId(e.target.value)}>
            <option value="">Sem pacote (valor livre)</option>
            {pacotes?.filter((p) => p.ativo).map((p) => <option key={p.id} value={p.id}>{p.nome} · {moeda(p.preco)}</option>)}
          </select>
        </label>
        <label className="campo"><span>Tipo de ensaio</span><input id="nt-tipo" name="tipo" placeholder="Ensaio feminino" /></label>
        <label className="campo"><span>Data e hora</span><input id="nt-data" name="data" type="datetime-local" /></label>
        <label className="campo"><span>Local</span><input id="nt-local" name="local" placeholder="Endereço ou locação" /></label>
        <label className="campo"><span>Valor total (R$)</span>
          <input id="nt-valor" required inputMode="decimal" value={valor} onChange={(e) => setValor(e.target.value.replace(',', '.'))} />
        </label>
        <label className="campo"><span>Pagamento</span>
          <select id="nt-forma" value={forma} onChange={(e) => setForma(e.target.value as FormaPagamento)}>
            <option value="AVISTA">À vista</option><option value="PARCELADO">Parcelado</option>
          </select>
        </label>
        {forma === 'PARCELADO' && (
          <>
            <label className="campo"><span>Sinal (R$)</span><input id="nt-sinal" inputMode="decimal" value={sinal} onChange={(e) => setSinal(e.target.value.replace(',', '.'))} /></label>
            <label className="campo"><span>Parcelas depois do sinal</span>
              <select id="nt-parcelas" value={parcelas} onChange={(e) => setParcelas(e.target.value)}>
                {[1, 2, 3, 4, 5, 6].map((i) => <option key={i} value={i}>{i}x de {moeda(resto / i)}</option>)}
              </select>
            </label>
          </>
        )}
        <label className="campo"><span>{forma === 'AVISTA' ? 'Vencimento' : '1º vencimento'}</span><input id="nt-venc" name="vencimento" type="date" /></label>
        <label className="campo inteira"><span>Observações</span><textarea id="nt-obs" name="obs" /></label>
        <p className="legenda inteira" style={{ margin: 0 }}>
          {forma === 'PARCELADO'
            ? `O download libera depois que o cliente pagar ${Number(sinal) > 0 ? 'o sinal e ' : ''}a 1ª parcela.`
            : 'O download libera depois que o pagamento for registrado.'}
          {pacote?.qtdFotos ? ` O cliente escolhe ${pacote.qtdFotos} fotos; extras a ${moeda(pacote.precoFotoExtra)}.` : pacote ? ' Este pacote entrega todas as fotos, sem seleção.' : ''}
        </p>
        <Erro msg={erro} />
        <button className="botao principal inteira" disabled={enviando || !clienteId}>{enviando ? 'Salvando…' : 'Criar trabalho'}</button>
      </form>
    </>
  );
}
