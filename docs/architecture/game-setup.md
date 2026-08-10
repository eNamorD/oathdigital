# Bounded game setup

Status: implemented and replay-tested, reviewed August 2026.

This document distinguishes the historical v1 pawn-placement proof from the
complete exile-only introductory setup used by the current game stream.

## State and command boundary

`SetupState` has three immutable states: `NotStarted`, `InProgress`, and
`Completed`. Started state pins the production `CatalogRef`, preserves ordered
player/lineage participants, stores the ordered 2/3/3 eight-site layout, and
records pawn placements in participant order.

`BeginSetup` contains the exact ordered eight-site selection. That order is the
externally recorded result of any shuffle or draw. Command handling and replay
perform no random selection and receive no RNG. `PlacePawn` identifies the
participant and an in-play site. Several pawns may share a site.

Commands emit `SetupStarted`, `PawnPlaced`, and, immediately after the final
pawn, `SetupCompleted`. The typed continuation reports either the next
participant awaiting placement or completion. Replay applies only events and
reports the zero-based index of the first invalid event.

## Validated scope

The rules validate the pinned catalog, nonempty and unique players and
lineages, exactly eight distinct catalog sites, strict placement order, and
catalog-known/in-play pawn destinations. Tests load the production typed
catalog projection containing setup cards, Supply boards, and sites.

## Deliberate deferrals

The setup events use the versioned durable format described in
[`authoritative-events.md`](authoritative-events.md). Commands are not part of
that wire format.

The v1 bounded slice does not initialize player boards or Supply, advisers,
legacies, edifices, Foundations, the Chronicle, card decks, resources, roles,
or a full `OathGame`. It does not implement UI, database/network storage,
snapshots, or gameplay.

## Exile-only complete first-game endpoint

`FirstGameSetupRules` is a separate v2 state machine so the v1 event classes,
commands, replay behavior, and checked-in golden bytes remain unchanged. It
starts at `NoGame`, records a complete `FirstGameSetupPlan`, reuses the existing
typed `SetupCommand.PlacePawn`, records each starting adviser choice, and ends
at `Ready(ReadyGame)`. The endpoint contains a structurally valid
`OathGame`; its selected first player is active at `Phase.Wake`, meaning they
are ready to begin their first turn. Wake behavior is not executed here.

The plan records every external outcome that setup would randomize:

- seating, stable player/color/lineage identities, and first player;
- the ordered eight selected sites;
- the 60-denizen pool order and exact final World Deck order;
- all five fixed first-game Vision identities and their packet positions;
- the complete ordinary-relic order; and
- the matching ruined edifice chosen for each selected Homeland.

Replay applies these recorded values and never invokes randomness. The engine
validates 10 denizens per suit, the two-card regional discard seeds, three-card
player hands, the 10-denizen/2-Vision and 15-denizen/3-Vision packets, complete
ordinary-relic conservation, matching Homeland/edifice suits, placement order,
and adviser ownership.

The built aggregate includes site starting resources, capacity bandits,
facedown site relics, ruined Homeland edifices, regional discards, advisers,
the World and relic decks, starting Exile board wealth and full Supply, banners,
tracks, Oath of Supremacy, an empty Oathkeeper title, fixed favor banks, and
six normal Foundations with no alteration sources.

This inclusion is sourced to Combined Rulebook pp. 6-7 and the New Foundations
first-game clarification on p. 8. The implementation deliberately excludes
Legacy draws/choices/effects, Chancellor/Citizen/Imperial setup, campaign
restoration, generic Foundation interpretation, and Chronicle progression. It
also does not claim the contents of the
Dispossessed or the order of the 16 unselected Atlas sites; neither is needed
to make the selected world, player state, and active decks structurally valid
for this endpoint. The Grand Scepter is excluded from the ordinary relic
shuffle because it is an Imperial component.

## Gameplay handoff

The final setup event produces `Ready(ReadyGame)` with the selected first Exile
active in Wake. From that point `OathRules` owns Wake and Act behavior; setup
rules do not special-case later gameplay. See
[gameplay-modules.md](gameplay-modules.md),
[rule-resolution.md](rule-resolution.md), and
[bounded-search.md](bounded-search.md) for the implemented gameplay boundaries.
