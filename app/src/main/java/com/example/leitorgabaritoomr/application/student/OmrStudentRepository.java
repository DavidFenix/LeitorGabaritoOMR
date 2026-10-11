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

    /**
     * Cria ou atualiza todos os alunos como uma única operação.
     *
     * A implementação persistente deve confirmar o lote inteiro ou não
     * gravar nenhuma linha quando qualquer aluno for inválido.
     */
    void saveAll(List<OmrStudentProfile> students);

    List<OmrStudentProfile> loadAll();

    OmrStudentProfile findByIdOrNull(String studentId);

    OmrStudentProfile findByRegistrationOrNull(
            String registration
    );
}
