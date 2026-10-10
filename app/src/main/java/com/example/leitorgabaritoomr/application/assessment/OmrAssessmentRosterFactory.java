package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produz a fotografia ordenada dos alunos de uma aplicação de prova.
 */
public final class OmrAssessmentRosterFactory {

    public List<OmrAssessmentParticipant> create(
            OmrAssessmentApplication application,
            OmrClassGroup classGroup,
            List<OmrClassEnrollment> enrollments,
            List<OmrStudentProfile> students
    ) {
        if (application == null) {
            throw new IllegalArgumentException(
                    "A aplicação da prova é obrigatória."
            );
        }

        if (classGroup == null) {
            throw new IllegalArgumentException(
                    "A turma é obrigatória."
            );
        }

        if (!application.belongsToClassGroup(
                classGroup.getClassGroupId()
        )) {
            throw new IllegalArgumentException(
                    "A turma informada não pertence à aplicação."
            );
        }

        if (enrollments == null || enrollments.isEmpty()) {
            throw new IllegalArgumentException(
                    "A aplicação deve possuir pelo menos"
                            + " um aluno matriculado."
            );
        }

        if (students == null) {
            throw new IllegalArgumentException(
                    "A coleção de alunos é obrigatória."
            );
        }

        Map<String, OmrStudentProfile> studentsById =
                indexStudents(students);

        Set<String> enrolledStudentIds = new HashSet<>();
        Set<String> enrolledRegistrations = new HashSet<>();
        Set<Integer> occupiedRosterPositions = new HashSet<>();

        List<OmrAssessmentParticipant> participants =
                new ArrayList<>(enrollments.size());

        for (OmrClassEnrollment enrollment : enrollments) {
            if (enrollment == null) {
                throw new IllegalArgumentException(
                        "A lista de matrículas não pode"
                                + " conter valores nulos."
                );
            }

            validateEnrollmentClass(
                    classGroup,
                    enrollment
            );

            String studentId = enrollment.getStudentId();

            if (!enrolledStudentIds.add(studentId)) {
                throw new IllegalArgumentException(
                        "Aluno repetido na turma: " + studentId
                );
            }

            int rosterPosition =
                    enrollment.getRosterPosition();

            if (!occupiedRosterPositions.add(
                    rosterPosition
            )) {
                throw new IllegalArgumentException(
                        "Posição repetida na chamada: "
                                + rosterPosition
                );
            }

            OmrStudentProfile student =
                    studentsById.get(studentId);

            if (student == null) {
                throw new IllegalArgumentException(
                        "Cadastro do aluno não encontrado: "
                                + studentId
                );
            }

            if (!enrolledRegistrations.add(
                    student.getRegistration()
            )) {
                throw new IllegalArgumentException(
                        "Matrícula escolar repetida na turma: "
                                + student.getRegistration()
                );
            }

            participants.add(
                    new OmrAssessmentParticipant(
                            application.getApplicationId(),
                            student.getStudentId(),
                            student.getRegistration(),
                            student.getName(),
                            classGroup.getName(),
                            rosterPosition
                    )
            );
        }

        participants.sort(
                Comparator.comparingInt(
                        OmrAssessmentParticipant
                                ::getRosterPosition
                )
        );

        return Collections.unmodifiableList(
                participants
        );
    }

    private static Map<String, OmrStudentProfile>
    indexStudents(
            List<OmrStudentProfile> students
    ) {
        Map<String, OmrStudentProfile> studentsById =
                new HashMap<>();

        for (OmrStudentProfile student : students) {
            if (student == null) {
                throw new IllegalArgumentException(
                        "A coleção de alunos não pode"
                                + " conter valores nulos."
                );
            }

            if (studentsById.put(
                    student.getStudentId(),
                    student
            ) != null) {
                throw new IllegalArgumentException(
                        "Cadastro de aluno repetido: "
                                + student.getStudentId()
                );
            }
        }

        return studentsById;
    }

    private static void validateEnrollmentClass(
            OmrClassGroup classGroup,
            OmrClassEnrollment enrollment
    ) {
        if (!enrollment.belongsToClassGroup(
                classGroup.getClassGroupId()
        )) {
            throw new IllegalArgumentException(
                    "Matrícula vinculada a outra turma: aluno="
                            + enrollment.getStudentId()
                            + ", turma="
                            + enrollment.getClassGroupId()
                            + "."
            );
        }
    }
}
