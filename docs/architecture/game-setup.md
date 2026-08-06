# Bounded game setup

Status: bounded pawn placement and exile-only first-game slices implemented,
July 2026.

This slice proves typed commands, domain events, optimistic event persistence,
and deterministic replay without constructing a complete `OathGame`.

## State and command boundary

`SetupState` has three immutable states: `NotStarted`, `InProgress`, and
`Completed`. Started state pins the production `CatalogRef`, preserves ordered
player/lineage participants, stores the ordered 2/3/3 eight-site layout, and
records pawn placements in participant order.

`BeginSetup` contains the exact ordered eight-site selection. That order is the
externally recorded result of any shuffle or draw. Command handling and replay
perform no random selection and receive no RNG. `PlacePawn` identifies the
participant and an in-play site. Several pawns may share a site.

Commands emit `SetupStarted`, `PawnPlaced`, and, immediately after the final
pawn, `SetupCompleted`. The typed continuation reports either the next
participant awaiting placement or completion. Replay applies only events and
reports the zero-based index of the first invalid event.

## Validated scope

The rules validate the pinned catalog, nonempty and unique players and
lineages, exactly eight distinct catalog sites, strict placement order, and
catalog-known/in-play pawn destinations. Tests load the production typed
catalog projection containing setup cards, Supply boards, and sites.

## Deliberate deferrals

The setup events use the versioned durable format described in
[`authoritative-events.md`](authoritative-events.md). Commands are not part of
that wire format.

The v1 bounded slice does not initialize player boards or Supply, advisers,
legacies, edifices, Foundations, the Chronicle, card decks, resources, roles,
or a full `OathGame`. It does not implement UI, database/network storage,
snapshots, or gameplay.

## Exile-only complete first-game endpoint

`FirstGameSetupRules` is a separate v2 state machine so the v1 event classes,
commands, replay behavior, and checked-in golden bytes remain unchanged. It
starts at `NoGame`, records a complete `FirstGameSetupPlan`, reuses the existing
typed `SetupCommand.PlacePawn`, records each starting adviser choice, and ends
at `Ready(ReadyFirstGame)`. The endpoint contains a structurally valid
`OathGame`; its selected first player is active at `Phase.Wake`, meaning they
are ready to begin their first turn. Wake behavior is not executed here.

The plan records every external outcome that setup would randomize:

- seating, stable player/color/lineage identities, and first player;
- the ordered eight selected sites;
- the 60-denizen pool order and exact final World Deck order;
- all five fixed first-game Vision identities and their packet positions;
- the complete ordinary-relic order; and
- the matching ruined edifice chosen for each selected Homeland.

Replay applies these recorded values and never invokes randomness. The engine
validates 10 denizens per suit, the two-card regional discard seeds, three-card
player hands, the 10-denizen/2-Vision and 15-denizen/3-Vision packets, complete
ordinary-relic conservation, matching Homeland/edifice suits, placement order,
and adviser ownership.

The built aggregate includes site starting resources, capacity bandits,
facedown site relics, ruined Homeland edifices, regional discards, advisers,
the World and relic decks, starting Exile board wealth and full Supply, banners,
tracks, Oath of Supremacy, an empty Oathkeeper title, fixed favor banks, and
six normal Foundations with no alteration sources.

This inclusion is sourced to Combined Rulebook pp. 6-7 and the New Foundations
first-game clarification on p. 8. The implementation deliberately excludes
Legacy draws/choices/effects, Chancellor/Citizen/Imperial setup, campaign
restoration, generic Foundation interpretation, Chronicle progression, and all
Wake/Act/Rest behavior. It also does not claim the contents of the
Dispossessed or the order of the 16 unselected Atlas sites; neither is needed
to make the selected world, player state, and active decks structurally valid
for this endpoint. The Grand Scepter is excluded from the ordinary relic
shuffle because it is an Imperial component.

## First-turn Wake endpoint

The first post-setup slice retains `Ready(ReadyFirstGame)` as the aggregate and
implements only the built-in Take Wealth power and the explicit choice to end
Wake. CR p. 17 orders the mandatory Oathkeeper/Usurper and Vision checks before
optional Wake powers, permits Wake powers once each, and defines Take Wealth.
The general power rules on CR pp. 28 and 31 motivate identifying a use by
timing, source, and stable power identity rather than by a display label.

`TakeWealth(playerId, Favor|Secret)` derives the pawn's current site. Its
`gameplay.take-wealth` event transfers exactly one loose token to the player's
board, records that site's Take Wealth power instance as used, and remains in
Wake. A future Wake movement effect can therefore expose a distinct Take
Wealth instance at a different site without permitting repetition at the old
site. `EndWake(playerId)` is independent: `gameplay.wake-ended` enters Act and
opens an informational normal-action selection boundary.

The slice validates the active player, Wake timing, game/result state, current
in-play pawn site, loose resource, enemy-pawn exclusion, and per-instance use.
Any other Exile pawn at the site is an enemy in this all-Exile milestone.
Oathkeeper/Usurper ownership or a revealed Vision is a typed unsupported
victory state so mandatory checks cannot be bypassed. Other Wake powers are
optional and do not block End Wake.

River movement, generic card/relic/edifice/Foundation power interpretation,
victory resolution, Act actions and costs, later turns, Rest and Supply
refresh, and production authentication are deliberately excluded.

## First-turn normal Travel endpoint

The first Act slice implements normal Travel for the supported unaltered,
exile-only first game. `Travel(playerId, destinationSiteId)` derives the pawn's
source and the exact Supply cost from the authoritative state. The region
matrix and mandatory Coast, Island, Mountain, and Pass site powers follow
Combined Rulebook pp. 21 and 31 and the New Foundations site-power summary on
p. 11. Coast's one-Supply route overrides the other Travel modifiers. Direct
travel to a Pass is legal; an actor-ruled Pass permits entry to its region's
other sites; a bandit-ruled Pass blocks it. Another player's Pass is omitted
from legal destinations and returns typed unsupported-consent because the
consent timing described on CR p. 43 is not yet modeled.

`gameplay.traveled` is a v3 gameplay event containing actor, derived source,
destination, and Supply spent. Evolution recomputes source, legality, and cost,
then atomically moves the pawn and debits Supply. The phase remains Act and the
continuation reopens normal action selection, so repeated affordable Travel is
allowed. `usedPowers` is unchanged: moving to a new site does not reopen Wake or
permit Take Wealth in the same turn.

The player-private projection owns the payable destination/cost pairs. The L2
World remains semantic read-only site articles during ordinary Act selection;
local Travel selection turns only projected destinations into buttons, labels
their Supply costs, and supports a no-command Cancel. An accepted command,
polling advance, reload, or stale-position refresh closes local selection and
renders the authoritative snapshot.

River movement, a Pass consent workflow, generic card/relic/edifice/legacy or
altered-Foundation modifiers, Campaign, Rest, later turns, and production
authentication remain excluded. Face-up advisers, held relics, intact
edifices, active legacies, altered Foundations, or non-Exile roles trigger a
typed unsupported Travel state rather than silently bypassing possible powers.
