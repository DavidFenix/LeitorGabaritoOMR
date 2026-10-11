package com.example.leitorgabaritoomr.application.classroom;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.util.List;

/**
 * Porta transacional para importar listas escolares completas.
 *
 * Cada turma recebida tem sua lista atual substituída pelos vínculos do
 * mesmo lote. Turmas, alunos e matrículas devem ser gravados ou rejeitados
 * juntos, impedindo importações parciais.
 */
public interface OmrClassRosterBatchRepository {

    void replaceRosters(
            List<OmrClassGroup> classGroups,
            List<OmrStudentProfile> students,
            List<OmrClassEnrollment> enrollments
    );
}
