package com.gammonscope.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gammonscope.eval.EvalEngine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the /api/eval/hint endpoint JSON wiring end-to-end using a real
 * {@link EvalEngine} (weights loaded from the classpath), without a servlet
 * container or database.
 */
class EvalControllerTest {

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new EvalController(new EvalEngine())).build();
    }

    // Starting position, both players own-perspective: 24-pt(2), 13-pt(5), 8-pt(3), 6-pt(5).
    private static final String START_CHECKERS =
            "[[0,0,0,0,0,5,0,3,0,0,0,0,5,0,0,0,0,0,0,0,0,0,0,2,0],"
          + " [0,0,0,0,0,5,0,3,0,0,0,0,5,0,0,0,0,0,0,0,0,0,0,2,0]]";

    @Test
    void hintForOpeningThreeOneRanksFivePointFirst() throws Exception {
        String body = "{\"checkers\":" + START_CHECKERS + ",\"onRoll\":0,\"dice0\":3,\"dice1\":1}";
        mockMvc().perform(post("/api/eval/hint")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves[0].notation").value("8/5 6/5"))
                .andExpect(jsonPath("$.moves[0].win").exists())
                .andExpect(jsonPath("$.moves[0].equity").exists());
    }

    @Test
    void rejectsBadDice() throws Exception {
        String body = "{\"checkers\":" + START_CHECKERS + ",\"onRoll\":0,\"dice0\":0,\"dice1\":7}";
        mockMvc().perform(post("/api/eval/hint")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
