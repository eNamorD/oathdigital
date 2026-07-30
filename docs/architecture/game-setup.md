# Bounded game setup

Status: implemented vertical slice, July 2026.

This slice proves typed commands, domain events, optimistic event persistence,
and deterministic replay without constructing a complete `OathGame`.

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

This slice does not initialize player boards or Supply, advisers, legacies,
edifices, Foundations, the Chronicle, card decks, resources, roles, or a full
`OathGame`. It does not implement UI, database/network storage, snapshots, or
gameplay. Those mechanics require their own source-verified rules slices.
