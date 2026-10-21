import { useEffect, useImperativeHandle, useRef, useState, forwardRef } from 'react';

export interface QuadroRef {
  /** PNG (fundo transparente) da assinatura, ou null se o quadro está vazio. */
  imagem: () => string | null;
  limpar: () => void;
}

/**
 * Quadro para desenhar a assinatura com o dedo, a caneta ou o mouse.
 * Usa Pointer Events (funciona no iPhone, Android e computador) e guarda os traços
 * para redesenhar quando o quadro muda de tamanho (girar o celular).
 */
export const QuadroAssinatura = forwardRef<QuadroRef, { rotulo?: string; aoMudar?: (vazio: boolean) => void }>(
  function QuadroAssinatura({ rotulo = 'Assine aqui', aoMudar }, ref) {
    const canvas = useRef<HTMLCanvasElement>(null);
    const tracos = useRef<{ x: number; y: number }[][]>([]);
    const desenhando = useRef(false);
    const [vazio, setVazio] = useState(true);

    const contexto = () => canvas.current?.getContext('2d') ?? null;

    function redesenhar() {
      const c = canvas.current, g = contexto();
      if (!c || !g) return;
      const r = c.getBoundingClientRect();
      const escala = Math.min(window.devicePixelRatio || 1, 2);
      c.width = Math.round(r.width * escala);
      c.height = Math.round(r.height * escala);
      g.setTransform(escala, 0, 0, escala, 0, 0);
      g.lineCap = 'round';
      g.lineJoin = 'round';
      g.strokeStyle = '#141414';
      g.lineWidth = 2.6;
      for (const t of tracos.current) {
        g.beginPath();
        t.forEach((p, i) => (i === 0 ? g.moveTo(p.x * r.width, p.y * r.height) : g.lineTo(p.x * r.width, p.y * r.height)));
        if (t.length === 1) g.lineTo(t[0].x * r.width + 0.1, t[0].y * r.height);
        g.stroke();
      }
    }

    useEffect(() => {
      redesenhar();
      const obs = new ResizeObserver(() => redesenhar());
      if (canvas.current) obs.observe(canvas.current);
      return () => obs.disconnect();
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const ponto = (e: React.PointerEvent) => {
      const r = canvas.current!.getBoundingClientRect();
      return { x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height };
    };

    function inicio(e: React.PointerEvent<HTMLCanvasElement>) {
      e.preventDefault();
      canvas.current!.setPointerCapture(e.pointerId);
      desenhando.current = true;
      tracos.current.push([ponto(e)]);
    }

    function move(e: React.PointerEvent<HTMLCanvasElement>) {
      if (!desenhando.current) return;
      const t = tracos.current[tracos.current.length - 1];
      const anterior = t[t.length - 1];
      const p = ponto(e);
      t.push(p);
      const g = contexto(), r = canvas.current!.getBoundingClientRect();
      if (!g) return;
      g.beginPath();
      g.moveTo(anterior.x * r.width, anterior.y * r.height);
      g.lineTo(p.x * r.width, p.y * r.height);
      g.stroke();
    }

    function fim() {
      if (!desenhando.current) return;
      desenhando.current = false;
      redesenhar();
      const v = tracos.current.reduce((n, t) => n + t.length, 0) < 4;
      setVazio(v);
      aoMudar?.(v);
    }

    function limpar() {
      tracos.current = [];
      redesenhar();
      setVazio(true);
      aoMudar?.(true);
    }

    useImperativeHandle(ref, () => ({
      limpar,
      imagem: () => {
        if (vazio || !canvas.current) return null;
        // Exporta em tamanho fixo e moderado (o servidor recusa imagens enormes).
        const r = canvas.current.getBoundingClientRect();
        const largura = 600, altura = Math.round((600 * r.height) / r.width);
        const c = document.createElement('canvas');
        c.width = largura;
        c.height = altura;
        const g = c.getContext('2d')!;
        g.lineCap = 'round';
        g.lineJoin = 'round';
        g.strokeStyle = '#141414';
        g.lineWidth = 2.6 * (largura / r.width) * 1.1;
        for (const t of tracos.current) {
          g.beginPath();
          t.forEach((p, i) => (i === 0 ? g.moveTo(p.x * largura, p.y * altura) : g.lineTo(p.x * largura, p.y * altura)));
          if (t.length === 1) g.lineTo(t[0].x * largura + 0.5, t[0].y * altura);
          g.stroke();
        }
        return c.toDataURL('image/png');
      },
    }));

    return (
      <div className="quadro-assinatura">
        <canvas ref={canvas} aria-label={rotulo} role="img"
          onPointerDown={inicio} onPointerMove={move} onPointerUp={fim} onPointerCancel={fim} onPointerLeave={fim} />
        {vazio && <span className="quadro-dica" aria-hidden="true">{rotulo}</span>}
        <span className="quadro-linha" aria-hidden="true" />
        <button type="button" className="botao-link quadro-limpar" onClick={limpar} disabled={vazio}>Limpar</button>
      </div>
    );
  },
);
