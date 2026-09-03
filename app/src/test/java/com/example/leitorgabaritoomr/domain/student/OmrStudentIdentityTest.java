package com.example.leitorgabaritoomr.domain.student;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class OmrStudentIdentityTest {

    @Test
    public void preservesAndNormalizesStudentData() {
        OmrStudentIdentity student = new OmrStudentIdentity(
                " student-001 ",
                " 000123 ",
                " Ana Beatriz ",
                " 9 A "
        );

        assertEquals("student-001", student.getStudentId());
        assertEquals("000123", student.getRegistration());
        assertEquals("Ana Beatriz", student.getName());
        assertEquals("9 A", student.getClassName());
    }

    @Test
    public void registrationPreservesLeadingZerosAndLetters() {
        OmrStudentIdentity student = student(
                "0007-A"
        );

        assertEquals(
                "0007-A",
                student.getRegistration()
        );
    }

    @Test
    public void identityUsesOnlyStableStudentId() {
        OmrStudentIdentity original = new OmrStudentIdentity(
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A"
        );

        OmrStudentIdentity updatedData =
                new OmrStudentIdentity(
                        "student-001",
                        "000123",
                        "Ana B. Costa",
                        "9 B"
                );

        assertEquals(original, updatedData);
        assertEquals(
                original.hashCode(),
                updatedData.hashCode()
        );
    }

    @Test
    public void differentStudentIdMeansDifferentIdentity() {
        OmrStudentIdentity first = student(
                "000123"
        );

        OmrStudentIdentity second =
                new OmrStudentIdentity(
                        "student-002",
                        "000123",
                        "Ana Beatriz",
                        "9 A"
                );

        assertNotEquals(first, second);
    }

    @Test
    public void findsStudentByNormalizedId() {
        OmrStudentIdentity student = student(
                "000123"
        );

        assertTrue(student.hasStudentId(" student-001 "));
        assertFalse(student.hasStudentId("student-002"));
        assertFalse(student.hasStudentId(null));
    }

    @Test
    public void rejectsInvalidStudentId() {
        expectIllegalArgument(() ->
                new OmrStudentIdentity(
                        " ",
                        "000123",
                        "Ana Beatriz",
                        "9 A"
                )
        );

        expectIllegalArgument(() ->
                new OmrStudentIdentity(
                        null,
                        "000123",
                        "Ana Beatriz",
                        "9 A"
                )
        );
    }

    @Test
    public void rejectsInvalidRegistration() {
        expectIllegalArgument(() ->
                new OmrStudentIdentity(
                        "student-001",
                        " ",
                        "Ana Beatriz",
                        "9 A"
                )
        );
    }

    @Test
    public void rejectsInvalidName() {
        expectIllegalArgument(() ->
                new OmrStudentIdentity(
                        "student-001",
                        "000123",
                        null,
                        "9 A"
                )
        );
    }

    @Test
    public void rejectsInvalidClassName() {
        expectIllegalArgument(() ->
                new OmrStudentIdentity(
                        "student-001",
                        "000123",
                        "Ana Beatriz",
                        ""
                )
        );
    }

    @Test
    public void remainsEquivalentAfterSerialization()
            throws Exception {

        OmrStudentIdentity original = student(
                "000123"
        );

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrStudentIdentity restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrStudentIdentity)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(
                original.getRegistration(),
                restored.getRegistration()
        );
        assertEquals(
                original.getName(),
                restored.getName()
        );
        assertEquals(
                original.getClassName(),
                restored.getClassName()
        );
    }

    private OmrStudentIdentity student(
            String registration
    ) {
        return new OmrStudentIdentity(
                "student-001",
                registration,
                "Ana Beatriz",
                "9 A"
        );
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
