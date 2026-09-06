> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design:
> `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`.

# Bounded Campaign design

This design began as the smallest coherent first-game Campaign procedure and
now includes authoritative multi-site target declaration for Conquest against
bandits, with fixed unaltered Foundations and a typed boundary for relevant
Campaign powers. Multi-site loss and placement allocation are complete for
bandit and player Conquest defenders, and the base Raid procedure is complete
for player defenders.

## Typed rule pipeline and inventory

Campaign rule discovery is exact-ID based and ordered by handler priority,
stable source identity, and handler ID. It checks all actor advisers, faceup
held relics, the target, and every actor-ruled site. Facedown advisers remain
discoverable for conservative classification, but their passive powers are not
active and are not treated as revealable before Campaign begins. The resolver
models the printed windows explicitly: target
and force formation; attacker battle plans; attack roll and skull losses;
attacker sacrifice; defender battle plans and roll; outcome; conquest
placement or Raid resolution and pawn relocation; and remaining end, victory,
and defeat effects. The terminal windows are explicit even though their
additional printed handlers remain deferred.

The source-verified inventory for this boundary is:

- safely executable now: the mandatory `denizen.vow-of-peace` Campaign block
  at target and force formation, and optional attacker plan
  `denizen.outriders` (“Ignore all skulls you roll”) and the paid
  `relic.brass-army.campaign` (`[secret] +4 [attack-die]`) at the attacker battle-plan
  window; the title defender plan (+1 die, or +2 on its Usurper side); and
  cost-free, choice-free bandit `denizen.watchdog` where applicable;
- blocked by missing decisions or data: every other optional attacker battle plan;
  rerolls, costs, directional `±` choices, conditional pools, discard/bury,
  favor-bank rewards, and remaining victory/defeat/end effects;
- irrelevant to the implemented result: Imperial effects. Unsupported
  Raid-specific and other player-defender powers are discovered and block until their handlers
  are implemented. Bag of Siegeworks,
  Weeping Banner, and Peace Envoy are not in this class: each can change this
  boundary and therefore rejects pending typed resolution.

The attacker receives an authoritative, ordered multi-plan decision containing
each unused accessible registered source plus an explicit finish control. Each distinct
source may be selected once. Selecting a plan records and applies its cost or
reveal without rolling, keeps the decision open, and removes that source from
the unused choices. Events record the complete selected source order and typed
cost/effect vectors. Replay re-resolves every handler and rejects duplicate,
substituted, stale, or reordered sources. Choices and selected order
are projected only to the current decision owner, while public and other-player projections show
only that Campaign is waiting. Blocked rules report exact handler and stable
source identity.
Mountain/Plains Campaign effects also remain blocked. Site access is derived through `SiteRule`; corrupt
lineage-to-ruler mappings reject instead of being treated as harmless.

## Authoritative procedure

Campaign costs 2 Supply. The active Exile must be in Act, have a pawn at a site
with a legal Conquest ruler or a co-located enemy pawn for Raid, and may gather
from zero up to all warbands on their board.
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
decision. A player defender receives an independently authorized plan window
after the attacker finishes. Attack dice are prepared only after both windows
finish and the server revalidates all choices. Finishing with no selections is skip.
Brass Army is offered only when held faceup, empty, unused, and payable with one faceup
secret. Selection places that secret on the relic and rolls exactly four extra
attack dice when the actor finishes. Those dice do not increase physical force or later loss,
sacrifice, survival, or placement limits.
When Outriders was also selected, all skull losses are ignored and every skull
die retains its swords. Without Outriders, skull dice beyond the physical force
cannot kill a warband and contribute no swords.

Raid instead persists a typed kind and a canonical pawn-first target vector.
The co-located enemy pawn is mandatory; zero or more of that defender's faceup
relics and held banners follow in stable relic/banner order. Declaration and
replay reject missing pawn, duplicate, reordered, stale, facedown, foreign, or
no-longer-held targets. Raid never targets the pawn's site.

Defender dice equal the targeted sites' printed defense plus resolved defender
plan effects; bandit or player forces at all targets likewise contribute to
defense. For Raid, the pawn contributes two dice, every targeted relic adds its
printed defense, each targeted banner adds three dice, and the defender's board
warbands are their force. The title is an optional registered defender battle plan: Oathkeeper
adds one die and Usurper adds two. Bandits automatically use every applicable
registered plan only when it is cost-free and choice-free; Watchdog is the
first such handler. Other relevant bandit plans block conservatively.
Attack faces record hollow
swords, swords, and the skull-plus-two-swords face. Hollow swords score one per
pair. A skull removes one force warband, and its two swords count only when that
loss can be paid. Defense faces use the existing blank/shield/doubler
vocabulary. Before the defense roll, the actor explicitly chooses how many
surviving force warbands to sacrifice for one attack each. Attack must strictly
exceed defense.

On victory, all warbands at every targeted site are removed and
the actor allocates zero or more surviving force warbands across the complete
ordered target set. Every target appears exactly once in the submitted
allocation, including zero allocations; the total cannot exceed the surviving
force, and unplaced force returns to the board. The browser keeps this draft
local, reports allocated and remaining totals, and submits one atomic command.
On defeat, the default registered policy kills half the attacker's surviving
force rounded down and returns the remainder to its board; the
bandits remain. Supply and committed pieces are validated against the preceding
state during replay. Zero forces are represented as
`SiteForces.Empty`, never as an occupied zero-count force.

On a successful Raid, the registered loss policy kills half the defender's
board force rounded down and returns the remainder to that board. A durable
Raid event then records and replay-validates the printed order: targeted faceup
relics and banners transfer; People's Favor resources return one at a time to
the least-filled, leftmost-on-tie favor bank and the exact number of Darkest
Secret resources burned is durable; ordinary facedown advisers append in board
order to the Raid site's next-region discard pile, the Conspiracy returns to
the box, facedown relics enter the Chronicle reliquary, and half the defender's
favor, rounded down, burns. The attacker then receives a typed owner-scoped pending
procedure containing canonical legal destinations and relocates the defender's
pawn to another site. This relocation is not Travel. Other viewers receive no
hidden discarded identities or relocation controls.

Defender loss is resolved through a registered, stable-ID policy seam. The
default policy emits and applies ordered `Remove` effects at every target.
Bandits lose them all. A player defender loses half the aggregate targeted
force, rounded down, and the policy records returning every survivor to that
player's board before attacker placement. Replay resolves the recorded policy again and verifies
its complete effect vector before applying it. The effect vocabulary also
represents preservation, relocation, and replacement; placement cannot
overwrite a force that the selected policy leaves at a target. These dormant
forms provide the mechanical boundary for future powers, but no such printed
power is inferred or activated by this slice.

Attacker defeat uses the same stable-ID policy/result path. Its committed-force
effects use a kind-aware, validated Campaign origin: the mandatory first site
for Conquest and the co-located pawn site for Raid. Shared loss code does not
index Conquest targets for Raid procedures.
Effects explicitly record killed, returned, preserved, or site-relocated pieces;
replay resolves the selected policy and validates complete disposition before
changing the board. Thus alternate loss rules do not require rewriting the
terminal Campaign evolution.

The mandatory pawn-site ruler is the typed Campaign defender. Every optional
site must have that same ruler. Powers across a player defender's advisers,
relics, and ruled sites are classified in defender context: known attacker-only
handlers do not block merely because the defender rules them, while unknown or
defender-relevant effects reject conservatively. Projection and command
handling share these checks. During the defender window, the defender receives
owner-only controls while the active attacker receives a waiting state.

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
only the decision owner's selected target set and choices; authenticated routes derive the
actor and never accept dice. Pending state and controls are viewer-scoped to
that owner. Finite Exile warbands are preserved by moving existing pieces only.
No migration layer, Forge, Imperial
Campaign, Vision, Chronicle, or general power interpreter is introduced.

## Deferred work

- further optional attacker, defender, and deterministic bandit plan families;
- non-deterministic sacrifice/loss choices where multiple legal assignments
  matter;
- additional Raid, victory, defeat, and `At End` card-power handlers; the timing
  windows exist structurally, but no behavior is inferred for them;
