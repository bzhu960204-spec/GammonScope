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
const W = 1000
const H = 640

export interface Orient {
  /** swap top/bottom halves (white view <-> black view) */
  flipV: boolean
  /** mirror left/right (home board side) */
  flipH: boolean
}

const NO_FLIP: Orient = { flipV: false, flipH: false }

// Everything is authored in the canonical white-at-bottom frame, then each
// coordinate is projected. Only positions flip — glyphs/text stay upright.
function px(x: number, o: Orient): number {
  return o.flipH ? W - x : x
}
function py(y: number, o: Orient): number {
  return o.flipV ? H - y : y
}
function rectX(x: number, w: number, o: Orient): number {
  return o.flipH ? W - x - w : x
}
function rectY(y: number, h: number, o: Orient): number {
  return o.flipV ? H - y - h : y
}
function poly(pts: [number, number][], o: Orient): string {
  return pts.map(([x, y]) => `${px(x, o)},${py(y, o)}`).join(' ')
}

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
  orient?: Orient
  showNumbers?: boolean
}

export default function Board({ board, player0, player1, orient, showNumbers = true }: Props) {
  const o = orient ?? NO_FLIP
  const triangles: ReactNode[] = []
  const checkers: ReactNode[] = []
  const labels: ReactNode[] = []

  for (let p = 0; p <= 23; p++) {
    const { col, top } = layout(p)
    const x = pointX(col)
    const dark = (p + (top ? 0 : 1)) % 2 === 0
    const fill = dark ? '#7a5230' : '#c7a06a'

    const verts: [number, number][] = top
      ? [[x, TOP_BASE], [x + POINT_W, TOP_BASE], [x + POINT_W / 2, TOP_BASE + APEX]]
      : [[x, BOT_BASE], [x + POINT_W, BOT_BASE], [x + POINT_W / 2, BOT_BASE - APEX]]
    triangles.push(
      <polygon key={`t${p}`} points={poly(verts, o)} fill={fill} stroke="#3a2a18" strokeWidth={1} />,
    )

    const white = board.checkers[0][p]
    const black = board.checkers[1][23 - p]
    const count = white + black
    const owner = white > 0 ? 0 : 1
    drawStack(checkers, p, x + POINT_W / 2, top, count, owner, o)

    if (showNumbers) {
      // Point number is perspective-relative; position follows the point but the glyph stays upright.
      const human = o.flipV ? 24 - p : p + 1
      // Centered in the 22px frame gutter above/below the play panel (symmetric so flip stays clear of edges).
      const ly = top ? TOP_BASE - 19 : BOT_BASE + 19
      labels.push(
        <text
          key={`l${p}`}
          x={px(x + POINT_W / 2, o)}
          y={py(ly, o)}
          textAnchor="middle"
          dominantBaseline="central"
          fontSize={16}
          fontWeight={600}
          fill="#d8c9a8"
          style={{ pointerEvents: 'none', userSelect: 'none' }}
        >
          {human}
        </text>,
      )
    }
  }

  // Bar checkers (hit / entering)
  const barX = MARGIN + 6 * POINT_W + BAR_W / 2
  drawBar(checkers, 'w', barX, false, board.checkers[0][24], 0, o)
  drawBar(checkers, 'b', barX, true, board.checkers[1][24], 1, o)

  const panelW = 6 * POINT_W + 16
  const panelH = BOT_BASE - TOP_BASE + 16

  return (
    <svg viewBox="0 0 1000 640" className="board-svg" role="img" aria-label="Backgammon board">
      <rect x={0} y={0} width={1000} height={640} rx={14} fill="#2b1d10" />
      <rect x={rectX(MARGIN - 8, panelW, o)} y={rectY(TOP_BASE - 8, panelH, o)} width={panelW} height={panelH}
        fill="#e9d6b0" stroke="#3a2a18" strokeWidth={2} />
      <rect x={rectX(pointX(6) - 8, panelW, o)} y={rectY(TOP_BASE - 8, panelH, o)} width={panelW} height={panelH}
        fill="#e9d6b0" stroke="#3a2a18" strokeWidth={2} />
      {/* bar */}
      <rect x={rectX(MARGIN + 6 * POINT_W, BAR_W, o)} y={rectY(TOP_BASE - 8, panelH, o)} width={BAR_W} height={panelH}
        fill="#5a3f22" stroke="#3a2a18" strokeWidth={2} />

      {triangles}
      {labels}
      {checkers}

      {/* off trays */}
      <OffTray x={920} label={player0} count={board.off[0]} player={0} top={false} o={o} />
      <OffTray x={920} label={player1} count={board.off[1]} player={1} top o={o} />

      {/* dice */}
      {board.dice[0] > 0 && (
        <>
          <Die x={560} y={300} value={board.dice[0]} o={o} />
          <Die x={640} y={300} value={board.dice[1]} o={o} />
        </>
      )}

      {/* cube */}
      <Cube value={board.cube} owner={board.cubeOwner} o={o} />
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
  o: Orient,
) {
  if (count <= 0) {
    return
  }
  const shown = Math.min(count, 5)
  for (let k = 0; k < shown; k++) {
    const cy = top ? TOP_BASE + R + k * STACK : BOT_BASE - R - k * STACK
    out.push(<Checker key={`c${p}-${k}`} cx={cx} cy={cy} owner={owner} o={o} />)
    if (count > 5 && k === shown - 1) {
      out.push(
        <text key={`n${p}`} x={px(cx, o)} y={py(cy, o) + 6} textAnchor="middle" fontSize={22} fontWeight={700}
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
  o: Orient,
) {
  if (count <= 0) {
    return
  }
  const midStart = top ? 60 : 580
  for (let k = 0; k < Math.min(count, 4); k++) {
    const cy = top ? midStart + k * STACK : midStart - k * STACK
    out.push(<Checker key={`bar${key}${k}`} cx={cx} cy={cy} owner={owner} o={o} />)
  }
  if (count > 4) {
    const cy = top ? midStart + 3 * STACK : midStart - 3 * STACK
    out.push(
      <text key={`barn${key}`} x={px(cx, o)} y={py(cy, o) + 6}
        textAnchor="middle" fontSize={20} fontWeight={700} fill={owner === 0 ? '#222' : '#eee'}>
        {count}
      </text>,
    )
  }
}

function Checker({ cx, cy, owner, o }: { cx: number; cy: number; owner: number; o: Orient }) {
  const x = px(cx, o)
  const y = py(cy, o)
  const fill = owner === 0 ? '#f4f1ea' : '#2a2a2a'
  const stroke = owner === 0 ? '#b9b2a2' : '#000'
  return (
    <g>
      <circle cx={x} cy={y} r={R} fill={fill} stroke={stroke} strokeWidth={2} />
      <circle cx={x} cy={y} r={R - 7} fill="none" stroke={owner === 0 ? '#d8d2c2' : '#444'} strokeWidth={2} />
    </g>
  )
}

function Die({ x, y, value, o }: { x: number; y: number; value: number; o: Orient }) {
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
      <rect x={rectX(x, size, o)} y={rectY(y, size, o)} width={size} height={size} rx={10} fill="#f4f1ea" stroke="#333" strokeWidth={2} />
      {(positions[value] ?? []).map(([dx, dy], i) => (
        <circle key={i} cx={px(x + dx * size, o)} cy={py(y + dy * size, o)} r={5} fill="#222" />
      ))}
    </g>
  )
}

function Cube({ value, owner, o }: { value: number; owner: number; o: Orient }) {
  const display = value <= 1 ? 64 : value
  const size = 48
  const x = 6
  const y = owner === 0 ? 560 : owner === 1 ? 40 : 300
  return (
    <g>
      <rect x={rectX(x, size, o)} y={rectY(y, size, o)} width={size} height={size} rx={8} fill="#eee" stroke="#222" strokeWidth={2} />
      <text x={px(x + size / 2, o)} y={py(y + size / 2, o) + 8} textAnchor="middle" fontSize={24} fontWeight={700} fill="#222">
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
  o,
}: {
  x: number
  label: string
  count: number
  player: number
  top: boolean
  o: Orient
}) {
  const w = 58
  const h = 120
  const y = top ? TOP_BASE : BOT_BASE - 120
  return (
    <g>
      <rect x={rectX(x, w, o)} y={rectY(y, h, o)} width={w} height={h} rx={8} fill="#1d130a" stroke="#3a2a18" strokeWidth={2} />
      <text x={px(x + 29, o)} y={py(y + 50, o) + 8} textAnchor="middle" fontSize={26} fontWeight={700}
        fill={player === 0 ? '#f4f1ea' : '#c9c9c9'}>
        {count}
      </text>
      <text x={px(x + 29, o)} y={py(top ? y + 104 : y + 16, o) + 4} textAnchor="middle" fontSize={11} fill="#b8a98a">
        {label.slice(0, 8)}
      </text>
    </g>
  )
}
