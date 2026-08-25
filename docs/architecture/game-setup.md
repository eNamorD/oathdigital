# First-game setup

Status: implemented and replay-tested, reviewed August 2026.

`gameplay/setup/FirstGameSetup.scala` owns the introductory all-Exile setup
state machine. It begins at `OathState.NoGame`, records a complete
`FirstGameSetupPlan`, validates pawn placement and starting-adviser choices,
and ends at `OathState.Ready(ReadyGame)` with the selected first player in
Wake.

## Recorded plan and validation

The plan records every externally randomized outcome:

- ordered participants, player/color/lineage identities, and first player;
- the ordered eight-site layout;
- the 60-denizen pool and final World Deck order;
- the five fixed first-game Visions and packet positions;
- the ordinary-relic order; and
- the matching ruined edifice for each selected Homeland.

Replay applies only recorded values. Setup validates catalog identity, unique
participants and lineages, ten denizens per suit, regional discard seeds,
three-card starting hands, Vision packets, ordinary-relic conservation,
Homeland/edifice suits, placement order, adviser ownership, and structural
domain invariants.

The completed aggregate includes site resources and bandits, facedown site
relics, ruined Homeland edifices, regional discards, player advisers, World and
relic decks, starting Exile boards and Supply, banners, tracks, the selected
Oathkeeper goal, favor banks, and six unaltered Foundations.

The scope follows Combined Rulebook pp. 6-7 and the New Foundations first-game
clarification on p. 8. It excludes Legacy selection/effects,
Chancellor/Citizen/Imperial setup, campaign restoration, altered Foundations,
and Chronicle progression.

## Application and transport

`DevelopmentFirstGamePlanFactory` creates a reproducible catalog-derived plan
for loopback development. Production bootstrap accepts only public participant
configuration; the server derives the hidden plan and records it in the first
event. Neither bootstrap response nor ordinary projection exposes hidden order.

Shared `FirstGameBootstrapRequest` and its codec compile on JVM and Scala.js.
They are actorless configuration DTOs. The authenticated route derives
authorization and player seats from memberships. The application maps bootstrap
configuration to domain setup and appends the same current event envelope used
by gameplay.

Player/public setup projections use `GameProjector`. Only the active adviser
chooser sees candidate identities and controls; pawn candidates are projected
as typed board targets. Replay, application, route, redaction, and shared codec
tests cover the boundary.
