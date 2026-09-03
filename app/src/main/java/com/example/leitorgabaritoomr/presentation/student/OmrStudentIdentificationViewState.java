package com.example.leitorgabaritoomr.presentation.student;

/**
 * Estado visual imutavel e independente de Android do formulario de
 * identificacao do aluno.
 *
 * A tela preserva o texto digitado, mas considera preenchido apenas o campo
 * que possui algum caractere diferente de espaco. A criacao da identidade
 * tecnica permanece na camada de aplicacao.
 */
public final class OmrStudentIdentificationViewState {

    public enum ValidationError {
        NONE,
        REGISTRATION_REQUIRED,
        NAME_REQUIRED,
        CLASS_NAME_REQUIRED
    }

    private final String registration;
    private final String name;
    private final String className;

    private OmrStudentIdentificationViewState(
            String registration,
            String name,
            String className
    ) {
        this.registration = valueOrEmpty(registration);
        this.name = valueOrEmpty(name);
        this.className = valueOrEmpty(className);
    }

    public static OmrStudentIdentificationViewState empty() {
        return new OmrStudentIdentificationViewState(
                "",
                "",
                ""
        );
    }

    public static OmrStudentIdentificationViewState from(
            String registration,
            String name,
            String className
    ) {
        return new OmrStudentIdentificationViewState(
                registration,
                name,
                className
        );
    }

    public OmrStudentIdentificationViewState withRegistration(
            String registration
    ) {
        return new OmrStudentIdentificationViewState(
                registration,
                name,
                className
        );
    }

    public OmrStudentIdentificationViewState withName(
            String name
    ) {
        return new OmrStudentIdentificationViewState(
                registration,
                name,
                className
        );
    }

    public OmrStudentIdentificationViewState withClassName(
            String className
    ) {
        return new OmrStudentIdentificationViewState(
                registration,
                name,
                className
        );
    }

    private static String valueOrEmpty(
            String value
    ) {
        return value == null ? "" : value;
    }

    private static boolean hasText(
            String value
    ) {
        return !value.trim().isEmpty();
    }

    public String getRegistration() {
        return registration;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }

    public String getNormalizedRegistration() {
        return registration.trim();
    }

    public String getNormalizedName() {
        return name.trim();
    }

    public String getNormalizedClassName() {
        return className.trim();
    }

    public boolean isRegistrationValid() {
        return hasText(registration);
    }

    public boolean isNameValid() {
        return hasText(name);
    }

    public boolean isClassNameValid() {
        return hasText(className);
    }

    public int getMissingFieldCount() {
        int missingCount = 0;

        if (!isRegistrationValid()) {
            missingCount++;
        }

        if (!isNameValid()) {
            missingCount++;
        }

        if (!isClassNameValid()) {
            missingCount++;
        }

        return missingCount;
    }

    public ValidationError getFirstValidationError() {
        if (!isRegistrationValid()) {
            return ValidationError.REGISTRATION_REQUIRED;
        }

        if (!isNameValid()) {
            return ValidationError.NAME_REQUIRED;
        }

        if (!isClassNameValid()) {
            return ValidationError.CLASS_NAME_REQUIRED;
        }

        return ValidationError.NONE;
    }

    public boolean canContinue() {
        return getFirstValidationError()
                == ValidationError.NONE;
    }
}
