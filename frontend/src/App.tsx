import { useState } from 'react'
import MatchListPage from './pages/MatchListPage'
import ReplayPage from './pages/ReplayPage'
import './App.css'

function App() {
  const [openId, setOpenId] = useState<number | null>(null)

  return (
    <main className="app">
      <header className="app-header">
        <h1 onClick={() => setOpenId(null)} className="brand">GammonScope</h1>
        <p className="tagline">Import, browse and replay your backgammon matches.</p>
      </header>

      {openId === null ? (
        <MatchListPage onOpen={setOpenId} />
      ) : (
        <ReplayPage matchId={openId} onBack={() => setOpenId(null)} />
      )}
    </main>
  )
}

export default App
