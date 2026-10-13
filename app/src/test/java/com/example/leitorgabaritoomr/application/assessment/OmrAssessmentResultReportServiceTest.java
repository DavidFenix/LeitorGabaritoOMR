package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.history.OmrGradingHistoryRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentResultReportServiceTest {

    private Memory memory;
    private ApplicationRepository applications;
    private AssessmentRepository assessments;
    private ParticipantRepository participants;
    private AttemptRepository attempts;
    private HistoryRepository histories;
    private OmrAssessmentResultReportService service;

    @Before
    public void setUp() {
        memory = new Memory();
        memory.application = OmrAssessmentResultTestData.application();
        memory.assessment = OmrAssessmentResultTestData.assessment();
        memory.participants.add(OmrAssessmentResultTestData.participant(
                "student-2", "MAT-002", "Bruno", 2
        ));
        memory.participants.add(OmrAssessmentResultTestData.participant(
                "student-1", "MAT-001", "Ana", 1
        ));
        applications = new ApplicationRepository(memory);
        assessments = new AssessmentRepository(memory);
        participants = new ParticipantRepository(memory);
        attempts = new AttemptRepository(memory);
        histories = new HistoryRepository(memory);
        service = new OmrAssessmentResultReportService(
                applications, assessments, participants, attempts, histories
        );
    }

    @Test
    public void rejectsMissingDependency() {
        expectIllegalArgument(() -> new OmrAssessmentResultReportService(
                null, assessments, participants, attempts, histories
        ));
        expectIllegalArgument(() -> new OmrAssessmentResultReportService(
                applications, null, participants, attempts, histories
        ));
        expectIllegalArgument(() -> new OmrAssessmentResultReportService(
                applications, assessments, participants, null, histories
        ));
    }

    @Test
    public void rejectsMissingIdentityOrTimestamp() {
        expectIllegalArgument(() -> service.build(" ", 2_000L));
        expectIllegalArgument(() -> service.build("application-1", 0L));
    }

    @Test
    public void rejectsUnknownApplication() {
        expectIllegalArgument(() -> service.build("unknown", 2_000L));
    }

    @Test
    public void rejectsMissingAssessmentVersion() {
        memory.assessment = null;
        expectIllegalState(() -> service.build("application-1", 2_000L));
    }

    @Test
    public void rejectsApplicationWithoutFrozenRoster() {
        memory.participants.clear();
        expectIllegalState(() -> service.build("application-1", 2_000L));
    }

    @Test
    public void sortsFrozenRosterByPosition() {
        OmrAssessmentResultReport report = service.build(
                " application-1 ", 4_000L
        );
        assertEquals("student-1", report.getRows().get(0)
                .getParticipant().getStudentId());
        assertEquals("student-2", report.getRows().get(1)
                .getParticipant().getStudentId());
        assertEquals("9º A", report.getFrozenClassName());
        assertEquals(4_000L, report.getGeneratedAtEpochMillis());
    }

    @Test
    public void usesHighestRevisionEvenWhenAttemptsAreUnordered() {
        memory.attempts.add(OmrAssessmentResultTestData.attempt(
                "student-1", 2, "history-new", 3_000L
        ));
        memory.attempts.add(OmrAssessmentResultTestData.attempt(
                "student-1", 1, "history-old", 2_000L
        ));
        OmrAssessmentParticipant participant = memory.participants.get(1);
        memory.histories.add(OmrAssessmentResultTestData.history(
                participant, "history-new", 3_000L
        ));

        OmrAssessmentResultReport.Row row = service.build(
                "application-1", 4_000L
        ).getRows().get(0);
        assertEquals(2, row.getRevision());
        assertEquals("history-new", row.getHistoryRecord()
                .getHistoryRecordId());
    }

    @Test
    public void computesSummaryFromLatestResults() {
        OmrAssessmentParticipant ana = memory.participants.get(1);
        memory.attempts.add(OmrAssessmentResultTestData.attempt(
                "student-1", 1, "history-ana", 3_000L
        ));
        memory.histories.add(OmrAssessmentResultTestData.history(
                ana, "history-ana", 3_000L
        ));

        OmrAssessmentResultReport report = service.build(
                "application-1", 4_000L
        );
        assertEquals(2, report.getParticipantCount());
        assertEquals(1, report.getCorrectedCount());
        assertEquals(1, report.getPendingCount());
        assertEquals(0, report.getMissingHistoryCount());
        assertEquals(100.0, report.getAveragePercentage(), 0.0001);
        assertFalse(report.hasDataIntegrityIssue());
    }

    @Test
    public void exposesMissingHistoryWithoutTreatingItAsPending() {
        memory.attempts.add(OmrAssessmentResultTestData.attempt(
                "student-1", 1, "history-lost", 3_000L
        ));
        OmrAssessmentResultReport report = service.build(
                "application-1", 4_000L
        );
        assertEquals(0, report.getCorrectedCount());
        assertEquals(1, report.getPendingCount());
        assertEquals(1, report.getMissingHistoryCount());
        assertTrue(report.hasDataIntegrityIssue());
        assertEquals(OmrAssessmentResultReport.Status.MISSING_HISTORY,
                report.getRows().get(0).getStatus());
    }

    @Test
    public void rejectsHistoryBelongingToAnotherStudentSnapshot() {
        memory.attempts.add(OmrAssessmentResultTestData.attempt(
                "student-1", 1, "history-wrong", 3_000L
        ));
        memory.histories.add(OmrAssessmentResultTestData.history(
                memory.participants.get(0), "history-wrong", 3_000L
        ));
        OmrAssessmentResultReport.Row row = service.build(
                "application-1", 4_000L
        ).getRows().get(0);
        assertEquals(OmrAssessmentResultReport.Status.MISSING_HISTORY,
                row.getStatus());
        assertNull(row.getHistoryRecord());
    }

    @Test
    public void reportRowsAreImmutable() {
        OmrAssessmentResultReport report = service.build(
                "application-1", 4_000L
        );
        try {
            report.getRows().clear();
            fail("A lista deveria ser imutável.");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalStateException.");
        } catch (IllegalStateException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static final class Memory {
        OmrAssessmentApplication application;
        OmrAssessmentDefinition assessment;
        final List<OmrAssessmentParticipant> participants =
                new ArrayList<>();
        final List<OmrAssessmentAttempt> attempts = new ArrayList<>();
        final List<OmrGradingHistoryRecord> histories = new ArrayList<>();
    }

    private static final class ApplicationRepository
            implements OmrAssessmentApplicationRepository {
        private final Memory memory;

        ApplicationRepository(Memory memory) {
            this.memory = memory;
        }

        @Override
        public boolean save(OmrAssessmentApplication value) {
            memory.application = value;
            return true;
        }

        @Override
        public List<OmrAssessmentApplication> loadAll() {
            return memory.application == null ? Collections.emptyList()
                    : Collections.singletonList(memory.application);
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
            return memory.application != null
                    && memory.application.hasApplicationId(id)
                    ? memory.application : null;
        }
    }

    private static final class AssessmentRepository
            implements OmrAssessmentRepository {
        private final Memory memory;

        AssessmentRepository(Memory memory) {
            this.memory = memory;
        }

        @Override
        public boolean save(OmrAssessmentDefinition value) {
            memory.assessment = value;
            return true;
        }

        @Override
        public List<OmrAssessmentDefinition> loadAll() {
            return loadAssessmentAll();
        }

        @Override
        public List<OmrAssessmentDefinition> loadLatestVersions() {
            return loadAssessmentAll();
        }

        private List<OmrAssessmentDefinition> loadAssessmentAll() {
            return memory.assessment == null ? Collections.emptyList()
                    : Collections.singletonList(memory.assessment);
        }

        @Override
        public OmrAssessmentDefinition findOrNull(String id, int version) {
            return memory.assessment != null
                    && memory.assessment.hasIdentity(id, version)
                    ? memory.assessment : null;
        }

        @Override
        public OmrAssessmentDefinition findLatestVersionOrNull(String id) {
            return memory.assessment;
        }
    }

    private static final class ParticipantRepository
            implements OmrAssessmentParticipantRepository {
        private final Memory memory;

        ParticipantRepository(Memory memory) {
            this.memory = memory;
        }

        @Override
        public boolean saveRoster(
                String id, List<OmrAssessmentParticipant> values
        ) {
            memory.participants.addAll(values);
            return true;
        }

        @Override
        public List<OmrAssessmentParticipant> loadForApplication(String id) {
            return new ArrayList<>(memory.participants);
        }

        @Override
        public OmrAssessmentParticipant findOrNull(String id, String student) {
            for (OmrAssessmentParticipant participant : memory.participants) {
                if (participant.belongsToApplication(id)
                        && participant.belongsToStudent(student)) {
                    return participant;
                }
            }
            return null;
        }
    }

    private static final class AttemptRepository
            implements OmrAssessmentAttemptRepository {
        private final Memory memory;

        AttemptRepository(Memory memory) {
            this.memory = memory;
        }

        @Override
        public boolean save(OmrAssessmentAttempt value) {
            memory.attempts.add(value);
            return true;
        }

        @Override
        public List<OmrAssessmentAttempt> loadForApplication(String id) {
            return new ArrayList<>(memory.attempts);
        }

        @Override
        public List<OmrAssessmentAttempt> loadForParticipant(
                String id, String student
        ) {
            List<OmrAssessmentAttempt> result = new ArrayList<>();
            for (OmrAssessmentAttempt attempt : memory.attempts) {
                if (attempt.belongsToParticipant(id, student)) {
                    result.add(attempt);
                }
            }
            return result;
        }

        @Override
        public OmrAssessmentAttempt findLatestForParticipantOrNull(
                String id, String student
        ) {
            OmrAssessmentAttempt latest = null;
            for (OmrAssessmentAttempt attempt
                    : loadForParticipant(id, student)) {
                if (latest == null
                        || attempt.getRevision() > latest.getRevision()) {
                    latest = attempt;
                }
            }
            return latest;
        }

        @Override
        public OmrAssessmentAttempt findByIdOrNull(String id) {
            return null;
        }

        @Override
        public OmrAssessmentAttempt findByHistoryRecordIdOrNull(String id) {
            return null;
        }
    }

    private static final class HistoryRepository
            implements OmrGradingHistoryRepository {
        private final Memory memory;

        HistoryRepository(Memory memory) {
            this.memory = memory;
        }

        @Override
        public boolean save(OmrGradingHistoryRecord value) {
            memory.histories.add(value);
            return true;
        }

        @Override
        public List<OmrGradingHistoryRecord> loadAll() {
            return new ArrayList<>(memory.histories);
        }

        @Override
        public OmrGradingHistoryRecord findByIdOrNull(String id) {
            for (OmrGradingHistoryRecord history : memory.histories) {
                if (history.getHistoryRecordId().equals(id)) {
                    return history;
                }
            }
            return null;
        }

        @Override
        public OmrGradingHistoryRecord findByReadingIdOrNull(String id) {
            return null;
        }

        @Override
        public List<OmrGradingHistoryRecord> loadByStudentId(String id) {
            return Collections.emptyList();
        }
    }
}
