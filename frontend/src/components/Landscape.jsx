import { useId } from "react";

// Ilustração decorativa da orla: céu, sol, pipa, morros (com o Pão de Açúcar), mar, areia e o
// calçadão de ondas. O lado esquerdo fica baixo de propósito, para o texto do destaque ficar sobre o céu.
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
          <path d="M0 15 C16 1 16 29 32 15 C48 1 48 29 64 15" fill="none" stroke="#183D32" strokeOpacity="0.5" strokeWidth="4" />
        </pattern>
      </defs>
      <rect width="1200" height="360" fill="#DDEEF3" />
      <circle cx="960" cy="100" r="74" fill="#F4C95D" opacity="0.28" />
      <circle cx="960" cy="100" r="46" fill="#F4C95D" />
      <g fill="#FFFFFF" opacity="0.85">
        <path d="M1090 52 C1092 34 1118 30 1128 42 C1138 26 1170 30 1172 50 C1188 50 1192 68 1176 70 L1098 70 C1082 70 1080 54 1090 52 Z" />
        <path d="M700 150 C702 136 722 134 730 144 C738 132 762 136 762 150 C774 150 776 164 764 166 L706 166 C694 166 692 152 700 150 Z" />
      </g>
      <path d="M820 110 C806 126 832 140 814 156 C798 170 818 182 804 196" fill="none" stroke="#D9734E" strokeWidth="2" />
      <path d="M820 52 L840 80 L820 110 L800 80 Z" fill="#D9734E" />
      <path d="M820 52 L820 110 M800 80 L840 80" stroke="#F6F3E8" strokeWidth="1.5" />
      <path d="M560 290 C640 236 700 222 770 244 C830 206 900 196 960 232 C1020 204 1100 208 1200 232 L1200 300 L560 300 Z" fill="#A9C9B3" />
      <path d="M896 292 C906 252 938 236 968 246 C988 253 994 272 1000 292 Z" fill="#6E9C80" />
      <path d="M982 292 C982 212 998 150 1040 140 C1082 132 1096 204 1102 292 Z" fill="#5E8F74" />
      <path d="M954 240 L1036 144" stroke="#183D32" strokeOpacity="0.55" strokeWidth="1.5" />
      <rect x="990" y="190" width="10" height="8" rx="2" fill="#183D32" opacity="0.7" />
      <path d="M1076 300 C1110 252 1156 238 1200 244 L1200 300 Z" fill="#24634B" />
      <rect y="272" width="1200" height="36" fill="#A9D6E0" />
      <g fill="none" stroke="#FFFFFF" strokeOpacity="0.75" strokeWidth="2" strokeLinecap="round">
        <path d="M60 284 q14 -6 28 0 M210 292 q14 -6 28 0 M380 282 q14 -6 28 0 M560 290 q14 -6 28 0 M760 284 q14 -6 28 0 M930 294 q14 -6 28 0 M1110 286 q14 -6 28 0" />
      </g>
      <path d="M0 302 Q150 294 300 302 T600 302 T900 302 T1200 302 V334 H0 Z" fill="#EBDDBA" />
      <path d="M436 324 L442 278" stroke="#7A5C3E" strokeWidth="3" strokeLinecap="round" />
      <path d="M404 286 Q442 246 480 286 Z" fill="#F4C95D" />
      <path d="M430 262 Q434 274 432 286 L452 286 Q450 272 448 262 Z" fill="#D9734E" />
      <circle cx="530" cy="318" r="7" fill="#FFFFFF" stroke="#183D32" strokeWidth="1.5" />
      <g transform="translate(60 0)">
        <path d="M708 326 C714 290 704 258 720 220" fill="none" stroke="#7A5C3E" strokeWidth="7" strokeLinecap="round" />
        <g fill="#3E7D5C">
          <path d="M720 220 C700 204 676 206 662 220 C684 214 702 218 720 220 Z" />
          <path d="M720 220 C716 196 698 184 680 186 C700 194 712 206 720 220 Z" />
          <path d="M720 220 C730 196 752 188 768 194 C748 198 732 208 720 220 Z" />
          <path d="M720 220 C744 212 766 218 776 236 C756 226 738 222 720 220 Z" />
          <path d="M720 220 C712 230 702 246 704 262 C708 244 714 232 720 220 Z" />
        </g>
      </g>
      <rect y="332" width="1200" height="28" fill="#F6F3E8" />
      <rect y="332" width="1200" height="28" fill={`url(#${waves})`} />
    </svg>
  );
}
