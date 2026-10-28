import { Bricolage_Grotesque } from "next/font/google";
import { THEME_SCRIPT } from "../lib/tema-script";
import "./globals.css";

// Fonte dos títulos e da marca; o restante da interface usa a fonte do sistema.
const display = Bricolage_Grotesque({
  subsets: ["latin"],
  variable: "--font-display",
  display: "swap",
});

export const metadata = {
  title: "ChegaAí — Rolés ao ar livre no Rio",
  description: "Descubra e organize rolés presenciais no Rio de Janeiro, com foco em atividades ao ar livre. Confira a reputação dos organizadores e participe.",
};

// O servidor manda o tema claro; o script do <head> troca para o tema escolhido antes da primeira
// pintura, e o suppressHydrationWarning aceita o data-theme que ele deixou no <html>.
export default function RootLayout({ children }) {
  return (
    <html lang="pt-BR" className={display.variable} data-theme="light" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
