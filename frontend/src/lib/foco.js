// Ao fechar um diálogo, devolve o foco a quem o abriu. Se esse botão sumiu (rolé excluído, formulário
// de criação desmontado), vai para o título da lista de rolés ou, fora do feed, para o conteúdo principal.
export function returnFocus(opener) {
  if (opener instanceof HTMLElement && opener.isConnected && opener !== document.body) {
    opener.focus();
    return;
  }
  (document.getElementById("roles-disponiveis") || document.getElementById("conteudo"))?.focus();
}
