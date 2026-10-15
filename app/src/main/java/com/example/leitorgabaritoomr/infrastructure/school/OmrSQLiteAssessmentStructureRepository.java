package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentStructureRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentScoringPolicy;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Repositorio SQLite das estruturas curriculares versionadas das provas. */
public final class OmrSQLiteAssessmentStructureRepository
        implements OmrAssessmentStructureRepository,
        AutoCloseable {

    private static final String POLICY_TYPE_PROPORTIONAL =
            "proportional";

    private static final String[] STRUCTURE_PROJECTION = {
            OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
            OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
            OmrSchoolDatabase.COLUMN_STRUCTURE_QUESTION_COUNT,
            OmrSchoolDatabase.COLUMN_STRUCTURE_PAYLOAD
    };

    private static final String[] BLOCK_PROJECTION = {
            OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
            OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
            OmrSchoolDatabase.COLUMN_BLOCK_ID,
            OmrSchoolDatabase.COLUMN_BLOCK_ORDER,
            OmrSchoolDatabase.COLUMN_AREA_NAME,
            OmrSchoolDatabase.COLUMN_COMPONENT_CODE,
            OmrSchoolDatabase.COLUMN_COMPONENT_NAME,
            OmrSchoolDatabase.COLUMN_FIRST_QUESTION_POSITION,
            OmrSchoolDatabase.COLUMN_BLOCK_QUESTION_COUNT,
            OmrSchoolDatabase.COLUMN_POLICY_ID,
            OmrSchoolDatabase.COLUMN_POLICY_VERSION,
            OmrSchoolDatabase.COLUMN_BLOCK_PAYLOAD
    };

    private static final String[] POLICY_PROJECTION = {
            OmrSchoolDatabase.COLUMN_POLICY_ID,
            OmrSchoolDatabase.COLUMN_POLICY_VERSION,
            OmrSchoolDatabase.COLUMN_POLICY_TYPE,
            OmrSchoolDatabase.COLUMN_POLICY_BASIS,
            OmrSchoolDatabase.COLUMN_MAXIMUM_SCORE,
            OmrSchoolDatabase.COLUMN_DECIMAL_PLACES,
            OmrSchoolDatabase.COLUMN_ROUNDING_MODE,
            OmrSchoolDatabase.COLUMN_POLICY_PAYLOAD
    };

    private static final String[] ASSESSMENT_PROJECTION = {
            OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
            OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
            OmrSchoolDatabase.COLUMN_TITLE,
            OmrSchoolDatabase.COLUMN_SUBJECT,
            OmrSchoolDatabase.COLUMN_GRADE_LEVEL,
            OmrSchoolDatabase.COLUMN_ACADEMIC_PERIOD,
            OmrSchoolDatabase.COLUMN_SCHOOL_YEAR,
            OmrSchoolDatabase.COLUMN_ANSWER_KEY_ID,
            OmrSchoolDatabase.COLUMN_ANSWER_KEY_VERSION,
            OmrSchoolDatabase.COLUMN_PAYLOAD
    };

    private final OmrSchoolDatabase database;
    private final OmrAssessmentDefinitionBinaryCodec assessmentCodec;
    private final OmrAssessmentScoringPolicyBinaryCodec policyCodec;
    private final OmrAssessmentBlockBinaryCodec blockCodec;
    private final OmrAssessmentStructureBinaryCodec structureCodec;

    public OmrSQLiteAssessmentStructureRepository(Context context) {
        this(context, OmrSchoolDatabase.DEFAULT_DATABASE_NAME);
    }

    public OmrSQLiteAssessmentStructureRepository(
            Context context,
            String databaseName
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto Android e obrigatorio."
            );
        }

        if (databaseName == null
                || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco escolar e obrigatorio."
            );
        }

        Context applicationContext = context.getApplicationContext();
        database = new OmrSchoolDatabase(
                applicationContext == null ? context : applicationContext,
                databaseName.trim()
        );

        assessmentCodec = new OmrAssessmentDefinitionBinaryCodec();
        policyCodec = new OmrAssessmentScoringPolicyBinaryCodec();
        blockCodec = new OmrAssessmentBlockBinaryCodec();
        structureCodec = new OmrAssessmentStructureBinaryCodec();
    }

    @Override
    public boolean save(OmrAssessmentStructure structure) {
        if (structure == null) {
            throw new IllegalArgumentException(
                    "A estrutura da prova e obrigatoria."
            );
        }

        SQLiteDatabase writable = database.getWritableDatabase();

        if (findHealthyAssessmentOrNull(
                writable,
                structure.getAssessmentId(),
                structure.getAssessmentVersion()
        ) == null) {
            throw new IllegalArgumentException(
                    "A versao da prova deve existir e estar integra"
                            + " antes de salvar sua estrutura."
            );
        }

        writable.beginTransaction();
        try {
            if (structureRowExists(
                    writable,
                    structure.getAssessmentId(),
                    structure.getAssessmentVersion()
            )) {
                writable.setTransactionSuccessful();
                return false;
            }

            for (OmrAssessmentBlock block : structure.getBlocks()) {
                persistOrValidatePolicy(
                        writable,
                        block.getScoringPolicy()
                );
            }

            long structureRow = writable.insertWithOnConflict(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE,
                    null,
                    structureValuesOf(structure),
                    SQLiteDatabase.CONFLICT_IGNORE
            );

            if (structureRow == -1L) {
                throw new IllegalStateException(
                        "Nao foi possivel inserir a estrutura da prova."
                );
            }

            for (OmrAssessmentBlock block : structure.getBlocks()) {
                long blockRow = writable.insert(
                        OmrSchoolDatabase.TABLE_ASSESSMENT_BLOCK,
                        null,
                        blockValuesOf(structure, block)
                );

                if (blockRow == -1L) {
                    throw new IllegalStateException(
                            "Nao foi possivel inserir todos os blocos"
                                    + " da estrutura."
                    );
                }
            }

            writable.setTransactionSuccessful();
            return true;
        } finally {
            writable.endTransaction();
        }
    }

    @Override
    public OmrAssessmentStructure findStoredOrNull(
            String assessmentId,
            int assessmentVersion
    ) {
        String normalizedId = normalizeOrNull(assessmentId);
        if (normalizedId == null || assessmentVersion <= 0) {
            return null;
        }

        return lookupStored(
                database.getReadableDatabase(),
                normalizedId,
                assessmentVersion
        ).structure;
    }

    @Override
    public List<OmrAssessmentStructure> loadStoredVersions(
            String assessmentId
    ) {
        String normalizedId = normalizeOrNull(assessmentId);
        if (normalizedId == null) {
            return Collections.emptyList();
        }

        SQLiteDatabase readable = database.getReadableDatabase();
        List<OmrAssessmentStructure> structures = new ArrayList<>();

        try (Cursor cursor = readable.query(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE,
                STRUCTURE_PROJECTION,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID + " = ?",
                new String[]{normalizedId},
                null,
                null,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION + " DESC"
        )) {
            while (cursor.moveToNext()) {
                OmrAssessmentStructure structure =
                        decodeAndValidateStructureOrNull(
                                readable,
                                cursor
                        );

                if (structure != null) {
                    structures.add(structure);
                }
            }
        }

        return Collections.unmodifiableList(structures);
    }

    @Override
    public OmrAssessmentStructure resolveOrLegacy(
            String assessmentId,
            int assessmentVersion,
            int questionCount
    ) {
        String normalizedId = normalizeOrNull(assessmentId);
        if (normalizedId == null
                || assessmentVersion <= 0
                || questionCount <= 0) {
            return null;
        }

        SQLiteDatabase readable = database.getReadableDatabase();
        StructureLookup lookup = lookupStored(
                readable,
                normalizedId,
                assessmentVersion
        );

        if (lookup.exists) {
            return lookup.structure != null
                    && lookup.structure.getQuestionCount()
                    == questionCount
                    ? lookup.structure
                    : null;
        }

        if (findHealthyAssessmentOrNull(
                readable,
                normalizedId,
                assessmentVersion
        ) == null) {
            return null;
        }

        return OmrAssessmentStructure.legacySingleBlock(
                normalizedId,
                assessmentVersion,
                questionCount
        );
    }

    @Override
    public void close() {
        database.close();
    }

    private void persistOrValidatePolicy(
            SQLiteDatabase writable,
            OmrAssessmentScoringPolicy policy
    ) {
        ContentValues values = policyValuesOf(policy);

        long row = writable.insertWithOnConflict(
                OmrSchoolDatabase.TABLE_ASSESSMENT_SCORING_POLICY,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        if (row != -1L) {
            return;
        }

        OmrAssessmentScoringPolicy stored = findPolicyOrNull(
                writable,
                policy.getPolicyId(),
                policy.getVersion()
        );

        if (!policy.equals(stored)) {
            throw new IllegalStateException(
                    "A identidade da politica de nota ja existe"
                            + " com outra configuracao: "
                            + policy.getPolicyId()
                            + "@v"
                            + policy.getVersion()
            );
        }
    }

    private ContentValues structureValuesOf(
            OmrAssessmentStructure structure
    ) {
        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
                structure.getAssessmentId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
                structure.getAssessmentVersion()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_STRUCTURE_QUESTION_COUNT,
                structure.getQuestionCount()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_STRUCTURE_PAYLOAD,
                structureCodec.encode(structure)
        );
        return values;
    }

    private ContentValues blockValuesOf(
            OmrAssessmentStructure structure,
            OmrAssessmentBlock block
    ) {
        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
                structure.getAssessmentId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
                structure.getAssessmentVersion()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_BLOCK_ID,
                block.getBlockId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_BLOCK_ORDER,
                block.getOrder()
        );
        putNullable(
                values,
                OmrSchoolDatabase.COLUMN_AREA_NAME,
                block.getAreaName()
        );
        putNullable(
                values,
                OmrSchoolDatabase.COLUMN_COMPONENT_CODE,
                block.getComponentCode()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_COMPONENT_NAME,
                block.getComponentName()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_FIRST_QUESTION_POSITION,
                block.getFirstQuestionPosition()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_BLOCK_QUESTION_COUNT,
                block.getQuestionCount()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_POLICY_ID,
                block.getScoringPolicy().getPolicyId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_POLICY_VERSION,
                block.getScoringPolicy().getVersion()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_BLOCK_PAYLOAD,
                blockCodec.encode(block)
        );
        return values;
    }

    private ContentValues policyValuesOf(
            OmrAssessmentScoringPolicy policy
    ) {
        byte[] payload = policyCodec.encode(policy);

        if (!(policy instanceof OmrProportionalScoringPolicy)) {
            throw new IllegalArgumentException(
                    "Tipo de politica de nota ainda nao suportado."
            );
        }

        OmrProportionalScoringPolicy proportional =
                (OmrProportionalScoringPolicy) policy;

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_POLICY_ID,
                proportional.getPolicyId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_POLICY_VERSION,
                proportional.getVersion()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_POLICY_TYPE,
                POLICY_TYPE_PROPORTIONAL
        );
        values.put(
                OmrSchoolDatabase.COLUMN_POLICY_BASIS,
                proportional.getBasis().name()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_MAXIMUM_SCORE,
                proportional.getMaximumScore().toPlainString()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_DECIMAL_PLACES,
                proportional.getDecimalPlaces()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ROUNDING_MODE,
                proportional.getRoundingMode().name()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_POLICY_PAYLOAD,
                payload
        );
        return values;
    }

    private StructureLookup lookupStored(
            SQLiteDatabase readable,
            String assessmentId,
            int assessmentVersion
    ) {
        try (Cursor cursor = readable.query(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE,
                STRUCTURE_PROJECTION,
                exactAssessmentSelection(),
                exactAssessmentArguments(
                        assessmentId,
                        assessmentVersion
                ),
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return StructureLookup.missing();
            }

            return StructureLookup.present(
                    decodeAndValidateStructureOrNull(
                            readable,
                            cursor
                    )
            );
        }
    }

    private OmrAssessmentStructure decodeAndValidateStructureOrNull(
            SQLiteDatabase readable,
            Cursor cursor
    ) {
        try {
            OmrAssessmentStructure structure = structureCodec.decode(
                    blobAt(
                            cursor,
                            OmrSchoolDatabase.COLUMN_STRUCTURE_PAYLOAD
                    )
            );

            if (!metadataMatchesStructure(cursor, structure)) {
                return null;
            }

            List<OmrAssessmentBlock> storedBlocks =
                    loadAndValidateBlocksOrNull(
                            readable,
                            structure.getAssessmentId(),
                            structure.getAssessmentVersion()
                    );

            return storedBlocks != null
                    && storedBlocks.equals(structure.getBlocks())
                    ? structure
                    : null;

        } catch (RuntimeException exception) {
            return null;
        }
    }

    private List<OmrAssessmentBlock> loadAndValidateBlocksOrNull(
            SQLiteDatabase readable,
            String assessmentId,
            int assessmentVersion
    ) {
        List<OmrAssessmentBlock> blocks = new ArrayList<>();

        try (Cursor cursor = readable.query(
                OmrSchoolDatabase.TABLE_ASSESSMENT_BLOCK,
                BLOCK_PROJECTION,
                exactAssessmentSelection(),
                exactAssessmentArguments(
                        assessmentId,
                        assessmentVersion
                ),
                null,
                null,
                OmrSchoolDatabase.COLUMN_BLOCK_ORDER + " ASC"
        )) {
            while (cursor.moveToNext()) {
                OmrAssessmentBlock block = blockCodec.decode(
                        blobAt(
                                cursor,
                                OmrSchoolDatabase.COLUMN_BLOCK_PAYLOAD
                        )
                );

                if (!metadataMatchesBlock(
                        cursor,
                        assessmentId,
                        assessmentVersion,
                        block
                )) {
                    return null;
                }

                OmrAssessmentScoringPolicy policy = findPolicyOrNull(
                        readable,
                        block.getScoringPolicy().getPolicyId(),
                        block.getScoringPolicy().getVersion()
                );

                if (!block.getScoringPolicy().equals(policy)) {
                    return null;
                }

                blocks.add(block);
            }

            return Collections.unmodifiableList(blocks);

        } catch (RuntimeException exception) {
            return null;
        }
    }

    private OmrAssessmentScoringPolicy findPolicyOrNull(
            SQLiteDatabase readable,
            String policyId,
            int policyVersion
    ) {
        try (Cursor cursor = readable.query(
                OmrSchoolDatabase.TABLE_ASSESSMENT_SCORING_POLICY,
                POLICY_PROJECTION,
                OmrSchoolDatabase.COLUMN_POLICY_ID
                        + " = ? AND "
                        + OmrSchoolDatabase.COLUMN_POLICY_VERSION
                        + " = ?",
                new String[]{
                        policyId,
                        Integer.toString(policyVersion)
                },
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            OmrAssessmentScoringPolicy policy = policyCodec.decode(
                    blobAt(
                            cursor,
                            OmrSchoolDatabase.COLUMN_POLICY_PAYLOAD
                    )
            );

            return metadataMatchesPolicy(cursor, policy)
                    ? policy
                    : null;

        } catch (RuntimeException exception) {
            return null;
        }
    }

    private OmrAssessmentDefinition findHealthyAssessmentOrNull(
            SQLiteDatabase readable,
            String assessmentId,
            int assessmentVersion
    ) {
        try (Cursor cursor = readable.query(
                OmrSchoolDatabase.TABLE_ASSESSMENT,
                ASSESSMENT_PROJECTION,
                exactAssessmentSelection(),
                exactAssessmentArguments(
                        assessmentId,
                        assessmentVersion
                ),
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            OmrAssessmentDefinition assessment = assessmentCodec.decode(
                    blobAt(cursor, OmrSchoolDatabase.COLUMN_PAYLOAD)
            );

            return metadataMatchesAssessment(cursor, assessment)
                    ? assessment
                    : null;

        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean structureRowExists(
            SQLiteDatabase readable,
            String assessmentId,
            int assessmentVersion
    ) {
        try (Cursor cursor = readable.query(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE,
                new String[]{OmrSchoolDatabase.COLUMN_ASSESSMENT_ID},
                exactAssessmentSelection(),
                exactAssessmentArguments(
                        assessmentId,
                        assessmentVersion
                ),
                null,
                null,
                null
        )) {
            return cursor.moveToFirst();
        }
    }

    private static boolean metadataMatchesStructure(
            Cursor cursor,
            OmrAssessmentStructure structure
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
        ).equals(structure.getAssessmentId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
        ) == structure.getAssessmentVersion()
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_STRUCTURE_QUESTION_COUNT
        ) == structure.getQuestionCount();
    }

    private static boolean metadataMatchesBlock(
            Cursor cursor,
            String assessmentId,
            int assessmentVersion,
            OmrAssessmentBlock block
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
        ).equals(assessmentId)
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
        ) == assessmentVersion
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_BLOCK_ID
        ).equals(block.getBlockId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_BLOCK_ORDER
        ) == block.getOrder()
                && Objects.equals(
                nullableTextAt(
                        cursor,
                        OmrSchoolDatabase.COLUMN_AREA_NAME
                ),
                block.getAreaName()
        )
                && Objects.equals(
                nullableTextAt(
                        cursor,
                        OmrSchoolDatabase.COLUMN_COMPONENT_CODE
                ),
                block.getComponentCode()
        )
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_COMPONENT_NAME
        ).equals(block.getComponentName())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_FIRST_QUESTION_POSITION
        ) == block.getFirstQuestionPosition()
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_BLOCK_QUESTION_COUNT
        ) == block.getQuestionCount()
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_POLICY_ID
        ).equals(block.getScoringPolicy().getPolicyId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_POLICY_VERSION
        ) == block.getScoringPolicy().getVersion();
    }

    private static boolean metadataMatchesPolicy(
            Cursor cursor,
            OmrAssessmentScoringPolicy policy
    ) {
        if (!(policy instanceof OmrProportionalScoringPolicy)) {
            return false;
        }

        OmrProportionalScoringPolicy proportional =
                (OmrProportionalScoringPolicy) policy;

        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_POLICY_ID
        ).equals(proportional.getPolicyId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_POLICY_VERSION
        ) == proportional.getVersion()
                && POLICY_TYPE_PROPORTIONAL.equals(textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_POLICY_TYPE
        ))
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_POLICY_BASIS
        ).equals(proportional.getBasis().name())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_MAXIMUM_SCORE
        ).equals(proportional.getMaximumScore().toPlainString())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_DECIMAL_PLACES
        ) == proportional.getDecimalPlaces()
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ROUNDING_MODE
        ).equals(proportional.getRoundingMode().name());
    }

    private static boolean metadataMatchesAssessment(
            Cursor cursor,
            OmrAssessmentDefinition assessment
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
        ).equals(assessment.getAssessmentId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
        ) == assessment.getVersion()
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_TITLE
        ).equals(assessment.getTitle())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_SUBJECT
        ).equals(assessment.getSubject())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_GRADE_LEVEL
        ).equals(assessment.getGradeLevel())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ACADEMIC_PERIOD
        ).equals(assessment.getAcademicPeriod())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_SCHOOL_YEAR
        ) == assessment.getSchoolYear()
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ANSWER_KEY_ID
        ).equals(assessment.getAnswerKeyId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ANSWER_KEY_VERSION
        ) == assessment.getAnswerKeyVersion();
    }

    private static String exactAssessmentSelection() {
        return OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
                + " = ? AND "
                + OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
                + " = ?";
    }

    private static String[] exactAssessmentArguments(
            String assessmentId,
            int assessmentVersion
    ) {
        return new String[]{
                assessmentId,
                Integer.toString(assessmentVersion)
        };
    }

    private static void putNullable(
            ContentValues values,
            String column,
            String value
    ) {
        if (value == null) {
            values.putNull(column);
        } else {
            values.put(column, value);
        }
    }

    private static String textAt(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private static String nullableTextAt(
            Cursor cursor,
            String column
    ) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static int intAt(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column));
    }

    private static byte[] blobAt(Cursor cursor, String column) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(column));
    }

    private static String normalizeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static final class StructureLookup {
        private final boolean exists;
        private final OmrAssessmentStructure structure;

        private StructureLookup(
                boolean exists,
                OmrAssessmentStructure structure
        ) {
            this.exists = exists;
            this.structure = structure;
        }

        private static StructureLookup missing() {
            return new StructureLookup(false, null);
        }

        private static StructureLookup present(
                OmrAssessmentStructure structure
        ) {
            return new StructureLookup(true, structure);
        }
    }
}
