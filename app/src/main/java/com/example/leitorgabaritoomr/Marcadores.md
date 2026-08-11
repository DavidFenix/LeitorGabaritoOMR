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
