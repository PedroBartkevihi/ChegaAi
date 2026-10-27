import { useId } from "react";

// Ilustração decorativa da orla: céu, sol, pipa, morros (com o Pão de Açúcar), mar, areia e o
// calçadão de ondas. O lado esquerdo fica baixo de propósito, para o texto do destaque ficar sobre o céu.
// As cores vêm dos tokens do globals.css, pelas classes ls-*; no tema escuro o sol vira lua e aparecem estrelas.
export default function Landscape({ className = "" }) {
  const waves = `calcadao-${useId().replace(/:/g, "")}`;
  return (
    <svg
      className={`landscape ${className}`}
      viewBox="0 0 1200 360"
      preserveAspectRatio="xMaxYMax slice"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <pattern id={waves} width="64" height="30" patternUnits="userSpaceOnUse">
          <path className="ls-wave" d="M0 15 C16 1 16 29 32 15 C48 1 48 29 64 15" />
        </pattern>
      </defs>
      <rect className="ls-sky" width="1200" height="360" />
      <g className="ls-stars">
        <circle cx="640" cy="40" r="2" />
        <circle cx="700" cy="96" r="1.5" />
        <circle cx="780" cy="30" r="1.5" />
        <circle cx="870" cy="60" r="2" />
        <circle cx="1060" cy="40" r="1.5" />
        <circle cx="1110" cy="110" r="2" />
        <circle cx="1160" cy="66" r="1.5" />
        <circle cx="900" cy="170" r="1.5" />
      </g>
      <circle className="ls-halo" cx="960" cy="100" r="74" />
      <circle className="ls-sun" cx="960" cy="100" r="46" />
      <g className="ls-cloud">
        <path d="M1090 52 C1092 34 1118 30 1128 42 C1138 26 1170 30 1172 50 C1188 50 1192 68 1176 70 L1098 70 C1082 70 1080 54 1090 52 Z" />
        <path d="M700 150 C702 136 722 134 730 144 C738 132 762 136 762 150 C774 150 776 164 764 166 L706 166 C694 166 692 152 700 150 Z" />
      </g>
      <path className="ls-kite-line" d="M820 110 C806 126 832 140 814 156 C798 170 818 182 804 196" />
      <path className="ls-kite" d="M820 52 L840 80 L820 110 L800 80 Z" />
      <path className="ls-kite-cross" d="M820 52 L820 110 M800 80 L840 80" />
      <path className="ls-hill-far" d="M560 290 C640 236 700 222 770 244 C830 206 900 196 960 232 C1020 204 1100 208 1200 232 L1200 300 L560 300 Z" />
      <path className="ls-hill-mid" d="M896 292 C906 252 938 236 968 246 C988 253 994 272 1000 292 Z" />
      <path className="ls-hill-near" d="M982 292 C982 212 998 150 1040 140 C1082 132 1096 204 1102 292 Z" />
      <path className="ls-cable" d="M954 240 L1036 144" />
      <rect className="ls-cabin" x="990" y="190" width="10" height="8" rx="2" />
      <path className="ls-hill-front" d="M1076 300 C1110 252 1156 238 1200 244 L1200 300 Z" />
      <rect className="ls-sea" y="272" width="1200" height="36" />
      <path className="ls-foam" d="M60 284 q14 -6 28 0 M210 292 q14 -6 28 0 M380 282 q14 -6 28 0 M560 290 q14 -6 28 0 M760 284 q14 -6 28 0 M930 294 q14 -6 28 0 M1110 286 q14 -6 28 0" />
      <path className="ls-sand" d="M0 302 Q150 294 300 302 T600 302 T900 302 T1200 302 V334 H0 Z" />
      <path className="ls-pole" d="M436 324 L442 278" />
      <path className="ls-sun" d="M404 286 Q442 246 480 286 Z" />
      <path className="ls-kite" d="M430 262 Q434 274 432 286 L452 286 Q450 272 448 262 Z" />
      <circle className="ls-ball" cx="530" cy="318" r="7" />
      <g transform="translate(60 0)">
        <path className="ls-trunk" d="M708 326 C714 290 704 258 720 220" />
        <g className="ls-leaf">
          <path d="M720 220 C700 204 676 206 662 220 C684 214 702 218 720 220 Z" />
          <path d="M720 220 C716 196 698 184 680 186 C700 194 712 206 720 220 Z" />
          <path d="M720 220 C730 196 752 188 768 194 C748 198 732 208 720 220 Z" />
          <path d="M720 220 C744 212 766 218 776 236 C756 226 738 222 720 220 Z" />
          <path d="M720 220 C712 230 702 246 704 262 C708 244 714 232 720 220 Z" />
        </g>
      </g>
      <rect className="ls-walk" y="332" width="1200" height="28" />
      <rect y="332" width="1200" height="28" fill={`url(#${waves})`} />
    </svg>
  );
}
