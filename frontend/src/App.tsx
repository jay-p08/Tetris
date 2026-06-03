import { useEffect, useMemo, useReducer, useRef, useState } from 'react'
import './App.css'

const BOARD_WIDTH = 10
const BOARD_HEIGHT = 20
const FRAME_MS = 1000 / 60
const PIECE_TYPES = ['I', 'O', 'T', 'S', 'Z', 'J', 'L'] as const

type PieceType = (typeof PIECE_TYPES)[number]
type Cell = PieceType | null
type Board = Cell[][]
type Status = 'idle' | 'playing' | 'paused' | 'gameOver'
type View = 'menu' | 'game' | 'settings'
type ControlAction =
  | 'moveLeft'
  | 'moveRight'
  | 'softDrop'
  | 'hardDrop'
  | 'rotateCCW'
  | 'rotateCW'
  | 'rotate180'
  | 'hold'
  | 'pause'
  | 'start'

type Point = { x: number; y: number }
type Piece = { type: PieceType; x: number; y: number; rotation: number }

type HandlingSettings = {
  das: number
  arr: number
  dcd: number
  sdf: number
}

type Settings = {
  controls: Record<ControlAction, string>
  handling: HandlingSettings
}

type GameState = {
  board: Board
  current: Piece
  hold: PieceType | null
  canHold: boolean
  queue: PieceType[]
  score: number
  lines: number
  level: number
  status: Status
  lastActionWasRotate: boolean
  lastEvent: string
}

type Action =
  | { type: 'start' }
  | { type: 'pause' }
  | { type: 'move'; dx: number }
  | { type: 'shiftHorizontal'; dx: number }
  | { type: 'softDrop' }
  | { type: 'hardDrop' }
  | { type: 'rotate'; direction: 1 | -1 | 2 }
  | { type: 'hold' }
  | { type: 'tick' }

const defaultSettings: Settings = {
  controls: {
    moveLeft: 'ArrowLeft',
    moveRight: 'ArrowRight',
    softDrop: 'ArrowDown',
    hardDrop: 'Space',
    rotateCCW: 'KeyA',
    rotateCW: 'KeyD',
    rotate180: 'KeyS',
    hold: 'ShiftLeft',
    pause: 'KeyP',
    start: 'Enter',
  },
  handling: {
    das: 10,
    arr: 2,
    dcd: 0,
    sdf: 6,
  },
}

const controlLabels: Record<ControlAction, string> = {
  moveLeft: 'Move Left',
  moveRight: 'Move Right',
  softDrop: 'Soft Drop',
  hardDrop: 'Hard Drop',
  rotateCCW: 'Rotate Left',
  rotateCW: 'Rotate Right',
  rotate180: '180 Rotate',
  hold: 'Hold',
  pause: 'Pause',
  start: 'Start',
}

const controlOrder: ControlAction[] = [
  'moveLeft',
  'moveRight',
  'softDrop',
  'hardDrop',
  'rotateCCW',
  'rotateCW',
  'rotate180',
  'hold',
  'pause',
  'start',
]

const SHAPES: Record<PieceType, Point[][]> = {
  I: [
    [
      { x: 0, y: 1 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
      { x: 3, y: 1 },
    ],
    [
      { x: 2, y: 0 },
      { x: 2, y: 1 },
      { x: 2, y: 2 },
      { x: 2, y: 3 },
    ],
    [
      { x: 0, y: 2 },
      { x: 1, y: 2 },
      { x: 2, y: 2 },
      { x: 3, y: 2 },
    ],
    [
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 1, y: 2 },
      { x: 1, y: 3 },
    ],
  ],
  O: [
    [
      { x: 1, y: 0 },
      { x: 2, y: 0 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
    ],
  ],
  T: [
    [
      { x: 1, y: 0 },
      { x: 0, y: 1 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
    ],
    [
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
      { x: 1, y: 2 },
    ],
    [
      { x: 0, y: 1 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
      { x: 1, y: 2 },
    ],
    [
      { x: 1, y: 0 },
      { x: 0, y: 1 },
      { x: 1, y: 1 },
      { x: 1, y: 2 },
    ],
  ],
  S: [
    [
      { x: 1, y: 0 },
      { x: 2, y: 0 },
      { x: 0, y: 1 },
      { x: 1, y: 1 },
    ],
    [
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
      { x: 2, y: 2 },
    ],
  ],
  Z: [
    [
      { x: 0, y: 0 },
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
    ],
    [
      { x: 2, y: 0 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
      { x: 1, y: 2 },
    ],
  ],
  J: [
    [
      { x: 0, y: 0 },
      { x: 0, y: 1 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
    ],
    [
      { x: 1, y: 0 },
      { x: 2, y: 0 },
      { x: 1, y: 1 },
      { x: 1, y: 2 },
    ],
    [
      { x: 0, y: 1 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
      { x: 2, y: 2 },
    ],
    [
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 0, y: 2 },
      { x: 1, y: 2 },
    ],
  ],
  L: [
    [
      { x: 2, y: 0 },
      { x: 0, y: 1 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
    ],
    [
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 1, y: 2 },
      { x: 2, y: 2 },
    ],
    [
      { x: 0, y: 1 },
      { x: 1, y: 1 },
      { x: 2, y: 1 },
      { x: 0, y: 2 },
    ],
    [
      { x: 0, y: 0 },
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 1, y: 2 },
    ],
  ],
}

const LINE_SCORE = [0, 100, 300, 500, 800]
const SPIN_SCORE = [100, 400, 800, 1200, 1600]
const KICKS = [0, -1, 1, -2, 2]

const emptyBoard = (): Board =>
  Array.from({ length: BOARD_HEIGHT }, () => Array<Cell>(BOARD_WIDTH).fill(null))

const makeBag = () => {
  const bag = [...PIECE_TYPES]
  for (let i = bag.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[bag[i], bag[j]] = [bag[j], bag[i]]
  }
  return bag
}

const spawnPiece = (type: PieceType): Piece => ({
  type,
  x: 3,
  y: type === 'I' ? -2 : -1,
  rotation: 0,
})

const fillQueue = (queue: PieceType[]) => {
  const next = [...queue]
  while (next.length < 7) next.push(...makeBag())
  return next
}

const popNext = (queue: PieceType[]) => {
  const nextQueue = fillQueue(queue)
  const [nextType, ...rest] = nextQueue
  return { piece: spawnPiece(nextType), queue: fillQueue(rest) }
}

const createGame = (): GameState => {
  const { piece, queue } = popNext(makeBag())
  return {
    board: emptyBoard(),
    current: piece,
    hold: null,
    canHold: true,
    queue,
    score: 0,
    lines: 0,
    level: 1,
    status: 'idle',
    lastActionWasRotate: false,
    lastEvent: 'Ready',
  }
}

const defaultRankings = [
  { name: 'YOU', score: 0 },
  { name: 'PLAYER01', score: 128400 },
  { name: 'KIM_T', score: 93600 },
  { name: 'STACKER', score: 71900 },
]

const loadRankings = () => {
  try {
    const stored = localStorage.getItem('tetris-rankings')
    return stored
      ? (JSON.parse(stored) as { name: string; score: number }[])
      : defaultRankings
  } catch {
    return defaultRankings
  }
}

const loadSettings = (): Settings => {
  try {
    const stored = localStorage.getItem('tetris-settings')
    return stored
      ? {
          controls: { ...defaultSettings.controls, ...JSON.parse(stored).controls },
          handling: { ...defaultSettings.handling, ...JSON.parse(stored).handling },
        }
      : defaultSettings
  } catch {
    return defaultSettings
  }
}

const getCells = (piece: Piece) =>
  SHAPES[piece.type][piece.rotation % SHAPES[piece.type].length].map((cell) => ({
    x: piece.x + cell.x,
    y: piece.y + cell.y,
  }))

const isValid = (board: Board, piece: Piece) =>
  getCells(piece).every(({ x, y }) => {
    if (x < 0 || x >= BOARD_WIDTH || y >= BOARD_HEIGHT) return false
    return y < 0 || board[y][x] === null
  })

const placePiece = (board: Board, piece: Piece) => {
  const nextBoard = board.map((row) => [...row])
  getCells(piece).forEach(({ x, y }) => {
    if (y >= 0 && y < BOARD_HEIGHT) nextBoard[y][x] = piece.type
  })
  return nextBoard
}

const clearLines = (board: Board) => {
  const remaining = board.filter((row) => row.some((cell) => cell === null))
  const cleared = BOARD_HEIGHT - remaining.length
  const nextRows = Array.from({ length: cleared }, () =>
    Array<Cell>(BOARD_WIDTH).fill(null),
  )
  return { board: [...nextRows, ...remaining], cleared }
}

const isAllSpin = (board: Board, piece: Piece, lastActionWasRotate: boolean) => {
  if (!lastActionWasRotate) return false
  return (
    !isValid(board, { ...piece, x: piece.x - 1 }) &&
    !isValid(board, { ...piece, x: piece.x + 1 }) &&
    !isValid(board, { ...piece, y: piece.y + 1 })
  )
}

const lockPiece = (state: GameState): GameState => {
  const spin = isAllSpin(state.board, state.current, state.lastActionWasRotate)
  const placed = placePiece(state.board, state.current)
  const { board, cleared } = clearLines(placed)
  const totalLines = state.lines + cleared
  const level = Math.floor(totalLines / 10) + 1
  const { piece, queue } = popNext(state.queue)
  const status = isValid(board, piece) ? 'playing' : 'gameOver'
  const spinName = spin ? `${state.current.type}-SPIN` : ''
  const clearName =
    cleared > 0
      ? cleared === 4
        ? 'TETRIS'
        : `${cleared} LINE${cleared > 1 ? 'S' : ''}`
      : ''

  return {
    ...state,
    board,
    current: piece,
    queue,
    canHold: true,
    score:
      state.score +
      (spin ? (SPIN_SCORE[cleared] ?? SPIN_SCORE[0]) : LINE_SCORE[cleared]) *
        state.level,
    lines: totalLines,
    level,
    status,
    lastActionWasRotate: false,
    lastEvent: spinName && clearName ? `${spinName} ${clearName}` : spinName || clearName || 'LOCK',
  }
}

const dropDistance = (board: Board, piece: Piece) => {
  let distance = 0
  while (isValid(board, { ...piece, y: piece.y + distance + 1 })) distance += 1
  return distance
}

const moveDown = (state: GameState, scoreSoftDrop = false): GameState => {
  const nextPiece = { ...state.current, y: state.current.y + 1 }
  if (isValid(state.board, nextPiece)) {
    return {
      ...state,
      current: nextPiece,
      score: state.score + (scoreSoftDrop ? 1 : 0),
      lastActionWasRotate: false,
    }
  }
  return lockPiece(state)
}

const rotatePiece = (state: GameState, direction: 1 | -1 | 2): GameState => {
  const shapeCount = SHAPES[state.current.type].length
  const rotation = (state.current.rotation + direction + shapeCount) % shapeCount

  for (const kick of KICKS) {
    const nextPiece = { ...state.current, x: state.current.x + kick, rotation }
    if (isValid(state.board, nextPiece)) {
      return { ...state, current: nextPiece, lastActionWasRotate: true }
    }
  }
  return state
}

const reducer = (state: GameState, action: Action): GameState => {
  if (action.type === 'start') return { ...createGame(), status: 'playing' }

  if (action.type === 'pause') {
    if (state.status === 'playing') return { ...state, status: 'paused' }
    if (state.status === 'paused') return { ...state, status: 'playing' }
    return state
  }

  if (state.status !== 'playing') return state

  switch (action.type) {
    case 'move': {
      const current = { ...state.current, x: state.current.x + action.dx }
      return isValid(state.board, current)
        ? { ...state, current, lastActionWasRotate: false }
        : state
    }
    case 'shiftHorizontal': {
      let current = state.current
      while (isValid(state.board, { ...current, x: current.x + action.dx })) {
        current = { ...current, x: current.x + action.dx }
      }
      return current === state.current
        ? state
        : { ...state, current, lastActionWasRotate: false }
    }
    case 'softDrop':
      return moveDown(state, true)
    case 'hardDrop': {
      const distance = dropDistance(state.board, state.current)
      return lockPiece({
        ...state,
        current: { ...state.current, y: state.current.y + distance },
        score: state.score + distance * 2,
      })
    }
    case 'rotate':
      return rotatePiece(state, action.direction)
    case 'hold': {
      if (!state.canHold) return state
      if (state.hold) {
        const current = spawnPiece(state.hold)
        return isValid(state.board, current)
          ? {
              ...state,
              current,
              hold: state.current.type,
              canHold: false,
              lastActionWasRotate: false,
            }
          : { ...state, status: 'gameOver' }
      }

      const { piece, queue } = popNext(state.queue)
      return {
        ...state,
        current: piece,
        queue,
        hold: state.current.type,
        canHold: false,
        lastActionWasRotate: false,
      }
    }
    case 'tick':
      return moveDown(state)
    default:
      return state
  }
}

const getGhost = (board: Board, piece: Piece): Piece => ({
  ...piece,
  y: piece.y + dropDistance(board, piece),
})

const buildRenderBoard = (state: GameState) => {
  const cells = state.board.map((row) =>
    row.map((type) => ({ type, ghost: false, active: false })),
  )

  if (state.status === 'playing' || state.status === 'paused') {
    getCells(getGhost(state.board, state.current)).forEach(({ x, y }) => {
      if (y >= 0 && y < BOARD_HEIGHT && !cells[y][x].type) {
        cells[y][x] = { type: state.current.type, ghost: true, active: false }
      }
    })
    getCells(state.current).forEach(({ x, y }) => {
      if (y >= 0 && y < BOARD_HEIGHT) {
        cells[y][x] = { type: state.current.type, ghost: false, active: true }
      }
    })
  }
  return cells
}

const displayKey = (code: string) =>
  code
    .replace('Arrow', '')
    .replace('Key', '')
    .replace('Digit', '')
    .replace('ShiftLeft', 'L Shift')
    .replace('Space', 'Space')

const MiniPiece = ({ type }: { type: PieceType | null }) => {
  const cells = Array.from({ length: 16 }, () => null as PieceType | null)
  if (type) {
    SHAPES[type][0].forEach((cell) => {
      cells[(type === 'I' ? cell.y : cell.y + 1) * 4 + cell.x] = type
    })
  }

  return (
    <div className="mini-grid" aria-label={type ? `${type} piece` : 'empty'}>
      {cells.map((cell, index) => (
        <span className={cell ? `mini-cell ${cell}` : 'mini-cell'} key={index} />
      ))}
    </div>
  )
}

function App() {
  const [view, setView] = useState<View>('menu')
  const [state, dispatch] = useReducer(reducer, undefined, createGame)
  const [rankings, setRankings] = useState(loadRankings)
  const [settings, setSettings] = useState(loadSettings)
  const [listeningFor, setListeningFor] = useState<ControlAction | null>(null)
  const savedGameOverScore = useRef<number | null>(null)
  const pressed = useRef<Record<string, boolean>>({})
  const horizontal = useRef({
    direction: 0,
    startedAt: 0,
    lastRepeatAt: 0,
    arrZeroFired: false,
  })
  const softDrop = useRef({ active: false, lastDropAt: 0 })
  const renderBoard = useMemo(() => buildRenderBoard(state), [state])
  const tickSpeed = Math.max(90, 720 - (state.level - 1) * 55)
  const nextPieces = state.queue.slice(0, 5)

  useEffect(() => {
    localStorage.setItem('tetris-settings', JSON.stringify(settings))
  }, [settings])

  useEffect(() => {
    if (state.status !== 'playing') return
    const timer = window.setInterval(() => dispatch({ type: 'tick' }), tickSpeed)
    return () => window.clearInterval(timer)
  }, [state.status, tickSpeed])

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (state.status !== 'playing') return
      const now = performance.now()
      const dasMs = settings.handling.das * FRAME_MS
      const arrMs = settings.handling.arr * FRAME_MS
      const h = horizontal.current

      if (h.direction !== 0 && now - h.startedAt >= dasMs) {
        if (settings.handling.arr === 0) {
          if (!h.arrZeroFired) {
            dispatch({ type: 'shiftHorizontal', dx: h.direction })
            h.arrZeroFired = true
          }
        } else if (now - h.lastRepeatAt >= arrMs) {
          dispatch({ type: 'move', dx: h.direction })
          h.lastRepeatAt = now
        }
      }

      if (softDrop.current.active) {
        const softInterval = Math.max(16, tickSpeed / Math.max(1, settings.handling.sdf))
        if (now - softDrop.current.lastDropAt >= softInterval) {
          dispatch({ type: 'softDrop' })
          softDrop.current.lastDropAt = now
        }
      }
    }, 16)

    return () => window.clearInterval(timer)
  }, [settings.handling.arr, settings.handling.das, settings.handling.sdf, state.status, tickSpeed])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (listeningFor) {
        event.preventDefault()
        setSettings((current) => ({
          ...current,
          controls: { ...current.controls, [listeningFor]: event.code },
        }))
        setListeningFor(null)
        return
      }

      const action = controlOrder.find((item) => settings.controls[item] === event.code)
      if (!action) return
      event.preventDefault()

      if (view !== 'game' && action !== 'start') return

      if (action === 'start') {
        setView('game')
        dispatch({ type: 'start' })
        return
      }

      if (event.repeat && !['moveLeft', 'moveRight', 'softDrop'].includes(action)) return
      if (pressed.current[action]) return
      pressed.current[action] = true

      const now = performance.now()
      if (action === 'moveLeft' || action === 'moveRight') {
        const direction = action === 'moveLeft' ? -1 : 1
        horizontal.current = {
          direction,
          startedAt: now,
          lastRepeatAt: now,
          arrZeroFired: false,
        }
        dispatch({ type: 'move', dx: direction })
      } else if (action === 'softDrop') {
        softDrop.current = { active: true, lastDropAt: now }
        dispatch({ type: 'softDrop' })
      } else if (action === 'hardDrop') {
        dispatch({ type: 'hardDrop' })
      } else if (action === 'rotateCCW') {
        dispatch({ type: 'rotate', direction: -1 })
      } else if (action === 'rotateCW') {
        dispatch({ type: 'rotate', direction: 1 })
      } else if (action === 'rotate180') {
        dispatch({ type: 'rotate', direction: 2 })
      } else if (action === 'hold') {
        dispatch({ type: 'hold' })
      } else if (action === 'pause') {
        dispatch({ type: 'pause' })
      }
    }

    const onKeyUp = (event: KeyboardEvent) => {
      const action = controlOrder.find((item) => settings.controls[item] === event.code)
      if (!action) return
      pressed.current[action] = false

      if (
        (action === 'moveLeft' && horizontal.current.direction === -1) ||
        (action === 'moveRight' && horizontal.current.direction === 1)
      ) {
        const otherPressed =
          action === 'moveLeft'
            ? pressed.current.moveRight
              ? 1
              : 0
            : pressed.current.moveLeft
              ? -1
              : 0
        horizontal.current = {
          direction: otherPressed,
          startedAt: performance.now() + settings.handling.dcd * FRAME_MS,
          lastRepeatAt: performance.now(),
          arrZeroFired: false,
        }
      }

      if (action === 'softDrop') softDrop.current.active = false
    }

    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('keyup', onKeyUp)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('keyup', onKeyUp)
    }
  }, [listeningFor, settings.controls, settings.handling.dcd, view])

  useEffect(() => {
    if (state.status !== 'gameOver' || savedGameOverScore.current === state.score) return
    savedGameOverScore.current = state.score
    const nextRankings = [
      { name: 'YOU', score: state.score },
      ...rankings.filter((rank) => rank.name !== 'YOU'),
    ]
      .sort((a, b) => b.score - a.score)
      .slice(0, 5)
    setRankings(nextRankings)
    localStorage.setItem('tetris-rankings', JSON.stringify(nextRankings))
  }, [rankings, state.score, state.status])

  const updateHandling = (key: keyof HandlingSettings, value: number) => {
    setSettings((current) => ({
      ...current,
      handling: {
        ...current.handling,
        [key]: Number.isFinite(value) ? Math.max(0, value) : current.handling[key],
      },
    }))
  }

  if (view === 'menu') {
    return (
      <main className="menu-shell">
        <section className="menu-panel">
          <p className="eyebrow">Web build</p>
          <h1>Tetris Ultimate</h1>
          <div className="menu-actions">
            <button
              type="button"
              onClick={() => {
                setView('game')
                dispatch({ type: 'start' })
              }}
            >
              Solo Play
            </button>
            <button type="button" disabled>
              Multiplayer
            </button>
            <button type="button" onClick={() => setView('settings')}>
              Settings
            </button>
          </div>
        </section>
      </main>
    )
  }

  if (view === 'settings') {
    return (
      <main className="settings-shell">
        <section className="settings-panel">
          <header className="settings-header">
            <div>
              <p className="eyebrow">Config</p>
              <h1>Settings</h1>
            </div>
            <button type="button" onClick={() => setView('menu')}>
              Back
            </button>
          </header>

          <section className="settings-section">
            <h2>Controls</h2>
            <div className="key-grid">
              {controlOrder.map((action) => (
                <button
                  className={listeningFor === action ? 'key-bind listening' : 'key-bind'}
                  key={action}
                  type="button"
                  onClick={() => setListeningFor(action)}
                >
                  <span>{controlLabels[action]}</span>
                  <strong>
                    {listeningFor === action ? 'Press key' : displayKey(settings.controls[action])}
                  </strong>
                </button>
              ))}
            </div>
          </section>

          <section className="settings-section">
            <h2>Handling</h2>
            <div className="handling-grid">
              {(['das', 'arr', 'dcd', 'sdf'] as const).map((key) => (
                <label key={key}>
                  <span>{key.toUpperCase()}</span>
                  <input
                    max={key === 'sdf' ? 60 : 30}
                    min="0"
                    step={key === 'sdf' ? 0.5 : 0.1}
                    type="number"
                    value={settings.handling[key]}
                    onChange={(event) => updateHandling(key, Number(event.target.value))}
                  />
                  <em>
                    {key === 'sdf'
                      ? `${settings.handling[key]}x`
                      : `${Math.round(settings.handling[key] * FRAME_MS)}ms`}
                  </em>
                </label>
              ))}
            </div>
            <button className="reset-button" type="button" onClick={() => setSettings(defaultSettings)}>
              Reset to TETR.IO-style defaults
            </button>
          </section>
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <section className="game-layout" aria-label="Tetris game">
        <aside className="left-panel">
          <header className="brand-block">
            <span>Tetris Ultimate</span>
            <strong>{state.status === 'playing' ? 'LIVE' : state.status}</strong>
          </header>

          <section className="panel-card">
            <h2>Hold</h2>
            <MiniPiece type={state.hold} />
          </section>

          <section className="stats-grid">
            <div>
              <span>Score</span>
              <strong>{state.score.toLocaleString()}</strong>
            </div>
            <div>
              <span>Lines</span>
              <strong>{state.lines}</strong>
            </div>
            <div>
              <span>Level</span>
              <strong>{state.level}</strong>
            </div>
          </section>

          <section className="panel-card event-card">
            <h2>Event</h2>
            <strong>{state.lastEvent}</strong>
          </section>

          <div className="button-row">
            <button type="button" onClick={() => dispatch({ type: 'start' })}>
              Restart
            </button>
            <button type="button" onClick={() => setView('menu')}>
              Menu
            </button>
          </div>
        </aside>

        <section className="board-wrap">
          <div className="board-frame">
            <div className="board" aria-label="Playable Tetris board">
              {renderBoard.flatMap((row, y) =>
                row.map((cell, x) => {
                  const className = [
                    'cell',
                    cell.type,
                    cell.active ? 'active' : '',
                    cell.ghost ? 'ghost' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')
                  return <span className={className} key={`${x}-${y}`} />
                }),
              )}
            </div>

            {state.status !== 'playing' && (
              <div className="board-overlay">
                <strong>
                  {state.status === 'paused'
                    ? 'Paused'
                    : state.status === 'gameOver'
                      ? 'Game Over'
                      : 'Ready'}
                </strong>
                <span>Press {displayKey(settings.controls.start)}</span>
              </div>
            )}
          </div>
        </section>

        <aside className="right-panel">
          <section className="panel-card next-card">
            <h2>Next</h2>
            <div className="next-list">
              {nextPieces.map((piece, index) => (
                <MiniPiece type={piece} key={`${piece}-${index}`} />
              ))}
            </div>
          </section>

          <section className="panel-card">
            <h2>Ranking</h2>
            <ol className="ranking-list">
              {rankings.map((player) => (
                <li key={`${player.name}-${player.score}`}>
                  <span>{player.name}</span>
                  <strong>{player.score.toLocaleString()}</strong>
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </section>
    </main>
  )
}

export default App
