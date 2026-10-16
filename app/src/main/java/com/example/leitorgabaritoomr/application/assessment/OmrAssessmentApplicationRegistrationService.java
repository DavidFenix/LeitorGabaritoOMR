package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.application.classroom.OmrClassEnrollmentRepository;
import com.example.leitorgabaritoomr.application.classroom.OmrClassGroupRepository;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.application.student.OmrStudentRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Cria aplicações manuais e importa aplicações em lote com chamada congelada.
 */
public final class OmrAssessmentApplicationRegistrationService {

    private static final int MAX_ID_GENERATION_ATTEMPTS = 32;
    private static final String CSV_ID_PREFIX = "application-csv:";

    private final OmrAssessmentRepository assessmentRepository;
    private final OmrClassGroupRepository classRepository;
    private final OmrClassEnrollmentRepository enrollmentRepository;
    private final OmrStudentRepository studentRepository;
    private final OmrAssessmentApplicationRepository applicationRepository;
    private final OmrAssessmentParticipantRepository participantRepository;
    private final OmrAssessmentApplicationBatchRepository batchRepository;
    private final OmrAssessmentStructureRepository structureRepository;
    private final OmrAnswerKeyRepository answerKeyRepository;
    private final ApplicationIdGenerator idGenerator;
    private final TimeProvider timeProvider;
    private final OmrAssessmentApplicationFactory applicationFactory;
    private final OmrAssessmentRosterFactory rosterFactory;

    public OmrAssessmentApplicationRegistrationService(
            OmrAssessmentRepository assessmentRepository,
            OmrClassGroupRepository classRepository,
            OmrClassEnrollmentRepository enrollmentRepository,
            OmrStudentRepository studentRepository,
            OmrAssessmentApplicationRepository applicationRepository,
            OmrAssessmentParticipantRepository participantRepository,
            OmrAssessmentApplicationBatchRepository batchRepository
    ) {
        this(
                assessmentRepository,
                classRepository,
                enrollmentRepository,
                studentRepository,
                applicationRepository,
                participantRepository,
                batchRepository,
                null,
                null,
                () -> "application-" + UUID.randomUUID(),
                System::currentTimeMillis
        );
    }

    /**
     * Construtor usado pelo aplicativo. Além da chamada, ele fotografa a
     * estrutura curricular (inclusive o bloco legado) dentro da aplicação.
     */
    public OmrAssessmentApplicationRegistrationService(
            OmrAssessmentRepository assessmentRepository,
            OmrClassGroupRepository classRepository,
            OmrClassEnrollmentRepository enrollmentRepository,
            OmrStudentRepository studentRepository,
            OmrAssessmentApplicationRepository applicationRepository,
            OmrAssessmentParticipantRepository participantRepository,
            OmrAssessmentApplicationBatchRepository batchRepository,
            OmrAssessmentStructureRepository structureRepository,
            OmrAnswerKeyRepository answerKeyRepository
    ) {
        this(
                assessmentRepository,
                classRepository,
                enrollmentRepository,
                studentRepository,
                applicationRepository,
                participantRepository,
                batchRepository,
                structureRepository,
                answerKeyRepository,
                () -> "application-" + UUID.randomUUID(),
                System::currentTimeMillis
        );
    }

    OmrAssessmentApplicationRegistrationService(
            OmrAssessmentRepository assessmentRepository,
            OmrClassGroupRepository classRepository,
            OmrClassEnrollmentRepository enrollmentRepository,
            OmrStudentRepository studentRepository,
            OmrAssessmentApplicationRepository applicationRepository,
            OmrAssessmentParticipantRepository participantRepository,
            OmrAssessmentApplicationBatchRepository batchRepository,
            ApplicationIdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        this(
                assessmentRepository,
                classRepository,
                enrollmentRepository,
                studentRepository,
                applicationRepository,
                participantRepository,
                batchRepository,
                null,
                null,
                idGenerator,
                timeProvider
        );
    }

    OmrAssessmentApplicationRegistrationService(
            OmrAssessmentRepository assessmentRepository,
            OmrClassGroupRepository classRepository,
            OmrClassEnrollmentRepository enrollmentRepository,
            OmrStudentRepository studentRepository,
            OmrAssessmentApplicationRepository applicationRepository,
            OmrAssessmentParticipantRepository participantRepository,
            OmrAssessmentApplicationBatchRepository batchRepository,
            OmrAssessmentStructureRepository structureRepository,
            OmrAnswerKeyRepository answerKeyRepository,
            ApplicationIdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        if (assessmentRepository == null
                || classRepository == null
                || enrollmentRepository == null
                || studentRepository == null
                || applicationRepository == null
                || participantRepository == null
                || batchRepository == null) {
            throw new IllegalArgumentException(
                    "Todos os repositórios da aplicação são obrigatórios."
            );
        }
        if (idGenerator == null || timeProvider == null) {
            throw new IllegalArgumentException(
                    "Os geradores da aplicação são obrigatórios."
            );
        }
        if ((structureRepository == null)
                != (answerKeyRepository == null)) {
            throw new IllegalArgumentException(
                    "Estruturas e gabaritos devem ser fornecidos juntos."
            );
        }

        this.assessmentRepository = assessmentRepository;
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.applicationRepository = applicationRepository;
        this.participantRepository = participantRepository;
        this.batchRepository = batchRepository;
        this.structureRepository = structureRepository;
        this.answerKeyRepository = answerKeyRepository;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
        applicationFactory = new OmrAssessmentApplicationFactory();
        rosterFactory = new OmrAssessmentRosterFactory();
    }

    /** Aplica manualmente uma versão exata de prova a uma turma. */
    public RegistrationResult registerNew(
            String assessmentId,
            int assessmentVersion,
            String classGroupId
    ) {
        OmrAssessmentDefinition assessment = requireExactAssessment(
                assessmentId,
                assessmentVersion
        );
        OmrClassGroup classGroup = requireExactClass(classGroupId);

        for (int attempt = 0;
             attempt < MAX_ID_GENERATION_ATTEMPTS;
             attempt++) {
            String applicationId = normalizeOrNull(
                    idGenerator.createApplicationId()
            );

            if (applicationId == null
                    || applicationRepository.findByIdOrNull(
                    applicationId
            ) != null) {
                continue;
            }

            PreparedRegistration prepared = prepare(
                    applicationId,
                    timeProvider.currentTimeMillis(),
                    assessment,
                    classGroup
            );

            batchRepository.saveAllNew(
                    Collections.singletonList(prepared.application),
                    prepared.participants
            );

            return new RegistrationResult(
                    prepared.application,
                    prepared.participants
            );
        }

        throw new IllegalStateException(
                "Não foi possível gerar um identificador único para a aplicação."
        );
    }

    /**
     * Importa aplicações idempotentes. Aplicações novas fotografam a chamada
     * atual; uma reaplicação do mesmo CSV preserva a fotografia original.
     */
    public ImportSummary importPreview(
            OmrAssessmentApplicationCsvParser.Preview preview
    ) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A pré-visualização do CSV é obrigatória."
            );
        }
        if (!preview.isReadyToImport()) {
            throw new IllegalArgumentException(
                    "O CSV deve estar sem erros antes da importação."
            );
        }

        List<OmrAssessmentApplication> newApplications =
                new ArrayList<>();
        List<OmrAssessmentParticipant> newParticipants =
                new ArrayList<>();
        int unchangedCount = 0;

        for (OmrAssessmentApplicationCsvParser.Row row
                : preview.getRows()) {
            String applicationId = applicationIdForCsvCode(
                    row.getApplicationCode()
            );
            OmrAssessmentDefinition assessment = resolveAssessment(
                    row.getAssessmentReference(),
                    row.getAssessmentVersion()
            );
            OmrClassGroup classGroup = resolveClass(
                    row.getClassReference(),
                    assessment.getSchoolYear()
            );
            OmrAssessmentApplication existing = applicationRepository
                    .findByIdOrNull(applicationId);

            if (existing != null) {
                validateUnchangedApplication(
                        existing,
                        assessment,
                        classGroup
                );

                if (participantRepository
                        .loadForApplication(applicationId)
                        .isEmpty()) {
                    throw new IllegalStateException(
                            "A aplicação existente não possui chamada "
                                    + "congelada: "
                                    + row.getApplicationCode()
                    );
                }

                unchangedCount++;
                continue;
            }

            PreparedRegistration prepared = prepare(
                    applicationId,
                    timeProvider.currentTimeMillis(),
                    assessment,
                    classGroup
            );
            newApplications.add(prepared.application);
            newParticipants.addAll(prepared.participants);
        }

        if (!newApplications.isEmpty()) {
            batchRepository.saveAllNew(
                    newApplications,
                    newParticipants
            );
        }

        return new ImportSummary(
                newApplications.size(),
                unchangedCount,
                preview.getRows().size(),
                newParticipants.size(),
                newApplications
        );
    }

    static String applicationIdForCsvCode(String code) {
        String normalized = requireText(
                code,
                "O código da aplicação é obrigatório."
        );
        return CSV_ID_PREFIX + canonical(normalized)
                .toUpperCase(Locale.ROOT);
    }

    private PreparedRegistration prepare(
            String applicationId,
            long createdAtEpochMillis,
            OmrAssessmentDefinition assessment,
            OmrClassGroup classGroup
    ) {
        OmrAssessmentStructure frozenStructure = freezeStructure(
                assessment
        );
        OmrAssessmentApplication application = applicationFactory.create(
                applicationId,
                createdAtEpochMillis,
                assessment,
                classGroup,
                frozenStructure
        );
        List<OmrClassEnrollment> enrollments = enrollmentRepository
                .loadForClassGroup(classGroup.getClassGroupId());
        List<OmrStudentProfile> students = new ArrayList<>();

        for (OmrClassEnrollment enrollment : enrollments) {
            OmrStudentProfile student = studentRepository
                    .findByIdOrNull(enrollment.getStudentId());
            if (student == null) {
                throw new IllegalStateException(
                        "Cadastro do aluno não encontrado: "
                                + enrollment.getStudentId()
                );
            }
            students.add(student);
        }

        List<OmrAssessmentParticipant> participants = rosterFactory.create(
                application,
                classGroup,
                enrollments,
                students
        );
        return new PreparedRegistration(application, participants);
    }

    private OmrAssessmentStructure freezeStructure(
            OmrAssessmentDefinition assessment
    ) {
        if (structureRepository == null) {
            return null;
        }

        OmrAnswerKeyDefinition answerKey = answerKeyRepository.findOrNull(
                assessment.getAnswerKeyId(),
                assessment.getAnswerKeyVersion()
        );
        if (answerKey == null) {
            throw new IllegalStateException(
                    "O gabarito exato da prova não está disponível."
            );
        }

        OmrAssessmentStructure structure = structureRepository
                .resolveOrLegacy(
                        assessment.getAssessmentId(),
                        assessment.getVersion(),
                        answerKey.getQuestionCount()
                );
        if (structure == null) {
            throw new IllegalStateException(
                    "A estrutura curricular da prova está inconsistente."
            );
        }
        return structure;
    }

    private OmrAssessmentDefinition requireExactAssessment(
            String assessmentId,
            int assessmentVersion
    ) {
        String normalizedId = requireText(
                assessmentId,
                "A prova é obrigatória."
        );
        if (assessmentVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versão da prova deve ser positiva."
            );
        }

        OmrAssessmentDefinition assessment = assessmentRepository
                .findOrNull(normalizedId, assessmentVersion);
        if (assessment == null) {
            throw new IllegalArgumentException(
                    "A versão exata da prova não está cadastrada."
            );
        }
        return assessment;
    }

    private OmrClassGroup requireExactClass(String classGroupId) {
        String normalizedId = requireText(
                classGroupId,
                "A turma é obrigatória."
        );
        OmrClassGroup classGroup = classRepository
                .findByIdOrNull(normalizedId);
        if (classGroup == null) {
            throw new IllegalArgumentException(
                    "A turma informada não está cadastrada."
            );
        }
        return classGroup;
    }

    private OmrAssessmentDefinition resolveAssessment(
            String reference,
            int version
    ) {
        String normalizedReference = requireText(
                reference,
                "A referência da prova é obrigatória."
        );
        if (version <= 0) {
            throw new IllegalArgumentException(
                    "A versão da prova deve ser positiva."
            );
        }

        OmrAssessmentDefinition exact = assessmentRepository.findOrNull(
                normalizedReference,
                version
        );
        if (exact != null) {
            return exact;
        }

        String importedId = OmrAssessmentRegistrationService
                .assessmentIdForCsvCode(normalizedReference);
        exact = assessmentRepository.findOrNull(importedId, version);
        if (exact != null) {
            return exact;
        }

        String canonicalReference = canonical(normalizedReference);
        OmrAssessmentDefinition match = null;

        for (OmrAssessmentDefinition candidate
                : assessmentRepository.loadAll()) {
            if (candidate.getVersion() != version
                    || !canonical(candidate.getTitle()).equals(
                    canonicalReference
            )) {
                continue;
            }
            if (match != null
                    && !match.getAssessmentId().equals(
                    candidate.getAssessmentId()
            )) {
                throw new IllegalArgumentException(
                        "Há mais de uma prova chamada "
                                + normalizedReference
                                + " na versão " + version
                                + ". Use o ID exato no CSV."
                );
            }
            match = candidate;
        }

        if (match == null) {
            throw new IllegalArgumentException(
                    "Nenhuma prova corresponde a "
                            + normalizedReference
                            + " na versão " + version + "."
            );
        }
        return match;
    }

    private OmrClassGroup resolveClass(
            String reference,
            int schoolYear
    ) {
        String normalizedReference = requireText(
                reference,
                "A referência da turma é obrigatória."
        );
        OmrClassGroup exact = classRepository.findByIdOrNull(
                normalizedReference
        );
        if (exact != null) {
            return exact;
        }

        String canonicalReference = canonical(normalizedReference);
        OmrClassGroup match = null;

        for (OmrClassGroup candidate : classRepository.loadAll()) {
            if (candidate.getSchoolYear() != schoolYear
                    || !canonical(candidate.getName()).equals(
                    canonicalReference
            )) {
                continue;
            }
            if (match != null
                    && !match.getClassGroupId().equals(
                    candidate.getClassGroupId()
            )) {
                throw new IllegalArgumentException(
                        "Há mais de uma turma chamada "
                                + normalizedReference
                                + " no ano " + schoolYear
                                + ". Use o ID exato no CSV."
                );
            }
            match = candidate;
        }

        if (match == null) {
            throw new IllegalArgumentException(
                    "Nenhuma turma corresponde a "
                            + normalizedReference
                            + " no ano " + schoolYear + "."
            );
        }
        return match;
    }

    private static void validateUnchangedApplication(
            OmrAssessmentApplication existing,
            OmrAssessmentDefinition assessment,
            OmrClassGroup classGroup
    ) {
        if (!existing.usesAssessment(
                assessment.getAssessmentId(),
                assessment.getVersion()
        ) || !existing.belongsToClassGroup(
                classGroup.getClassGroupId()
        )) {
            throw new IllegalArgumentException(
                    "O código da aplicação já existe com outra prova "
                            + "ou turma."
            );
        }
    }

    private static String requireText(
            String value,
            String message
    ) {
        String normalized = normalizeOrNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String canonical(String value) {
        String withoutAccents = Normalizer.normalize(
                value.trim(),
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "");
        return withoutAccents
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    interface ApplicationIdGenerator {
        String createApplicationId();
    }

    interface TimeProvider {
        long currentTimeMillis();
    }

    public static final class RegistrationResult {
        private final OmrAssessmentApplication application;
        private final List<OmrAssessmentParticipant> participants;

        private RegistrationResult(
                OmrAssessmentApplication application,
                List<OmrAssessmentParticipant> participants
        ) {
            this.application = application;
            this.participants = Collections.unmodifiableList(
                    new ArrayList<>(participants)
            );
        }

        public OmrAssessmentApplication getApplication() {
            return application;
        }

        public List<OmrAssessmentParticipant> getParticipants() {
            return participants;
        }
    }

    public static final class ImportSummary {
        private final int createdApplicationCount;
        private final int unchangedApplicationCount;
        private final int processedRowCount;
        private final int frozenParticipantCount;
        private final List<OmrAssessmentApplication> createdApplications;

        private ImportSummary(
                int createdApplicationCount,
                int unchangedApplicationCount,
                int processedRowCount,
                int frozenParticipantCount,
                List<OmrAssessmentApplication> createdApplications
        ) {
            this.createdApplicationCount = createdApplicationCount;
            this.unchangedApplicationCount = unchangedApplicationCount;
            this.processedRowCount = processedRowCount;
            this.frozenParticipantCount = frozenParticipantCount;
            this.createdApplications = Collections.unmodifiableList(
                    new ArrayList<>(createdApplications)
            );
        }

        public int getCreatedApplicationCount() {
            return createdApplicationCount;
        }

        public int getUnchangedApplicationCount() {
            return unchangedApplicationCount;
        }

        public int getProcessedRowCount() {
            return processedRowCount;
        }

        public int getFrozenParticipantCount() {
            return frozenParticipantCount;
        }

        public List<OmrAssessmentApplication> getCreatedApplications() {
            return createdApplications;
        }
    }

    private static final class PreparedRegistration {
        private final OmrAssessmentApplication application;
        private final List<OmrAssessmentParticipant> participants;

        private PreparedRegistration(
                OmrAssessmentApplication application,
                List<OmrAssessmentParticipant> participants
        ) {
            this.application = application;
            this.participants = participants;
        }
    }
}
