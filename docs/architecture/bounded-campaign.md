# Bounded Campaign design

This slice implements the smallest coherent first-game Campaign procedure:
single-site Conquest against bandits at the acting Exile's pawn site, with
fixed unaltered Foundations and no relevant battle-plan or Campaign powers.
Full multi-site Conquest and Raid remain out of scope.

## Boundary and blocker

The catalog identifies printed powers, but the runtime has no executable
Campaign or battle-plan handlers. Raid also needs facedown-card dispossession,
relic/banner transfers, favor burning, and a typed defender relocation choice.
Those dependencies prevent full Campaign from failing safely and replaying
deterministically. This slice therefore rejects any active relevant Campaign or
battle-plan power instead of ignoring it, and exposes no Raid command.

The unsupported-power guard checks every actor adviser, including facedown
denizens that could reveal a battle plan; faceup held relics; all denizens and
edifices at the target and every site the actor rules. Site access is derived
through `SiteRule`; corrupt lineage-to-ruler mappings reject the action rather
than being treated as harmless. This deliberately conservative scan remains
until typed Campaign handlers can classify and execute relevant powers.

## Authoritative procedure

Campaign costs 2 Supply. The active Exile must be in Act, have a pawn at a site
ruled by bandits, and have at least one board warband. The authoritative engine
and HTTP contracts accept a positive attack-die count no greater than the
actor's board warbands; that count is the force. The current browser
deliberately uses the bounded formation “commit all board warbands” and labels
that choice before confirmation. Selecting a partial force in the browser is
deferred.
The pawn site is the mandatory and only target. The player confirms that typed
site target through the reusable board-target protocol. The application service
supplies recorded attack and defense dice; clients never supply randomness.

Defender dice equal the site's printed defense. Attack faces record hollow
swords, swords, and the skull-plus-two-swords face. Hollow swords score one per
pair. A skull removes one force warband, and its two swords count only when that
loss can be paid. Defense faces use the existing blank/shield/doubler
vocabulary. Before the defense roll, the actor explicitly chooses how many
surviving force warbands to sacrifice for one attack each. Attack must strictly
exceed defense.

On victory against bandits, all defending site warbands are removed and the
actor explicitly chooses how many surviving force warbands to place at the
site; unplaced force returns to the board. On defeat, the attacker loses half
its surviving force rounded down and returns the remainder to its board; the
bandits remain. Supply and committed pieces are validated against the preceding
state during replay. Zero forces are represented as
`SiteForces.Empty`, never as an occupied zero-count force.

Typed staged events record target, force, cost, both dice vectors, sacrifice,
losses, outcome, and placement. Replay recalculates every field and rejects
tampering without rerolling. After terminal evolution, the aggregate action
completion boundary refills every empty positive-capacity site with its printed
Bandit force, then runs the existing bounded Supremacy evaluation.

## Boundaries

Legality and projection share Campaign rule queries. HTTP and Scala.js accept
only the actor's selected site and choices; authenticated routes derive the
actor and never accept dice. Pending state and controls are viewer-scoped to
the actor. Finite Exile warbands are preserved by moving existing pieces only.
No event-version bump, migration layer, Forge, Imperial
Campaign, Vision, Chronicle, or general power interpreter is introduced.

## Deferred work

- optional additional same-ruler sites and multi-site force/loss allocation;
- conquest against another player;
- partial-force selection in the browser (the engine/API already support it);
- executable attacker/defender battle plans and explicit timing decisions;
- non-deterministic sacrifice/loss choices where multiple legal assignments
  matter;
- Raid targets, theft/discard/burn effects, banner rules, and relocation;
- displaced-Oathkeeper tied-recipient choice.
