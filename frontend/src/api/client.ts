export interface HealthResponse {
  status: string
  service: string
  time: string
}

export interface MatchSummary {
  id: number
  player0: string
  player1: string
  matchLength: number
  variation: string | null
  result: string | null
  sourceFormat: string | null
  event: string | null
  matchDate: string | null
  importedAt: string
  gameCount: number
}

export type MoveType =
  | 'NORMAL'
  | 'DOUBLE'
  | 'TAKE'
  | 'DROP'
  | 'RESIGN'
  | 'SET_DICE'
  | 'SET_BOARD'
  | 'SET_CUBE_VALUE'
  | 'SET_CUBE_POS'

export interface SetupBoard {
  checkers: [number[], number[]]
  off: [number, number]
  turn: number
}

export interface MoveDto {
  seq: number
  player: number
  type: MoveType
  dice0?: number
  dice1?: number
  /** [from,to] internal-index pairs. */
  moves?: number[][]
  notation?: string
  comment?: string
  cubeValue?: number
  resigned?: number
  /** Cube owner for SET_CUBE_POS: -1 center, 0 or 1. */
  cubeOwner?: number
  /** Board override for SET_BOARD records. */
  setup?: SetupBoard
}

export interface GameDto {
  gameNumber: number
  score0: number
  score1: number
  winner: number
  points: number
  crawford: boolean
  setupBoard?: string
  moves: MoveDto[]
}

export interface MatchDetail {
  id: number
  player0: string
  player1: string
  matchLength: number
  variation: string | null
  result: string | null
  event: string | null
  site: string | null
  round: string | null
  matchDate: string | null
  sourceFormat: string | null
  importedAt: string
  games: GameDto[]
}

const API_BASE = '/api'

export async function getHealth(): Promise<HealthResponse> {
  const res = await fetch(`${API_BASE}/health`)
  if (!res.ok) {
    throw new Error(`Health check failed: ${res.status}`)
  }
  return res.json()
}

export async function listMatches(): Promise<MatchSummary[]> {
  const res = await fetch(`${API_BASE}/matches`)
  if (!res.ok) {
    throw new Error(`Failed to load matches: ${res.status}`)
  }
  return res.json()
}

export async function getMatch(id: number): Promise<MatchDetail> {
  const res = await fetch(`${API_BASE}/matches/${id}`)
  if (!res.ok) {
    throw new Error(`Failed to load match ${id}: ${res.status}`)
  }
  return res.json()
}

export async function importMatch(file: File): Promise<void> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`${API_BASE}/matches/import`, {
    method: 'POST',
    body: form,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.error ?? `Import failed: ${res.status}`)
  }
}

export async function deleteMatch(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/matches/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    throw new Error(`Failed to delete match ${id}: ${res.status}`)
  }
}

export async function updateMoveComment(
  matchId: number,
  gameIndex: number,
  seq: number,
  comment: string,
): Promise<void> {
  const res = await fetch(
    `${API_BASE}/matches/${matchId}/games/${gameIndex}/moves/${seq}/comment`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ comment }),
    },
  )
  if (!res.ok) {
    throw new Error(`Failed to save comment: ${res.status}`)
  }
}

export function exportUrl(matchId: number, format: 'mat' | 'sgf'): string {
  return `${API_BASE}/matches/${matchId}/export/${format}`
}

export interface EvalMove {
  /** [from,to] internal-index pairs (24 = bar, -1 = off). */
  moves: number[][]
  notation: string
  /** 0-ply cubeless money equity for the player on roll. */
  equity: number
  win: number
  winGammon: number
  winBackgammon: number
  loseGammon: number
  loseBackgammon: number
}

export interface EvalResponse {
  moves: EvalMove[]
}

export async function requestHint(
  checkers: [number[], number[]],
  onRoll: number,
  dice0: number,
  dice1: number,
): Promise<EvalResponse> {
  const res = await fetch(`${API_BASE}/eval/hint`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ checkers, onRoll, dice0, dice1 }),
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.error ?? `Hint failed: ${res.status}`)
  }
  return res.json()
}
