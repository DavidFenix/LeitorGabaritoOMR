package com.example.leitorgabaritoomr.application.assessment;

/** Porta transacional para excluir uma prova somente quando nao estiver em uso. */
public interface OmrAssessmentDeletionRepository {

    DeletionResult deleteAllVersionsIfUnused(String assessmentId);

    final class DeletionResult {
        public enum Status {
            DELETED,
            NOT_FOUND,
            BLOCKED_BY_APPLICATIONS
        }

        private final Status status;
        private final int deletedVersionCount;
        private final int blockingApplicationCount;

        private DeletionResult(
                Status status,
                int deletedVersionCount,
                int blockingApplicationCount
        ) {
            this.status = status;
            this.deletedVersionCount = deletedVersionCount;
            this.blockingApplicationCount = blockingApplicationCount;
        }

        public static DeletionResult deleted(int versionCount) {
            return new DeletionResult(Status.DELETED, versionCount, 0);
        }

        public static DeletionResult notFound() {
            return new DeletionResult(Status.NOT_FOUND, 0, 0);
        }

        public static DeletionResult blocked(int applicationCount) {
            return new DeletionResult(
                    Status.BLOCKED_BY_APPLICATIONS,
                    0,
                    Math.max(1, applicationCount)
            );
        }

        public Status getStatus() {
            return status;
        }

        public int getDeletedVersionCount() {
            return deletedVersionCount;
        }

        public int getBlockingApplicationCount() {
            return blockingApplicationCount;
        }

        public boolean isDeleted() {
            return status == Status.DELETED;
        }
    }
}
