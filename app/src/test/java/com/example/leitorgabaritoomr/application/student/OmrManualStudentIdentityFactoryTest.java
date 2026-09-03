package com.example.leitorgabaritoomr.application.student;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import org.junit.Test;

import java.util.Locale;

public final class OmrManualStudentIdentityFactoryTest {

    private final OmrManualStudentIdentityFactory factory =
            new OmrManualStudentIdentityFactory();

    @Test
    public void createsStudentWithNormalizedRegistration() {
        OmrStudentIdentity student = factory.create(
                " 000123a ",
                " Ana Beatriz ",
                " 9 A "
        );

        assertEquals("000123A", student.getRegistration());
        assertEquals("Ana Beatriz", student.getName());
        assertEquals("9 A", student.getClassName());
    }

    @Test
    public void sameRegistrationAlwaysProducesSameStudentId() {
        OmrStudentIdentity first = factory.create(
                " abc-001 ",
                "Ana Beatriz",
                "9 A"
        );

        OmrStudentIdentity updatedData = factory.create(
                "ABC-001",
                "Ana B. Costa",
                "9 B"
        );

        assertEquals(
                first.getStudentId(),
                updatedData.getStudentId()
        );
        assertEquals(first, updatedData);
    }

    @Test
    public void helperProducesIdUsedByCreatedStudent() {
        String expectedId =
                factory.studentIdForRegistration(
                        "000123"
                );

        OmrStudentIdentity student = factory.create(
                "000123",
                "Ana Beatriz",
                "9 A"
        );

        assertEquals(expectedId, student.getStudentId());
    }

    @Test
    public void differentRegistrationsProduceDifferentIds() {
        String first = factory.studentIdForRegistration(
                "000123"
        );

        String second = factory.studentIdForRegistration(
                "000124"
        );

        assertNotEquals(first, second);
    }

    @Test
    public void leadingZerosRemainSignificant() {
        OmrStudentIdentity withLeadingZeros =
                factory.create(
                        "000123",
                        "Ana Beatriz",
                        "9 A"
                );

        OmrStudentIdentity withoutLeadingZeros =
                factory.create(
                        "123",
                        "Ana Beatriz",
                        "9 A"
                );

        assertEquals(
                "000123",
                withLeadingZeros.getRegistration()
        );
        assertNotEquals(
                withLeadingZeros.getStudentId(),
                withoutLeadingZeros.getStudentId()
        );
    }

    @Test
    public void punctuationAndInternalSpacesArePreserved() {
        OmrStudentIdentity student = factory.create(
                " 00.12 3-a ",
                "Ana Beatriz",
                "9 A"
        );

        assertEquals(
                "00.12 3-A",
                student.getRegistration()
        );

        assertNotEquals(
                student.getStudentId(),
                factory.studentIdForRegistration(
                        "00123A"
                )
        );
    }

    @Test
    public void generatedIdHasStablePrefixAndSha256Length() {
        String studentId =
                factory.studentIdForRegistration(
                        "000123"
                );

        assertTrue(
                studentId.startsWith("manual-sha256-")
        );
        assertEquals(
                "manual-sha256-".length() + 64,
                studentId.length()
        );
        assertTrue(
                studentId.matches(
                        "manual-sha256-[0-9a-f]{64}"
                )
        );
    }

    @Test
    public void identityDoesNotDependOnDefaultLocale() {
        Locale previousLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.US);

            String underEnglish =
                    factory.studentIdForRegistration(
                            "i-001"
                    );

            Locale.setDefault(new Locale("tr", "TR"));

            String underTurkish =
                    factory.studentIdForRegistration(
                            "i-001"
                    );

            assertEquals(underEnglish, underTurkish);
            assertEquals(
                    "I-001",
                    factory.normalizeRegistration("i-001")
            );

        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    public void rejectsNullOrBlankRegistration() {
        expectIllegalArgument(() ->
                factory.create(
                        null,
                        "Ana Beatriz",
                        "9 A"
                )
        );

        expectIllegalArgument(() ->
                factory.studentIdForRegistration(" ")
        );

        expectIllegalArgument(() ->
                factory.normalizeRegistration("")
        );
    }

    @Test
    public void rejectsMissingNameOrClassName() {
        expectIllegalArgument(() ->
                factory.create(
                        "000123",
                        " ",
                        "9 A"
                )
        );

        expectIllegalArgument(() ->
                factory.create(
                        "000123",
                        "Ana Beatriz",
                        null
                )
        );

        OmrStudentIdentity valid = factory.create(
                "000123",
                "Ana Beatriz",
                "9 A"
        );

        assertFalse(valid.getName().isEmpty());
        assertFalse(valid.getClassName().isEmpty());
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
