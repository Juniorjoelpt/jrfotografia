// Logo JR Fotografia em vetor, desenhado com currentColor para herdar a cor do texto.

const MONOGRAMA = (
  <>
    <path d="M150 40 H240 V270 A87.5 87.5 0 0 1 100 340" />
    <path d="M266 272 V40 H322 A44 44 0 0 1 322 128 H266" />
    <path d="M318 128 L440 272" />
  </>
);

const PALAVRA =
  'M22 488 V460 H44 M22 474 H40 M91 460 A11 14 0 1 1 90.99 460 M138 460 H160 M149 460 V488 ' +
  'M207 460 A11 14 0 1 1 206.99 460 M275 466 A11 14 0 1 0 276 481 V475 H266 ' +
  'M312 488 V460 H328 A6 7 0 0 1 328 474 H312 M326 474 L334 488 M370 488 L381 460 L392 488 ' +
  'M428 488 V460 H450 M428 474 H446 M497 460 V488 M544 488 L555 460 L566 488';

export function Logo({ largura = 160, titulo = 'JR Fotografia' }: { largura?: number; titulo?: string }) {
  return (
    <svg viewBox="0 0 600 520" width={largura} height={(largura * 520) / 600} role="img" aria-label={titulo}
      fill="none" stroke="currentColor" strokeWidth={9} strokeLinecap="butt" strokeLinejoin="miter">
      {MONOGRAMA}
      <path d={PALAVRA} strokeWidth={4} />
    </svg>
  );
}

export function Monograma({ tamanho = 32, titulo = 'JR' }: { tamanho?: number; titulo?: string }) {
  return (
    <svg viewBox="60 20 410 340" width={tamanho} height={(tamanho * 340) / 410} role="img" aria-label={titulo}
      fill="none" stroke="currentColor" strokeWidth={12} strokeLinecap="butt" strokeLinejoin="miter">
      {MONOGRAMA}
    </svg>
  );
}
