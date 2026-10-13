package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.util.Arrays;
import java.util.Collections;

public final class OmrAssessmentResultTestData {

    private OmrAssessmentResultTestData() {
    }

    public static OmrAssessmentApplication application() {
        return new OmrAssessmentApplication(
                "application-1", "assessment-1", 2,
                "class-1", 1_000L
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
}
