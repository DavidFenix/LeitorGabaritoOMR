package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentRepositoryInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteAssessmentRepository repository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        testDatabaseName =
                "omr_school_assessment_test_"
                        + UUID.randomUUID()
                        + ".db";

        applicationContext.deleteDatabase(testDatabaseName);
        openRepository();
    }

    @After
    public void tearDown() {
        closeRepository();
        applicationContext.deleteDatabase(testDatabaseName);
    }

    @Test
    public void emptyRepositoryReturnsEmptyResults() {
        assertTrue(repository.loadAll().isEmpty());
        assertTrue(
                repository.loadLatestVersions().isEmpty()
        );
        assertNull(repository.findOrNull(
                "assessment-001",
                1
        ));
        assertNull(repository.findLatestVersionOrNull(
                "assessment-001"
        ));
    }

    @Test
    public void saveAndFindPreserveCompleteAssessment() {
        OmrAssessmentDefinition original = assessment(
                "assessment-001",
                1,
                "Prova de Matemática",
                2026,
                "answer-key-001",
                2
        );

        assertTrue(repository.save(original));

        assertCompleteAssessment(
                original,
                repository.findOrNull(
                        " assessment-001 ",
                        1
                )
        );
    }

    @Test
    public void duplicateIdentityPreservesFirstVersionContent() {
        OmrAssessmentDefinition original = assessment(
                "assessment-001",
                1,
                "Título original",
                2026,
                "answer-key-001",
                1
        );

        OmrAssessmentDefinition conflicting = assessment(
                "assessment-001",
                1,
                "Título conflitante",
                2027,
                "answer-key-999",
                9
        );

        assertTrue(repository.save(original));
        assertFalse(repository.save(conflicting));

        assertEquals(1, repository.loadAll().size());
        assertCompleteAssessment(
                original,
                repository.findOrNull(
                        "assessment-001",
                        1
                )
        );
    }

    @Test
    public void multipleVersionsCoexistAndAreFoundExactly() {
        OmrAssessmentDefinition versionOne = assessment(
                "assessment-001",
                1,
                "Prova de Matemática",
                2026,
                "answer-key-001",
                1
        );

        OmrAssessmentDefinition versionTwo = assessment(
                "assessment-001",
                2,
                "Prova de Matemática revisada",
                2026,
                "answer-key-001",
                2
        );

        assertTrue(repository.save(versionOne));
        assertTrue(repository.save(versionTwo));

        assertEquals(2, repository.loadAll().size());
        assertCompleteAssessment(
                versionOne,
                repository.findOrNull("assessment-001", 1)
        );
        assertCompleteAssessment(
                versionTwo,
                repository.findOrNull("assessment-001", 2)
        );
    }

    @Test
    public void findsHighestVersionByStableAssessmentId() {
        repository.save(assessment(
                "assessment-001",
                2,
                "Versão dois",
                2026,
                "answer-key-001",
                2
        ));
        repository.save(assessment(
                "assessment-001",
                1,
                "Versão um",
                2026,
                "answer-key-001",
                1
        ));
        repository.save(assessment(
                "assessment-001",
                3,
                "Versão três",
                2026,
                "answer-key-001",
                3
        ));

        OmrAssessmentDefinition latest =
                repository.findLatestVersionOrNull(
                        " assessment-001 "
                );

        assertNotNull(latest);
        assertEquals(3, latest.getVersion());
        assertEquals("Versão três", latest.getTitle());
    }

    @Test
    public void loadAllUsesDocumentedSchoolOrder() {
        repository.save(assessment(
                "assessment-z",
                1,
                "Algebra",
                2025,
                "key-z",
                1
        ));
        repository.save(assessment(
                "assessment-b",
                1,
                "Geometria",
                2026,
                "key-b",
                1
        ));
        repository.save(assessment(
                "assessment-a",
                1,
                "Algebra",
                2026,
                "key-a",
                1
        ));
        repository.save(assessment(
                "assessment-a",
                2,
                "Algebra",
                2026,
                "key-a",
                2
        ));

        List<OmrAssessmentDefinition> stored =
                repository.loadAll();

        assertEquals(4, stored.size());
        assertIdentity(stored.get(0), "assessment-a", 2);
        assertIdentity(stored.get(1), "assessment-a", 1);
        assertIdentity(stored.get(2), "assessment-b", 1);
        assertIdentity(stored.get(3), "assessment-z", 1);
    }

    @Test
    public void latestVersionsKeepOneHighestVersionPerId() {
        repository.save(assessment(
                "assessment-a",
                1,
                "Título antigo que ordenaria depois",
                2025,
                "key-a",
                1
        ));
        repository.save(assessment(
                "assessment-a",
                2,
                "Algebra",
                2026,
                "key-a",
                2
        ));
        repository.save(assessment(
                "assessment-b",
                1,
                "Geometria",
                2027,
                "key-b",
                1
        ));

        List<OmrAssessmentDefinition> latest =
                repository.loadLatestVersions();

        assertEquals(2, latest.size());
        assertIdentity(latest.get(0), "assessment-b", 1);
        assertIdentity(latest.get(1), "assessment-a", 2);
    }

    @Test
    public void newInstanceReadsPersistedAssessments() {
        OmrAssessmentDefinition original = assessment(
                "assessment-persisted",
                4,
                "Prova persistida",
                2026,
                "answer-key-004",
                5
        );

        repository.save(original);
        closeRepository();
        openRepository();

        assertCompleteAssessment(
                original,
                repository.findOrNull(
                        "assessment-persisted",
                        4
                )
        );
    }

    @Test
    public void returnedListsAreImmutable() {
        repository.save(assessment(
                "assessment-001",
                1,
                "Prova",
                2026,
                "answer-key-001",
                1
        ));

        expectUnsupportedOperation(() ->
                repository.loadAll().clear()
        );
        expectUnsupportedOperation(() ->
                repository.loadLatestVersions().clear()
        );
    }

    @Test
    public void invalidIdentitiesReturnNull() {
        repository.save(assessment(
                "assessment-001",
                1,
                "Prova",
                2026,
                "answer-key-001",
                1
        ));

        assertNull(repository.findOrNull(null, 1));
        assertNull(repository.findOrNull(" ", 1));
        assertNull(repository.findOrNull("assessment-001", 0));
        assertNull(repository.findOrNull("missing", 1));
        assertNull(repository.findLatestVersionOrNull(null));
        assertNull(repository.findLatestVersionOrNull(" "));
        assertNull(repository.findLatestVersionOrNull("missing"));
    }

    @Test
    public void rejectsNullAssessmentAndInvalidConstructorData() {
        expectIllegalArgument(() -> repository.save(null));

        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentRepository(
                        null,
                        testDatabaseName
                )
        );

        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentRepository(
                        applicationContext,
                        " "
                )
        );
    }

    @Test
    public void tamperedMetadataIsSkippedWithoutHidingHealthyRows() {
        repository.save(assessment(
                "assessment-tampered",
                1,
                "Prova adulterada",
                2026,
                "key-tampered",
                1
        ));
        repository.save(assessment(
                "assessment-healthy",
                1,
                "Prova saudável",
                2026,
                "key-healthy",
                1
        ));

        closeRepository();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_TITLE,
                "Título adulterado"
        );

        updateDatabaseRow(
                values,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
                        + " = ?",
                new String[]{"assessment-tampered"}
        );

        openRepository();

        List<OmrAssessmentDefinition> stored =
                repository.loadAll();

        assertEquals(1, stored.size());
        assertEquals(
                "assessment-healthy",
                stored.get(0).getAssessmentId()
        );
        assertNull(repository.findOrNull(
                "assessment-tampered",
                1
        ));
    }

    @Test
    public void corruptedPayloadIsSkippedWithoutHidingHealthyRows() {
        repository.save(assessment(
                "assessment-corrupted",
                2,
                "Prova corrompida",
                2026,
                "key-corrupted",
                2
        ));
        repository.save(assessment(
                "assessment-corrupted",
                1,
                "Versão anterior saudável",
                2026,
                "key-corrupted",
                1
        ));
        repository.save(assessment(
                "assessment-healthy",
                1,
                "Outra prova saudável",
                2026,
                "key-healthy",
                1
        ));

        closeRepository();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_PAYLOAD,
                new byte[]{1, 2, 3}
        );

        updateDatabaseRow(
                values,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
                        + " = ? AND "
                        + OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
                        + " = ?",
                new String[]{"assessment-corrupted", "2"}
        );

        openRepository();

        assertEquals(2, repository.loadAll().size());
        assertNull(repository.findOrNull(
                "assessment-corrupted",
                2
        ));

        OmrAssessmentDefinition latestHealthy =
                repository.findLatestVersionOrNull(
                        "assessment-corrupted"
                );

        assertNotNull(latestHealthy);
        assertEquals(1, latestHealthy.getVersion());
    }

    private void openRepository() {
        repository = new OmrSQLiteAssessmentRepository(
                applicationContext,
                testDatabaseName
        );
    }

    private void closeRepository() {
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    private void updateDatabaseRow(
            ContentValues values,
            String whereClause,
            String[] whereArguments
    ) {
        SQLiteDatabase database =
                SQLiteDatabase.openDatabase(
                        applicationContext
                                .getDatabasePath(testDatabaseName)
                                .getPath(),
                        null,
                        SQLiteDatabase.OPEN_READWRITE
                );

        try {
            int updated = database.update(
                    OmrSchoolDatabase.TABLE_ASSESSMENT,
                    values,
                    whereClause,
                    whereArguments
            );

            assertEquals(1, updated);
        } finally {
            database.close();
        }
    }

    private OmrAssessmentDefinition assessment(
            String assessmentId,
            int version,
            String title,
            int schoolYear,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrAssessmentDefinition(
                assessmentId,
                version,
                title,
                "Matemática",
                "9º ano",
                "2º bimestre",
                schoolYear,
                answerKeyId,
                answerKeyVersion
        );
    }

    private void assertIdentity(
            OmrAssessmentDefinition assessment,
            String expectedId,
            int expectedVersion
    ) {
        assertEquals(expectedId, assessment.getAssessmentId());
        assertEquals(expectedVersion, assessment.getVersion());
    }

    private void assertCompleteAssessment(
            OmrAssessmentDefinition expected,
            OmrAssessmentDefinition actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getSubject(), actual.getSubject());
        assertEquals(
                expected.getGradeLevel(),
                actual.getGradeLevel()
        );
        assertEquals(
                expected.getAcademicPeriod(),
                actual.getAcademicPeriod()
        );
        assertEquals(
                expected.getSchoolYear(),
                actual.getSchoolYear()
        );
        assertEquals(
                expected.getAnswerKeyId(),
                actual.getAnswerKeyId()
        );
        assertEquals(
                expected.getAnswerKeyVersion(),
                actual.getAnswerKeyVersion()
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
