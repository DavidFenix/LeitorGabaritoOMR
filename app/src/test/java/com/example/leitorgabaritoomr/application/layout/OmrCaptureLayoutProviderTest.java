package com.example.leitorgabaritoomr.application.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyDefinitionFactory;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OmrCaptureLayoutProviderTest {

    private final OmrCaptureLayoutProvider provider =
            new OmrCaptureLayoutProvider();

    @Test
    public void captureWithoutAnswerKeyKeepsLegacyLayout() {
        OmrLayoutDefinition layout =
                provider.resolve(null);

        assertEquals("avalie-ce-development", layout.getId());
        assertEquals(1, layout.getVersion());
        assertEquals(52, layout.getQuestionCount());
        assertEquals(208, layout.getOptionCount());
    }

    @Test
    public void captureUsesEveryCompactAnswerKeyFromOneToTen() {
        for (int questionCount = 1;
             questionCount <= 10;
             questionCount++) {

            OmrAnswerKeyDefinition answerKey =
                    createCompactAnswerKey(questionCount);

            OmrLayoutDefinition layout =
                    provider.resolve(answerKey);

            assertEquals(
                    answerKey.getLayoutId(),
                    layout.getId()
            );
            assertEquals(
                    answerKey.getLayoutVersion(),
                    layout.getVersion()
            );
            assertEquals(
                    answerKey.getQuestionCount(),
                    layout.getQuestionCount()
            );
            assertEquals(
                    questionCount * 4,
                    layout.getOptionCount()
            );
        }
    }

    @Test
    public void captureRejectsUnknownAnswerKeyLayout() {
        OmrAnswerKeyDefinition incompatible =
                new OmrAnswerKeyDefinition(
                        "incompatible-answer-key",
                        1,
                        "Gabarito incompatível",
                        "layout-inexistente",
                        1,
                        Collections.singletonList(
                                new OmrAnswerKeyEntry(
                                        "question-001",
                                        Collections.singleton(
                                                "question-001-option-01"
                                        ),
                                        1.0
                                )
                        )
                );

        expectIllegalArgument(() ->
                provider.resolve(incompatible)
        );
    }

    @Test
    public void captureRejectsIdentityThatDisagreesWithCount() {
        OmrAnswerKeyDefinition inconsistent =
                new OmrAnswerKeyDefinition(
                        "inconsistent-answer-key",
                        1,
                        "Gabarito inconsistente",
                        "omr-compact-ad-q010",
                        1,
                        Collections.singletonList(
                                new OmrAnswerKeyEntry(
                                        "question-001",
                                        Collections.singleton(
                                                "question-001-option-01"
                                        ),
                                        1.0
                                )
                        )
                );

        expectIllegalArgument(() ->
                provider.resolve(inconsistent)
        );
    }

    private OmrAnswerKeyDefinition createCompactAnswerKey(
            int questionCount
    ) {
        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(
                        OmrSheetTemplateCatalog
                                .compactFourOptions(
                                        questionCount
                                )
                );

        return new OmrAnswerKeyDefinitionFactory()
                .createSingleAnswerKey(
                        "answer-key-" + questionCount,
                        1,
                        "Gabarito " + questionCount,
                        layout,
                        answerLabels(questionCount),
                        1.0
                );
    }

    private List<String> answerLabels(
            int questionCount
    ) {
        List<String> labels =
                new ArrayList<>(questionCount);

        String[] availableLabels = {
                "A",
                "B",
                "C",
                "D"
        };

        for (int index = 0;
             index < questionCount;
             index++) {

            labels.add(
                    availableLabels[index % availableLabels.length]
            );
        }

        return labels;
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
