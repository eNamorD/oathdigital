# Arcs-inspired Panel UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver the approved four-panel game table with stable navigation and optional development controls.

**Architecture:** A persistent DOM shell owns panel placement, scroll/focus restoration, and development visibility. Existing renderers supply game content. A separate map viewport controller owns fit/zoom/drag behavior using a small pure geometry model.

**Tech Stack:** Scala 2.13.16, Scala.js, scalajs-dom 2.8.0, CSS Grid, MUnit.

**Spec:** [Approved design](../specs/2026-09-08-arcs-panel-ui-design.md)

## Global Constraints

- World Map, Players, Action Selection, and Game Log remain simultaneously present.
- No game rules, commands, event formats, or backend persistence changes are required.
- Raw authoritative events remain developer content and must not populate the log placeholder.
- Preserve upstream MIT notices for copied or substantially adapted code.
- Verify 1440x900, 1024x768, 768x1024, and 390x844 CSS pixel viewports.

## File structure

- `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala`: persistent shell, content replacement, focus and scroll restoration, developer panel.
- `frontend/src/main/scala/oathdigital/frontend/MapViewport.scala`: DOM map sizing, zoom, drag and resize lifecycle.
- `frontend/src/main/scala/oathdigital/frontend/MapViewState.scala`: pure fit and bounded scroll geometry.
- `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala`: route existing renderer output into shell.
- `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala`: one combined player display with existing target callbacks.
- `frontend/styles.css`: viewport layout and panel styles appended after existing content CSS, shared by development and packaged frontend assets.
- `frontend/src/test/scala/oathdigital/frontend/MapViewStateSuite.scala`: geometry and update regression tests.

### Task 1: Navigation model

**Interfaces:** Produce `MapViewState(scale: Double, fit: Boolean, left: Double, top: Double)` and `MapBounds(viewWidth: Double, viewHeight: Double, contentWidth: Double, contentHeight: Double)`. Methods `resize(bounds)`, `zoomBy(factor, bounds)`, and `panTo(left, top, bounds)` return updated state.

- [x] Run baseline `./sbtw frontend/test` with bundled Node on PATH.
- [x] Add meaningful regression tests before implementation:

```scala
val bounds = MapBounds(500, 300, 1000, 600)
assertEquals(MapViewState().resize(bounds).scale, 0.5)
val manual = MapViewState().resize(bounds).zoomBy(2, bounds)
assertEquals(manual.scale, 1.0)
assertEquals(manual.left, 250.0)
assertEquals(manual.top, 150.0)
assertEquals(manual.panTo(9999, -10, bounds).left, 500.0)
assertEquals(manual.panTo(9999, -10, bounds).top, 0.0)
assertEquals(manual.resize(bounds.copy(viewWidth = 1200)).left, 0.0)
```

- [x] Run `./sbtw 'frontend/testOnly *MapViewStateSuite'`; confirm missing-model failure.
- [x] Implement fit as the smaller viewport/content ratio (capped at 1), zoom around the viewport center, clamp scroll to nonnegative scaled-content overflow, and retain manual scale during resize. Bound zoom between fit scale and 3.
- [x] Run focused tests and confirm fit mode re-fits while manual mode stays fixed.

### Task 2: Stable shell and responsive placement

**Interfaces:** Produce `GameTableShell(mount: dom.Element)`, with `update(gameId: String, decisionKey: String, players: dom.Element, world: dom.Element, actions: dom.Element, development: dom.Element): Unit` and `dispose(): Unit`.

- [x] Create four persistent semantic sections with fixed headings and scrollable content regions:

```scala
val section = element("section", "game-pane pane-players")
section.setAttribute("aria-labelledby", "players-heading")
val heading = text("h2", "pane-heading", "Players")
heading.id = "players-heading"
val content = element("div", "pane-content")
content.setAttribute("tabindex", "0")
section.appendChild(heading)
section.appendChild(content)
```

- [x] Replace children within containers, capturing/restoring scroll and semantic focus identity. Reset action scrolling for changed decision keys; restore the same focused action by stable target identity/label where possible, otherwise focus its panel heading.
- [x] Add compact developer toggle and floating nonmodal dialog with close/Escape and focus restoration. Put existing dev controls/raw history inside it.
- [x] Add CSS Grid variants using both aspect ratio and width. Wide areas: `players players / world log / world actions`; compact: `players players / world actions / world log`; portrait: `players players / world world / actions log`.
- [x] Give all tracks and content regions `min-width:0; min-height:0`, use `100dvh`, suppress root overflow, retain independent content scrolling and visible keyboard focus.
- [x] Update `ServerModeUi.render` to construct renderer contents and call shell update. Derive decision key from selected player, phase, current action/decision prompt, and pending decision identifiers; avoid sequence-only resets. Put connection status/retry/errors in Actions. Preserve backend gating.
- [x] Consolidate existing player summary and board contents per player, preserving target handlers and board fields. Use active-player styling and local player scrolling.
- [x] Run `./sbtw frontend/compile frontend/test`.

### Task 3: Map viewport integration

**Interfaces:** Produce `MapViewport(viewport: dom.html.Div, content: dom.html.Div, onScale: Double => Unit)` with `refresh(reset: Boolean)`, `zoomBy(factor: Double)`, `reset()`, and `dispose()`.

- [x] Integrate `MapViewState` using a scaled DOM content wrapper with a correctly sized scroll surface. Keep natural Oath region/card content and callbacks.
- [x] Use ResizeObserver to recompute fit/clamp on panel resize. Restore scroll after content replacement. Add zoom in/out/reset controls in the fixed map header.
- [x] Implement pointer drag with a movement threshold, pointer capture only after drag begins, and suppression of the resulting click. Ignore form controls. Permit native touch scrolling and keyboard arrow scrolling; support keyboard zoom/reset on the viewport itself.
- [x] Disconnect resize observer and remove owned listeners on dispose. Reset only when switching games or explicitly fitting.
- [x] Verify `./sbtw frontend/test frontend/fastLinkJS`.

### Task 4: Browser acceptance and handoff

- [x] Start a separate loopback server using a fresh task-specific test database and available port. Do not reuse or restart another task's server.
- [x] At each specified viewport, check all headings and panel bounds are visible and root scroll dimensions match the viewport. Inspect screenshots for readable controls and local overflow.
- [x] Exercise developer open/close/Escape and compare panel rectangles. Confirm raw payloads are absent from player-facing log.
- [x] Exercise map zoom/reset, drag versus target click, resizing, and keyboard access. Complete a representative pawn/adviser decision with the test game.
- [x] Verify map and panel position through a projection update and raw-history refresh. Verify new decisions become visible and surviving controls retain focus.
- [x] Run frontend tests, optimized frontend build, markdown link check, and `git diff --check`. Correct issues revealed by these checks and rerun only affected verification.
- [x] Commit scoped files and report branch/worktree, checks, and remaining limitations. Keep the branch available for review; no unsolicited merge or push.
