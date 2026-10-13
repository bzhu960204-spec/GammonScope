package com.gammonscope.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
