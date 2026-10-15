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

### 3A. Nucleo do cadastro de blocos (checkpoint atual)

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
`componente` e a quantidade positiva de `itens` sao obrigatorios. Este
checkpoint ainda nao expoe os comandos nas telas; ele estabelece a operacao
validada e transacional que o editor visual utilizara.

### 3B. Telas e resultados

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

## Fora dos checkpoints 1, 2 e 3A

Estes checkpoints ainda nao mudam o CSV atual de provas, telas, exportacoes,
cartao-resposta, reconhecimento OMR ou fluxos de correcao ja publicados. O
CSV de blocos e adicional e nao altera a interpretacao de arquivos antigos.
A versao 6 do banco apenas acrescenta as novas tabelas e preserva os
registros historicos existentes.
