> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design:
> `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`.

# Bounded Forge action

The implemented Forge slice follows Combined Rulebook p. 25 and New
Foundations p. 14. It is a normal Act action costing exactly 1 Supply. The
authoritative validator derives the pawn site, confirms the actor rules it,
finds three empty faceup denizens, reads the printed `forgeRequirements`, and
checks that the actor can afford that favor/secret total. `recoverDifficulty`
is never used as a substitute. Supply is validated by the first
`AdjustSupply` operation before the action can park, rather than duplicated as
a semantic start gate.

Forge starts a typed `PendingProcedure.Forge` containing stable
`SiteDenizenTarget`s and the printed resource multiset. Completion supplies one
typed `ForgeResourceAssignment` per target. Replay rejects a changed site,
targets, cost, Supply payment, and resource multiset before any state is
returned. Each assignment becomes a `PayCost` from the actor's play area onto
its denizen; suit banks are not involved. If the relic deck has a top card,
completion moves it facedown to the actor's relics. An empty relic deck remains
a legal wasted Forge: Supply and resources are paid, with no relic gained.

The application owns relic preparation through `RelicDrawPort`, parallel to
Search draw and Recover dice ports. Owner-scoped projection exposes the pending
assignment; other players see only a waiting phase, and player-board projection
continues to hide facedown relic identity.

This milestone implements only the printed base procedure. The complete
pre-release handler vocabulary was audited and has no component that modifies
the base Forge procedure. Forge pins the exact audited handler-vocabulary
fingerprint; any added or changed vocabulary makes active component handlers
block with `UnsupportedForgeState` until the catalog is explicitly re-audited.
Forge never interprets component `rulesText`.
