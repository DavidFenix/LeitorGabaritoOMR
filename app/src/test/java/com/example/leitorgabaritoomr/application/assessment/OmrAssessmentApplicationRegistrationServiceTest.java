package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.leitorgabaritoomr.application.classroom.OmrClassEnrollmentRepository;
import com.example.leitorgabaritoomr.application.classroom.OmrClassGroupRepository;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.application.student.OmrStudentRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

public final class OmrAssessmentApplicationRegistrationServiceTest {

    @Test
    public void constructorRejectsMissingRepository() {
        Fixture fixture = new Fixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> new OmrAssessmentApplicationRegistrationService(
                        fixture.assessments,
                        fixture.classes,
                        fixture.enrollments,
                        fixture.students,
                        fixture.applications,
                        fixture.participants,
                        null
                )
        );
    }

    @Test
    public void manualRegistrationFreezesSortedRoster() {
        Fixture fixture = new Fixture();

        OmrAssessmentApplicationRegistrationService.RegistrationResult
                result = fixture.service().registerNew(
                "assessment-1",
                2,
                "class-1"
        );

        assertEquals("application-1", result.getApplication()
                .getApplicationId());
        assertEquals(2, result.getApplication()
                .getAssessmentVersion());
        assertEquals(1000L, result.getApplication()
                .getCreatedAtEpochMillis());
        assertEquals(2, result.getParticipants().size());
        assertEquals("student-1", result.getParticipants().get(0)
                .getStudentId());
        assertEquals(1, result.getParticipants().get(0)
                .getRosterPosition());
        assertEquals("student-2", result.getParticipants().get(1)
                .getStudentId());
        assertEquals(1, fixture.batch.saveCallCount);
        assertNotNull(fixture.applications.findByIdOrNull(
                "application-1"
        ));
    }

    @Test
    public void productionRegistrationFreezesPublishedStructure() {
        Fixture fixture = new Fixture();
        OmrAssessmentStructure published = fixture.multidisciplinaryStructure();
        fixture.structures.stored = published;

        OmrAssessmentApplication application = fixture.snapshotService()
                .registerNew("assessment-1", 2, "class-1")
                .getApplication();

        assertTrue(application.hasFrozenStructure());
        assertEquals(published, application.getFrozenStructureOrNull());
    }

    @Test
    public void productionRegistrationFreezesLegacyBlockWhenNoStructureExists() {
        Fixture fixture = new Fixture();

        OmrAssessmentApplication application = fixture.snapshotService()
                .registerNew("assessment-1", 2, "class-1")
                .getApplication();

        assertTrue(application.hasFrozenStructure());
        assertTrue(application.getFrozenStructureOrNull()
                .isLegacySingleBlock());
        assertEquals(2, application.getFrozenStructureOrNull()
                .getQuestionCount());
    }

    @Test
    public void laterStructurePublicationDoesNotChangeApplicationSnapshot() {
        Fixture fixture = new Fixture();
        OmrAssessmentApplication application = fixture.snapshotService()
                .registerNew("assessment-1", 2, "class-1")
                .getApplication();
        OmrAssessmentStructure frozen = application
                .getFrozenStructureOrNull();

        fixture.structures.stored = fixture.multidisciplinaryStructure();

        assertTrue(frozen.isLegacySingleBlock());
        assertEquals(frozen, application.getFrozenStructureOrNull());
    }

    @Test
    public void manualRegistrationUsesExactAssessmentVersion() {
        Fixture fixture = new Fixture();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().registerNew(
                        "assessment-1",
                        3,
                        "class-1"
                )
        );

        assertTrue(exception.getMessage().contains("versão exata"));
        assertEquals(0, fixture.batch.saveCallCount);
    }

    @Test
    public void manualRegistrationRejectsDifferentSchoolYears() {
        Fixture fixture = new Fixture();
        fixture.classes.save(new OmrClassGroup(
                "class-old", "9º B", "9º ano", 2025
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().registerNew(
                        "assessment-1", 2, "class-old"
                )
        );

        assertTrue(exception.getMessage().contains("mesmo ano letivo"));
    }

    @Test
    public void manualRegistrationRejectsClassWithoutStudents() {
        Fixture fixture = new Fixture();
        fixture.classes.save(new OmrClassGroup(
                "class-empty", "9º C", "9º ano", 2026
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().registerNew(
                        "assessment-1", 2, "class-empty"
                )
        );

        assertTrue(exception.getMessage().contains("pelo menos"));
    }

    @Test
    public void manualRegistrationRejectsMissingStudentProfile() {
        Fixture fixture = new Fixture();
        fixture.students.byId.remove("student-2");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.service().registerNew(
                        "assessment-1", 2, "class-1"
                )
        );

        assertTrue(exception.getMessage().contains("student-2"));
    }

    @Test
    public void manualRegistrationRetriesCollidingGeneratedId() {
        Fixture fixture = new Fixture();
        fixture.applications.save(new OmrAssessmentApplication(
                "application-used",
                "assessment-1",
                2,
                "class-1",
                900L
        ));
        fixture.generatedIds.clear();
        fixture.generatedIds.add("application-used");
        fixture.generatedIds.add("application-free");

        OmrAssessmentApplicationRegistrationService.RegistrationResult
                result = fixture.service().registerNew(
                "assessment-1", 2, "class-1"
        );

        assertEquals("application-free", result.getApplication()
                .getApplicationId());
    }

    @Test
    public void manualResultCollectionsAreImmutable() {
        Fixture fixture = new Fixture();
        OmrAssessmentApplicationRegistrationService.RegistrationResult
                result = fixture.service().registerNew(
                "assessment-1", 2, "class-1"
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.getParticipants().clear()
        );
    }

    @Test
    public void csvCreatesMultipleApplicationsInOneBatch() {
        Fixture fixture = new Fixture();
        OmrAssessmentApplicationCsvParser.Preview preview = preview(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "MAT-9A;assessment-1;2;class-1\n"
                        + "MAT-9B;assessment-1;2;class-2\n"
        );

        OmrAssessmentApplicationRegistrationService.ImportSummary summary =
                fixture.service().importPreview(preview);

        assertEquals(2, summary.getCreatedApplicationCount());
        assertEquals(0, summary.getUnchangedApplicationCount());
        assertEquals(2, summary.getProcessedRowCount());
        assertEquals(4, summary.getFrozenParticipantCount());
        assertEquals(1, fixture.batch.saveCallCount);
        assertNotNull(fixture.applications.findByIdOrNull(
                "application-csv:MAT-9A"
        ));
    }

    @Test
    public void csvPreservesExplicitInternalApplicationId() {
        Fixture fixture = new Fixture();
        OmrAssessmentApplicationCsvParser.Preview preview = preview(
                "codigo_aplicacao;prova;versao_prova;turma;"
                        + "id_interno_aplicacao\n"
                        + "APP-VISIVEL;assessment-1;2;class-1;"
                        + "application-manual-123\n"
        );

        OmrAssessmentApplicationRegistrationService.ImportSummary summary =
                fixture.service().importPreview(preview);

        assertEquals(1, summary.getCreatedApplicationCount());
        assertEquals(
                "application-manual-123",
                summary.getCreatedApplications().get(0).getApplicationId()
        );
        assertNotNull(fixture.applications.findByIdOrNull(
                "application-manual-123"
        ));
    }

    @Test
    public void csvApplicationFreezesPublishedStructure() {
        Fixture fixture = new Fixture();
        OmrAssessmentStructure structure =
                fixture.multidisciplinaryStructure();
        fixture.structures.save(structure);

        OmrAssessmentApplicationRegistrationService.ImportSummary summary =
                fixture.snapshotService().importPreview(preview(
                        "codigo_aplicacao;prova;versao_prova;turma\n"
                                + "APP-NAT;assessment-1;2;class-1\n"
                ));

        assertEquals(1, summary.getCreatedApplicationCount());
        OmrAssessmentApplication created = summary
                .getCreatedApplications().get(0);
        assertTrue(created.hasFrozenStructure());
        assertEquals(structure, created.getFrozenStructureOrNull());
    }

    @Test
    public void csvResolvesImportedAssessmentCodeAndClassName() {
        Fixture fixture = new Fixture();
        fixture.assessments.save(new OmrAssessmentDefinition(
                "assessment-csv:MAT-B1",
                1,
                "Avaliação bimestral",
                "Matemática",
                "9º ano",
                "1º bimestre",
                2026,
                "key-1",
                1
        ));
        OmrAssessmentApplicationCsvParser.Preview preview = preview(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "Aplicação Á;mat-b1;1;9º A\n"
        );

        OmrAssessmentApplicationRegistrationService.ImportSummary summary =
                fixture.service().importPreview(preview);

        assertEquals(1, summary.getCreatedApplicationCount());
        OmrAssessmentApplication application = summary
                .getCreatedApplications().get(0);
        assertEquals(
                "application-csv:APLICACAO A",
                application.getApplicationId()
        );
        assertEquals("assessment-csv:MAT-B1",
                application.getAssessmentId());
        assertEquals("class-1", application.getClassGroupId());
    }

    @Test
    public void csvCanResolveUniqueAssessmentTitle() {
        Fixture fixture = new Fixture();
        OmrAssessmentApplicationCsvParser.Preview preview = preview(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "APP;Prova matemática;2;9º A\n"
        );

        OmrAssessmentApplicationRegistrationService.ImportSummary summary =
                fixture.service().importPreview(preview);

        assertEquals("assessment-1", summary.getCreatedApplications()
                .get(0).getAssessmentId());
    }

    @Test
    public void csvReimportPreservesOriginalFrozenRoster() {
        Fixture fixture = new Fixture();
        OmrAssessmentApplicationCsvParser.Preview preview = preview(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "APP-KEEP;assessment-1;2;class-1\n"
        );
        OmrAssessmentApplicationRegistrationService service =
                fixture.service();

        service.importPreview(preview);
        fixture.students.save(new OmrStudentProfile(
                "student-1", "MAT-001", "Nome alterado"
        ));
        fixture.enrollments.byClass.get("class-1").clear();

        OmrAssessmentApplicationRegistrationService.ImportSummary second =
                service.importPreview(preview);

        assertEquals(0, second.getCreatedApplicationCount());
        assertEquals(1, second.getUnchangedApplicationCount());
        assertEquals(1, fixture.batch.saveCallCount);
        List<OmrAssessmentParticipant> frozen = fixture.participants
                .loadForApplication("application-csv:APP-KEEP");
        assertEquals(2, frozen.size());
        assertEquals("Ana", frozen.get(0).getStudentName());
    }

    @Test
    public void csvRejectsExistingCodeWithDifferentBinding() {
        Fixture fixture = new Fixture();
        fixture.applications.save(new OmrAssessmentApplication(
                "application-csv:APP-CONFLICT",
                "assessment-1",
                2,
                "class-2",
                800L
        ));
        fixture.participants.saveRoster(
                "application-csv:APP-CONFLICT",
                Collections.singletonList(new OmrAssessmentParticipant(
                        "application-csv:APP-CONFLICT",
                        "student-1",
                        "MAT-001",
                        "Ana",
                        "9º B",
                        1
                ))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().importPreview(preview(
                        "codigo_aplicacao;prova;versao_prova;turma\n"
                                + "APP-CONFLICT;assessment-1;2;class-1\n"
                ))
        );

        assertTrue(exception.getMessage().contains("outra prova ou turma"));
    }

    @Test
    public void csvRejectsExistingApplicationWithoutRoster() {
        Fixture fixture = new Fixture();
        fixture.applications.save(new OmrAssessmentApplication(
                "application-csv:APP-BROKEN",
                "assessment-1",
                2,
                "class-1",
                800L
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.service().importPreview(preview(
                        "codigo_aplicacao;prova;versao_prova;turma\n"
                                + "APP-BROKEN;assessment-1;2;class-1\n"
                ))
        );

        assertTrue(exception.getMessage().contains("não possui chamada"));
    }

    @Test
    public void csvRejectsAmbiguousAssessmentTitle() {
        Fixture fixture = new Fixture();
        fixture.assessments.save(new OmrAssessmentDefinition(
                "assessment-2",
                2,
                "Prova Matemática",
                "Matemática",
                "9º ano",
                "1º bimestre",
                2026,
                "key-1",
                1
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().importPreview(preview(
                        "codigo_aplicacao;prova;versao_prova;turma\n"
                                + "APP;prova matematica;2;class-1\n"
                ))
        );

        assertTrue(exception.getMessage().contains("mais de uma prova"));
    }

    @Test
    public void csvRejectsClassFromAnotherSchoolYear() {
        Fixture fixture = new Fixture();
        fixture.classes.save(new OmrClassGroup(
                "class-2025", "9º Antiga", "9º ano", 2025
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().importPreview(preview(
                        "codigo_aplicacao;prova;versao_prova;turma\n"
                                + "APP;assessment-1;2;class-2025\n"
                ))
        );

        assertTrue(exception.getMessage().contains("mesmo ano letivo"));
    }

    @Test
    public void csvRejectsPreviewWithParserIssues() {
        Fixture fixture = new Fixture();
        OmrAssessmentApplicationCsvParser.Preview invalid = preview(
                "codigo_aplicacao;prova;versao_prova;turma\n"
                        + "APP;assessment-1;zero;class-1\n"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().importPreview(invalid)
        );
        assertEquals(0, fixture.batch.saveCallCount);
    }

    @Test
    public void importSummaryCollectionsAreImmutable() {
        Fixture fixture = new Fixture();
        OmrAssessmentApplicationRegistrationService.ImportSummary summary =
                fixture.service().importPreview(preview(
                        "codigo_aplicacao;prova;versao_prova;turma\n"
                                + "APP;assessment-1;2;class-1\n"
                ));

        assertThrows(
                UnsupportedOperationException.class,
                () -> summary.getCreatedApplications().clear()
        );
    }

    private static OmrAssessmentApplicationCsvParser.Preview preview(
            String csv
    ) {
        return new OmrAssessmentApplicationCsvParser().parse(csv);
    }

    private static final class Fixture {
        private final MemoryAssessmentRepository assessments =
                new MemoryAssessmentRepository();
        private final MemoryClassRepository classes =
                new MemoryClassRepository();
        private final MemoryEnrollmentRepository enrollments =
                new MemoryEnrollmentRepository();
        private final MemoryStudentRepository students =
                new MemoryStudentRepository();
        private final MemoryApplicationRepository applications =
                new MemoryApplicationRepository();
        private final MemoryParticipantRepository participants =
                new MemoryParticipantRepository();
        private final MemoryBatchRepository batch =
                new MemoryBatchRepository(applications, participants);
        private final MemoryStructureRepository structures =
                new MemoryStructureRepository();
        private final MemoryAnswerKeyRepository answerKeys =
                new MemoryAnswerKeyRepository();
        private final Deque<String> generatedIds = new ArrayDeque<>();

        private Fixture() {
            assessments.save(new OmrAssessmentDefinition(
                    "assessment-1",
                    2,
                    "Prova Matemática",
                    "Matemática",
                    "9º ano",
                    "1º bimestre",
                    2026,
                    "key-1",
                    1
            ));
            classes.save(new OmrClassGroup(
                    "class-1", "9º A", "9º ano", 2026
            ));
            classes.save(new OmrClassGroup(
                    "class-2", "9º B", "9º ano", 2026
            ));
            students.save(new OmrStudentProfile(
                    "student-1", "MAT-001", "Ana"
            ));
            students.save(new OmrStudentProfile(
                    "student-2", "MAT-002", "Bruno"
            ));
            enrollments.save(new OmrClassEnrollment(
                    "class-1", "student-2", 2
            ));
            enrollments.save(new OmrClassEnrollment(
                    "class-1", "student-1", 1
            ));
            enrollments.save(new OmrClassEnrollment(
                    "class-2", "student-1", 2
            ));
            enrollments.save(new OmrClassEnrollment(
                    "class-2", "student-2", 1
            ));
            generatedIds.add("application-1");
            answerKeys.save(new OmrAnswerKeyDefinition(
                    "key-1", 1, "Gabarito", "layout-2", 1,
                    Arrays.asList(
                            OmrAnswerKeyEntry.singleAnswer(
                                    "question-1", "A", 1.0
                            ),
                            OmrAnswerKeyEntry.singleAnswer(
                                    "question-2", "B", 1.0
                            )
                    )
            ));
        }

        private OmrAssessmentApplicationRegistrationService service() {
            return new OmrAssessmentApplicationRegistrationService(
                    assessments,
                    classes,
                    enrollments,
                    students,
                    applications,
                    participants,
                    batch,
                    () -> generatedIds.isEmpty()
                            ? "application-generated"
                            : generatedIds.removeFirst(),
                    () -> 1000L
            );
        }

        private OmrAssessmentApplicationRegistrationService
        snapshotService() {
            return new OmrAssessmentApplicationRegistrationService(
                    assessments,
                    classes,
                    enrollments,
                    students,
                    applications,
                    participants,
                    batch,
                    structures,
                    answerKeys,
                    () -> generatedIds.isEmpty()
                            ? "application-generated"
                            : generatedIds.removeFirst(),
                    () -> 1000L
            );
        }

        private OmrAssessmentStructure multidisciplinaryStructure() {
            return new OmrAssessmentStructure(
                    "assessment-1", 2, 2,
                    Arrays.asList(
                            new OmrAssessmentBlock(
                                    "qui", 1, "Natureza", "QUI", "Química",
                                    1, 1,
                                    OmrProportionalScoringPolicy
                                            .correctQuestionsToTenV1()
                            ),
                            new OmrAssessmentBlock(
                                    "fis", 2, "Natureza", "FIS", "Física",
                                    2, 1,
                                    OmrProportionalScoringPolicy
                                            .correctQuestionsToTenV1()
                            )
                    )
            );
        }
    }

    private static final class MemoryStructureRepository
            implements OmrAssessmentStructureRepository {
        private OmrAssessmentStructure stored;

        @Override
        public boolean save(OmrAssessmentStructure structure) {
            if (stored != null) {
                return false;
            }
            stored = structure;
            return true;
        }

        @Override
        public OmrAssessmentStructure findStoredOrNull(
                String assessmentId,
                int assessmentVersion
        ) {
            return stored != null && stored.hasAssessmentIdentity(
                    assessmentId, assessmentVersion
            ) ? stored : null;
        }

        @Override
        public List<OmrAssessmentStructure> loadStoredVersions(
                String assessmentId
        ) {
            return stored == null
                    ? Collections.emptyList()
                    : Collections.singletonList(stored);
        }

        @Override
        public OmrAssessmentStructure resolveOrLegacy(
                String assessmentId,
                int assessmentVersion,
                int questionCount
        ) {
            if (stored != null) {
                return stored.hasAssessmentIdentity(
                        assessmentId, assessmentVersion
                ) && stored.getQuestionCount() == questionCount
                        ? stored : null;
            }
            return OmrAssessmentStructure.legacySingleBlock(
                    assessmentId, assessmentVersion, questionCount
            );
        }
    }

    private static final class MemoryAnswerKeyRepository
            implements OmrAnswerKeyRepository {
        private OmrAnswerKeyDefinition value;
        private OmrAnswerKeyDefinition active;

        @Override
        public void save(OmrAnswerKeyDefinition answerKeyDefinition) {
            value = answerKeyDefinition;
        }

        @Override
        public List<OmrAnswerKeyDefinition> loadAll() {
            return value == null
                    ? Collections.emptyList()
                    : Collections.singletonList(value);
        }

        @Override
        public OmrAnswerKeyDefinition findOrNull(String id, int version) {
            return value != null
                    && value.getId().equals(id)
                    && value.getVersion() == version
                    ? value : null;
        }

        @Override
        public void selectActive(String id, int version) {
            active = findOrNull(id, version);
            if (active == null) {
                throw new IllegalArgumentException("Gabarito ausente.");
            }
        }

        @Override
        public boolean delete(String id, int version) {
            if (findOrNull(id, version) == null) {
                return false;
            }
            if (active == value) {
                active = null;
            }
            value = null;
            return true;
        }

        @Override
        public void saveActive(OmrAnswerKeyDefinition answerKeyDefinition) {
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

    private static final class MemoryAssessmentRepository
            implements OmrAssessmentRepository {
        private final List<OmrAssessmentDefinition> values =
                new ArrayList<>();

        @Override
        public boolean save(OmrAssessmentDefinition assessment) {
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
        public List<OmrAssessmentDefinition> loadAll() {
            return Collections.unmodifiableList(new ArrayList<>(values));
        }

        @Override
        public List<OmrAssessmentDefinition> loadLatestVersions() {
            Map<String, OmrAssessmentDefinition> latest =
                    new LinkedHashMap<>();
            for (OmrAssessmentDefinition value : values) {
                OmrAssessmentDefinition current = latest.get(
                        value.getAssessmentId()
                );
                if (current == null
                        || value.getVersion() > current.getVersion()) {
                    latest.put(value.getAssessmentId(), value);
                }
            }
            return new ArrayList<>(latest.values());
        }

        @Override
        public OmrAssessmentDefinition findOrNull(
                String assessmentId,
                int assessmentVersion
        ) {
            for (OmrAssessmentDefinition value : values) {
                if (value.hasIdentity(assessmentId, assessmentVersion)) {
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
            for (OmrAssessmentDefinition value : values) {
                if (value.getAssessmentId().equals(assessmentId)
                        && (latest == null
                        || value.getVersion() > latest.getVersion())) {
                    latest = value;
                }
            }
            return latest;
        }
    }

    private static final class MemoryClassRepository
            implements OmrClassGroupRepository {
        private final Map<String, OmrClassGroup> byId =
                new LinkedHashMap<>();

        @Override
        public void save(OmrClassGroup classGroup) {
            byId.put(classGroup.getClassGroupId(), classGroup);
        }

        @Override
        public List<OmrClassGroup> loadAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public List<OmrClassGroup> loadForSchoolYear(int schoolYear) {
            List<OmrClassGroup> result = new ArrayList<>();
            for (OmrClassGroup value : byId.values()) {
                if (value.getSchoolYear() == schoolYear) {
                    result.add(value);
                }
            }
            return result;
        }

        @Override
        public OmrClassGroup findByIdOrNull(String classGroupId) {
            return byId.get(classGroupId);
        }
    }

    private static final class MemoryEnrollmentRepository
            implements OmrClassEnrollmentRepository {
        private final Map<String, List<OmrClassEnrollment>> byClass =
                new LinkedHashMap<>();

        @Override
        public void save(OmrClassEnrollment enrollment) {
            List<OmrClassEnrollment> values = byClass.get(
                    enrollment.getClassGroupId()
            );
            if (values == null) {
                values = new ArrayList<>();
                byClass.put(enrollment.getClassGroupId(), values);
            }
            values.add(enrollment);
        }

        @Override
        public List<OmrClassEnrollment> loadForClassGroup(
                String classGroupId
        ) {
            List<OmrClassEnrollment> values = byClass.get(classGroupId);
            return values == null
                    ? Collections.emptyList()
                    : new ArrayList<>(values);
        }

        @Override
        public List<OmrClassEnrollment> loadForStudent(String studentId) {
            List<OmrClassEnrollment> result = new ArrayList<>();
            for (List<OmrClassEnrollment> values : byClass.values()) {
                for (OmrClassEnrollment value : values) {
                    if (value.getStudentId().equals(studentId)) {
                        result.add(value);
                    }
                }
            }
            return result;
        }

        @Override
        public OmrClassEnrollment findOrNull(
                String classGroupId,
                String studentId
        ) {
            for (OmrClassEnrollment value
                    : loadForClassGroup(classGroupId)) {
                if (value.getStudentId().equals(studentId)) {
                    return value;
                }
            }
            return null;
        }
    }

    private static final class MemoryStudentRepository
            implements OmrStudentRepository {
        private final Map<String, OmrStudentProfile> byId =
                new LinkedHashMap<>();

        @Override
        public void save(OmrStudentProfile student) {
            byId.put(student.getStudentId(), student);
        }

        @Override
        public void saveAll(List<OmrStudentProfile> students) {
            for (OmrStudentProfile student : students) {
                save(student);
            }
        }

        @Override
        public List<OmrStudentProfile> loadAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public OmrStudentProfile findByIdOrNull(String studentId) {
            return byId.get(studentId);
        }

        @Override
        public OmrStudentProfile findByRegistrationOrNull(
                String registration
        ) {
            for (OmrStudentProfile value : byId.values()) {
                if (value.hasRegistration(registration)) {
                    return value;
                }
            }
            return null;
        }
    }

    private static final class MemoryApplicationRepository
            implements OmrAssessmentApplicationRepository {
        private final Map<String, OmrAssessmentApplication> byId =
                new LinkedHashMap<>();

        @Override
        public boolean save(OmrAssessmentApplication application) {
            if (byId.containsKey(application.getApplicationId())) {
                return false;
            }
            byId.put(application.getApplicationId(), application);
            return true;
        }

        @Override
        public List<OmrAssessmentApplication> loadAll() {
            List<OmrAssessmentApplication> result =
                    new ArrayList<>(byId.values());
            result.sort(Comparator.comparingLong(
                    OmrAssessmentApplication::getCreatedAtEpochMillis
            ).reversed());
            return result;
        }

        @Override
        public List<OmrAssessmentApplication> loadForClassGroup(
                String classGroupId
        ) {
            List<OmrAssessmentApplication> result = new ArrayList<>();
            for (OmrAssessmentApplication value : byId.values()) {
                if (value.belongsToClassGroup(classGroupId)) {
                    result.add(value);
                }
            }
            return result;
        }

        @Override
        public List<OmrAssessmentApplication> loadForAssessment(
                String assessmentId,
                int assessmentVersion
        ) {
            List<OmrAssessmentApplication> result = new ArrayList<>();
            for (OmrAssessmentApplication value : byId.values()) {
                if (value.usesAssessment(
                        assessmentId,
                        assessmentVersion
                )) {
                    result.add(value);
                }
            }
            return result;
        }

        @Override
        public OmrAssessmentApplication findByIdOrNull(
                String applicationId
        ) {
            return byId.get(applicationId);
        }
    }

    private static final class MemoryParticipantRepository
            implements OmrAssessmentParticipantRepository {
        private final Map<String, List<OmrAssessmentParticipant>> byApp =
                new LinkedHashMap<>();

        @Override
        public boolean saveRoster(
                String applicationId,
                List<OmrAssessmentParticipant> participants
        ) {
            if (byApp.containsKey(applicationId)) {
                return false;
            }
            byApp.put(applicationId, new ArrayList<>(participants));
            return true;
        }

        @Override
        public List<OmrAssessmentParticipant> loadForApplication(
                String applicationId
        ) {
            List<OmrAssessmentParticipant> values = byApp.get(applicationId);
            return values == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(
                    new ArrayList<>(values)
            );
        }

        @Override
        public OmrAssessmentParticipant findOrNull(
                String applicationId,
                String studentId
        ) {
            for (OmrAssessmentParticipant value
                    : loadForApplication(applicationId)) {
                if (value.getStudentId().equals(studentId)) {
                    return value;
                }
            }
            return null;
        }
    }

    private static final class MemoryBatchRepository
            implements OmrAssessmentApplicationBatchRepository {
        private final MemoryApplicationRepository applications;
        private final MemoryParticipantRepository participants;
        private int saveCallCount;

        private MemoryBatchRepository(
                MemoryApplicationRepository applications,
                MemoryParticipantRepository participants
        ) {
            this.applications = applications;
            this.participants = participants;
        }

        @Override
        public void saveAllNew(
                List<OmrAssessmentApplication> applicationsToSave,
                List<OmrAssessmentParticipant> participantsToSave
        ) {
            saveCallCount++;
            Map<String, List<OmrAssessmentParticipant>> grouped =
                    new LinkedHashMap<>();
            for (OmrAssessmentApplication application
                    : applicationsToSave) {
                applications.save(application);
                grouped.put(
                        application.getApplicationId(),
                        new ArrayList<>()
                );
            }
            for (OmrAssessmentParticipant participant
                    : participantsToSave) {
                grouped.get(participant.getApplicationId())
                        .add(participant);
            }
            for (Map.Entry<String, List<OmrAssessmentParticipant>> entry
                    : grouped.entrySet()) {
                participants.saveRoster(entry.getKey(), entry.getValue());
            }
        }
    }
}
