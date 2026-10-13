import type { GameDto, MoveDto } from '../api/client'

/**
 * Board state in GNU Backgammon's internal representation.
 * `checkers[player][i]` is the number of that player's checkers on internal index i,
 * from that player's own perspective: i=0 is their ace point, i=23 their furthest
 * point, i=24 is the bar. Borne-off checkers are tracked in `off`.
 */
export interface BoardState {
  checkers: [number[], number[]]
  off: [number, number]
  cube: number
  cubeOwner: number // -1 center, 0, or 1
  dice: [number, number]
  turn: number // player to act, or -1 if unknown
}

export function startingBoard(): BoardState {
  const empty = () => new Array<number>(25).fill(0)
  const a = empty()
  const b = empty()
  for (const side of [a, b]) {
    side[23] = 2
    side[12] = 5
    side[7] = 3
    side[5] = 5
  }
  return {
    checkers: [a, b],
    off: [0, 0],
    cube: 1,
    cubeOwner: -1,
    dice: [0, 0],
    turn: -1,
  }
}

function clone(s: BoardState): BoardState {
  return {
    checkers: [s.checkers[0].slice(), s.checkers[1].slice()],
    off: [s.off[0], s.off[1]],
    cube: s.cube,
    cubeOwner: s.cubeOwner,
    dice: [s.dice[0], s.dice[1]],
    turn: s.turn,
  }
}

function applyMove(prev: BoardState, move: MoveDto): BoardState {
  const s = clone(prev)
  const p = move.player
  const opp = 1 - p

  switch (move.type) {
    case 'NORMAL': {
      s.turn = p
      s.dice = [move.dice0 ?? 0, move.dice1 ?? 0]
      for (const [from, to] of move.moves ?? []) {
        if (from >= 0 && from <= 24) {
          s.checkers[p][from] = Math.max(0, s.checkers[p][from] - 1)
        }
        if (to === -1) {
          s.off[p] += 1
        } else if (to >= 0 && to <= 23) {
          const oppIndex = 23 - to
          if (s.checkers[opp][oppIndex] === 1) {
            s.checkers[opp][oppIndex] = 0
            s.checkers[opp][24] += 1 // send to opponent's bar
          }
          s.checkers[p][to] += 1
        }
      }
      break
    }
    case 'SET_DICE':
      s.turn = p
      s.dice = [move.dice0 ?? 0, move.dice1 ?? 0]
      break
    case 'SET_BOARD':
      if (move.setup) {
        s.checkers = [move.setup.checkers[0].slice(), move.setup.checkers[1].slice()]
        s.off = [move.setup.off[0], move.setup.off[1]]
        s.turn = move.setup.turn
        s.dice = [0, 0]
      }
      break
    case 'SET_CUBE_POS':
      if (move.cubeOwner !== undefined) {
        s.cubeOwner = move.cubeOwner
      }
      break
    case 'DOUBLE':
      s.turn = p
      break
    case 'TAKE':
      s.cube *= 2
      s.cubeOwner = move.player
      break
    case 'SET_CUBE_VALUE':
      if (move.cubeValue) {
        s.cube = move.cubeValue
      }
      break
    case 'DROP':
    case 'RESIGN':
    default:
      break
  }
  return s
}

/** Precompute every board snapshot: states[0] is the start, states[k] is after move k-1. */
export function buildStates(game: GameDto): BoardState[] {
  const states: BoardState[] = [startingBoard()]
  let current = states[0]
  for (const move of game.moves) {
    current = applyMove(current, move)
    states.push(current)
  }
  return states
}

export function pipCount(board: BoardState, player: number): number {
  let pips = 0
  const side = board.checkers[player]
  for (let i = 0; i <= 23; i++) {
    pips += side[i] * (i + 1)
  }
  pips += side[24] * 25 // bar
  return pips
}

const BASE64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

/**
 * GNU Backgammon Position ID: the legacy base64 key of the board, from the
 * perspective of the player on roll. Matches gnubg's PositionID exactly.
 */
export function positionId(board: BoardState, onRoll: number): string {
  const key = new Uint8Array(10)
  const rows = [board.checkers[1 - onRoll], board.checkers[onRoll]] // anBoard[0]=opponent, [1]=on roll
  let iBit = 0
  for (const row of rows) {
    for (let j = 0; j < 25; j++) {
      const nc = row[j]
      for (let b = 0; b < nc; b++) {
        const pos = iBit + b
        key[pos >> 3] |= 1 << (pos & 7)
      }
      iBit += nc + 1
    }
  }
  let out = ''
  let pi = 0
  for (let g = 0; g < 3; g++) {
    out += BASE64[key[pi] >> 2]
    out += BASE64[((key[pi] & 0x03) << 4) | (key[pi + 1] >> 4)]
    out += BASE64[((key[pi + 1] & 0x0f) << 2) | (key[pi + 2] >> 6)]
    out += BASE64[key[pi + 2] & 0x3f]
    pi += 3
  }
  out += BASE64[key[pi] >> 2]
  out += BASE64[(key[pi] & 0x03) << 4]
  return out
}

export interface MatchIdOpts {
  onRoll: number
  matchTo: number
  score0: number
  score1: number
  crawford: boolean
  doubled?: boolean
  resigned?: number
  jacoby?: boolean
}

/** GNU Backgammon Match ID: the base64 match-state key. Matches gnubg's MatchID. */
export function matchId(board: BoardState, opts: MatchIdOpts): string {
  const key = new Uint8Array(9)
  const setBits = (bitPos: number, nBits: number, value: number) => {
    for (let i = 0; i < nBits; i++) {
      const pos = bitPos + i
      if ((value >> i) & 1) {
        key[pos >> 3] |= 1 << (pos & 7)
      }
    }
  }

  let logCube = 0
  for (let v = Math.max(1, board.cube); v > 1; v >>= 1) {
    logCube++
  }
  const jacoby = opts.jacoby ?? opts.matchTo === 0
  const [d0, d1] = board.dice
  const high = d0 >= d1 ? d0 : d1
  const low = d0 >= d1 ? d1 : d0

  setBits(0, 4, logCube)
  setBits(4, 2, board.cubeOwner & 0x3) // -1 center -> 3
  setBits(6, 1, opts.onRoll)
  setBits(7, 1, opts.crawford ? 1 : 0)
  setBits(8, 3, 1) // gamestate: GAME_PLAYING
  setBits(11, 1, opts.onRoll)
  setBits(12, 1, opts.doubled ? 1 : 0)
  setBits(13, 2, opts.resigned ?? 0)
  setBits(15, 3, high & 0x7)
  setBits(18, 3, low & 0x7)
  setBits(21, 15, opts.matchTo & 0x7fff)
  setBits(36, 15, opts.score0 & 0x7fff)
  setBits(51, 15, opts.score1 & 0x7fff)
  setBits(66, 1, jacoby ? 0 : 1)

  let out = ''
  for (let g = 0; g < 3; g++) {
    const o = g * 3
    out += BASE64[key[o] >> 2]
    out += BASE64[((key[o] & 0x03) << 4) | (key[o + 1] >> 4)]
    out += BASE64[((key[o + 1] & 0x0f) << 2) | (key[o + 2] >> 6)]
    out += BASE64[key[o + 2] & 0x3f]
  }
  return out
}
