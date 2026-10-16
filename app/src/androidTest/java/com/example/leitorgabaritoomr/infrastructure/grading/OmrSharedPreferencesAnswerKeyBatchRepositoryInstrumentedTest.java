package com.example.leitorgabaritoomr.infrastructure.grading;

import static org.junit.Assert.assertEquals;
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
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class
OmrSharedPreferencesAnswerKeyBatchRepositoryInstrumentedTest {

    private static final String REPOSITORY_PREFERENCES =
            "omr_answer_key_batch_repository_test";
    private static final String LEGACY_PREFERENCES =
            "omr_answer_key_batch_legacy_test";

    private SharedPreferences repositoryPreferences;
    private SharedPreferences legacyPreferences;
    private Context isolatedContext;
    private OmrSharedPreferencesAnswerKeyRepository repository;

    @Before
    public void setUp() {
        Context application = ApplicationProvider.getApplicationContext();
        repositoryPreferences = application.getSharedPreferences(
                REPOSITORY_PREFERENCES, Context.MODE_PRIVATE
        );
        legacyPreferences = application.getSharedPreferences(
                LEGACY_PREFERENCES, Context.MODE_PRIVATE
        );
        clearPreferences();

        isolatedContext = new ContextWrapper(application) {
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
        repository = new OmrSharedPreferencesAnswerKeyRepository(
                isolatedContext
        );
    }

    @After
    public void tearDown() {
        clearPreferences();
    }

    @Test
    public void batchPersistsOnceAndPreservesActiveSelection() {
        OmrAnswerKeyDefinition active = key(
                "active", 1, "Ativo", 1.0
        );
        OmrAnswerKeyDefinition first = key(
                "csv-first", 1, "Primeiro", 1.0
        );
        OmrAnswerKeyDefinition second = key(
                "csv-second", 2, "Segundo", 2.0
        );
        repository.saveActive(active);

        assertEquals(2, repository.saveAllCompatible(
                Arrays.asList(first, second)
        ));
        assertEquals(3, repository.loadAll().size());
        assertEquals(first.getId(), repository.loadAll().get(0).getId());
        assertEquals(second.getId(), repository.loadAll().get(1).getId());
        assertEquals(active.getId(), repository.loadActiveOrNull().getId());

        OmrSharedPreferencesAnswerKeyRepository restored =
                new OmrSharedPreferencesAnswerKeyRepository(isolatedContext);
        assertNotNull(restored.findOrNull("csv-first", 1));
        assertEquals("active", restored.loadActiveOrNull().getId());
    }

    @Test
    public void identicalReimportIsIdempotent() {
        OmrAnswerKeyDefinition answerKey = key(
                "same", 1, "Mesmo", 1.0
        );
        assertEquals(1, repository.saveAllCompatible(
                Collections.singletonList(answerKey)
        ));
        assertEquals(0, repository.saveAllCompatible(
                Collections.singletonList(answerKey)
        ));
        assertEquals(1, repository.loadAll().size());
    }

    @Test
    public void conflictRejectsWholeBatchWithoutPartialWrite() {
        OmrAnswerKeyDefinition original = key(
                "existing", 1, "Original", 1.0
        );
        repository.saveActive(original);
        OmrAnswerKeyDefinition newKey = key(
                "would-be-new", 1, "Novo", 1.0
        );
        OmrAnswerKeyDefinition conflict = key(
                "existing", 1, "Alterado", 2.0
        );

        expectRejected(() -> repository.saveAllCompatible(
                Arrays.asList(newKey, conflict)
        ));

        assertNull(repository.findOrNull("would-be-new", 1));
        assertEquals(1, repository.loadAll().size());
        assertEquals("Original", repository.findOrNull(
                "existing", 1
        ).getName());
        assertEquals("existing", repository.loadActiveOrNull().getId());
    }

    @Test
    public void duplicateIdentityInsideBatchIsRejected() {
        OmrAnswerKeyDefinition first = key("duplicate", 1, "A", 1.0);
        OmrAnswerKeyDefinition second = key("duplicate", 1, "B", 2.0);
        expectRejected(() -> repository.saveAllCompatible(
                Arrays.asList(first, second)
        ));
        assertTrue(repository.loadAll().isEmpty());
    }

    private static OmrAnswerKeyDefinition key(
            String id,
            int version,
            String name,
            double weight
    ) {
        return new OmrAnswerKeyDefinition(
                id,
                version,
                name,
                "layout-test",
                1,
                Arrays.asList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "Q01", "Q01-A", weight
                        ),
                        OmrAnswerKeyEntry.singleAnswer(
                                "Q02", "Q02-B", 1.0
                        )
                )
        );
    }

    private static void expectRejected(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private void clearPreferences() {
        assertTrue(repositoryPreferences.edit().clear().commit());
        assertTrue(legacyPreferences.edit().clear().commit());
    }
}
