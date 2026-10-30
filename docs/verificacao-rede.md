# Verificação temporária da cadeia de proxies

**Estado: executada em 30/10/2026, nas fases `framework` e `native`; veja o [resultado](#resultado-30102026).**
Esta verificação é separada dos PRs de proteção do produto. Ela não configura confiança de
proxies, não cria endpoint e não autoriza mudanças em produção por si só.

## O que foi integrado

Um filtro opcional observa somente `GET /api/actuator/health` com o cabeçalho explícito
`X-Chegaai-Diagnostico: proxy-v1`. O health mantém seu status, corpo e cabeçalhos normais,
inclusive se o diagnóstico falhar. Não há acesso a repositórios, gravação de dados ou mudança
nas regras de negócio.

| Configuração | Padrão | Limite |
|---|---|---|
| `CHEGAAI_REDE_DIAGNOSTICO` | `false` | Só `true` registra o filtro |
| `CHEGAAI_REDE_DIAGNOSTICO_MAX_OBSERVACOES` | `3` | Inteiro de 1 a 3 |

O teto é de **três tentativas de observação por JVM**, compartilhado pelos contextos da
aplicação nessa JVM. Uma falha ao observar ou registrar também usa uma tentativa; não produz
repetições ilimitadas. O marcador é público e não funciona como autenticação.

O plano completo prevê **no máximo três GETs marcados por fase** (`framework` e `native`):
**até seis probes no total**, se ambas forem autorizadas. A primeira aprovação proposta cobre
somente `framework`, com **até três probes**, e a restauração ao final. Isso não é um teto global
de logs: réplicas, reinícios e revisões diferentes têm JVMs e cotas próprias. Leituras normais
do health, sem o marcador, não consomem a cota.

## Dados observados e privacidade

O logger `chegaai.rede.diagnostico`, com prefixo `REDE_DIAGNOSTICO`, recebe somente:

- Estratégia controlada: `framework`, `native`, `none` ou `outra`.
- Classe do endereço exposto pela base servlet: loopback, link-local, privado, público ou inválido.
- Literal dessa base somente quando interno; endereço público é substituído por `omitido`.
- Esquema limitado a `http`, `https` ou `outro`.
- Contagens, classes e índices das cadeias `X-Forwarded-For` e `Forwarded`.
- Índices de marcadores TEST-NET/documentação e indicadores de truncamento.
- Validade e igualdade com `X-Chegaai-Cliente-Esperado`, sem registrar seu valor.

Os índices começam em 1. Não são registrados IP público do visitante/operador, e-mail, token,
cookie, host arbitrário, cabeçalhos completos nem mensagens de exceção. Cada cadeia tem
orçamento de 2.048 caracteres e 16 componentes, somando cabeçalhos repetidos. Fragmentos
cortados pelo limite não são interpretados como IP completo. Literais de endereço têm até
64 caracteres; hostnames e zonas são recusados, sem consultas DNS.

**A base servlet não é uma prova do endereço de transporte original.** Em `framework`, o
filtro consegue observar a base anterior ao wrapper do Spring. Em `native`, a Valve do Tomcat
já pode ter alterado `getRemoteAddr()` e os cabeçalhos antes do filtro. Desembrulhar requests
não desfaz essa transformação. Os testes com Tomcat real demonstram essa diferença.

## Escopo da aprovação necessária

A primeira autorização proposta deve abranger o hash revisado e o PR separado do diagnóstico,
sua integração na `main`, o deploy temporário, a fase `framework` com até três probes abaixo,
a leitura dos resumos e a restauração ao final. A fase `native` e seu candidato de proxy serão
apresentados para decisão específica após as observações, se os dados permitirem essa escolha.
As duas fases foram autorizadas e executadas em 30/10/2026; o resultado está no fim deste documento.

Antes de começar, registrar a revisão e a configuração anteriores da API, sem copiar valores
de banco, JWT ou outros segredos. Manter a escala existente. A ordem proposta é:

1. Integrar o PR revisado com o diagnóstico **desligado** e aguardar o deploy correspondente.
2. Confirmar a disponibilidade por health normal, sem marcador.
3. Na fase `framework`, preservar a estratégia atual e ativar a flag temporária. Executar
   até três probes e ler apenas os resumos permitidos. Com os limites de autenticação
   integrados, a API não inicia mais em `framework`; veja
   [como repetir a verificação](#para-repetir-a-verificação).
4. Só depois do peer observado nessa fase, preparar um candidato específico de proxy para
   a fase `native`. A configuração candidata precisa ser explícita e revisada; não usar redes
   privadas inteiras, primeiro XFF, número presumido de hops ou ranges públicos genéricos.
   Se o dado não permitir estabelecer um candidato, parar antes dessa fase e apresentar a
   lacuna. O candidato escolhido está no resultado, no fim deste documento.
5. Sem autorização específica para a fase `native`, seguir diretamente para a restauração.
   Se essa fase for autorizada e viável, aplicar `native` com o candidato restrito, em nova
   revisão/JVM, e executar até três probes. A base observada agora será posterior à Valve.
6. Restaurar `CHEGAAI_REDE_DIAGNOSTICO=false` e a estratégia/configuração anteriores
   registradas. Confirmar o deploy e o health normal. A revisão anterior é a referência de
   rollback se a verificação prejudicar a disponibilidade.

## Probes revisáveis

O mesmo trio é usado em cada fase. A URL é a existente; não se usa login, cookie ou token.
O IP esperado deve ser conhecido pelo operador e fornecido voluntariamente somente para
comparação. Não o descobrir por serviço externo nem copiá-lo para o relatório. Se ele não for
conhecido, omitir esse cabeçalho e registrar a comparação como **pendente**. Nesse caso,
`esperadoValido=false` e `esperadoResolvido=false` não provam divergência ou resolução correta.

```powershell
$health = 'https://chegaai-api-966787539425.southamerica-east1.run.app/api/actuator/health'
$comum = @{ 'X-Chegaai-Diagnostico' = 'proxy-v1' }
# Opcional: somente se já conhecido e fornecido voluntariamente pelo operador.
# $comum['X-Chegaai-Cliente-Esperado'] = '<literal público conhecido do operador>'
# Probe 1: sem Forwarded/XFF fornecido pelo operador.
(Invoke-WebRequest -UseBasicParsing -Uri $health -Headers $comum -TimeoutSec 45).StatusCode
# Probe 2: marcador IPv4 TEST-NET em XFF.
$xff = $comum.Clone()
$xff['X-Forwarded-For'] = '198.51.100.9'
(Invoke-WebRequest -UseBasicParsing -Uri $health -Headers $xff -TimeoutSec 45).StatusCode
# Probe 3: marcador IPv4 TEST-NET em Forwarded.
$forwarded = $comum.Clone()
$forwarded['Forwarded'] = 'for=192.0.2.9;proto=https'
(Invoke-WebRequest -UseBasicParsing -Uri $health -Headers $forwarded -TimeoutSec 45).StatusCode
```

Não repetir automaticamente probes marcados, reiniciar a aplicação para renovar a cota ou
trocar de origem para obter observações adicionais. Falta de log não prova ausência de proxy;
a cota pode ter sido consumida. O relatório final guarda apenas hash/revisão, fase, resumos
permitidos, resultado da restauração e conclusões sustentadas. Não guarda cabeçalhos brutos
nem o IP esperado.

## Resultado (30/10/2026)

Os probes saíram de uma máquina do operador, pela URL `run.app`, sem domínio próprio nem
balanceador. Cada fase usou uma revisão própria e foi restaurada no fim, com o health normal
em `200/UP`. A comparação com o IP esperado foi dispensada, porque o IP não foi informado.

**Fase `framework`** (a estratégia que a `main` usava):

- Base servlet link-local `169.254.169.126` nos três probes, com `esquema=https`.
- O Cloud Run acrescentou o cliente no fim de `X-Forwarded-For` e de `Forwarded`. Sem
  cabeçalho do operador, cada cadeia chegou com um componente público; com o marcador, ele
  ficou na posição 1 e o cliente, na 2.
- O `ForwardedHeaderFilter` aceitou o `Forwarded` forjado como cliente
  (`resolvidoMarcador=true`). O XFF forjado não foi usado, porque o `Forwarded` tem precedência.

**Fase `native`**, com o candidato exato `169\.254\.169\.126`, ensaiada antes localmente:

- Nos três probes, `estrategia=native`, `baseClasse=PUBLICO`, `esquema=https` e
  `resolvidoMarcador=false`.
- O XFF forjado sobrou à esquerda depois da Valve e foi ignorado; o `Forwarded` forjado não é
  lido pelo Tomcat.
- O endereço resolvido é o único componente público que o próprio Cloud Run acrescenta.

**Configuração adotada:** `CHEGAAI_PROXIES_CONFIAVEIS=169\.254\.169\.126` no Cloud Run, antes
de integrar os limites de autenticação. Depois da integração, o login respondeu 429 ao esgotar
a cota, inclusive com `X-Forwarded-For` ou `Forwarded` forjados.

**Limitações:** o endereço vem de duas observações, em revisões diferentes, e o Google não o
documenta. Se ele mudar, a API continua iniciando, mas o limitador passa a ver todos os
visitantes com o mesmo IP. Um balanceador ou domínio próprio na frente da API também muda a
cadeia. Nos dois casos, repita a verificação.

## Para repetir a verificação

Com os limites integrados, a fase `framework` não inicia mais: a guarda exige `native` e
proxies explícitos. Repita só a fase `native`, com a configuração em uso:

1. Crie uma nova revisão com `CHEGAAI_REDE_DIAGNOSTICO=true`, mantendo `CHEGAAI_PROXIES_CONFIAVEIS`.
2. Com o health normal em `200/UP`, rode os três probes acima, uma vez só.
3. Leia os três resumos `REDE_DIAGNOSTICO` e volte a flag para `false`.

Com o proxy certo, os três resumos trazem `baseClasse=PUBLICO` e `esquema=https`. Uma classe
interna com outro literal, ou `esquema=http`, indica que o proxy mudou: troque
`CHEGAAI_PROXIES_CONFIAVEIS` pelo literal observado só depois de verificá-lo da mesma forma.

## Verificação local

`cd backend; ./mvnw.cmd -B -ntp test` executa os unitários e os testes de Tomcat real em
loopback/porta aleatória, sem banco. Os testes cobrem flag desligada, rota/método/marcador,
teto concorrente, privacidade e parsing limitado, falha do registro sem alterar o health,
wrapper do Spring e Valve nativa. Uma regressão intercepta `InetAddress.getByName` e exige
zero chamadas para entradas iniciadas por ponto em XFF, `Forwarded` e cliente esperado.
A verificação local não caracteriza a topologia do Cloud Run.
