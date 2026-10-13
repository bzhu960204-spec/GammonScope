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

const ACCEPTED_EXTENSIONS = ['.mat', '.sgf', '.json']

type UploadStatus = 'pending' | 'uploading' | 'done' | 'error'

interface UploadItem {
  name: string
  status: UploadStatus
  error?: string
}

function isAccepted(file: File): boolean {
  const name = file.name.toLowerCase()
  return ACCEPTED_EXTENSIONS.some((ext) => name.endsWith(ext))
}

export default function MatchListPage({ onOpen }: Props) {
  const [matches, setMatches] = useState<MatchSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [uploads, setUploads] = useState<UploadItem[]>([])
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

  async function uploadFiles(fileList: FileList | File[]) {
    const files = Array.from(fileList)
    if (files.length === 0) {
      return
    }

    const accepted = files.filter(isAccepted)
    const rejected = files.filter((f) => !isAccepted(f))

    if (accepted.length === 0) {
      setError('No supported files selected. Only .mat, .sgf and .json files are accepted.')
      return
    }

    setError(rejected.length > 0
      ? `Skipped ${rejected.length} unsupported file(s): ${rejected.map((f) => f.name).join(', ')}`
      : null)

    setUploads(accepted.map((f) => ({ name: f.name, status: 'pending' })))
    setBusy(true)

    await Promise.allSettled(
      accepted.map(async (file, index) => {
        setUploads((prev) => prev.map((u, i) => (i === index ? { ...u, status: 'uploading' } : u)))
        try {
          await importMatch(file)
          setUploads((prev) => prev.map((u, i) => (i === index ? { ...u, status: 'done' } : u)))
        } catch (e) {
          setUploads((prev) =>
            prev.map((u, i) => (i === index ? { ...u, status: 'error', error: (e as Error).message } : u)))
        }
      }),
    )

    setBusy(false)
    await refresh()
  }

  function onDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragging(false)
    if (busy) {
      return
    }
    uploadFiles(e.dataTransfer.files)
  }

  function onInputChange() {
    const files = fileRef.current?.files
    if (files && files.length > 0) {
      uploadFiles(files)
    }
    if (fileRef.current) {
      fileRef.current.value = ''
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
        <p className="hint">Supported formats: Jellyfish <code>.mat</code>, GNU Backgammon <code>.sgf</code> and GammonScope <code>.json</code>.</p>
        <div
          className={`dropzone${dragging ? ' dragging' : ''}${busy ? ' busy' : ''}`}
          onClick={() => !busy && fileRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault()
            if (!busy) setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
        >
          <input
            ref={fileRef}
            type="file"
            accept=".mat,.sgf,.json"
            multiple
            hidden
            onChange={onInputChange}
          />
          <p className="dropzone-title">
            {busy ? 'Importing…' : 'Drag & drop files here'}
          </p>
          <p className="hint">or click to browse — multiple files supported</p>
        </div>

        {uploads.length > 0 && (
          <ul className="upload-list">
            {uploads.map((u, i) => (
              <li key={i} className={`upload-item ${u.status}`}>
                <span className="upload-name">{u.name}</span>
                <span className="upload-state">
                  {u.status === 'pending' && 'Queued'}
                  {u.status === 'uploading' && 'Uploading…'}
                  {u.status === 'done' && 'Imported'}
                  {u.status === 'error' && `Failed: ${u.error ?? 'unknown error'}`}
                </span>
              </li>
            ))}
          </ul>
        )}
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
