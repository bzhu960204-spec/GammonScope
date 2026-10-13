package com.gammonscope.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

@Entity
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "match_id")
    @JsonIgnore
    private BgMatch match;

    private int gameNumber;
    private int score0;
    private int score1;

    /** -1 if the game has no recorded winner. */
    private int winner = -1;
    private int points;

    private boolean crawford;

    /** Optional non-standard starting position, encoded by the parser; null means the standard start. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String setupBoard;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<MoveRecord> moves = new ArrayList<>();

    public void addMove(MoveRecord move) {
        move.setGame(this);
        moves.add(move);
    }

    public Long getId() {
        return id;
    }

    public BgMatch getMatch() {
        return match;
    }

    public void setMatch(BgMatch match) {
        this.match = match;
    }

    public int getGameNumber() {
        return gameNumber;
    }

    public void setGameNumber(int gameNumber) {
        this.gameNumber = gameNumber;
    }

    public int getScore0() {
        return score0;
    }

    public void setScore0(int score0) {
        this.score0 = score0;
    }

    public int getScore1() {
        return score1;
    }

    public void setScore1(int score1) {
        this.score1 = score1;
    }

    public int getWinner() {
        return winner;
    }

    public void setWinner(int winner) {
        this.winner = winner;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public boolean isCrawford() {
        return crawford;
    }

    public void setCrawford(boolean crawford) {
        this.crawford = crawford;
    }

    public String getSetupBoard() {
        return setupBoard;
    }

    public void setSetupBoard(String setupBoard) {
        this.setupBoard = setupBoard;
    }

    public List<MoveRecord> getMoves() {
        return moves;
    }
}
