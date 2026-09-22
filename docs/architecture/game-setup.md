> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design:
> `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`.

# First-game setup

Status: implemented and replay-tested, reviewed September 2026 (Chronicle
design, slice 3: `docs/superpowers/specs/2026-09-21-chronicle-setup-design.md`).

`gameplay/setup/GameStartRules.evolve` builds a fresh `Ready` game directly
from a `Chronicle` and `SetupOrders`: it begins at `OathState.NoGame`, records
one `GameStarted` event carrying both, and runs Setup as an ordinary
`TriggeredProcedureRef` walker (`gameplay/setup/SetupProcedure.scala`) from
there. There is no separate setup state machine or command payload; the
walker's per-player tree -- `Decide` a site, place the pawn, `Decide` an
adviser, keep/discard, a `SetupEnd` window that folds the six batch-1 edifice
powers (`gameplay/powers/setup/`: Great Market/Bandit Market, Great
Forge/Broken Forge, Proving Grounds/Empty Grounds) -- ends the same way every
other triggered procedure does, with `BeginTurn(firstPlayer, Wake)`.

## Chronicle, orders, and validation

A `Chronicle` is the between-game record a Chronicle generator produces: the
ordered atlas box (which sites are in play and their stored edifices), the
World Deck's denizen pool, the ordinary-relic order, the reliquary, and the
dispossessed board. `SetupOrders` is the one-time per-game deal drawn from
it -- participants, first player, and the dealt `worldDeckOrder` -- computed
by `ChronicleFirstGamePlan.dealOrder` and recorded alongside the Chronicle on
`GameStarted`, so replay never re-derives either.

`GameStartRules.evolve` validates only what Setup itself needs from the
Chronicle: known and unique atlas/denizen/relic ids, at least 8 atlas sites,
enough World Deck and relic cards to deal, and one matching edifice per
Homeland in play. The deeper suit-count and Vision-packet audit a Chronicle
must satisfy is the generator's own job, checked once at generation time
(`FirstGameChronicleGenerator.validate`) rather than again at every setup.

The completed aggregate includes site resources and bandits, facedown site
relics, ruined Homeland edifices, regional discards, player advisers, World and
relic decks, starting Exile boards and Supply, banners, tracks, the Oathkeeper
goal, favor banks, and six unaltered Foundations.

The scope follows Combined Rulebook pp. 6-7 and the New Foundations first-game
clarification on p. 8. It excludes Legacy selection/effects,
Chancellor/Citizen/Imperial setup, campaign restoration, altered Foundations,
and Chronicle progression beyond the first game.

## Application and transport

`GeneratedFirstGamePlanFactory` derives a random Chronicle through
`FirstGameChronicleGenerator` and shuffles seating; trusted-game
provisioning, development game creation, and authenticated bootstrap all use
it (the dev-only loopback bootstrap route and its deterministic
`DevelopmentFirstGamePlanFactory` fixture were retired once the development
start page moved onto the same trusted `POST /games` provisioning). Tests
that need a fixed seating order construct it with an identity (no-shuffle)
`ChronicleRandomPort` instead. Bootstrap accepts only public participant
configuration; the server derives the Chronicle and records it in the first
event. Neither the bootstrap response nor ordinary projection exposes hidden
order.

Shared `FirstGameBootstrapRequest` and its codec compile on JVM and Scala.js.
They are actorless configuration DTOs. The authenticated route derives
authorization and player seats from memberships. The application maps bootstrap
configuration to a Chronicle deal and appends the same current event envelope
used by gameplay.

Setup's `Decide`s are ordinary walker decisions: the client resolves them with
the same generic `resolveWalker` intent and walker-decision panel every other
procedure uses, not a bespoke pawn-placement or card-decision command. A
`Phase.Setup` game flows through the ordinary `Ready` projection; the
walker-decision projector governs who sees what -- the parked decision's
owner gets the owner-private `walkerDecision` view (including their
preview-only adviser identities), everyone else gets `walkerWaiting` naming
who it is waiting on. The setup table already exposes populated sites, ruined
Homeland edifices, decks, public pile tops, banks, banners, tracks, first
player, and starting boards, so completion does not visually rebuild the
world. Replay, redaction, codec, and continuity tests cover the boundary.
