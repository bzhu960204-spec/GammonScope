import { useEffect, useMemo, useState } from 'react'
import {
  exportUrl,
  getMatch,
  requestHint,
  updateMatchMeta,
  updateMoveComment,
  type EvalMove,
  type MatchDetail,
  type MatchMetadataInput,
  type MoveDto,
} from '../api/client'
import Board from '../components/Board'
import MatchMetaDialog from '../components/MatchMetaDialog'
import { buildStates, matchId, pipCount, positionId } from '../engine/board'

interface Props {
  matchId: number
  onBack: () => void
}

export default function ReplayPage({ matchId: id, onBack }: Props) {
  const [match, setMatch] = useState<MatchDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [gameIndex, setGameIndex] = useState(0)
  const [moveIndex, setMoveIndex] = useState(0)
  const [commentDraft, setCommentDraft] = useState('')
  const [savingComment, setSavingComment] = useState(false)
  const [hint, setHint] = useState<EvalMove[] | null>(null)
  const [hintLoading, setHintLoading] = useState(false)
  const [hintError, setHintError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [savingMeta, setSavingMeta] = useState(false)
  const [metaError, setMetaError] = useState<string | null>(null)
  const [flipV, setFlipV] = useState(() => localStorage.getItem('gs.flipV') === '1')
  const [flipH, setFlipH] = useState(() => localStorage.getItem('gs.flipH') === '1')
  const [showNumbers, setShowNumbers] = useState(() => localStorage.getItem('gs.showNumbers') !== '0')

  useEffect(() => {
    localStorage.setItem('gs.flipV', flipV ? '1' : '0')
  }, [flipV])
  useEffect(() => {
    localStorage.setItem('gs.flipH', flipH ? '1' : '0')
  }, [flipH])
  useEffect(() => {
    localStorage.setItem('gs.showNumbers', showNumbers ? '1' : '0')
  }, [showNumbers])

  useEffect(() => {
    getMatch(id)
      .then((m) => {
        setMatch(m)
        setGameIndex(0)
        setMoveIndex(0)
      })
      .catch((e) => setError((e as Error).message))
  }, [id])

  const game = match?.games[gameIndex]
  const states = useMemo(() => (game ? buildStates(game) : []), [game])
  const board = states[moveIndex] ?? states[0]

  // Fold the flat half-move list into gnubg-style rows: player 0 on the left,
  // player 1 on the right. A new row starts whenever a column is already taken,
  // so cube actions, resigns and SET records land in the acting player's column.
  const moveRows = useMemo(() => buildMoveRows(game?.moves ?? []), [game])

  // The selected move is the one about to be played from the current position.
  const currentMove = game ? game.moves[moveIndex] : undefined

  useEffect(() => {
    setCommentDraft(currentMove?.comment ?? '')
  }, [currentMove])

  // Hints are tied to a specific position/roll; clear them when the view moves.
  useEffect(() => {
    setHint(null)
    setHintError(null)
  }, [moveIndex, gameIndex])

  // Left/right (and Home/End) arrow keys step through the position history,
  // up/down switch games; disabled while typing in an input.
  const lastIndex = states.length - 1
  const gameCount = match?.games.length ?? 0
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.ctrlKey || e.altKey || e.metaKey) {
        return
      }
      const el = document.activeElement as HTMLElement | null
      if (
        el &&
        (el.tagName === 'INPUT' ||
          el.tagName === 'TEXTAREA' ||
          el.isContentEditable)
      ) {
        return
      }
      switch (e.key) {
        case 'ArrowLeft':
          e.preventDefault()
          setMoveIndex((i) => Math.max(0, i - 1))
          break
        case 'ArrowRight':
          e.preventDefault()
          setMoveIndex((i) => Math.min(lastIndex, i + 1))
          break
        case 'ArrowUp':
          e.preventDefault()
          setGameIndex((g) => {
            const next = Math.max(0, g - 1)
            if (next !== g) {
              setMoveIndex(0)
            }
            return next
          })
          break
        case 'ArrowDown':
          e.preventDefault()
          setGameIndex((g) => {
            const next = Math.min(gameCount - 1, g + 1)
            if (next !== g) {
              setMoveIndex(0)
            }
            return next
          })
          break
        case 'Home':
          e.preventDefault()
          setMoveIndex(0)
          break
        case 'End':
          e.preventDefault()
          setMoveIndex(lastIndex)
          break
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [lastIndex, gameCount])

  if (error) {
    return (
      <div className="panel">
        <button className="ghost" onClick={onBack}>← Back</button>
        <p className="status-error">{error}</p>
      </div>
    )
  }
  if (!match || !game || !board) {
    return <p className="hint">Loading…</p>
  }

  function selectGame(idx: number) {
    setGameIndex(idx)
    setMoveIndex(0)
  }

  async function saveComment() {
    if (!game || !currentMove) {
      return
    }
    setSavingComment(true)
    try {
      await updateMoveComment(id, gameIndex, currentMove.seq, commentDraft)
      setMatch((prev) => {
        if (!prev) {
          return prev
        }
        const next = structuredClone(prev)
        const mv = next.games[gameIndex].moves[moveIndex]
        mv.comment = commentDraft.trim() === '' ? undefined : commentDraft
        return next
      })
      setError(null)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSavingComment(false)
    }
  }

  async function saveMeta(meta: MatchMetadataInput) {
    setSavingMeta(true)
    setMetaError(null)
    try {
      await updateMatchMeta(id, meta)
      setMatch((prev) => (prev ? { ...prev, ...meta } : prev))
      setEditing(false)
    } catch (e) {
      setMetaError((e as Error).message)
    } finally {
      setSavingMeta(false)
    }
  }

  // Player on roll at the current position, used for the gnubg Position / Match IDs.
  const nextMove = game.moves[moveIndex]
  const prevMove = moveIndex > 0 ? game.moves[moveIndex - 1] : undefined
  const onRoll = nextMove ? nextMove.player : prevMove ? 1 - prevMove.player : 0

  // Show the position the player faced for the selected move: the current
  // checkers with the upcoming roll, without playing the move out (GNU style).
  const displayBoard = {
    ...board,
    dice: [nextMove?.dice0 ?? 0, nextMove?.dice1 ?? 0] as [number, number],
    turn: nextMove ? nextMove.player : board.turn,
  }
  const doubled = nextMove?.type === 'TAKE' || nextMove?.type === 'DROP'
  const posId = positionId(board, onRoll)
  const mId = matchId(board, {
    onRoll,
    matchTo: match.matchLength,
    score0: game.score0,
    score1: game.score1,
    crawford: game.crawford,
    doubled,
  })

  const canHint =
    nextMove?.type === 'NORMAL' &&
    !!nextMove.dice0 &&
    !!nextMove.dice1

  async function loadHint() {
    if (!board || !nextMove || !nextMove.dice0 || !nextMove.dice1) {
      return
    }
    setHintLoading(true)
    setHintError(null)
    try {
      const res = await requestHint(
        board.checkers,
        nextMove.player,
        nextMove.dice0,
        nextMove.dice1,
      )
      setHint(res.moves)
    } catch (e) {
      setHintError((e as Error).message)
    } finally {
      setHintLoading(false)
    }
  }

  const bestEquity = hint && hint.length > 0 ? hint[0].equity : 0
  const playedPairs = sortedPairs(nextMove?.moves)

  // Score / pip labels follow the horizontal mirror so they match the trays.
  const side0 = { name: match.player0, score: game.score0, player: 0 }
  const side1 = { name: match.player1, score: game.score1, player: 1 }
  const [left, right] = flipH ? [side1, side0] : [side0, side1]

  return (
    <div>
      <div className="replay-head">
        <button className="ghost" onClick={onBack}>← Back</button>
        <h2>
          {match.player0} vs {match.player1}
          {match.matchLength > 0 ? ` — ${match.matchLength}pt match` : ' — money session'}
        </h2>
        <div className="export-row">
          <button className="ghost" onClick={() => { setMetaError(null); setEditing(true) }}>Edit info</button>
          <a className="ghost" href={exportUrl(id, 'mat')}>Export .mat</a>
          <a className="ghost" href={exportUrl(id, 'sgf')}>Export .sgf</a>
        </div>
      </div>

      <div className="game-tabs">
        {match.games.map((g, i) => (
          <button
            key={i}
            className={i === gameIndex ? 'tab active' : 'tab'}
            onClick={() => selectGame(i)}
          >
            Game {g.gameNumber}
          </button>
        ))}
      </div>

      <div className="replay-body">
        <div className="board-wrap">
          <div className="score-bar">
            <span>{left.name}: {left.score}</span>
            <span>Cube: {board.cube}</span>
            <span>{right.name}: {right.score}</span>
          </div>
          <Board
            board={displayBoard}
            player0={match.player0}
            player1={match.player1}
            orient={{ flipV, flipH }}
            showNumbers={showNumbers}
          />
          <div className="view-controls">
            <button
              className={flipV ? 'ghost active' : 'ghost'}
              onClick={() => setFlipV((v) => !v)}
              title="Vertical flip: white / black perspective"
            >
              ⇅ Flip
            </button>
            <button
              className={flipH ? 'ghost active' : 'ghost'}
              onClick={() => setFlipH((v) => !v)}
              title="Horizontal mirror: swap home board side"
            >
              ⇄ Mirror
            </button>
            <button
              className={showNumbers ? 'ghost active' : 'ghost'}
              onClick={() => setShowNumbers((v) => !v)}
              title="Toggle point numbers"
            >
              # Numbers
            </button>
          </div>
          <div className="pip-bar">
            <span>Pip {left.name}: {pipCount(board, left.player)}</span>
            <span>Pip {right.name}: {pipCount(board, right.player)}</span>
          </div>
          <div className="id-bar">
            <div><span className="id-label">Position ID</span><code>{posId}</code></div>
            <div><span className="id-label">Match ID</span><code>{mId}</code></div>
          </div>
          <div className="nav">
            <button onClick={() => setMoveIndex(0)} disabled={moveIndex === 0}>⏮</button>
            <button onClick={() => setMoveIndex((i) => Math.max(0, i - 1))} disabled={moveIndex === 0}>◀</button>
            <span className="nav-pos">{moveIndex} / {lastIndex}</span>
            <button onClick={() => setMoveIndex((i) => Math.min(lastIndex, i + 1))} disabled={moveIndex === lastIndex}>▶</button>
            <button onClick={() => setMoveIndex(lastIndex)} disabled={moveIndex === lastIndex}>⏭</button>
          </div>
          <div className="engine">
            <div className="engine-head">
              <button
                className="engine-btn"
                onClick={loadHint}
                disabled={!canHint || hintLoading}
                title={canHint ? 'Evaluate the next move with the gnubg engine' : 'Step to a position with a dice roll to use the engine'}
              >
                {hintLoading ? 'Evaluating…' : `🧠 Hint${canHint ? ` (${nextMove?.dice0}${nextMove?.dice1})` : ''}`}
              </button>
              {hint && <span className="engine-note">0-ply · cubeless money equity</span>}
            </div>
            {hintError && <p className="status-error">{hintError}</p>}
            {hint && hint.length === 0 && <p className="hint">No legal moves (dance).</p>}
            {hint && hint.length > 0 && (
              <ol className="eval-list">
                {hint.map((m, i) => {
                  const played = pairsEqual(sortedPairs(m.moves), playedPairs)
                  const diff = m.equity - bestEquity
                  return (
                    <li key={i} className={played ? 'eval-move played' : 'eval-move'}>
                      <span className="eval-rank">{i + 1}.</span>
                      <span className="eval-notation">{m.notation}</span>
                      <span className="eval-equity">
                        {m.equity >= 0 ? '+' : ''}{m.equity.toFixed(3)}
                        {i > 0 && <span className="eval-diff"> ({diff.toFixed(3)})</span>}
                      </span>
                      <span className="eval-prob">
                        {(m.win * 100).toFixed(1)}% W · {((m.winGammon) * 100).toFixed(1)}% G
                      </span>
                      {played && <span className="eval-tag">played</span>}
                    </li>
                  )
                })}
              </ol>
            )}
          </div>
          <div className="annotation">
            <label htmlFor="annotation">
              Annotation {currentMove ? `(move ${moveIndex + 1})` : '(final position — step to a move to annotate)'}
            </label>
            <textarea
              id="annotation"
              value={commentDraft}
              placeholder={currentMove ? 'Add a comment…' : ''}
              disabled={!currentMove}
              onChange={(e) => setCommentDraft(e.target.value)}
            />
            <div className="annotation-actions">
              <button
                onClick={saveComment}
                disabled={!currentMove || savingComment || commentDraft === (currentMove?.comment ?? '')}
              >
                {savingComment ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>

        <div className="move-list">
          <h3>Moves</h3>
          <table className="move-grid">
            <thead>
              <tr>
                <th className="move-num">#</th>
                <th className="move-col p0" title={match.player0}>{match.player0}</th>
                <th className="move-col p1" title={match.player1}>{match.player1}</th>
              </tr>
            </thead>
            <tbody>
              {moveRows.map((row, r) => (
                <tr key={r}>
                  <td className="move-num">{r + 1}</td>
                  {([row.p0, row.p1] as const).map((cell, col) => (
                    <td key={col} className="move-cell">
                      {cell && (
                        <div
                          className={cell.index === moveIndex ? 'move-half current' : 'move-half'}
                          onClick={() => setMoveIndex(cell.index)}
                        >
                          <span className="desc">{describe(cell.move)}</span>
                          {cell.move.comment && (
                            <span className="move-comment" title={cell.move.comment}>💬</span>
                          )}
                        </div>
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
          {game.winner >= 0 && (
            <p className="winner">
              {game.winner === 0 ? match.player0 : match.player1} wins {game.points} point{game.points === 1 ? '' : 's'}
            </p>
          )}
        </div>
      </div>

      {editing && (
        <MatchMetaDialog
          title="Edit match info"
          initial={{
            player0: match.player0,
            player1: match.player1,
            matchLength: match.matchLength,
            variation: match.variation ?? '',
            result: match.result ?? '',
            event: match.event ?? '',
            site: match.site ?? '',
            round: match.round ?? '',
            matchDate: match.matchDate ?? '',
          }}
          saving={savingMeta}
          error={metaError}
          onCancel={() => setEditing(false)}
          onSave={saveMeta}
        />
      )}
    </div>
  )
}

interface MoveCell {
  move: MoveDto
  index: number
}

interface MoveRow {
  p0?: MoveCell
  p1?: MoveCell
}

/** Fold the flat half-move list into two-column rows (player 0 left, player 1 right). */
function buildMoveRows(moves: MoveDto[]): MoveRow[] {
  const rows: MoveRow[] = []
  let row: MoveRow = {}
  let hasCell = false
  for (let i = 0; i < moves.length; i++) {
    const mv = moves[i]
    const key = mv.player === 0 ? 'p0' : 'p1'
    // A left-column move always opens a new row; so a lead opening by the
    // right player stays alone with the left cell empty (gnubg convention).
    const needNew = key === 'p0' ? hasCell : row[key] != null
    if (needNew) {
      rows.push(row)
      row = {}
      hasCell = false
    }
    row[key] = { move: mv, index: i }
    hasCell = true
  }
  if (hasCell) {
    rows.push(row)
  }
  return rows
}

/** Serialize a move's [from,to] pairs into a canonical string for comparison. */
function sortedPairs(moves: number[][] | undefined): string {
  if (!moves || moves.length === 0) {
    return ''
  }
  return moves
    .map((p) => `${p[0]}:${p[1]}`)
    .sort()
    .join(',')
}

function pairsEqual(a: string, b: string): boolean {
  return a !== '' && a === b
}

function describe(mv: MoveDto): string {
  switch (mv.type) {
    case 'NORMAL':
      return `${mv.dice0}${mv.dice1}: ${mv.notation || '(dance)'}`
    case 'DOUBLE':
      return 'doubles'
    case 'TAKE':
      return 'takes'
    case 'DROP':
      return 'drops'
    case 'RESIGN':
      return 'resigns'
    case 'SET_DICE':
      return `rolls ${mv.dice0}${mv.dice1}`
    case 'SET_CUBE_VALUE':
      return `cube = ${mv.cubeValue}`
    case 'SET_BOARD':
      return 'sets up position'
    case 'SET_CUBE_POS': {
      const owner = mv.cubeOwner === 0 ? 'player 1' : mv.cubeOwner === 1 ? 'player 2' : 'center'
      return `cube to ${owner}`
    }
    default:
      return mv.type
  }
}
