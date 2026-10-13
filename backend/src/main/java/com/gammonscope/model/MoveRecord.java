package com.gammonscope.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @ManyToOne
    @JoinColumn(name = "game_id")
    @JsonIgnore
    private Game game;

    /** Order of this record within its game. */
    private int seq;

    /** 0 or 1; the player who made this action. */
    private int player;

    @Enumerated(EnumType.STRING)
    private MoveType type;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int dice0;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int dice1;

    /** JSON array of [from,to] internal-index pairs for NORMAL moves, e.g. "[[23,17],[12,9]]". */
    @Column(length = 2000)
    @JsonRawValue
    @JsonProperty("moves")
    private String movesInternal;

    /** Human-readable notation as found in the source file, e.g. "24/18 13/10". */
    private String notation;

    private String comment;

    private Integer cubeValue;

    private Integer resigned;

    /** Cube owner for SET_CUBE_POS records: -1 center, 0 or 1. */
    private Integer cubeOwner;

    /** JSON board override for SET_BOARD records, e.g. {"checkers":[[...],[...]],"off":[0,0],"turn":0}. */
    @Column(length = 2000)
    @JsonRawValue
    private String setup;

    public Long getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        this.player = player;
    }

    public MoveType getType() {
        return type;
    }

    public void setType(MoveType type) {
        this.type = type;
    }

    public int getDice0() {
        return dice0;
    }

    public void setDice0(int dice0) {
        this.dice0 = dice0;
    }

    public int getDice1() {
        return dice1;
    }

    public void setDice1(int dice1) {
        this.dice1 = dice1;
    }

    public String getMovesInternal() {
        return movesInternal;
    }

    public void setMovesInternal(String movesInternal) {
        this.movesInternal = movesInternal;
    }

    public String getNotation() {
        return notation;
    }

    public void setNotation(String notation) {
        this.notation = notation;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getCubeValue() {
        return cubeValue;
    }

    public void setCubeValue(Integer cubeValue) {
        this.cubeValue = cubeValue;
    }

    public Integer getResigned() {
        return resigned;
    }

    public void setResigned(Integer resigned) {
        this.resigned = resigned;
    }

    public Integer getCubeOwner() {
        return cubeOwner;
    }

    public void setCubeOwner(Integer cubeOwner) {
        this.cubeOwner = cubeOwner;
    }

    public String getSetup() {
        return setup;
    }

    public void setSetup(String setup) {
        this.setup = setup;
    }
}
