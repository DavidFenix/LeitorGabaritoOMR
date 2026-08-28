package com.example.leitorgabaritoomr.infrastructure.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Codifica uma fotografia completa do repositório de gabaritos.
 *
 * A ordem da lista é preservada e a seleção ativa é armazenada como índice da
 * própria fotografia. Cada item usa internamente o formato individual já
 * definido por {@link OmrAnswerKeyBinaryCodec}.
 */
public final class OmrAnswerKeyRepositoryBinaryCodec {

    public static final class Snapshot {

        private final List<OmrAnswerKeyDefinition> answerKeys;
        private final OmrAnswerKeyDefinition activeAnswerKey;

        private Snapshot(
                List<OmrAnswerKeyDefinition> answerKeys,
                OmrAnswerKeyDefinition activeAnswerKey
        ) {
            this.answerKeys = Collections.unmodifiableList(
                    new ArrayList<>(answerKeys)
            );

            this.activeAnswerKey = activeAnswerKey;
        }

        public List<OmrAnswerKeyDefinition> getAnswerKeys() {
            return answerKeys;
        }

        public OmrAnswerKeyDefinition getActiveAnswerKeyOrNull() {
            return activeAnswerKey;
        }

        public int getAnswerKeyCount() {
            return answerKeys.size();
        }

        public boolean hasActiveAnswerKey() {
            return activeAnswerKey != null;
        }
    }

    private static final int MAGIC = 0x4F4D5252;
    private static final int FORMAT_VERSION = 1;

    private static final int MAX_ANSWER_KEY_COUNT = 1_000;
    private static final int MAX_ANSWER_KEY_BYTES =
            10 * 1024 * 1024;

    private static final int MAX_REPOSITORY_BYTES =
            50 * 1024 * 1024;

    private final OmrAnswerKeyBinaryCodec answerKeyCodec;

    public OmrAnswerKeyRepositoryBinaryCodec() {
        answerKeyCodec = new OmrAnswerKeyBinaryCodec();
    }

    public byte[] encode(
            List<OmrAnswerKeyDefinition> answerKeys,
            OmrAnswerKeyDefinition activeAnswerKey
    ) {
        if (answerKeys == null) {
            throw new IllegalArgumentException(
                    "A lista de gabaritos é obrigatória."
            );
        }

        if (answerKeys.size() > MAX_ANSWER_KEY_COUNT) {
            throw new IllegalArgumentException(
                    "A quantidade de gabaritos excede"
                            + " o limite permitido."
            );
        }

        int activeIndex = -1;
        Set<String> identities =
                new HashSet<>(answerKeys.size());

        for (int index = 0;
             index < answerKeys.size();
             index++) {

            OmrAnswerKeyDefinition answerKey =
                    answerKeys.get(index);

            if (answerKey == null) {
                throw new IllegalArgumentException(
                        "A lista de gabaritos não pode"
                                + " conter valores nulos."
                );
            }

            if (!identities.add(identityOf(answerKey))) {
                throw new IllegalArgumentException(
                        "A fotografia contém uma identidade"
                                + " de gabarito repetida."
                );
            }

            if (activeAnswerKey != null
                    && sameIdentity(
                    answerKey,
                    activeAnswerKey
            )) {
                activeIndex = index;
            }
        }

        if (activeAnswerKey != null && activeIndex < 0) {
            throw new IllegalArgumentException(
                    "O gabarito ativo deve pertencer à coleção."
            );
        }

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(answerKeys.size());
            output.writeInt(activeIndex);

            for (OmrAnswerKeyDefinition answerKey
                    : answerKeys) {

                byte[] encodedAnswerKey =
                        answerKeyCodec.encode(answerKey);

                if (encodedAnswerKey.length == 0
                        || encodedAnswerKey.length
                        > MAX_ANSWER_KEY_BYTES) {

                    throw new IllegalArgumentException(
                            "Um gabarito excede o tamanho"
                                    + " permitido."
                    );
                }

                long projectedSize =
                        (long) byteOutput.size()
                                + 4L
                                + encodedAnswerKey.length;

                if (projectedSize > MAX_REPOSITORY_BYTES) {
                    throw new IllegalArgumentException(
                            "O repositório excede o tamanho"
                                    + " máximo permitido."
                    );
                }

                output.writeInt(encodedAnswerKey.length);
                output.write(encodedAnswerKey);
            }

            output.flush();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível codificar o repositório.",
                    exception
            );
        }

        byte[] payload = byteOutput.toByteArray();

        if (payload.length > MAX_REPOSITORY_BYTES) {
            throw new IllegalArgumentException(
                    "O repositório excede o tamanho"
                            + " máximo permitido."
            );
        }

        return payload;
    }

    public Snapshot decode(
            byte[] payload
    ) {
        if (payload == null
                || payload.length == 0
                || payload.length > MAX_REPOSITORY_BYTES) {

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
                        "Versão do repositório não suportada: "
                                + formatVersion
                );
            }

            int answerKeyCount = input.readInt();

            if (answerKeyCount < 0
                    || answerKeyCount > MAX_ANSWER_KEY_COUNT) {
                throw invalidPayload(null);
            }

            int activeIndex = input.readInt();

            if (activeIndex < -1
                    || activeIndex >= answerKeyCount) {
                throw invalidPayload(null);
            }

            List<OmrAnswerKeyDefinition> answerKeys =
                    new ArrayList<>(answerKeyCount);

            Set<String> identities =
                    new HashSet<>(answerKeyCount);

            for (int index = 0;
                 index < answerKeyCount;
                 index++) {

                int answerKeyByteCount = input.readInt();

                if (answerKeyByteCount <= 0
                        || answerKeyByteCount
                        > MAX_ANSWER_KEY_BYTES) {

                    throw invalidPayload(null);
                }

                byte[] encodedAnswerKey =
                        new byte[answerKeyByteCount];

                input.readFully(encodedAnswerKey);

                OmrAnswerKeyDefinition answerKey =
                        answerKeyCodec.decode(
                                encodedAnswerKey
                        );

                if (!identities.add(identityOf(answerKey))) {
                    throw invalidPayload(null);
                }

                answerKeys.add(answerKey);
            }

            if (input.read() != -1) {
                throw invalidPayload(null);
            }

            OmrAnswerKeyDefinition activeAnswerKey =
                    activeIndex < 0
                            ? null
                            : answerKeys.get(activeIndex);

            return new Snapshot(
                    answerKeys,
                    activeAnswerKey
            );

        } catch (EOFException exception) {
            throw invalidPayload(exception);

        } catch (IOException exception) {
            throw invalidPayload(exception);

        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(
                    "Versão do repositório não suportada:"
            )) {
                throw exception;
            }

            throw invalidPayload(exception);
        }
    }

    private static boolean sameIdentity(
            OmrAnswerKeyDefinition first,
            OmrAnswerKeyDefinition second
    ) {
        return first.getVersion() == second.getVersion()
                && first.getId().equals(second.getId());
    }

    private static String identityOf(
            OmrAnswerKeyDefinition answerKey
    ) {
        String id = answerKey.getId();

        return id.length()
                + ":"
                + id
                + "@"
                + answerKey.getVersion();
    }

    private static IllegalArgumentException invalidPayload(
            Throwable cause
    ) {
        return new IllegalArgumentException(
                "Os dados persistidos do repositório"
                        + " de gabaritos são inválidos.",
                cause
        );
    }
}
