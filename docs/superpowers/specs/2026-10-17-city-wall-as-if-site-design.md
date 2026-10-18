# City Wall reads an as-if Trade site

**Status:** designed 2026-10-17. It covers roadmap item **13c. City Wall
reads an as-if Trade site** under **Phase - Catalog batch 8**. It builds on
slice 7 of [catalog batch 8](2026-10-12-catalog-batch-8-design.md) (Trade
at another site), slice 10 (power-use restrictions) and slice 13 (a queued
Campaign's origin).

## Goal

City Wall (card 252): "While at this site, enemies of City Wall's ruler
cannot place [favor] / [secret] on cards or use non-persistent powers."
Today the site a player is "at" is a queued Campaign's origin, else the
pawn site (`CampaignSetup.originOf`). Small Friends and Friendly Familiar
make a Trader act as if their pawn were at another site, but that site
lives only in the walk's answers (`PowerAccess.asIfSite(answered)`), and
neither `PowerUseRestriction.refusal` nor `OperationRestriction.reason` is
handed them. So City Wall never sees it.

With this design the as-if site is game state for the rest of the walk.
City Wall reads it, and so does every rule that read it from the answers.

## Rulings

Ruled 2026-10-17. They replace the slice 10 line "City Wall's 'while at
this site' reads the physical pawn, not an as-if site."

1. **The as-if site replaces the pawn site.** Once a player acts as if at
   another site, City Wall treats them as at that site only. A player whose
   pawn stands at City Wall's site, and who acts as if elsewhere, is no
   longer walled in for the rest of that Trade. This follows slice 7's
   "the player is not in two places at once" and the way a queued origin
   replaces the pawn site.
2. **When.** The as-if site counts from the site answer to the end of that
   Trade's walk. Before the answer the pawn site counts, so the pick of
   Small Friends or Friendly Familiar, and Friendly Familiar's secret paid
   onto its card, are judged at the pawn site. After the walk the pawn site
   counts again.
3. **Both bans.** The as-if site walls a player in for both of City Wall's
   bans: using a non-persistent power (a later modifier pass, an automatic
   power judged inside the Trade) and placing favor or secrets on a card
   (a use whose cost places on its card, and any placing operation).
4. **Only the walk's actor.** The as-if site is the walk's actor's. Any
   other player judged during that Trade counts from their own pawn site.
5. **Consent is unchanged.** A Trade is outside a Campaign, so a refusal
   City Wall makes there is askable of a player ruler, as in slice 10. No
   new line or note is written.

## Engine shape

- **State.** A new walker-scratch field on `CurrentGameState`,
  `walkerAsIfSite: Option[SiteId]`: the site the walk's actor acts as if
  at. Like `walkerDeferred`, it is cleared where the walker finishes a walk
  (`ProcedureWalker.finish`) and where replay applies `WalkerCompleted`
  (`WalkerReplay`), so "for the rest of the Trade" needs no code of its own.
  It is never projected.
- **Operation.** A new primitive, `ActAsIf(site: SiteId)`, sets the field.
  The journal records it like `Defer`, so a game reloaded from its events
  holds the same site. It writes no Game Log line.
- **Where it runs.** `ActingSite.selectionQuestions` becomes: the site
  `Decide`, then a `BuildOps` that reads the answer from the pending tree
  and emits `ActAsIf(site)`, then the existing `Note`. The pick, its
  payment and the question keep their order.
- **One reader.** `PowerAccess.asIfSite(ready, actor)` returns the field
  when `actor` is `walkActor`, else `None`. `presentSite(ready, actor)`
  becomes the as-if site, else the pawn site.
- **Readers move to state.** `PowerAccess.locate` and `usableSites`,
  `SelectedModifier.selectable` and its overrides, Map Library,
  `EconomySources` (the region and `relocated`) and `ActingSite.offerReason`
  read `asIfSite(ready, actor)`. `asIfSite(answered)` is deleted. An
  `answered` parameter that only carried the as-if site is deleted with
  its plumbing (`ModifierChoice` to `locate`, the `selectable` overrides);
  one that still serves another reader stays. Behaviour is otherwise
  unchanged; the slice 7 suites pin it.
- **City Wall.** `walledIn` reads where the player acts from:
  `PowerAccess.asIfSite(ready, player)`, else `CampaignSetup.originOf`. A
  Trade never has a queued origin, so the order does not matter. Its doc
  drops "does not count yet (ROADMAP 13c)".
- **Layering.** City Wall is a power and may read `PowerAccess`. No engine
  walker or operations file names a power.

### Risk to check at plan time

The modifier loop judges later passes, and the "Start" dry runs, against a
pass's state (`ProcedureWalker.advance`'s `seed`, `ModifierChoice`). Slice
7 handed answers there because the as-if site was in the answers. The plan
must confirm that the state those checks read is the state after the
`ActAsIf` ran, in the live walk and in each dry run. If it is not, the
plan says where that state comes from before any reader moves.

## Testing

In `CityWallSuite`, unless noted:

1. An enemy's pawn stands elsewhere and they act as if at City Wall's site
   with Small Friends. A later non-persistent modifier at that site is
   askable of a player ruler; where nobody may be asked (bandits rule), it
   is unavailable with City Wall's reason.
2. Same position: a modifier whose cost places favor or secrets on its
   card is refused by the placing ban.
3. After that Trade ends, the same player's next action is not walled in.
4. The pawn stands at City Wall's site, the ruler consents to Small
   Friends, and the player acts as if elsewhere. Later modifiers are not
   refused.
5. A player other than the walk's actor, judged during the Trade, still
   counts from their own pawn site.
6. A game parked after the site answer and rebuilt from its events keeps
   the player walled in (a replay test beside the existing walker replay
   tests).

`ActAsIf` gets a model-level test that it sets the field and that
`WalkerCompleted` clears it. The slice 7 suites (`SmallFriendsSuite`,
`FriendlyFamiliarSuite`, Map Library, Skilled Merchants) pass unchanged in
behaviour; only their setup changes where they hand answers that no longer
exist.

## Docs

- The batch 8 design: slice 10's "Enemies" ruling drops the physical-pawn
  sentence and points here; slice 7's as-if bullet says the site is state.
- The batch 8 rulings: City Wall's entry says the as-if Trade site counts.
- `PowerAccess` and `ActingSite` docs say the site is state.
- ROADMAP: 13c checked with a link here; "Only item 14b of catalog batch 8
  remains open."
