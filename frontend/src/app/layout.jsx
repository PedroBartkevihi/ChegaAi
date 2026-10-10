import "./globals.css";

export const metadata = {
  title: "ChegaAí - Rolés presenciais com segurança",
  description: "Plataforma para criar, descobrir e participar de eventos presenciais com organizadores bem avaliados.",
};

export default function RootLayout({ children }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
