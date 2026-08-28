package com.example.leitorgabaritoomr.infrastructure.grading;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.example.leitorgabaritoomr.application.grading.OmrActiveAnswerKeyStore;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Armazena o gabarito oficial ativo nas preferências privadas do aplicativo.
 *
 * O conteúdo é codificado pelo formato binário versionado e acompanhado de
 * SHA-256. Uma gravação somente é considerada concluída quando o Android
 * confirma de forma síncrona a atualização das duas informações.
 */
public final class OmrSharedPreferencesActiveAnswerKeyStore
        implements OmrActiveAnswerKeyStore {

    static final String PREFERENCES_NAME =
            "omr_active_answer_key";

    static final String KEY_PAYLOAD =
            "active_answer_key_payload";

    static final String KEY_CHECKSUM =
            "active_answer_key_sha256";

    private static final String TAG =
            "OmrAnswerKeyStore";

    private static final int MAX_ENCODED_PAYLOAD_LENGTH =
            14 * 1024 * 1024;

    private final SharedPreferences preferences;
    private final OmrAnswerKeyBinaryCodec codec;

    public OmrSharedPreferencesActiveAnswerKeyStore(
            Context context
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        Context applicationContext =
                context.getApplicationContext();

        Context storageContext =
                applicationContext == null
                        ? context
                        : applicationContext;

        this.preferences =
                storageContext.getSharedPreferences(
                        PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );

        this.codec = new OmrAnswerKeyBinaryCodec();
    }

    @Override
    public synchronized void saveActive(
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        if (answerKeyDefinition == null) {
            throw new IllegalArgumentException(
                    "O gabarito é obrigatório."
            );
        }

        byte[] payload = codec.encode(
                answerKeyDefinition
        );

        String encodedPayload = Base64.encodeToString(
                payload,
                Base64.NO_WRAP
        );

        String checksum = calculateChecksum(payload);

        boolean saved = preferences.edit()
                .putString(KEY_PAYLOAD, encodedPayload)
                .putString(KEY_CHECKSUM, checksum)
                .commit();

        if (!saved) {
            throw new IllegalStateException(
                    "O Android não confirmou a gravação"
                            + " do gabarito ativo."
            );
        }
    }

    @Override
    public synchronized OmrAnswerKeyDefinition
    loadActiveOrNull() {
        String encodedPayload;
        String persistedChecksum;

        try {
            encodedPayload = preferences.getString(
                    KEY_PAYLOAD,
                    null
            );

            persistedChecksum = preferences.getString(
                    KEY_CHECKSUM,
                    null
            );

        } catch (ClassCastException exception) {
            logInvalidStoredData(exception);
            return null;
        }

        if (encodedPayload == null
                && persistedChecksum == null) {
            return null;
        }

        if (encodedPayload == null
                || persistedChecksum == null
                || encodedPayload.isEmpty()
                || persistedChecksum.length() != 64
                || encodedPayload.length()
                > MAX_ENCODED_PAYLOAD_LENGTH) {

            logInvalidStoredData(null);
            return null;
        }

        try {
            byte[] payload = Base64.decode(
                    encodedPayload,
                    Base64.NO_WRAP
            );

            String calculatedChecksum =
                    calculateChecksum(payload);

            if (!sameChecksum(
                    persistedChecksum,
                    calculatedChecksum
            )) {
                logInvalidStoredData(null);
                return null;
            }

            return codec.decode(payload);

        } catch (IllegalArgumentException exception) {
            logInvalidStoredData(exception);
            return null;
        }
    }

    @Override
    public synchronized void clearActive() {
        boolean cleared = preferences.edit()
                .remove(KEY_PAYLOAD)
                .remove(KEY_CHECKSUM)
                .commit();

        if (!cleared) {
            throw new IllegalStateException(
                    "O Android não confirmou a remoção"
                            + " do gabarito ativo."
            );
        }
    }

    private static String calculateChecksum(
            byte[] payload
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    "SHA-256"
            );

            byte[] checksum = digest.digest(payload);
            StringBuilder hexadecimal =
                    new StringBuilder(checksum.length * 2);

            for (byte value : checksum) {
                int unsigned = value & 0xFF;

                hexadecimal.append(
                        Character.forDigit(
                                unsigned >>> 4,
                                16
                        )
                );

                hexadecimal.append(
                        Character.forDigit(
                                unsigned & 0x0F,
                                16
                        )
                );
            }

            return hexadecimal.toString();

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 não está disponível neste dispositivo.",
                    exception
            );
        }
    }

    private static boolean sameChecksum(
            String first,
            String second
    ) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static void logInvalidStoredData(
            Throwable cause
    ) {
        if (cause == null) {
            Log.e(
                    TAG,
                    "O gabarito persistido foi ignorado"
                            + " porque está incompleto ou inválido."
            );

            return;
        }

        Log.e(
                TAG,
                "O gabarito persistido foi ignorado"
                        + " porque está incompleto ou inválido.",
                cause
        );
    }
}
