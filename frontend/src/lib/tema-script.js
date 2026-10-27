// Tema: "system" (padrão, segue o sistema), "light" ou "dark", guardado no navegador.
// O <html> recebe data-theme já resolvido ("light" ou "dark"); o CSS troca os tokens por ele.
// Arquivo sem React: o layout (componente de servidor) importa o THEME_SCRIPT daqui.
export const THEME_KEY = "chegaai.theme";
export const THEME_CHOICES = ["system", "light", "dark"];
export const DARK_QUERY = "(prefers-color-scheme: dark)";
// Roda no <head> antes da primeira pintura (ver app/layout.jsx), para a página não piscar no tema errado.
// Mantém a mesma regra de applyTheme, em lib/tema.js.
export const THEME_SCRIPT = `(function(){try{var c=localStorage.getItem("${THEME_KEY}");var d=c==="dark"||(c!=="light"&&matchMedia("${DARK_QUERY}").matches);document.documentElement.setAttribute("data-theme",d?"dark":"light")}catch(e){}})()`;
