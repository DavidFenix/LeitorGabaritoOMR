package com.example.leitorgabaritoomr.infrastructure.school;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentScoringPolicy;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;

import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Codec versionado das politicas de nota persistidas no banco escolar. */
public final class OmrAssessmentScoringPolicyBinaryCodec {

    private static final int MAGIC = 0x4F4D5259;
    private static final String POLICY_TYPE_PROPORTIONAL =
            "proportional";
    private static final String INVALID_MESSAGE =
            "Os dados persistidos da politica de nota sao invalidos.";

    public byte[] encode(OmrAssessmentScoringPolicy policy) {
        if (!(policy instanceof OmrProportionalScoringPolicy)) {
            throw new IllegalArgumentException(
                    "Tipo de politica de nota ainda nao suportado."
            );
        }

        OmrProportionalScoringPolicy proportional =
                (OmrProportionalScoringPolicy) policy;

        byte[] body = OmrAssessmentPersistenceBinaryCodecSupport
                .encodeBody(
                        output -> {
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeText(
                                            output,
                                            POLICY_TYPE_PROPORTIONAL
                                    );
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeText(
                                            output,
                                            proportional.getPolicyId()
                                    );
                            output.writeInt(proportional.getVersion());
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeText(
                                            output,
                                            proportional.getBasis().name()
                                    );
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeText(
                                            output,
                                            proportional
                                                    .getMaximumScore()
                                                    .toPlainString()
                                    );
                            output.writeInt(
                                    proportional.getDecimalPlaces()
                            );
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeText(
                                            output,
                                            proportional
                                                    .getRoundingMode()
                                                    .name()
                                    );
                        },
                        "Nao foi possivel codificar a politica de nota."
                );

        return OmrAssessmentPersistenceBinaryCodecSupport.envelope(
                MAGIC,
                body,
                INVALID_MESSAGE,
                "Nao foi possivel codificar a politica de nota."
        );
    }

    public OmrAssessmentScoringPolicy decode(byte[] payload) {
        byte[] body = OmrAssessmentPersistenceBinaryCodecSupport
                .decodeEnvelope(
                        payload,
                        MAGIC,
                        "Versao de politica de nota armazenada nao suportada: ",
                        INVALID_MESSAGE
                );

        try (DataInputStream input =
                     OmrAssessmentPersistenceBinaryCodecSupport
                             .inputOf(body)) {

            String policyType = readText(input);
            if (!POLICY_TYPE_PROPORTIONAL.equals(policyType)) {
                throw invalid(null);
            }

            String policyId = readText(input);
            int version = readPositiveInt(input);

            OmrProportionalScoringPolicy.Basis basis =
                    OmrProportionalScoringPolicy.Basis.valueOf(
                            readText(input)
                    );

            BigDecimal maximumScore = new BigDecimal(
                    readText(input)
            );

            int decimalPlaces = input.readInt();
            RoundingMode roundingMode = RoundingMode.valueOf(
                    readText(input)
            );

            OmrAssessmentPersistenceBinaryCodecSupport.requireEnd(
                    input,
                    INVALID_MESSAGE
            );

            return new OmrProportionalScoringPolicy(
                    policyId,
                    version,
                    basis,
                    maximumScore,
                    decimalPlaces,
                    roundingMode
            );

        } catch (IOException | RuntimeException exception) {
            throw invalid(exception);
        }
    }

    private static String readText(DataInputStream input)
            throws IOException {
        return OmrAssessmentPersistenceBinaryCodecSupport.readText(
                input,
                INVALID_MESSAGE
        );
    }

    private static int readPositiveInt(DataInputStream input)
            throws IOException {
        return OmrAssessmentPersistenceBinaryCodecSupport
                .readPositiveInt(input, INVALID_MESSAGE);
    }

    private static IllegalArgumentException invalid(Throwable cause) {
        return OmrAssessmentPersistenceBinaryCodecSupport.invalid(
                INVALID_MESSAGE,
                cause
        );
    }
}
