package com.gammonscope.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gammonscope.dto.MatchMetadata;
import com.gammonscope.dto.MatchSummary;
import com.gammonscope.model.BgMatch;
import com.gammonscope.model.Game;
import com.gammonscope.model.MoveRecord;
import com.gammonscope.repository.MatchRepository;

@Service
public class MatchService {

    private final MatchRepository matchRepository;

    public MatchService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @Transactional(readOnly = true)
    public List<MatchSummary> listSummaries() {
        return matchRepository.findAllSummaries();
    }

    @Transactional(readOnly = true)
    public Optional<BgMatch> getMatch(Long id) {
        return matchRepository.findById(id).map(this::initialize);
    }

    private BgMatch initialize(BgMatch match) {
        for (Game game : match.getGames()) {
            game.getMoves().size();
        }
        return match;
    }

    /** Creates a new manual match with the given metadata and a single empty game. */
    @Transactional
    public BgMatch createMatch(MatchMetadata meta) {
        BgMatch match = new BgMatch();
        applyMetadata(match, meta);
        match.setSourceFormat("MANUAL");
        match.setImportedAt(Instant.now());

        Game game = new Game();
        game.setGameNumber(1);
        game.setScore0(0);
        game.setScore1(0);
        game.setWinner(-1);
        match.addGame(game);

        return matchRepository.save(match);
    }

    /** Updates the editable match-level metadata; returns false if the match does not exist. */
    @Transactional
    public boolean updateMetadata(Long id, MatchMetadata meta) {
        Optional<BgMatch> opt = matchRepository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        BgMatch match = opt.get();
        applyMetadata(match, meta);
        matchRepository.save(match);
        return true;
    }

    private void applyMetadata(BgMatch match, MatchMetadata meta) {
        match.setPlayer0(blankToDefault(meta.player0(), "Player 1"));
        match.setPlayer1(blankToDefault(meta.player1(), "Player 2"));
        match.setMatchLength(Math.max(0, meta.matchLength()));
        match.setVariation(blankToDefault(meta.variation(), "Backgammon"));
        match.setResult(trimToNull(meta.result()));
        match.setEvent(trimToNull(meta.event()));
        match.setSite(trimToNull(meta.site()));
        match.setRound(trimToNull(meta.round()));
        match.setMatchDate(trimToNull(meta.matchDate()));
    }

    private String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @Transactional
    public boolean deleteMatch(Long id) {
        if (!matchRepository.existsById(id)) {
            return false;
        }
        matchRepository.deleteById(id);
        return true;
    }

    /** Updates (or clears, when comment is null/blank) the comment on one move, located by game index and seq. */
    @Transactional
    public boolean updateMoveComment(Long matchId, int gameIndex, int seq, String comment) {
        Optional<BgMatch> opt = matchRepository.findById(matchId);
        if (opt.isEmpty()) {
            return false;
        }
        BgMatch match = opt.get();
        List<Game> games = match.getGames();
        if (gameIndex < 0 || gameIndex >= games.size()) {
            return false;
        }
        String normalized = (comment == null || comment.isBlank()) ? null : comment;
        for (MoveRecord move : games.get(gameIndex).getMoves()) {
            if (move.getSeq() == seq) {
                move.setComment(normalized);
                matchRepository.save(match);
                return true;
            }
        }
        return false;
    }
}
