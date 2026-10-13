import { useEffect, useMemo, useState } from 'react'
import {
  exportUrl,
  getMatch,
  requestHint,
  updateMoveComment,
  type EvalMove,
  type MatchDetail,
  type MoveDto,
} from '../api/client'
import Board from '../components/Board'
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

  // The move whose annotation we edit is the one that produced the current position.
  const currentMove = game && moveIndex > 0 ? game.moves[moveIndex - 1] : undefined

  useEffect(() => {
    setCommentDraft(currentMove?.comment ?? '')
  }, [currentMove])

  // Hints are tied to a specific position/roll; clear them when the view moves.
  useEffect(() => {
    setHint(null)
    setHintError(null)
  }, [moveIndex, gameIndex])

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
    if (!game || moveIndex === 0 || !currentMove) {
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
        const mv = next.games[gameIndex].moves[moveIndex - 1]
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

  const lastIndex = states.length - 1

  // Player on roll at the current position, used for the gnubg Position / Match IDs.
  const nextMove = game.moves[moveIndex]
  const prevMove = moveIndex > 0 ? game.moves[moveIndex - 1] : undefined
  const onRoll = nextMove ? nextMove.player : prevMove ? 1 - prevMove.player : 0
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

  return (
    <div>
      <div className="replay-head">
        <button className="ghost" onClick={onBack}>← Back</button>
        <h2>
          {match.player0} vs {match.player1}
          {match.matchLength > 0 ? ` — ${match.matchLength}pt match` : ' — money session'}
        </h2>
        <div className="export-row">
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
            Game {g.gameNumber + 1}
          </button>
        ))}
      </div>

      <div className="replay-body">
        <div className="board-wrap">
          <div className="score-bar">
            <span>{match.player0}: {game.score0}</span>
            <span>Cube: {board.cube}</span>
            <span>{match.player1}: {game.score1}</span>
          </div>
          <Board board={board} player0={match.player0} player1={match.player1} />
          <div className="pip-bar">
            <span>Pip {match.player0}: {pipCount(board, 0)}</span>
            <span>Pip {match.player1}: {pipCount(board, 1)}</span>
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
              Annotation {currentMove ? `(move ${moveIndex})` : '(start position — select a move to annotate)'}
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
          <ol>
            {game.moves.map((mv, i) => (
              <li
                key={i}
                className={i + 1 === moveIndex ? 'move current' : 'move'}
                onClick={() => setMoveIndex(i + 1)}
              >
                <span className={mv.player === 0 ? 'who p0' : 'who p1'}>
                  {mv.player === 0 ? match.player0 : match.player1}
                </span>
                <span className="desc">{describe(mv)}</span>
                {mv.comment && <span className="move-comment" title={mv.comment}>💬</span>}
              </li>
            ))}
          </ol>
          {game.winner >= 0 && (
            <p className="winner">
              {game.winner === 0 ? match.player0 : match.player1} wins {game.points} point{game.points === 1 ? '' : 's'}
            </p>
          )}
        </div>
      </div>
    </div>
  )
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
