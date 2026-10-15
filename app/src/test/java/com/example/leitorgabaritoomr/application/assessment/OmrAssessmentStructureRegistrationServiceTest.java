package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class OmrAssessmentStructureRegistrationServiceTest {

    private MemoryAssessmentRepository assessments;
    private MemoryStructureRepository structures;
    private MemoryAnswerKeyRepository answerKeys;
    private OmrAssessmentStructureRegistrationService service;

    @Before
    public void setUp() {
        assessments = new MemoryAssessmentRepository();
        structures = new MemoryStructureRepository();
        answerKeys = new MemoryAnswerKeyRepository();
        answerKeys.save(answerKey("key-45", 1, 45));
        assessments.save(assessment(
                "assessment-manual", 1, "Caderno Natureza", "key-45", 1
        ));
        assessments.save(assessment(
                "assessment-csv:NAT-1", 1, "Caderno CSV", "key-45", 1
        ));

        service = new OmrAssessmentStructureRegistrationService(
                assessments,
                structures,
                structures,
                answerKeys
        );
    }

    @Test
    public void constructorRejectsMissingDependencies() {
        expectIllegalArgument(() ->
                new OmrAssessmentStructureRegistrationService(
                        null, structures, structures, answerKeys
                ));
        expectIllegalArgument(() ->
                new OmrAssessmentStructureRegistrationService(
                        assessments, null, structures, answerKeys
                ));
        expectIllegalArgument(() ->
                new OmrAssessmentStructureRegistrationService(
                        assessments, structures, null, answerKeys
                ));
        expectIllegalArgument(() ->
                new OmrAssessmentStructureRegistrationService(
                        assessments, structures, structures, null
                ));
    }

    @Test
    public void manualRegistrationBuildsSequentialBlocks() {
        OmrAssessmentStructureRegistrationService.RegistrationResult result =
                service.register(
                        " assessment-manual ",
                        1,
                        natureBlocks()
                );

        assertTrue(result.isCreated());
        OmrAssessmentStructure structure = result.getStructure();
        assertEquals(45, structure.getQuestionCount());
        assertEquals(3, structure.getBlockCount());
        assertBlock(structure.getBlocks().get(0), 1, 1, 15, "Química");
        assertBlock(structure.getBlocks().get(1), 2, 16, 15, "Física");
        assertBlock(structure.getBlocks().get(2), 3, 31, 15, "Biologia");
        assertEquals(
                OmrProportionalScoringPolicy.CORRECT_QUESTIONS_POLICY_ID,
                structure.getBlocks().get(0)
                        .getScoringPolicy().getPolicyId()
        );
    }

    @Test
    public void manualRegistrationNormalizesOptionalMetadataAndIds() {
        List<OmrAssessmentStructureRegistrationService.BlockInput> inputs =
                Collections.singletonList(block(
                        1, "  PROJ ÉTICA  ", "  Formação   geral ",
                        "  FG  ", "  Projeto   Integrador ", 45
                ));

        OmrAssessmentBlock stored = service.register(
                "assessment-manual", 1, inputs
        ).getStructure().getBlocks().get(0);

        assertEquals("bloco:proj-etica", stored.getBlockId());
        assertEquals("Formação geral", stored.getAreaName());
        assertEquals("FG", stored.getComponentCode());
        assertEquals("Projeto Integrador", stored.getComponentName());
    }

    @Test
    public void generatedBlockIdUsesOrderWhenCodeIsMissing() {
        OmrAssessmentBlock stored = service.register(
                "assessment-manual",
                1,
                Collections.singletonList(block(
                        1, null, null, null, "Resultado", 45
                ))
        ).getStructure().getBlocks().get(0);

        assertEquals("bloco:1", stored.getBlockId());
    }

    @Test
    public void rejectsQuestionTotalDifferentFromAnswerKey() {
        List<OmrAssessmentStructureRegistrationService.BlockInput> inputs =
                new ArrayList<>(natureBlocks());
        inputs.set(2, block(
                3, "BIO", "Natureza", "BIO", "Biologia", 14
        ));

        expectIllegalArgument(() -> service.register(
                "assessment-manual", 1, inputs
        ));

        assertNull(structures.findStoredOrNull("assessment-manual", 1));
    }

    @Test
    public void rejectsMissingOrCorruptAssessment() {
        expectIllegalArgument(() -> service.register(
                "missing", 1, natureBlocks()
        ));
        expectIllegalArgument(() -> service.register(
                "assessment-manual", 0, natureBlocks()
        ));
    }

    @Test
    public void rejectsMissingExactAnswerKey() {
        assessments.save(assessment(
                "assessment-orphan", 1, "Orfa", "missing", 1
        ));

        expectIllegalState(() -> service.register(
                "assessment-orphan", 1, natureBlocks()
        ));
    }

    @Test
    public void rejectsNullEmptyAndNonContiguousBlocks() {
        expectIllegalArgument(() -> service.register(
                "assessment-manual", 1, null
        ));
        expectIllegalArgument(() -> service.register(
                "assessment-manual", 1, Collections.emptyList()
        ));

        List<OmrAssessmentStructureRegistrationService.BlockInput> gap =
                new ArrayList<>();
        gap.add(block(1, "A", null, null, "A", 15));
        gap.add(block(3, "B", null, null, "B", 30));
        expectIllegalArgument(() -> service.register(
                "assessment-manual", 1, gap
        ));

        List<OmrAssessmentStructureRegistrationService.BlockInput> withNull =
                new ArrayList<>();
        withNull.add(null);
        expectIllegalArgument(() -> service.register(
                "assessment-manual", 1, withNull
        ));
    }

    @Test
    public void identicalManualRegistrationIsIdempotent() {
        assertTrue(service.register(
                "assessment-manual", 1, natureBlocks()
        ).isCreated());
        int saves = structures.singleSaveCalls;

        OmrAssessmentStructureRegistrationService.RegistrationResult again =
                service.register(
                        "assessment-manual", 1, natureBlocks()
                );

        assertFalse(again.isCreated());
        assertEquals(saves, structures.singleSaveCalls);
    }

    @Test
    public void changedManualRegistrationCannotReplacePublishedStructure() {
        service.register("assessment-manual", 1, natureBlocks());
        List<OmrAssessmentStructureRegistrationService.BlockInput> changed =
                new ArrayList<>();
        changed.add(block(1, "QUI", "Natureza", "QUI", "Química", 10));
        changed.add(block(2, "FIS", "Natureza", "FIS", "Física", 20));
        changed.add(block(3, "BIO", "Natureza", "BIO", "Biologia", 15));

        expectIllegalArgument(() -> service.register(
                "assessment-manual", 1, changed
        ));

        assertEquals(
                15,
                structures.findStoredOrNull("assessment-manual", 1)
                        .getBlocks().get(0).getQuestionCount()
        );
    }

    @Test
    public void csvResolvesStableAssessmentCode() {
        OmrAssessmentStructureRegistrationService.ImportSummary summary =
                service.importPreview(preview("NAT-1"));

        assertEquals(1, summary.getCreatedStructureCount());
        assertNotNull(structures.findStoredOrNull(
                "assessment-csv:NAT-1", 1
        ));
    }

    @Test
    public void csvAlsoAcceptsExactAssessmentId() {
        OmrAssessmentStructureRegistrationService.ImportSummary summary =
                service.importPreview(preview("assessment-manual"));

        assertEquals(1, summary.getCreatedStructureCount());
        assertNotNull(structures.findStoredOrNull(
                "assessment-manual", 1
        ));
    }

    @Test
    public void csvImportsMultipleStructuresInOneBatch() {
        assessments.save(assessment(
                "assessment-csv:NAT-2", 1, "Outra", "key-45", 1
        ));
        String csv = csvHeader() + "\n"
                + csvRows("NAT-1") + "\n"
                + csvRows("NAT-2");

        OmrAssessmentStructureRegistrationService.ImportSummary summary =
                service.importPreview(
                        new OmrAssessmentStructureCsvParser().parse(csv)
                );

        assertEquals(2, summary.getCreatedStructureCount());
        assertEquals(2, summary.getProcessedAssessmentCount());
        assertEquals(6, summary.getProcessedBlockCount());
        assertEquals(1, structures.batchSaveCalls);
        assertEquals(2, structures.lastBatchSize);
    }

    @Test
    public void identicalCsvReimportIsIdempotent() {
        OmrAssessmentStructureCsvParser.Preview preview = preview("NAT-1");
        service.importPreview(preview);
        int batches = structures.batchSaveCalls;

        OmrAssessmentStructureRegistrationService.ImportSummary again =
                service.importPreview(preview);

        assertEquals(0, again.getCreatedStructureCount());
        assertEquals(1, again.getUnchangedStructureCount());
        assertEquals(batches, structures.batchSaveCalls);
    }

    @Test
    public void csvCannotChangePublishedStructure() {
        service.importPreview(preview("NAT-1"));
        String changed = csvHeader() + "\n"
                + "NAT-1;1;1;QUI;Natureza;QUI;Química;10\n"
                + "NAT-1;1;2;FIS;Natureza;FIS;Física;20\n"
                + "NAT-1;1;3;BIO;Natureza;BIO;Biologia;15";

        expectIllegalArgument(() -> service.importPreview(
                new OmrAssessmentStructureCsvParser().parse(changed)
        ));
    }

    @Test
    public void csvRejectsUnknownAssessmentBeforeBatch() {
        expectIllegalArgument(() -> service.importPreview(
                preview("DESCONHECIDA")
        ));

        assertEquals(0, structures.batchSaveCalls);
        assertTrue(structures.values.isEmpty());
    }

    @Test
    public void csvQuestionMismatchRejectsEntireBatch() {
        assessments.save(assessment(
                "assessment-csv:BAD", 1, "Invalida", "key-45", 1
        ));
        String csv = csvHeader() + "\n"
                + csvRows("NAT-1") + "\n"
                + "BAD;1;1;GERAL;;;Geral;44";

        expectIllegalArgument(() -> service.importPreview(
                new OmrAssessmentStructureCsvParser().parse(csv)
        ));

        assertEquals(0, structures.batchSaveCalls);
        assertTrue(structures.values.isEmpty());
    }

    @Test
    public void invalidAndNullPreviewsAreRejected() {
        expectIllegalArgument(() -> service.importPreview(null));
        expectIllegalArgument(() -> service.importPreview(
                new OmrAssessmentStructureCsvParser().parse(" ")
        ));
    }

    @Test
    public void batchFailureDoesNotExposePartialStructures() {
        assessments.save(assessment(
                "assessment-csv:NAT-2", 1, "Outra", "key-45", 1
        ));
        structures.failNextBatch = true;
        String csv = csvHeader() + "\n"
                + csvRows("NAT-1") + "\n"
                + csvRows("NAT-2");

        expectIllegalState(() -> service.importPreview(
                new OmrAssessmentStructureCsvParser().parse(csv)
        ));

        assertTrue(structures.values.isEmpty());
    }

    @Test
    public void importSummaryListIsImmutable() {
        OmrAssessmentStructureRegistrationService.ImportSummary summary =
                service.importPreview(preview("NAT-1"));

        try {
            summary.getCreatedStructures().clear();
            fail("A lista deveria ser imutavel.");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }

    private static List<OmrAssessmentStructureRegistrationService.BlockInput>
    natureBlocks() {
        List<OmrAssessmentStructureRegistrationService.BlockInput> blocks =
                new ArrayList<>();
        blocks.add(block(
                1, "QUI", "Ciências da Natureza", "QUI", "Química", 15
        ));
        blocks.add(block(
                2, "FIS", "Ciências da Natureza", "FIS", "Física", 15
        ));
        blocks.add(block(
                3, "BIO", "Ciências da Natureza", "BIO", "Biologia", 15
        ));
        return blocks;
    }

    private static OmrAssessmentStructureRegistrationService.BlockInput block(
            int order,
            String code,
            String area,
            String componentCode,
            String componentName,
            int count
    ) {
        return new OmrAssessmentStructureRegistrationService.BlockInput(
                order,
                code,
                area,
                componentCode,
                componentName,
                count
        );
    }

    private static OmrAssessmentStructureCsvParser.Preview preview(
            String assessmentReference
    ) {
        return new OmrAssessmentStructureCsvParser().parse(
                csvHeader() + "\n" + csvRows(assessmentReference)
        );
    }

    private static String csvHeader() {
        return "codigo_prova;versao_prova;ordem_bloco;codigo_bloco;"
                + "area;codigo_componente;componente;itens";
    }

    private static String csvRows(String reference) {
        return reference + ";1;1;QUI;Natureza;QUI;Química;15\n"
                + reference + ";1;2;FIS;Natureza;FIS;Física;15\n"
                + reference + ";1;3;BIO;Natureza;BIO;Biologia;15";
    }

    private static OmrAssessmentDefinition assessment(
            String id,
            int version,
            String title,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrAssessmentDefinition(
                id,
                version,
                title,
                "Caderno multidisciplinar",
                "Ensino médio",
                "3º período",
                2026,
                answerKeyId,
                answerKeyVersion
        );
    }

    private static OmrAnswerKeyDefinition answerKey(
            String id,
            int version,
            int questions
    ) {
        List<OmrAnswerKeyEntry> entries = new ArrayList<>();
        for (int index = 1; index <= questions; index++) {
            entries.add(OmrAnswerKeyEntry.singleAnswer(
                    "question-" + index,
                    "option-a",
                    1.0
            ));
        }
        return new OmrAnswerKeyDefinition(
                id,
                version,
                "Gabarito " + questions,
                "layout-" + questions,
                1,
                entries
        );
    }

    private static void assertBlock(
            OmrAssessmentBlock block,
            int order,
            int first,
            int count,
            String name
    ) {
        assertEquals(order, block.getOrder());
        assertEquals(first, block.getFirstQuestionPosition());
        assertEquals(count, block.getQuestionCount());
        assertEquals(name, block.getComponentName());
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(true);
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalStateException.");
        } catch (IllegalStateException expected) {
            assertTrue(true);
        }
    }

    private static final class MemoryAssessmentRepository
            implements OmrAssessmentRepository {
        private final List<OmrAssessmentDefinition> values =
                new ArrayList<>();

        @Override
        public boolean save(OmrAssessmentDefinition value) {
            if (findOrNull(value.getAssessmentId(), value.getVersion())
                    != null) {
                return false;
            }
            values.add(value);
            return true;
        }

        @Override
        public List<OmrAssessmentDefinition> loadAll() {
            return Collections.unmodifiableList(new ArrayList<>(values));
        }

        @Override
        public List<OmrAssessmentDefinition> loadLatestVersions() {
            return loadAll();
        }

        @Override
        public OmrAssessmentDefinition findOrNull(String id, int version) {
            if (id == null) {
                return null;
            }
            for (OmrAssessmentDefinition value : values) {
                if (value.hasIdentity(id, version)) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public OmrAssessmentDefinition findLatestVersionOrNull(String id) {
            OmrAssessmentDefinition latest = null;
            if (id == null) {
                return null;
            }
            for (OmrAssessmentDefinition value : values) {
                if (value.getAssessmentId().equals(id.trim())
                        && (latest == null
                        || value.getVersion() > latest.getVersion())) {
                    latest = value;
                }
            }
            return latest;
        }
    }

    private static final class MemoryStructureRepository
            implements OmrAssessmentStructureRepository,
            OmrAssessmentStructureBatchRepository {
        private final List<OmrAssessmentStructure> values =
                new ArrayList<>();
        private int singleSaveCalls;
        private int batchSaveCalls;
        private int lastBatchSize;
        private boolean failNextBatch;

        @Override
        public boolean save(OmrAssessmentStructure structure) {
            singleSaveCalls++;
            if (findStoredOrNull(
                    structure.getAssessmentId(),
                    structure.getAssessmentVersion()
            ) != null) {
                return false;
            }
            values.add(structure);
            return true;
        }

        @Override
        public void saveAllNew(List<OmrAssessmentStructure> batch) {
            batchSaveCalls++;
            lastBatchSize = batch.size();
            if (failNextBatch) {
                failNextBatch = false;
                throw new IllegalStateException("Falha simulada.");
            }
            for (OmrAssessmentStructure candidate : batch) {
                if (findStoredOrNull(
                        candidate.getAssessmentId(),
                        candidate.getAssessmentVersion()
                ) != null) {
                    throw new IllegalArgumentException("Duplicada.");
                }
            }
            values.addAll(batch);
        }

        @Override
        public OmrAssessmentStructure findStoredOrNull(
                String id,
                int version
        ) {
            if (id == null) {
                return null;
            }
            for (OmrAssessmentStructure value : values) {
                if (value.hasAssessmentIdentity(id, version)) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public List<OmrAssessmentStructure> loadStoredVersions(String id) {
            List<OmrAssessmentStructure> found = new ArrayList<>();
            for (OmrAssessmentStructure value : values) {
                if (id != null
                        && value.getAssessmentId().equals(id.trim())) {
                    found.add(value);
                }
            }
            found.sort(Comparator.comparingInt(
                    OmrAssessmentStructure::getAssessmentVersion
            ).reversed());
            return Collections.unmodifiableList(found);
        }

        @Override
        public OmrAssessmentStructure resolveOrLegacy(
                String id,
                int version,
                int questionCount
        ) {
            return findStoredOrNull(id, version);
        }
    }

    private static final class MemoryAnswerKeyRepository
            implements OmrAnswerKeyRepository {
        private final List<OmrAnswerKeyDefinition> values =
                new ArrayList<>();
        private OmrAnswerKeyDefinition active;

        @Override
        public void save(OmrAnswerKeyDefinition value) {
            delete(value.getId(), value.getVersion());
            values.add(0, value);
        }

        @Override
        public List<OmrAnswerKeyDefinition> loadAll() {
            return Collections.unmodifiableList(new ArrayList<>(values));
        }

        @Override
        public OmrAnswerKeyDefinition findOrNull(String id, int version) {
            if (id == null) {
                return null;
            }
            for (OmrAnswerKeyDefinition value : values) {
                if (value.getId().equals(id.trim())
                        && value.getVersion() == version) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public void selectActive(String id, int version) {
            OmrAnswerKeyDefinition found = findOrNull(id, version);
            if (found == null) {
                throw new IllegalArgumentException();
            }
            active = found;
        }

        @Override
        public boolean delete(String id, int version) {
            OmrAnswerKeyDefinition found = findOrNull(id, version);
            if (found == null) {
                return false;
            }
            values.remove(found);
            if (found.equals(active)) {
                active = null;
            }
            return true;
        }

        @Override
        public void saveActive(OmrAnswerKeyDefinition value) {
            save(value);
            active = value;
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
