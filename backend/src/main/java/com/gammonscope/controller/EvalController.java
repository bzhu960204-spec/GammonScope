package com.gammonscope.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gammonscope.dto.EvalRequest;
import com.gammonscope.dto.EvalResponse;
import com.gammonscope.eval.EvalEngine;
import com.gammonscope.eval.GnubgEval;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalEngine engine;

    public EvalController(EvalEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/hint")
    public ResponseEntity<?> hint(@RequestBody EvalRequest req) {
        String error = validate(req);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }

        List<GnubgEval.ScoredMove> scored = engine.hint(req.checkers(), req.onRoll(), req.dice0(), req.dice1());
        List<EvalResponse.EvalMove> moves = scored.stream()
                .map(m -> new EvalResponse.EvalMove(
                        m.subMoves,
                        m.notation,
                        m.equity,
                        m.output[GnubgEval.OUTPUT_WIN],
                        m.output[GnubgEval.OUTPUT_WINGAMMON],
                        m.output[GnubgEval.OUTPUT_WINBACKGAMMON],
                        m.output[GnubgEval.OUTPUT_LOSEGAMMON],
                        m.output[GnubgEval.OUTPUT_LOSEBACKGAMMON]))
                .toList();
        return ResponseEntity.ok(new EvalResponse(moves));
    }

    private static String validate(EvalRequest req) {
        if (req.checkers() == null || req.checkers().length != 2
                || req.checkers()[0] == null || req.checkers()[0].length != 25
                || req.checkers()[1] == null || req.checkers()[1].length != 25) {
            return "checkers must be a 2x25 array";
        }
        if (req.onRoll() != 0 && req.onRoll() != 1) {
            return "onRoll must be 0 or 1";
        }
        if (req.dice0() < 1 || req.dice0() > 6 || req.dice1() < 1 || req.dice1() > 6) {
            return "dice must be in 1..6";
        }
        return null;
    }
}
