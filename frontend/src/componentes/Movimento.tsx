import { useEffect, useRef, useState, type ImgHTMLAttributes } from 'react';

export const movimentoReduzido = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** Número que conta até o valor (ease-out, 700 ms). Sem animação se o aparelho pede movimento reduzido. */
export function Numero({ valor, formatar = (n) => String(Math.round(n)) }: { valor: number; formatar?: (n: number) => string }) {
  const [mostrado, setMostrado] = useState(valor);
  const anterior = useRef(0);
  useEffect(() => {
    const de = anterior.current;
    anterior.current = valor;
    if (movimentoReduzido() || de === valor) { setMostrado(valor); return; }
    const inicio = performance.now();
    const duracao = 700;
    let quadro = 0;
    const passo = (agora: number) => {
      const t = Math.min(1, (agora - inicio) / duracao);
      const e = 1 - Math.pow(1 - t, 3);
      setMostrado(de + (valor - de) * e);
      if (t < 1) quadro = requestAnimationFrame(passo);
    };
    quadro = requestAnimationFrame(passo);
    return () => cancelAnimationFrame(quadro);
  }, [valor]);
  return <>{formatar(mostrado)}</>;
}

/** <img> que aparece suave ao terminar de carregar (e já visível se vier do cache). */
export function Imagem({ className = '', onLoad, ...props }: ImgHTMLAttributes<HTMLImageElement>) {
  const [carregada, setCarregada] = useState(false);
  const ref = useRef<HTMLImageElement>(null);
  useEffect(() => { if (ref.current?.complete && ref.current.naturalWidth) setCarregada(true); }, [props.src]);
  return (
    <img ref={ref} decoding="async" {...props} className={`img-suave${carregada ? ' carregada' : ''} ${className}`}
      onLoad={(e) => { setCarregada(true); onLoad?.(e); }} />
  );
}

/**
 * Marca elementos com data-revelar para aparecerem ao entrar na tela.
 * Só esconde depois que o observador existe: sem JavaScript, nada fica invisível.
 */
export function useRevelar(dependencias: unknown[] = []) {
  useEffect(() => {
    if (movimentoReduzido() || !('IntersectionObserver' in window)) return;
    document.documentElement.classList.add('js-revelar');
    const obs = new IntersectionObserver((entradas) => {
      entradas.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('visivel'); obs.unobserve(e.target); } });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.08 });
    document.querySelectorAll('[data-revelar]:not(.visivel)').forEach((el) => obs.observe(el));
    return () => obs.disconnect();
  }, dependencias); // eslint-disable-line react-hooks/exhaustive-deps
}

/** Fundo desfocado: a foto entra por cima das luzes da marca com fade quando termina de carregar. */
export function Ambiente({ src }: { src?: string | null }) {
  const [pronta, setPronta] = useState<string | null>(null);
  useEffect(() => {
    if (!src) return;
    const img = new Image();
    img.onload = () => setPronta(src);
    img.src = src;
  }, [src]);
  return (
    <>
      <div className="ambiente ambiente-base" />
      <div className={`ambiente ambiente-foto${pronta ? ' pronta' : ''}`} style={pronta ? { backgroundImage: `url(${pronta})` } : undefined} />
      <div className="ambiente-veu" />
    </>
  );
}
