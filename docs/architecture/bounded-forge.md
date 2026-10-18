> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) was replaced by the procedure-walker design, which
> now runs every action.

# Bounded Forge action

The implemented Forge slice follows Combined Rulebook p. 25 and New
Foundations p. 14. It is a normal Act action costing exactly 1 Supply. It runs
as the `ForgeProcedure` walker tree. The start gates are `ForgeRules.validate`:
the actor rules their pawn site, the site prints a Forge cost of three
resources, the site holds exactly three empty faceup denizens, the actor can
afford that favor/secret total, the actor has at least 1 Supply, and the relic
deck is not empty. `recoverDifficulty` is never used as a substitute. Together
the gates make a started Forge always answerable.

The walk first spends 1 Supply. A site that prints both favor and secrets then
parks on the `forge.assignment` decision, which places each resource on one of
the three targets; a single-resource cost needs no decision. Eligible targets
are read live from state, so the offered set and the accepted set have one
definition. Each placed resource becomes a `PayCost` from the actor's play area
onto its denizen; suit banks are not involved. The last step plays the top
relic of the relic deck facedown into the actor's play area. That relic is read
from state when the step runs; no port prepares it, and no answer carries it.

A resumed Forge rebuilds its tree without the start gates. Starting spends 1
Supply, so re-checking supply would strand a player who began with exactly 1.

This milestone implements only the printed base procedure. The complete
pre-release handler vocabulary was audited and has no component that modifies
the base Forge procedure. Forge never interprets component `rulesText`.
