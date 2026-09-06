> **Note (2026-09-05): implementation form superseded.** Rules content here stays
> authoritative; the code it describes (bespoke action procedures, power seams,
> typed-fact vocabularies) is being replaced by the procedure-walker design:
> `docs/superpowers/specs/2026-09-05-procedure-walker-design.md`.

# First-game setup

Status: implemented and replay-tested, reviewed August 2026.

`gameplay/setup/FirstGameSetup.scala` owns the introductory all-Exile setup
state machine, while `FirstGameSetupMaterializer` is the pure source of every
physical table formula used by both in-progress projection and completion. It
begins at `OathState.NoGame`, records a complete
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

Player/public setup projections use `GameProjector`. During pawn placement only
the active viewer receives their three preview-only adviser identities; the
same cards become the actionable Keep/Discard decision after placement. Earlier
rejections immediately extend the destination regional discard. Other viewers
and public scope never receive the identities. The setup table already exposes
populated sites, ruined Homeland edifices, decks, public pile tops, banks,
banners, tracks, first player, and starting boards, so completion does not
visually rebuild the world. Replay, redaction, codec, and continuity tests cover
the boundary.
