> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design:
> `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`.

# Bounded Forge action

The implemented Forge slice follows Combined Rulebook p. 25 and New
Foundations p. 14. It is a normal Act action costing exactly 1 Supply. The
authoritative validator derives the pawn site, its ruler, three empty faceup
denizens, and the printed `forgeRequirements` from current state and catalog
data. `recoverDifficulty` is never used as a substitute.

Forge starts a typed `PendingProcedure.Forge` containing stable
`SiteDenizenTarget`s and the printed resource multiset. Completion supplies one
typed `ForgeResourceAssignment` per target. Replay rejects a changed site,
targets, cost, Supply payment, resource multiset, or relic-deck top before any
state is returned. Favor assignments also validate and consume the finite bank
matching each target denizen's catalog suit; the current model has no separate
secret-bank state. A successful completion adds one resource to each denizen,
removes the exact top relic from the deck, and adds it facedown to the actor's
relics.

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
