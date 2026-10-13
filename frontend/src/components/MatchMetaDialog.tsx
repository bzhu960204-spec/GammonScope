import { useEffect, useState } from 'react'
import type { MatchMetadataInput } from '../api/client'

interface Props {
  title: string
  initial: MatchMetadataInput
  saving?: boolean
  error?: string | null
  onCancel: () => void
  onSave: (meta: MatchMetadataInput) => void
}

const EMPTY_META: MatchMetadataInput = {
  player0: '',
  player1: '',
  matchLength: 0,
  variation: 'Backgammon',
  result: '',
  event: '',
  site: '',
  round: '',
  matchDate: '',
}

export function emptyMeta(): MatchMetadataInput {
  return { ...EMPTY_META }
}

export default function MatchMetaDialog({ title, initial, saving, error, onCancel, onSave }: Props) {
  const [meta, setMeta] = useState<MatchMetadataInput>(initial)

  useEffect(() => {
    setMeta(initial)
  }, [initial])

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onCancel()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onCancel])

  function set<K extends keyof MatchMetadataInput>(key: K, value: MatchMetadataInput[K]) {
    setMeta((prev) => ({ ...prev, [key]: value }))
  }

  function submit(e: React.FormEvent) {
    e.preventDefault()
    onSave(meta)
  }

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <form className="meta-form" onSubmit={submit}>
          <div className="meta-grid">
            <label>
              <span>Player 1</span>
              <input
                value={meta.player0}
                placeholder="Player 1"
                onChange={(e) => set('player0', e.target.value)}
              />
            </label>
            <label>
              <span>Player 2</span>
              <input
                value={meta.player1}
                placeholder="Player 2"
                onChange={(e) => set('player1', e.target.value)}
              />
            </label>
            <label>
              <span>Match length (0 = money)</span>
              <input
                type="number"
                min={0}
                value={meta.matchLength}
                onChange={(e) => set('matchLength', Math.max(0, Number(e.target.value) || 0))}
              />
            </label>
            <label>
              <span>Variation</span>
              <input
                value={meta.variation}
                placeholder="Backgammon"
                onChange={(e) => set('variation', e.target.value)}
              />
            </label>
            <label>
              <span>Result</span>
              <input value={meta.result} onChange={(e) => set('result', e.target.value)} />
            </label>
            <label>
              <span>Event</span>
              <input value={meta.event} onChange={(e) => set('event', e.target.value)} />
            </label>
            <label>
              <span>Site</span>
              <input value={meta.site} onChange={(e) => set('site', e.target.value)} />
            </label>
            <label>
              <span>Round</span>
              <input value={meta.round} onChange={(e) => set('round', e.target.value)} />
            </label>
            <label>
              <span>Date</span>
              <input
                value={meta.matchDate}
                placeholder="YYYY-MM-DD"
                onChange={(e) => set('matchDate', e.target.value)}
              />
            </label>
          </div>

          {error && <p className="status-error">{error}</p>}

          <div className="modal-actions">
            <button type="button" className="ghost" onClick={onCancel} disabled={saving}>
              Cancel
            </button>
            <button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
