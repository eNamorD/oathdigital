# Bounded Visions and Conspiracy

Design: [visions and conspiracy walker design](../superpowers/specs/2026-09-19-visions-conspiracy-walker-design.md)

This slice implements the fixed, unaltered, all-Exile rules from Combined
Rulebook pp. 16-17, 20, 27, and 30, the New Foundations booklet p. 16, and the
printed Vision cards.

## True Visions

The four stable mappings are Conquest to Supremacy, Sanctuary to Protection,
Rebellion to The People, and Faith to Devotion. Conquest and Sanctuary require
a unique leader and a positive holding; banner goals require possession. All
four additionally require at least three Visions (including Conspiracy) drawn
from the world deck. Ties do not qualify: the Oathkeeper holder's tie privilege
belongs to the title, not to Vision victory.

An Exile may reveal a held true Vision during Act for zero Supply, or reveal it
as the kept card of Search. A prior revealed Vision is discarded facedown to
the next region. Victory is evaluated only at the start of that player's Wake,
after the printed Oathkeeper/Usurper step. The title and a revealed Vision may
coexist and neither changes the other.

`VisionVictoryEligibility` is the sole qualification implementation used by
both Wake and War Exhaustion. This prevents unique-leader Wake rules from
drifting into tied-leader War Exhaustion results. War Exhaustion retains the
printed Conquest, Rebellion, Sanctuary, then Faith priority. Every scoped
finished projection is `game-over`, suppresses controls and waiting state,
publishes the winner and victory kind, and retains the inspectable final table.

## Conspiracy

Conspiracy is not a victory Vision. Playing it faceup from a facedown adviser
is a zero-Supply minor action; keeping it faceup from Search plays it the same
way after the Search cost is paid. Either play reaches the card-play hook
`ActionCardPlayedFaceup`, where `ConspiracyWhenPlayed` adds the take. The actor
takes exactly one relic or banner from a co-located enemy when possible. There
is no consent window. With no eligible asset, the mandatory effect does as much
as possible and still returns Conspiracy to the box.

Opponent facedown relics are selected by an opaque owner-qualified slot.
Commands never contain their identity; the server resolves the slot to the
durable relic identity. Public and non-owner projections retain the card back.
Banner transfer applies its printed right ribbon before completion. People's
Favor returns favor deterministically to the least-stocked banks with leftmost
ties. Darkest Secret burns all secrets on the banner (they return to the
untracked shared bank) and transfers the empty banner; no site placement or
return-to-holder occurs on a Conspiracy take. Completion then runs the ordinary
action-boundary Oathkeeper evaluation.

## Boundaries and deferrals

The Conspiracy target is a walker decision. When the take runs, the chosen
target is checked again against the live legal targets
(`ConspiracyUnavailable`). The answer is a recorded walker step and the take
is recorded core operations, so replay applies them without recalculating.

Altered Foundations, Empire-only Vision rules, and the alternate banner faces
(Grand Council, Festival) have no behaviour. `RuleSourceIndex` lists the
handler ids of those faces and of an altered Foundation. An applicable handler
the engine does not support is ignored, not blocking. The procedure that
reaches its timing records it through `IgnoredRulesRecorded`, with its stable
source identity and handler ID.

Three cards forbid a faceup Vision play and one rewards it. Each is a power on
the card-play hook `ActionCardPlayedFaceup`:

- **Vow of Obedience** (`denizen.vow-of-obedience`): its faceup holder cannot
  play Visions faceup, the Conspiracy included. Its REST takes one favor from
  any stocked bank.
- **Secret Police** (`denizen.secret-police`): enemies of its site's ruler
  cannot while their pawn is at a site that ruler rules. Under Bandit rule
  that is every player at a Bandit site. An Empire ruler binds no one yet.
  A player ruler may consent to a faceup play.
- **Sacred Ground** (`edifice.e08.intact`): no player can, except with the
  Conspiracy, unless their pawn is at its site.
- **Book Binders** (`denizen.book-binders`): after another player plays a
  Vision faceup, its holder takes two favor from a bank of their choice.

The first three are `Restriction`s. They bind only while the Vision is still
in the player's temporary hand or among their facedown advisers. The walker's
restriction look-ahead removes the faceup placement from Search and from
facedown-adviser play, which are the only faceup paths, and the answer-time
check refuses a submitted one. Desecrated Ground (`edifice.e08.ruined`) is
not implemented; E08 never appears in a generated game.
