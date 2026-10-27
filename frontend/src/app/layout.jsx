import { Bricolage_Grotesque } from "next/font/google";
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

export default function RootLayout({ children }) {
  return (
    <html lang="pt-BR" className={display.variable}>
      <body>{children}</body>
    </html>
  );
}
