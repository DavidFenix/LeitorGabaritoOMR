package com.example.leitorgabaritoomr.domain.reading;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Resultado imutavel e transportavel de uma leitura OMR completa.
 *
 * Guarda somente informacao de negocio: identidade da leitura,
 * identidade do layout e respostas interpretadas. Nao transporta
 * Mat, imagem, contorno, medicao nem objetos internos do pipeline.
 */
public final class OmrReadingResult
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String readingId;
    private final long capturedAtEpochMillis;

    private final String layoutId;
    private final int layoutVersion;
    private final String layoutName;

    private final List<OmrQuestionResult>
            questionResults;

    private final Map<OmrQuestionResult.Status, Integer>
            countByStatus;

    public OmrReadingResult(
            String readingId,
            long capturedAtEpochMillis,
            String layoutId,
            int layoutVersion,
            String layoutName,
            List<OmrQuestionResult> questionResults
    ) {
        if (capturedAtEpochMillis <= 0L) {
            throw new IllegalArgumentException(
                    "capturedAtEpochMillis deve ser positivo."
            );
        }

        if (layoutVersion <= 0) {
            throw new IllegalArgumentException(
                    "layoutVersion deve iniciar em 1."
            );
        }

        if (questionResults == null
                || questionResults.isEmpty()) {

            throw new IllegalArgumentException(
                    "A leitura deve possuir pelo menos uma questao."
            );
        }

        List<OmrQuestionResult> resultsCopy =
                copyAndValidateQuestions(
                        questionResults
                );

        this.readingId =
                requireText("readingId", readingId);

        this.capturedAtEpochMillis =
                capturedAtEpochMillis;

        this.layoutId =
                requireText("layoutId", layoutId);

        this.layoutVersion = layoutVersion;

        this.layoutName =
                requireText("layoutName", layoutName);

        this.questionResults =
                Collections.unmodifiableList(
                        resultsCopy
                );

        this.countByStatus =
                createStatusCounts(
                        resultsCopy
                );
    }

    /**
     * Cria uma nova leitura com UUID e instante atuais.
     */
    public static OmrReadingResult create(
            String layoutId,
            int layoutVersion,
            String layoutName,
            List<OmrQuestionResult> questionResults
    ) {
        return new OmrReadingResult(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                layoutId,
                layoutVersion,
                layoutName,
                questionResults
        );
    }

    private static List<OmrQuestionResult>
    copyAndValidateQuestions(
            List<OmrQuestionResult> questionResults
    ) {
        List<OmrQuestionResult> copy =
                new ArrayList<>(
                        questionResults.size()
                );

        Set<String> questionIds =
                new HashSet<>();

        for (int index = 0;
             index < questionResults.size();
             index++) {

            OmrQuestionResult questionResult =
                    questionResults.get(index);

            if (questionResult == null) {
                throw new IllegalArgumentException(
                        "A lista nao pode conter questoes nulas."
                );
            }

            int expectedPosition = index + 1;

            if (questionResult.getPosition()
                    != expectedPosition) {

                throw new IllegalArgumentException(
                        "A posicao da questao deve acompanhar"
                                + " a ordem da lista: esperado="
                                + expectedPosition
                                + ", recebido="
                                + questionResult.getPosition()
                                + "."
                );
            }

            if (!questionIds.add(
                    questionResult.getQuestionId()
            )) {
                throw new IllegalArgumentException(
                        "Questao repetida na leitura: "
                                + questionResult
                                .getQuestionId()
                );
            }

            copy.add(questionResult);
        }

        return copy;
    }

    private static Map<OmrQuestionResult.Status, Integer>
    createStatusCounts(
            List<OmrQuestionResult> questionResults
    ) {
        EnumMap<OmrQuestionResult.Status, Integer> counts =
                new EnumMap<>(
                        OmrQuestionResult.Status.class
                );

        for (OmrQuestionResult.Status status
                : OmrQuestionResult.Status.values()) {

            counts.put(status, 0);
        }

        for (OmrQuestionResult result
                : questionResults) {

            OmrQuestionResult.Status status =
                    result.getStatus();

            counts.put(
                    status,
                    counts.get(status) + 1
            );
        }

        return Collections.unmodifiableMap(counts);
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    fieldName + " nao pode ser vazio."
            );
        }

        return value.trim();
    }

    public String getReadingId() {
        return readingId;
    }

    public long getCapturedAtEpochMillis() {
        return capturedAtEpochMillis;
    }

    public String getLayoutId() {
        return layoutId;
    }

    public int getLayoutVersion() {
        return layoutVersion;
    }

    public String getLayoutName() {
        return layoutName;
    }

    public List<OmrQuestionResult>
    getQuestionResults() {
        return questionResults;
    }

    public OmrQuestionResult getQuestionAtPosition(
            int position
    ) {
        if (position <= 0
                || position > questionResults.size()) {

            return null;
        }

        return questionResults.get(position - 1);
    }

    public OmrQuestionResult findByQuestionId(
            String questionId
    ) {
        if (questionId == null) {
            return null;
        }

        for (OmrQuestionResult questionResult
                : questionResults) {

            if (questionResult
                    .getQuestionId()
                    .equals(questionId)) {

                return questionResult;
            }
        }

        return null;
    }

    public int getQuestionCount() {
        return questionResults.size();
    }

    public int getCount(
            OmrQuestionResult.Status status
    ) {
        if (status == null) {
            return 0;
        }

        Integer count = countByStatus.get(status);

        return count == null ? 0 : count;
    }

    public Map<OmrQuestionResult.Status, Integer>
    getCountByStatus() {
        return countByStatus;
    }

    public int getSingleMarkCount() {
        return getCount(
                OmrQuestionResult.Status.SINGLE_MARK
        );
    }

    public int getBlankCount() {
        return getCount(
                OmrQuestionResult.Status.BLANK
        );
    }

    public int getMultipleMarkCount() {
        return getCount(
                OmrQuestionResult.Status.MULTIPLE_MARKS
        );
    }

    public int getAmbiguousCount() {
        return getCount(
                OmrQuestionResult.Status.AMBIGUOUS
        );
    }

    public int getNotReadyCount() {
        return getCount(
                OmrQuestionResult.Status.NOT_READY
        );
    }

    public int getReviewRequiredCount() {
        return getMultipleMarkCount()
                + getAmbiguousCount();
    }

    public boolean isComplete() {
        return getNotReadyCount() == 0;
    }

    public boolean requiresReview() {
        return getReviewRequiredCount() > 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrReadingResult)) {
            return false;
        }

        OmrReadingResult that =
                (OmrReadingResult) other;

        return capturedAtEpochMillis
                == that.capturedAtEpochMillis
                && layoutVersion == that.layoutVersion
                && readingId.equals(that.readingId)
                && layoutId.equals(that.layoutId)
                && layoutName.equals(that.layoutName)
                && questionResults.equals(
                that.questionResults
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                readingId,
                capturedAtEpochMillis,
                layoutId,
                layoutVersion,
                layoutName,
                questionResults
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "reading=%s layout=%s@%d questions=%d"
                        + " single=%d blank=%d multiple=%d"
                        + " ambiguous=%d notReady=%d review=%d",
                readingId,
                layoutId,
                layoutVersion,
                getQuestionCount(),
                getSingleMarkCount(),
                getBlankCount(),
                getMultipleMarkCount(),
                getAmbiguousCount(),
                getNotReadyCount(),
                getReviewRequiredCount()
        );
    }
}
