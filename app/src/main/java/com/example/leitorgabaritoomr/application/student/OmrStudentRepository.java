package com.example.leitorgabaritoomr.application.student;

import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.util.List;

/**
 * Catálogo permanente de alunos conhecidos pelo dispositivo.
 *
 * O identificador estável do aluno define se
 * {@link #save(OmrStudentProfile)} cria ou atualiza um cadastro. A matrícula
 * permanece única entre alunos diferentes.
 */
public interface OmrStudentRepository {

    void save(OmrStudentProfile student);

    List<OmrStudentProfile> loadAll();

    OmrStudentProfile findByIdOrNull(String studentId);

    OmrStudentProfile findByRegistrationOrNull(
            String registration
    );
}
