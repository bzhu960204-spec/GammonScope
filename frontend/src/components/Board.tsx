import type { ReactNode } from 'react'
import type { BoardState } from '../engine/board'

const POINT_W = 68
const BAR_W = 60
const MARGIN = 30
const TOP_BASE = 30
const BOT_BASE = 610
const APEX = 240
const R = 24
const STACK = 46

function pointX(col: number): number {
  return MARGIN + col * POINT_W + (col >= 6 ? BAR_W : 0)
}

/** physical point -> {col, top} */
function layout(p: number): { col: number; top: boolean } {
  if (p <= 11) {
    return { col: 11 - p, top: false }
  }
  return { col: p - 12, top: true }
}

interface Props {
  board: BoardState
  player0: string
  player1: string
}

export default function Board({ board, player0, player1 }: Props) {
  const triangles: ReactNode[] = []
  const checkers: ReactNode[] = []

  for (let p = 0; p <= 23; p++) {
    const { col, top } = layout(p)
    const x = pointX(col)
    const dark = (p + (top ? 0 : 1)) % 2 === 0
    const fill = dark ? '#7a5230' : '#c7a06a'

    if (top) {
      triangles.push(
        <polygon
          key={`t${p}`}
          points={`${x},${TOP_BASE} ${x + POINT_W},${TOP_BASE} ${x + POINT_W / 2},${TOP_BASE + APEX}`}
          fill={fill}
          stroke="#3a2a18"
          strokeWidth={1}
        />,
      )
    } else {
      triangles.push(
        <polygon
          key={`b${p}`}
          points={`${x},${BOT_BASE} ${x + POINT_W},${BOT_BASE} ${x + POINT_W / 2},${BOT_BASE - APEX}`}
          fill={fill}
          stroke="#3a2a18"
          strokeWidth={1}
        />,
      )
    }

    const white = board.checkers[0][p]
    const black = board.checkers[1][23 - p]
    const count = white + black
    const owner = white > 0 ? 0 : 1
    drawStack(checkers, p, x + POINT_W / 2, top, count, owner)
  }

  // Bar checkers (hit / entering)
  const barX = MARGIN + 6 * POINT_W + BAR_W / 2
  drawBar(checkers, 'w', barX, false, board.checkers[0][24], 0)
  drawBar(checkers, 'b', barX, true, board.checkers[1][24], 1)

  return (
    <svg viewBox="0 0 1000 640" className="board-svg" role="img" aria-label="Backgammon board">
      <rect x={0} y={0} width={1000} height={640} rx={14} fill="#2b1d10" />
      <rect x={MARGIN - 8} y={TOP_BASE - 8} width={6 * POINT_W + 16} height={BOT_BASE - TOP_BASE + 16}
        fill="#e9d6b0" stroke="#3a2a18" strokeWidth={2} />
      <rect x={pointX(6) - 8} y={TOP_BASE - 8} width={6 * POINT_W + 16} height={BOT_BASE - TOP_BASE + 16}
        fill="#e9d6b0" stroke="#3a2a18" strokeWidth={2} />
      {/* bar */}
      <rect x={MARGIN + 6 * POINT_W} y={TOP_BASE - 8} width={BAR_W} height={BOT_BASE - TOP_BASE + 16}
        fill="#5a3f22" stroke="#3a2a18" strokeWidth={2} />

      {triangles}
      {checkers}

      {/* off trays */}
      <OffTray x={920} label={player0} count={board.off[0]} player={0} top={false} />
      <OffTray x={920} label={player1} count={board.off[1]} player={1} top />

      {/* dice */}
      {board.dice[0] > 0 && (
        <>
          <Die x={560} y={300} value={board.dice[0]} />
          <Die x={640} y={300} value={board.dice[1]} />
        </>
      )}

      {/* cube */}
      <Cube value={board.cube} owner={board.cubeOwner} />
    </svg>
  )
}

function drawStack(
  out: ReactNode[],
  p: number,
  cx: number,
  top: boolean,
  count: number,
  owner: number,
) {
  if (count <= 0) {
    return
  }
  const shown = Math.min(count, 5)
  for (let k = 0; k < shown; k++) {
    const cy = top ? TOP_BASE + R + k * STACK : BOT_BASE - R - k * STACK
    out.push(<Checker key={`c${p}-${k}`} cx={cx} cy={cy} owner={owner} />)
    if (count > 5 && k === shown - 1) {
      out.push(
        <text key={`n${p}`} x={cx} y={cy + 6} textAnchor="middle" fontSize={22} fontWeight={700}
          fill={owner === 0 ? '#222' : '#eee'}>
          {count}
        </text>,
      )
    }
  }
}

function drawBar(
  out: ReactNode[],
  key: string,
  cx: number,
  top: boolean,
  count: number,
  owner: number,
) {
  if (count <= 0) {
    return
  }
  const midStart = top ? 60 : 580
  for (let k = 0; k < Math.min(count, 4); k++) {
    const cy = top ? midStart + k * STACK : midStart - k * STACK
    out.push(<Checker key={`bar${key}${k}`} cx={cx} cy={cy} owner={owner} />)
  }
  if (count > 4) {
    out.push(
      <text key={`barn${key}`} x={cx} y={top ? midStart + 3 * STACK + 6 : midStart - 3 * STACK + 6}
        textAnchor="middle" fontSize={20} fontWeight={700} fill={owner === 0 ? '#222' : '#eee'}>
        {count}
      </text>,
    )
  }
}

function Checker({ cx, cy, owner }: { cx: number; cy: number; owner: number }) {
  const fill = owner === 0 ? '#f4f1ea' : '#2a2a2a'
  const stroke = owner === 0 ? '#b9b2a2' : '#000'
  return (
    <g>
      <circle cx={cx} cy={cy} r={R} fill={fill} stroke={stroke} strokeWidth={2} />
      <circle cx={cx} cy={cy} r={R - 7} fill="none" stroke={owner === 0 ? '#d8d2c2' : '#444'} strokeWidth={2} />
    </g>
  )
}

function Die({ x, y, value }: { x: number; y: number; value: number }) {
  const positions: Record<number, [number, number][]> = {
    1: [[0.5, 0.5]],
    2: [[0.3, 0.3], [0.7, 0.7]],
    3: [[0.3, 0.3], [0.5, 0.5], [0.7, 0.7]],
    4: [[0.3, 0.3], [0.7, 0.3], [0.3, 0.7], [0.7, 0.7]],
    5: [[0.3, 0.3], [0.7, 0.3], [0.5, 0.5], [0.3, 0.7], [0.7, 0.7]],
    6: [[0.3, 0.25], [0.7, 0.25], [0.3, 0.5], [0.7, 0.5], [0.3, 0.75], [0.7, 0.75]],
  }
  const size = 56
  return (
    <g>
      <rect x={x} y={y} width={size} height={size} rx={10} fill="#f4f1ea" stroke="#333" strokeWidth={2} />
      {(positions[value] ?? []).map(([dx, dy], i) => (
        <circle key={i} cx={x + dx * size} cy={y + dy * size} r={5} fill="#222" />
      ))}
    </g>
  )
}

function Cube({ value, owner }: { value: number; owner: number }) {
  const display = value <= 1 ? 64 : value
  const y = owner === 0 ? 560 : owner === 1 ? 40 : 300
  return (
    <g>
      <rect x={6} y={y} width={48} height={48} rx={8} fill="#eee" stroke="#222" strokeWidth={2} />
      <text x={30} y={y + 32} textAnchor="middle" fontSize={24} fontWeight={700} fill="#222">
        {display}
      </text>
    </g>
  )
}

function OffTray({
  x,
  label,
  count,
  player,
  top,
}: {
  x: number
  label: string
  count: number
  player: number
  top: boolean
}) {
  const y = top ? TOP_BASE : BOT_BASE - 120
  return (
    <g>
      <rect x={x} y={y} width={58} height={120} rx={8} fill="#1d130a" stroke="#3a2a18" strokeWidth={2} />
      <text x={x + 29} y={y + 70} textAnchor="middle" fontSize={26} fontWeight={700}
        fill={player === 0 ? '#f4f1ea' : '#c9c9c9'}>
        {count}
      </text>
      <text x={x + 29} y={top ? y + 110 : y + 20} textAnchor="middle" fontSize={11} fill="#b8a98a">
        {label.slice(0, 8)}
      </text>
    </g>
  )
}
