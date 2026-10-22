import { useState } from 'react';
import type { Estudio, MeioPagamento } from '../tipos';
import { linkWhatsapp, mensagemCobranca } from '../util';

/**
 * Cobrar e dar baixa numa parcela em aberto. Fica igual no Trabalho e em Financeiro › A receber.
 * Com o Pix automático ligado, a cobrança leva a área da cliente e a baixa à mão vira "Recebi por fora",
 * com aviso se já existe um Pix aberto (a cliente poderia pagar duas vezes).
 */
export function AcoesParcela({ parcela, clienteNome, telefone, areaToken, estudio, pixAberto, ocupado, aoPagar }: {
  parcela: { descricao: string; valor: number; vencimento?: string | null };
  clienteNome: string; telefone?: string | null; areaToken?: string | null; estudio?: Estudio | null;
  pixAberto?: boolean; ocupado?: boolean; aoPagar: (forma: MeioPagamento) => void;
}) {
  const [confirmar, setConfirmar] = useState<MeioPagamento | null>(null);
  const automatico = !!estudio?.pixAutomatico;
  const baixar = (forma: MeioPagamento) => (pixAberto ? setConfirmar(forma) : aoPagar(forma));

  if (confirmar) {
    return (
      <span className="aviso anima-entrada" role="alertdialog" style={{ margin: 0, display: 'grid', gap: 'var(--espaco-2)', borderColor: 'var(--alerta)' }}>
        <span>Há um Pix aberto para esta parcela na área da cliente. Se ela pagar também, paga duas vezes. Marcar como recebido mesmo assim?</span>
        <span className="acoes-linha">
          <button className="botao principal pequeno" disabled={ocupado} onClick={() => { aoPagar(confirmar); setConfirmar(null); }}>Sim, já recebi</button>
          <button className="botao pequeno" onClick={() => setConfirmar(null)}>Voltar</button>
        </span>
      </span>
    );
  }
  return (
    <>
      {telefone && (
        <a className="botao pequeno" target="_blank" rel="noreferrer"
          href={linkWhatsapp(telefone, mensagemCobranca(clienteNome, parcela, estudio, areaToken))}>Cobrar</a>
      )}
      {pixAberto && <span className="legenda" style={{ alignSelf: 'center', color: 'var(--alerta)' }}>PIX ABERTO NA ÁREA</span>}
      <button className="botao pequeno" disabled={ocupado} onClick={() => baixar('PIX')}>{automatico ? 'Recebi por fora (Pix)' : 'Pago no Pix'}</button>
      <button className="botao pequeno" disabled={ocupado} onClick={() => baixar('DINHEIRO')}>Dinheiro</button>
    </>
  );
}
