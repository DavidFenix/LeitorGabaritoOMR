package com.example.leitorgabaritoomr.presentation.student;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OmrStudentIdentificationViewStateTest {

    @Test
    public void emptyStateRequiresAllFields() {
        OmrStudentIdentificationViewState state =
                OmrStudentIdentificationViewState.empty();

        assertEquals("", state.getRegistration());
        assertEquals("", state.getName());
        assertEquals("", state.getClassName());

        assertFalse(state.isRegistrationValid());
        assertFalse(state.isNameValid());
        assertFalse(state.isClassNameValid());
        assertEquals(3, state.getMissingFieldCount());
        assertEquals(
                OmrStudentIdentificationViewState
                        .ValidationError
                        .REGISTRATION_REQUIRED,
                state.getFirstValidationError()
        );
        assertFalse(state.canContinue());
    }

    @Test
    public void nullValuesBecomeSafeEmptyStrings() {
        OmrStudentIdentificationViewState state =
                OmrStudentIdentificationViewState.from(
                        null,
                        null,
                        null
                );

        assertEquals("", state.getRegistration());
        assertEquals("", state.getName());
        assertEquals("", state.getClassName());
        assertEquals(3, state.getMissingFieldCount());
    }

    @Test
    public void registrationOnlyMovesErrorToName() {
        OmrStudentIdentificationViewState state =
                OmrStudentIdentificationViewState.empty()
                        .withRegistration("000123");

        assertTrue(state.isRegistrationValid());
        assertFalse(state.isNameValid());
        assertFalse(state.isClassNameValid());
        assertEquals(2, state.getMissingFieldCount());
        assertEquals(
                OmrStudentIdentificationViewState
                        .ValidationError.NAME_REQUIRED,
                state.getFirstValidationError()
        );
        assertFalse(state.canContinue());
    }

    @Test
    public void registrationAndNameMoveErrorToClassName() {
        OmrStudentIdentificationViewState state =
                OmrStudentIdentificationViewState.empty()
                        .withRegistration("000123")
                        .withName("Ana Beatriz");

        assertEquals(1, state.getMissingFieldCount());
        assertEquals(
                OmrStudentIdentificationViewState
                        .ValidationError.CLASS_NAME_REQUIRED,
                state.getFirstValidationError()
        );
        assertFalse(state.canContinue());
    }

    @Test
    public void completeStateAllowsContinuation() {
        OmrStudentIdentificationViewState state =
                OmrStudentIdentificationViewState.from(
                        "000123",
                        "Ana Beatriz",
                        "9 A"
                );

        assertTrue(state.isRegistrationValid());
        assertTrue(state.isNameValid());
        assertTrue(state.isClassNameValid());
        assertEquals(0, state.getMissingFieldCount());
        assertEquals(
                OmrStudentIdentificationViewState
                        .ValidationError.NONE,
                state.getFirstValidationError()
        );
        assertTrue(state.canContinue());
    }

    @Test
    public void whitespaceOnlyFieldsRemainInvalid() {
        OmrStudentIdentificationViewState state =
                OmrStudentIdentificationViewState.from(
                        "  \t ",
                        "\n ",
                        "   "
                );

        assertEquals(3, state.getMissingFieldCount());
        assertFalse(state.canContinue());
    }

    @Test
    public void preservesOriginalTextAndExposesTrimmedValues() {
        OmrStudentIdentificationViewState state =
                OmrStudentIdentificationViewState.from(
                        " 000123 ",
                        " Ana Beatriz ",
                        " 9 A "
                );

        assertEquals(" 000123 ", state.getRegistration());
        assertEquals(" Ana Beatriz ", state.getName());
        assertEquals(" 9 A ", state.getClassName());

        assertEquals(
                "000123",
                state.getNormalizedRegistration()
        );
        assertEquals(
                "Ana Beatriz",
                state.getNormalizedName()
        );
        assertEquals(
                "9 A",
                state.getNormalizedClassName()
        );
    }

    @Test
    public void withMethodsCreateNewStatesWithoutChangingPreviousOnes() {
        OmrStudentIdentificationViewState empty =
                OmrStudentIdentificationViewState.empty();

        OmrStudentIdentificationViewState withRegistration =
                empty.withRegistration("000123");

        OmrStudentIdentificationViewState withName =
                withRegistration.withName("Ana Beatriz");

        OmrStudentIdentificationViewState complete =
                withName.withClassName("9 A");

        assertEquals("", empty.getRegistration());
        assertEquals("", withRegistration.getName());
        assertEquals("", withName.getClassName());

        assertFalse(empty.canContinue());
        assertFalse(withRegistration.canContinue());
        assertFalse(withName.canContinue());
        assertTrue(complete.canContinue());

        assertEquals("000123", complete.getRegistration());
        assertEquals("Ana Beatriz", complete.getName());
        assertEquals("9 A", complete.getClassName());
    }
}
