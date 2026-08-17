Sim. Agora é a etapa correta. Os quatro marcadores já aparecem entre os candidatos quando possuem margem adequada; portanto, o próximo problema é exclusivamente:

> Entre dezenas de candidatos, descobrir quais quatro delimitam o gabarito.

Mas faremos uma pequena melhoria no plano: **não testaremos todas as combinações de quatro candidatos**.

Com 46 candidatos:

[
\binom{46}{4}=163.185
]

Executar isso em cada frame seria desnecessariamente caro.

## Estratégia eficiente

O `MarkerSetResolver` funcionará em três fases:

### 1. Pré-seleção por posição

Para cada papel geométrico, selecionaremos alguns candidatos mais promissores:

* `TOP_LEFT`: menores valores de (x+y);
* `TOP_RIGHT`: maiores valores de (x-y);
* `BOTTOM_RIGHT`: maiores valores de (x+y);
* `BOTTOM_LEFT`: menores valores de (x-y).

Não escolheremos apenas um imediatamente. Manteremos, por exemplo, os três melhores de cada posição.

Isso reduz o universo de dezenas de candidatos para poucas combinações.

### 2. Validação dos conjuntos

Cada conjunto de quatro será avaliado por:

* candidatos distintos;
* quadrilátero convexo;
* área da região;
* tamanhos semelhantes dos marcadores;
* distâncias coerentes;
* ordem correta dos cantos;
* confiança individual dos candidatos;
* ausência de cruzamento dos lados.

### 3. Decisão e ambiguidade

O resolvedor manterá:

* melhor conjunto;
* segundo melhor conjunto;
* diferença entre as pontuações.

Se dois conjuntos forem quase equivalentes, o resultado será rejeitado como ambíguo, em vez de escolher silenciosamente.

## Arquivos que construiremos

```text
vision/geometry/
├── CornerRole.java
├── ResolvedMarker.java
├── ResolvedMarkerSet.java
├── MarkerSetResolutionResult.java
├── MarkerSetResolverConfig.java
└── MarkerSetResolver.java
```

E para desenhar:

```text
vision/drawing/
└── ResolvedMarkerOverlayRenderer.java
```

## Integração com o pipeline

O fluxo ficará:

```text
SolidSquareMarkerDetector
    ↓
MarkerDetectionResult
    ↓
MarkerSetResolver
    ↓
MarkerSetResolutionResult
    ↓
ResolvedMarkerOverlayRenderer
```

A `MainActivity` continuará sem conhecer os cálculos geométricos.

## Laboratório OMR

Atualizaremos as etapas para:

```java
ORIGINAL("1/6 - Original"),
GRAYSCALE("2/6 - Escala de cinza"),
BLURRED("3/6 - Suavizacao"),
BINARY("4/6 - Imagem binaria"),
ACCEPTED_CANDIDATES("5/6 - Candidatos aceitos"),
RESOLVED_MARKERS("6/6 - Quatro marcadores");
```

Na última etapa:

* candidatos comuns: verde fino;
* marcadores escolhidos: magenta grosso;
* centros: amarelo;
* ligação entre os quatro: azul;
* papéis: `TL`, `TR`, `BR`, `BL`;
* rejeitado: mensagem visual;
* aceito: confiança do conjunto.

## Ordem segura de implementação

1. Criar modelos geométricos.
2. Implementar pré-seleção e pontuação.
3. Registrar o resultado no Logcat.
4. Confirmar se escolhe os quatro corretos.
5. Adicionar a sexta etapa visual.
6. Testar inclinação, distância e perspectiva.
7. Somente depois adicionar estabilidade temporal.

Ainda não faremos o recorte nem a correção de perspectiva. Primeiro precisamos provar que o conjunto de quatro é correto e estável.

Portanto, sim: seguimos agora para o `MarkerSetResolver`, começando pelos modelos e pelo algoritmo geométrico eficiente.


# -------------------

As imagens mostram que a binarização está funcionando razoavelmente bem. Os quatro marcadores existem claramente na imagem binária. O problema principal agora não é mais “enxergar preto”: é separar os quatro marcadores entre dezenas de formas parecidas.

## O que a imagem binária nos ensina

Na imagem binária:

* o papel virou preto;
* elementos escuros viraram brancos;
* os quatro marcadores aparecem;
* bolhas não preenchidas aparecem como anéis;
* bolhas preenchidas aparecem como regiões brancas;
* linhas pontilhadas e números também geram contornos;
* o pequeno centro preto continua presente em áreas preenchidas.

Isso confirma que foi correta a mudança anterior:

```text
Imagem binária → encontrar formas
Imagem cinza → medir escuridão
```

Não precisamos tentar deixar os quadrados 100% brancos na binária.

## Por que tantas bolhas viraram `SOLID_SQUARE`

O detector atual pergunta individualmente:

> Este contorno parece um quadrilátero escuro e preenchido?

Algumas bolhas marcadas atendem parcialmente a essas condições:

* são escuras;
* ocupam uma pequena região;
* o contorno ou a caixa externa produz quatro vértices;
* possuem proporções próximas de um quadrado.

O detector ainda não pergunta:

> Este candidato faz parte de um conjunto de quatro marcadores nos extremos de uma mesma região?

Essa será a função do `MarkerSetResolver`.

## Os marcadores estão perigosamente próximos das bordas

Nas duas imagens, principalmente os marcadores da direita, existe pouquíssima margem externa. O superior e o inferior direitos estão praticamente encostados no limite da imagem.

Isso pode causar:

* contorno cortado;
* união com a borda branca da página na imagem binária;
* perda de um dos lados;
* cinco ou seis vértices;
* variação entre frames;
* rejeição por preenchimento ou proporção.

Na própria imagem binária vemos uma linha branca acompanhando as bordas superior e inferior. Se ela encostar no marcador, o OpenCV pode enxergar tudo como um único contorno.

Para o próximo teste, deixe uma margem visível ao redor dos quatro marcadores:

```text
Imagem atual:

|■────────────────────────────■|

Imagem recomendada:

|   ■────────────────────■   |
|                            |
```

Não significa que o gabarito final precisará de uma margem enorme. Precisamos apenas evitar que o marcador seja cortado pela câmera. Uma pequena “área de silêncio” branca ao redor dele será importante no modelo impresso.

## Não ajustaria mais o threshold agora

A imagem binária já contém informação suficiente. Alterar agressivamente:

* `blockSize`;
* `C`;
* blur;
* threshold;
* escuridão mínima;

pode melhorar um marcador e destruir outro.

O próximo passo deve ser geométrico.

## Antes disso, vamos limpar a visualização

Os textos `SOLID_SQUARE` estão cobrindo a imagem e não acrescentam informação, pois todos os candidatos têm o mesmo tipo.

Em `MarkerOverlayRenderer.java`, localize:

```java
String label =
        marker.getCode() == null
                ? marker.getType().name()
                : "ID " + marker.getCode();

Imgproc.putText(
        rgbaFrame,
        label,
        new Point(
                corners[0].x,
                Math.max(
                        20,
                        corners[0].y - 10
                )
        ),
        Imgproc.FONT_HERSHEY_SIMPLEX,
        0.7,
        COLOR_GREEN,
        2
);
```

Substitua por:

```java
/*
 * Marcadores codificados, como ArUco, exibem seu ID.
 *
 * Quadrados simples não recebem texto porque podem existir
 * muitos candidatos no mesmo frame.
 */
if (marker.getCode() != null) {

    Imgproc.putText(
            rgbaFrame,
            "ID " + marker.getCode(),
            new Point(
                    corners[0].x,
                    Math.max(
                            20,
                            corners[0].y - 10
                    )
            ),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.7,
            COLOR_GREEN,
            2
    );
}
```

Os contornos verdes e centros amarelos permanecerão, mas sem poluição textual.

## O próximo componente

Agora podemos criar:

```text
vision/geometry/
├── CornerRole.java
├── ResolvedMarker.java
├── ResolvedMarkerSet.java
├── MarkerSetResolutionResult.java
├── MarkerSetResolverConfig.java
└── MarkerSetResolver.java
```

Ele receberá todos os candidatos:

```java
List<DetectedMarker> candidates;
```

E tentará formar conjuntos de quatro.

Cada combinação será avaliada por:

1. formar um quadrilátero convexo;
2. possuir grande área interna;
3. utilizar candidatos com tamanhos semelhantes;
4. possuir um candidato em cada extremo;
5. apresentar lados e diagonais coerentes;
6. conter a maior parte dos demais elementos dentro da região;
7. não estar excessivamente próxima do limite do frame;
8. superar uma confiança mínima;
9. não empatar com outra combinação igualmente plausível.

Resultado:

```text
46 candidatos individuais
→ várias combinações possíveis
→ melhor combinação encontrada
→ quatro marcadores confirmados
```

## Como aparecerá no Laboratório

Adicionaremos uma sexta etapa:

```text
6/6 - Quatro marcadores resolvidos
```

Nela:

* candidatos comuns: verde fino;
* quatro selecionados: azul ou magenta grosso;
* ligação entre os quatro: quadrilátero;
* nomes: `TL`, `TR`, `BR`, `BL`;
* confiança geral;
* resultado aceito ou rejeitado.

A conclusão das imagens é positiva: **os quatro marcadores já chegam à binarização e aparecem ocasionalmente como candidatos**. Agora precisamos deixar uma margem externa no teste e iniciar o resolvedor geométrico, que é justamente o componente responsável por ignorar todas essas bolhas internas.


# -----------------

Esse teste comparativo foi excelente e já permite uma decisão baseada em dados.

| Tamanho | Resultado no preview `480×720`                       |
| ------- | ---------------------------------------------------- |
| 5 mm    | Muito fraco; nunca detectou os quatro                |
| 7 mm    | Fraco; no máximo dois simultaneamente                |
| 10 mm   | Limítrofe; detectou os quatro apenas uma vez         |
| 12 mm   | Bom; detectou os quatro repetidamente                |
| 15 mm   | Bom/ótimo; várias detecções completas                |
| 20 mm   | Muito robusto; manteve os quatro por vários segundos |

## Recomendação

Para o gabarito atual e esse preview:

```text
15 × 15 mm
```

é o melhor equilíbrio entre tamanho e estabilidade.

Marcadores de 12 mm também são viáveis, mas 15 mm oferece uma margem importante para:

* celulares inferiores;
* câmera tremendo;
* baixa iluminação;
* papel inclinado;
* impressão desgastada;
* foco imperfeito.

Marcadores de 20 mm funcionam melhor, mas começam a consumir uma área excessiva do gabarito. Eu os reservaria para testes ou situações muito adversas.

Quando migrarmos para fotografia de alta resolução, poderemos testar novamente 10 ou 12 mm.

## Há outra descoberta importante: ID duplicado

Nos testes de 15 e 20 mm apareceram resultados como:

```text
[1; 2; 3; 1]
```

e:

```text
[1; 0; 2; 3; 1]
```

Se existem somente quatro marcadores físicos, isso significa que houve:

* um falso positivo reconhecido como ID `1`; ou
* o ID `1` foi impresso duas vezes no modelo.

Precisamos conferir visualmente se os quatro marcadores impressos são exatamente:

```text
superior esquerdo: 0
superior direito:  1
inferior direito:  2
inferior esquerdo: 3
```

A ordem pode ser outra, mas cada ID deve aparecer somente uma vez.

## O leitor não deve confiar apenas na lista de IDs

A validação final deve exigir:

* IDs `0`, `1`, `2` e `3`;
* uma ocorrência de cada ID;
* cada marcador no quadrante esperado;
* geometria formando um quadrilátero válido;
* estabilidade durante alguns frames consecutivos.

Por exemplo:

```text
ID 0 → quadrante superior esquerdo
ID 1 → quadrante superior direito
ID 2 → quadrante inferior direito
ID 3 → quadrante inferior esquerdo
```

Se aparecer um segundo ID `1` no centro da folha ou no quadrante errado, ele será descartado.

## Critério de estabilidade

Eu adotaria inicialmente:

```text
quatro IDs válidos durante 3 frames consecutivos
```

Depois podemos evoluir para uma pontuação considerando:

* número de marcadores;
* posição;
* tamanho em pixels;
* nitidez;
* inclinação;
* estabilidade;
* proporção esperada da folha.

Conclusão: **15 mm é a escolha mais segura para o preview atual; 12 mm é aceitável; 10 mm ainda não é confiável nessa resolução**. O teste também revelou que a validação por posição e unicidade será indispensável para impedir falsos positivos.
