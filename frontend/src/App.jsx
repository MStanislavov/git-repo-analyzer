import { useState, useEffect, useRef, useCallback } from 'react'
import './App.css'

const STEPS = ['CLONING', 'BUILDING', 'ANALYZING', 'COLLECTING']

/* ── Utilities ── */

function ratingToLetter(val) {
  if (val == null) return '-'
  const n = Math.round(val)
  return ['', 'A', 'B', 'C', 'D', 'E'][n] || '-'
}

function ratingClass(val) {
  const letter = ratingToLetter(val).toLowerCase()
  return letter !== '-' ? `rating rating-${letter}` : 'rating'
}

function debtDisplay(minutes) {
  if (minutes == null) return '-'
  if (minutes < 60) return `${minutes}m`
  const hours = Math.floor(minutes / 60)
  const rem = minutes % 60
  return rem > 0 ? `${hours}h ${rem}m` : `${hours}h`
}

function getStepState(currentStep, analysisStatus, stepName) {
  const currentIdx = STEPS.indexOf(currentStep)
  const thisIdx = STEPS.indexOf(stepName)
  if (analysisStatus === 'SUCCEEDED') return 'done'
  if (analysisStatus === 'FAILED') {
    if (thisIdx < currentIdx) return 'done'
    if (thisIdx === currentIdx) return 'failed'
    return ''
  }
  if (thisIdx < currentIdx) return 'done'
  if (thisIdx === currentIdx) return 'active'
  return ''
}

/* ── Icons ── */

const icons = {
  code: (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" />
    </svg>
  ),
  grid: (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" />
      <rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" />
    </svg>
  ),
  sun: (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="5" />
      <line x1="12" y1="1" x2="12" y2="3" /><line x1="12" y1="21" x2="12" y2="23" />
      <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" /><line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
      <line x1="1" y1="12" x2="3" y2="12" /><line x1="21" y1="12" x2="23" y2="12" />
      <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" /><line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
    </svg>
  ),
  moon: (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  ),
  gitBranch: (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="6" y1="3" x2="6" y2="15" /><circle cx="18" cy="6" r="3" /><circle cx="6" cy="18" r="3" />
      <path d="M18 9a9 9 0 0 1-9 9" />
    </svg>
  ),
  folder: (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </svg>
  ),
  check: (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  ),
  xCircle: (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" /><line x1="15" y1="9" x2="9" y2="15" /><line x1="9" y1="9" x2="15" y2="15" />
    </svg>
  ),
  play: (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" /><polygon points="10 8 16 12 10 16 10 8" />
    </svg>
  ),
  menu: (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="18" x2="21" y2="18" />
    </svg>
  ),
}

/* ── Sidebar ── */

function Sidebar({ view, setView, theme, toggleTheme, mobileOpen, setMobileOpen }) {
  return (
    <>
      <div className={`sidebar-overlay ${mobileOpen ? 'open' : ''}`} onClick={() => setMobileOpen(false)} />
      <aside className={`sidebar ${mobileOpen ? 'open' : ''}`}>
        <div className="sidebar-brand">
          <span className="brand-icon">{icons.gitBranch}</span>
          <div>
            <div className="brand-name">Git Repo Analyzer</div>
            <div className="brand-sub">Static code analyzer</div>
          </div>
        </div>

        <nav className="sidebar-nav">
          <button className={`nav-item ${view === 'analyze' ? 'active' : ''}`} onClick={() => { setView('analyze'); setMobileOpen(false) }}>
            {icons.code}<span>Analyze</span>
          </button>
          <button className={`nav-item ${view === 'dashboard' ? 'active' : ''}`} onClick={() => { setView('dashboard'); setMobileOpen(false) }}>
            {icons.grid}<span>Dashboard</span>
          </button>
        </nav>

        <div className="sidebar-footer">
          <button className="theme-btn" onClick={toggleTheme}>
            {theme === 'dark' ? icons.sun : icons.moon}
            <span>{theme === 'dark' ? 'Light Mode' : 'Dark Mode'}</span>
          </button>
        </div>
      </aside>
    </>
  )
}

/* ── Stats ── */

function StatsCards({ results, activeCount }) {
  const succeeded = results.filter(r => r.analysisStatus === 'SUCCEEDED').length
  const failed = results.filter(r => r.analysisStatus === 'FAILED').length

  return (
    <div className="stats-row">
      <div className="stat-card">
        <div className="stat-icon icon-repos">{icons.folder}</div>
        <div className="stat-label">Repositories</div>
        <div className="stat-value">{results.length}</div>
      </div>
      <div className="stat-card">
        <div className="stat-icon icon-success">{icons.check}</div>
        <div className="stat-label">Succeeded</div>
        <div className="stat-value">{succeeded}</div>
      </div>
      <div className="stat-card">
        <div className="stat-icon icon-failed">{icons.xCircle}</div>
        <div className="stat-label">Failed</div>
        <div className="stat-value">{failed}</div>
      </div>
      <div className="stat-card">
        <div className="stat-icon icon-active">{icons.play}</div>
        <div className="stat-label">In Progress</div>
        <div className="stat-value">{activeCount}</div>
      </div>
    </div>
  )
}

/* ── Input Section ── */

function InputSection({ onAnalyzing, cloneDirectory, setCloneDirectory, persistCloneDirectory }) {
  const [mode, setMode] = useState('url')
  const [url, setUrl] = useState('')
  const [xml, setXml] = useState('')
  const [fileName, setFileName] = useState('')
  const [loading, setLoading] = useState(false)
  const [picking, setPicking] = useState(false)
  const fileRef = useRef(null)

  const openNativePicker = async () => {
    setPicking(true)
    try {
      const res = await fetch('/api/v1/config/select-directory', { method: 'POST' })
      if (res.ok) {
        const data = await res.json()
        if (data.cloneDirectory) setCloneDirectory(data.cloneDirectory)
      }
    } catch { /* ignore */ }
    finally { setPicking(false) }
  }

  const submitUrl = async () => {
    if (!url.trim()) return
    setLoading(true)
    try {
      const res = await fetch('/api/v1/analyze/url', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: url.trim(), cloneDirectory: cloneDirectory.trim() })
      })
      if (res.ok) { onAnalyzing(); setUrl('') }
    } finally { setLoading(false) }
  }

  const submitXml = async () => {
    setLoading(true)
    try {
      const formData = new FormData()
      if (fileRef.current?.files[0]) {
        formData.append('file', fileRef.current.files[0])
      } else if (xml.trim()) {
        formData.append('xml', xml.trim())
      } else { return }
      if (cloneDirectory.trim()) formData.append('cloneDirectory', cloneDirectory.trim())
      const res = await fetch('/api/v1/analyze/xml', { method: 'POST', body: formData })
      if (res.ok) { onAnalyzing(); setXml(''); setFileName(''); if (fileRef.current) fileRef.current.value = '' }
    } finally { setLoading(false) }
  }

  const handleFile = (e) => {
    const file = e.target.files[0]
    setFileName(file ? file.name : '')
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2 className="section-title">New Analysis</h2>
      </div>

      <div className="form-group">
        <label className="form-label">Clone Directory</label>
        <div className="input-row">
          <input
            type="text"
            className="input mono"
            placeholder="/path/to/clone-dirs"
            value={cloneDirectory}
            onChange={(e) => setCloneDirectory(e.target.value)}
            onBlur={() => persistCloneDirectory(cloneDirectory)}
          />
          <button className="btn btn-outline btn-sm" onClick={openNativePicker} disabled={picking}>
            {picking ? 'Selecting...' : 'Browse'}
          </button>
        </div>
      </div>

      <div className="mode-toggle">
        <button className={mode === 'url' ? 'active' : ''} onClick={() => setMode('url')}>Single URL</button>
        <button className={mode === 'xml' ? 'active' : ''} onClick={() => setMode('xml')}>Bulk XML</button>
      </div>

      {mode === 'url' ? (
        <div className="input-row">
          <input
            type="text"
            className="input"
            placeholder="https://github.com/user/repo.git"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && submitUrl()}
          />
          <button className="btn btn-primary" onClick={submitUrl} disabled={loading || !url.trim()}>
            {loading ? 'Starting...' : 'Analyze'}
          </button>
        </div>
      ) : (
        <div className="xml-group">
          <textarea
            className="input mono"
            placeholder={'<repositories>\n  <repository>\n    <url>https://github.com/user/repo.git</url>\n  </repository>\n</repositories>'}
            value={xml}
            onChange={(e) => setXml(e.target.value)}
          />
          <div className="file-upload" onClick={() => fileRef.current?.click()}>
            <input type="file" accept=".xml" ref={fileRef} onChange={handleFile} />
            {fileName ? fileName : 'Click to upload XML file or drag and drop'}
          </div>
          <button className="btn btn-primary" onClick={submitXml} disabled={loading || (!xml.trim() && !fileName)}>
            {loading ? 'Starting...' : 'Analyze All'}
          </button>
        </div>
      )}
      <p className="hint">Currently supports Java (Maven) repositories only</p>
    </div>
  )
}

/* ── Progress ── */

function ProgressSection({ progress }) {
  const entries = Object.entries(progress)
  if (entries.length === 0) return null

  return (
    <div className="card">
      <div className="card-header">
        <h2 className="section-title">Pipeline Progress</h2>
      </div>
      <div className="progress-list">
        {entries.map(([repoName, info]) => (
          <div className="progress-row" key={repoName}>
            <span className="progress-name">{repoName}</span>
            <div className="progress-steps">
              {STEPS.map((step, i) => (
                <span key={step}>
                  {i > 0 && <span className="step-arrow">&rsaquo;</span>}
                  <span className={`step ${getStepState(info.step, info.status, step)}`}>{step}</span>
                </span>
              ))}
            </div>
            <span className={`badge badge-${info.status === 'SUCCEEDED' ? 'success' : info.status === 'FAILED' ? 'danger' : 'info'}`}>
              {info.status === 'IN_PROGRESS' ? 'running' : info.status?.toLowerCase()}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

/* ── Repo Table ── */

function RepoTable({ results, selectedId, onSelect }) {
  if (results.length === 0) {
    return (
      <div className="card">
        <div className="empty-state">No results yet. Submit a repository to analyze.</div>
      </div>
    )
  }

  return (
    <div className="card card-table">
      <div className="card-header">
        <h2 className="section-title">Analyzed Repositories</h2>
      </div>
      <table className="table">
        <thead>
          <tr>
            <th>Repository</th>
            <th>Status</th>
            <th>Quality Gate</th>
            <th>Tech Debt</th>
            <th>Analyzed</th>
          </tr>
        </thead>
        <tbody>
          {results.map((r) => (
            <tr
              key={r.id}
              className={r.id === selectedId ? 'selected' : ''}
              onClick={() => onSelect(r.id === selectedId ? null : r.id)}
            >
              <td className="cell-repo">
                <span className="repo-name">{r.repositoryName}</span>
              </td>
              <td>
                <span className={`badge badge-${r.analysisStatus === 'SUCCEEDED' ? 'success' : r.analysisStatus === 'FAILED' ? 'danger' : 'info'}`}>
                  {r.analysisStatus === 'IN_PROGRESS' ? 'running' : r.analysisStatus?.toLowerCase()}
                </span>
              </td>
              <td>
                {r.alertStatus ? (
                  <span className={`badge badge-${r.alertStatus === 'OK' ? 'success' : 'danger'}`}>
                    {r.alertStatus === 'OK' ? 'passed' : 'failed'}
                  </span>
                ) : <span className="text-muted">-</span>}
              </td>
              <td className="mono">{debtDisplay(r.sqaleIndex)}</td>
              <td className="text-muted">{r.analyzedAt ? new Date(r.analyzedAt).toLocaleString() : '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* ── Repo Detail ── */

function RepoDetail({ selected, onReanalyze, onDelete, onClearData }) {
  const [confirmAction, setConfirmAction] = useState(null)

  if (!selected) return null

  return (
    <div className="card detail-card">
      <div className="detail-header">
        <div>
          <h3 className="detail-name">{selected.repositoryName}</h3>
          {selected.repositoryUrl && <span className="detail-url mono">{selected.repositoryUrl}</span>}
        </div>
        <div className="detail-badges">
          {selected.alertStatus && (
            <span className={`badge badge-${selected.alertStatus === 'OK' ? 'success' : 'danger'}`}>
              Gate: {selected.alertStatus === 'OK' ? 'passed' : 'failed'}
            </span>
          )}
          <span className={`badge badge-${selected.analysisStatus === 'SUCCEEDED' ? 'success' : selected.analysisStatus === 'FAILED' ? 'danger' : 'info'}`}>
            {selected.analysisStatus?.toLowerCase()}
          </span>
          {selected.analyzedAt && <span className="text-muted text-sm">{new Date(selected.analyzedAt).toLocaleString()}</span>}
        </div>
      </div>

      {selected.analysisStatus === 'FAILED' && selected.errorMessage && (
        <div className="error-box">{selected.errorMessage}</div>
      )}

      {selected.analysisStatus === 'SUCCEEDED' && (
        <div className="metrics-grid">
          <div className="metric-card">
            <div className="metric-val">{selected.ncloc?.toLocaleString() ?? '-'}</div>
            <div className="metric-lbl">Lines of Code</div>
          </div>
          <div className="metric-card">
            <div className="metric-val">{selected.bugs ?? '-'}</div>
            <div className="metric-lbl">Bugs</div>
          </div>
          <div className="metric-card">
            <div className="metric-val">{selected.vulnerabilities ?? '-'}</div>
            <div className="metric-lbl">Vulnerabilities</div>
          </div>
          <div className="metric-card">
            <div className="metric-val">{selected.codeSmells ?? '-'}</div>
            <div className="metric-lbl">Code Smells</div>
          </div>
          <div className="metric-card">
            <div className="metric-val">{debtDisplay(selected.sqaleIndex)}</div>
            <div className="metric-lbl">Tech Debt</div>
          </div>
          <div className="metric-card">
            <div className="metric-val">{selected.coverage != null ? `${selected.coverage.toFixed(1)}%` : '-'}</div>
            <div className="metric-lbl">Coverage</div>
          </div>
          <div className="metric-card">
            <div className="metric-val">{selected.duplicatedLinesDensity != null ? `${selected.duplicatedLinesDensity.toFixed(1)}%` : '-'}</div>
            <div className="metric-lbl">Duplication</div>
          </div>
          <div className="metric-card">
            <div className="metric-val"><span className={ratingClass(selected.reliabilityRating)}>{ratingToLetter(selected.reliabilityRating)}</span></div>
            <div className="metric-lbl">Reliability</div>
          </div>
          <div className="metric-card">
            <div className="metric-val"><span className={ratingClass(selected.securityRating)}>{ratingToLetter(selected.securityRating)}</span></div>
            <div className="metric-lbl">Security</div>
          </div>
          <div className="metric-card">
            <div className="metric-val"><span className={ratingClass(selected.sqaleRating)}>{ratingToLetter(selected.sqaleRating)}</span></div>
            <div className="metric-lbl">Maintainability</div>
          </div>
        </div>
      )}

      <div className="detail-actions">
        {selected.analysisStatus !== 'IN_PROGRESS' && (
          <button className="btn btn-primary btn-sm" onClick={() => onReanalyze(selected.repositoryUrl)}>Re-analyze</button>
        )}
        {confirmAction === 'delete' ? (
          <div className="confirm-group">
            <span className="text-muted text-sm">Delete repo + files?</span>
            <button className="btn btn-danger btn-sm" onClick={() => { onDelete(selected.id); setConfirmAction(null) }}>Confirm</button>
            <button className="btn btn-ghost btn-sm" onClick={() => setConfirmAction(null)}>Cancel</button>
          </div>
        ) : (
          <button className="btn btn-danger btn-sm" onClick={() => setConfirmAction('delete')}>Remove</button>
        )}
        {confirmAction === 'clear' ? (
          <div className="confirm-group">
            <span className="text-muted text-sm">Clear analysis data?</span>
            <button className="btn btn-warning btn-sm" onClick={() => { onClearData(selected.id); setConfirmAction(null) }}>Confirm</button>
            <button className="btn btn-ghost btn-sm" onClick={() => setConfirmAction(null)}>Cancel</button>
          </div>
        ) : (
          <button className="btn btn-warning btn-sm" onClick={() => setConfirmAction('clear')}>Clear Data</button>
        )}
      </div>
    </div>
  )
}

/* ── App ── */

function App() {
  const [view, setView] = useState('analyze')
  const [theme, setTheme] = useState(() => {
    try { return localStorage.getItem('theme') || 'dark' } catch { return 'dark' }
  })
  const [results, setResults] = useState([])
  const [progress, setProgress] = useState({})
  const [cloneDirectory, setCloneDirectory] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    try { localStorage.setItem('theme', theme) } catch { /* ignore */ }
  }, [theme])

  const toggleTheme = useCallback(() => setTheme(t => t === 'dark' ? 'light' : 'dark'), [])

  const fetchResults = useCallback(async () => {
    try {
      const res = await fetch('/api/v1/results')
      if (res.ok) setResults(await res.json())
    } catch { /* ignore */ }
  }, [])

  useEffect(() => {
    fetchResults()
    fetch('/api/v1/config/clone-directory')
      .then(res => res.ok ? res.json() : null)
      .then(data => { if (data) setCloneDirectory(data.cloneDirectory) })
      .catch(() => {})
  }, [fetchResults])

  useEffect(() => {
    const eventSource = new EventSource('/api/v1/analyze/stream')
    eventSource.addEventListener('progress', (e) => {
      const data = JSON.parse(e.data)
      setProgress(prev => ({
        ...prev,
        [data.repoName]: { step: data.step, status: data.status, message: data.message }
      }))
      if (data.status === 'SUCCEEDED' || data.status === 'FAILED') {
        setTimeout(fetchResults, 1000)
      }
    })
    eventSource.onerror = () => { eventSource.close() }
    return () => eventSource.close()
  }, [fetchResults])

  const persistCloneDirectory = useCallback(async (dir) => {
    if (!dir.trim()) return
    try {
      await fetch('/api/v1/config/clone-directory', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cloneDirectory: dir.trim() })
      })
    } catch { /* ignore */ }
  }, [])

  const reanalyze = async (repoUrl) => {
    if (!repoUrl) return
    const res = await fetch('/api/v1/analyze/url', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: repoUrl, cloneDirectory: cloneDirectory.trim() })
    })
    if (res.ok) { fetchResults(); setView('analyze') }
  }

  const deleteRepo = async (id) => {
    const res = await fetch(`/api/v1/results/${id}`, { method: 'DELETE' })
    if (res.ok) { setSelectedId(null); fetchResults() }
  }

  const clearData = async (id) => {
    const res = await fetch(`/api/v1/results/${id}/data`, { method: 'DELETE' })
    if (res.ok) fetchResults()
  }

  const activeCount = Object.values(progress).filter(p => p.status === 'IN_PROGRESS').length
  const selected = results.find(r => r.id === selectedId) || null

  return (
    <div className="layout">
      <Sidebar
        view={view} setView={setView}
        theme={theme} toggleTheme={toggleTheme}
        mobileOpen={mobileOpen} setMobileOpen={setMobileOpen}
      />

      <main className="main">
        <div className="topbar">
          <button className="mobile-menu" onClick={() => setMobileOpen(true)}>{icons.menu}</button>
          <h1 className="page-title">{view === 'analyze' ? 'Analyze' : 'Dashboard'}</h1>
        </div>

        <StatsCards results={results} activeCount={activeCount} />

        {view === 'analyze' ? (
          <>
            <InputSection
              onAnalyzing={() => { fetchResults() }}
              cloneDirectory={cloneDirectory}
              setCloneDirectory={setCloneDirectory}
              persistCloneDirectory={persistCloneDirectory}
            />
            <ProgressSection progress={progress} />
          </>
        ) : (
          <>
            <RepoTable results={results} selectedId={selectedId} onSelect={setSelectedId} />
            <RepoDetail selected={selected} onReanalyze={reanalyze} onDelete={deleteRepo} onClearData={clearData} />
          </>
        )}
      </main>
    </div>
  )
}

export default App
