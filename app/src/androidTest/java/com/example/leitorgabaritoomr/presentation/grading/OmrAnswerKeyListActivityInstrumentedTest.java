package com.example.leitorgabaritoomr.presentation.grading;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.infrastructure.grading.OmrSharedPreferencesAnswerKeyRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Protege o contrato Android da tela de gabaritos salvos.
 *
 * Os testes usam preferências limpas do processo de teste e gabaritos de duas
 * questões. Não inicializam câmera, OpenCV nem o pipeline de leitura.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrAnswerKeyListActivityInstrumentedTest {

    private static final String REPOSITORY_PREFERENCES_NAME =
            "omr_answer_key_repository";

    private static final String LEGACY_PREFERENCES_NAME =
            "omr_active_answer_key";

    private Context applicationContext;
    private OmrSharedPreferencesAnswerKeyRepository repository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        clearStoredAnswerKeys();

        repository =
                new OmrSharedPreferencesAnswerKeyRepository(
                        applicationContext
                );
    }

    @After
    public void tearDown() {
        clearStoredAnswerKeys();
        repository = null;
        applicationContext = null;
    }

    @Test
    public void emptyRepositoryShowsEmptyStateAndActions() {
        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> {
                        assertText(
                                activity,
                                R.id.textOmrAnswerKeyListCount,
                                activity
                                .getResources()
                                .getQuantityString(
                                        R.plurals
                                        .omr_answer_key_list_count,
                                        0,
                                        0
                                )
                        );

                        assertVisibility(
                                activity,
                                R.id.containerOmrAnswerKeyListEmpty,
                                View.VISIBLE
                        );

                        assertVisibility(
                                activity,
                                R.id.listOmrAnswerKeys,
                                View.GONE
                        );

                        assertButtonEnabled(
                                activity,
                                R.id.buttonOmrAnswerKeyListCreate,
                                true
                        );

                        assertButtonEnabled(
                                activity,
                                R.id.buttonOmrAnswerKeyListBack,
                                true
                        );
                    }
            );
        }
    }

    @Test
    public void twoAnswerKeysRenderInRepositoryOrderWithActiveCard() {
        OmrAnswerKeyDefinition active = createAnswerKey(
                "active-key",
                1,
                "Gabarito ativo",
                1.0
        );

        OmrAnswerKeyDefinition recent = createAnswerKey(
                "recent-key",
                3,
                "Gabarito recente",
                2.0
        );

        repository.saveActive(active);
        repository.save(recent);

        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> {
                        ListView listView =
                                getAnswerKeyList(activity);

                        ListAdapter adapter =
                                listView.getAdapter();

                        assertNotNull(adapter);
                        assertEquals(2, adapter.getCount());

                        View recentRow = getRow(
                                activity,
                                0
                        );

                        View activeRow = getRow(
                                activity,
                                1
                        );

                        assertRow(
                                activity,
                                recentRow,
                                recent,
                                false
                        );

                        assertRow(
                                activity,
                                activeRow,
                                active,
                                true
                        );

                        assertTrue(
                                listView.getChildCount()
                                        <= adapter.getCount()
                        );
                    }
            );
        }
    }

    @Test
    public void selectingKeySurvivesRecreationAndReturnsActiveKey() {
        OmrAnswerKeyDefinition originalActive = createAnswerKey(
                "original-active",
                1,
                "Ativo original",
                1.0
        );

        OmrAnswerKeyDefinition selected = createAnswerKey(
                "selected-key",
                2,
                "Gabarito selecionado",
                2.0
        );

        repository.saveActive(originalActive);
        repository.save(selected);

        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> clickRowButton(
                            activity,
                            0,
                            R.id.buttonOmrAnswerKeySelect
                    )
            );

            assertIdentity(
                    selected,
                    repository.loadActiveOrNull()
            );

            scenario.recreate();

            scenario.onActivity(
                    activity -> {
                        assertRow(
                                activity,
                                getRow(activity, 0),
                                selected,
                                true
                        );

                        assertRow(
                                activity,
                                getRow(activity, 1),
                                originalActive,
                                false
                        );

                        clickButton(
                                activity,
                                R.id.buttonOmrAnswerKeyListBack
                        );
                    }
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_OK,
                    result.getResultCode()
            );

            assertTrue(
                    OmrAnswerKeyListActivity
                            .didRepositoryChange(
                                    result.getResultData()
                            )
            );

            assertIdentity(
                    selected,
                    OmrAnswerKeyListActivity
                            .extractActiveAnswerKey(
                                    result.getResultData()
                            )
            );
        }
    }

    @Test
    public void confirmedDeletionOfInactiveKeyPreservesActiveKey() {
        OmrAnswerKeyDefinition active = createAnswerKey(
                "preserved-active",
                1,
                "Ativo preservado",
                1.0
        );

        OmrAnswerKeyDefinition inactive = createAnswerKey(
                "deleted-inactive",
                1,
                "Inativo removido",
                2.0
        );

        repository.saveActive(active);
        repository.save(inactive);

        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario = ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> clickRowButton(
                            activity,
                            0,
                            R.id.buttonOmrAnswerKeyDelete
                    )
            );

            confirmDeletion();

            scenario.onActivity(
                    activity -> {
                        assertEquals(
                                1,
                                getAnswerKeyList(activity)
                                        .getAdapter()
                                        .getCount()
                        );

                        assertRow(
                                activity,
                                getRow(activity, 0),
                                active,
                                true
                        );
                    }
            );

            assertNull(
                    repository.findOrNull(
                            inactive.getId(),
                            inactive.getVersion()
                    )
            );

            assertIdentity(
                    active,
                    repository.loadActiveOrNull()
            );
        }
    }

    @Test
    public void confirmedDeletionOfActiveKeyShowsEmptyAndReturnsNull() {
        OmrAnswerKeyDefinition active = createAnswerKey(
                "deleted-active",
                1,
                "Ativo removido",
                1.0
        );

        repository.saveActive(active);

        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> clickRowButton(
                            activity,
                            0,
                            R.id.buttonOmrAnswerKeyDelete
                    )
            );

            confirmDeletion();

            scenario.onActivity(
                    activity -> {
                        assertVisibility(
                                activity,
                                R.id
                                .containerOmrAnswerKeyListEmpty,
                                View.VISIBLE
                        );

                        assertVisibility(
                                activity,
                                R.id.listOmrAnswerKeys,
                                View.GONE
                        );

                        clickButton(
                                activity,
                                R.id.buttonOmrAnswerKeyListBack
                        );
                    }
            );

            assertTrue(repository.loadAll().isEmpty());
            assertNull(repository.loadActiveOrNull());

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_OK,
                    result.getResultCode()
            );

            assertTrue(
                    OmrAnswerKeyListActivity
                            .didRepositoryChange(
                                    result.getResultData()
                            )
            );

            assertNull(
                    OmrAnswerKeyListActivity
                            .extractActiveAnswerKey(
                                    result.getResultData()
                            )
            );
        }
    }

    @Test
    public void backWithoutChangesReturnsCanceled() {
        repository.saveActive(
                createAnswerKey(
                        "unchanged",
                        1,
                        "Sem alterações",
                        1.0
                )
        );

        try (ActivityScenario<OmrAnswerKeyListActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> clickButton(
                            activity,
                            R.id.buttonOmrAnswerKeyListBack
                    )
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_CANCELED,
                    result.getResultCode()
            );

            assertFalse(
                    OmrAnswerKeyListActivity
                            .didRepositoryChange(
                                    result.getResultData()
                            )
            );
        }
    }

    private Intent createActivityIntent() {
        return OmrAnswerKeyListActivity.createIntent(
                applicationContext
        );
    }

    private void clearStoredAnswerKeys() {
        if (applicationContext == null) {
            return;
        }

        clearPreferences(REPOSITORY_PREFERENCES_NAME);
        clearPreferences(LEGACY_PREFERENCES_NAME);
    }

    private void clearPreferences(
            String preferencesName
    ) {
        SharedPreferences preferences =
                applicationContext.getSharedPreferences(
                        preferencesName,
                        Context.MODE_PRIVATE
                );

        assertTrue(
                preferences.edit()
                        .clear()
                        .commit()
        );
    }

    private static void confirmDeletion() {
        onView(
                withText(
                        R.string
                        .omr_answer_key_list_delete_confirm
                )
        )
                .inRoot(isDialog())
                .perform(click());
    }

    private static ListView getAnswerKeyList(
            Activity activity
    ) {
        ListView listView =
                activity.findViewById(
                        R.id.listOmrAnswerKeys
                );

        assertNotNull(listView);
        return listView;
    }

    private static View getRow(
            Activity activity,
            int position
    ) {
        ListView listView =
                getAnswerKeyList(activity);

        ListAdapter adapter =
                listView.getAdapter();

        assertNotNull(adapter);
        assertTrue(position >= 0);
        assertTrue(position < adapter.getCount());

        return adapter.getView(
                position,
                null,
                listView
        );
    }

    private static void clickRowButton(
            Activity activity,
            int position,
            int buttonId
    ) {
        View row = getRow(activity, position);

        Button button = row.findViewById(buttonId);

        assertNotNull(button);
        assertTrue(button.isEnabled());
        assertTrue(button.performClick());
    }

    private static void clickButton(
            Activity activity,
            int buttonId
    ) {
        Button button = activity.findViewById(buttonId);

        assertNotNull(button);
        assertTrue(button.isEnabled());
        assertTrue(button.performClick());
    }

    private static void assertRow(
            Activity activity,
            View row,
            OmrAnswerKeyDefinition answerKey,
            boolean active
    ) {
        assertChildText(
                row,
                R.id.textOmrAnswerKeyName,
                answerKey.getName()
        );

        assertChildText(
                row,
                R.id.textOmrAnswerKeyVersion,
                activity.getString(
                        R.string
                        .omr_answer_key_list_version_format,
                        answerKey.getVersion()
                )
        );

        assertChildText(
                row,
                R.id.textOmrAnswerKeyQuestions,
                activity
                .getResources()
                .getQuantityString(
                        R.plurals
                        .omr_answer_key_list_questions_format,
                        answerKey.getQuestionCount(),
                        answerKey.getQuestionCount()
                )
        );

        assertChildText(
                row,
                R.id.textOmrAnswerKeyWeight,
                activity.getString(
                        R.string
                        .omr_answer_key_list_weight_format,
                        answerKey.getTotalWeight()
                )
        );

        TextView badge = row.findViewById(
                R.id.textOmrAnswerKeyActiveBadge
        );

        Button selectButton = row.findViewById(
                R.id.buttonOmrAnswerKeySelect
        );

        assertNotNull(badge);
        assertNotNull(selectButton);

        assertEquals(
                active ? View.VISIBLE : View.GONE,
                badge.getVisibility()
        );

        assertEquals(!active, selectButton.isEnabled());

        assertEquals(
                activity.getString(
                        active
                                ? R.string
                                .omr_answer_key_list_action_selected
                                : R.string
                                .omr_answer_key_list_action_select
                ),
                selectButton.getText().toString()
        );
    }

    private static void assertText(
            Activity activity,
            int viewId,
            String expectedText
    ) {
        TextView textView =
                activity.findViewById(viewId);

        assertNotNull(textView);
        assertEquals(
                expectedText,
                textView.getText().toString()
        );
    }

    private static void assertChildText(
            View rootView,
            int viewId,
            String expectedText
    ) {
        TextView textView = rootView.findViewById(viewId);

        assertNotNull(textView);
        assertEquals(
                expectedText,
                textView.getText().toString()
        );
    }

    private static void assertVisibility(
            Activity activity,
            int viewId,
            int expectedVisibility
    ) {
        View view = activity.findViewById(viewId);

        assertNotNull(view);
        assertEquals(
                expectedVisibility,
                view.getVisibility()
        );
    }

    private static void assertButtonEnabled(
            Activity activity,
            int buttonId,
            boolean expectedEnabled
    ) {
        Button button = activity.findViewById(buttonId);

        assertNotNull(button);
        assertEquals(
                expectedEnabled,
                button.isEnabled()
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
                OmrAnswerKeyEntry.singleAnswer(
                        "Q02",
                        "Q02-B",
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

    private static void assertIdentity(
            OmrAnswerKeyDefinition expected,
            OmrAnswerKeyDefinition actual
    ) {
        assertNotNull(actual);
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getVersion(), actual.getVersion());
        assertEquals(expected.getName(), actual.getName());
    }
}
