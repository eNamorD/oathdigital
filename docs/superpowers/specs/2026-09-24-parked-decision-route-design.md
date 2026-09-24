# Parked Decision Route

> Status: design approved 2026-09-24. Not yet implemented. This is a
behavior-preserving architecture slice. Sequence it after the frontend tasks
of §4 (`subjectCards`) and §7 (generalised roll payload) in
[the vision identity and modifier selection design](2026-09-24-vision-identity-and-modifier-selection-design.md)
have merged; both edit `WalkerPanelSupport` and `ActionDecisionRenderer` in
regions this slice moves.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**,
**form** and **surface**.

## Purpose

The mapping from a parked decision to the surface that shows it has no owning
module. `ActionDecisionRenderer.actionsPanel` calls six panel renderers
unconditionally (`ActionDecisionRenderer.scala:277-286`); each re-opens
`value.walkerDecision`, re-tests `presentation.showGameplayControls` and
re-matches `query.form` as a string. The seventh surface, Setup's pawn
placement, is dispatched from a different pane (`WorldBoardRenderer.world`)
and excluded from the generic choose-one panel by a negative prefix predicate
in `WalkerPanelSupport.chooseOneStep`. The form string is matched in nine
places across six files with no exhaustiveness check, and four of the ten
heading and confirm-label sites bypass the fallback owner
(`WalkerPanelSupport.decisionHeading`, `partitionConfirmLabel`).

Adding a form today means editing four or more files. Understanding which
surface a decision reaches means reading all seven.

## Ownership and interface

A new frontend module, `ParkedDecision`, owns every render-side read of
`walkerDecision` and `walkerWaiting`. It has two entry points.

`route(projection, presentation): Routed` is pure. It parses the projection
once and returns

```scala
final case class Routed(surface: Option[Surface], notice: Option[String])
```

where `notice` is today's `waitingNotice` and `Surface` is a sealed trait
whose cases each carry the whole `WalkerDecisionState`:

- `Recover(decision, step)` with `step` one of `Roll(pool)`, `Choice(query)`,
  `Relic(query)` — exactly today's `RecoverWalkerStep`, selected when
  `decision.action == "recover"`.
- `ChooseOne(decision, query)` — a `decide` park with a choose-one query that
  is neither Recover's nor pawn placement's.
- `Partition(decision, query)`, `Distribute(decision, query)`.
- `Selection(decision, query, form)` where `form` is `ChooseMany` or
  `ChooseAmount`.
- `Negotiate(deal, editor)` where `editor` is `Some((decisionId, editing))`
  for the viewer who may act and `None` for an observer. This one case covers
  the controller's summary-plus-editor, an observer's summary of the parked
  deal, and the summary of `walkerWaiting.deal`.
- `PawnPlacement(decision, query)` — the board surface, selected by the
  `setup.pawn-placement.` decision-id prefix. The prefix becomes a private
  constant of the route; nothing else in the frontend spells it.

A projection with no parked decision, an unknown form, or a Roll park outside
Recover routes to `surface = None`, which is what renders nothing today. The
form string is parsed once into a frontend-local sealed `DecisionForm` with an
`Unknown(raw)` case; the wire field `form: String` in
`DecisionQueryProjection` does not change.

`render(routed, canControl, panel, ui)` is the adapter table: one exhaustive
match over `Surface`, appending the surface then the notice in today's order.
The board pane does not use it; `WorldBoardRenderer.world` gains an
`Option[Surface.PawnPlacement]` parameter and keeps its own site rendering.

`recoverWalkerStep`, `chooseOneStep`, `pawnPlacementStep`, `chooseOneQuery`
and `waitingNotice` move from `WalkerPanelSupport` into the route; they are
the route. `WalkerPanelSupport` keeps only panels.

Each panel renderer's interface narrows to the matched value: the decision and
query the route has already proven of the right form, plus `canControl`,
`panel` and `ui`. No panel re-opens the projection or tests the form. Panels
that pair the decision with a draft (`partition`, `distribute`, `selection`)
keep doing so through `ui.currentWalker*` by `decisionId`; the drafts stay on
the session, and `ServerUiView` is not touched.

Routing runs once per render in `ServerModeUi.render`. The `Routed` value is
passed to `ActionDecisionRenderer.actionsPanel` and its `PawnPlacement` case
to `WorldBoardRenderer.world`.

## Scope and preservation

Rendered DOM is byte-identical before and after, including element order,
classes, labels and handlers. The one source change that touches copy is that
`DistributePanelRenderer` and `WalkerSelectionPanels` call `decisionHeading`
and `partitionConfirmLabel` instead of inlining the same fallback strings; the
output is unchanged.

Out of scope, each recorded as its own follow-up:

- The DOM-scraped decision cache key in `ServerModeUi.scala:84-92`. It belongs
  to the session-draft slice, which owns the session.
- Narrowing `ServerUiView` or moving drafts into the route. Same slice.
- The two independent click handlers a site can receive in
  `WorldBoardRenderer.world`. Separate fix.
- A typed form or a server-declared surface hint on the wire. Separate
  protocol slice; this design makes it a one-file change on the frontend.
- Any change to Recover's surface. §7 of the vision design makes roll feedback
  an attribute of any parked decision; because every `Surface` case carries
  the decision, that later change edits one adapter and no route.

## Verification

Bracket the refactor with the existing frontend suites that reach a panel:
`RecoverPanelSuite`, `WalkerChoicePanelRenderSuite`, `CardChoicePanelSuite`,
`PartitionPanelRenderSuite`, `DistributePanelRenderSuite`,
`NegotiationDealPanelSuite`, `WalkerSelectionPanelsSuite`, `PlayerBoardSuite`,
and the jsdom tests in `ServerModeUiSuite`. Run them before and after every
commit.

Add `ParkedDecisionSuite`, testing `route` as a pure function with no DOM: one
case per form and per surface, the Recover branches, pawn placement to the
board surface, an observer's negotiation summary alongside a waiting notice,
an unknown form, a Roll park outside Recover, and a viewer without gameplay
controls. Rewrite the panel suites' setup to construct the matched value
directly instead of a full projection.

Run the full Scala.js suite and `python3 scripts/check-architecture.py`.
Inspect the diff for unchanged decision strings, unchanged wire types, and no
remaining `form ==` or `setup.pawn-placement.` literal outside the route.

## Delivery

Own branch, three commits, each green:

1. `ParkedDecision` with `route`, `ParkedDecisionSuite` and `CONTEXT.md`;
   panels unchanged and still called from `ActionDecisionRenderer`.
2. Narrow each panel's interface to the matched value; move the exhaustive
   match into `ParkedDecision.render`; rewrite panel suite setup.
3. Route once in `ServerModeUi.render`; thread `Routed` and the pawn-placement
   surface into both panes; delete the prefix constant and the step helpers
   from `WalkerPanelSupport`.
