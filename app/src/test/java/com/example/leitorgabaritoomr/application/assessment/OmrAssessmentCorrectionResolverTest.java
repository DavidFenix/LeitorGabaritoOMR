package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentCorrectionResolverTest {

    @Test
    public void rejectsMissingRepositories() {
        Fixture fixture = new Fixture();
        expectIllegalArgument(() -> new OmrAssessmentCorrectionResolver(
                null,
                fixture.assessments,
                fixture.participants,
                fixture.answerKeys
        ));
        expectIllegalArgument(() -> new OmrAssessmentCorrectionResolver(
                fixture.applications,
                null,
                fixture.participants,
                fixture.answerKeys
        ));
        expectIllegalArgument(() -> new OmrAssessmentCorrectionResolver(
                fixture.applications,
                fixture.assessments,
                null,
                fixture.answerKeys
        ));
        expectIllegalArgument(() -> new OmrAssessmentCorrectionResolver(
                fixture.applications,
                fixture.assessments,
                fixture.participants,
                null
        ));
    }

    @Test
    public void rejectsMissingIdentitiesWithoutConsultingRepositories() {
        Fixture fixture = new Fixture();
        OmrAssessmentCorrectionResolver resolver = fixture.resolver();

        expectIllegalArgument(() -> resolver.resolve(null, "student-1"));
        expectIllegalArgument(() -> resolver.resolve(" ", "student-1"));
        expectIllegalArgument(() -> resolver.resolve("application-1", null));
        expectIllegalArgument(() -> resolver.resolve("application-1", " "));
        assertEquals(0, fixture.applications.findCalls);
    }

    @Test
    public void resolvesExactApplicationAssessmentParticipantAndAnswerKey() {
        Fixture fixture = new Fixture();

        OmrAssessmentCorrectionContext context = fixture.resolver().resolve(
                " application-1 ",
                " student-1 "
        );

        assertSame(fixture.application, context.getApplication());
        assertSame(fixture.assessment, context.getAssessment());
        assertSame(fixture.participant, context.getParticipant());
        assertSame(fixture.answerKey, context.getAnswerKey());
        assertEquals("student-1", context.getStudentIdentity().getStudentId());
        assertTrue(context.belongsTo(" application-1 ", " student-1 "));
    }

    @Test
    public void exactStoredAnswerKeyWinsOverActiveSelection() {
        Fixture fixture = new Fixture();
        fixture.answerKeys.active = answerKey("other-key", 8);

        OmrAssessmentCorrectionContext context = fixture.resolver().resolve(
                "application-1",
                "student-1"
        );

        assertSame(fixture.answerKey, context.getAnswerKey());
        assertEquals("key-1", fixture.answerKeys.lastFindId);
        assertEquals(3, fixture.answerKeys.lastFindVersion);
    }

    @Test
    public void rejectsMissingApplication() {
        Fixture fixture = new Fixture();
        fixture.applications.application = null;
        expectIllegalState("aplicação", () -> fixture.resolver().resolve(
                "application-1", "student-1"
        ));
    }

    @Test
    public void rejectsMissingExactAssessmentVersion() {
        Fixture fixture = new Fixture();
        fixture.assessments.assessment = null;
        expectIllegalState("versão exata", () -> fixture.resolver().resolve(
                "application-1", "student-1"
        ));
    }

    @Test
    public void rejectsStudentOutsideFrozenRoster() {
        Fixture fixture = new Fixture();
        fixture.participants.participant = null;
        expectIllegalState("chamada congelada", () -> fixture.resolver().resolve(
                "application-1", "student-1"
        ));
    }

    @Test
    public void rejectsMissingExactAnswerKeyVersion() {
        Fixture fixture = new Fixture();
        fixture.answerKeys.answerKey = null;
        expectIllegalState("gabarito exato", () -> fixture.resolver().resolve(
                "application-1", "student-1"
        ));
    }

    @Test
    public void contextRejectsAssessmentFromAnotherApplication() {
        Fixture fixture = new Fixture();
        fixture.assessments.assessment = new OmrAssessmentDefinition(
                "assessment-other", 2, "Outra", "Matemática",
                "9º ano", "2º bimestre", 2026, "key-1", 3
        );
        expectIllegalArgument(() -> fixture.resolver().resolve(
                "application-1", "student-1"
        ));
    }

    @Test
    public void contextRejectsParticipantFromAnotherApplication() {
        Fixture fixture = new Fixture();
        fixture.participants.participant = new OmrAssessmentParticipant(
                "application-other", "student-1", "MAT-001",
                "Ana", "9º A", 1
        );
        expectIllegalArgument(() -> fixture.resolver().resolve(
                "application-1", "student-1"
        ));
    }

    @Test
    public void contextRejectsAnswerKeyFromAnotherVersion() {
        Fixture fixture = new Fixture();
        fixture.answerKeys.answerKey = answerKey("key-1", 4);
        expectIllegalArgument(() -> fixture.resolver().resolve(
                "application-1", "student-1"
        ));
    }

    @Test
    public void contextIsSerializableAcrossAndroidIntentBoundary()
            throws Exception {
        OmrAssessmentCorrectionContext original =
                new Fixture().resolver().resolve(
                        "application-1",
                        "student-1"
                );
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        OmrAssessmentCorrectionContext restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())
        )) {
            restored = (OmrAssessmentCorrectionContext) input.readObject();
        }

        assertNotNull(restored);
        assertEquals("application-1",
                restored.getApplication().getApplicationId());
        assertEquals("student-1",
                restored.getParticipant().getStudentId());
        assertEquals("key-1", restored.getAnswerKey().getId());
        assertEquals(3, restored.getAnswerKey().getVersion());
    }

    private static OmrAnswerKeyDefinition answerKey(String id, int version) {
        return new OmrAnswerKeyDefinition(
                id,
                version,
                "Gabarito",
                "omr-flex-ad-q001-r001",
                3,
                Collections.singletonList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-001",
                                "question-001-option-01",
                                1.0
                        )
                )
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static void expectIllegalState(
            String messagePart,
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada IllegalStateException.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(messagePart));
        }
    }

    private static final class Fixture {
        private final OmrAssessmentApplication application =
                new OmrAssessmentApplication(
                        "application-1", "assessment-1", 2,
                        "class-1", 1_000L
                );
        private final OmrAssessmentDefinition assessment =
                new OmrAssessmentDefinition(
                        "assessment-1", 2, "Avaliação", "Matemática",
                        "9º ano", "2º bimestre", 2026, "key-1", 3
                );
        private final OmrAssessmentParticipant participant =
                new OmrAssessmentParticipant(
                        "application-1", "student-1", "MAT-001",
                        "Ana", "9º A", 1
                );
        private final OmrAnswerKeyDefinition answerKey = answerKey("key-1", 3);

        private final FakeApplications applications =
                new FakeApplications(application);
        private final FakeAssessments assessments =
                new FakeAssessments(assessment);
        private final FakeParticipants participants =
                new FakeParticipants(participant);
        private final FakeAnswerKeys answerKeys = new FakeAnswerKeys(answerKey);

        private OmrAssessmentCorrectionResolver resolver() {
            return new OmrAssessmentCorrectionResolver(
                    applications,
                    assessments,
                    participants,
                    answerKeys
            );
        }
    }

    private static final class FakeApplications
            implements OmrAssessmentApplicationRepository {
        private OmrAssessmentApplication application;
        private int findCalls;

        private FakeApplications(OmrAssessmentApplication application) {
            this.application = application;
        }

        @Override
        public boolean save(OmrAssessmentApplication value) {
            application = value;
            return true;
        }

        @Override
        public List<OmrAssessmentApplication> loadAll() {
            return application == null
                    ? Collections.emptyList()
                    : Collections.singletonList(application);
        }

        @Override
        public List<OmrAssessmentApplication> loadForClassGroup(String id) {
            return loadAll();
        }

        @Override
        public List<OmrAssessmentApplication> loadForAssessment(
                String id, int version
        ) {
            return loadAll();
        }

        @Override
        public OmrAssessmentApplication findByIdOrNull(String id) {
            findCalls++;
            return application;
        }
    }

    private static final class FakeAssessments
            implements OmrAssessmentRepository {
        private OmrAssessmentDefinition assessment;

        private FakeAssessments(OmrAssessmentDefinition assessment) {
            this.assessment = assessment;
        }

        @Override
        public boolean save(OmrAssessmentDefinition value) {
            assessment = value;
            return true;
        }

        @Override
        public List<OmrAssessmentDefinition> loadAll() {
            return assessment == null
                    ? Collections.emptyList()
                    : Collections.singletonList(assessment);
        }

        @Override
        public List<OmrAssessmentDefinition> loadLatestVersions() {
            return loadAll();
        }

        @Override
        public OmrAssessmentDefinition findOrNull(String id, int version) {
            return assessment;
        }

        @Override
        public OmrAssessmentDefinition findLatestVersionOrNull(String id) {
            return assessment;
        }
    }

    private static final class FakeParticipants
            implements OmrAssessmentParticipantRepository {
        private OmrAssessmentParticipant participant;

        private FakeParticipants(OmrAssessmentParticipant participant) {
            this.participant = participant;
        }

        @Override
        public boolean saveRoster(
                String id, List<OmrAssessmentParticipant> values
        ) {
            participant = values.isEmpty() ? null : values.get(0);
            return true;
        }

        @Override
        public List<OmrAssessmentParticipant> loadForApplication(String id) {
            return participant == null
                    ? Collections.emptyList()
                    : Collections.singletonList(participant);
        }

        @Override
        public OmrAssessmentParticipant findOrNull(String appId, String studentId) {
            return participant;
        }
    }

    private static final class FakeAnswerKeys
            implements OmrAnswerKeyRepository {
        private OmrAnswerKeyDefinition answerKey;
        private OmrAnswerKeyDefinition active;
        private String lastFindId;
        private int lastFindVersion;

        private FakeAnswerKeys(OmrAnswerKeyDefinition answerKey) {
            this.answerKey = answerKey;
        }

        @Override
        public void save(OmrAnswerKeyDefinition value) {
            answerKey = value;
        }

        @Override
        public List<OmrAnswerKeyDefinition> loadAll() {
            return answerKey == null
                    ? Collections.emptyList()
                    : Collections.singletonList(answerKey);
        }

        @Override
        public OmrAnswerKeyDefinition findOrNull(String id, int version) {
            lastFindId = id;
            lastFindVersion = version;
            return answerKey;
        }

        @Override
        public void selectActive(String id, int version) {
            active = answerKey;
        }

        @Override
        public boolean delete(String id, int version) {
            answerKey = null;
            return true;
        }

        @Override
        public void saveActive(OmrAnswerKeyDefinition value) {
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
