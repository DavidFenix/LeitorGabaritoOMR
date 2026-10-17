package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cadastro manual e importação idempotente de provas versionadas. */
public final class OmrAssessmentRegistrationService {

    private static final int MAX_ID_GENERATION_ATTEMPTS = 32;
    private static final String CSV_ID_PREFIX = "assessment-csv:";

    private final OmrAssessmentRepository assessmentRepository;
    private final OmrAssessmentBatchRepository batchRepository;
    private final OmrAnswerKeyRepository answerKeyRepository;
    private final AssessmentIdGenerator idGenerator;

    public OmrAssessmentRegistrationService(
            OmrAssessmentRepository assessmentRepository,
            OmrAssessmentBatchRepository batchRepository,
            OmrAnswerKeyRepository answerKeyRepository
    ) {
        this(
                assessmentRepository,
                batchRepository,
                answerKeyRepository,
                () -> "assessment-" + UUID.randomUUID()
        );
    }

    OmrAssessmentRegistrationService(
            OmrAssessmentRepository assessmentRepository,
            OmrAssessmentBatchRepository batchRepository,
            OmrAnswerKeyRepository answerKeyRepository,
            AssessmentIdGenerator idGenerator
    ) {
        if (assessmentRepository == null) {
            throw new IllegalArgumentException(
                    "O repositório de provas é obrigatório."
            );
        }
        if (batchRepository == null) {
            throw new IllegalArgumentException(
                    "O repositório de lotes de provas é obrigatório."
            );
        }
        if (answerKeyRepository == null) {
            throw new IllegalArgumentException(
                    "O repositório de gabaritos é obrigatório."
            );
        }
        if (idGenerator == null) {
            throw new IllegalArgumentException(
                    "O gerador de identificadores é obrigatório."
            );
        }

        this.assessmentRepository = assessmentRepository;
        this.batchRepository = batchRepository;
        this.answerKeyRepository = answerKeyRepository;
        this.idGenerator = idGenerator;
    }

    /** Cria uma prova manual independente em sua primeira versão. */
    public OmrAssessmentDefinition registerNew(
            String title,
            String subject,
            String gradeLevel,
            String academicPeriod,
            int schoolYear,
            String answerKeyId,
            int answerKeyVersion
    ) {
        OmrAnswerKeyDefinition answerKey = requireExactAnswerKey(
                answerKeyId,
                answerKeyVersion
        );

        for (int attempt = 0;
             attempt < MAX_ID_GENERATION_ATTEMPTS;
             attempt++) {

            String assessmentId = normalizeOrNull(
                    idGenerator.createAssessmentId()
            );

            if (assessmentId == null
                    || assessmentRepository
                    .findLatestVersionOrNull(assessmentId) != null) {
                continue;
            }

            OmrAssessmentDefinition assessment = assessment(
                    assessmentId,
                    1,
                    title,
                    subject,
                    gradeLevel,
                    academicPeriod,
                    schoolYear,
                    answerKey
            );

            if (assessmentRepository.save(assessment)) {
                return assessment;
            }
        }

        throw new IllegalStateException(
                "Não foi possível gerar um identificador único para a prova."
        );
    }

    /** Publica uma nova versão, preservando todas as versões anteriores. */
    public OmrAssessmentDefinition registerNewVersion(
            String assessmentId,
            String title,
            String subject,
            String gradeLevel,
            String academicPeriod,
            int schoolYear,
            String answerKeyId,
            int answerKeyVersion
    ) {
        String normalizedId = requireText(
                assessmentId,
                "O identificador da prova é obrigatório."
        );

        OmrAssessmentDefinition current = assessmentRepository
                .findLatestVersionOrNull(normalizedId);

        if (current == null) {
            throw new IllegalArgumentException(
                    "A prova que receberia uma nova versão não existe."
            );
        }

        if (current.getVersion() == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "A prova atingiu o limite de versões."
            );
        }

        OmrAnswerKeyDefinition answerKey = requireExactAnswerKey(
                answerKeyId,
                answerKeyVersion
        );

        OmrAssessmentDefinition next = assessment(
                normalizedId,
                current.getVersion() + 1,
                title,
                subject,
                gradeLevel,
                academicPeriod,
                schoolYear,
                answerKey
        );

        if (!assessmentRepository.save(next)) {
            throw new IllegalStateException(
                    "A nova versão da prova já foi armazenada."
            );
        }

        return next;
    }

    /**
     * Importa versões descritas explicitamente no CSV.
     *
     * Códigos são convertidos em IDs estáveis. Uma identidade já existente é
     * aceita apenas quando todo o conteúdo é igual, tornando a reimportação
     * segura. Versões novas devem formar uma sequência sem lacunas.
     */
    public ImportSummary importPreview(
            OmrAssessmentCsvParser.Preview preview
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

        Map<String, List<OmrAssessmentDefinition>> byAssessmentId =
                new LinkedHashMap<>();
        Set<String> batchIdentities = new HashSet<>();

        for (OmrAssessmentCsvParser.Row row : preview.getRows()) {
            String assessmentId = row.getAssessmentInternalId() == null
                    ? assessmentIdForCsvCode(row.getAssessmentCode())
                    : requireText(
                            row.getAssessmentInternalId(),
                            "O ID interno da prova é inválido."
                    );

            OmrAnswerKeyDefinition answerKey = resolveAnswerKey(
                    row.getAnswerKeyReference(),
                    row.getAnswerKeyVersion()
            );

            OmrAssessmentDefinition assessment = assessment(
                    assessmentId,
                    row.getAssessmentVersion(),
                    row.getTitle(),
                    row.getSubject(),
                    row.getGradeLevel(),
                    row.getAcademicPeriod(),
                    row.getSchoolYear(),
                    answerKey
            );

            String identity = identityOf(assessment);

            if (!batchIdentities.add(identity)) {
                throw new IllegalArgumentException(
                        "O lote possui uma versão de prova repetida."
                );
            }

            List<OmrAssessmentDefinition> versions =
                    byAssessmentId.get(assessmentId);

            if (versions == null) {
                versions = new ArrayList<>();
                byAssessmentId.put(assessmentId, versions);
            }

            versions.add(assessment);
        }

        List<OmrAssessmentDefinition> newVersions =
                new ArrayList<>();
        int unchangedCount = 0;

        for (Map.Entry<String, List<OmrAssessmentDefinition>> entry
                : byAssessmentId.entrySet()) {

            List<OmrAssessmentDefinition> versions = entry.getValue();
            versions.sort(Comparator.comparingInt(
                    OmrAssessmentDefinition::getVersion
            ));

            OmrAssessmentDefinition latest = assessmentRepository
                    .findLatestVersionOrNull(entry.getKey());

            int expectedNewVersion = latest == null
                    ? 1
                    : nextVersionOf(latest);

            for (OmrAssessmentDefinition candidate : versions) {
                OmrAssessmentDefinition existing = assessmentRepository
                        .findOrNull(
                                candidate.getAssessmentId(),
                                candidate.getVersion()
                        );

                if (existing != null) {
                    if (!sameContent(existing, candidate)) {
                        throw new IllegalArgumentException(
                                "A versão "
                                        + candidate.getVersion()
                                        + " da prova "
                                        + csvCodeFromAssessmentId(
                                        candidate.getAssessmentId()
                                )
                                        + " já existe com outro conteúdo."
                        );
                    }

                    unchangedCount++;
                    continue;
                }

                if (candidate.getVersion() != expectedNewVersion) {
                    throw new IllegalArgumentException(
                            "A próxima versão da prova "
                                    + csvCodeFromAssessmentId(
                                    candidate.getAssessmentId()
                            )
                                    + " deve ser "
                                    + expectedNewVersion
                                    + "."
                    );
                }

                newVersions.add(candidate);
                expectedNewVersion++;
            }
        }

        if (!newVersions.isEmpty()) {
            batchRepository.saveAllNew(newVersions);
        }

        return new ImportSummary(
                newVersions.size(),
                unchangedCount,
                preview.getRows().size(),
                newVersions
        );
    }

    static String assessmentIdForCsvCode(String code) {
        String normalized = requireText(
                code,
                "O código da prova é obrigatório."
        ).replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);

        return CSV_ID_PREFIX + normalized;
    }

    private OmrAnswerKeyDefinition requireExactAnswerKey(
            String answerKeyId,
            int answerKeyVersion
    ) {
        String normalizedId = requireText(
                answerKeyId,
                "O identificador do gabarito é obrigatório."
        );

        if (answerKeyVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versão do gabarito deve ser positiva."
            );
        }

        OmrAnswerKeyDefinition answerKey = answerKeyRepository
                .findOrNull(normalizedId, answerKeyVersion);

        if (answerKey == null) {
            throw new IllegalArgumentException(
                    "O gabarito selecionado não está armazenado."
            );
        }

        return answerKey;
    }

    private OmrAnswerKeyDefinition resolveAnswerKey(
            String reference,
            int version
    ) {
        String normalizedReference = requireText(
                reference,
                "A referência do gabarito é obrigatória."
        );

        if (version <= 0) {
            throw new IllegalArgumentException(
                    "A versão do gabarito deve ser positiva."
            );
        }

        OmrAnswerKeyDefinition exact = answerKeyRepository
                .findOrNull(normalizedReference, version);

        if (exact != null) {
            return exact;
        }

        String canonicalReference = canonical(normalizedReference);
        OmrAnswerKeyDefinition match = null;

        for (OmrAnswerKeyDefinition candidate
                : answerKeyRepository.loadAll()) {

            if (candidate.getVersion() != version
                    || !canonical(candidate.getName()).equals(
                    canonicalReference
            )) {
                continue;
            }

            if (match != null
                    && !match.getId().equals(candidate.getId())) {
                throw new IllegalArgumentException(
                        "Há mais de um gabarito com o nome "
                                + normalizedReference
                                + " na versão "
                                + version
                                + ". Use o ID exato no CSV."
                );
            }

            match = candidate;
        }

        if (match == null) {
            throw new IllegalArgumentException(
                    "Nenhum gabarito corresponde a "
                            + normalizedReference
                            + " na versão "
                            + version
                            + "."
            );
        }

        return match;
    }

    private static OmrAssessmentDefinition assessment(
            String assessmentId,
            int version,
            String title,
            String subject,
            String gradeLevel,
            String academicPeriod,
            int schoolYear,
            OmrAnswerKeyDefinition answerKey
    ) {
        return new OmrAssessmentDefinition(
                requireText(
                        assessmentId,
                        "O identificador da prova é obrigatório."
                ),
                version,
                normalizedRequiredText(
                        title,
                        "O título da prova é obrigatório."
                ),
                normalizedRequiredText(
                        subject,
                        "A disciplina da prova é obrigatória."
                ),
                normalizedRequiredText(
                        gradeLevel,
                        "A etapa da prova é obrigatória."
                ),
                normalizedRequiredText(
                        academicPeriod,
                        "O período da prova é obrigatório."
                ),
                schoolYear,
                answerKey.getId(),
                answerKey.getVersion()
        );
    }

    private static int nextVersionOf(
            OmrAssessmentDefinition assessment
    ) {
        if (assessment.getVersion() == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "A prova atingiu o limite de versões."
            );
        }

        return assessment.getVersion() + 1;
    }

    private static boolean sameContent(
            OmrAssessmentDefinition first,
            OmrAssessmentDefinition second
    ) {
        return first.getAssessmentId().equals(second.getAssessmentId())
                && first.getVersion() == second.getVersion()
                && first.getTitle().equals(second.getTitle())
                && first.getSubject().equals(second.getSubject())
                && first.getGradeLevel().equals(second.getGradeLevel())
                && first.getAcademicPeriod().equals(
                second.getAcademicPeriod()
        )
                && first.getSchoolYear() == second.getSchoolYear()
                && first.getAnswerKeyId().equals(second.getAnswerKeyId())
                && first.getAnswerKeyVersion()
                == second.getAnswerKeyVersion();
    }

    private static String identityOf(
            OmrAssessmentDefinition assessment
    ) {
        return assessment.getAssessmentId()
                + "\u0000"
                + assessment.getVersion();
    }

    private static String csvCodeFromAssessmentId(
            String assessmentId
    ) {
        return assessmentId.startsWith(CSV_ID_PREFIX)
                ? assessmentId.substring(CSV_ID_PREFIX.length())
                : assessmentId;
    }

    private static String normalizedRequiredText(
            String value,
            String message
    ) {
        return requireText(value, message)
                .replaceAll("\\s+", " ");
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

        return value.trim();
    }

    private static String canonical(String value) {
        String withoutAccents = Normalizer.normalize(
                value.trim(),
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "");

        return withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    interface AssessmentIdGenerator {
        String createAssessmentId();
    }

    public static final class ImportSummary {
        private final int createdVersionCount;
        private final int unchangedVersionCount;
        private final int processedRowCount;
        private final List<OmrAssessmentDefinition> createdVersions;

        private ImportSummary(
                int createdVersionCount,
                int unchangedVersionCount,
                int processedRowCount,
                List<OmrAssessmentDefinition> createdVersions
        ) {
            this.createdVersionCount = createdVersionCount;
            this.unchangedVersionCount = unchangedVersionCount;
            this.processedRowCount = processedRowCount;
            this.createdVersions = Collections.unmodifiableList(
                    new ArrayList<>(createdVersions)
            );
        }

        public int getCreatedVersionCount() {
            return createdVersionCount;
        }

        public int getUnchangedVersionCount() {
            return unchangedVersionCount;
        }

        public int getProcessedRowCount() {
            return processedRowCount;
        }

        public List<OmrAssessmentDefinition> getCreatedVersions() {
            return createdVersions;
        }
    }
}
