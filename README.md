# GammonScope

A web-based backgammon match viewer and analysis tool. Import, store, browse and replay
backgammon match records in your browser. Built on a Spring Boot (H2) backend and a
React + Vite frontend, inspired by GNU Backgammon.

## Supported import formats

| Format | Extension | Notes |
|--------|-----------|-------|
| Jellyfish / GridGammon | `.mat` | Fully supported |
| GNU Backgammon | `.sgf` | Supported (`GM[6]` game trees) |
| GammonScope native | `.json` | Fully supported (see below) |

## JSON match format

The native `.json` format maps directly onto GammonScope's internal match model.
Move notation is **identical to `.mat`**: points are `1`–`24`, `25/X` enters a checker
from the bar, and `X/0` bears a checker off (e.g. `25/20` = bar in, `6/0` = bear off).
Multiple steps share one token (`13/7/2`) and repeats use `(n)` (e.g. `6/1(2)`).

### Field reference

Top level (match):

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `event` | string | no | Event name |
| `site` | string | no | Location |
| `round` | string | no | Round label |
| `date` | string | no | Match date |
| `matchLength` | number | no | Points to win; `0` = unlimited money game |
| `variation` | string | no | `Backgammon` (default), `Nackgammon`, `Hypergammon` |
| `result` | string | no | Free-text final result |
| `players` | object | no | `{ "white": "...", "black": "..." }` (white = player 0) |
| `games` | array | **yes** | List of games |

Game:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `gameNumber` | number | no | Defaults to its position in the array |
| `score` | `[number, number]` | no | Score `[white, black]` before the game |
| `crawford` | boolean | no | Crawford game flag |
| `winner` | string/number | no | `white`/`black` (or `0`/`1`); otherwise derived from scores |
| `points` | number | no | Points won; otherwise derived from scores |
| `setup` | object | no | Non-standard starting position override |
| `moves` | array | no | Ordered list of actions |

Move / action:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `player` | string/number | **yes** | `white`/`black` (or `0`/`1`) |
| `type` | string | no | `normal` (default), `double`, `take`, `drop`, `resign` |
| `dice` | `[number, number]` | for `normal` | The roll, e.g. `[3, 1]` |
| `play` | string | for `normal` | `.mat`-style notation, e.g. `"8/5 6/5"`, `"25/20"`, `"6/0"` |
| `comment` | string | no | Annotation for the move |
| `cubeValue` | number | for `double` | New cube face value (`2`, `4`, `8`, …) |
| `cubeOwner` | number | no | `-1` center, `0`, or `1` |
| `resign` | number | for `resign` | Points resigned (1 / 2 / 3) |

### Sample

```json
{
  "event": "World Championship 2026",
  "site": "Monte Carlo",
  "round": "Final",
  "date": "2026-07-20",
  "matchLength": 7,
  "variation": "Backgammon",
  "players": { "white": "Player A", "black": "Player B" },
  "games": [
    {
      "gameNumber": 1,
      "score": [0, 0],
      "crawford": false,
      "moves": [
        {
          "player": "white",
          "dice": [3, 1],
          "play": "8/5 6/5",
          "comment": "经典开局，占领5号要点。"
        },
        {
          "player": "black",
          "dice": [6, 4],
          "play": "24/18 13/9"
        },
        {
          "player": "white",
          "type": "double",
          "cubeValue": 2
        },
        {
          "player": "black",
          "type": "take"
        },
        {
          "player": "white",
          "dice": [5, 2],
          "play": "25/20 13/11"
        }
      ],
      "winner": "white",
      "points": 2
    }
  ]
}
```
