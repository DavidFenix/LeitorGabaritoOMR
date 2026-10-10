package com.example.leitorgabaritoomr.application.classroom;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;

import java.util.List;

/**
 * Catálogo dos vínculos entre alunos permanentes e turmas escolares.
 */
public interface OmrClassEnrollmentRepository {

    void save(OmrClassEnrollment enrollment);

    List<OmrClassEnrollment> loadForClassGroup(
            String classGroupId
    );

    List<OmrClassEnrollment> loadForStudent(
            String studentId
    );

    OmrClassEnrollment findOrNull(
            String classGroupId,
            String studentId
    );
}
