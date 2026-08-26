package com.example.leitorgabaritoomr.domain.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class OmrAnswerKeyDefinitionTest {

    private static final double DELTA = 0.000001;

    @Test
    public void entryAcceptsEveryConfiguredOption() {
        OmrAnswerKeyEntry entry = new OmrAnswerKeyEntry(
                " Q15 ",
                Arrays.asList(" B ", "D"),
                2.5
        );

        assertEquals("Q15", entry.getQuestionId());
        assertEquals(2, entry.getAcceptedOptionCount());
        assertEquals(2.5, entry.getWeight(), DELTA);

        assertTrue(entry.acceptsOption("B"));
        assertTrue(entry.acceptsOption(" D "));
        assertFalse(entry.acceptsOption("A"));
        assertFalse(entry.acceptsOption(null));
    }

    @Test
    public void singleAnswerCreatesConventionalEntry() {
        OmrAnswerKeyEntry entry =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "C",
                        1.0
                );

        assertEquals("Q01", entry.getQuestionId());
        assertEquals(1, entry.getAcceptedOptionCount());
        assertTrue(entry.acceptsOption("C"));
        assertFalse(entry.acceptsOption("D"));
    }

    @Test
    public void entryRejectsRepeatedAcceptedOption() {
        expectIllegalArgument(() ->
                new OmrAnswerKeyEntry(
                        "Q01",
                        Arrays.asList("A", " A "),
                        1.0
                )
        );
    }

    @Test
    public void entryAcceptedOptionsAreImmutable() {
        OmrAnswerKeyEntry entry =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "A",
                        1.0
                );

        expectUnsupportedOperation(() ->
                entry.getAcceptedOptionIds().add("B")
        );
    }

    @Test
    public void definitionPreservesMetadataAndCalculatesTotalWeight() {
        OmrAnswerKeyEntry first =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "A",
                        1.0
                );

        OmrAnswerKeyEntry second =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q02",
                        "D",
                        2.5
                );

        OmrAnswerKeyDefinition definition =
                new OmrAnswerKeyDefinition(
                        "official-2026",
                        3,
                        "Gabarito oficial",
                        "layout-52",
                        2,
                        Arrays.asList(first, second)
                );

        assertEquals("official-2026", definition.getId());
        assertEquals(3, definition.getVersion());
        assertEquals("Gabarito oficial", definition.getName());
        assertEquals("layout-52", definition.getLayoutId());
        assertEquals(2, definition.getLayoutVersion());
        assertEquals(2, definition.getQuestionCount());
        assertEquals(3.5, definition.getTotalWeight(), DELTA);

        assertSame(first, definition.getEntries().get(0));
        assertSame(second, definition.getEntries().get(1));
    }

    @Test
    public void definitionFindsQuestionByNormalizedId() {
        OmrAnswerKeyEntry entry =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q27",
                        "B",
                        1.0
                );

        OmrAnswerKeyDefinition definition =
                definitionWith(entry);

        assertTrue(definition.containsQuestion(" Q27 "));
        assertFalse(definition.containsQuestion("Q28"));

        assertSame(
                entry,
                definition.findEntryByQuestionId(" Q27 ")
        );

        assertNull(
                definition.findEntryByQuestionId(null)
        );
    }

    @Test
    public void definitionRejectsRepeatedQuestionId() {
        OmrAnswerKeyEntry first =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "A",
                        1.0
                );

        OmrAnswerKeyEntry repeated =
                OmrAnswerKeyEntry.singleAnswer(
                        " Q01 ",
                        "B",
                        1.0
                );

        expectIllegalArgument(() ->
                new OmrAnswerKeyDefinition(
                        "answer-key",
                        1,
                        "Gabarito",
                        "layout",
                        1,
                        Arrays.asList(first, repeated)
                )
        );
    }

    @Test
    public void definitionEntriesAreImmutable() {
        OmrAnswerKeyDefinition definition =
                definitionWith(
                        OmrAnswerKeyEntry.singleAnswer(
                                "Q01",
                                "A",
                                1.0
                        )
                );

        expectUnsupportedOperation(() ->
                definition.getEntries().clear()
        );
    }

    @Test
    public void definitionRejectsInvalidVersions() {
        OmrAnswerKeyEntry entry =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "A",
                        1.0
                );

        expectIllegalArgument(() ->
                new OmrAnswerKeyDefinition(
                        "answer-key",
                        0,
                        "Gabarito",
                        "layout",
                        1,
                        Collections.singletonList(entry)
                )
        );

        expectIllegalArgument(() ->
                new OmrAnswerKeyDefinition(
                        "answer-key",
                        1,
                        "Gabarito",
                        "layout",
                        0,
                        Collections.singletonList(entry)
                )
        );
    }

    @Test
    public void definitionIdentityUsesOnlyIdAndVersion() {
        OmrAnswerKeyEntry entry =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "A",
                        1.0
                );

        OmrAnswerKeyDefinition first =
                new OmrAnswerKeyDefinition(
                        "answer-key",
                        1,
                        "Primeiro nome",
                        "layout-a",
                        1,
                        Collections.singletonList(entry)
                );

        OmrAnswerKeyDefinition sameIdentity =
                new OmrAnswerKeyDefinition(
                        "answer-key",
                        1,
                        "Outro nome",
                        "layout-b",
                        2,
                        Collections.singletonList(entry)
                );

        OmrAnswerKeyDefinition anotherVersion =
                new OmrAnswerKeyDefinition(
                        "answer-key",
                        2,
                        "Primeiro nome",
                        "layout-a",
                        1,
                        Collections.singletonList(entry)
                );

        assertEquals(first, sameIdentity);
        assertEquals(first.hashCode(), sameIdentity.hashCode());
        assertNotEquals(first, anotherVersion);
    }

    private OmrAnswerKeyDefinition definitionWith(
            OmrAnswerKeyEntry entry
    ) {
        return new OmrAnswerKeyDefinition(
                "answer-key",
                1,
                "Gabarito",
                "layout",
                1,
                Collections.singletonList(entry)
        );
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private void expectUnsupportedOperation(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");

        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }
}
