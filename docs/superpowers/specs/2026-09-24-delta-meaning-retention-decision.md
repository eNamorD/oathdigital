# Delta Meaning Retention: A Write-Only Journal Field Kept on Purpose

> Status: superseded 2026-10-18. The revisit condition this record set has
> been met and the decision reversed: `DeltaMeaning` is removed. Read
> "Closure" at the foot of this file before acting on anything above it. The
> rest of the document is kept as the record of why retention was right in
> September, not as current guidance.
>
> Original status: decision recorded 2026-09-24 from an architecture review.
> It records why `DeltaMeaning` is not deleted, so a later review does not
> re-propose it. No implementation is authorized by this document alone.

## Decision

`DeltaMeaning` (`gameplay/walker/WalkerEvents.scala`) stays, with its four
cases, its derivation in `ProcedureWalker.deltaMeaning`, and its encoding in
`serialization/WalkerEventCodec`. It is excluded from the write-only deletion
sweep that removes `OperationShadow` and `OathContinue`.

It is genuinely write-only today. No production code reads the `meaning` field.
`WalkerReplay` matches its carrier as a type only, to tell a delta step from a
roll step and a choice step, and works from the recorded operations alone.

## Why

The field is the one item on the deletion list with a named future reader. The
roadmap's player-facing action history phase specifies a human-readable log
derived from authoritative event batches "through a typed semantic formatter".
`DeltaMeaning` is a typed semantic summary of each executed batch, already
derived at the point where the operations and their intent are both in hand.
Deleting it now and re-deriving an equivalent layer when that phase is designed
is the expensive order, and the second derivation would be further from the
walk that produced the operations.

It is also already on disk. The working journal holds 469 `meaning` fields
across 1058 event rows in two streams, and `WalkerEventCodec` decodes them
strictly. Removing the field is a durable wire change, not a code cleanup, so
it needs a reason stronger than tidiness.

The assertions that would be lost have no replacement. `RecoverProcedureSuite`
and `GameApplicationServiceSuite` assert the semantic sequence a command
produced — that Recover spent supply and then acquired a named relic — which
the recorded operations express only positionally.

## Considered and rejected

- **Delete the field and accept that existing journals stop loading.**
  Rejected: the cost is small today because the only journals are local and
  disposable, but the argument is entirely about convenience now and gives up a
  field the roadmap has a use for.
- **Delete the field and add a tolerant decode arm that ignores a legacy
  `meaning` key.** Rejected: a permanent decoder concession paid for two
  disposable local journals, and it would have to be removed again if the
  action history phase reinstates the field.
- **Stop deriving the field but keep decoding it.** Rejected: it leaves the
  wire shape and the codec intact while removing the only thing that makes the
  field meaningful, which is the worst of both.

## Consequences

- Architecture reviews should not re-propose deleting `DeltaMeaning` on the
  grounds that nothing reads it. That it has no reader is recorded here and is
  not new information.
- Revisit when the player-facing action history phase is designed. That design
  decides whether the formatter reads this field or the recorded operations. If
  it reads the operations, this field becomes deletable and the deletion should
  be part of that phase.
- `WalkerStepPayload.DeltaRecorded` stays regardless of the outcome above. It
  is a live discriminator in `WalkerReplay`, whose delta arm requires recorded
  operations while the roll and choice arms require none.

## Closure (2026-10-18)

The revisit condition above is met. The player-facing action history was
designed as the game log (`2026-09-25-game-log-design.md`) and shipped, and it
reads the recorded operations. Under this record's own terms that makes the
field deletable, and it is now deleted: `DeltaMeaning`, its four cases, its
`of` derivation and both codec arms are gone, and
`WalkerStepPayload.DeltaRecorded` survives as a fieldless discriminator.

Each of the three reasons for retention expired:

- **A named future reader.** The game log's design settles the opposite:
  "The formatter reads operations, not `DeltaMeaning`" (that design, under
  "Reading the journal"). The consumer this record was holding the field for
  declined it, on the ground that `OperationApplied` falls back to a class
  name for every batch that is not one of three shapes, which is too little
  to narrate from. No other consumer was named then or exists now: on
  2026-10-13 the field had zero production readers, and the five main-source
  sites that match `DeltaRecorded` match it as a type only.
- **Already on disk.** The standing rule since 2026-10-13 is that a wire
  change need not keep old journals decodable. The cost this record weighed
  is one the project no longer pays. As it happens no journal breaks either:
  `decodeStepPayload` reads named keys, so a stored `meaning` key is ignored
  rather than rejected. No tolerant decode arm was added; there is simply no
  check to fail.
- **Assertions with no replacement.** This was true when written and is false
  now. Both semantic assertions sit beside an `ops` assertion of the same
  fact: `RecoverProcedureSuite` asserts `SupplySpent(actor, 1)` and then
  `ops == Vector(SpendSupply(actor, 1))`, and the same pairing for the relic
  `Move`; `GameApplicationServiceRecoverSuite` asserts the three meanings
  directly below asserting the three operations in the same order. The
  duplicate assertions were deleted and the operation assertions kept, so no
  coverage was lost.

The cost of keeping it had meanwhile grown: thirteen test files and every new
walker plan had to construct a value nothing read.

This question is now closed in both directions. A later review should not
re-propose deleting the field, which no longer exists, and should not
reinstate it on the strength of the September reasoning above without a
consumer that will read it.
