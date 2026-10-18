# Bounded banners and Challenge

Design: [challenge walker design](../superpowers/specs/2026-09-19-challenge-walker-design.md)

This slice implements Combined Rulebook pp. 26 and 32 and New Foundations
pp. 12 and 15 for the fixed first-game banner faces: Mob and Wandering Flame.
The face identities are typed state. The Grand Council and Festival faces have
no implemented power: `RuleSourceIndex` lists their handler ids, and they are
ignored and recorded like any other unimplemented power.

`BannerRules` owns holder and resource lookup, the faceup-only comparison
input (favor, or faceup secrets only), least-stocked favor banks and sites,
and the one-at-a-time favor return. The People's Favor ribbon, Campaign Raid
and Conspiracy share that favor return (`raidFavorReturn`), including the
printed leftmost tied-bank rule. Only Wandering Flame site ties create a
player decision. Nothing derives behaviour from component `rulesText`.

## Challenge

Challenge is a normal Act major action and runs as the `ChallengeProcedure`
walker tree. Its start gates refuse any face other than Mob and Wandering
Flame (`UnsupportedBannerState`) and a start with no legal banner. A banner is
legal when the actor does not hold it, its holder (if any) shares the actor's
site, and the actor has strictly more relevant faceup resources than the
banner holds.

The walk opens with the modifier decision, spends exactly 1 Supply, then asks
for the banner and for a replacement amount strictly greater than the prior
amount. Both answers come before any resource moves. The ribbon then returns
the banner's resources (`ChallengeRibbon`):

- People's Favor returns every favor to the least-favor banks, leftmost first.
- Darkest Secret first gives the previous holder their retained half, then
  places the rest on the least-stocked sites one pass at a time. When fewer
  secrets remain than tied sites, the challenger picks which sites.

Last, the challenger pays the amount onto the banner and takes it by a
required `Take`. Each answer is a recorded walker step and every effect is
recorded core operations, so replay applies them without re-deriving them.

## Banner resources and face powers

Place Banner Resource (`PlaceBannerResourceProcedure`) is a 0-Supply minor
action for a banner's holder. It moves a positive amount of favor or faceup
secrets from the holder's own board onto the banner.

The Mob face is a contribution (`PeoplesFavorMob`): when its holder plays a
card to a site, they may first discard a card there. The Wandering Flame face
has two free, unlimited actions (`WanderingFlameMove`, `WanderingFlamePlace`):
move the pawn to another site with a secret on it, or place a faceup secret on
the pawn's site. `BannerFacePowers` registers them.

## Visibility

Banner faces, holders, and resource totals are public projections. Challenge
decisions, their legal bank and site choices, and the amount bounds are
visible only to the decision owner. Authenticated HTTP intents do not accept a
player identifier; development commands keep the loopback actor selector
check.
