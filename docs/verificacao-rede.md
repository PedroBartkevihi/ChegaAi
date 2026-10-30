# Verificação temporária da cadeia de proxies

**Estado: preparada para revisão; execução em produção depende de autorização específica.**
Esta verificação é separada dos PRs de proteção do produto. Ela não configura confiança de
proxies, não cria endpoint e não autoriza mudanças em produção por si só.

## O que será integrado

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

O plano abaixo permite ao operador **no máximo três GETs marcados por fase**, em duas fases
(`framework` e `native`): **até seis probes no total**. Isso não é um teto global de logs:
réplicas, reinícios e revisões diferentes têm JVMs e cotas próprias. Leituras normais do health,
sem o marcador, não consomem a cota.

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

A autorização deve abranger o hash revisado e o PR separado do diagnóstico, sua integração
na `main`, o deploy temporário, as configurações das duas fases, os probes abaixo, a leitura
dos resumos e a restauração ao final. Nenhuma dessas ações foi realizada nesta preparação.

Antes de começar, registrar a revisão e a configuração anteriores da API, sem copiar valores
de banco, JWT ou outros segredos. Manter a escala existente. A ordem proposta é:

1. Integrar o PR revisado com o diagnóstico **desligado** e aguardar o deploy correspondente.
2. Confirmar a disponibilidade por health normal, sem marcador.
3. Na fase `framework`, preservar a estratégia atual e ativar a flag temporária. Executar
   até três probes e ler apenas os resumos permitidos.
4. Só depois do peer observado nessa fase, preparar um candidato específico de proxy para
   a fase `native`. A configuração candidata precisa ser explícita e revisada; não usar redes
   privadas inteiras, primeiro XFF, número presumido de hops ou ranges públicos genéricos.
   Se o dado não permitir estabelecer um candidato, parar antes dessa fase e apresentar a
   lacuna. Nenhum candidato está escolhido neste documento.
5. Se autorizado e viável, aplicar `native` com esse candidato restrito, em nova revisão/JVM,
   e executar até três probes. A base observada agora será posterior à Valve.
6. Restaurar `CHEGAAI_REDE_DIAGNOSTICO=false` e a estratégia/configuração anteriores
   registradas. Confirmar o deploy e o health normal. A revisão anterior é a referência de
   rollback se a verificação prejudicar a disponibilidade.

## Probes revisáveis

O mesmo trio é usado em cada fase. A URL é a existente; não se usa login, cookie ou token.
O IP esperado deve ser conhecido pelo operador e fornecido voluntariamente somente para
comparação. Não o descobrir por serviço externo nem copiá-lo para o relatório.

```powershell
$health = 'https://chegaai-api-966787539425.southamerica-east1.run.app/api/actuator/health'
$ipEsperado = '<literal público conhecido do operador>'
$comum = @{
  'X-Chegaai-Diagnostico' = 'proxy-v1'
  'X-Chegaai-Cliente-Esperado' = $ipEsperado
}
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

## Verificação local

`cd backend; ./mvnw.cmd -B -ntp test` executa os unitários e os testes de Tomcat real em
loopback/porta aleatória, sem banco. Os testes cobrem flag desligada, rota/método/marcador,
teto concorrente, privacidade e parsing limitado, falha do registro sem alterar o health,
wrapper do Spring e Valve nativa. A verificação local não caracteriza a topologia do Cloud Run.
