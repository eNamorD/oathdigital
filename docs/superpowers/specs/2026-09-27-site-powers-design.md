# Site Powers

**Status:** designed and built 2026-09-27. Implements the roadmap's cleanup task
"Implement every site power".

## Goal

Every site power the engine can express works as printed. The River is the
one power with no code at all. Homeland works, but differs from its printed
text in two ways. The other powers are already done or wait for a system
that does not exist yet.

## Rulings

The printed powers are on CR p. 31 and repeated on NF p. 11.

| Power | Sites | Printed text, in short | State before this phase | This phase |
|---|---|---|---|---|
| Plains | Dunes, Buried Giant, Salt Flats, Solitary Pillar, Shrouded Woods | "This site has no power." | nothing to do | nothing |
| Coast | Desolate Shore, Fair Isle, Green Shore, Rocky Coast, Sunken Isles, Tidal Marshes | Travel from here to a Coast or Island costs 1 and ignores other modifiers | `TravelSitePowers` | nothing |
| Island | Fair Isle, Sunken Isles | Coast, and +2 Supply unless from a Coast or Island | `TravelSitePowers` | nothing |
| Mountain | Broken Peaks, Hidden Place, Mines, Headwaters | +1 Supply unless a Coast route | `TravelSitePowers` | nothing |
| Pass | Narrow Pass | no travel into or campaign target in the region from outside it, unless the Pass's ruler consents | `TravelSitePowers`, without consent | nothing; consent waits for the Consent system phase |
| **River** | Ancient City, Headwaters, Riverbank, Tidal Marshes | "WAKE: You may place your pawn at another River. This is not a Travel action." | absent | **built** |
| Enduring | Ancient City | cards here are not discarded in the Chronicle Phase's Shape Empire step | absent | deferred: the engine has no Chronicle Phase |
| **Homeland** | Deep Woods, Golden Valley, Great Slum, Painted Towers, Standing Stones, Steppe | "When playing a card of its Homeland suit to this site, you may discard a card from the site first (even one of matching suit)." | a card-play rule, only at a full site, keyed on an edifice | **corrected** |

Rulings this phase makes:

- **River access.** The River is a site power, so the player whose pawn is at
  the River site may use it (the existing `PowerAccess` rule for
  `RuleSourceRef.Site`). The pawn needs to be at the River only when the
  power is used, not at the start of the Wake.
- **River destinations.** Any other River site in play, in any region,
  whoever rules it and whoever is there. The placement is not a Travel
  action: no Supply is paid, no Travel window runs, so no Pass restriction
  and no Travel cost modifier applies.
- **River use limit.** Once per turn per source, the engine's rule for Wake
  powers. A player who uses one River and then stands at another may use the
  second one too; that reaches nothing the first use could not.
- **Homeland without a full site.** The printed text sets no capacity
  condition. At a Homeland of the played card's suit the discard is offered
  whenever the play goes to that site: optional when the site has room,
  required when it is full.
- **Which site is a suit's Homeland.** The site card's own `homeland-<suit>`
  handler decides, not an edifice of that suit standing at the site.

## Design

### River: a Wake phase power sourced by its site

Other phase powers (Horned Mask and Marble Fountains in the Wake, Magic
Carpet in the Act) are `PhasePower`s the player uses through a button; the
River follows them.
A step in the Wake procedure that asks every Wake was rejected: no other
Wake power works that way, and it would ask a question most Wakes do not
need.

- `gameplay/powers/wake/RiverSitePower.scala` defines
  `RiverSitePower(site: SiteId, id: PowerId, catalog)`, a `PhasePower` with
  timing `Wake` and a free cost.
- `RiverSitePower.forCatalog(catalog)` builds one power per reviewed River
  handler present in the catalog, from an explicit list like
  `TravelSitePowers.supported`: `site.ancient-city.river`,
  `site.headwaters.river`, `site.riverbank.river`,
  `site.tidal-marshes.river`. `PhasePowerCatalog.default` registers them.
- `usable` holds when at least one other River site is in play. Access (pawn
  at the site), timing and the per-turn limit are the engine's.
- `build` is a site choice over the other River sites in play, in map order
  (`PawnMoves.siteChoice`), then `BuildOps` running `PawnMoves.relocate`,
  then the note. A River site is a site whose catalog handlers include one
  ending in `.river`.
- `PhasePowerProcedure.sourceRef` maps `RuleSourceRef.Site(id)` to
  `PowerSourceRef.Site(id)` and `DecisionOptionRef.Site(id)`, and
  `sourceOf` maps `DecisionOptionRef.Site(id)` back. Phase powers could not
  come from a site until now. A cost is still refused from a site source, as
  from a banner.

### River's line

The note is "River: {Red} placed at {Ancient City}." It reuses
`PawnMoves.placedKey` with the `used` key, so it is the action's own line.

The log names a site-sourced note by its site today, which reads well for
Narrow Pass ("Narrow Pass: Red cannot target other sites in the region.")
and badly for a power four sites share. A power may now name the source its
notes are written under:

- `NotingPower` gains `def noteSource: Option[String] = None`.
- `NoteWordings` carries each power's `noteSource` beside its templates.
- `PowerLines` writes the named source as text in place of
  `words.source(note.source, ...)` when the power has one.
- `RiverSitePower` names "River". Narrow Pass and the Homeland rule keep
  their site names.

### Projection

- A new `application/SitePowerText` holds each site power kind's name and
  printed text, from the table above.
- `PhasePowerProjector.printed` gains a `PowerSourceRef.Site` case that reads
  it, so the River's button reads "River" with the printed text as its
  title. The frontend already renders one button per projected power and
  needs no change.
- `GamePresentationProjector.sitePower` reads the same table, replacing its
  placeholder descriptions ("Part of the River route." and the rest), so a
  site's details show its printed powers. Enduring and the six Homelands
  gain entries they lack today.

### Homeland in card play

Homeland stays a card-play rule in `CardPlay`; moving it onto a
contribution waits for the roadmap's placement-through-Operations item.

- `CardPlay.homelandSuit(catalog, site): Option[Suit]` reads the site's
  `site.<id>.homeland-<suit>` handler.
- In `legalChoices` and `validateSiteReplacement` a play to a site whose
  Homeland suit is the played card's suit is treated as if the rules had
  `siteDiscardFirst`: the discard is optional with room and required when
  full. A full site of another suit without a power's permission is refused
  as before.
- `CardPlayProcedure` already writes the Homeland rule's line after a
  discard answer when no power permitted the discard. It now does so at any
  matching Homeland, full or not.

### Docs

- `docs/ROADMAP.md`: the task is ticked, naming what was built; Enduring is
  recorded as waiting for a Chronicle Phase; Pass consent stays with the
  Consent system phase.
- `docs/rules/implementation-traceability.md`: the Wake and Travel rows stop
  listing River movement as deferred.
- The power log lines spec's Homeland row drops "full".

## Testing

River:

- Usable in the Wake with the pawn at a River site; the projected button is
  "River".
- Not offered in the Act or Rest, at a non-River site, or when no other River
  site is in play.
- Offers exactly the other River sites in play, in map order.
- Moves the pawn with no Supply spent and no Travel window.
- Refused a second time from the same site in one turn.
- Writes "River: {Red} placed at {site}." as the action's line.
- Replays from the event stream to the same state.

Homeland:

- A matching Homeland with room offers an optional discard; the play is legal
  with or without it, and a discard writes the Homeland line.
- A matching Homeland that is full requires a discard.
- A full site of another suit refuses the play without a power's permission.
- The Homeland suit comes from the site's handler: a site whose handler names
  the suit but holds no edifice of it still offers the discard.

Gates: the server and frontend suites, the architecture check, the Markdown
link check, and the golden Game Logs unchanged.
