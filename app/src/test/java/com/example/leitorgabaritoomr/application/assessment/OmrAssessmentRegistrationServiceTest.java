package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class OmrAssessmentRegistrationServiceTest {

    private MemoryAssessmentRepository assessments;
    private MemoryAnswerKeyRepository answerKeys;
    private SequenceIdGenerator idGenerator;
    private OmrAssessmentRegistrationService service;

    @Before
    public void setUp() {
        assessments = new MemoryAssessmentRepository();
        answerKeys = new MemoryAnswerKeyRepository();
        idGenerator = new SequenceIdGenerator("assessment-manual-1");
        answerKeys.save(answerKey("key-1", 1, "Gabarito Matemática"));
        answerKeys.save(answerKey("key-1", 2, "Gabarito Matemática"));

        service = new OmrAssessmentRegistrationService(
                assessments,
                assessments,
                answerKeys,
                idGenerator
        );
    }

    @Test
    public void constructorRejectsMissingDependencies() {
        expectIllegalArgument(() -> new OmrAssessmentRegistrationService(
                null, assessments, answerKeys, idGenerator
        ));
        expectIllegalArgument(() -> new OmrAssessmentRegistrationService(
                assessments, null, answerKeys, idGenerator
        ));
        expectIllegalArgument(() -> new OmrAssessmentRegistrationService(
                assessments, assessments, null, idGenerator
        ));
        expectIllegalArgument(() -> new OmrAssessmentRegistrationService(
                assessments, assessments, answerKeys, null
        ));
    }

    @Test
    public void manualRegistrationCreatesNormalizedVersionOne() {
        OmrAssessmentDefinition created = service.registerNew(
                "  Prova   bimestral  ",
                "  Matemática ",
                " 9º   ano ",
                " 2º   bimestre ",
                2026,
                " key-1 ",
                1
        );

        assertEquals("assessment-manual-1", created.getAssessmentId());
        assertEquals(1, created.getVersion());
        assertEquals("Prova bimestral", created.getTitle());
        assertEquals("Matemática", created.getSubject());
        assertEquals("9º ano", created.getGradeLevel());
        assertEquals("2º bimestre", created.getAcademicPeriod());
        assertEquals("key-1", created.getAnswerKeyId());
        assertEquals(1, assessments.loadAll().size());
    }

    @Test
    public void manualRegistrationRetriesCollidingGeneratedId() {
        assessments.save(assessment(
                "assessment-existing", 1, "Existente", "key-1", 1
        ));
        SequenceIdGenerator collidingIds =
                new SequenceIdGenerator("assessment-existing");
        collidingIds.add("assessment-new");
        OmrAssessmentRegistrationService collisionService =
                new OmrAssessmentRegistrationService(
                        assessments,
                        assessments,
                        answerKeys,
                        collidingIds
                );

        OmrAssessmentDefinition created = collisionService.registerNew(
                "Prova",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "key-1",
                1
        );

        assertEquals("assessment-new", created.getAssessmentId());
        assertEquals(2, assessments.loadAll().size());
    }

    @Test
    public void manualRegistrationRejectsUnknownAnswerKey() {
        expectIllegalArgument(() -> service.registerNew(
                "Prova", "Matemática", "9º ano", "Bimestre",
                2026, "missing", 1
        ));

        assertTrue(assessments.loadAll().isEmpty());
    }

    @Test
    public void manualRegistrationRejectsInvalidMetadata() {
        expectIllegalArgument(() -> service.registerNew(
                " ", "Matemática", "9º ano", "Bimestre",
                2026, "key-1", 1
        ));
        expectIllegalArgument(() -> service.registerNew(
                "Prova", "Matemática", "9º ano", "Bimestre",
                0, "key-1", 1
        ));
    }

    @Test
    public void manualRevisionPreservesIdAndCreatesNextVersion() {
        OmrAssessmentDefinition first = registerManual();

        OmrAssessmentDefinition second = service.registerNewVersion(
                first.getAssessmentId(),
                "Prova revisada",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "key-1",
                2
        );

        assertEquals(first.getAssessmentId(), second.getAssessmentId());
        assertEquals(2, second.getVersion());
        assertEquals("key-1", second.getAnswerKeyId());
        assertEquals(2, second.getAnswerKeyVersion());
        assertEquals(2, assessments.loadAll().size());
        assertNotNull(assessments.findOrNull(first.getAssessmentId(), 1));
    }

    @Test
    public void manualRevisionRequiresExistingAssessment() {
        expectIllegalArgument(() -> service.registerNewVersion(
                "missing", "Prova", "Matemática", "9º ano",
                "Bimestre", 2026, "key-1", 1
        ));
    }

    @Test
    public void manualRevisionProtectsIntegerVersionLimit() {
        assessments.save(assessment(
                "assessment-max",
                Integer.MAX_VALUE,
                "Prova",
                "key-1",
                1
        ));

        expectIllegalState(() -> service.registerNewVersion(
                "assessment-max", "Prova", "Matemática", "9º ano",
                "Bimestre", 2026, "key-1", 1
        ));
    }

    @Test
    public void csvImportResolvesAnswerKeyByNormalizedName() {
        OmrAssessmentRegistrationService.ImportSummary summary =
                service.importPreview(preview(
                        csvRow(
                                "MAT-B2", 1, "Prova B2",
                                "gabarito matematica", 1
                        )
                ));

        assertEquals(1, summary.getCreatedVersionCount());
        assertEquals(0, summary.getUnchangedVersionCount());
        OmrAssessmentDefinition stored = assessments
                .findLatestVersionOrNull("assessment-csv:MAT-B2");
        assertNotNull(stored);
        assertEquals("key-1", stored.getAnswerKeyId());
    }

    @Test
    public void csvImportResolvesAnswerKeyByExactId() {
        service.importPreview(preview(
                csvRow("MAT-B2", 1, "Prova", "key-1", 2)
        ));

        OmrAssessmentDefinition stored = assessments
                .findLatestVersionOrNull("assessment-csv:MAT-B2");
        assertEquals(2, stored.getAnswerKeyVersion());
    }

    @Test
    public void csvCodeProducesStableCaseInsensitiveAssessmentId() {
        assertEquals(
                "assessment-csv:PROVA 01",
                OmrAssessmentRegistrationService
                        .assessmentIdForCsvCode(" prova   01 ")
        );
    }

    @Test
    public void csvImportsContiguousVersionsInSingleBatch() {
        OmrAssessmentRegistrationService.ImportSummary summary =
                service.importPreview(preview(
                        csvRow("P1", 2, "Versão dois", "key-1", 2)
                                + "\n"
                                + csvRow("P1", 1, "Versão um", "key-1", 1)
                                + "\n"
                                + csvRow("P2", 1, "Outra prova", "key-1", 1)
                ));

        assertEquals(3, summary.getCreatedVersionCount());
        assertEquals(3, summary.getProcessedRowCount());
        assertEquals(1, assessments.batchCallCount);
        assertEquals(3, assessments.lastBatchSize);
        assertEquals(3, assessments.loadAll().size());
    }

    @Test
    public void csvRequiresFirstVersionToBeOne() {
        expectIllegalArgument(() -> service.importPreview(preview(
                csvRow("P1", 2, "Versão dois", "key-1", 1)
        )));

        assertTrue(assessments.loadAll().isEmpty());
        assertEquals(0, assessments.batchCallCount);
    }

    @Test
    public void csvRejectsVersionGapAfterExistingVersion() {
        service.importPreview(preview(
                csvRow("P1", 1, "Versão um", "key-1", 1)
        ));

        expectIllegalArgument(() -> service.importPreview(preview(
                csvRow("P1", 3, "Versão três", "key-1", 2)
        )));

        assertEquals(1, assessments.loadAll().size());
    }

    @Test
    public void identicalCsvReimportIsIdempotent() {
        OmrAssessmentCsvParser.Preview preview = preview(
                csvRow("P1", 1, "Versão um", "key-1", 1)
        );

        service.importPreview(preview);
        int callsAfterFirstImport = assessments.batchCallCount;

        OmrAssessmentRegistrationService.ImportSummary second =
                service.importPreview(preview);

        assertEquals(0, second.getCreatedVersionCount());
        assertEquals(1, second.getUnchangedVersionCount());
        assertEquals(callsAfterFirstImport, assessments.batchCallCount);
        assertEquals(1, assessments.loadAll().size());
    }

    @Test
    public void changedContentForExistingIdentityIsRejected() {
        service.importPreview(preview(
                csvRow("P1", 1, "Título original", "key-1", 1)
        ));

        expectIllegalArgument(() -> service.importPreview(preview(
                csvRow("P1", 1, "Título alterado", "key-1", 1)
        )));

        assertEquals(
                "Título original",
                assessments.findLatestVersionOrNull(
                        "assessment-csv:P1"
                ).getTitle()
        );
    }

    @Test
    public void csvRejectsUnknownAnswerKeyBeforeWritingBatch() {
        expectIllegalArgument(() -> service.importPreview(preview(
                csvRow("P1", 1, "Prova", "inexistente", 1)
        )));

        assertTrue(assessments.loadAll().isEmpty());
        assertEquals(0, assessments.batchCallCount);
    }

    @Test
    public void csvRejectsAmbiguousAnswerKeyName() {
        answerKeys.save(answerKey(
                "key-ambiguous", 1, "Gabarito Matemática"
        ));

        expectIllegalArgument(() -> service.importPreview(preview(
                csvRow("P1", 1, "Prova", "Gabarito Matemática", 1)
        )));

        assertTrue(assessments.loadAll().isEmpty());
    }

    @Test
    public void invalidPreviewAndNullPreviewAreRejected() {
        expectIllegalArgument(() -> service.importPreview(null));
        expectIllegalArgument(() -> service.importPreview(
                new OmrAssessmentCsvParser().parse(" ")
        ));
    }

    @Test
    public void batchFailureDoesNotExposeAnyImportedVersion() {
        assessments.failNextBatch = true;

        expectIllegalArgument(() -> service.importPreview(preview(
                csvRow("P1", 1, "Primeira", "key-1", 1)
                        + "\n"
                        + csvRow("P2", 1, "Segunda", "key-1", 1)
        )));

        assertTrue(assessments.loadAll().isEmpty());
    }

    @Test
    public void importSummaryCreatedVersionsAreImmutable() {
        OmrAssessmentRegistrationService.ImportSummary summary =
                service.importPreview(preview(
                        csvRow("P1", 1, "Prova", "key-1", 1)
                ));

        expectUnsupported(() -> summary.getCreatedVersions().clear());
    }

    private OmrAssessmentDefinition registerManual() {
        return service.registerNew(
                "Prova", "Matemática", "9º ano", "2º bimestre",
                2026, "key-1", 1
        );
    }

    private static OmrAssessmentCsvParser.Preview preview(String rows) {
        return new OmrAssessmentCsvParser().parse(
                "codigo_prova,versao_prova,titulo,disciplina,etapa,"
                        + "periodo,ano_letivo,gabarito,versao_gabarito\n"
                        + rows
        );
    }

    private static String csvRow(
            String code,
            int assessmentVersion,
            String title,
            String answerKeyReference,
            int answerKeyVersion
    ) {
        return code + "," + assessmentVersion + "," + title
                + ",Matemática,9º ano,2º bimestre,2026,"
                + answerKeyReference + "," + answerKeyVersion;
    }

    private static OmrAssessmentDefinition assessment(
            String id,
            int version,
            String title,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrAssessmentDefinition(
                id, version, title, "Matemática", "9º ano",
                "2º bimestre", 2026, answerKeyId, answerKeyVersion
        );
    }

    private static OmrAnswerKeyDefinition answerKey(
            String id,
            int version,
            String name
    ) {
        return new OmrAnswerKeyDefinition(
                id,
                version,
                name,
                "layout-1",
                1,
                Collections.singletonList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-001", "option-a", 1.0
                        )
                )
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalStateException.");
        } catch (IllegalStateException expected) {
            // Resultado esperado.
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    private static final class SequenceIdGenerator
            implements OmrAssessmentRegistrationService
            .AssessmentIdGenerator {

        private final List<String> values = new ArrayList<>();
        private int index;

        private SequenceIdGenerator(String firstValue) {
            values.add(firstValue);
        }

        private void add(String value) {
            values.add(value);
        }

        @Override
        public String createAssessmentId() {
            if (index >= values.size()) {
                return values.get(values.size() - 1);
            }
            return values.get(index++);
        }
    }

    private static final class MemoryAssessmentRepository
            implements OmrAssessmentRepository,
            OmrAssessmentBatchRepository {

        private final List<OmrAssessmentDefinition> values =
                new ArrayList<>();
        private int batchCallCount;
        private int lastBatchSize;
        private boolean failNextBatch;

        @Override
        public boolean save(OmrAssessmentDefinition assessment) {
            if (assessment == null) {
                throw new IllegalArgumentException();
            }
            if (findOrNull(
                    assessment.getAssessmentId(),
                    assessment.getVersion()
            ) != null) {
                return false;
            }
            values.add(assessment);
            return true;
        }

        @Override
        public void saveAllNew(
                List<OmrAssessmentDefinition> assessments
        ) {
            batchCallCount++;
            lastBatchSize = assessments.size();

            if (failNextBatch) {
                failNextBatch = false;
                throw new IllegalArgumentException("Falha simulada.");
            }

            for (OmrAssessmentDefinition assessment : assessments) {
                if (findOrNull(
                        assessment.getAssessmentId(),
                        assessment.getVersion()
                ) != null) {
                    throw new IllegalArgumentException("Duplicada.");
                }
            }
            values.addAll(assessments);
        }

        @Override
        public List<OmrAssessmentDefinition> loadAll() {
            List<OmrAssessmentDefinition> copy =
                    new ArrayList<>(values);
            copy.sort(order());
            return Collections.unmodifiableList(copy);
        }

        @Override
        public List<OmrAssessmentDefinition> loadLatestVersions() {
            List<OmrAssessmentDefinition> latest = new ArrayList<>();
            for (OmrAssessmentDefinition candidate : loadAll()) {
                if (findById(latest, candidate.getAssessmentId()) == null) {
                    latest.add(candidate);
                }
            }
            return Collections.unmodifiableList(latest);
        }

        @Override
        public OmrAssessmentDefinition findOrNull(
                String assessmentId,
                int assessmentVersion
        ) {
            if (assessmentId == null) {
                return null;
            }
            for (OmrAssessmentDefinition value : values) {
                if (value.hasIdentity(
                        assessmentId,
                        assessmentVersion
                )) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public OmrAssessmentDefinition findLatestVersionOrNull(
                String assessmentId
        ) {
            OmrAssessmentDefinition latest = null;
            if (assessmentId == null) {
                return null;
            }
            for (OmrAssessmentDefinition value : values) {
                if (value.getAssessmentId().equals(assessmentId.trim())
                        && (latest == null
                        || value.getVersion() > latest.getVersion())) {
                    latest = value;
                }
            }
            return latest;
        }

        private static OmrAssessmentDefinition findById(
                List<OmrAssessmentDefinition> values,
                String id
        ) {
            for (OmrAssessmentDefinition value : values) {
                if (value.getAssessmentId().equals(id)) {
                    return value;
                }
            }
            return null;
        }

        private static Comparator<OmrAssessmentDefinition> order() {
            return (first, second) -> {
                int byId = first.getAssessmentId().compareTo(
                        second.getAssessmentId()
                );
                return byId != 0
                        ? byId
                        : Integer.compare(
                                second.getVersion(),
                                first.getVersion()
                        );
            };
        }
    }

    private static final class MemoryAnswerKeyRepository
            implements OmrAnswerKeyRepository {

        private final List<OmrAnswerKeyDefinition> values =
                new ArrayList<>();
        private OmrAnswerKeyDefinition active;

        @Override
        public void save(OmrAnswerKeyDefinition answerKeyDefinition) {
            delete(
                    answerKeyDefinition.getId(),
                    answerKeyDefinition.getVersion()
            );
            values.add(0, answerKeyDefinition);
        }

        @Override
        public List<OmrAnswerKeyDefinition> loadAll() {
            return Collections.unmodifiableList(
                    new ArrayList<>(values)
            );
        }

        @Override
        public OmrAnswerKeyDefinition findOrNull(
                String answerKeyId,
                int answerKeyVersion
        ) {
            if (answerKeyId == null) {
                return null;
            }
            for (OmrAnswerKeyDefinition value : values) {
                if (value.getId().equals(answerKeyId.trim())
                        && value.getVersion() == answerKeyVersion) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public void selectActive(
                String answerKeyId,
                int answerKeyVersion
        ) {
            active = findOrNull(answerKeyId, answerKeyVersion);
            if (active == null) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public boolean delete(
                String answerKeyId,
                int answerKeyVersion
        ) {
            OmrAnswerKeyDefinition existing = findOrNull(
                    answerKeyId,
                    answerKeyVersion
            );
            if (existing == null) {
                return false;
            }
            values.remove(existing);
            if (active == existing) {
                active = null;
            }
            return true;
        }

        @Override
        public void saveActive(
                OmrAnswerKeyDefinition answerKeyDefinition
        ) {
            save(answerKeyDefinition);
            active = answerKeyDefinition;
        }

        @Override
        public OmrAnswerKeyDefinition loadActiveOrNull() {
            return active;
        }

        @Override
        public void clearActive() {
            active = null;
        }
    }
}
