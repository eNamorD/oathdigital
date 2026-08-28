# Fixed all-Exile round endings

This slice follows the current New Foundations rules, not the original Oath
ending procedure. The authoritative sources are *Oath Combined Rulebook* p.19,
*Oath: Welcome to New Foundations* p.8, and the current Oathkeeper goal back in
`reference/2026-03-17-new-foundations-rev1/oathkeeper-goal-backs.pdf`.

## Procedure

After the final player in first-player order completes Rest, the round ends.
For rounds 1 through 7, advance the round marker by one and enter the first
player's Wake. At the end of round 3, remove the Usurper Limiter, so the
Oathkeeper may flip at the first player's round-4 Wake. There is no exile-only
end die and no ending check in rounds 5 through 7.

At the end of round 8, do not begin another Wake and do not advance beyond the
printed track. Resolve War Exhaustion in this strict order:

`PlayerResourceSources` is the single pure definition of the player, denizen
advisers, held relics, and deduplicated pawn/ruled site cards. Every Rest
completion derives one immutable cleanup plan from that physical source set.
Favor on denizens/edifices returns by printed suit,
secrets from those cards and held relics return to the player, relic favor is
left in place, and facedown board secrets flip faceup. Command handling and
replay derive the same plan before validating and evolving `RestCompleted`.

`PlayerSecretSummary` is derived rather than stored: available means faceup
board secrets, facedown means facedown board secrets, committed means secrets
on attributable cards, and `totalSecrets` is their sum. The active player uses
`PlayerResourceSources`. An inactive player must have zero secrets on owned
advisers and relics and is assigned no site commitments; otherwise derivation
and projection fail explicitly because ruler-based site attribution is unsafe.
Valid projections publish aggregate counts without exposing card identities.

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
Replay recalculates every deterministic outcome and rejects changed outcome
kind, Vision, candidate order, or an ineligible random winner.

## Bounded support and powers

The slice accepts only fixed, unaltered Foundations and all-Exile lineages.
Before Rest, a SHA-256 fingerprint pins the complete handler vocabulary for
denizens, relics, both edifice faces, Legacies, and sites. Runtime discovery
then compares active, accessible sources only against an explicit audited set
of five relevant Rest handlers: `denizen.vow-of-poverty`,
`denizen.naysayers`, `denizen.silver-tongue`, `denizen.insomnia`, and
`denizen.vow-of-obedience`. An applicable unimplemented handler blocks with its
stable source key and exact handler ID. Any changed catalog handler inventory
blocks before discovery with the expected and actual fingerprints; mechanics
are never inferred from rules text or handler-name fragments. The fixed Mob and
Wandering Flame banner faces and fixed Foundations remain separate typed checks.
