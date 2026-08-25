package com.example.leitorgabaritoomr.application.reading;

import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionInterpretation;
import com.example.leitorgabaritoomr.vision.interpretation.QuestionMarkState;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte a saída técnica da interpretação OMR em um resultado
 * imutável e transportável da camada de negócio.
 *
 * Este componente é a única ponte entre vision.interpretation e
 * domain.reading. Ele não recalcula evidências, não reclassifica
 * questões e não depende de Android, OpenCV ou Mat.
 */
public final class OmrReadingResultMapper {

    /**
     * Cria uma nova leitura com UUID e instante atuais.
     */
    public OmrReadingResult map(
            SheetInterpretationResult source
    ) {
        validateSource(source);

        OmrLayoutDefinition layout =
                source
                .getEvidenceAggregate()
                .getLayout();

        OmrReadingResult result =
                OmrReadingResult.create(
                        layout.getId(),
                        layout.getVersion(),
                        layout.getName(),
                        mapQuestions(source)
                );

        validateEquivalentTotals(
                source,
                result
        );

        return result;
    }

    /**
     * Versão determinística destinada a testes, importações e
     * cenários em que a identidade e o instante da captura já são
     * conhecidos pela aplicação.
     */
    public OmrReadingResult map(
            SheetInterpretationResult source,
            String readingId,
            long capturedAtEpochMillis
    ) {
        validateSource(source);

        OmrLayoutDefinition layout =
                source
                .getEvidenceAggregate()
                .getLayout();

        OmrReadingResult result =
                new OmrReadingResult(
                        readingId,
                        capturedAtEpochMillis,
                        layout.getId(),
                        layout.getVersion(),
                        layout.getName(),
                        mapQuestions(source)
                );

        validateEquivalentTotals(
                source,
                result
        );

        return result;
    }

    private List<OmrQuestionResult> mapQuestions(
            SheetInterpretationResult source
    ) {
        List<QuestionInterpretation> interpretations =
                source.getQuestionInterpretations();

        List<OmrQuestionResult> results =
                new ArrayList<>(
                        interpretations.size()
                );

        for (int index = 0;
             index < interpretations.size();
             index++) {

            QuestionInterpretation interpretation =
                    interpretations.get(index);

            results.add(
                    new OmrQuestionResult(
                            index + 1,
                            interpretation
                                    .getQuestion()
                                    .getId(),
                            mapStatus(
                                    interpretation.getState()
                            ),
                            mapOptions(
                                    interpretation
                                    .getRelevantOptions()
                            ),
                            interpretation.getConfidence()
                    )
            );
        }

        return results;
    }

    private List<OmrQuestionResult.Option> mapOptions(
            List<OmrOptionDefinition> sourceOptions
    ) {
        List<OmrQuestionResult.Option> options =
                new ArrayList<>(
                        sourceOptions.size()
                );

        for (OmrOptionDefinition sourceOption
                : sourceOptions) {

            options.add(
                    new OmrQuestionResult.Option(
                            sourceOption.getId(),
                            sourceOption.getLabel()
                    )
            );
        }

        return options;
    }

    private OmrQuestionResult.Status mapStatus(
            QuestionMarkState sourceState
    ) {
        switch (sourceState) {
            case NOT_READY:
                return OmrQuestionResult.Status.NOT_READY;

            case SINGLE_MARK:
                return OmrQuestionResult.Status.SINGLE_MARK;

            case BLANK:
                return OmrQuestionResult.Status.BLANK;

            case MULTIPLE_MARKS:
                return OmrQuestionResult.Status.MULTIPLE_MARKS;

            case AMBIGUOUS:
                return OmrQuestionResult.Status.AMBIGUOUS;

            default:
                throw new IllegalStateException(
                        "Estado de questão não suportado: "
                                + sourceState
                );
        }
    }

    private void validateSource(
            SheetInterpretationResult source
    ) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "O resultado da interpretação é obrigatório."
            );
        }

        if (source.getEvidenceAggregate() == null
                || source
                .getEvidenceAggregate()
                .getLayout() == null) {

            throw new IllegalArgumentException(
                    "A interpretação deve preservar seu layout."
            );
        }
    }

    /**
     * Garante que a conversão não alterou nenhuma classificação.
     * Se uma nova categoria for criada em uma das camadas sem a
     * atualização correspondente, a falha ocorrerá imediatamente.
     */
    private void validateEquivalentTotals(
            SheetInterpretationResult source,
            OmrReadingResult result
    ) {
        requireSameCount(
                "questões",
                source.getQuestionCount(),
                result.getQuestionCount()
        );

        requireSameCount(
                "marcações únicas",
                source.getSingleMarkCount(),
                result.getSingleMarkCount()
        );

        requireSameCount(
                "questões em branco",
                source.getBlankCount(),
                result.getBlankCount()
        );

        requireSameCount(
                "marcações múltiplas",
                source.getMultipleMarkCount(),
                result.getMultipleMarkCount()
        );

        requireSameCount(
                "questões ambíguas",
                source.getAmbiguousCount(),
                result.getAmbiguousCount()
        );

        requireSameCount(
                "questões não prontas",
                source.getNotReadyCount(),
                result.getNotReadyCount()
        );

        requireSameCount(
                "questões que exigem revisão",
                source.getReviewRequiredCount(),
                result.getReviewRequiredCount()
        );
    }

    private void requireSameCount(
            String fieldName,
            int sourceCount,
            int resultCount
    ) {
        if (sourceCount != resultCount) {
            throw new IllegalStateException(
                    "A conversão alterou o total de "
                            + fieldName
                            + ": origem="
                            + sourceCount
                            + ", resultado="
                            + resultCount
                            + "."
            );
        }
    }
}
