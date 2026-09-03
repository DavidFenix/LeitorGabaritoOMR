package com.example.leitorgabaritoomr.infrastructure.history;

import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.CRC32;

/**
 * Codec binario versionado de um registro individual do historico.
 *
 * Cada registro e independente para permitir que o repositorio grave uma
 * prova por linha no banco local. O envelope contem assinatura, versao,
 * tamanho e CRC32 antes do instantaneo Java serializado.
 */
public final class OmrGradingHistoryRecordBinaryCodec {

    private static final int MAGIC = 0x4F4D5248;
    private static final int FORMAT_VERSION = 1;

    private static final int HEADER_BYTE_COUNT =
            Integer.BYTES
                    + Integer.BYTES
                    + Integer.BYTES
                    + Long.BYTES;

    private static final int MAX_RECORD_BYTES =
            2 * 1024 * 1024;

    public byte[] encode(
            OmrGradingHistoryRecord record
    ) {
        if (record == null) {
            throw new IllegalArgumentException(
                    "O registro historico e obrigatorio."
            );
        }

        byte[] serializedRecord = serialize(record);

        if (serializedRecord.length == 0
                || serializedRecord.length
                > MAX_RECORD_BYTES) {

            throw new IllegalArgumentException(
                    "O registro historico excede o tamanho"
                            + " permitido."
            );
        }

        CRC32 checksum = new CRC32();
        checksum.update(serializedRecord);

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream(
                        HEADER_BYTE_COUNT
                                + serializedRecord.length
                );

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(serializedRecord.length);
            output.writeLong(checksum.getValue());
            output.write(serializedRecord);
            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Nao foi possivel codificar o registro historico.",
                    exception
            );
        }

        return byteOutput.toByteArray();
    }

    public OmrGradingHistoryRecord decode(
            byte[] payload
    ) {
        if (payload == null
                || payload.length <= HEADER_BYTE_COUNT
                || payload.length
                > HEADER_BYTE_COUNT + MAX_RECORD_BYTES) {

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
                        "Versao do registro historico"
                                + " nao suportada: "
                                + formatVersion
                );
            }

            int serializedByteCount = input.readInt();
            long expectedChecksum = input.readLong();

            if (serializedByteCount <= 0
                    || serializedByteCount > MAX_RECORD_BYTES
                    || serializedByteCount
                    != payload.length - HEADER_BYTE_COUNT) {

                throw invalidPayload(null);
            }

            byte[] serializedRecord =
                    new byte[serializedByteCount];

            input.readFully(serializedRecord);

            if (input.read() != -1) {
                throw invalidPayload(null);
            }

            CRC32 checksum = new CRC32();
            checksum.update(serializedRecord);

            if (checksum.getValue() != expectedChecksum) {
                throw invalidPayload(null);
            }

            return deserialize(serializedRecord);

        } catch (EOFException exception) {
            throw invalidPayload(exception);

        } catch (IOException exception) {
            throw invalidPayload(exception);

        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(
                    "Versao do registro historico"
            )) {
                throw exception;
            }

            throw invalidPayload(exception);
        }
    }

    private static byte[] serialize(
            OmrGradingHistoryRecord record
    ) {
        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(record);
            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Nao foi possivel serializar"
                            + " o registro historico.",
                    exception
            );
        }

        return byteOutput.toByteArray();
    }

    private static OmrGradingHistoryRecord deserialize(
            byte[] serializedRecord
    ) {
        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     serializedRecord
                             )
                     )) {

            Object decoded = input.readObject();

            if (!(decoded
                    instanceof OmrGradingHistoryRecord)) {

                throw invalidPayload(null);
            }

            if (input.read() != -1) {
                throw invalidPayload(null);
            }

            return (OmrGradingHistoryRecord) decoded;

        } catch (IOException exception) {
            throw invalidPayload(exception);

        } catch (ClassNotFoundException exception) {
            throw invalidPayload(exception);
        }
    }

    private static IllegalArgumentException invalidPayload(
            Throwable cause
    ) {
        return new IllegalArgumentException(
                "Os dados persistidos do registro historico"
                        + " sao invalidos.",
                cause
        );
    }
}
