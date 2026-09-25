# Delta Meaning Retention: A Write-Only Journal Field Kept on Purpose

> Status: decision recorded 2026-09-24 from an architecture review. It records
> why `DeltaMeaning` is not deleted, so a later review does not re-propose it.
> No implementation is authorized by this document alone.

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
