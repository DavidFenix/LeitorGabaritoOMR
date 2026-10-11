package com.example.leitorgabaritoomr.application.classroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.student.OmrStudentRepository;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OmrClassRosterRegistrationServiceTest {

    private final OmrClassRosterCsvParser parser =
            new OmrClassRosterCsvParser();

    @Test
    public void rejectsMissingDependenciesAndGenerators() {
        Repositories r = new Repositories();
        expectArgument(() -> new OmrClassRosterRegistrationService(
                null, r.enrollments, r.students, r.batch
        ));
        expectArgument(() -> new OmrClassRosterRegistrationService(
                r.classes, null, r.students, r.batch
        ));
        expectArgument(() -> new OmrClassRosterRegistrationService(
                r.classes, r.enrollments, null, r.batch
        ));
        expectArgument(() -> new OmrClassRosterRegistrationService(
                r.classes, r.enrollments, r.students, null
        ));
        expectArgument(() -> new OmrClassRosterRegistrationService(
                r.classes, r.enrollments, r.students, r.batch,
                null, ids("student")
        ));
    }

    @Test
    public void manualClassCreationNormalizesAndPersists() {
        Repositories r = new Repositories();
        OmrClassRosterRegistrationService service = service(
                r, ids("class-new"), ids("unused")
        );

        OmrClassGroup created = service.registerOrUpdateClass(
                " 7º   A ", " Fundamental   II ", 2026
        );

        assertEquals("class-new", created.getClassGroupId());
        assertEquals("7º A", created.getName());
        assertEquals("Fundamental II", created.getGradeLevel());
        assertEquals(1, r.classes.saveCalls);
    }

    @Test
    public void naturalClassUpdatePreservesId() {
        Repositories r = new Repositories();
        r.classes.put(group(
                "class-existing", "Sétimo A", "Antiga", 2026
        ));
        OmrClassRosterRegistrationService service = service(
                r, ids("unused"), ids("unused")
        );

        OmrClassGroup updated = service.registerOrUpdateClass(
                " setimo  a ", "Atual", 2026
        );

        assertEquals("class-existing", updated.getClassGroupId());
        assertEquals("Atual", updated.getGradeLevel());
        assertEquals(1, r.classes.size());
    }

    @Test
    public void explicitClassEditPreservesIdAndRejectsCollision() {
        Repositories r = new Repositories();
        r.classes.put(group("class-a", "A", "Etapa", 2026));
        r.classes.put(group("class-b", "B", "Etapa", 2026));
        OmrClassRosterRegistrationService service = service(
                r, ids("unused"), ids("unused")
        );

        OmrClassGroup edited = service.registerOrUpdateClass(
                "class-a", "A editada", "Nova", 2026
        );
        assertEquals("class-a", edited.getClassGroupId());

        expectArgument(() -> service.registerOrUpdateClass(
                "class-a", "B", "Etapa", 2026
        ));
        expectArgument(() -> service.registerOrUpdateClass(
                "missing", "C", "Etapa", 2026
        ));
    }

    @Test
    public void invalidManualClassDoesNotWrite() {
        Repositories r = new Repositories();
        OmrClassRosterRegistrationService service = service(
                r, ids("class"), ids("student")
        );

        expectArgument(() -> service.registerOrUpdateClass(
                " ", "Etapa", 2026
        ));
        expectArgument(() -> service.registerOrUpdateClass(
                "A", " ", 2026
        ));
        expectArgument(() -> service.registerOrUpdateClass(
                "A", "Etapa", 0
        ));
        assertEquals(0, r.classes.saveCalls);
    }

    @Test
    public void classCreationRetriesReservedId() {
        Repositories r = new Repositories();
        r.classes.put(group("used", "A", "Etapa", 2026));
        OmrClassRosterRegistrationService service = service(
                r, ids("used", "new"), ids("unused")
        );

        assertEquals(
                "new",
                service.registerOrUpdateClass(
                        "B", "Etapa", 2026
                ).getClassGroupId()
        );
    }

    @Test
    public void manualEnrollmentValidatesReferences() {
        Repositories r = baseSchool();
        OmrClassRosterRegistrationService service = service(
                r, ids("unused"), ids("unused")
        );

        OmrClassEnrollment created = service.enrollStudent(
                "class-a", "student-a", 1
        );
        assertEquals(1, created.getRosterPosition());
        assertEquals(1, r.enrollments.saveCalls);

        expectArgument(() -> service.enrollStudent(
                "missing", "student-a", 2
        ));
        expectArgument(() -> service.enrollStudent(
                "class-a", "missing", 2
        ));
        expectArgument(() -> service.enrollStudent(
                "class-a", "student-a", 0
        ));
    }

    @Test
    public void manualEnrollmentRejectsOccupiedPosition() {
        Repositories r = baseSchool();
        r.enrollments.put(link("class-a", "student-a", 1));
        OmrClassRosterRegistrationService service = service(
                r, ids("unused"), ids("unused")
        );

        expectArgument(() -> service.enrollStudent(
                "class-a", "student-b", 1
        ));
        assertEquals(0, r.enrollments.saveCalls);
    }

    @Test
    public void studentCannotJoinTwoClassesSameYear() {
        Repositories r = baseSchool();
        r.classes.put(group("class-b", "B", "Etapa", 2026));
        r.enrollments.put(link("class-a", "student-a", 1));
        OmrClassRosterRegistrationService service = service(
                r, ids("unused"), ids("unused")
        );

        expectArgument(() -> service.enrollStudent(
                "class-b", "student-a", 1
        ));
    }

    @Test
    public void studentMayJoinClassesInDifferentYears() {
        Repositories r = baseSchool();
        r.classes.put(group("class-next", "B", "Etapa", 2027));
        r.enrollments.put(link("class-a", "student-a", 1));
        OmrClassRosterRegistrationService service = service(
                r, ids("unused"), ids("unused")
        );

        assertEquals(
                "class-next",
                service.enrollStudent(
                        "class-next", "student-a", 2
                ).getClassGroupId()
        );
    }

    @Test
    public void csvImportCreatesAllEntitiesInOneBatch() {
        Repositories r = new Repositories();
        OmrClassRosterRegistrationService service = service(
                r,
                ids("class-a", "class-b"),
                ids("student-a", "student-b", "student-c")
        );

        OmrClassRosterRegistrationService.ImportSummary summary =
                service.importPreview(preview(
                        row("A", "Etapa 1", 2026, "001", "Ana", 1)
                                + "\n" + row(
                                "A", "Etapa 1", 2026, "002", "Bruno", 2
                        ) + "\n" + row(
                                "B", "Etapa 2", 2026, "003", "Carla", 1
                        )
                ));

        assertEquals(2, summary.getCreatedClassCount());
        assertEquals(3, summary.getCreatedStudentCount());
        assertEquals(2, summary.getProcessedClassCount());
        assertEquals(3, summary.getEnrollmentCount());
        assertEquals(1, r.batch.calls);
        assertEquals(1, r.classes.loadAllCalls);
        assertEquals(1, r.students.loadAllCalls);
    }

    @Test
    public void csvUpdatePreservesStableIdsAndChangesMetadata() {
        Repositories r = new Repositories();
        r.classes.put(group(
                "class-existing", "Sétimo A", "Antiga", 2026
        ));
        r.students.put(student(
                "student-existing", "001", "Nome antigo"
        ));
        OmrClassRosterRegistrationService service = service(
                r, ids("unused"), ids("unused")
        );

        OmrClassRosterRegistrationService.ImportSummary summary =
                service.importPreview(preview(row(
                        "setimo a", "Atual", 2026,
                        "001", "Ana atualizada", 4
                )));

        assertEquals(1, summary.getUpdatedClassCount());
        assertEquals(1, summary.getUpdatedStudentCount());
        assertEquals(
                "class-existing",
                summary.getClassGroups().get(0).getClassGroupId()
        );
        assertEquals(
                "student-existing",
                summary.getStudents().get(0).getStudentId()
        );
        assertEquals(
                "Ana atualizada",
                r.students.findByIdOrNull("student-existing").getName()
        );
    }

    @Test
    public void repeatedImportIsIdempotent() {
        Repositories r = new Repositories();
        OmrClassRosterRegistrationService service = service(
                r, ids("class-a", "unused"),
                ids("student-a", "unused")
        );
        OmrClassRosterCsvParser.Preview preview = preview(row(
                "A", "Etapa", 2026, "001", "Ana", 1
        ));

        OmrClassRosterRegistrationService.ImportSummary first =
                service.importPreview(preview);
        OmrClassRosterRegistrationService.ImportSummary second =
                service.importPreview(preview);

        assertEquals(
                first.getClassGroups().get(0).getClassGroupId(),
                second.getClassGroups().get(0).getClassGroupId()
        );
        assertEquals(
                first.getStudents().get(0).getStudentId(),
                second.getStudents().get(0).getStudentId()
        );
        assertEquals(0, second.getCreatedClassCount());
        assertEquals(1, r.classes.size());
        assertEquals(1, r.students.size());
        assertEquals(1, r.enrollments.size());
    }

    @Test
    public void reimportReplacesOnlyIncludedClassRoster() {
        Repositories r = new Repositories();
        r.classes.put(group("class-a", "A", "Etapa", 2026));
        r.classes.put(group("class-b", "B", "Etapa", 2026));
        r.students.put(student("old-a", "000", "Removido"));
        r.students.put(student("student-b", "002", "Bruno"));
        r.enrollments.put(link("class-a", "old-a", 1));
        r.enrollments.put(link("class-b", "student-b", 1));
        OmrClassRosterRegistrationService service = service(
                r, ids("unused"), ids("student-new")
        );

        service.importPreview(preview(row(
                "A", "Etapa", 2026, "001", "Ana", 7
        )));

        assertNull(r.enrollments.findOrNull("class-a", "old-a"));
        assertNotNull(r.enrollments.findOrNull(
                "class-a", "student-new"
        ));
        assertNotNull(r.enrollments.findOrNull(
                "class-b", "student-b"
        ));
        assertNotNull(r.students.findByIdOrNull("old-a"));
    }

    @Test
    public void invalidPreviewDoesNotReadOrWrite() {
        Repositories r = new Repositories();
        OmrClassRosterRegistrationService service = service(
                r, ids("class"), ids("student")
        );

        expectArgument(() -> service.importPreview(null));
        expectArgument(() -> service.importPreview(parser.parse(
                header() + "\nA,Etapa,0,001,Ana,1"
        )));
        assertEquals(0, r.classes.loadAllCalls);
        assertEquals(0, r.students.loadAllCalls);
        assertEquals(0, r.batch.calls);
    }

    @Test
    public void importRetriesReservedIdsAndExhaustionWritesNothing() {
        Repositories r = new Repositories();
        r.classes.put(group("used-class", "Old", "Etapa", 2025));
        r.students.put(student("used-student", "999", "Old"));
        OmrClassRosterRegistrationService service = service(
                r,
                ids("used-class", "new-class"),
                ids("used-student", "new-student")
        );

        OmrClassRosterRegistrationService.ImportSummary summary =
                service.importPreview(preview(row(
                        "A", "Etapa", 2026, "001", "Ana", 1
                )));
        assertEquals(
                "new-class",
                summary.getClassGroups().get(0).getClassGroupId()
        );
        assertEquals(
                "new-student",
                summary.getStudents().get(0).getStudentId()
        );

        Repositories failed = new Repositories();
        OmrClassRosterRegistrationService failing = service(
                failed, ids(" "), ids("student")
        );
        expectState(() -> failing.importPreview(preview(row(
                "A", "Etapa", 2026, "001", "Ana", 1
        ))));
        assertEquals(0, failed.batch.calls);
    }

    @Test
    public void summaryIsImmutableAndBatchFailureDoesNotFakeWrite() {
        Repositories r = new Repositories();
        OmrClassRosterRegistrationService service = service(
                r, ids("class"), ids("student")
        );
        OmrClassRosterRegistrationService.ImportSummary summary =
                service.importPreview(preview(row(
                        "A", "Etapa", 2026, "001", "Ana", 1
                )));
        expectUnsupported(() -> summary.getClassGroups().clear());
        expectUnsupported(() -> summary.getStudents().clear());
        expectUnsupported(() -> summary.getEnrollments().clear());

        Repositories failed = new Repositories();
        failed.batch.fail = true;
        OmrClassRosterRegistrationService failing = service(
                failed, ids("class"), ids("student")
        );
        expectState(() -> failing.importPreview(preview(row(
                "A", "Etapa", 2026, "001", "Ana", 1
        ))));
        assertEquals(0, failed.classes.size());
        assertEquals(0, failed.students.size());
    }

    private OmrClassRosterRegistrationService service(
            Repositories r,
            TestIds classIds,
            TestIds studentIds
    ) {
        return new OmrClassRosterRegistrationService(
                r.classes,
                r.enrollments,
                r.students,
                r.batch,
                classIds,
                studentIds
        );
    }

    private OmrClassRosterCsvParser.Preview preview(String body) {
        OmrClassRosterCsvParser.Preview preview = parser.parse(
                header() + "\n" + body
        );
        if (!preview.isReadyToImport()) {
            fail("Prévia inválida: " + preview.getIssues());
        }
        return preview;
    }

    private static TestIds ids(String... values) {
        return new TestIds(values);
    }

    private static String header() {
        return "turma,etapa,ano_letivo,matricula,nome,posicao";
    }

    private static String row(
            String className,
            String grade,
            int year,
            String registration,
            String name,
            int position
    ) {
        return className + "," + grade + "," + year + ","
                + registration + "," + name + "," + position;
    }

    private static Repositories baseSchool() {
        Repositories r = new Repositories();
        r.classes.put(group("class-a", "A", "Etapa", 2026));
        r.students.put(student("student-a", "001", "Ana"));
        r.students.put(student("student-b", "002", "Bruno"));
        return r;
    }

    private static OmrClassGroup group(
            String id, String name, String grade, int year
    ) {
        return new OmrClassGroup(id, name, grade, year);
    }

    private static OmrStudentProfile student(
            String id, String registration, String name
    ) {
        return new OmrStudentProfile(id, registration, name);
    }

    private static OmrClassEnrollment link(
            String classId, String studentId, int position
    ) {
        return new OmrClassEnrollment(classId, studentId, position);
    }

    private static void expectArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private static void expectState(Runnable action) {
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

    private static final class TestIds
            implements OmrClassRosterRegistrationService.ClassGroupIdGenerator,
            OmrClassRosterRegistrationService.StudentIdGenerator {
        private final List<String> values;
        private int index;

        private TestIds(String... values) {
            this.values = Arrays.asList(values);
        }

        @Override
        public String createClassGroupId() { return next(); }

        @Override
        public String createStudentId() { return next(); }

        private String next() {
            return index < values.size()
                    ? values.get(index++)
                    : values.get(values.size() - 1);
        }
    }

    private static final class Repositories {
        private final Classes classes = new Classes();
        private final Enrollments enrollments = new Enrollments();
        private final Students students = new Students();
        private final Batch batch = new Batch(classes, enrollments, students);
    }

    private static final class Classes implements OmrClassGroupRepository {
        private final Map<String, OmrClassGroup> values =
                new LinkedHashMap<>();
        private int saveCalls;
        private int loadAllCalls;

        @Override
        public void save(OmrClassGroup value) {
            saveCalls++;
            put(value);
        }

        @Override
        public List<OmrClassGroup> loadAll() {
            loadAllCalls++;
            return Collections.unmodifiableList(
                    new ArrayList<>(values.values())
            );
        }

        @Override
        public List<OmrClassGroup> loadForSchoolYear(int year) {
            List<OmrClassGroup> result = new ArrayList<>();
            for (OmrClassGroup value : values.values()) {
                if (value.getSchoolYear() == year) {
                    result.add(value);
                }
            }
            return result;
        }

        @Override
        public OmrClassGroup findByIdOrNull(String id) {
            return id == null ? null : values.get(id.trim());
        }

        private void put(OmrClassGroup value) {
            values.put(value.getClassGroupId(), value);
        }

        private int size() { return values.size(); }
    }

    private static final class Students implements OmrStudentRepository {
        private final Map<String, OmrStudentProfile> values =
                new LinkedHashMap<>();
        private int loadAllCalls;

        @Override
        public void save(OmrStudentProfile value) { put(value); }

        @Override
        public void saveAll(List<OmrStudentProfile> values) {
            for (OmrStudentProfile value : values) {
                put(value);
            }
        }

        @Override
        public List<OmrStudentProfile> loadAll() {
            loadAllCalls++;
            return Collections.unmodifiableList(
                    new ArrayList<>(values.values())
            );
        }

        @Override
        public OmrStudentProfile findByIdOrNull(String id) {
            return id == null ? null : values.get(id.trim());
        }

        @Override
        public OmrStudentProfile findByRegistrationOrNull(String registration) {
            for (OmrStudentProfile value : values.values()) {
                if (value.hasRegistration(registration)) {
                    return value;
                }
            }
            return null;
        }

        private void put(OmrStudentProfile value) {
            values.put(value.getStudentId(), value);
        }

        private int size() { return values.size(); }
    }

    private static final class Enrollments
            implements OmrClassEnrollmentRepository {
        private final Map<String, OmrClassEnrollment> values =
                new LinkedHashMap<>();
        private int saveCalls;

        @Override
        public void save(OmrClassEnrollment value) {
            saveCalls++;
            put(value);
        }

        @Override
        public List<OmrClassEnrollment> loadForClassGroup(String classId) {
            List<OmrClassEnrollment> result = new ArrayList<>();
            for (OmrClassEnrollment value : values.values()) {
                if (value.belongsToClassGroup(classId)) {
                    result.add(value);
                }
            }
            return result;
        }

        @Override
        public List<OmrClassEnrollment> loadForStudent(String studentId) {
            List<OmrClassEnrollment> result = new ArrayList<>();
            for (OmrClassEnrollment value : values.values()) {
                if (value.belongsToStudent(studentId)) {
                    result.add(value);
                }
            }
            return result;
        }

        @Override
        public OmrClassEnrollment findOrNull(
                String classId,
                String studentId
        ) {
            return values.get(key(classId, studentId));
        }

        private void put(OmrClassEnrollment value) {
            values.put(key(
                    value.getClassGroupId(), value.getStudentId()
            ), value);
        }

        private void replace(
                List<OmrClassGroup> classes,
                List<OmrClassEnrollment> replacements
        ) {
            for (OmrClassGroup classGroup : classes) {
                List<String> remove = new ArrayList<>();
                for (Map.Entry<String, OmrClassEnrollment> entry
                        : values.entrySet()) {
                    if (entry.getValue().belongsToClassGroup(
                            classGroup.getClassGroupId()
                    )) {
                        remove.add(entry.getKey());
                    }
                }
                for (String key : remove) {
                    values.remove(key);
                }
            }
            for (OmrClassEnrollment value : replacements) {
                put(value);
            }
        }

        private int size() { return values.size(); }

        private static String key(String classId, String studentId) {
            return classId + "\u0000" + studentId;
        }
    }

    private static final class Batch implements OmrClassRosterBatchRepository {
        private final Classes classes;
        private final Enrollments enrollments;
        private final Students students;
        private int calls;
        private boolean fail;

        private Batch(
                Classes classes,
                Enrollments enrollments,
                Students students
        ) {
            this.classes = classes;
            this.enrollments = enrollments;
            this.students = students;
        }

        @Override
        public void replaceRosters(
                List<OmrClassGroup> classGroups,
                List<OmrStudentProfile> studentProfiles,
                List<OmrClassEnrollment> links
        ) {
            calls++;
            if (fail) {
                throw new IllegalStateException("falha simulada");
            }
            for (OmrClassGroup value : classGroups) {
                classes.put(value);
            }
            for (OmrStudentProfile value : studentProfiles) {
                students.put(value);
            }
            enrollments.replace(classGroups, links);
        }
    }
}
