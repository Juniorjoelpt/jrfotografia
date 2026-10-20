import { useEffect, useRef, useState, type ReactNode, type TouchEvent } from 'react';
import './visualizador.css';

/**
 * Foto em tela cheia. Abre com zoom, troca de foto com deslize na direção certa,
 * aceita setas do teclado e arrastar para os lados no celular. O conteúdo extra vem em "rodape".
 */
export function Visualizador({ src, alt, aoFechar, aoAnterior, aoProxima, rodape }: {
  src: string; alt: string; aoFechar: () => void; aoAnterior?: () => void; aoProxima?: () => void; rodape?: ReactNode;
}) {
  const [direcao, setDirecao] = useState<'prox' | 'ant' | null>(null);
  const [arrasto, setArrasto] = useState(0);
  const [saindo, setSaindo] = useState(false);
  const toque = useRef<{ x: number; y: number } | null>(null);

  const fechar = () => {
    setSaindo(true);
    window.setTimeout(aoFechar, 180);
  };
  const ir = (d: 'prox' | 'ant') => {
    const acao = d === 'prox' ? aoProxima : aoAnterior;
    if (!acao) return;
    setDirecao(d);
    acao();
  };

  useEffect(() => {
    const tecla = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar();
      if (e.key === 'ArrowLeft') ir('ant');
      if (e.key === 'ArrowRight') ir('prox');
    };
    window.addEventListener('keydown', tecla);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { window.removeEventListener('keydown', tecla); document.body.style.overflow = overflow; };
  }); // re-registra a cada render para usar as funções atuais

  const inicioToque = (e: TouchEvent) => { toque.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }; };
  const movendo = (e: TouchEvent) => {
    if (!toque.current) return;
    const dx = e.touches[0].clientX - toque.current.x;
    const dy = e.touches[0].clientY - toque.current.y;
    if (Math.abs(dx) > Math.abs(dy)) setArrasto(dx);
  };
  const fimToque = () => {
    if (arrasto < -60) ir('prox');
    else if (arrasto > 60) ir('ant');
    setArrasto(0);
    toque.current = null;
  };

  return (
    <div className={`visualizador${saindo ? ' saindo' : ''}`} role="dialog" aria-modal="true" aria-label={alt} onClick={fechar}>
      <div className="palco" onClick={(e) => e.stopPropagation()}
        onTouchStart={inicioToque} onTouchMove={movendo} onTouchEnd={fimToque}>
        <img key={src} src={src} alt={alt} className={direcao ? `vindo-${direcao}` : 'abrindo'}
          style={arrasto ? { transform: `translateX(${arrasto}px) rotate(${arrasto / 60}deg)`, transition: 'none' } : undefined} />
        {rodape}
      </div>
      <button className="botao pequeno fechar" onClick={fechar}>Fechar</button>
      {aoAnterior && <button className="botao pequeno navegar ant" aria-label="Foto anterior" onClick={(e) => { e.stopPropagation(); ir('ant'); }}>‹</button>}
      {aoProxima && <button className="botao pequeno navegar prox" aria-label="Próxima foto" onClick={(e) => { e.stopPropagation(); ir('prox'); }}>›</button>}
    </div>
  );
}
