# Modifier Selection on the Walker

**Status:** designed and implemented 2026-10-09, in four slices. It is a
prerequisite of slices 3 and 4 of
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
  `ModifierOffers.paying` (`paying` stays as a private helper; plan-time
  ruling 16).
- `SelectedModifier`'s eligibility-window payment Transform. Its
  `selectionPayments` stays as the pick's payment (plan-time ruling 5).
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

## Plan-time rulings

Settled while planning slice 1:

1. **The decision is `{action}.modifiers`** (`search.modifiers`), one id per
   modifier window, so a facedown-adviser play shares Search's. A pick
   answers with the modifier's card. "Start" answers with the button
   `start`, and it is listed first, so a test's default answer starts the
   action with no modifier.
2. **Who is listed.** Every player-selected power that may be picked at the
   window and whose card the actor may use now. Each is judged in this
   order:
   - Its own `offerReason`, from the picks so far.
   - A dry run of its payment, priced, or unavailable with the shortfall.
   - Whether it is applicable, else "Cannot be used now".

   An `offerReason` warning marks an otherwise available option.
3. **When a pass asks.** The first pass asks only when some modifier can be
   picked, so a player with nothing to pick is asked nothing (ruling 1).
   After a pick, a pass asks while any modifier is still listed, available
   or not, so the reasons stay visible. Once every listed modifier is
   picked, the action starts by itself.
4. **"Start"'s reason** is the first refused option of the action's first
   decision, as the procedure prices it (Search: "Needs 2 Supply; you
   have 0"). A procedure that prices nothing says "Cannot start now".
5. **`selectionPayments` stays.** It is the pick's payment. Only the
   eligibility-window Transform goes, action by action as each moves
   (`SelectedModifier.paidAtPick`).
6. **The powers follow the answers.** `WalkerPowers` keeps the full catalog
   as its pool. `ProcedureWalker` adds the powers picked in the answers at
   every entry and after each answer. Within a command, picks are only ever
   added.
7. **A picked modifier applies at its own selection window,** however much
   it could still pay, so its option restrictions reach the remaining
   options.
8. **Picked cards are protected.** `CardSlots` reads the picked cards from
   the answers, and `ActiveModifier` refuses their discard, as it does for
   the modifiers a start selected.
9. **The Game Log.** The modifier decision posts no "Chose" line. The Search
   tag waits until the decision is settled: it was never asked, "Start" was
   answered, or a later decision was (a walk whose every modifier is picked
   starts by itself). The facedown-adviser tag moves from the start to the
   adviser's pick.
10. **Labels.** Search's button reads "Start Search", and the facedown-adviser
    play's reads "Play facedown adviser".

Settled while planning slice 4:

11. **Catacombs says why it cannot be picked.** At a site with no empty
    relic slot it reads "This site has no empty relic slot"; with an empty
    relic deck, "The relic deck is empty" (`offerReason`). Its eligibility
    Transform still places the relic, and no longer pays: the pick does.
12. **Recover's "Start" is never unavailable.** Recover prices no option,
    and a Recover that cannot roll (no Supply) is refused at the start, as
    before, so the decision is never asked about it. Ruling 4's generic
    "Cannot start now" would apply if a future modifier made it ask.
13. **The Recover tag waits for the dice.** It anchors at the step that
    opens Recover's dice pool, the first of the Recover itself.
14. **Old journals.** The journal stores events, not commands, so every old
    game still loads. A park's `modifiers` key is ignored. A game finished
    before slice 4, with modifiers chosen at its start, loses the "With …"
    part of that start line.
15. **The wire.** `StartWalker` has no `modifiers` key, and its decoder
    rejects one. A stale browser gets one rejection; a reload fixes it.
16. **What stays.** `ModifierOffers.paying` stays, private: the Act panel's
    start controls still dry-run payments (ruling 9). The Game Log's card
    lookup moved from `PreviewModifierDescriptions` to the log's own
    `PrintedCards`.

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
