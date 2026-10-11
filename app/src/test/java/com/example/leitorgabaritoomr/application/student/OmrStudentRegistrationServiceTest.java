package com.example.leitorgabaritoomr.application.student;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OmrStudentRegistrationServiceTest {

    private final OmrStudentCsvParser parser =
            new OmrStudentCsvParser();

    @Test
    public void rejectsMissingRepositoryOrIdGenerator() {
        expectIllegalArgument(() ->
                new OmrStudentRegistrationService(null)
        );

        expectIllegalArgument(() ->
                new OmrStudentRegistrationService(
                        new InMemoryRepository(),
                        null
                )
        );
    }

    @Test
    public void manualRegistrationCreatesNormalizedStudent() {
        InMemoryRepository repository =
                new InMemoryRepository();
        OmrStudentRegistrationService service = service(
                repository,
                "student-new"
        );

        OmrStudentProfile student = service.registerOrUpdate(
                " 000123a ",
                "  Ana   Beatriz  "
        );

        assertEquals("student-new", student.getStudentId());
        assertEquals("000123A", student.getRegistration());
        assertEquals("Ana Beatriz", student.getName());
        assertEquals(1, repository.singleSaveCalls);
        assertEquals(0, repository.batchSaveCalls);
        assertEquals(
                student,
                repository.findByIdOrNull("student-new")
        );
    }

    @Test
    public void manualRegistrationUpdatesAndPreservesIdentity() {
        InMemoryRepository repository =
                new InMemoryRepository();
        repository.put(student(
                "student-existing",
                "000123",
                "Nome anterior"
        ));

        OmrStudentRegistrationService service = service(
                repository,
                "id-que-nao-deve-ser-usado"
        );

        OmrStudentProfile updated = service.registerOrUpdate(
                " 000123 ",
                "Ana Beatriz"
        );

        assertEquals("student-existing", updated.getStudentId());
        assertEquals("Ana Beatriz", updated.getName());
        assertEquals(1, repository.size());
        assertEquals(0, repository.generatedIdLookups);
    }

    @Test
    public void invalidManualDataDoesNotWrite() {
        InMemoryRepository repository =
                new InMemoryRepository();
        OmrStudentRegistrationService service = service(
                repository,
                "student-new"
        );

        expectIllegalArgument(() ->
                service.registerOrUpdate(null, "Ana")
        );
        expectIllegalArgument(() ->
                service.registerOrUpdate("000123", " ")
        );

        assertEquals(0, repository.singleSaveCalls);
        assertEquals(0, repository.batchSaveCalls);
        assertEquals(0, repository.size());
    }

    @Test
    public void csvImportCreatesAllStudentsInOneBatch() {
        InMemoryRepository repository =
                new InMemoryRepository();
        OmrStudentRegistrationService service = service(
                repository,
                "student-1",
                "student-2",
                "student-3"
        );

        OmrStudentRegistrationService.ImportSummary summary =
                service.importPreview(preview(
                        "matricula,nome\n"
                                + "000001,Ana\n"
                                + "000002,Bruno\n"
                                + "000003,Carla"
                ));

        assertEquals(3, summary.getCreatedCount());
        assertEquals(0, summary.getUpdatedCount());
        assertEquals(3, summary.getProcessedCount());
        assertEquals(0, repository.singleSaveCalls);
        assertEquals(1, repository.batchSaveCalls);
        assertEquals(1, repository.loadAllCalls);
        assertEquals(0, repository.registrationLookupCalls);
        assertEquals(0, repository.generatedIdLookups);
        assertEquals(3, repository.lastBatch.size());
    }

    @Test
    public void csvImportCreatesAndUpdatesWithoutChangingExistingId() {
        InMemoryRepository repository =
                new InMemoryRepository();
        repository.put(student(
                "student-existing",
                "000001",
                "Nome anterior"
        ));

        OmrStudentRegistrationService service = service(
                repository,
                "student-new"
        );

        OmrStudentRegistrationService.ImportSummary summary =
                service.importPreview(preview(
                        "matricula;nome\n"
                                + "000001;Ana atualizada\n"
                                + "000002;Bruno"
                ));

        assertEquals(1, summary.getCreatedCount());
        assertEquals(1, summary.getUpdatedCount());
        assertEquals(2, summary.getProcessedCount());
        assertEquals(
                "student-existing",
                summary.getStudents().get(0).getStudentId()
        );
        assertEquals(
                "Ana atualizada",
                repository.findByIdOrNull(
                        "student-existing"
                ).getName()
        );
        assertNotNull(repository.findByRegistrationOrNull(
                "000002"
        ));
    }

    @Test
    public void csvImportRejectsNullOrInvalidPreviewBeforeReadingRepository() {
        InMemoryRepository repository =
                new InMemoryRepository();
        OmrStudentRegistrationService service = service(
                repository,
                "student-new"
        );

        expectIllegalArgument(() ->
                service.importPreview(null)
        );
        expectIllegalArgument(() ->
                service.importPreview(parser.parse(
                        "matricula,nome\n,Ana"
                ))
        );

        assertEquals(0, repository.loadAllCalls);
        assertEquals(0, repository.batchSaveCalls);
    }

    @Test
    public void manualCreationRetriesIdentifierAlreadyInRepository() {
        InMemoryRepository repository =
                new InMemoryRepository();
        repository.put(student(
                "student-existing",
                "000001",
                "Ana"
        ));

        OmrStudentRegistrationService service = service(
                repository,
                "student-existing",
                "student-new"
        );

        OmrStudentProfile created = service.registerOrUpdate(
                "000002",
                "Bruno"
        );

        assertEquals("student-new", created.getStudentId());
        assertEquals(2, repository.generatedIdLookups);
    }

    @Test
    public void csvCreationRetriesIdentifierReservedByExistingStudent() {
        InMemoryRepository repository =
                new InMemoryRepository();
        repository.put(student(
                "student-existing",
                "000001",
                "Ana"
        ));

        OmrStudentRegistrationService service = service(
                repository,
                "student-existing",
                "student-new"
        );

        OmrStudentRegistrationService.ImportSummary summary =
                service.importPreview(preview(
                        "matricula,nome\n000002,Bruno"
                ));

        assertEquals(
                "student-new",
                summary.getStudents().get(0).getStudentId()
        );
        assertEquals(0, repository.generatedIdLookups);
        assertEquals(1, repository.loadAllCalls);
    }

    @Test
    public void exhaustedIdentifierGenerationDoesNotWriteBatch() {
        InMemoryRepository repository =
                new InMemoryRepository();
        OmrStudentRegistrationService service =
                new OmrStudentRegistrationService(
                        repository,
                        () -> " "
                );

        expectIllegalState(() ->
                service.importPreview(preview(
                        "matricula,nome\n000001,Ana"
                ))
        );

        assertEquals(0, repository.batchSaveCalls);
        assertEquals(0, repository.size());
    }

    @Test
    public void importSummaryStudentsAreImmutable() {
        InMemoryRepository repository =
                new InMemoryRepository();
        OmrStudentRegistrationService service = service(
                repository,
                "student-new"
        );

        OmrStudentRegistrationService.ImportSummary summary =
                service.importPreview(preview(
                        "matricula,nome\n000001,Ana"
                ));

        expectUnsupportedOperation(() ->
                summary.getStudents().clear()
        );
        assertEquals(1, summary.getProcessedCount());
    }

    @Test
    public void repositoryBatchFailureIsPropagatedWithoutPartialFakeWrite() {
        InMemoryRepository repository =
                new InMemoryRepository();
        repository.failBatch = true;

        OmrStudentRegistrationService service = service(
                repository,
                "student-1",
                "student-2"
        );

        expectIllegalState(() ->
                service.importPreview(preview(
                        "matricula,nome\n"
                                + "000001,Ana\n"
                                + "000002,Bruno"
                ))
        );

        assertEquals(1, repository.batchSaveCalls);
        assertEquals(0, repository.size());
    }

    private OmrStudentRegistrationService service(
            InMemoryRepository repository,
            String... generatedIds
    ) {
        List<String> ids = Arrays.asList(generatedIds);
        int[] nextIndex = {0};

        return new OmrStudentRegistrationService(
                repository,
                () -> nextIndex[0] < ids.size()
                        ? ids.get(nextIndex[0]++)
                        : " "
        );
    }

    private OmrStudentCsvParser.Preview preview(String csv) {
        OmrStudentCsvParser.Preview preview = parser.parse(csv);
        if (!preview.isReadyToImport()) {
            fail("Prévia inválida: " + preview.getIssues());
        }
        return preview;
    }

    private static OmrStudentProfile student(
            String studentId,
            String registration,
            String name
    ) {
        return new OmrStudentProfile(
                studentId,
                registration,
                name
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

    private static void expectUnsupportedOperation(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    private static final class InMemoryRepository
            implements OmrStudentRepository {

        private final Map<String, OmrStudentProfile> byId =
                new LinkedHashMap<>();

        private int singleSaveCalls;
        private int batchSaveCalls;
        private int loadAllCalls;
        private int registrationLookupCalls;
        private int generatedIdLookups;
        private boolean failBatch;
        private List<OmrStudentProfile> lastBatch =
                Collections.emptyList();

        @Override
        public void save(OmrStudentProfile student) {
            singleSaveCalls++;
            put(student);
        }

        @Override
        public void saveAll(List<OmrStudentProfile> students) {
            batchSaveCalls++;
            lastBatch = new ArrayList<>(students);

            if (failBatch) {
                throw new IllegalStateException(
                        "Falha simulada no lote."
                );
            }

            Map<String, OmrStudentProfile> updated =
                    new LinkedHashMap<>(byId);

            for (OmrStudentProfile student : students) {
                updated.put(student.getStudentId(), student);
            }

            byId.clear();
            byId.putAll(updated);
        }

        @Override
        public List<OmrStudentProfile> loadAll() {
            loadAllCalls++;
            return Collections.unmodifiableList(
                    new ArrayList<>(byId.values())
            );
        }

        @Override
        public OmrStudentProfile findByIdOrNull(String studentId) {
            generatedIdLookups++;
            return byId.get(studentId);
        }

        @Override
        public OmrStudentProfile findByRegistrationOrNull(
                String registration
        ) {
            registrationLookupCalls++;

            if (registration == null) {
                return null;
            }

            for (OmrStudentProfile student : byId.values()) {
                if (student.hasRegistration(registration)) {
                    return student;
                }
            }

            return null;
        }

        private void put(OmrStudentProfile student) {
            byId.put(student.getStudentId(), student);
        }

        private int size() {
            return byId.size();
        }
    }
}
