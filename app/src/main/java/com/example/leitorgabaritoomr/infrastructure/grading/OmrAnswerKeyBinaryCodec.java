package com.example.leitorgabaritoomr.infrastructure.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Codifica e decodifica um gabarito oficial em um formato binário próprio,
 * determinístico e versionado.
 *
 * O formato grava apenas os dados públicos do domínio. Nenhum detalhe interno
 * de implementação das classes é serializado, permitindo que o modelo evolua
 * sem tornar automaticamente ilegíveis os gabaritos já salvos.
 */
public final class OmrAnswerKeyBinaryCodec {

    private static final int MAGIC = 0x4F4D524B;
    private static final int FORMAT_VERSION = 1;

    private static final int MAX_PAYLOAD_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 64 * 1024;
    private static final int MAX_ENTRY_COUNT = 10_000;
    private static final int MAX_OPTIONS_PER_ENTRY = 100;

    public byte[] encode(
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        if (answerKeyDefinition == null) {
            throw new IllegalArgumentException(
                    "O gabarito é obrigatório."
            );
        }

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);

            writeText(output, answerKeyDefinition.getId());
            output.writeInt(answerKeyDefinition.getVersion());
            writeText(output, answerKeyDefinition.getName());
            writeText(output, answerKeyDefinition.getLayoutId());
            output.writeInt(
                    answerKeyDefinition.getLayoutVersion()
            );

            List<OmrAnswerKeyEntry> entries =
                    answerKeyDefinition.getEntries();

            writeCount(
                    output,
                    entries.size(),
                    MAX_ENTRY_COUNT,
                    "questões"
            );

            for (OmrAnswerKeyEntry entry : entries) {
                writeText(output, entry.getQuestionId());
                output.writeDouble(entry.getWeight());

                Set<String> acceptedOptionIds =
                        entry.getAcceptedOptionIds();

                writeCount(
                        output,
                        acceptedOptionIds.size(),
                        MAX_OPTIONS_PER_ENTRY,
                        "alternativas aceitas"
                );

                for (String optionId : acceptedOptionIds) {
                    writeText(output, optionId);
                }
            }

            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível codificar o gabarito.",
                    exception
            );
        }

        byte[] payload = byteOutput.toByteArray();

        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "O gabarito excede o tamanho máximo permitido."
            );
        }

        return payload;
    }

    public OmrAnswerKeyDefinition decode(
            byte[] payload
    ) {
        if (payload == null || payload.length == 0) {
            throw invalidPayload(null);
        }

        if (payload.length > MAX_PAYLOAD_BYTES) {
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
                        "Versão de armazenamento não suportada: "
                                + formatVersion
                );
            }

            String answerKeyId = readText(input);
            int answerKeyVersion = readPositiveInt(input);
            String answerKeyName = readText(input);
            String layoutId = readText(input);
            int layoutVersion = readPositiveInt(input);

            int entryCount = readCount(
                    input,
                    MAX_ENTRY_COUNT
            );

            List<OmrAnswerKeyEntry> entries =
                    new ArrayList<>(entryCount);

            for (int entryIndex = 0;
                 entryIndex < entryCount;
                 entryIndex++) {

                String questionId = readText(input);
                double weight = input.readDouble();

                int optionCount = readCount(
                        input,
                        MAX_OPTIONS_PER_ENTRY
                );

                Set<String> acceptedOptionIds =
                        new LinkedHashSet<>(optionCount);

                for (int optionIndex = 0;
                     optionIndex < optionCount;
                     optionIndex++) {

                    if (!acceptedOptionIds.add(
                            readText(input)
                    )) {
                        throw invalidPayload(null);
                    }
                }

                entries.add(
                        new OmrAnswerKeyEntry(
                                questionId,
                                acceptedOptionIds,
                                weight
                        )
                );
            }

            if (input.read() != -1) {
                throw invalidPayload(null);
            }

            return new OmrAnswerKeyDefinition(
                    answerKeyId,
                    answerKeyVersion,
                    answerKeyName,
                    layoutId,
                    layoutVersion,
                    entries
            );

        } catch (EOFException exception) {
            throw invalidPayload(exception);

        } catch (IOException exception) {
            throw invalidPayload(exception);

        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(
                    "Versão de armazenamento não suportada:"
            )) {
                throw exception;
            }

            throw invalidPayload(exception);
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
                    "Texto fora do limite permitido."
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

        return new String(
                bytes,
                StandardCharsets.UTF_8
        );
    }

    private static void writeCount(
            DataOutputStream output,
            int count,
            int maximum,
            String description
    ) throws IOException {
        if (count <= 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Quantidade inválida de "
                            + description
                            + ": "
                            + count
            );
        }

        output.writeInt(count);
    }

    private static int readCount(
            DataInputStream input,
            int maximum
    ) throws IOException {
        int count = input.readInt();

        if (count <= 0 || count > maximum) {
            throw invalidPayload(null);
        }

        return count;
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
                "Os dados persistidos do gabarito são inválidos.",
                cause
        );
    }
}
