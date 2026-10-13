package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

/** Resolve todos os dados imutáveis necessários para corrigir um participante. */
public final class OmrAssessmentCorrectionResolver {

    private final OmrAssessmentApplicationRepository applicationRepository;
    private final OmrAssessmentRepository assessmentRepository;
    private final OmrAssessmentParticipantRepository participantRepository;
    private final OmrAnswerKeyRepository answerKeyRepository;

    public OmrAssessmentCorrectionResolver(
            OmrAssessmentApplicationRepository applicationRepository,
            OmrAssessmentRepository assessmentRepository,
            OmrAssessmentParticipantRepository participantRepository,
            OmrAnswerKeyRepository answerKeyRepository
    ) {
        if (applicationRepository == null
                || assessmentRepository == null
                || participantRepository == null
                || answerKeyRepository == null) {

            throw new IllegalArgumentException(
                    "Os repositórios da correção são obrigatórios."
            );
        }

        this.applicationRepository = applicationRepository;
        this.assessmentRepository = assessmentRepository;
        this.participantRepository = participantRepository;
        this.answerKeyRepository = answerKeyRepository;
    }

    public OmrAssessmentCorrectionContext resolve(
            String applicationId,
            String studentId
    ) {
        String normalizedApplicationId = requireText(
                "applicationId",
                applicationId
        );
        String normalizedStudentId = requireText(
                "studentId",
                studentId
        );

        OmrAssessmentApplication application =
                applicationRepository.findByIdOrNull(
                        normalizedApplicationId
                );

        if (application == null) {
            throw new IllegalStateException(
                    "A aplicação não está mais disponível."
            );
        }

        OmrAssessmentDefinition assessment =
                assessmentRepository.findOrNull(
                        application.getAssessmentId(),
                        application.getAssessmentVersion()
                );

        if (assessment == null) {
            throw new IllegalStateException(
                    "A versão exata da prova não está mais disponível."
            );
        }

        OmrAssessmentParticipant participant =
                participantRepository.findOrNull(
                        normalizedApplicationId,
                        normalizedStudentId
                );

        if (participant == null) {
            throw new IllegalStateException(
                    "O aluno não pertence à chamada congelada."
            );
        }

        OmrAnswerKeyDefinition answerKey =
                answerKeyRepository.findOrNull(
                        assessment.getAnswerKeyId(),
                        assessment.getAnswerKeyVersion()
                );

        if (answerKey == null) {
            throw new IllegalStateException(
                    "O gabarito exato usado pela prova não está disponível."
            );
        }

        return new OmrAssessmentCorrectionContext(
                application,
                assessment,
                participant,
                answerKey
        );
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " não pode ser vazio."
            );
        }

        return value.trim();
    }
}
