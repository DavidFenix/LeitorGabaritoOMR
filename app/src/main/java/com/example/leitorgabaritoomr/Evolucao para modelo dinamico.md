Sim — o projeto está bem preparado para essa evolução, mas ainda não completamente pronto para provas de qualquer quantidade de itens.

O melhor caminho é preservar a versão atual como uma edição inicial offline e evoluir o mesmo código para o modo escolar. Não recomendo criar dois motores OMR nem manter duas ramificações permanentes, pois elas acabariam divergindo.

## O que já está preparado

| Componente                              | Situação atual                                            |
| --------------------------------------- | --------------------------------------------------------- |
| `OmrAnswerKeyDefinition`                | Aceita quantidade variável de questões                    |
| `OmrReadingResult` e `OmrGradingResult` | Trabalham com listas dinâmicas                            |
| Correção por `questionId`               | Reutilizável para 1, 10, 20 ou mais itens                 |
| Medição das bolhas                      | Percorre o layout recebido                                |
| Identificação do aluno                  | Já existe e pode ser reaproveitada                        |
| Histórico SQLite                        | Guarda aluno, gabarito, leitura e resultado               |
| Layout físico                           | Ainda fixo em 52 questões × 4 alternativas                |
| Captura e tela de resultado             | Ainda usam diretamente `AvalieCeDevelopmentLayoutFactory` |
| Turmas, provas e aplicações             | Ainda precisam ser modeladas                              |
| Autenticação e sincronização            | Ainda não existem                                         |

Portanto, o motor lógico já é variável. O bloqueio principal é transformar o layout físico fixo em um catálogo de modelos OMR versionados.

## Como preservar a versão inicial

Devemos fazer duas coisas:

1. Criar um marco no Git, por exemplo:

   `v0.1.0-offline-inicial`

2. Manter um único aplicativo com dois espaços de trabalho:

    * **Continuar sem conta**: funcionamento local atual;
    * **Entrar na escola**: autenticação, permissões e sincronização futura.

O usuário sem conta teria automaticamente um `workspace` local. O professor autenticado acessaria um ou mais `workspaces` escolares. O motor OMR seria exatamente o mesmo.

Essa abordagem segue a recomendação de separar UI, domínio e repositórios, mantendo uma única fonte de verdade para os dados. [Android Architecture Recommendations](https://developer.android.com/topic/architecture/recommendations)

## 1. Prova com 10 itens e modelo para impressão

Este deve ser o primeiro desenvolvimento.

Precisaremos criar algo semelhante a:

* `OmrSheetTemplateSpec`
* `OmrDynamicLayoutFactory`
* `OmrLayoutCatalog`
* `OmrSheetTemplateGenerator`
* `OmrLayoutResolver`

Uma especificação poderia conter:

* quantidade de questões;
* alternativas por questão;
* quantidade de colunas ou blocos;
* dimensões canônicas;
* posição dos marcadores;
* geometria das bolhas;
* identificação e versão do modelo.

Em vez de uma única folha para tudo, podemos ter famílias:

* compacto: 1–10 itens;
* médio: 11–30;
* grande: 31–60;
* denso: 61–90.

Cada geometria publicada deve ser imutável. Se alterarmos posições ou tamanhos, criaremos uma nova versão.

### Formatos para baixar

O aplicativo deve gerar:

* **SVG**, recomendado para inserir no Word sem perder qualidade;
* **PNG em alta resolução**, para compatibilidade;
* **PDF**, pronto para imprimir.

### Redimensionamento sem manter a proporção

Uma deformação global moderada pode ser corrigida. Os quatro marcadores permitem que a homografia reverta escala horizontal, vertical, rotação e perspectiva, desde que todo o cartão seja deformado junto.

Não conseguiremos garantir leitura quando houver:

* deformação extrema;
* baixa resolução;
* marcador cortado;
* bolhas e marcadores redimensionados separadamente;
* recorte automático do editor;
* cartão pequeno demais;
* folha muito ondulada.

Portanto, exibiremos:

> Recomenda-se manter a proporção original. Redimensionamentos moderados podem ser corrigidos automaticamente.

O leitor também deverá recusar cartões cuja qualidade geométrica esteja abaixo do mínimo, em vez de produzir uma correção duvidosa.

## 2. Separar três conceitos

Precisamos deixar de chamar tudo de “gabarito”:

* **Prova**: Matemática – 1º bimestre, por exemplo;
* **Gabarito oficial**: respostas corretas;
* **Cartão-resposta OMR**: imagem com bolhas, marcadores e QR Code.

Isso evitará ambiguidades no banco, na interface e nos relatórios.

## 3. Modelo escolar local

Antes da autenticação, implementaremos o fluxo escolar completamente no próprio aparelho:

* `Workspace`
* `Assessment` — prova;
* `AssessmentVersion` — versão e gabarito oficial;
* `SheetTemplate` — modelo OMR;
* `ClassGroup` — turma;
* `Student`
* `Enrollment` — aluno na turma;
* `AssessmentApplication` — prova aplicada à turma;
* `StudentAttempt` — correção daquele aluno.

A relação principal será:

```text
Prova/versão + turma = aplicação

Aplicação + aluno = tentativa/correção
```

O `OmrStudentIdentity` atual poderá continuar sendo guardado como fotografia histórica do aluno, mas o cadastro permanente usará `Student` e `Enrollment`.

Para esse volume de relacionamentos, vale migrar gradualmente a persistência para Room, mantendo os repositórios atuais atrás das interfaces. O Android recomenda Room para dados estruturados, consultas validadas e migrações de banco. [Documentação do Room](https://developer.android.com/training/data-storage/room)

## 4. Cadastro da turma

A turma permitirá:

* inclusão manual de alunos;
* edição e exclusão;
* importação CSV;
* pré-visualização antes de confirmar;
* identificação de matrículas repetidas;
* relatório das linhas inválidas;
* preservação de zeros à esquerda nas matrículas.

O CSV deve aceitar inicialmente algo simples:

```csv
matricula,nome
000123,Ana Beatriz
000124,Bruno Lima
```

## 5. Fluxo de correção

O fluxo recomendado será:

1. Professor abre a prova.
2. Seleciona a turma.
3. Visualiza todos os alunos.
4. Toca no aluno.
5. Lê o QR Code do cartão.
6. O aplicativo confirma prova, versão e modelo.
7. A câmera OMR abre automaticamente.
8. A correção é registrada.
9. O aplicativo retorna à turma.
10. O aluno aparece como corrigido.
11. O próximo aluno ainda não corrigido fica em destaque.

O QR Code não deve conter respostas corretas, pois ficará visível aos alunos. Ele pode conter:

* versão do protocolo;
* ID e versão da prova;
* ID e versão do modelo OMR;
* quantidade de questões e alternativas;
* código de verificação.

A implementação ficará atrás de uma interface `OmrQrDecoder`, permitindo usar OpenCV ou um leitor dedicado, como o [ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android).

## 6. Resultados da turma

A primeira tela mostrará:

```text
Ana Beatriz       8/10 · 80%
Bruno Lima        6/10 · 60%
Carlos Eduardo    Não corrigido
```

A segunda será uma tabela:

| Aluno          | Acertos | Total | Percentual | Situação  |
| -------------- | ------: | ----: | ---------: | --------- |
| Ana Beatriz    |       8 |    10 |        80% | Concluído |
| Bruno Lima     |       6 |    10 |        60% | Concluído |
| Carlos Eduardo |       — |    10 |          — | Pendente  |

Depois poderemos gerar PDF pelo próprio Android usando `PdfDocument`. [Android PdfDocument](https://developer.android.com/reference/android/graphics/pdf/PdfDocument)

## 7. Autenticação e vários professores

Isso deve vir depois que o fluxo local estiver estável.

Autenticação sozinha não permitirá compartilhamento entre celulares. Será necessário um servidor central com:

* usuários e escolas;
* professores e grupos;
* disciplinas e turmas;
* proprietário da prova;
* permissões para corrigir;
* permissões para visualizar;
* permissões para imprimir/exportar;
* auditoria de quem corrigiu;
* sincronização e resolução de conflitos.

Mesmo autenticado, o aplicativo deve continuar funcionando sem internet. O banco local será a fonte imediata, e o servidor será sincronizado posteriormente, conforme a arquitetura offline-first recomendada pelo Android. [Guia offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first)

## Ordem recomendada

1. Preservar a versão offline atual no Git.
2. Criar o modelo dinâmico de cartões para 1–90 questões.
3. Começar com testes automatizados para **1 e 10 itens**.
4. Gerar SVG/PNG/PDF do cartão de 10 itens.
5. Realizar o ciclo completo: gerar → imprimir → marcar → fotografar → corrigir.
6. Criar prova, turma, aluno, matrícula e aplicação local.
7. Implementar importação CSV.
8. Criar o fluxo prova → turma → aluno → QR → OMR.
9. Criar lista consolidada, tabela e PDF.
10. Adicionar autenticação, servidor, permissões e sincronização.

Minha recomendação concreta é começar agora pelo núcleo reutilizável: **modelo dinâmico de cartão OMR**, sem mexer ainda nas telas. O primeiro marco será comprovar por testes que o domínio consegue gerar layouts válidos e estáveis para exatamente **1 e 10 questões**.
