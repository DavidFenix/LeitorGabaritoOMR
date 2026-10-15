# Cadernos multidisciplinares e resultados por bloco

## Objetivo

Expandir o fluxo atual de provas sem substituir os modelos existentes. Uma
prova pode continuar sendo tratada como um unico resultado geral ou pode
declarar blocos sequenciais, por exemplo:

| Ordem | Area | Componente | Questoes |
|---:|---|---|---:|
| 1 | Ciencias da Natureza | Quimica | 1 a 15 |
| 2 | Ciencias da Natureza | Fisica | 16 a 30 |
| 3 | Ciencias da Natureza | Biologia | 31 a 45 |

A geometria e a leitura do cartao-resposta nao mudam. Os blocos usam a
posicao logica das questoes ja produzida pela correcao OMR.

## Decisoes de compatibilidade

- Cada estrutura pertence a um `assessmentId@version` exato.
- Os blocos devem cobrir todas as questoes, em ordem, sem lacunas nem
  sobreposicoes.
- Area, codigo e nome de componente sao textos de dominio, nao uma lista fixa.
- Provas antigas recebem implicitamente um unico bloco `Resultado geral`.
- O bloco legado calcula por pontos ponderados e preserva o comportamento dos
  pesos existentes no gabarito.
- O fluxo escolar novo pode calcular pela quantidade de acertos, com a regra
  proporcional `acertos * 10 / itens`, uma casa decimal e arredondamento
  `HALF_UP`.
- A politica de nota possui identificador e versao. Alterar a formula exige
  publicar outra versao; resultados historicos nao devem ser recalculados em
  silencio.
- Acertos, erros, brancos, marcacoes multiplas, ambiguidades e itens ainda nao
  prontos permanecem distintos. Uma nota com pendencias pode ser exibida como
  provisoria, mas nao como resultado final.
- A nota calculada e a futura nota opcional escolhida para lancamento no SIGE
  serao campos diferentes. Um ajuste manual nunca deve apagar o calculo.

## Checkpoints

### 1. Dominio e calculo (este pacote)

- estrutura versionada da prova;
- blocos curriculares e faixas de questoes;
- politicas proporcionais versionadas;
- detalhamento de acertos, estados, pontos e nota por bloco;
- adaptador legado de bloco unico;
- testes unitarios puros, sem Android, SQLite ou OpenCV.

### 2. Persistencia

- tabelas/codec para estruturas, blocos e politicas;
- migracao aditiva, sem alterar registros historicos;
- resolucao automatica do bloco legado quando nao houver estrutura gravada;
- testes instrumentados de SQLite, versoes e reinicializacao.

### 3. Cadastro e resultados

- editor manual e importacao CSV dos blocos;
- validacao da soma de itens contra o gabarito escolhido;
- apresentacao por componente curricular;
- estados explicitos para pendente, ausente, inconsistente, em revisao e zero;
- congelamento do detalhamento junto da revisao da correcao.

### 4. Exportacoes e SIGE

- planilha com pares `ACERTOS` e `NOTA` por componente;
- CSV detalhado e PDF de relatorio;
- campo opcional de nota para o SIGE, preservando a nota calculada;
- politica de nota identificada em toda exportacao auditavel.

## Fora do checkpoint 1

Este pacote nao muda banco de dados, CSV atual de provas, telas, exportacoes,
cartao-resposta, reconhecimento OMR ou fluxos de correcao ja publicados.
