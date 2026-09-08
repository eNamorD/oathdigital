# Arcs-inspired Oath Digital panel UI

Date: 2026-09-08

Status: Layout A and interaction/reuse approach approved in conversation; consolidated specification awaiting final review.

## Objective

Replace Oath Digital's vertically stacked game page with a viewport-filling, responsive table inspired by Arcs. World Map, Players, Action Selection, and Game Log remain simultaneously present. Preserve existing game interactions while making overflow navigable within each panel.

## Approved layout

Use adaptive layout A from the visual companion. On wide windows, Players occupies the top band, World Map the large lower-left area, and Game Log and Action Selection share the right column. On compact windows, retain the top Players band and place Actions above Log to the right of the map. In portrait orientation, Players sits above World Map, with Actions and Log beside each other below it.

The map receives the largest individual share of the available viewport. The log remains a smaller reserved area. Determine layout from both available width and height; do not hard-code one screenshot's pixel dimensions. Keep panel headings visible and scroll content locally. The game page itself must not become a long scrolling document. Panel visibility means that each panel has a visible heading and usable content viewport, not that every item must fit without scrolling.

Use compact dark panel shells, narrow separators, restrained Oath colors, and existing player colors. This slice addresses layout and navigation, not replacement artwork. Existing Oath site and card presentation remains the content source.

## Panel contents and behavior

### World Map

Reuse the existing world renderer, site targets, cards, piles, shared information, and region structure. Initially fit map content into its panel. Provide zoom in, zoom out, and reset-to-fit controls. Allow pointer/touch panning with bounded offsets so content remains recoverable. Keep ordinary click/tap target selection distinct from dragging. Provide keyboard-accessible controls and scroll navigation; zoom and pan must not be the only way to reach actionable content.

Preserve map zoom and position during projection updates. Recompute fit when in fit mode and the viewport changes. When manually zoomed, retain the user's view as far as bounds permit. Reset navigation when switching games; clamp or reset when content dimensions make the previous position invalid.

### Players

Combine the current player summary and player-board renderers into one Players panel. Include each player's identity, role, pawn location, resources, advisers, relics, banners, and revealed vision when available in the projection. Preserve player and card target selection and hidden-information behavior. Show the active player distinctly. Allow local scrolling when player count or content exceeds available space.

### Action Selection

Reuse existing action controls, pending decisions, target selection, confirmations, and cancellation behavior. Render these in one scrollable panel with the current phase/turn context. Preserve selections during ordinary updates. When a new decision requires attention, make its prompt accessible without leaving the user at an unrelated old scroll position.

Show loading, connection failures, and action errors in a visible status area in or adjacent to this panel. Preserve existing command disabling and reconnect behavior. Keep controls and focus indicators readable rather than shrinking text indefinitely to fit the viewport.

### Game Log

Reserve a persistent, labeled panel with a concise empty-state message. Do not implement event projection or player-facing history in this slice. Raw authoritative events remain developer content and must not populate this placeholder.

### Development interface

Move existing session controls and raw event diagnostics into a dismissible floating panel opened by a compact development-tools control. It does not participate in game-panel sizing. Opening and closing it must leave panel geometry unchanged. Provide an explicit close control, keyboard access, and focus restoration. Preserve existing development access boundaries; adding production authentication or changing API exposure is outside this slice.

## Reuse and implementation boundaries

Oath's frontend is Scala.js with DOM renderers. Arcs' `GreyUI.CanvasPaneX` depends on its Gaming, resources, sprites, canvas, and element infrastructure. Its layout search uses custom collections and fitting utilities. Oath's currently vendored HRF files are reference sources and are not compiled into the frontend.

Adapt the useful upstream layout priorities, alternate arrangements, viewport sizing, and constrained pan/zoom behavior into a small Oath-owned panel shell. Retain existing Oath renderers and command callbacks. Use standard DOM/CSS layout for the three approved arrangements; the full HRF fitting search is not required for this finite set. Extract or port upstream navigation calculations where they fit the DOM implementation, preserving upstream MIT notices for copied or substantially adapted code. Do not introduce the complete HRF canvas/game framework solely for panel layout.

Keep shell layout and navigation state separate from authoritative game state. The server projection remains the source of game content and permissions. No game rules, commands, event formats, or backend persistence changes are required.

## Rendering lifecycle

`ServerModeUi.render` currently clears the entire mount before rebuilding content. Introduce a stable panel shell and explicit client-side view state so routine game updates do not discard map position, panel scroll offsets, open development state, or keyboard focus unnecessarily. Content may still be rebuilt within stable containers, provided navigation and focus are restored using stable identifiers.

Preserve scroll on ordinary updates; clamp when content shrinks. When a selected target or focused control disappears, move focus to an appropriate surviving panel or decision heading. Dispose of resize and pointer listeners when their owning shell is replaced.

## Verification and acceptance

- All four panels remain visible without page-level scrolling at representative wide desktop, laptop, compact landscape, and portrait sizes, including 1440x900, 1024x768, 768x1024, and 390x844 CSS pixels.
- Dense player boards and long action lists remain reachable through local scrolling. Headings remain visible, and keyboard focus is not clipped or stranded.
- Map fit, zoom, pan, reset, resize clamping, and click-versus-drag behavior work with existing targets.
- Ordinary projection refreshes preserve map navigation and panel scroll state. New decisions remain discoverable.
- Existing action/decision selections, confirmations, cancellation, and player/card/site targets still work.
- Game Log shows only its reserved empty state. Raw event payloads appear only in the developer interface.
- Toggling development tools does not resize the four game panels.
- Loading and disconnected/error states remain understandable and preserve existing command restrictions.
- Compile the frontend, run relevant existing frontend suites, and add focused tests for nontrivial navigation/state transitions introduced by this work. Perform browser verification of the viewport matrix and key interactions. Test layout behavior rather than duplicating CSS declarations in assertions.

## Out of scope

Player-facing game-log implementation; new game artwork; backend/protocol changes; manual panel docking or resizing; persistent user layout customization; new production access controls; wholesale adoption of HRF's rendering framework.

## Source references

- Oath: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala`, `WorldBoardRenderer.scala`, `ActionDecisionRenderer.scala`, `DevelopmentRenderer.scala`, and `frontend/styles.css`.
- Arcs: `/Users/roman/projects/haunt-roll-fail/haunt-roll-fail/arcs/ui.scala` for panel priorities, layout variants, and map bounds.
- HRF: `haunt-roll-fail/grey.scala` for navigation and resize lifecycle; `haunt-roll-fail/panes-again.scala` for fitting concepts; repository `LICENSE` for MIT attribution.
- Visual companion: workspace `.superpowers/brainstorm/26189-1788884218/content/panel-layout.html`.
