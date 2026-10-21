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
    // Aba escondida não roda animação (o navegador pausa): mostra o número final direto.
    if (movimentoReduzido() || de === valor || document.visibilityState === 'hidden') { setMostrado(valor); return; }
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
    const garantia = setTimeout(() => setMostrado(valor), duracao + 200);
    return () => { cancelAnimationFrame(quadro); clearTimeout(garantia); };
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

/**
 * Modo "casca" no celular: a página fica do tamanho da tela e só o miolo rola, com a barra de baixo
 * no fluxo normal (sem position: fixed). Contorna o defeito do iOS 26 em que, depois de abrir e
 * fechar o teclado, barras fixas ficam paradas no meio da tela.
 */
export function useCasca(ativo = true) {
  useEffect(() => {
    if (!ativo) return;
    const html = document.documentElement;
    html.classList.add('casca');
    // Ao fechar o teclado, devolve a tela ao topo para o iOS não ficar com o deslocamento preso.
    // Só quando quem perdeu o foco foi um campo de texto (o teclado estava aberto) e a casca está valendo
    // (celular); no computador a página rola pela janela e voltar ao topo atrapalharia (ex.: tocar no coração).
    const aoSair = (e: FocusEvent) => {
      const saiu = e.target;
      if (!(saiu instanceof HTMLElement) || !saiu.matches('input:not([type=checkbox]):not([type=radio]):not([type=file]), textarea, select, [contenteditable]')) return;
      if (getComputedStyle(html).overflow !== 'hidden') return;
      setTimeout(() => {
        const ativo = document.activeElement;
        if (!(ativo instanceof HTMLElement) || !ativo.matches('input, textarea, select, [contenteditable]')) window.scrollTo(0, 0);
      }, 120);
    };
    document.addEventListener('focusout', aoSair);
    return () => { html.classList.remove('casca'); document.removeEventListener('focusout', aoSair); };
  }, [ativo]);
}
