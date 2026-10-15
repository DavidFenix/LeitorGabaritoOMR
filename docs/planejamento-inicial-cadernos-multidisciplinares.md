A melhor decisão é expandir o modelo atual, sem criar uma implementação paralela e sem perder nenhum fluxo existente. A geometria do cartão-resposta também não precisa mudar: os blocos serão metadados da prova, associados aos números das questões.

O PDF mostra exatamente o padrão necessário: componentes agrupados por área, uma quantidade variável de questões para cada componente e duas colunas por resultado — acertos e nota. A conversão atual é proporcional a 10, com uma casa decimal, mas as quantidades variam bastante entre turmas e componentes. NOTAS BIMESTRAIS 3º PERIODO 2026 - NOTAS 2026.pdf

## Modelo proposto

Uma prova continuará sendo uma única prova, porém poderá ter zero, um ou vários blocos:

| Elemento | Exemplo |
|---|---|
| Prova | Caderno de Ciências da Natureza |
| Total de itens | 45 |
| Bloco 1 | Química — questões 1 a 15 |
| Bloco 2 | Física — questões 16 a 30 |
| Bloco 3 | Biologia — questões 31 a 45 |
| Gabarito | Continua sendo único, com 45 respostas |
| Cartão-resposta | Continua usando a geometria atual v4 |
| Aplicação | Continua vinculada à turma e chamada congelada |
| Resultado | Passa a ter total geral e detalhamento por bloco |

Exemplo de resultado de um aluno:

| Resultado | Acertos | Nota calculada |
|---|---:|---:|
| Química | 8/15 | 5,3 |
| Física | 12/15 | 8,0 |
| Biologia | 10/15 | 6,7 |
| Total do caderno | 30/45 | opcional |

## Compatibilidade com o que já existe

Não criaremos “um novo tipo de prova” incompatível. A regra será:

- Prova antiga ou sem divisão: recebe virtualmente um bloco chamado `Resultado geral`.
- Prova nova: pode possuir vários blocos disciplinares.
- Cartões v3 e v4 continuam reconhecidos normalmente.
- Gabaritos, aplicações, chamadas congeladas, tentativas e revisões continuam funcionando.
- Resultados históricos não serão reescritos.
- Uma nova correção continuará criando uma revisão, agora com resultados detalhados por bloco.
- O professor corretor não precisará ser professor da turma. A aplicação pertence à turma; o responsável pela correção será apenas informação de auditoria.

## Estrutura nova

Eu sugiro acrescentar estes conceitos:

### 1. Bloco da prova

`OmrAssessmentSection`, contendo:

- área do conhecimento opcional;
- sigla;
- nome do componente;
- ordem;
- primeira questão;
- quantidade de questões;
- última questão calculada;
- política de cálculo;
- parâmetros da nota.

Os nomes não devem ser enumerações fixas. O sistema poderá sugerir Química, Física, Biologia etc., mas também aceitar componentes como `FGB1`, `IFA`, `CN_ENEM` ou qualquer denominação usada por outra escola.

### 2. Política de cálculo versionada

A fórmula não deve ficar dentro da tela nem ser gravada diretamente no SQL. Teremos uma estratégia como:

```text
PROPORCIONAL_0_A_10_V1
nota = arredondar(acertos × 10 ÷ quantidade_de_itens, 1 casa)
```

Assim:

- `8/15 = 5,3`
- `12/15 = 8,0`
- `21/35 = 6,0`

No futuro podemos adicionar outra política — pesos, penalização, média por habilidades ou fórmula da rede — sem alterar provas antigas. Cada aplicação guarda qual versão da fórmula utilizou.

### 3. Resultado congelado por bloco

Cada revisão de correção deve guardar, por bloco:

- itens previstos;
- acertos;
- erros;
- em branco;
- marcações múltiplas/inválidas;
- nota calculada;
- política e parâmetros utilizados;
- data da correção.

Isso impede que uma futura mudança de fórmula altere notas históricas.

### 4. Nota para lançamento no SIGE

Devemos separar claramente:

- `notaCalculada`: imutável, produzida pela regra;
- `notaParaLancamento`: inicialmente igual à calculada, mas editável;
- justificativa do ajuste;
- data e responsável pelo ajuste.

Alterar a nota para lançamento nunca apagará a nota calculada. A integração automática com o SIGE pode ficar para o futuro; inicialmente o professor apenas copia o valor escolhido.

## Fluxo na tela

Na criação da prova:

1. Informar o total de questões.
2. Escolher entre `Resultado geral` e `Dividir por componentes`.
3. Para cada componente, informar área, nome, sigla e quantidade.
4. O app calcula automaticamente os intervalos.
5. Exibe `45 de 45 questões distribuídas`.
6. Impede salvar se houver lacunas, sobreposição ou soma diferente do total.
7. Ao publicar a versão, a composição fica congelada.

Na correção:

1. O professor escolhe a aplicação/turma.
2. Corrige normalmente o cartão completo.
3. O app compara todas as respostas uma única vez.
4. Distribui automaticamente os resultados pelos blocos.
5. Mostra total geral e cartões individuais por componente.

Nos resultados:

- filtro por turma;
- filtro por prova;
- filtro por componente;
- visualização de acertos ou notas;
- exportação completa;
- exportação somente de uma disciplina.

## Exportação

A principal exportação deverá ser XLSX, reproduzindo a lógica da planilha atual:

```text
Turma | Nº | Aluno | QUI Acertos | QUI Nota | FIS Acertos | FIS Nota | BIO Acertos | BIO Nota
```

Também podemos manter:

- CSV para interoperabilidade;
- PDF para relatório e conferência;
- exportação filtrada de apenas uma disciplina.

No celular, não tentaremos reproduzir aquela planilha extremamente larga. A tela usará filtros e cartões; somente o Excel terá a estrutura horizontal completa.

## Cuidados importantes

Também devemos distinguir:

- aluno ausente;
- correção ainda pendente;
- cartão corrigido com zero acertos;
- cartão inválido.

Na planilha atual, células vazias e notas `0,0` podem gerar ambiguidade. No app, um ausente não poderá parecer um aluno que realizou a prova e tirou zero.

## Implementação segura

Eu dividiria em quatro entregas:

1. Modelo de blocos, validações e calculadora versionada, com testes unitários.
2. Persistência SQLite aditiva e compatibilidade com provas antigas.
3. Criação da prova e apresentação dos resultados por disciplina.
4. Exportações XLSX/CSV/PDF, nota para SIGE e regressão completa.

Não devemos:

- alterar a geometria OMR;
- criar um cartão separado para cada disciplina;
- apagar o resultado geral atual;
- fixar as disciplinas no código;
- substituir registros históricos;
- embutir a fórmula diretamente nas telas.

A nova etapa pode ser chamada de **Cadernos multidisciplinares e resultados por bloco**. Para começarmos a implementação sobre a versão exata `b23afac`, o próximo passo é disponibilizar um novo `app.zip` dessa máquina já sincronizada.