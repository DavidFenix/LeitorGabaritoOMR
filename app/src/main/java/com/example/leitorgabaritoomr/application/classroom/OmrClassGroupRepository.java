package com.example.leitorgabaritoomr.application.classroom;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

import java.util.List;

/**
 * Catálogo permanente das turmas escolares conhecidas pelo dispositivo.
 */
public interface OmrClassGroupRepository {

    void save(OmrClassGroup classGroup);

    List<OmrClassGroup> loadAll();

    List<OmrClassGroup> loadForSchoolYear(int schoolYear);

    OmrClassGroup findByIdOrNull(String classGroupId);
}
