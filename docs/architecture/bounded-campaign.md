# Bounded Campaign design

This design began as the smallest coherent first-game Campaign procedure and
now includes authoritative multi-site target declaration for Conquest against
bandits, with fixed unaltered Foundations and a typed boundary for relevant
Campaign powers. Multi-site loss and placement allocation are complete for
bandit defenders; Raid remains out of scope.

## Typed rule pipeline and inventory

Campaign rule discovery is exact-ID based and ordered by handler priority,
stable source identity, and handler ID. It checks all actor advisers, faceup
held relics, the target, and every actor-ruled site. Facedown advisers remain
discoverable for conservative classification, but their passive powers are not
active and are not treated as revealable before Campaign begins. The resolver
models the printed windows explicitly: target
and force formation; attacker battle plans; attack roll and skull losses;
attacker sacrifice; defender battle plans and roll; outcome; conquest
placement; and remaining end, victory, and defeat effects.

The source-verified inventory for this boundary is:

- safely executable now: the mandatory `denizen.vow-of-peace` Campaign block
  at target and force formation, and optional attacker plan
  `denizen.outriders` (“Ignore all skulls you roll”) and the paid
  `relic.brass-army` (`[secret] +4 [attack-die]`) at the attacker battle-plan
  window;
- blocked by missing decisions or data: every other optional attacker battle plan;
  rerolls, costs, directional `±` choices, conditional pools, discard/bury,
  favor-bank rewards, and remaining victory/defeat/end effects;
- irrelevant to bandit Conquest: defender-only and Raid-only powers,
  player-defender interactions, and Imperial effects. Bag of Siegeworks,
  Weeping Banner, and Peace Envoy are not in this class: each can change this
  boundary and therefore rejects pending typed resolution.

No printed battle plan is auto-selected. The actor receives an authoritative,
ordered multi-plan decision containing each unused accessible Outriders or
Brass Army source plus an explicit finish-and-roll control. Each distinct
source may be selected once. Selecting a plan records and applies its cost or
reveal without rolling, keeps the decision open, and removes that source from
the unused choices. The finish event records the complete selected source order,
aggregate `ignoreAttackSkulls` result, added attack-die count, and physical
attack faces. Replay validates every selection in order and rejects duplicate,
substituted, stale, or reordered sources. Choices and selected order
are projected only to the actor, while public and other-player projections show
only that Campaign is waiting. Blocked rules report exact handler and stable
source identity.
Mountain/Plains Campaign effects also remain blocked. Raid still needs
facedown-card dispossession, relic/banner transfers, favor burning, and a typed
defender relocation choice. Site access is derived through `SiteRule`; corrupt
lineage-to-ruler mappings reject instead of being treated as harmless.

## Authoritative procedure

Campaign costs 2 Supply. The active Exile must be in Act, have a pawn at a site
ruled by bandits, and may gather from zero up to all warbands on their board.
The authoritative engine and HTTP contracts accept that non-negative count as
the force. The Empty Attack Pool rule therefore permits a zero-force Campaign.
The private action projection
publishes the current legal force minimum and maximum, available board warbands,
and Supply cost alongside the mandatory target. After target selection, the
browser holds a local formation draft and presents labelled decrement,
increment, and direct-value buttons. It reports committed force, remaining board
warbands, attack dice before plans, and cost. Only the explicit Confirm Campaign
control submits; Back and Cancel produce no authoritative state change.
Formation drafts are keyed to game, viewer, sequence, action, candidate, and
projected formation facts, and are discarded on any change, conflict, cancel,
or submission. These bounds improve the interaction only: command handling and
replay still revalidate actor, phase, target, Supply, pending state, current
warbands, and submitted force.
The pawn site is mandatory and is persisted first. The player may toggle any
number of additional sites ruled by the same bandit defender. Candidates are
projected in map order, the mandatory site cannot be deselected, and the final
distinct target vector must retain canonical map order. The server rejects
missing, duplicate, reordered, stale, differently ruled, and Pass-blocked
targets. The complete target set is carried by the command, pending procedure,
event, projection, and replay path. The player confirms those typed site targets
through the reusable board-target protocol. The application service
supplies recorded attack and defense dice; clients never supply randomness.
Declaration first commits force and Supply and creates the pending attacker-plan
decision. Attack dice are prepared only after the server revalidates the chosen
sources and the explicit finish request, so the complete printed plan window
precedes the single randomness request. Finishing with no selections is skip.
Brass Army is offered only when held faceup, empty, unused, and payable with one faceup
secret. Selection places that secret on the relic and rolls exactly four extra
attack dice when the actor finishes. Those dice do not increase physical force or later loss,
sacrifice, survival, or placement limits.
When Outriders was also selected, all skull losses are ignored and every skull
die retains its swords. Without Outriders, skull dice beyond the physical force
cannot kill a warband and contribute no swords.

Defender dice equal the sum of the targeted sites' printed defense; bandit
forces at all targets likewise contribute to defense. Attack faces record hollow
swords, swords, and the skull-plus-two-swords face. Hollow swords score one per
pair. A skull removes one force warband, and its two swords count only when that
loss can be paid. Defense faces use the existing blank/shield/doubler
vocabulary. Before the defense roll, the actor explicitly chooses how many
surviving force warbands to sacrifice for one attack each. Attack must strictly
exceed defense.

On victory against bandits, all warbands at every targeted site are removed and
the actor allocates zero or more surviving force warbands across the complete
ordered target set. Every target appears exactly once in the submitted
allocation, including zero allocations; the total cannot exceed the surviving
force, and unplaced force returns to the board. The browser keeps this draft
local, reports allocated and remaining totals, and submits one atomic command.
On defeat, the attacker loses half
its surviving force rounded down and returns the remainder to its board; the
bandits remain. Supply and committed pieces are validated against the preceding
state during replay. Zero forces are represented as
`SiteForces.Empty`, never as an occupied zero-count force.

Defender loss is resolved through a registered, stable-ID policy seam. The
current policy emits and applies one ordered `Remove` effect for all bandit
warbands at each target. Replay resolves the recorded policy again and verifies
its complete effect vector before applying it. The effect vocabulary also
represents preservation, relocation, and replacement; placement cannot
overwrite a force that the selected policy leaves at a target. These dormant
forms provide the mechanical boundary for future powers, but no such printed
power is inferred or activated by this slice. Player-defender and attacking
force losses must use this same policy/result direction when those procedures
are expanded rather than embedding another fixed “kill half” calculation.

Typed staged events record the ordered target set, force, cost, both dice vectors, sacrifice,
losses, outcome, and placement. Replay recalculates every field and rejects
tampering without rerolling. After terminal evolution, the aggregate action
completion boundary refills every empty positive-capacity site with its printed
Bandit force once after the entire placement, then runs the existing bounded
Supremacy evaluation once. If this displaces the current Oathkeeper into a tie,
the former holder receives a durable, owner-authorized recipient decision;
selection is replay-validated before normal Act controls resume.

## Boundaries

Legality and projection share Campaign rule queries. HTTP and Scala.js accept
only the actor's selected target set and choices; authenticated routes derive the
actor and never accept dice. Pending state and controls are viewer-scoped to
the actor. Finite Exile warbands are preserved by moving existing pieces only.
No migration layer, Forge, Imperial
Campaign, Vision, Chronicle, or general power interpreter is introduced.

## Deferred work

- conquest against another player;
- further optional attacker plans and all defender battle-plan selection;
- non-deterministic sacrifice/loss choices where multiple legal assignments
  matter;
- Raid targets, theft/discard/burn effects, banner rules, and relocation;
