package com.example.leitorgabaritoomr.infrastructure.grading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class OmrSharedPreferencesAnswerKeyRepositoryInstrumentedTest {

    private static final double DELTA = 0.000001;

    private static final String TEST_REPOSITORY_PREFERENCES =
            "omr_answer_key_repository_instrumented_test";

    private static final String TEST_LEGACY_PREFERENCES =
            "omr_answer_key_legacy_instrumented_test";

    private SharedPreferences repositoryPreferences;
    private SharedPreferences legacyPreferences;
    private Context isolatedContext;

    private OmrSharedPreferencesAnswerKeyRepository repository;

    @Before
    public void setUp() {
        Context applicationContext =
                ApplicationProvider.getApplicationContext();

        repositoryPreferences =
                applicationContext.getSharedPreferences(
                        TEST_REPOSITORY_PREFERENCES,
                        Context.MODE_PRIVATE
                );

        legacyPreferences =
                applicationContext.getSharedPreferences(
                        TEST_LEGACY_PREFERENCES,
                        Context.MODE_PRIVATE
                );

        clearTestPreferences();

        isolatedContext = new ContextWrapper(
                applicationContext
        ) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(
                    String name,
                    int mode
            ) {
                if (OmrSharedPreferencesAnswerKeyRepository
                        .PREFERENCES_NAME.equals(name)) {
                    return repositoryPreferences;
                }

                if (OmrSharedPreferencesActiveAnswerKeyStore
                        .PREFERENCES_NAME.equals(name)) {
                    return legacyPreferences;
                }

                return super.getSharedPreferences(name, mode);
            }
        };

        repository =
                new OmrSharedPreferencesAnswerKeyRepository(
                        isolatedContext
                );
    }

    @After
    public void tearDown() {
        clearTestPreferences();
    }

    @Test
    public void emptyRepositoryReturnsEmptyListAndNoActiveKey() {
        assertTrue(repository.loadAll().isEmpty());
        assertNull(repository.loadActiveOrNull());
    }

    @Test
    public void saveOrdersMostRecentFirstWithoutSelectingIt() {
        OmrAnswerKeyDefinition first = createAnswerKey(
                "first",
                1,
                "Primeiro",
                1.0
        );

        OmrAnswerKeyDefinition second = createAnswerKey(
                "second",
                1,
                "Segundo",
                2.0
        );

        repository.save(first);
        repository.save(second);

        List<OmrAnswerKeyDefinition> stored =
                repository.loadAll();

        assertEquals(2, stored.size());
        assertCompleteAnswerKey(second, stored.get(0));
        assertCompleteAnswerKey(first, stored.get(1));
        assertNull(repository.loadActiveOrNull());
    }

    @Test
    public void saveActiveAddsAndSelectsAnswerKeyAtomically() {
        OmrAnswerKeyDefinition answerKey = createAnswerKey(
                "active",
                2,
                "Gabarito ativo",
                1.5
        );

        repository.saveActive(answerKey);

        List<OmrAnswerKeyDefinition> stored =
                repository.loadAll();

        assertEquals(1, stored.size());
        assertCompleteAnswerKey(answerKey, stored.get(0));
        assertCompleteAnswerKey(
                answerKey,
                repository.loadActiveOrNull()
        );
    }

    @Test
    public void newInstanceReadsCollectionAndActiveSelection() {
        OmrAnswerKeyDefinition first = createAnswerKey(
                "persisted-first",
                1,
                "Persistido 1",
                1.0
        );

        OmrAnswerKeyDefinition second = createAnswerKey(
                "persisted-second",
                4,
                "Persistido 2",
                2.0
        );

        repository.saveActive(first);
        repository.save(second);

        OmrSharedPreferencesAnswerKeyRepository restored =
                new OmrSharedPreferencesAnswerKeyRepository(
                        isolatedContext
                );

        List<OmrAnswerKeyDefinition> stored =
                restored.loadAll();

        assertEquals(2, stored.size());
        assertCompleteAnswerKey(second, stored.get(0));
        assertCompleteAnswerKey(first, stored.get(1));
        assertCompleteAnswerKey(
                first,
                restored.loadActiveOrNull()
        );
    }

    @Test
    public void sameIdentityIsReplacedMovedFirstAndActiveUpdated() {
        OmrAnswerKeyDefinition original = createAnswerKey(
                "replaceable",
                3,
                "Versão original",
                1.0
        );

        OmrAnswerKeyDefinition another = createAnswerKey(
                "another",
                1,
                "Outro gabarito",
                2.0
        );

        OmrAnswerKeyDefinition replacement = createAnswerKey(
                "replaceable",
                3,
                "Conteúdo atualizado",
                4.0
        );

        repository.saveActive(original);
        repository.save(another);
        repository.save(replacement);

        List<OmrAnswerKeyDefinition> stored =
                repository.loadAll();

        assertEquals(2, stored.size());
        assertCompleteAnswerKey(replacement, stored.get(0));
        assertCompleteAnswerKey(another, stored.get(1));
        assertCompleteAnswerKey(
                replacement,
                repository.loadActiveOrNull()
        );
    }

    @Test
    public void differentVersionsOfSameIdCoexist() {
        OmrAnswerKeyDefinition versionOne = createAnswerKey(
                "same-id",
                1,
                "Versão 1",
                1.0
        );

        OmrAnswerKeyDefinition versionTwo = createAnswerKey(
                "same-id",
                2,
                "Versão 2",
                2.0
        );

        repository.save(versionOne);
        repository.save(versionTwo);

        assertEquals(2, repository.loadAll().size());
        assertCompleteAnswerKey(
                versionOne,
                repository.findOrNull("same-id", 1)
        );
        assertCompleteAnswerKey(
                versionTwo,
                repository.findOrNull("same-id", 2)
        );
    }

    @Test
    public void selectActiveChangesSelectionWithoutChangingOrder() {
        OmrAnswerKeyDefinition first = createAnswerKey(
                "select-first",
                1,
                "Selecionável 1",
                1.0
        );

        OmrAnswerKeyDefinition second = createAnswerKey(
                "select-second",
                1,
                "Selecionável 2",
                2.0
        );

        repository.saveActive(first);
        repository.save(second);

        repository.selectActive(
                second.getId(),
                second.getVersion()
        );

        List<OmrAnswerKeyDefinition> stored =
                repository.loadAll();

        assertCompleteAnswerKey(second, stored.get(0));
        assertCompleteAnswerKey(first, stored.get(1));
        assertCompleteAnswerKey(
                second,
                repository.loadActiveOrNull()
        );
    }

    @Test
    public void selectingMissingOrInvalidIdentityIsRejected() {
        repository.save(
                createAnswerKey(
                        "existing",
                        1,
                        "Existente",
                        1.0
                )
        );

        assertSelectionRejected("missing", 1);
        assertSelectionRejected(null, 1);
        assertSelectionRejected("", 1);
        assertSelectionRejected("existing", 0);
    }

    @Test
    public void clearActiveIsIdempotentAndPreservesCollection() {
        OmrAnswerKeyDefinition answerKey = createAnswerKey(
                "clear-active",
                1,
                "Limpar seleção",
                1.0
        );

        repository.saveActive(answerKey);

        repository.clearActive();
        repository.clearActive();

        assertNull(repository.loadActiveOrNull());
        assertEquals(1, repository.loadAll().size());
        assertCompleteAnswerKey(
                answerKey,
                repository.loadAll().get(0)
        );
    }

    @Test
    public void deletingInactiveKeyPreservesActiveKey() {
        OmrAnswerKeyDefinition active = createAnswerKey(
                "delete-active-preserved",
                1,
                "Ativo preservado",
                1.0
        );

        OmrAnswerKeyDefinition inactive = createAnswerKey(
                "delete-inactive",
                1,
                "Inativo removido",
                2.0
        );

        repository.saveActive(active);
        repository.save(inactive);

        assertTrue(repository.delete(
                inactive.getId(),
                inactive.getVersion()
        ));

        assertFalse(repository.delete(
                inactive.getId(),
                inactive.getVersion()
        ));

        assertEquals(1, repository.loadAll().size());
        assertCompleteAnswerKey(
                active,
                repository.loadActiveOrNull()
        );
    }

    @Test
    public void deletingActiveKeyClearsOnlySelection() {
        OmrAnswerKeyDefinition inactive = createAnswerKey(
                "remaining",
                1,
                "Gabarito restante",
                1.0
        );

        OmrAnswerKeyDefinition active = createAnswerKey(
                "deleted-active",
                2,
                "Gabarito ativo removido",
                2.0
        );

        repository.save(inactive);
        repository.saveActive(active);

        assertTrue(repository.delete(
                active.getId(),
                active.getVersion()
        ));

        assertNull(repository.loadActiveOrNull());
        assertEquals(1, repository.loadAll().size());
        assertCompleteAnswerKey(
                inactive,
                repository.loadAll().get(0)
        );
    }

    @Test
    public void returnedCollectionIsImmutable() {
        repository.save(
                createAnswerKey(
                        "immutable",
                        1,
                        "Coleção imutável",
                        1.0
                )
        );

        try {
            repository.loadAll().clear();
            fail("A coleção não deveria aceitar alterações.");

        } catch (UnsupportedOperationException expected) {
            // Comportamento esperado.
        }

        assertEquals(1, repository.loadAll().size());
    }

    @Test
    public void findReturnsNullForMissingOrInvalidIdentity() {
        repository.save(
                createAnswerKey(
                        "findable",
                        1,
                        "Localizável",
                        1.0
                )
        );

        assertNull(repository.findOrNull("missing", 1));
        assertNull(repository.findOrNull(null, 1));
        assertNull(repository.findOrNull("", 1));
        assertNull(repository.findOrNull("findable", 0));
    }

    @Test
    public void legacyActiveKeyIsMigratedOnlyOnce() {
        OmrAnswerKeyDefinition legacy = createAnswerKey(
                "legacy",
                1,
                "Gabarito anterior",
                1.0
        );

        OmrAnswerKeyDefinition laterLegacyValue = createAnswerKey(
                "legacy-later",
                1,
                "Valor antigo posterior",
                2.0
        );

        OmrSharedPreferencesActiveAnswerKeyStore legacyStore =
                new OmrSharedPreferencesActiveAnswerKeyStore(
                        isolatedContext
                );

        legacyStore.saveActive(legacy);

        assertEquals(1, repository.loadAll().size());
        assertCompleteAnswerKey(
                legacy,
                repository.loadActiveOrNull()
        );

        legacyStore.saveActive(laterLegacyValue);

        OmrSharedPreferencesAnswerKeyRepository restored =
                new OmrSharedPreferencesAnswerKeyRepository(
                        isolatedContext
                );

        assertCompleteAnswerKey(
                legacy,
                restored.loadActiveOrNull()
        );
        assertNull(restored.findOrNull("legacy-later", 1));
    }

    @Test
    public void missingChecksumIsIgnored() {
        assertTrue(
                repositoryPreferences.edit()
                        .putString(
                                OmrSharedPreferencesAnswerKeyRepository
                                        .KEY_PAYLOAD,
                                "QUJD"
                        )
                        .commit()
        );

        assertTrue(repository.loadAll().isEmpty());
        assertNull(repository.loadActiveOrNull());
    }

    @Test
    public void alteredChecksumIsIgnoredWithoutRevivingLegacyKey() {
        OmrAnswerKeyDefinition current = createAnswerKey(
                "current",
                1,
                "Gabarito atual",
                1.0
        );

        OmrAnswerKeyDefinition legacy = createAnswerKey(
                "stale-legacy",
                1,
                "Gabarito antigo",
                2.0
        );

        repository.saveActive(current);

        new OmrSharedPreferencesActiveAnswerKeyStore(
                isolatedContext
        ).saveActive(legacy);

        assertTrue(
                repositoryPreferences.edit()
                        .putString(
                                OmrSharedPreferencesAnswerKeyRepository
                                        .KEY_CHECKSUM,
                                zeroChecksum()
                        )
                        .commit()
        );

        assertTrue(repository.loadAll().isEmpty());
        assertNull(repository.loadActiveOrNull());
    }

    @Test
    public void invalidBase64IsIgnored() {
        assertTrue(
                repositoryPreferences.edit()
                        .putString(
                                OmrSharedPreferencesAnswerKeyRepository
                                        .KEY_PAYLOAD,
                                "%%%conteudo-invalido%%%"
                        )
                        .putString(
                                OmrSharedPreferencesAnswerKeyRepository
                                        .KEY_CHECKSUM,
                                zeroChecksum()
                        )
                        .commit()
        );

        assertTrue(repository.loadAll().isEmpty());
        assertNull(repository.loadActiveOrNull());
    }

    @Test
    public void valueWithWrongPreferenceTypeIsIgnored() {
        assertTrue(
                repositoryPreferences.edit()
                        .putInt(
                                OmrSharedPreferencesAnswerKeyRepository
                                        .KEY_PAYLOAD,
                                7
                        )
                        .putString(
                                OmrSharedPreferencesAnswerKeyRepository
                                        .KEY_CHECKSUM,
                                zeroChecksum()
                        )
                        .commit()
        );

        assertTrue(repository.loadAll().isEmpty());
        assertNull(repository.loadActiveOrNull());
    }

    @Test
    public void nullAnswerKeyIsRejected() {
        try {
            repository.save(null);
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Comportamento esperado.
        }

        try {
            repository.saveActive(null);
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Comportamento esperado.
        }
    }

    private void assertSelectionRejected(
            String answerKeyId,
            int answerKeyVersion
    ) {
        try {
            repository.selectActive(
                    answerKeyId,
                    answerKeyVersion
            );

            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Comportamento esperado.
        }
    }

    private void clearTestPreferences() {
        assertTrue(
                repositoryPreferences.edit()
                        .clear()
                        .commit()
        );

        assertTrue(
                legacyPreferences.edit()
                        .clear()
                        .commit()
        );
    }

    private static OmrAnswerKeyDefinition createAnswerKey(
            String id,
            int version,
            String name,
            double firstWeight
    ) {
        OmrAnswerKeyEntry first =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "Q01-A",
                        firstWeight
                );

        OmrAnswerKeyEntry second =
                new OmrAnswerKeyEntry(
                        "Q02",
                        Arrays.asList(
                                "Q02-B",
                                "Q02-D"
                        ),
                        2.5
                );

        return new OmrAnswerKeyDefinition(
                id,
                version,
                name,
                "avalie-ce-development",
                1,
                Arrays.asList(first, second)
        );
    }

    private static void assertCompleteAnswerKey(
            OmrAnswerKeyDefinition expected,
            OmrAnswerKeyDefinition actual
    ) {
        assertNotNull(actual);

        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getVersion(), actual.getVersion());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getLayoutId(), actual.getLayoutId());
        assertEquals(
                expected.getLayoutVersion(),
                actual.getLayoutVersion()
        );
        assertEquals(
                expected.getQuestionCount(),
                actual.getQuestionCount()
        );
        assertEquals(
                expected.getTotalWeight(),
                actual.getTotalWeight(),
                DELTA
        );

        for (int index = 0;
             index < expected.getQuestionCount();
             index++) {

            OmrAnswerKeyEntry expectedEntry =
                    expected.getEntries().get(index);

            OmrAnswerKeyEntry actualEntry =
                    actual.getEntries().get(index);

            assertEquals(
                    expectedEntry.getQuestionId(),
                    actualEntry.getQuestionId()
            );
            assertEquals(
                    expectedEntry.getAcceptedOptionIds(),
                    actualEntry.getAcceptedOptionIds()
            );
            assertEquals(
                    expectedEntry.getWeight(),
                    actualEntry.getWeight(),
                    DELTA
            );
        }
    }

    private static String zeroChecksum() {
        char[] characters = new char[64];
        Arrays.fill(characters, '0');
        return new String(characters);
    }
}
