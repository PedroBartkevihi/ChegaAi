import { Bike, Footprints, MapPin, Palette, PawPrint, Sun } from "lucide-react";

// O Lucide não tem bola de futebol nem patins: os dois são desenhados aqui no mesmo traço.
function SvgIcon({ size = 24, children, ...props }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...props}
    >
      {children}
    </svg>
  );
}

function SoccerBall(props) {
  return (
    <SvgIcon {...props}>
      <circle cx="12" cy="12" r="10" />
      <path d="m12 7.5 4.2 3-1.6 5h-5.2l-1.6-5z" />
      <path d="M12 7.5V2.3M16.2 10.5l4.9-1.6M14.6 15.5l3 4.2M9.4 15.5l-3 4.2M7.8 10.5 2.9 8.9" />
    </SvgIcon>
  );
}

function RollerSkate(props) {
  return (
    <SvgIcon {...props}>
      <path d="M5 3h6v6.5c0 1 .8 1.5 1.7 1.7l4.6 1c1.6.4 2.7 1.7 2.7 3.3V17H5z" />
      <path d="M11 6h-2M11 9H9" />
      <circle cx="8" cy="20" r="1.6" />
      <circle cx="17" cy="20" r="1.6" />
    </SvgIcon>
  );
}

const ICONS = {
  futebol: SoccerBall,
  pets: PawPrint,
  patinacao: RollerSkate,
  passeio: Footprints,
  esporte: Bike,
  cultura: Palette,
  lazer: Sun,
};

export default function CategoryIcon({ slug, size = 16, ...props }) {
  const Icon = ICONS[slug] || MapPin;
  return <Icon size={size} aria-hidden="true" focusable="false" {...props} />;
}
