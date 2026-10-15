package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentScoringPolicy;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Valida e publica os blocos de uma versao de prova ja cadastrada.
 *
 * O total dos blocos deve coincidir com a quantidade real de questoes do
 * gabarito exato. A estrutura e imutavel: repetir o mesmo conteudo e seguro,
 * mas tentar trocar blocos de uma versao publicada e rejeitado.
 */
public final class OmrAssessmentStructureRegistrationService {

    private final OmrAssessmentRepository assessmentRepository;
    private final OmrAssessmentStructureRepository structureRepository;
    private final OmrAssessmentStructureBatchRepository batchRepository;
    private final OmrAnswerKeyRepository answerKeyRepository;

    public OmrAssessmentStructureRegistrationService(
            OmrAssessmentRepository assessmentRepository,
            OmrAssessmentStructureRepository structureRepository,
            OmrAssessmentStructureBatchRepository batchRepository,
            OmrAnswerKeyRepository answerKeyRepository
    ) {
        if (assessmentRepository == null) {
            throw new IllegalArgumentException(
                    "O repositorio de provas e obrigatorio."
            );
        }
        if (structureRepository == null) {
            throw new IllegalArgumentException(
                    "O repositorio de estruturas e obrigatorio."
            );
        }
        if (batchRepository == null) {
            throw new IllegalArgumentException(
                    "O repositorio transacional de estruturas e obrigatorio."
            );
        }
        if (answerKeyRepository == null) {
            throw new IllegalArgumentException(
                    "O repositorio de gabaritos e obrigatorio."
            );
        }

        this.assessmentRepository = assessmentRepository;
        this.structureRepository = structureRepository;
        this.batchRepository = batchRepository;
        this.answerKeyRepository = answerKeyRepository;
    }

    /** Publica manualmente os blocos de uma versao exata de prova. */
    public RegistrationResult register(
            String assessmentId,
            int assessmentVersion,
            List<BlockInput> blocks
    ) {
        OmrAssessmentDefinition assessment = requireAssessment(
                assessmentId,
                assessmentVersion
        );
        OmrAssessmentStructure candidate = buildStructure(
                assessment,
                blocks
        );
        OmrAssessmentStructure existing = structureRepository
                .findStoredOrNull(
                        assessment.getAssessmentId(),
                        assessment.getVersion()
                );

        if (existing != null) {
            if (!existing.equals(candidate)) {
                throw new IllegalArgumentException(
                        "A versao da prova ja possui outra estrutura"
                                + " curricular publicada."
                );
            }
            return new RegistrationResult(existing, false);
        }

        if (structureRepository.save(candidate)) {
            return new RegistrationResult(candidate, true);
        }

        OmrAssessmentStructure concurrent = structureRepository
                .findStoredOrNull(
                        assessment.getAssessmentId(),
                        assessment.getVersion()
                );
        if (candidate.equals(concurrent)) {
            return new RegistrationResult(concurrent, false);
        }

        throw new IllegalStateException(
                "A estrutura da prova foi publicada simultaneamente"
                        + " com outro conteudo ou esta inconsistente."
        );
    }

    /** Importa todas as estruturas validas como uma unica transacao. */
    public ImportSummary importPreview(
            OmrAssessmentStructureCsvParser.Preview preview
    ) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A pre-visualizacao do CSV e obrigatoria."
            );
        }
        if (!preview.isReadyToImport()) {
            throw new IllegalArgumentException(
                    "O CSV de blocos deve estar sem erros antes da importacao."
            );
        }

        Map<String, ResolvedGroup> groups = new LinkedHashMap<>();

        for (OmrAssessmentStructureCsvParser.Row row
                : preview.getRows()) {
            OmrAssessmentDefinition assessment = resolveAssessment(
                    row.getAssessmentReference(),
                    row.getAssessmentVersion()
            );
            String identity = identityOf(assessment);
            ResolvedGroup group = groups.get(identity);
            if (group == null) {
                group = new ResolvedGroup(assessment);
                groups.put(identity, group);
            }
            group.rows.add(row);
        }

        List<OmrAssessmentStructure> created = new ArrayList<>();
        int unchanged = 0;

        for (ResolvedGroup group : groups.values()) {
            group.rows.sort(Comparator.comparingInt(
                    OmrAssessmentStructureCsvParser.Row::getBlockOrder
            ));

            List<BlockInput> inputs = new ArrayList<>();
            for (OmrAssessmentStructureCsvParser.Row row : group.rows) {
                inputs.add(new BlockInput(
                        row.getBlockOrder(),
                        row.getBlockCode(),
                        row.getAreaName(),
                        row.getComponentCode(),
                        row.getComponentName(),
                        row.getQuestionCount()
                ));
            }

            OmrAssessmentStructure candidate = buildStructure(
                    group.assessment,
                    inputs
            );
            OmrAssessmentStructure existing = structureRepository
                    .findStoredOrNull(
                            candidate.getAssessmentId(),
                            candidate.getAssessmentVersion()
                    );

            if (existing != null) {
                if (!existing.equals(candidate)) {
                    throw new IllegalArgumentException(
                            "A prova "
                                    + group.assessment.getTitle()
                                    + " na versao "
                                    + group.assessment.getVersion()
                                    + " ja possui outros blocos publicados."
                    );
                }
                unchanged++;
            } else {
                created.add(candidate);
            }
        }

        if (!created.isEmpty()) {
            batchRepository.saveAllNew(created);
        }

        return new ImportSummary(
                created.size(),
                unchanged,
                groups.size(),
                preview.getRows().size(),
                created
        );
    }

    private OmrAssessmentStructure buildStructure(
            OmrAssessmentDefinition assessment,
            List<BlockInput> inputs
    ) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Informe pelo menos um bloco curricular."
            );
        }

        OmrAnswerKeyDefinition answerKey = answerKeyRepository.findOrNull(
                assessment.getAnswerKeyId(),
                assessment.getAnswerKeyVersion()
        );
        if (answerKey == null) {
            throw new IllegalStateException(
                    "O gabarito exato vinculado a prova nao esta disponivel."
            );
        }

        List<BlockInput> ordered = new ArrayList<>(inputs);
        for (BlockInput input : ordered) {
            if (input == null) {
                throw new IllegalArgumentException(
                        "A lista de blocos nao pode conter valores nulos."
                );
            }
        }
        ordered.sort(Comparator.comparingInt(BlockInput::getOrder));
        List<OmrAssessmentBlock> blocks = new ArrayList<>();
        long firstPosition = 1L;

        for (int index = 0; index < ordered.size(); index++) {
            BlockInput input = ordered.get(index);
            int expectedOrder = index + 1;
            if (input.getOrder() != expectedOrder) {
                throw new IllegalArgumentException(
                        "As ordens dos blocos devem formar a sequencia"
                                + " de 1 a " + ordered.size() + "."
                );
            }
            if (firstPosition > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "A quantidade total de itens excede o limite."
                );
            }

            blocks.add(new OmrAssessmentBlock(
                    blockIdOf(input),
                    input.getOrder(),
                    input.getAreaName(),
                    input.getComponentCode(),
                    input.getComponentName(),
                    (int) firstPosition,
                    input.getQuestionCount(),
                    input.getScoringPolicy()
            ));
            firstPosition += input.getQuestionCount();
        }

        long informedQuestionCount = firstPosition - 1L;
        if (informedQuestionCount != answerKey.getQuestionCount()) {
            throw new IllegalArgumentException(
                    "Os blocos somam "
                            + informedQuestionCount
                            + " itens, mas o gabarito vinculado possui "
                            + answerKey.getQuestionCount()
                            + "."
            );
        }

        return new OmrAssessmentStructure(
                assessment.getAssessmentId(),
                assessment.getVersion(),
                answerKey.getQuestionCount(),
                blocks
        );
    }

    private OmrAssessmentDefinition requireAssessment(
            String assessmentId,
            int assessmentVersion
    ) {
        String normalized = requireText(
                assessmentId,
                "O identificador da prova e obrigatorio."
        );
        if (assessmentVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versao da prova deve ser positiva."
            );
        }

        OmrAssessmentDefinition assessment = assessmentRepository
                .findOrNull(normalized, assessmentVersion);
        if (assessment == null) {
            throw new IllegalArgumentException(
                    "A versao da prova nao esta armazenada ou esta corrompida."
            );
        }
        return assessment;
    }

    private OmrAssessmentDefinition resolveAssessment(
            String reference,
            int version
    ) {
        String normalized = requireText(
                reference,
                "A referencia da prova e obrigatoria."
        );
        if (version <= 0) {
            throw new IllegalArgumentException(
                    "A versao da prova deve ser positiva."
            );
        }

        OmrAssessmentDefinition direct = assessmentRepository.findOrNull(
                normalized,
                version
        );
        if (direct != null) {
            return direct;
        }

        String csvId = OmrAssessmentRegistrationService
                .assessmentIdForCsvCode(normalized);
        OmrAssessmentDefinition fromCode = assessmentRepository.findOrNull(
                csvId,
                version
        );
        if (fromCode == null) {
            throw new IllegalArgumentException(
                    "Nenhuma prova corresponde a "
                            + normalized
                            + " na versao "
                            + version
                            + "."
            );
        }
        return fromCode;
    }

    private static String blockIdOf(BlockInput input) {
        String source = input.getBlockCode() == null
                ? Integer.toString(input.getOrder())
                : input.getBlockCode();
        String canonical = Normalizer.normalize(
                source,
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (canonical.isEmpty()) {
            canonical = Integer.toString(input.getOrder());
        }
        return "bloco:" + canonical;
    }

    private static String identityOf(OmrAssessmentDefinition assessment) {
        return assessment.getAssessmentId()
                + "\u0000"
                + assessment.getVersion();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.trim().isEmpty()
                ? null
                : value.trim().replaceAll("\\s+", " ");
    }

    public static final class BlockInput {
        private final int order;
        private final String blockCode;
        private final String areaName;
        private final String componentCode;
        private final String componentName;
        private final int questionCount;
        private final OmrAssessmentScoringPolicy scoringPolicy;

        public BlockInput(
                int order,
                String blockCode,
                String areaName,
                String componentCode,
                String componentName,
                int questionCount
        ) {
            this(
                    order,
                    blockCode,
                    areaName,
                    componentCode,
                    componentName,
                    questionCount,
                    OmrProportionalScoringPolicy
                            .correctQuestionsToTenV1()
            );
        }

        public BlockInput(
                int order,
                String blockCode,
                String areaName,
                String componentCode,
                String componentName,
                int questionCount,
                OmrAssessmentScoringPolicy scoringPolicy
        ) {
            if (order <= 0) {
                throw new IllegalArgumentException(
                        "A ordem do bloco deve ser positiva."
                );
            }
            if (componentName == null
                    || componentName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "O componente curricular e obrigatorio."
                );
            }
            if (questionCount <= 0) {
                throw new IllegalArgumentException(
                        "O bloco deve possuir pelo menos um item."
                );
            }
            if (scoringPolicy == null) {
                throw new IllegalArgumentException(
                        "A politica de nota e obrigatoria."
                );
            }

            this.order = order;
            this.blockCode = optionalText(blockCode);
            this.areaName = optionalText(areaName);
            this.componentCode = optionalText(componentCode);
            this.componentName = componentName.trim()
                    .replaceAll("\\s+", " ");
            this.questionCount = questionCount;
            this.scoringPolicy = scoringPolicy;
        }

        public int getOrder() {
            return order;
        }

        public String getBlockCode() {
            return blockCode;
        }

        public String getAreaName() {
            return areaName;
        }

        public String getComponentCode() {
            return componentCode;
        }

        public String getComponentName() {
            return componentName;
        }

        public int getQuestionCount() {
            return questionCount;
        }

        public OmrAssessmentScoringPolicy getScoringPolicy() {
            return scoringPolicy;
        }
    }

    public static final class RegistrationResult {
        private final OmrAssessmentStructure structure;
        private final boolean created;

        private RegistrationResult(
                OmrAssessmentStructure structure,
                boolean created
        ) {
            this.structure = structure;
            this.created = created;
        }

        public OmrAssessmentStructure getStructure() {
            return structure;
        }

        public boolean isCreated() {
            return created;
        }
    }

    public static final class ImportSummary {
        private final int createdStructureCount;
        private final int unchangedStructureCount;
        private final int processedAssessmentCount;
        private final int processedBlockCount;
        private final List<OmrAssessmentStructure> createdStructures;

        private ImportSummary(
                int createdStructureCount,
                int unchangedStructureCount,
                int processedAssessmentCount,
                int processedBlockCount,
                List<OmrAssessmentStructure> createdStructures
        ) {
            this.createdStructureCount = createdStructureCount;
            this.unchangedStructureCount = unchangedStructureCount;
            this.processedAssessmentCount = processedAssessmentCount;
            this.processedBlockCount = processedBlockCount;
            this.createdStructures = Collections.unmodifiableList(
                    new ArrayList<>(createdStructures)
            );
        }

        public int getCreatedStructureCount() {
            return createdStructureCount;
        }

        public int getUnchangedStructureCount() {
            return unchangedStructureCount;
        }

        public int getProcessedAssessmentCount() {
            return processedAssessmentCount;
        }

        public int getProcessedBlockCount() {
            return processedBlockCount;
        }

        public List<OmrAssessmentStructure> getCreatedStructures() {
            return createdStructures;
        }
    }

    private static final class ResolvedGroup {
        private final OmrAssessmentDefinition assessment;
        private final List<OmrAssessmentStructureCsvParser.Row> rows =
                new ArrayList<>();

        private ResolvedGroup(OmrAssessmentDefinition assessment) {
            this.assessment = assessment;
        }
    }
}
