# Diagramas UML

Modelagem do ChegaAí em [PlantUML](https://plantuml.com), gerada a partir do código atual.
Cada diagrama tem o fonte (`.puml`) e a imagem renderizada (`.svg`).

| Diagrama | O que mostra |
|---|---|
| [Casos de uso](casos-de-uso.svg) | Atores (visitante, participante, organizador, Embaixador, moderador) e o que cada um faz |
| [Classes do domínio](classes.svg) | Entidades JPA, herança de usuários, relações com multiplicidade e enums |
| [Sequência: autenticação](sequencia-autenticacao.svg) | Login com BCrypt e JWT e uma requisição autenticada |
| [Sequência: inscrição e fila](sequencia-inscricao.svg) | Regras de inscrição, fila de espera e promoção por ordem de chegada |
| [Implantação](implantacao.svg) | Versão online: Render, Google Cloud Run, Neon e o fluxo de deploy |

## Como regenerar as imagens

Com Java instalado e o [`plantuml.jar`](https://github.com/plantuml/plantuml/releases):

```bash
java -jar plantuml.jar -tsvg -charset UTF-8 docs/uml/*.puml
```

O estilo comum fica em `estilo.iuml`. Os diagramas de casos de uso, classes e
implantação usam o layout interno do PlantUML (`!pragma layout smetana`), então
não precisam do Graphviz.
