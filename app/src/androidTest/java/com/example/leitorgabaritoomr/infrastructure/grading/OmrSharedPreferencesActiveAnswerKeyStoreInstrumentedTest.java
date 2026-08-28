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

@RunWith(AndroidJUnit4.class)
public final class OmrSharedPreferencesActiveAnswerKeyStoreInstrumentedTest {

    private static final double DELTA = 0.000001;

    private static final String TEST_PREFERENCES_NAME =
            "omr_active_answer_key_instrumented_test";

    private SharedPreferences testPreferences;
    private Context isolatedContext;

    private OmrSharedPreferencesActiveAnswerKeyStore store;

    @Before
    public void setUp() {
        Context applicationContext =
                ApplicationProvider.getApplicationContext();

        testPreferences =
                applicationContext.getSharedPreferences(
                        TEST_PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );

        assertTrue(
                testPreferences.edit()
                        .clear()
                        .commit()
        );

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
                return testPreferences;
            }
        };

        store =
                new OmrSharedPreferencesActiveAnswerKeyStore(
                        isolatedContext
                );
    }

    @After
    public void tearDown() {
        assertTrue(
                testPreferences.edit()
                        .clear()
                        .commit()
        );
    }

    @Test
    public void emptyStoreReturnsNull() {
        assertNull(store.loadActiveOrNull());
    }

    @Test
    public void saveAndLoadPreserveCompleteAnswerKey() {
        OmrAnswerKeyDefinition original =
                createAnswerKey(
                        "answer-key-2026",
                        3,
                        "Gabarito oficial",
                        1.5
                );

        store.saveActive(original);

        OmrAnswerKeyDefinition restored =
                store.loadActiveOrNull();

        assertCompleteAnswerKey(original, restored);
    }

    @Test
    public void newStoreInstanceReadsPersistedAnswerKey() {
        OmrAnswerKeyDefinition original =
                createAnswerKey(
                        "answer-key-restart",
                        1,
                        "Gabarito após reinício",
                        1.0
                );

        store.saveActive(original);

        OmrSharedPreferencesActiveAnswerKeyStore
                newStoreInstance =
                new OmrSharedPreferencesActiveAnswerKeyStore(
                        isolatedContext
                );

        assertCompleteAnswerKey(
                original,
                newStoreInstance.loadActiveOrNull()
        );
    }

    @Test
    public void savingAgainReplacesPreviousAnswerKey() {
        OmrAnswerKeyDefinition first =
                createAnswerKey(
                        "first-answer-key",
                        1,
                        "Primeiro gabarito",
                        1.0
                );

        OmrAnswerKeyDefinition second =
                createAnswerKey(
                        "second-answer-key",
                        4,
                        "Segundo gabarito",
                        2.0
                );

        store.saveActive(first);
        store.saveActive(second);

        OmrAnswerKeyDefinition restored =
                store.loadActiveOrNull();

        assertCompleteAnswerKey(second, restored);
        assertEquals("second-answer-key", restored.getId());
        assertEquals(4, restored.getVersion());
    }

    @Test
    public void clearIsIdempotent() {
        store.saveActive(
                createAnswerKey(
                        "answer-key-to-clear",
                        1,
                        "Gabarito removível",
                        1.0
                )
        );

        store.clearActive();
        store.clearActive();

        assertNull(store.loadActiveOrNull());
    }

    @Test
    public void missingChecksumIsIgnored() {
        assertTrue(
                testPreferences.edit()
                        .putString(
                                OmrSharedPreferencesActiveAnswerKeyStore
                                        .KEY_PAYLOAD,
                                "QUJD"
                        )
                        .commit()
        );

        assertNull(store.loadActiveOrNull());
    }

    @Test
    public void alteredChecksumIsIgnored() {
        store.saveActive(
                createAnswerKey(
                        "checksum-answer-key",
                        1,
                        "Gabarito com checksum",
                        1.0
                )
        );

        assertTrue(
                testPreferences.edit()
                        .putString(
                                OmrSharedPreferencesActiveAnswerKeyStore
                                        .KEY_CHECKSUM,
                                zeroChecksum()
                        )
                        .commit()
        );

        assertNull(store.loadActiveOrNull());
    }

    @Test
    public void invalidBase64IsIgnored() {
        assertTrue(
                testPreferences.edit()
                        .putString(
                                OmrSharedPreferencesActiveAnswerKeyStore
                                        .KEY_PAYLOAD,
                                "%%%conteudo-invalido%%%"
                        )
                        .putString(
                                OmrSharedPreferencesActiveAnswerKeyStore
                                        .KEY_CHECKSUM,
                                zeroChecksum()
                        )
                        .commit()
        );

        assertNull(store.loadActiveOrNull());
    }

    @Test
    public void valueWithWrongPreferenceTypeIsIgnored() {
        assertTrue(
                testPreferences.edit()
                        .putInt(
                                OmrSharedPreferencesActiveAnswerKeyStore
                                        .KEY_PAYLOAD,
                                7
                        )
                        .putString(
                                OmrSharedPreferencesActiveAnswerKeyStore
                                        .KEY_CHECKSUM,
                                zeroChecksum()
                        )
                        .commit()
        );

        assertNull(store.loadActiveOrNull());
    }

    @Test
    public void nullAnswerKeyIsRejected() {
        try {
            store.saveActive(null);
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Comportamento esperado.
        }
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
