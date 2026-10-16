package com.example.leitorgabaritoomr.infrastructure.school;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Codec binário versionado de uma aplicação de prova.
 */
public final class OmrAssessmentApplicationBinaryCodec {

    private static final int MAGIC = 0x4F4D5249;
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int FORMAT_VERSION = 2;

    private static final int HEADER_BYTE_COUNT =
            Integer.BYTES
                    + Integer.BYTES
                    + Integer.BYTES
                    + Long.BYTES;

    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 64 * 1024;

    public byte[] encode(
            OmrAssessmentApplication application
    ) {
        if (application == null) {
            throw new IllegalArgumentException(
                    "A aplicação da prova é obrigatória."
            );
        }

        byte[] body = encodeBody(application);

        if (body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "A aplicação excede o tamanho permitido."
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
                    "Não foi possível codificar a aplicação.",
                    exception
            );
        }

        return byteOutput.toByteArray();
    }

    public OmrAssessmentApplication decode(byte[] payload) {
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

            if (formatVersion != LEGACY_FORMAT_VERSION
                    && formatVersion != FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Versão de aplicação armazenada não suportada: "
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

            return decodeBody(body, formatVersion);

        } catch (EOFException exception) {
            throw invalidPayload(exception);

        } catch (IOException exception) {
            throw invalidPayload(exception);

        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(
                    "Versão de aplicação armazenada"
            )) {
                throw exception;
            }

            throw invalidPayload(exception);
        }
    }

    private static byte[] encodeBody(
            OmrAssessmentApplication application
    ) {
        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            writeText(output, application.getApplicationId());
            writeText(output, application.getAssessmentId());
            output.writeInt(application.getAssessmentVersion());
            writeText(output, application.getClassGroupId());
            output.writeLong(application.getCreatedAtEpochMillis());
            OmrAssessmentStructure structure = application
                    .getFrozenStructureOrNull();
            output.writeByte(structure == null ? 0 : 1);
            if (structure != null) {
                writeNestedPayload(
                        output,
                        new OmrAssessmentStructureBinaryCodec()
                                .encode(structure)
                );
            }
            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível codificar os dados da aplicação.",
                    exception
            );
        }

        return byteOutput.toByteArray();
    }

    private static OmrAssessmentApplication decodeBody(
            byte[] body,
            int formatVersion
    ) throws IOException {
        try (DataInputStream input =
                     new DataInputStream(
                             new ByteArrayInputStream(body)
                     )) {

            String applicationId = readText(input);
            String assessmentId = readText(input);
            int assessmentVersion = readPositiveInt(input);
            String classGroupId = readText(input);
            long createdAtEpochMillis = readPositiveLong(input);
            OmrAssessmentStructure structure = null;

            if (formatVersion >= FORMAT_VERSION) {
                int marker = input.readUnsignedByte();
                if (marker > 1) {
                    throw invalidPayload(null);
                }
                if (marker == 1) {
                    structure = new OmrAssessmentStructureBinaryCodec()
                            .decode(readNestedPayload(input));
                }
            }

            OmrAssessmentApplication application =
                    new OmrAssessmentApplication(
                            applicationId,
                            assessmentId,
                            assessmentVersion,
                            classGroupId,
                            createdAtEpochMillis,
                            structure
                    );

            if (input.read() != -1) {
                throw invalidPayload(null);
            }

            return application;
        }
    }

    private static void writeNestedPayload(
            DataOutputStream output,
            byte[] payload
    ) throws IOException {
        if (payload == null
                || payload.length == 0
                || payload.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "A estrutura congelada excede o tamanho permitido."
            );
        }
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static byte[] readNestedPayload(
            DataInputStream input
    ) throws IOException {
        int byteCount = input.readInt();
        if (byteCount <= 0 || byteCount > MAX_BODY_BYTES) {
            throw invalidPayload(null);
        }
        byte[] bytes = new byte[byteCount];
        input.readFully(bytes);
        return bytes;
    }

    private static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        if (bytes.length == 0 || bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(
                    "Texto da aplicação fora do limite permitido."
            );
        }

        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(
            DataInputStream input
    ) throws IOException {
        int byteCount = input.readInt();

        if (byteCount <= 0 || byteCount > MAX_TEXT_BYTES) {
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

    private static long readPositiveLong(
            DataInputStream input
    ) throws IOException {
        long value = input.readLong();

        if (value <= 0L) {
            throw invalidPayload(null);
        }

        return value;
    }

    private static IllegalArgumentException invalidPayload(
            Throwable cause
    ) {
        return new IllegalArgumentException(
                "Os dados persistidos da aplicação são inválidos.",
                cause
        );
    }
}
