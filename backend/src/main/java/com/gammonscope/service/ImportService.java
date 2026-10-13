package com.gammonscope.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gammonscope.model.BgMatch;
import com.gammonscope.model.Game;
import com.gammonscope.model.MoveRecord;
import com.gammonscope.parser.GameFileParser;
import com.gammonscope.parser.ImportException;
import com.gammonscope.parser.ParsedMatch;
import com.gammonscope.parser.ParsedMatch.ParsedGame;
import com.gammonscope.parser.ParsedMatch.ParsedMove;
import com.gammonscope.repository.MatchRepository;

@Service
public class ImportService {

    private final List<GameFileParser> parsers;
    private final MatchRepository matchRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImportService(List<GameFileParser> parsers, MatchRepository matchRepository) {
        this.parsers = parsers;
        this.matchRepository = matchRepository;
    }

    public BgMatch importFile(String fileName, byte[] bytes) {
        String content = decode(bytes);

        GameFileParser parser = parsers.stream()
                .filter(p -> p.supports(fileName, content))
                .findFirst()
                .orElseThrow(() -> new ImportException(
                        "Unsupported file format. Only .mat and .sgf files are supported."));

        ParsedMatch parsed = parser.parse(content, fileName);
        deriveResults(parsed);
        BgMatch match = toEntity(parsed);
        return matchRepository.save(match);
    }

    private String decode(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        return s;
    }

    /** Fill in each game's winner/points from the score progression of the following game. */
    private void deriveResults(ParsedMatch parsed) {
        List<ParsedGame> games = parsed.games;
        for (int i = 0; i + 1 < games.size(); i++) {
            ParsedGame cur = games.get(i);
            ParsedGame next = games.get(i + 1);
            int d0 = next.score0 - cur.score0;
            int d1 = next.score1 - cur.score1;
            if (d0 > 0) {
                cur.winner = 0;
                cur.points = d0;
            } else if (d1 > 0) {
                cur.winner = 1;
                cur.points = d1;
            }
        }
    }

    private BgMatch toEntity(ParsedMatch parsed) {
        BgMatch match = new BgMatch();
        match.setPlayer0(parsed.player0);
        match.setPlayer1(parsed.player1);
        match.setMatchLength(parsed.matchLength);
        match.setVariation(parsed.variation);
        match.setResult(parsed.result);
        match.setEvent(parsed.event);
        match.setSite(parsed.site);
        match.setRound(parsed.round);
        match.setMatchDate(parsed.matchDate);
        match.setSourceFormat(parsed.sourceFormat);
        match.setImportedAt(Instant.now());

        int seqBase = 0;
        for (ParsedGame pg : parsed.games) {
            Game game = new Game();
            game.setGameNumber(pg.gameNumber);
            game.setScore0(pg.score0);
            game.setScore1(pg.score1);
            game.setWinner(pg.winner);
            game.setPoints(pg.points);
            game.setCrawford(pg.crawford);
            game.setSetupBoard(pg.setupBoard);

            int seq = 0;
            for (ParsedMove pm : pg.moves) {
                MoveRecord move = new MoveRecord();
                move.setSeq(seq++);
                move.setPlayer(pm.player);
                move.setType(pm.type);
                move.setDice0(pm.dice0);
                move.setDice1(pm.dice1);
                move.setNotation(pm.notation);
                move.setComment(pm.comment);
                move.setCubeValue(pm.cubeValue);
                move.setResigned(pm.resigned);
                move.setCubeOwner(pm.cubeOwner);
                move.setSetup(pm.setup);
                move.setMovesInternal(serializeMoves(pm.moves));
                game.addMove(move);
            }
            match.addGame(game);
            seqBase++;
        }
        return match;
    }

    private String serializeMoves(List<int[]> moves) {
        if (moves == null || moves.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(moves);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
