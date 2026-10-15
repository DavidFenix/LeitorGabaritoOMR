package com.example.leitorgabaritoomr.infrastructure.school;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Operacoes compartilhadas pelos codecs do detalhamento da prova. */
final class OmrAssessmentPersistenceBinaryCodecSupport {

    static final int FORMAT_VERSION = 1;
    static final int HEADER_BYTE_COUNT =
            Integer.BYTES
                    + Integer.BYTES
                    + Integer.BYTES
                    + Long.BYTES;

    static final int MAX_BODY_BYTES = 1024 * 1024;
    static final int MAX_TEXT_BYTES = 64 * 1024;

    private OmrAssessmentPersistenceBinaryCodecSupport() {
    }

    static byte[] encodeBody(
            BodyWriter writer,
            String failureMessage
    ) {
        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            writer.write(output);
            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    failureMessage,
                    exception
            );
        }

        return byteOutput.toByteArray();
    }

    static byte[] envelope(
            int magic,
            byte[] body,
            String invalidMessage,
            String failureMessage
    ) {
        if (body == null
                || body.length == 0
                || body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(invalidMessage);
        }

        CRC32 checksum = new CRC32();
        checksum.update(body);

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream(
                        HEADER_BYTE_COUNT + body.length
                );

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            output.writeInt(magic);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(body.length);
            output.writeLong(checksum.getValue());
            output.write(body);
            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    failureMessage,
                    exception
            );
        }

        return byteOutput.toByteArray();
    }

    static byte[] decodeEnvelope(
            byte[] payload,
            int expectedMagic,
            String unsupportedVersionPrefix,
            String invalidMessage
    ) {
        if (payload == null
                || payload.length <= HEADER_BYTE_COUNT
                || payload.length
                > HEADER_BYTE_COUNT + MAX_BODY_BYTES) {
            throw invalid(invalidMessage, null);
        }

        try (DataInputStream input =
                     new DataInputStream(
                             new ByteArrayInputStream(payload)
                     )) {

            if (input.readInt() != expectedMagic) {
                throw invalid(invalidMessage, null);
            }

            int formatVersion = input.readInt();
            if (formatVersion != FORMAT_VERSION) {
                throw new UnsupportedFormatException(
                        unsupportedVersionPrefix + formatVersion
                );
            }

            int bodyByteCount = input.readInt();
            long expectedChecksum = input.readLong();

            if (bodyByteCount <= 0
                    || bodyByteCount > MAX_BODY_BYTES
                    || bodyByteCount
                    != payload.length - HEADER_BYTE_COUNT) {
                throw invalid(invalidMessage, null);
            }

            byte[] body = new byte[bodyByteCount];
            input.readFully(body);

            if (input.read() != -1) {
                throw invalid(invalidMessage, null);
            }

            CRC32 checksum = new CRC32();
            checksum.update(body);

            if (checksum.getValue() != expectedChecksum) {
                throw invalid(invalidMessage, null);
            }

            return body;

        } catch (UnsupportedFormatException exception) {
            throw exception;

        } catch (IOException | IllegalArgumentException exception) {
            throw invalid(invalidMessage, exception);
        }
    }

    static DataInputStream inputOf(byte[] body) {
        return new DataInputStream(new ByteArrayInputStream(body));
    }

    static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Texto obrigatorio ausente."
            );
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(
                    "Texto fora do limite permitido."
            );
        }

        output.writeInt(bytes.length);
        output.write(bytes);
    }

    static String readText(
            DataInputStream input,
            String invalidMessage
    ) throws IOException {
        int byteCount = input.readInt();
        if (byteCount <= 0 || byteCount > MAX_TEXT_BYTES) {
            throw invalid(invalidMessage, null);
        }

        byte[] bytes = new byte[byteCount];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeOptionalText(
            DataOutputStream output,
            String value
    ) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            writeText(output, value);
        }
    }

    static String readOptionalText(
            DataInputStream input,
            String invalidMessage
    ) throws IOException {
        return input.readBoolean()
                ? readText(input, invalidMessage)
                : null;
    }

    static void writeNestedPayload(
            DataOutputStream output,
            byte[] payload
    ) throws IOException {
        if (payload == null
                || payload.length == 0
                || payload.length
                > HEADER_BYTE_COUNT + MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Payload interno fora do limite permitido."
            );
        }

        output.writeInt(payload.length);
        output.write(payload);
    }

    static byte[] readNestedPayload(
            DataInputStream input,
            String invalidMessage
    ) throws IOException {
        int byteCount = input.readInt();
        if (byteCount <= 0
                || byteCount
                > HEADER_BYTE_COUNT + MAX_BODY_BYTES) {
            throw invalid(invalidMessage, null);
        }

        byte[] payload = new byte[byteCount];
        input.readFully(payload);
        return payload;
    }

    static int readPositiveInt(
            DataInputStream input,
            String invalidMessage
    ) throws IOException {
        int value = input.readInt();
        if (value <= 0) {
            throw invalid(invalidMessage, null);
        }
        return value;
    }

    static void requireEnd(
            DataInputStream input,
            String invalidMessage
    ) throws IOException {
        if (input.read() != -1) {
            throw invalid(invalidMessage, null);
        }
    }

    static IllegalArgumentException invalid(
            String message,
            Throwable cause
    ) {
        return new IllegalArgumentException(message, cause);
    }

    interface BodyWriter {
        void write(DataOutputStream output) throws IOException;
    }

    static final class UnsupportedFormatException
            extends IllegalArgumentException {

        UnsupportedFormatException(String message) {
            super(message);
        }
    }
}
