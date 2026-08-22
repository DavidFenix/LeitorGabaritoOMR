package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resultado da associação individual entre alvos esperados e
 * candidatos observados.
 *
 * Um alvo pode ter no máximo uma correspondência e um candidato
 * também pode ser utilizado no máximo uma vez.
 */
public final class BubbleCandidateMatchingResult {

    private final boolean success;

    private final List<ExpectedBubbleTarget> targets;
    private final List<BubbleContourCandidate> candidates;
    private final List<BubbleCandidateMatch> matches;

    private final List<ExpectedBubbleTarget>
            unmatchedTargets;

    private final List<BubbleContourCandidate>
            unmatchedCandidates;

    private final Map<String, BubbleCandidateMatch>
            matchByOptionId;

    private final Map<Integer, BubbleCandidateMatch>
            matchByCandidateId;

    private final String message;

    private BubbleCandidateMatchingResult(
            boolean success,
            List<ExpectedBubbleTarget> targets,
            List<BubbleContourCandidate> candidates,
            List<BubbleCandidateMatch> matches,
            String message
    ) {
        if (targets == null
                || candidates == null
                || matches == null) {

            throw new IllegalArgumentException(
                    "As listas do resultado são obrigatórias."
            );
        }

        this.success = success;

        this.targets = immutableCopy(targets);
        this.candidates = immutableCopy(candidates);
        this.matches = immutableCopy(matches);

        this.message =
                message == null
                        ? ""
                        : message.trim();

        Set<String> validOptionIds =
                collectTargetIds(this.targets);

        Set<Integer> validCandidateIds =
                collectCandidateIds(
                        this.candidates
                );

        Map<String, BubbleCandidateMatch>
                mutableMatchByOptionId =
                new HashMap<>();

        Map<Integer, BubbleCandidateMatch>
                mutableMatchByCandidateId =
                new HashMap<>();

        for (BubbleCandidateMatch match
                : this.matches) {

            if (match == null) {
                throw new IllegalArgumentException(
                        "A lista possui correspondência nula."
                );
            }

            String optionId =
                    match.getTarget()
                            .getOptionId();

            int candidateId =
                    match.getCandidate()
                            .getCandidateId();

            if (!validOptionIds.contains(optionId)) {
                throw new IllegalArgumentException(
                        "A correspondência contém alvo desconhecido: "
                                + optionId
                );
            }

            if (!validCandidateIds.contains(candidateId)) {
                throw new IllegalArgumentException(
                        "A correspondência contém candidato desconhecido: "
                                + candidateId
                );
            }

            if (mutableMatchByOptionId.put(
                    optionId,
                    match
            ) != null) {

                throw new IllegalArgumentException(
                        "Mais de um candidato foi associado a "
                                + optionId
                );
            }

            if (mutableMatchByCandidateId.put(
                    candidateId,
                    match
            ) != null) {

                throw new IllegalArgumentException(
                        "O candidato foi utilizado mais de uma vez: "
                                + candidateId
                );
            }
        }

        this.matchByOptionId =
                Collections.unmodifiableMap(
                        mutableMatchByOptionId
                );

        this.matchByCandidateId =
                Collections.unmodifiableMap(
                        mutableMatchByCandidateId
                );

        this.unmatchedTargets =
                createUnmatchedTargets(
                        this.targets,
                        mutableMatchByOptionId
                );

        this.unmatchedCandidates =
                createUnmatchedCandidates(
                        this.candidates,
                        mutableMatchByCandidateId
                );
    }

    public static BubbleCandidateMatchingResult success(
            List<ExpectedBubbleTarget> targets,
            List<BubbleContourCandidate> candidates,
            List<BubbleCandidateMatch> matches
    ) {
        return new BubbleCandidateMatchingResult(
                true,
                targets,
                candidates,
                matches,
                "Associação individual concluída."
        );
    }

    public static BubbleCandidateMatchingResult failure(
            String message
    ) {
        return new BubbleCandidateMatchingResult(
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                message
        );
    }

    private <T> List<T> immutableCopy(
            List<T> source
    ) {
        return Collections.unmodifiableList(
                new ArrayList<>(source)
        );
    }

    private Set<String> collectTargetIds(
            List<ExpectedBubbleTarget> source
    ) {
        Set<String> ids = new HashSet<>();

        for (ExpectedBubbleTarget target
                : source) {

            if (target == null) {
                throw new IllegalArgumentException(
                        "A lista possui alvo nulo."
                );
            }

            if (!ids.add(target.getOptionId())) {
                throw new IllegalArgumentException(
                        "Alvo duplicado: "
                                + target.getOptionId()
                );
            }
        }

        return ids;
    }

    private Set<Integer> collectCandidateIds(
            List<BubbleContourCandidate> source
    ) {
        Set<Integer> ids = new HashSet<>();

        for (BubbleContourCandidate candidate
                : source) {

            if (candidate == null) {
                throw new IllegalArgumentException(
                        "A lista possui candidato nulo."
                );
            }

            if (!ids.add(candidate.getCandidateId())) {
                throw new IllegalArgumentException(
                        "candidateId duplicado: "
                                + candidate.getCandidateId()
                );
            }
        }

        return ids;
    }

    private List<ExpectedBubbleTarget>
    createUnmatchedTargets(
            List<ExpectedBubbleTarget> source,
            Map<String, BubbleCandidateMatch> matched
    ) {
        List<ExpectedBubbleTarget> result =
                new ArrayList<>();

        for (ExpectedBubbleTarget target
                : source) {

            if (!matched.containsKey(
                    target.getOptionId()
            )) {
                result.add(target);
            }
        }

        return immutableCopy(result);
    }

    private List<BubbleContourCandidate>
    createUnmatchedCandidates(
            List<BubbleContourCandidate> source,
            Map<Integer, BubbleCandidateMatch> matched
    ) {
        List<BubbleContourCandidate> result =
                new ArrayList<>();

        for (BubbleContourCandidate candidate
                : source) {

            if (!matched.containsKey(
                    candidate.getCandidateId()
            )) {
                result.add(candidate);
            }
        }

        return immutableCopy(result);
    }

    public boolean isSuccess() {
        return success;
    }

    public List<ExpectedBubbleTarget> getTargets() {
        return targets;
    }

    public List<BubbleContourCandidate> getCandidates() {
        return candidates;
    }

    public List<BubbleCandidateMatch> getMatches() {
        return matches;
    }

    public List<ExpectedBubbleTarget> getUnmatchedTargets() {
        return unmatchedTargets;
    }

    public List<BubbleContourCandidate>
    getUnmatchedCandidates() {

        return unmatchedCandidates;
    }

    public int getTargetCount() {
        return targets.size();
    }

    public int getCandidateCount() {
        return candidates.size();
    }

    public int getMatchCount() {
        return matches.size();
    }

    public double getDirectMatchRatio() {
        if (targets.isEmpty()) {
            return 0.0;
        }

        return matches.size()
                / (double) targets.size();
    }

    public BubbleCandidateMatch findByOptionId(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        return matchByOptionId.get(optionId);
    }

    public BubbleCandidateMatch findByCandidateId(
            int candidateId
    ) {
        return matchByCandidateId.get(
                candidateId
        );
    }

    public String getMessage() {
        return message;
    }
}
