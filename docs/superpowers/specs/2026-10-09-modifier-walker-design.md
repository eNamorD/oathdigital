# Modifier Selection on the Walker

**Status:** designed 2026-10-09. It is a prerequisite of slices 3 and 4 of
[Catalog batch 6](2026-10-09-catalog-batch-6-design.md).

**Amends** ruling 1 of the
[search source design](2026-10-07-search-source-walker-design.md#rulings),
ruling 1 of the
[travel destination design](2026-10-07-travel-destination-walker-design.md#rulings)
and ruling 5 of the [undo design](2026-10-06-undo-design.md), in the last
slice.

## Goal

A major action's modifiers are chosen inside the walk, as its first
decision, instead of by the client before the start. Then the tools that
already shape a walker decision shape the modifier choice too:

- An `OptionRestriction` shows a modifier unavailable with a reason.
- A `Warned` option shows a modifier with a warning.
- A dry run prices each modifier, and says why one cannot be paid.

Catalog batch 6 needs this. Spinning Bee excludes every other Search
modifier, Mushrooms and Painted Trumpet exclude each other, and Wine of
Welcome is warned while Mushrooms is selected. The client cannot express
any of these: it fetches the candidates once and toggles them locally.

## Background

- **The start.** The client's modifier flow (`ModifierFlow`,
  `ModifierSelectionState`, `ModifierFlowDraft`) fetches the candidates from
  the preview endpoint (`MajorActionPreviewRequest` and
  `MajorActionPreviewResponse`, `PreviewModifier`), lets the player toggle
  and order them, and sends `StartWalker(action, modifiers, startArgs)`.
  `OathRulesWalker.validateModifiers` checks each against
  `ModifierOffers.offerable`, and `requirePayable` dry-runs their payments
  together through `ModifierOffers.paying`.
- **The payment.** `SelectedModifier` pays its cost through a Transform at
  the action's `*ActionEligibility` window. The same payment is
  `selectionPayments`, used only for the dry runs. For Search and Travel,
  that window sits after the source or destination pick.
- **The record.** The selection is recorded only in
  `WalkerParked.modifiers`, repeated on every park, and restored into
  `CurrentGameState.walkerModifiers`. A resume rebuilds `WalkerPowers` from
  it; `ActiveModifier` and `CardSlots.judged` read it.
- **Order.** The engine ignores the order of the modifiers. Only the Game
  Log's start line and the replay equality check read it, so the panel's
  Earlier and Later change nothing.
- **The modifiers.** There are 33 `PlayerSelected` powers: Search 8, Trade
  10, Muster 7, Travel 6 and Recover 2. Each has a card. Forge, Challenge and
  Campaign register a modifier window, but no power selects at it.
- **The pattern to follow.** Battle plans are already chosen inside the walk:
  a `Repeat` of `CampaignPlanChoice` passes, an `OfferHost`. Each pass offers
  a board ChooseOne of the unchosen plans plus "Finish", prices each from a
  dry run (`PlanPrice.priced`) and applies the chosen plan at once.

## Rulings

1. **Modifiers are the walk's first decision.** A procedure with a modifier
   window starts by parking on the modifier decision. When no modifier is
   offerable, nothing is asked and the walk goes on to its first real
   decision. `StartWalker` carries no modifiers.
2. **One modifier at a time.** The decision is a `Repeat` of passes. Each
   pass is a board ChooseOne of the modifiers not yet picked, plus a panel
   button "Start Search" ("Start Travel", and so on), the `Repeat`'s
   Finish. The heading is "Choose modifiers, or start".
3. **Pay at pick.** The picked modifier's cost is paid at once, as the
   decision's own child operation, and the next pass sees what it paid.
   Each option is priced from a dry run of its payment (`Priced`), or shown
   unavailable with the shortfall.
4. **The picks are the selection.** The selected modifiers are read from the
   answered picks, a pure function of the walk. A picked modifier is active
   from the next pass on, at the modifier window too.
5. **Modifiers judge and restrict each other.** An unpicked modifier judges
   its own option against the picks so far: unavailable with a reason, or
   warned. A picked modifier's `OptionRestriction`s and Transforms at the
   modifier window act on the remaining options. Each rule lives on the card
   that states it.
6. **Start says why it cannot.** "Start" is shown unavailable, with the dry
   run's reason, when the rest of the walk cannot complete under the picks,
   for example "Not enough Supply".
7. **Order is gone.** The pick order is kept only for the Game Log, whose
   start line lists the modifiers in that order, as it does today.
8. **Undo replaces Back and Cancel.** The start and each pick are commands,
   so Undo takes a pick back, and takes back the start before any pick.
9. **Start buttons keep their heuristic.** The Act panel's buttons still ask
   `ModifierOffers.selections` (no modifier, each alone, all together)
   whether a start is possible. The decision itself is exact.
10. **No compatibility for parked saves.** A game saved while parked mid-walk
    with modifiers selected fails to resume after the change.
11. **Empty windows stay.** Forge, Challenge and Campaign keep their
    modifier windows, so a future power needs no engine change. With
    nothing offerable, no decision is asked.

## Design

### The modifier decision

A new operation, `ModifierChoice(procedure, actor)`, sits at the root of
each procedure's tree, before its first node, inside the procedure's
`*ModifierSelection` window. It is the modifier counterpart of
`CampaignPlanChoice`, expanded at every fold from state and the answers:

- **Offerable modifiers** are those `ModifierOffers.offerable` returns
  today: `PlayerSelected`, applicable at the window, and payable alone.
  Each becomes its card's option (an adviser, a site card, a relic or an
  edifice). The board already draws these.
- **Its own judgment.** `SelectedModifier` gains
  `offerReason(ctx: PowerCtx): Option[OfferReason]`, where `OfferReason` is
  `Unavailable(reason)` or `Warned(reason)`. `ctx.answered` holds the picks
  so far. By default it says nothing.
- **Pricing.** A dry run of the option's payment against the live state
  prices it, or shows it unavailable with the shortfall, as `PlanPrice`
  does.
- **The picked option** applies its payment, a `PayCost` onto its card
  (`Costs.onCard`), and the pass ends. The `Repeat` ends on "Start".
- **"Start"** is offered when a walker preview of the rest of the tree,
  under the picks, finds a playable path (`WalkerSimulation.preview`, as
  `SearchProcedure.startable` does). Otherwise it is shown unavailable with
  the preview's first violation.

### Active powers

`WalkerPowers` for a walk is the persistent powers plus the modifiers
picked in its answers. The walker recomputes it after each pick within a
command, and on every resume from the answers, in place of
`walkerModifiers`. A selected power's fold must still not change across
resumes (`SelectedModifier`'s rule), which now covers the modifier window.

`ActiveModifier` (`OperationRestrictions`) and `CardSlots.judged` read the
picks through the same helper.

### What goes away

By the last slice:

- `StartWalker.modifiers`, `StartPayload.modifiers` and the dead
  `orderedModifiers` channel (`ModifierInvocation`, `WithModifiers`).
- `OathRulesWalker.validateModifiers` and `requirePayable`, and
  `ModifierOffers.paying`.
- `SelectedModifier`'s eligibility-window payment Transform and
  `selectionPayments`.
- `WalkerParked.modifiers` and `CurrentGameState.walkerModifiers`.
- The preview endpoint and its routes (`GameRoutes`, `TrustedGameGateway`,
  `AuthenticatedGameRoutes`, `MajorActionPreviewGuard`) and
  `GameApplicationService.preview`, with the legacy `PowerRuntime` branch
  it alone serves. `PreviewModifierDescriptions` stays only if the Game Log
  still uses it.
- The client's modifier flow, its panel in `ActionDecisionRenderer`, its
  wiring in `TableControls`, `SessionDrafts` and `ServerModeUi`, and its
  suites. An Act panel button sends its start directly.

### The Game Log

The start line keeps its wording and lists the modifiers in pick order,
read from the answers. A walk whose modifier decision was not asked has no
modifiers, as today.

## Slices

1. **The core, then Search.** `ModifierChoice`, active powers from the
   picks, pay at pick, `offerReason` and the "Start" preview. Search and
   PlayFacedownAdviser, which shares the Search modifier window, move onto
   it.
2. **Travel.**
3. **Muster and Trade.**
4. **Recover, the empty windows and the cleanup.** Recover moves; Forge,
   Challenge and Campaign start through `ModifierChoice` with nothing to
   offer. Then everything under "What goes away" is deleted, and the three
   amended rulings are rewritten in their specs.

Until slice 4, a procedure that has moved refuses a non-empty
`StartWalker.modifiers`, and the client sends its start directly, without
the modifier flow. A procedure not yet moved works as today.

## Testing

- Each slice moves its procedure's modifier suites onto the walker
  decision: a pick, its price, a shortfall, "Start" unavailable and why,
  and the Game Log's start line.
- Slice 1 adds a two-modifier exclusion through `offerReason` and an
  `OptionRestriction` from a picked modifier, using test powers, so that
  batch 6 can rely on both.
- Undo of a pick, and of the start.
- The verification gate is the roadmap's standing rule.
