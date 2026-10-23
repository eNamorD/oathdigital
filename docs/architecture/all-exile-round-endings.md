# Fixed all-Exile round endings

This slice follows the current New Foundations rules, not the original Oath
ending procedure. The authoritative sources are *Oath Combined Rulebook* p.19,
*Oath: Welcome to New Foundations* p.8, and the current Oathkeeper goal card
backs (New Foundations revision 1, March 2026). The goal-back scan is not in
the repository.

## Procedure

After the final player in first-player order completes Rest, the round ends.
For rounds 1 through 7, advance the round marker by one and enter the first
player's Wake. At the end of round 3, remove the Usurper Limiter, so the
Oathkeeper may flip at the first player's round-4 Wake. There is no exile-only
end die and no ending check in rounds 5 through 7.

At the end of round 8, do not begin another Wake and do not advance beyond the
printed track. Resolve War Exhaustion in this strict order:

1. If an Exile holds the Usurper title, that player wins.
2. Otherwise, once at least three Visions have been drawn, each Exile with a
   revealed Vision who meets its goal is eligible. If fewer than three Visions
   have been drawn, no Visionary is eligible. If several are eligible, use the
   printed Vision order: Conquest (most sites),
   Rebellion (People's Favor), Sanctuary (most relics), then Faith (Darkest
   Secret).
3. Otherwise, the Oathkeeper wins.
4. If there is no Oathkeeper, select the winner randomly from all players.

The Empire-only Stable Regime and Chancellor steps are skipped. Ordinary Wake
Usurper/Visionary victories never run after the round-8 procedure, so the above
precedence is authoritative. Vision eligibility uses the same holdings
evaluation as state-based Oathkeeper/Vision checks; it is not independently
reimplemented in Rest.

No player decision suspends this fixed procedure. The only randomness is the
no-title fallback. A server-owned port selects from canonical first-player
order, and the authoritative event records both that domain and the result.
`RoundEnded` and `WarExhaustionResolved` are state-based events, so replay
recomputes them (`StateBasedEvaluation.evolve`) rather than applying them
blindly. It rejects a changed outcome kind, Vision, candidate order, or an
ineligible random winner. The Rest walker steps themselves replay their
recorded operations only.

## Rest cleanup

Rest runs as the Begin Rest and Finish Rest walker procedures. Finish Rest's
cleanup step derives one immutable `RestCleanupPlan` when it runs and records
it as that step's core operations. The plan covers every resource-bearing
card in play (`InPlayCardResources`): advisers, held relics, and the denizens
and relics at sites in play. Favor on a denizen or edifice returns to the bank
of its effective suit (`EffectiveSuit`). That is its printed suit, except that
a Pledge to Discord holder's faceup advisers return favor to Discord. Secrets
on those cards return to the resting player, relic favor is left in place,
and the player's facedown board secrets flip faceup.

`PlayerSecretSummary` is derived rather than stored. Available means faceup
board secrets, facedown means facedown board secrets, and committed means the
secrets on cards in play, which Rest would return. Only the active player has
committed secrets; every other player's committed count is zero.
`totalSecrets` is their sum. Projections publish these counts without
exposing card identities.

## Bounded support and powers

Rest and the round ending accept only all-Exile lineages
(`BeginRestProcedure.validateSupportedState`, `StateBasedEvaluation.supported`).
Four Rest powers are implemented and registered in `PhasePowerCatalog`: Vow of
Poverty (`powers/economy`), Silver Tongue and Insomnia (`powers/rest`),
and Vow of Obedience (`powers/cardplay`). Naysayers (`denizen.naysayers`) is
not implemented. Its card stays in `catalog/holding/`. An applicable
unimplemented Rest handler is ignored, not blocking, and Begin Rest records
nothing about it. Mechanics are never inferred from
rules text or handler-name fragments. Banner faces and Foundations are rule
sources in `RuleSourceIndex` like any card. The Grand Council and Festival
banner faces and an altered Foundation have no implemented power, so their
handlers are ignored and recorded the same way at Rest start.
