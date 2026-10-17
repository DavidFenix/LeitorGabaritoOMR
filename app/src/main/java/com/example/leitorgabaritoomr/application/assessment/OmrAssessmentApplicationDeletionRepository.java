package com.example.leitorgabaritoomr.application.assessment;

/**
 * Porta transacional para excluir uma aplicação ainda sem correções.
 */
public interface OmrAssessmentApplicationDeletionRepository {

    DeletionResult deleteIfUncorrected(String applicationId);

    final class DeletionResult {
        public enum Status {
            DELETED,
            NOT_FOUND,
            BLOCKED_BY_ATTEMPTS
        }

        private final Status status;
        private final int deletedParticipantCount;
        private final int blockingAttemptCount;

        private DeletionResult(
                Status status,
                int deletedParticipantCount,
                int blockingAttemptCount
        ) {
            this.status = status;
            this.deletedParticipantCount = deletedParticipantCount;
            this.blockingAttemptCount = blockingAttemptCount;
        }

        public static DeletionResult deleted(int participantCount) {
            return new DeletionResult(
                    Status.DELETED,
                    Math.max(0, participantCount),
                    0
            );
        }

        public static DeletionResult notFound() {
            return new DeletionResult(Status.NOT_FOUND, 0, 0);
        }

        public static DeletionResult blocked(int attemptCount) {
            return new DeletionResult(
                    Status.BLOCKED_BY_ATTEMPTS,
                    0,
                    Math.max(1, attemptCount)
            );
        }

        public Status getStatus() {
            return status;
        }

        public int getDeletedParticipantCount() {
            return deletedParticipantCount;
        }

        public int getBlockingAttemptCount() {
            return blockingAttemptCount;
        }

        public boolean isDeleted() {
            return status == Status.DELETED;
        }
    }
}
