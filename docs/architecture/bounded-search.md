> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design:
> `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`.

# Bounded first-game Search

Status: implemented, August 2026.

## Rules and boundary

The authoritative rule is Combined Rulebook (CR) p. 20. A Search chooses the
world deck or the discard pile in the pawn's region, spends the Visions Drawn
track cost for the world deck or 2 Supply for the regional discard, and draws
one card at a time up to three. A Vision drawn from the world deck stops the
draw and advances the track. The player keeps exactly one drawn card, orders
the other cards onto the top of the next-region discard (Cradle to Provinces,
Provinces to Hinterland, Hinterland to Cradle), then plays or discards the kept
card. CR p. 42 gives the turn player control over simultaneous ordering, which
is why discard order is an explicit choice.

CR p. 20 also establishes these placement rules:

- a denizen played to the current site gives one favor from its suit bank;
- a full site rejects a denizen, except that a matching-suit Homeland may
  discard a site card first;
- a faceup site-only denizen cannot be an adviser, and an adviser-only denizen
  cannot be played to a site;
- facedown advisers have no suit, power, or play restriction;
- the adviser limit is three, so exceeding it first discards a non-locked
  adviser; locked cards cannot be discarded (CR pp. 20, 28);
- an Exile may reveal a Vision to the Vision space, replacing an existing
  revealed Vision, or hold it facedown as an adviser (CR p. 20).

The implementation treats the head of `worldDeck` as its top. Regional discard
vectors retain the established setup representation: the last element is the
top, so draws read from the end and discards append in the chosen order.
Regional discard tops are faceup public information, so projections include
their Denizen/Vision kind for pile display. Card backs and therefore card types
are public for every pile, so the World Deck also projects its top-card kind;
card fronts and identities remain hidden.

The implementation protects searched-card fronts as decision-owner-only.
CR p. 42 describes facedown cards
outside a player area as private but otherwise makes in-play information public;
the stronger digital redaction is a conservative anti-leak policy, not a claim
that tabletop players must conceal a Search draw. Only the actor projection
contains `drawnCards`; public, spectator, and other-player projections contain
only `search-waiting` and the active player identity.

## Authoritative procedure

`BeginSearch(actor, source)` is transient and contains no card identity or deck
order. The application asks the injected server-side `SearchDrawPort` for a
prepared outcome and submits that outcome to the rules runtime. The runtime
independently compares it with the authoritative source order before accepting:

1. `SearchStarted(actor, decision, source, origin, supplySpent, drawn)` spends
   Supply, removes the exact cards, advances Visions Drawn when applicable, and
   creates `PendingProcedure.Search`.
2. The transport submits generic `ResolveCardDecision(actor, decision,
   SearchResolution(...))`, which adapts to the internal typed Search command
   and verifies
   the exact drawn-card permutation, catalog identity, capacity, restrictions,
   replacements, and lock rules. Search then delegates placement to the shared
   typed `CardPlay` procedure used by facedown-adviser play. That procedure owns
   orientation, adviser/site destinations, replacement disposal, suit-bank
   favor movement, and next-region discard ordering. Search retains its
   draw/keep/discard decision and continuation, clears the pending procedure,
   and returns to Act action selection.

Source selection is the commit boundary. Before the begin intent is submitted,
the UI may abandon its local selection without state change. Once
`SearchStarted` is durable, Supply and draw are committed and there is no
cancellation; the decision must complete. Completion is one atomic event. A
reload or reconnect reconstructs the pending decision and reissues the same
player-scoped controls without drawing again.

The current event envelope uses explicit `gameplay.search-started` and
`gameplay.search-completed` discriminators in the unified setup/gameplay
stream. Completion records exact favor and replacement-discard effects authored
by the server. Replay recalculates those facts and validates the source top,
cost, draw stop/order, decision ID, card permutation, and placement invariants;
randomness never executes during replay.

## Rule runtime and interaction pattern

`CardRestrictions` remains the typed source for site-only, adviser-only, and
locked-adviser-only placement. Search does not interpret `rulesText` and does
not add a JSON rules DSL. The R1 `RuleQuery`/`RuleOutcome` registry remains the
extension point for activated component modifiers. This bounded profile has no
supported Search modifier, so it validates unaltered Foundations, exile-only
roles, and no active legacy/relic Search modifier before using the core typed
procedure directly. A future supported modifier may contribute typed cost,
block, or `RuleDecisionBoundary` outcomes; it must not silently activate merely
because a handler string exists.

The Search-derived facedown-adviser action previews and revalidates the same
precise Search modifier window before exposing owner-scoped faceup adviser/site
targets. Final placement is revalidated by `CardPlay` against newly loaded
state. Reviewed reached When Played handlers still produce source-scoped durable
fallback diagnostics; no component effect language was added.

HRF's useful pattern is its explicit `Ask` continuation and server-recorded
`Shuffle`/`Random` continuation vocabulary in `vendor/haunt-roll-fail/hrf/base.scala`.
The implementation adapts the interaction shape—a forced owner decision with
enumerated actions—but not HRF's action-authoritative timeline. Oath Digital
keeps domain events authoritative and projects legal controls from replayed
state. `SearchRules.legalPlacements` enumerates candidates by running the same
completion validator used by command handling and replay.

The authorized actor receives one `pendingCardDecision` envelope. The client
first arranges exactly one card in Keep and orders the remaining Discard zone,
then locally advances to resolution. Only final confirmation submits an atomic
resolution. Dragging is supplemented by Move to Keep, Move to Discard, Move
Left, and Move Right controls. Required adviser or site replacement is always
explicit. Public and other-player projections omit the entire envelope.

## In scope and deferred

In scope: exile-only first game, fixed unaltered Foundations, core world and
current-region sources, track/2-Supply costs, three-card/early-Vision draw,
private pending decision, ordered next-region discards, discard/site/adviser
placement, adviser and matching-Homeland replacement, locked-card enforcement,
site favor gain, HTTP intents, optimistic concurrency, replay, and Scala.js
reconnect-safe controls.

Deferred: all individual denizen/relic/site Search powers and modifiers,
visions' victory effects and Conspiracy action, Imperial Vision behavior,
legacy changes, altered Foundations, campaign setup, generic rules-text
execution, other action families, and any general hidden-information sharing or
event-feed product. Durable event APIs must remain privileged because Search
events necessarily contain the replay outcome; ordinary HTTP routes expose
only scoped snapshots and generic rejection messages.
