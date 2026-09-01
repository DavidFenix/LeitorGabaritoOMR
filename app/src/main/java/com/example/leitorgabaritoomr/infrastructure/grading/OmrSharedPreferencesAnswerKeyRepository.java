package com.example.leitorgabaritoomr.infrastructure.grading;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repositório privado dos gabaritos oficiais disponíveis no dispositivo.
 *
 * A coleção completa e sua seleção ativa são persistidas em uma única
 * fotografia binária, acompanhada de SHA-256. Isso impede que uma alteração
 * deixe a lista e o gabarito ativo em estados diferentes.
 *
 * Quando ainda não existe uma fotografia do repositório, o gabarito salvo
 * pelo armazenamento antigo é migrado automaticamente, sem remover os dados
 * anteriores. Essa preservação permite voltar temporariamente à versão antiga
 * do aplicativo sem perder o gabarito que já estava ativo.
 */
public final class OmrSharedPreferencesAnswerKeyRepository
        implements OmrAnswerKeyRepository {

    static final String PREFERENCES_NAME =
            "omr_answer_key_repository";

    static final String KEY_PAYLOAD =
            "answer_key_repository_payload";

    static final String KEY_CHECKSUM =
            "answer_key_repository_sha256";

    private static final String TAG =
            "OmrAnswerKeyRepository";

    private static final int MAX_ENCODED_PAYLOAD_LENGTH =
            70 * 1024 * 1024;

    private final SharedPreferences preferences;
    private final OmrAnswerKeyRepositoryBinaryCodec codec;
    private final OmrSharedPreferencesActiveAnswerKeyStore legacyStore;

    public OmrSharedPreferencesAnswerKeyRepository(
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

        preferences = storageContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );

        codec = new OmrAnswerKeyRepositoryBinaryCodec();

        legacyStore =
                new OmrSharedPreferencesActiveAnswerKeyStore(
                        storageContext
                );
    }

    @Override
    public synchronized void save(
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        requireAnswerKey(answerKeyDefinition);

        RepositoryState current = loadStateOrEmpty();

        List<OmrAnswerKeyDefinition> updated =
                withoutIdentity(
                        current.answerKeys,
                        answerKeyDefinition.getId(),
                        answerKeyDefinition.getVersion()
                );

        updated.add(0, answerKeyDefinition);

        OmrAnswerKeyDefinition active =
                current.activeAnswerKey;

        if (sameIdentity(
                active,
                answerKeyDefinition
        )) {
            active = answerKeyDefinition;
        }

        persist(updated, active);
    }

    @Override
    public synchronized List<OmrAnswerKeyDefinition> loadAll() {
        return loadStateOrEmpty().answerKeys;
    }

    @Override
    public synchronized OmrAnswerKeyDefinition findOrNull(
            String answerKeyId,
            int answerKeyVersion
    ) {
        if (!isValidIdentity(
                answerKeyId,
                answerKeyVersion
        )) {
            return null;
        }

        return findIn(
                loadStateOrEmpty().answerKeys,
                answerKeyId,
                answerKeyVersion
        );
    }

    @Override
    public synchronized void selectActive(
            String answerKeyId,
            int answerKeyVersion
    ) {
        requireIdentity(
                answerKeyId,
                answerKeyVersion
        );

        RepositoryState current = loadStateOrEmpty();

        OmrAnswerKeyDefinition selected = findIn(
                current.answerKeys,
                answerKeyId,
                answerKeyVersion
        );

        if (selected == null) {
            throw new IllegalArgumentException(
                    "O gabarito solicitado não está armazenado."
            );
        }

        persist(current.answerKeys, selected);
    }

    @Override
    public synchronized boolean delete(
            String answerKeyId,
            int answerKeyVersion
    ) {
        if (!isValidIdentity(
                answerKeyId,
                answerKeyVersion
        )) {
            return false;
        }

        RepositoryState current = loadStateOrEmpty();

        OmrAnswerKeyDefinition existing = findIn(
                current.answerKeys,
                answerKeyId,
                answerKeyVersion
        );

        if (existing == null) {
            return false;
        }

        List<OmrAnswerKeyDefinition> updated =
                withoutIdentity(
                        current.answerKeys,
                        answerKeyId,
                        answerKeyVersion
                );

        OmrAnswerKeyDefinition active =
                sameIdentity(
                        current.activeAnswerKey,
                        existing
                )
                        ? null
                        : current.activeAnswerKey;

        persist(updated, active);
        return true;
    }

    @Override
    public synchronized void saveActive(
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        requireAnswerKey(answerKeyDefinition);

        RepositoryState current = loadStateOrEmpty();

        List<OmrAnswerKeyDefinition> updated =
                withoutIdentity(
                        current.answerKeys,
                        answerKeyDefinition.getId(),
                        answerKeyDefinition.getVersion()
                );

        updated.add(0, answerKeyDefinition);

        persist(updated, answerKeyDefinition);
    }

    @Override
    public synchronized OmrAnswerKeyDefinition
    loadActiveOrNull() {
        return loadStateOrEmpty().activeAnswerKey;
    }

    @Override
    public synchronized void clearActive() {
        RepositoryState current = loadStateOrEmpty();

        if (current.activeAnswerKey == null) {
            return;
        }

        persist(current.answerKeys, null);
    }

    private RepositoryState loadStateOrEmpty() {
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
            return RepositoryState.empty();
        }

        if (encodedPayload == null
                && persistedChecksum == null) {
            return migrateLegacyOrEmpty();
        }

        if (encodedPayload == null
                || persistedChecksum == null
                || encodedPayload.isEmpty()
                || persistedChecksum.length() != 64
                || encodedPayload.length()
                > MAX_ENCODED_PAYLOAD_LENGTH) {

            logInvalidStoredData(null);
            return RepositoryState.empty();
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
                return RepositoryState.empty();
            }

            OmrAnswerKeyRepositoryBinaryCodec.Snapshot snapshot =
                    codec.decode(payload);

            return new RepositoryState(
                    snapshot.getAnswerKeys(),
                    snapshot.getActiveAnswerKeyOrNull()
            );

        } catch (IllegalArgumentException exception) {
            logInvalidStoredData(exception);
            return RepositoryState.empty();
        }
    }

    private RepositoryState migrateLegacyOrEmpty() {
        OmrAnswerKeyDefinition legacyAnswerKey =
                legacyStore.loadActiveOrNull();

        if (legacyAnswerKey == null) {
            return RepositoryState.empty();
        }

        List<OmrAnswerKeyDefinition> migrated =
                Collections.singletonList(legacyAnswerKey);

        persist(migrated, legacyAnswerKey);

        Log.i(
                TAG,
                "O gabarito ativo anterior foi migrado"
                        + " para o novo repositório."
        );

        return new RepositoryState(
                migrated,
                legacyAnswerKey
        );
    }

    private void persist(
            List<OmrAnswerKeyDefinition> answerKeys,
            OmrAnswerKeyDefinition activeAnswerKey
    ) {
        byte[] payload = codec.encode(
                answerKeys,
                activeAnswerKey
        );

        String encodedPayload = Base64.encodeToString(
                payload,
                Base64.NO_WRAP
        );

        if (encodedPayload.length()
                > MAX_ENCODED_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(
                    "O repositório excede o tamanho permitido."
            );
        }

        String checksum = calculateChecksum(payload);

        boolean saved = preferences.edit()
                .putString(KEY_PAYLOAD, encodedPayload)
                .putString(KEY_CHECKSUM, checksum)
                .commit();

        if (!saved) {
            throw new IllegalStateException(
                    "O Android não confirmou a gravação"
                            + " do repositório de gabaritos."
            );
        }
    }

    private static List<OmrAnswerKeyDefinition>
    withoutIdentity(
            List<OmrAnswerKeyDefinition> answerKeys,
            String answerKeyId,
            int answerKeyVersion
    ) {
        List<OmrAnswerKeyDefinition> result =
                new ArrayList<>(answerKeys.size());

        for (OmrAnswerKeyDefinition answerKey
                : answerKeys) {

            if (!sameIdentity(
                    answerKey,
                    answerKeyId,
                    answerKeyVersion
            )) {
                result.add(answerKey);
            }
        }

        return result;
    }

    private static OmrAnswerKeyDefinition findIn(
            List<OmrAnswerKeyDefinition> answerKeys,
            String answerKeyId,
            int answerKeyVersion
    ) {
        for (OmrAnswerKeyDefinition answerKey
                : answerKeys) {

            if (sameIdentity(
                    answerKey,
                    answerKeyId,
                    answerKeyVersion
            )) {
                return answerKey;
            }
        }

        return null;
    }

    private static boolean sameIdentity(
            OmrAnswerKeyDefinition first,
            OmrAnswerKeyDefinition second
    ) {
        return first != null
                && second != null
                && sameIdentity(
                first,
                second.getId(),
                second.getVersion()
        );
    }

    private static boolean sameIdentity(
            OmrAnswerKeyDefinition answerKey,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return answerKey != null
                && answerKey.getVersion()
                == answerKeyVersion
                && answerKey.getId().equals(answerKeyId);
    }

    private static void requireAnswerKey(
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        if (answerKeyDefinition == null) {
            throw new IllegalArgumentException(
                    "O gabarito é obrigatório."
            );
        }
    }

    private static void requireIdentity(
            String answerKeyId,
            int answerKeyVersion
    ) {
        if (!isValidIdentity(
                answerKeyId,
                answerKeyVersion
        )) {
            throw new IllegalArgumentException(
                    "A identidade do gabarito é inválida."
            );
        }
    }

    private static boolean isValidIdentity(
            String answerKeyId,
            int answerKeyVersion
    ) {
        return answerKeyId != null
                && !answerKeyId.trim().isEmpty()
                && answerKeyVersion > 0;
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
        String message =
                "O repositório persistido foi ignorado"
                        + " porque está incompleto ou inválido.";

        if (cause == null) {
            Log.e(TAG, message);
            return;
        }

        Log.e(TAG, message, cause);
    }

    private static final class RepositoryState {

        private final List<OmrAnswerKeyDefinition> answerKeys;
        private final OmrAnswerKeyDefinition activeAnswerKey;

        private RepositoryState(
                List<OmrAnswerKeyDefinition> answerKeys,
                OmrAnswerKeyDefinition activeAnswerKey
        ) {
            this.answerKeys = Collections.unmodifiableList(
                    new ArrayList<>(answerKeys)
            );

            this.activeAnswerKey = activeAnswerKey;
        }

        private static RepositoryState empty() {
            return new RepositoryState(
                    Collections.<OmrAnswerKeyDefinition>emptyList(),
                    null
            );
        }
    }
}
