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

### 1. Dominio e calculo (concluido)

- estrutura versionada da prova;
- blocos curriculares e faixas de questoes;
- politicas proporcionais versionadas;
- detalhamento de acertos, estados, pontos e nota por bloco;
- adaptador legado de bloco unico;
- testes unitarios puros, sem Android, SQLite ou OpenCV.

### 2. Persistencia (concluido)

- banco escolar evoluido de v5 para v6 por migracao apenas aditiva;
- tabelas normalizadas para estruturas, blocos e politicas versionadas;
- payloads binarios independentes, versionados e protegidos por CRC32;
- gravacao transacional: uma estrutura nunca fica salva pela metade;
- uma identidade de politica nao pode receber outra formula silenciosamente;
- resolucao automatica do bloco legado quando uma prova integra nao possuir
  estrutura gravada;
- falha fechada quando uma estrutura, bloco ou politica estiver corrompido;
- testes unitarios dos codecs e testes instrumentados de SQLite, versoes,
  reinicializacao e migracao real de banco v5.

### 3A. Nucleo do cadastro de blocos (concluido)

- CSV independente de blocos, com uma linha por componente curricular;
- referencia por codigo usado no CSV de provas ou pelo ID exato da prova;
- ordem dos blocos explicita, continua e sem repeticoes;
- inicio de cada faixa calculado automaticamente pela soma dos blocos
  anteriores;
- validacao obrigatoria da soma de itens contra a versao exata do gabarito;
- politica inicial `proportional-correct-questions@v1`, correspondente a
  `acertos * 10 / itens`, uma casa decimal e `HALF_UP`;
- reimportacao identica idempotente e tentativa de alterar estrutura ja
  publicada rejeitada;
- importacao de varias estruturas em uma unica transacao SQLite.

Formato canonico do arquivo:

```text
codigo_prova;versao_prova;ordem_bloco;codigo_bloco;area;codigo_componente;componente;itens
NAT-1;1;1;QUI;Ciencias da Natureza;QUI;Quimica;15
NAT-1;1;2;FIS;Ciencias da Natureza;FIS;Fisica;15
NAT-1;1;3;BIO;Ciencias da Natureza;BIO;Biologia;15
```

`codigo_bloco`, `area` e `codigo_componente` podem ficar vazios. O campo
`componente` e a quantidade positiva de `itens` sao obrigatorios. O nucleo
estabelece a operacao validada e transacional usada pelas telas.

### 3B1. Cadastro de blocos nas telas (concluido)

- cada prova informa se a estrutura curricular ainda precisa ser configurada;
- editor manual permite ordenar componentes, informar area opcional e definir
  a quantidade de questoes de cada bloco;
- total informado e comparado ao gabarito antes da publicacao;
- estrutura publicada fica somente para consulta; qualquer mudanca exige uma
  nova versao da prova;
- importacao de provas e importacao de blocos permanecem comandos separados;
- CSV de blocos possui previa e importacao transacional, sem gravacao parcial;
- pesquisa de provas tambem localiza area, codigo e nome dos componentes.

### 3B2. Resultados e congelamento (concluido)

- toda aplicacao nova fotografa a estrutura exata da prova no momento em que
  a chamada e congelada, inclusive quando a prova usa o bloco unico legado;
- toda nova revisao de correcao grava novamente essa estrutura junto do
  vinculo com o historico OMR;
- o resultado por componente e reproduzido pela combinacao imutavel entre a
  estrutura congelada na revisao e o resultado completo guardado no historico;
- alteracoes ou publicacoes posteriores de blocos nao recalculam silenciosamente
  aplicacoes e correcoes ja realizadas;
- os payloads binarios de aplicacao e tentativa passaram para o formato v2,
  mantendo leitura integral dos payloads v1 sem exigir alteracao no esquema
  SQLite nem reinstalacao do aplicativo;
- registros v1, que nao possuem fotografia curricular, continuam usando um
  unico bloco `Resultado geral`, com a quantidade historica de questoes;
- a tela preserva a nota geral existente e acrescenta, para cada componente,
  area e codigo opcionais, acertos, quantidade de itens, nota calculada, nota
  maxima e indicacao de resultado provisorio ou zero;
- a busca dos resultados tambem encontra area, codigo e nome do componente;
- os estados operacionais sao distintos: `PENDING` (ainda nao corrigido),
  `ABSENT` (a tentativa referencia um historico que nao foi localizado),
  `INCONSISTENT` (identidade ou estrutura incompativel), `IN_REVIEW`
  (correcao ainda provisoria), `ZERO` (nota zero real) e `FINAL`;
- ausencia de historico, inconsistencia e nota zero nunca sao convertidas uma
  na outra; a tela informa o caso exato e o resumo contabiliza os problemas de
  integridade separadamente dos pendentes;
- falha de estrutura fecha apenas o detalhamento por bloco: quando o historico
  geral e valido, sua nota total continua visivel para diagnostico e nao e
  descartada.

### 4A. Exportacoes por componente (concluido)

- CSV e planilha preservam, a esquerda, as colunas gerais historicas e
  acrescentam um par `ACERTOS` e `NOTA` para cada componente da estrutura
  congelada;
- pendencia, historico ausente ou detalhamento inconsistente deixam as celulas
  do componente vazias, sem converter ausencia de dado em nota zero;
- o CSV registra area, codigo, faixa de questoes, politica versionada e nota
  maxima antes da tabela de alunos;
- a planilha possui a aba `Resultados`, com os pares dinamicos, e a aba
  `Componentes`, com os metadados necessarios para auditoria;
- para facilitar a consulta pelo celular, `Resultados` inicia diretamente no
  cabecalho da tabela e congela somente essa linha; os metadados gerais ficam
  preservados em tres linhas compactas depois do ultimo participante;
- o PDF mantem as paginas do resultado geral e acrescenta paginas separadas
  por componente, com acertos, nota calculada e situacao de cada aluno;
- estruturas legadas de bloco unico e aplicacoes antigas sem fotografia
  curricular continuam no formato geral anterior, sem colunas duplicadas;
- toda exportacao detalhada identifica a politica e sua versao. Os valores
  exportados sao os calculados e congelados pelo aplicativo, nao formulas
  dependentes do programa usado para abrir o arquivo.

### 4B. Nota opcional para o SIGE (planejado)

- campo opcional de nota escolhida para lancamento no SIGE;
- persistencia separada da nota calculada e de seu historico auditavel;
- edicao e limpeza explicitas, sem substituir silenciosamente o calculo;
- inclusao da nota escolhida nas exportacoes quando ela existir.

## Fora dos checkpoints 1, 2, 3A, 3B1, 3B2 e 4A

Os checkpoints multidisciplinares nao mudam o cartao-resposta nem o
reconhecimento OMR. O CSV de blocos e adicional e nao altera a interpretacao
do CSV de provas nem de arquivos antigos. A versao 6 do banco apenas
acrescenta as tabelas de estruturas; os payloads de aplicacao e tentativa
continuam no formato v2 do checkpoint 3B2. O checkpoint 4A altera somente a
apresentacao dos arquivos exportados e nao exige migracao. A nota opcional
para lancamento no SIGE permanece reservada ao checkpoint 4B.
