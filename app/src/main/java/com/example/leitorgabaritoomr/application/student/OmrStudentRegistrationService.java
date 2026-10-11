package com.example.leitorgabaritoomr.application.student;

import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Porta única para cadastro manual e importação em lote de alunos.
 */
public final class OmrStudentRegistrationService {

    private static final int MAX_ID_GENERATION_ATTEMPTS = 32;

    private final OmrStudentRepository repository;
    private final StudentIdGenerator idGenerator;

    public OmrStudentRegistrationService(
            OmrStudentRepository repository
    ) {
        this(
                repository,
                () -> "student-" + UUID.randomUUID()
        );
    }

    OmrStudentRegistrationService(
            OmrStudentRepository repository,
            StudentIdGenerator idGenerator
    ) {
        if (repository == null) {
            throw new IllegalArgumentException(
                    "O repositório de alunos é obrigatório."
            );
        }

        if (idGenerator == null) {
            throw new IllegalArgumentException(
                    "O gerador de identificadores é obrigatório."
            );
        }

        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    public OmrStudentProfile registerOrUpdate(
            String registration,
            String name
    ) {
        String normalizedRegistration =
                requireRegistration(registration);
        String normalizedName = requireName(name);

        OmrStudentProfile existing =
                repository.findByRegistrationOrNull(
                        normalizedRegistration
                );

        String studentId = existing == null
                ? nextAvailableStudentId(Collections.emptySet())
                : existing.getStudentId();

        OmrStudentProfile student = new OmrStudentProfile(
                studentId,
                normalizedRegistration,
                normalizedName
        );

        repository.save(student);
        return student;
    }

    public ImportSummary importPreview(
            OmrStudentCsvParser.Preview preview
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

        List<OmrStudentProfile> students = new ArrayList<>();
        Map<String, OmrStudentProfile> existingByRegistration =
                new LinkedHashMap<>();
        Set<String> unavailableIds = new HashSet<>();

        for (OmrStudentProfile existing : repository.loadAll()) {
            existingByRegistration.put(
                    existing.getRegistration(),
                    existing
            );
            unavailableIds.add(existing.getStudentId());
        }

        Set<String> batchIds = new HashSet<>();
        Set<String> registrations = new HashSet<>();
        int createdCount = 0;
        int updatedCount = 0;

        for (OmrStudentCsvParser.Row row : preview.getRows()) {
            String registration = requireRegistration(
                    row.getRegistration()
            );
            String name = requireName(row.getName());

            if (!registrations.add(registration)) {
                throw new IllegalArgumentException(
                        "Matrícula repetida no lote: "
                                + registration
                );
            }

            OmrStudentProfile existing =
                    existingByRegistration.get(registration);

            String studentId;

            if (existing == null) {
                studentId = nextAvailableStudentId(
                        unavailableIds,
                        false
                );
                unavailableIds.add(studentId);
                createdCount++;
            } else {
                studentId = existing.getStudentId();
                updatedCount++;
            }

            if (!batchIds.add(studentId)) {
                throw new IllegalArgumentException(
                        "Identificador de aluno repetido no lote: "
                                + studentId
                );
            }

            students.add(new OmrStudentProfile(
                    studentId,
                    registration,
                    name
            ));
        }

        repository.saveAll(students);

        return new ImportSummary(
                createdCount,
                updatedCount,
                students
        );
    }

    private String nextAvailableStudentId(
            Set<String> reservedIds
    ) {
        return nextAvailableStudentId(reservedIds, true);
    }

    private String nextAvailableStudentId(
            Set<String> reservedIds,
            boolean verifyRepository
    ) {
        for (int attempt = 0;
             attempt < MAX_ID_GENERATION_ATTEMPTS;
             attempt++) {

            String candidate = normalizeText(
                    idGenerator.createStudentId()
            );

            if (candidate == null
                    || reservedIds.contains(candidate)
                    || (verifyRepository
                    && repository.findByIdOrNull(candidate) != null)) {
                continue;
            }

            return candidate;
        }

        throw new IllegalStateException(
                "Não foi possível gerar um identificador único para o aluno."
        );
    }

    private static String requireRegistration(String value) {
        String normalized = normalizeText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "A matrícula do aluno é obrigatória."
            );
        }

        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String requireName(String value) {
        String normalized = normalizeText(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "O nome do aluno é obrigatório."
            );
        }

        return normalized.replaceAll("\\s+", " ");
    }

    private static String normalizeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    interface StudentIdGenerator {
        String createStudentId();
    }

    public static final class ImportSummary {
        private final int createdCount;
        private final int updatedCount;
        private final List<OmrStudentProfile> students;

        private ImportSummary(
                int createdCount,
                int updatedCount,
                List<OmrStudentProfile> students
        ) {
            this.createdCount = createdCount;
            this.updatedCount = updatedCount;
            this.students = Collections.unmodifiableList(
                    new ArrayList<>(students)
            );
        }

        public int getCreatedCount() {
            return createdCount;
        }

        public int getUpdatedCount() {
            return updatedCount;
        }

        public int getProcessedCount() {
            return students.size();
        }

        public List<OmrStudentProfile> getStudents() {
            return students;
        }
    }
}
