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

public final class OmrClassGroupTest {

    @Test
    public void preservesAndNormalizesClassData() {
        OmrClassGroup classGroup = new OmrClassGroup(
                " class-001 ",
                " 9º A ",
                " 9º ano ",
                2026
        );

        assertEquals(
                "class-001",
                classGroup.getClassGroupId()
        );
        assertEquals("9º A", classGroup.getName());
        assertEquals("9º ano", classGroup.getGradeLevel());
        assertEquals(2026, classGroup.getSchoolYear());
    }

    @Test
    public void identityUsesOnlyStableClassGroupId() {
        OmrClassGroup original = classGroup();

        OmrClassGroup changedData = new OmrClassGroup(
                "class-001",
                "9º B",
                "Ensino Fundamental",
                2027
        );

        assertEquals(original, changedData);
        assertEquals(
                original.hashCode(),
                changedData.hashCode()
        );
    }

    @Test
    public void differentClassGroupIdMeansDifferentIdentity() {
        OmrClassGroup other = new OmrClassGroup(
                "class-002",
                "9º A",
                "9º ano",
                2026
        );

        assertNotEquals(classGroup(), other);
    }

    @Test
    public void findsClassGroupByNormalizedId() {
        OmrClassGroup classGroup = classGroup();

        assertTrue(classGroup.hasClassGroupId(" class-001 "));
        assertFalse(classGroup.hasClassGroupId("class-002"));
        assertFalse(classGroup.hasClassGroupId(null));
    }

    @Test
    public void matchesOnlyExactSchoolYear() {
        OmrClassGroup classGroup = classGroup();

        assertTrue(classGroup.belongsToSchoolYear(2026));
        assertFalse(classGroup.belongsToSchoolYear(2025));
    }

    @Test
    public void rejectsInvalidClassGroupId() {
        expectIllegalArgument(() -> new OmrClassGroup(
                null,
                "9º A",
                "9º ano",
                2026
        ));

        expectIllegalArgument(() -> new OmrClassGroup(
                " ",
                "9º A",
                "9º ano",
                2026
        ));
    }

    @Test
    public void rejectsInvalidName() {
        expectIllegalArgument(() -> new OmrClassGroup(
                "class-001",
                null,
                "9º ano",
                2026
        ));

        expectIllegalArgument(() -> new OmrClassGroup(
                "class-001",
                "",
                "9º ano",
                2026
        ));
    }

    @Test
    public void rejectsInvalidGradeLevel() {
        expectIllegalArgument(() -> new OmrClassGroup(
                "class-001",
                "9º A",
                null,
                2026
        ));

        expectIllegalArgument(() -> new OmrClassGroup(
                "class-001",
                "9º A",
                " ",
                2026
        ));
    }

    @Test
    public void rejectsNonPositiveSchoolYear() {
        expectIllegalArgument(() -> new OmrClassGroup(
                "class-001",
                "9º A",
                "9º ano",
                0
        ));

        expectIllegalArgument(() -> new OmrClassGroup(
                "class-001",
                "9º A",
                "9º ano",
                -2026
        ));
    }

    @Test
    public void remainsEquivalentAfterSerialization()
            throws Exception {

        OmrClassGroup original = classGroup();

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrClassGroup restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrClassGroup)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(original.getName(), restored.getName());
        assertEquals(
                original.getGradeLevel(),
                restored.getGradeLevel()
        );
        assertEquals(
                original.getSchoolYear(),
                restored.getSchoolYear()
        );
    }

    private OmrClassGroup classGroup() {
        return new OmrClassGroup(
                "class-001",
                "9º A",
                "9º ano",
                2026
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
