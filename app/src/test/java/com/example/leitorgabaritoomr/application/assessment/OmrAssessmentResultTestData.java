package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockBreakdown;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockScorer;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentResultTestData {

    private OmrAssessmentResultTestData() {
    }

    public static OmrAssessmentApplication application() {
        return new OmrAssessmentApplication(
                "application-1", "assessment-1", 2,
                "class-1", 1_000L
        );
    }

    public static OmrAssessmentApplication structuredApplication() {
        return new OmrAssessmentApplication(
                "application-1", "assessment-1", 2,
                "class-1", 1_000L,
                new OmrAssessmentStructure(
                        "assessment-1", 2, 2,
                        Arrays.asList(
                                new OmrAssessmentBlock(
                                        "qui", 1, "Ciências da Natureza",
                                        "QUI", "Química", 1, 1,
                                        OmrProportionalScoringPolicy
                                                .correctQuestionsToTenV1()
                                ),
                                new OmrAssessmentBlock(
                                        "fis", 2, "Ciências da Natureza",
                                        "FIS", "Física", 2, 1,
                                        OmrProportionalScoringPolicy
                                                .correctQuestionsToTenV1()
                                )
                        )
                )
        );
    }

    public static OmrAssessmentDefinition assessment() {
        return new OmrAssessmentDefinition(
                "assessment-1", 2, "Avaliação & revisão",
                "Matemática", "9º ano", "2º bimestre",
                2026, "key-1", 3
        );
    }

    public static OmrAssessmentParticipant participant(
            String studentId,
            String registration,
            String name,
            int position
    ) {
        return new OmrAssessmentParticipant(
                "application-1", studentId, registration,
                name, "9º A", position
        );
    }

    public static OmrAssessmentAttempt attempt(
            String studentId,
            int revision,
            String historyId,
            long recordedAt
    ) {
        return new OmrAssessmentAttempt(
                "attempt-" + studentId + "-" + revision,
                "application-1", studentId, revision,
                historyId, recordedAt
        );
    }

    public static OmrGradingHistoryRecord history(
            OmrAssessmentParticipant participant,
            String historyId,
            long storedAt
    ) {
        OmrQuestionResult question = new OmrQuestionResult(
                1,
                "question-001",
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(new OmrQuestionResult.Option(
                        "question-001-option-01", "A"
                )),
                0.99
        );
        OmrReadingResult reading = new OmrReadingResult(
                "reading-" + historyId,
                storedAt - 100L,
                "omr-flex-ad-q001-r001",
                3,
                "Folha flexível",
                Collections.singletonList(question)
        );
        OmrAnswerKeyDefinition key = new OmrAnswerKeyDefinition(
                "key-1",
                3,
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
        OmrGradingResult result = new OmrReadingGrader().grade(reading, key);
        OmrStudentIdentity student = participant.toStudentIdentity();
        return new OmrGradingHistoryRecord(
                historyId, storedAt, student, result
        );
    }

    public static OmrGradingHistoryRecord structuredHistory(
            OmrAssessmentParticipant participant,
            String historyId,
            long storedAt,
            boolean firstAnswerCorrect
    ) {
        OmrQuestionResult first = new OmrQuestionResult(
                1,
                "question-001",
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(new OmrQuestionResult.Option(
                        firstAnswerCorrect
                                ? "question-001-option-01"
                                : "question-001-option-02",
                        firstAnswerCorrect ? "A" : "B"
                )),
                0.99
        );
        OmrQuestionResult second = new OmrQuestionResult(
                2,
                "question-002",
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(new OmrQuestionResult.Option(
                        "question-002-option-02", "B"
                )),
                0.98
        );
        OmrReadingResult reading = new OmrReadingResult(
                "reading-" + historyId,
                storedAt - 100L,
                "omr-flex-ad-q002-r001",
                3,
                "Folha flexível",
                Arrays.asList(first, second)
        );
        OmrAnswerKeyDefinition key = new OmrAnswerKeyDefinition(
                "key-1",
                3,
                "Gabarito",
                "omr-flex-ad-q002-r001",
                3,
                Arrays.asList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-001",
                                "question-001-option-01",
                                1.0
                        ),
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-002",
                                "question-002-option-01",
                                1.0
                        )
                )
        );
        OmrGradingResult result = new OmrReadingGrader().grade(reading, key);
        return new OmrGradingHistoryRecord(
                historyId,
                storedAt,
                participant.toStudentIdentity(),
                result
        );
    }

    public static OmrAssessmentResultReport report() {
        OmrAssessmentParticipant ana = participant(
                "student-1", "MAT-001", "Ana Souza", 1
        );
        OmrAssessmentAttempt anaAttempt = attempt(
                "student-1", 2, "history-ana", 3_000L
        );
        OmrAssessmentParticipant bruno = participant(
                "student-2", "MAT-002", "Bruno Lima", 2
        );
        OmrAssessmentParticipant clara = participant(
                "student-3", "MAT-003", "Clara Alves", 3
        );
        return new OmrAssessmentResultReport(
                application(),
                assessment(),
                "9º A",
                4_000L,
                Arrays.asList(
                        new OmrAssessmentResultReport.Row(
                                ana,
                                anaAttempt,
                                history(ana, "history-ana", 3_000L)
                        ),
                        new OmrAssessmentResultReport.Row(
                                bruno, null, null
                        ),
                        new OmrAssessmentResultReport.Row(
                                clara,
                                attempt(
                                        "student-3", 1,
                                        "history-missing", 3_500L
                                ),
                                null
                        )
                )
        );
    }

    public static OmrAssessmentResultReport structuredReport() {
        return structuredReport(true);
    }

    public static OmrAssessmentResultReport structuredZeroReport() {
        return structuredReport(false);
    }

    private static OmrAssessmentResultReport structuredReport(
            boolean firstAnswerCorrect
    ) {
        OmrAssessmentApplication application = structuredApplication();
        OmrAssessmentParticipant participant = participant(
                "student-1", "MAT-001", "Ana Souza", 1
        );
        OmrGradingHistoryRecord history = structuredHistory(
                participant,
                "history-structured",
                3_000L,
                firstAnswerCorrect
        );
        OmrAssessmentAttempt attempt = new OmrAssessmentAttempt(
                "attempt-structured",
                application.getApplicationId(),
                participant.getStudentId(),
                1,
                history.getHistoryRecordId(),
                history.getStoredAtEpochMillis(),
                application.getFrozenStructureOrNull()
        );
        OmrAssessmentBlockBreakdown breakdown =
                new OmrAssessmentBlockScorer().calculate(
                        application.getFrozenStructureOrNull(),
                        history.getGradingResult()
                );
        return new OmrAssessmentResultReport(
                application,
                assessment(),
                "9º A",
                4_000L,
                Collections.singletonList(
                        new OmrAssessmentResultReport.Row(
                                participant,
                                attempt,
                                history,
                                breakdown
                        )
                )
        );
    }

    public static OmrAssessmentResultReport
    structuredInconsistentReport() {
        OmrAssessmentResultReport source = structuredReport();
        OmrAssessmentResultReport.Row original = source.getRows().get(0);
        OmrAssessmentResultReport.Row inconsistent =
                new OmrAssessmentResultReport.Row(
                        original.getParticipant(),
                        original.getLatestAttempt(),
                        original.getHistoryRecord(),
                        null,
                        OmrAssessmentResultReport.DetailStatus.INCONSISTENT
                );
        return new OmrAssessmentResultReport(
                source.getApplication(),
                source.getAssessment(),
                source.getFrozenClassName(),
                source.getGeneratedAtEpochMillis(),
                Collections.singletonList(inconsistent)
        );
    }

    public static OmrAssessmentResultReport structuredReportWithPending() {
        OmrAssessmentResultReport corrected = structuredReport();
        List<OmrAssessmentResultReport.Row> rows = new ArrayList<>(
                corrected.getRows()
        );
        rows.add(new OmrAssessmentResultReport.Row(
                participant(
                        "student-2",
                        "MAT-002",
                        "Bruno Lima",
                        2
                ),
                null,
                null
        ));
        return new OmrAssessmentResultReport(
                corrected.getApplication(),
                corrected.getAssessment(),
                corrected.getFrozenClassName(),
                corrected.getGeneratedAtEpochMillis(),
                rows
        );
    }
}
