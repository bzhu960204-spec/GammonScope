import { useEffect, useRef, useState } from 'react'
import {
  deleteMatch,
  importMatch,
  listMatches,
  type MatchSummary,
} from '../api/client'

interface Props {
  onOpen: (id: number) => void
}

export default function MatchListPage({ onOpen }: Props) {
  const [matches, setMatches] = useState<MatchSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  async function refresh() {
    try {
      setMatches(await listMatches())
      setError(null)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  useEffect(() => {
    refresh()
  }, [])

  async function onImport() {
    const file = fileRef.current?.files?.[0]
    if (!file) {
      return
    }
    setBusy(true)
    try {
      await importMatch(file)
      if (fileRef.current) {
        fileRef.current.value = ''
      }
      await refresh()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function onDelete(id: number) {
    if (!confirm('Delete this match?')) {
      return
    }
    try {
      await deleteMatch(id)
      await refresh()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <div>
      <section className="panel">
        <h2>Import a match</h2>
        <p className="hint">Supported formats: Jellyfish <code>.mat</code> and GNU Backgammon <code>.sgf</code>.</p>
        <div className="import-row">
          <input ref={fileRef} type="file" accept=".mat,.sgf" />
          <button onClick={onImport} disabled={busy}>
            {busy ? 'Importing…' : 'Import'}
          </button>
        </div>
      </section>

      {error && <p className="status-error">{error}</p>}

      <section className="panel">
        <h2>Matches</h2>
        {matches.length === 0 ? (
          <p className="hint">No matches yet. Import a file to get started.</p>
        ) : (
          <table className="match-table">
            <thead>
              <tr>
                <th>Players</th>
                <th>Length</th>
                <th>Games</th>
                <th>Format</th>
                <th>Event</th>
                <th>Date</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {matches.map((m) => (
                <tr key={m.id}>
                  <td className="link" onClick={() => onOpen(m.id)}>
                    {m.player0} vs {m.player1}
                  </td>
                  <td>{m.matchLength === 0 ? 'Money' : `${m.matchLength}pt`}</td>
                  <td>{m.gameCount}</td>
                  <td>{m.sourceFormat}</td>
                  <td>{m.event ?? '—'}</td>
                  <td>{m.matchDate ?? '—'}</td>
                  <td>
                    <button className="ghost" onClick={() => onOpen(m.id)}>Open</button>
                    <button className="ghost danger" onClick={() => onDelete(m.id)}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
