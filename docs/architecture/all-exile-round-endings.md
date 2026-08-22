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

1. If an Exile holds the Usurper title, that player wins.
2. Otherwise, each Exile with a revealed Vision who meets its goal is eligible.
   If several are eligible, use the printed Vision order: Conquest (most sites),
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
Before Rest, it inventories active faceup advisers, site cards, edifices, held
relics, and active Legacies for Rest, round-end, War Exhaustion, or win-changing
handlers. An applicable unimplemented handler blocks with its stable source key
and exact handler ID. Unknown active Legacy inventory also blocks. The fixed
Mob and Wandering Flame banner faces and fixed Foundations have no additional
round-end handler in this slice.
