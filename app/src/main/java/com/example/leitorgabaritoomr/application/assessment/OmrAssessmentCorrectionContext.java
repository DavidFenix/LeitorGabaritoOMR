package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.io.Serializable;

/**
 * Fotografia completa e validada de uma correção vinculada a uma aplicação.
 *
 * O objeto atravessa as telas de chamada, captura e resultado sem depender do
 * gabarito globalmente ativo. Assim, a correção sempre usa a versão exata que
 * pertence à prova aplicada e a identidade congelada do participante.
 */
public final class OmrAssessmentCorrectionContext
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final OmrAssessmentApplication application;
    private final OmrAssessmentDefinition assessment;
    private final OmrAssessmentParticipant participant;
    private final OmrAnswerKeyDefinition answerKey;

    public OmrAssessmentCorrectionContext(
            OmrAssessmentApplication application,
            OmrAssessmentDefinition assessment,
            OmrAssessmentParticipant participant,
            OmrAnswerKeyDefinition answerKey
    ) {
        if (application == null
                || assessment == null
                || participant == null
                || answerKey == null) {

            throw new IllegalArgumentException(
                    "Aplicação, prova, participante e gabarito são obrigatórios."
            );
        }

        if (!application.usesAssessment(
                assessment.getAssessmentId(),
                assessment.getVersion()
        )) {
            throw new IllegalArgumentException(
                    "A prova não corresponde à aplicação."
            );
        }

        if (!participant.belongsToApplication(
                application.getApplicationId()
        )) {
            throw new IllegalArgumentException(
                    "O participante não pertence à aplicação."
            );
        }

        if (!assessment.usesAnswerKey(
                answerKey.getId(),
                answerKey.getVersion()
        )) {
            throw new IllegalArgumentException(
                    "O gabarito não corresponde à versão exata da prova."
            );
        }

        this.application = application;
        this.assessment = assessment;
        this.participant = participant;
        this.answerKey = answerKey;
    }

    public OmrAssessmentApplication getApplication() {
        return application;
    }

    public OmrAssessmentDefinition getAssessment() {
        return assessment;
    }

    public OmrAssessmentParticipant getParticipant() {
        return participant;
    }

    public OmrAnswerKeyDefinition getAnswerKey() {
        return answerKey;
    }

    public OmrStudentIdentity getStudentIdentity() {
        return participant.toStudentIdentity();
    }

    public boolean belongsTo(
            String applicationId,
            String studentId
    ) {
        return application.hasApplicationId(applicationId)
                && participant.belongsToStudent(studentId);
    }
}
