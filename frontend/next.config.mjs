const API_URL = process.env.API_URL || "http://localhost:8080";

// NEXT_OUTPUT=export gera um site estático (front no Render, chamando a API direto, com CORS);
// NEXT_OUTPUT=standalone gera um servidor Node enxuto para a imagem Docker. Sem a variável,
// o Next.js repassa /api para o Spring Boot (desenvolvimento e testes ponta a ponta).
const output = process.env.NEXT_OUTPUT;

const nextConfig = {
  // Nos E2E de dev, o indicador do Next encobria o botão Sair. Erros continuam visíveis.
  ...(process.env.E2E_MODE === "dev" && { devIndicators: false }),
  ...(output && { output }),
  ...(output !== "export" && {
    async rewrites() {
      return [
        {
          source: "/api/:path*",
          destination: `${API_URL}/api/:path*`,
        },
      ];
    },
  }),
};

export default nextConfig;
