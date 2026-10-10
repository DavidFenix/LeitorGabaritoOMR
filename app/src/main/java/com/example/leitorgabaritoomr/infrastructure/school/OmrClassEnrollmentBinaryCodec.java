package com.example.leitorgabaritoomr.infrastructure.school;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Codec binário versionado de uma matrícula em turma.
 */
public final class OmrClassEnrollmentBinaryCodec {

    private static final int MAGIC = 0x4F4D5245;
    private static final int FORMAT_VERSION = 1;

    private static final int HEADER_BYTE_COUNT =
            Integer.BYTES
                    + Integer.BYTES
                    + Integer.BYTES
                    + Long.BYTES;

    private static final int MAX_BODY_BYTES =
            1024 * 1024;
    private static final int MAX_TEXT_BYTES =
            64 * 1024;

    public byte[] encode(
            OmrClassEnrollment enrollment
    ) {
        if (enrollment == null) {
            throw new IllegalArgumentException(
                    "A matrícula em turma é obrigatória."
            );
        }

        byte[] body = encodeBody(enrollment);

        if (body.length == 0
                || body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "A matrícula excede o tamanho permitido."
            );
        }

        CRC32 checksum = new CRC32();
        checksum.update(body);

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream(
                        HEADER_BYTE_COUNT + body.length
                );

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(body.length);
            output.writeLong(checksum.getValue());
            output.write(body);
            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível codificar a matrícula.",
                    exception
            );
        }

        return byteOutput.toByteArray();
    }

    public OmrClassEnrollment decode(
            byte[] payload
    ) {
        if (payload == null
                || payload.length <= HEADER_BYTE_COUNT
                || payload.length
                > HEADER_BYTE_COUNT + MAX_BODY_BYTES) {

            throw invalidPayload(null);
        }

        try (DataInputStream input =
                     new DataInputStream(
                             new ByteArrayInputStream(payload)
                     )) {

            if (input.readInt() != MAGIC) {
                throw invalidPayload(null);
            }

            int formatVersion = input.readInt();

            if (formatVersion != FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Versão de matrícula armazenada não suportada: "
                                + formatVersion
                );
            }

            int bodyByteCount = input.readInt();
            long expectedChecksum = input.readLong();

            if (bodyByteCount <= 0
                    || bodyByteCount > MAX_BODY_BYTES
                    || bodyByteCount
                    != payload.length - HEADER_BYTE_COUNT) {

                throw invalidPayload(null);
            }

            byte[] body = new byte[bodyByteCount];
            input.readFully(body);

            if (input.read() != -1) {
                throw invalidPayload(null);
            }

            CRC32 checksum = new CRC32();
            checksum.update(body);

            if (checksum.getValue() != expectedChecksum) {
                throw invalidPayload(null);
            }

            return decodeBody(body);

        } catch (EOFException exception) {
            throw invalidPayload(exception);

        } catch (IOException exception) {
            throw invalidPayload(exception);

        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(
                    "Versão de matrícula armazenada"
            )) {
                throw exception;
            }

            throw invalidPayload(exception);
        }
    }

    private static byte[] encodeBody(
            OmrClassEnrollment enrollment
    ) {
        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            writeText(output, enrollment.getClassGroupId());
            writeText(output, enrollment.getStudentId());
            output.writeInt(enrollment.getRosterPosition());
            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível codificar os dados da matrícula.",
                    exception
            );
        }

        return byteOutput.toByteArray();
    }

    private static OmrClassEnrollment decodeBody(
            byte[] body
    ) throws IOException {
        try (DataInputStream input =
                     new DataInputStream(
                             new ByteArrayInputStream(body)
                     )) {

            OmrClassEnrollment enrollment =
                    new OmrClassEnrollment(
                            readText(input),
                            readText(input),
                            readPositiveInt(input)
                    );

            if (input.read() != -1) {
                throw invalidPayload(null);
            }

            return enrollment;
        }
    }

    private static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        byte[] bytes = value.getBytes(
                StandardCharsets.UTF_8
        );

        if (bytes.length == 0
                || bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(
                    "Texto da matrícula fora do limite permitido."
            );
        }

        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(
            DataInputStream input
    ) throws IOException {
        int byteCount = input.readInt();

        if (byteCount <= 0
                || byteCount > MAX_TEXT_BYTES) {
            throw invalidPayload(null);
        }

        byte[] bytes = new byte[byteCount];
        input.readFully(bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readPositiveInt(
            DataInputStream input
    ) throws IOException {
        int value = input.readInt();

        if (value <= 0) {
            throw invalidPayload(null);
        }

        return value;
    }

    private static IllegalArgumentException invalidPayload(
            Throwable cause
    ) {
        return new IllegalArgumentException(
                "Os dados persistidos da matrícula são inválidos.",
                cause
        );
    }
}
