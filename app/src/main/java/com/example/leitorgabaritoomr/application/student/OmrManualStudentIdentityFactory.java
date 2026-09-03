package com.example.leitorgabaritoomr.application.student;

import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Cria identidades estaveis para alunos informados manualmente.
 *
 * A matricula normalizada e a unica origem da identidade tecnica. Nome e
 * turma permanecem dados descritivos, permitindo que sejam corrigidos ou
 * atualizados sem separar o historico do mesmo aluno.
 */
public final class OmrManualStudentIdentityFactory {

    private static final String STUDENT_ID_PREFIX =
            "manual-sha256-";

    private static final char[] HEX_DIGITS =
            "0123456789abcdef".toCharArray();

    public OmrStudentIdentity create(
            String registration,
            String name,
            String className
    ) {
        String normalizedRegistration =
                normalizeRegistration(registration);

        return new OmrStudentIdentity(
                studentIdForRegistration(
                        normalizedRegistration
                ),
                normalizedRegistration,
                name,
                className
        );
    }

    /**
     * Produz sempre a mesma identidade para a mesma matricula normalizada.
     */
    public String studentIdForRegistration(
            String registration
    ) {
        String normalizedRegistration =
                normalizeRegistration(registration);

        return STUDENT_ID_PREFIX
                + sha256Hex(normalizedRegistration);
    }

    /**
     * Remove espacos externos e uniformiza letras sem alterar zeros,
     * pontuacao ou caracteres internos da matricula.
     */
    public String normalizeRegistration(
            String registration
    ) {
        if (registration == null
                || registration.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "A matricula e obrigatoria."
            );
        }

        return registration
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static String sha256Hex(
            String value
    ) {
        byte[] digest;

        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            digest = messageDigest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 nao esta disponivel.",
                    exception
            );
        }

        char[] hexadecimal =
                new char[digest.length * 2];

        for (int index = 0;
             index < digest.length;
             index++) {

            int unsignedByte = digest[index] & 0xFF;

            hexadecimal[index * 2] =
                    HEX_DIGITS[unsignedByte >>> 4];

            hexadecimal[index * 2 + 1] =
                    HEX_DIGITS[unsignedByte & 0x0F];
        }

        return new String(hexadecimal);
    }
}
