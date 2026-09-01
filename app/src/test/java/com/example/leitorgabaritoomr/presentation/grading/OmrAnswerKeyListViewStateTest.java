package com.example.leitorgabaritoomr.presentation.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrAnswerKeyListViewStateTest {

    private static final double DELTA = 0.000001;

    @Test
    public void emptyListProducesEmptyStateWithoutActiveKey() {
        OmrAnswerKeyListViewState viewState =
                OmrAnswerKeyListViewState.from(
                        Collections
                                .<OmrAnswerKeyDefinition>emptyList(),
                        null
                );

        assertTrue(viewState.isEmpty());
        assertEquals(0, viewState.getAnswerKeyCount());
        assertTrue(viewState.getAnswerKeyItems().isEmpty());
        assertFalse(viewState.hasActiveAnswerKey());
        assertNull(viewState.getActiveAnswerKeyItemOrNull());
    }

    @Test
    public void fieldsAndRepositoryOrderArePreserved() {
        OmrAnswerKeyDefinition recent = createAnswerKey(
                "recent",
                3,
                "Avaliação mais recente",
                "layout-recent",
                2,
                1.5
        );

        OmrAnswerKeyDefinition older = createAnswerKey(
                "older",
                1,
                "Avaliação anterior",
                "layout-older",
                4,
                2.0
        );

        OmrAnswerKeyListViewState viewState =
                OmrAnswerKeyListViewState.from(
                        Arrays.asList(recent, older),
                        older
                );

        assertFalse(viewState.isEmpty());
        assertEquals(2, viewState.getAnswerKeyCount());

        OmrAnswerKeyListViewState.AnswerKeyItem first =
                viewState.getAnswerKeyItems().get(0);

        assertEquals("recent", first.getAnswerKeyId());
        assertEquals(3, first.getAnswerKeyVersion());
        assertEquals(
                "Avaliação mais recente",
                first.getAnswerKeyName()
        );
        assertEquals("layout-recent", first.getLayoutId());
        assertEquals(2, first.getLayoutVersion());
        assertEquals(2, first.getQuestionCount());
        assertEquals(4.0, first.getTotalWeight(), DELTA);
        assertFalse(first.isActive());

        OmrAnswerKeyListViewState.AnswerKeyItem second =
                viewState.getAnswerKeyItems().get(1);

        assertEquals("older", second.getAnswerKeyId());
        assertTrue(second.isActive());
        assertTrue(viewState.hasActiveAnswerKey());
        assertEquals(
                second,
                viewState.getActiveAnswerKeyItemOrNull()
        );
    }

    @Test
    public void activeKeyIsRelatedByIdentityNotObjectReference() {
        OmrAnswerKeyDefinition stored = createAnswerKey(
                "same-identity",
                5,
                "Conteúdo armazenado",
                "layout",
                1,
                1.0
        );

        OmrAnswerKeyDefinition separatelyDecodedActive =
                createAnswerKey(
                        "same-identity",
                        5,
                        "Outro objeto equivalente",
                        "another-layout",
                        7,
                        3.0
                );

        OmrAnswerKeyListViewState viewState =
                OmrAnswerKeyListViewState.from(
                        Collections.singletonList(stored),
                        separatelyDecodedActive
                );

        OmrAnswerKeyListViewState.AnswerKeyItem item =
                viewState.getActiveAnswerKeyItemOrNull();

        assertNotNull(item);
        assertTrue(item.isActive());

        assertEquals(
                stored.getName(),
                item.getAnswerKeyName()
        );
        assertEquals(stored.getLayoutId(), item.getLayoutId());
    }

    @Test
    public void listCanExistWithoutActiveSelection() {
        OmrAnswerKeyListViewState viewState =
                OmrAnswerKeyListViewState.from(
                        Collections.singletonList(
                                createAnswerKey(
                                        "not-active",
                                        1,
                                        "Sem seleção",
                                        "layout",
                                        1,
                                        1.0
                                )
                        ),
                        null
                );

        assertEquals(1, viewState.getAnswerKeyCount());
        assertFalse(viewState.hasActiveAnswerKey());
        assertNull(viewState.getActiveAnswerKeyItemOrNull());
        assertFalse(
                viewState.getAnswerKeyItems()
                        .get(0)
                        .isActive()
        );
    }

    @Test
    public void differentVersionsOfSameIdRemainDistinct() {
        OmrAnswerKeyDefinition versionOne = createAnswerKey(
                "assessment",
                1,
                "Avaliação v1",
                "layout",
                1,
                1.0
        );

        OmrAnswerKeyDefinition versionTwo = createAnswerKey(
                "assessment",
                2,
                "Avaliação v2",
                "layout",
                1,
                2.0
        );

        OmrAnswerKeyListViewState viewState =
                OmrAnswerKeyListViewState.from(
                        Arrays.asList(
                                versionTwo,
                                versionOne
                        ),
                        versionOne
                );

        assertEquals(2, viewState.getAnswerKeyCount());
        assertFalse(
                viewState.findItemOrNull(
                        "assessment",
                        2
                ).isActive()
        );
        assertTrue(
                viewState.findItemOrNull(
                        "assessment",
                        1
                ).isActive()
        );
    }

    @Test
    public void itemLookupIsSafeAndNormalizesSurroundingSpaces() {
        OmrAnswerKeyListViewState viewState =
                OmrAnswerKeyListViewState.from(
                        Collections.singletonList(
                                createAnswerKey(
                                        "find-me",
                                        6,
                                        "Localizável",
                                        "layout",
                                        1,
                                        1.0
                                )
                        ),
                        null
                );

        OmrAnswerKeyListViewState.AnswerKeyItem found =
                viewState.findItemOrNull(
                        "  find-me  ",
                        6
                );

        assertNotNull(found);
        assertTrue(found.hasIdentity(" find-me ", 6));
        assertFalse(found.hasIdentity("find-me", 5));
        assertFalse(found.hasIdentity(null, 6));

        assertNull(viewState.findItemOrNull(null, 6));
        assertNull(viewState.findItemOrNull("missing", 6));
        assertNull(viewState.findItemOrNull("find-me", 5));
    }

    @Test
    public void sourceListChangesDoNotAffectViewState() {
        List<OmrAnswerKeyDefinition> source =
                new ArrayList<>();

        source.add(
                createAnswerKey(
                        "copied",
                        1,
                        "Lista copiada",
                        "layout",
                        1,
                        1.0
                )
        );

        OmrAnswerKeyListViewState viewState =
                OmrAnswerKeyListViewState.from(
                        source,
                        null
                );

        source.clear();

        assertEquals(1, viewState.getAnswerKeyCount());
        assertNotNull(viewState.findItemOrNull("copied", 1));
    }

    @Test
    public void exposedItemListIsImmutable() {
        OmrAnswerKeyListViewState viewState =
                OmrAnswerKeyListViewState.from(
                        Collections.singletonList(
                                createAnswerKey(
                                        "immutable",
                                        1,
                                        "Imutável",
                                        "layout",
                                        1,
                                        1.0
                                )
                        ),
                        null
                );

        try {
            viewState.getAnswerKeyItems().clear();
            fail("Era esperada UnsupportedOperationException.");

        } catch (UnsupportedOperationException expected) {
            // Comportamento esperado.
        }

        assertEquals(1, viewState.getAnswerKeyCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullListIsRejected() {
        OmrAnswerKeyListViewState.from(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullItemIsRejected() {
        OmrAnswerKeyListViewState.from(
                Collections.singletonList(null),
                null
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdentityIsRejected() {
        OmrAnswerKeyDefinition first = createAnswerKey(
                "duplicate",
                2,
                "Primeiro conteúdo",
                "layout",
                1,
                1.0
        );

        OmrAnswerKeyDefinition duplicate = createAnswerKey(
                "duplicate",
                2,
                "Segundo conteúdo",
                "other-layout",
                3,
                4.0
        );

        OmrAnswerKeyListViewState.from(
                Arrays.asList(first, duplicate),
                null
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void activeKeyOutsideListIsRejected() {
        OmrAnswerKeyDefinition stored = createAnswerKey(
                "stored",
                1,
                "Armazenado",
                "layout",
                1,
                1.0
        );

        OmrAnswerKeyDefinition outside = createAnswerKey(
                "outside",
                1,
                "Fora da lista",
                "layout",
                1,
                1.0
        );

        OmrAnswerKeyListViewState.from(
                Collections.singletonList(stored),
                outside
        );
    }

    private static OmrAnswerKeyDefinition createAnswerKey(
            String id,
            int version,
            String name,
            String layoutId,
            int layoutVersion,
            double firstWeight
    ) {
        OmrAnswerKeyEntry first =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "Q01-A",
                        firstWeight
                );

        OmrAnswerKeyEntry second =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q02",
                        "Q02-B",
                        2.5
                );

        return new OmrAnswerKeyDefinition(
                id,
                version,
                name,
                layoutId,
                layoutVersion,
                Arrays.asList(first, second)
        );
    }
}
