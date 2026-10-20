import { useEffect, type ReactNode } from 'react';

/** Foto em tela cheia, com setas e teclado. O conteúdo extra (ex.: botão de favorita) vem em "rodape". */
export function Visualizador({ src, alt, aoFechar, aoAnterior, aoProxima, rodape }: {
  src: string; alt: string; aoFechar: () => void; aoAnterior?: () => void; aoProxima?: () => void; rodape?: ReactNode;
}) {
  useEffect(() => {
    const tecla = (e: KeyboardEvent) => {
      if (e.key === 'Escape') aoFechar();
      if (e.key === 'ArrowLeft') aoAnterior?.();
      if (e.key === 'ArrowRight') aoProxima?.();
    };
    window.addEventListener('keydown', tecla);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { window.removeEventListener('keydown', tecla); document.body.style.overflow = overflow; };
  }, [aoFechar, aoAnterior, aoProxima]);

  return (
    <div className="visualizador" role="dialog" aria-modal="true" aria-label={alt} onClick={aoFechar}>
      <div onClick={(e) => e.stopPropagation()} style={{ display: 'grid', gap: 12, justifyItems: 'center' }}>
        <img src={src} alt={alt} />
        {rodape}
      </div>
      <button className="botao pequeno fechar" onClick={aoFechar}>Fechar</button>
      {aoAnterior && <button className="botao pequeno navegar ant" aria-label="Foto anterior" onClick={(e) => { e.stopPropagation(); aoAnterior(); }}>‹</button>}
      {aoProxima && <button className="botao pequeno navegar prox" aria-label="Próxima foto" onClick={(e) => { e.stopPropagation(); aoProxima(); }}>›</button>}
    </div>
  );
}
