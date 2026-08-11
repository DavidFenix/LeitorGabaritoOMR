A explicação está justamente na diferença entre **marcadores de identificação** e **marcas de registro**.

No gabarito do EvalBee existem aproximadamente 15 quadrados pretos simples organizados em:

```text
3 colunas verticais × 5 níveis horizontais
```

Eles não parecem carregar IDs como ArUco. Cada quadrado é apenas uma referência geométrica extremamente fácil de detectar.

A documentação pública do EvalBee informa que ele usa um mecanismo “SmartScan” com IA/aprendizado de máquina, mas não descreve o algoritmo interno dessas marcas. Portanto, a explicação abaixo é uma inferência técnica baseada na estrutura visível do PDF. [EvalBee](https://evalbee.com/dashboard)

## O que os quadradinhos provavelmente fazem

As três colunas funcionam como trilhos de referência:

```text
esquerda       centro       direita
   ■              ■             ■
   ■              ■             ■
   ■              ■             ■
   ■              ■             ■
   ■              ■             ■
```

Cada linha de três quadrados informa ao leitor:

* posição horizontal da área esquerda;
* posição da divisão central;
* posição horizontal da área direita;
* altura exata daquele trecho;
* escala local;
* inclinação local;
* deformação da folha.

Em vez de corrigir a página inteira usando apenas quatro cantos, o EvalBee consegue recalibrar diferentes regiões do gabarito.

## Por que usar tantos?

### 1. Redundância

Se uma marca estiver:

* coberta por um dedo;
* apagada;
* amassada;
* sombreada;
* parcialmente cortada;

ainda restarão várias outras.

Com quatro marcadores apenas, perder um significa perder 25% das referências.

### 2. Correção de deformações locais

Quatro cantos corrigem muito bem uma folha plana por transformação de perspectiva. Mas papel real pode estar:

* curvado;
* ondulado;
* dobrado;
* fotografado sobre superfície irregular.

Nesse caso, uma única transformação global não corrige perfeitamente todas as regiões. Várias linhas de referência permitem calcular onde cada bloco realmente está.

### 3. Localização dos blocos

Observe que as linhas de quadrados acompanham mudanças no conteúdo:

* identificação do aluno;
* primeiros blocos;
* bloco seguinte;
* questões finais;
* limite inferior.

Os quadrados provavelmente funcionam também como separadores ou âncoras dos blocos. O leitor não precisa procurar todas as bolhas na página inteira; ele localiza a faixa entre duas linhas de marcas e sabe quais campos existem ali.

### 4. Marcas muito simples podem ser pequenas

Um ArUco precisa preservar uma grade de aproximadamente `6×6` células. Um quadrado sólido precisa preservar apenas:

* quatro lados;
* quatro cantos;
* preenchimento preto.

Por isso, um quadrado sólido pode ser reconhecido com muito menos pixels.

### 5. Não precisam de IDs

O EvalBee já conhece o modelo gerado. Ele pode atribuir significado pela posição:

```text
primeira marca à esquerda → referência esquerda superior
segunda marca à esquerda → referência esquerda intermediária
...
```

A identidade nasce da posição na grade, não de um código desenhado dentro da marca.

## Comparação das estratégias

| ArUco                             | Quadrados do EvalBee                |
| --------------------------------- | ----------------------------------- |
| Cada marcador contém um ID        | Marcas aparentemente idênticas      |
| Precisa de muitos pixels internos | Funciona com poucos pixels          |
| Pode ser localizado isoladamente  | Depende do conjunto e do template   |
| Quatro marcas podem bastar        | Usa muitas marcas redundantes       |
| Excelente para pose e objetos     | Excelente para formulário conhecido |
| Ocupa mais espaço por marca       | Cada marca ocupa pouco espaço       |

## Isso também explica os falsos IDs que encontramos

Nosso ArUco examinava o gabarito inteiro e encontrava dezenas de quadriláteros candidatos:

```text
rejeitados=70
rejeitados=129
```

O EvalBee provavelmente procura quadrados sólidos em corredores estreitos onde sabe que as marcas devem estar. Assim, as bolhas do centro não competem com as marcas laterais.

## O que eu adotaria no nosso leitor

Eu me inspiraria nessa estratégia, sem necessariamente copiar 15 marcas:

* três trilhos: esquerdo, central e direito;
* marcas pretas sólidas de aproximadamente `4–6 mm`;
* linhas de referência no início e no fim de cada bloco;
* busca restrita a pequenas regiões;
* validação do alinhamento das marcas;
* contorno da folha como referência adicional;
* captura final em alta resolução.

Por exemplo, para um gabarito simples:

```text
■              ■              ■
                               
■              ■              ■
                               
■              ■              ■
                               
■              ■              ■
```

Seriam 12 quadrados pequenos, mas cada conjunto de três definiria precisamente uma faixa do documento.

A grande sacada do EvalBee não é um marcador mais sofisticado. É o oposto: **muitas marcas extremamente simples, pequenas, redundantes e posicionadas em locais conhecidos**.

Para nosso OMR, essa estratégia provavelmente é mais adequada do que quatro ArUcos grandes. ArUco foi a ferramenta certa para validar o protótipo; uma malha de marcas simples parece mais adequada para o formulário definitivo.
