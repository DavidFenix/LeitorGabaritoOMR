package com.example.leitorgabaritoomr.domain.classroom;

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

public final class OmrClassEnrollmentTest {

    @Test
    public void preservesAndNormalizesEnrollmentData() {
        OmrClassEnrollment enrollment =
                new OmrClassEnrollment(
                        " class-001 ",
                        " student-001 ",
                        7
                );

        assertEquals(
                "class-001",
                enrollment.getClassGroupId()
        );
        assertEquals(
                "student-001",
                enrollment.getStudentId()
        );
        assertEquals(7, enrollment.getRosterPosition());
    }

    @Test
    public void identityUsesClassGroupAndStudentOnly() {
        OmrClassEnrollment original = enrollment();

        OmrClassEnrollment reordered =
                new OmrClassEnrollment(
                        "class-001",
                        "student-001",
                        19
                );

        assertEquals(original, reordered);
        assertEquals(
                original.hashCode(),
                reordered.hashCode()
        );
    }

    @Test
    public void differentClassGroupMeansDifferentIdentity() {
        OmrClassEnrollment other =
                new OmrClassEnrollment(
                        "class-002",
                        "student-001",
                        7
                );

        assertNotEquals(enrollment(), other);
    }

    @Test
    public void differentStudentMeansDifferentIdentity() {
        OmrClassEnrollment other =
                new OmrClassEnrollment(
                        "class-001",
                        "student-002",
                        7
                );

        assertNotEquals(enrollment(), other);
    }

    @Test
    public void identifiesItsClassGroup() {
        OmrClassEnrollment enrollment = enrollment();

        assertTrue(enrollment.belongsToClassGroup(
                " class-001 "
        ));
        assertFalse(enrollment.belongsToClassGroup(
                "class-002"
        ));
        assertFalse(enrollment.belongsToClassGroup(null));
    }

    @Test
    public void identifiesItsStudent() {
        OmrClassEnrollment enrollment = enrollment();

        assertTrue(enrollment.belongsToStudent(
                " student-001 "
        ));
        assertFalse(enrollment.belongsToStudent(
                "student-002"
        ));
        assertFalse(enrollment.belongsToStudent(null));
    }

    @Test
    public void rejectsInvalidClassGroupId() {
        expectIllegalArgument(() ->
                new OmrClassEnrollment(
                        null,
                        "student-001",
                        7
                )
        );

        expectIllegalArgument(() ->
                new OmrClassEnrollment(
                        " ",
                        "student-001",
                        7
                )
        );
    }

    @Test
    public void rejectsInvalidStudentId() {
        expectIllegalArgument(() ->
                new OmrClassEnrollment(
                        "class-001",
                        null,
                        7
                )
        );

        expectIllegalArgument(() ->
                new OmrClassEnrollment(
                        "class-001",
                        "",
                        7
                )
        );
    }

    @Test
    public void rejectsNonPositiveRosterPosition() {
        expectIllegalArgument(() ->
                new OmrClassEnrollment(
                        "class-001",
                        "student-001",
                        0
                )
        );

        expectIllegalArgument(() ->
                new OmrClassEnrollment(
                        "class-001",
                        "student-001",
                        -1
                )
        );
    }

    @Test
    public void remainsEquivalentAfterSerialization()
            throws Exception {

        OmrClassEnrollment original = enrollment();

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrClassEnrollment restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrClassEnrollment)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(
                original.getRosterPosition(),
                restored.getRosterPosition()
        );
    }

    private OmrClassEnrollment enrollment() {
        return new OmrClassEnrollment(
                "class-001",
                "student-001",
                7
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
