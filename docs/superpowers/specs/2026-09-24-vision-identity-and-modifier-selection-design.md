# Vision Identity and Modifier Selection — Design

**Status:** proposed
**Date:** 2026-09-24
**Supersedes nothing.** Extends `2026-09-19-visions-conspiracy-walker-design.md`
(Vision play) and `2026-09-20-powers-design.md` (modifier ordering).

## Goal

Make five things on the table say what they are: a Vision, the card a
placement question is about, a modifier power, a battle plan, and the dice a
Campaign just rolled. Every one of them is currently described by a string
that names an implementation detail (`denizen.vow-of-peace`,
`cardplay.place.vision.vision:vision-of-conquest`) or by nothing at all.

The companion polish pass (commits `92f9e750..b1ba16f9`) already fixed the
copy-only half of the same batch. What remains needs new payload on the wire
or a change to what a procedure asks, which is why it is specified here.

## Background: what exists today

- A revealed Vision renders as `card-face-denizen` inside the same
  `div.board-cards` as advisers and relics
  (`WorldBoardRenderer.scala:78-84`). Nothing distinguishes it.
- Vision names and rules text are five hardcoded entries in
  `shared/.../VisionCardPresentation.scala`. `VisionDefinition` carries only
  an id (`CatalogModel.scala:137`), and there is no Vision data in the
  catalog JSON.
- Playing a card goes through one generic tree. The placement decision
  `cardplay.place.<kind>.<value>` offers four buttons — Discard, Play at
  site, Play faceup, Play facedown (`CardPlayProcedure.scala:17-35`) — and a
  Vision is rejected later for the site option
  (`CardPlay.scala:136`). Displacing a revealed Vision asks
  `cardplay.replace.*` ("Choose a card to discard") with exactly one forced
  option when the new Vision comes from the temporary hand, and asks nothing
  when it comes from a facedown adviser (`CardPlay.scala:237-266`).
- `DecisionOption.Button` projects `card = None`
  (`WalkerDecisionProjector.scala:282`), so a placement panel has no card to
  draw. A facedown adviser being placed stays in `player.advisers` and never
  enters `temporaryHands`, so the "Cards in hand" preview does not cover it
  either.
- `PreviewModifier(sourceKey, handlerId, description)` is the whole payload
  for a modifier option, and all three route sites set `description =
  handlerId` (`GameRoutes.scala:62`, `AuthenticatedGameRoutes.scala:104`,
  `TrustedGameGateway.scala:54`). The server knows more: `Power.modifier:
  Option[MajorActionType]` (`PowerModel.scala:27-29`), and
  `PhasePowerProjector` already looks printed card names up in the catalog.
- Battle plans are chosen one at a time inside a `Repeat`. Each pass
  dry-runs every remaining offer through the power windows and drops the ones
  that come back `Left` (`CampaignPlanChoice.scala:64-68`); plans apply
  immediately when chosen, and `BattlePlan.sides: Set[CampaignPlanSide]`
  (Attacker/Defender) is never projected.
- Mid-walk roll feedback exists only for Recover:
  `rollOutcome = Option.when(procedure == ActionRef.Recover)(...)`
  (`WalkerDecisionProjector.scala:132-133`), and
  `WalkerRollOutcomeProjection(faces, score, difficulty)` is shaped for it.
  A Campaign's attack roll reaches the client only inside the
  `campaign.sacrifice` heading, as prose built by
  `CampaignBattle.sacrificeHeading` (`CampaignBattle.scala:109-113`).

## 1. Vision card text

Replace the five entries in `VisionCardPresentation` with the printed text.
`RulesTextRenderer` already supports `**bold**`, `_italic_`, bracket glyph
tokens, and blank-line paragraph splits, so the markup below renders as
printed and needs no renderer change.

| Id | Name | Rules text |
|---|---|---|
| `vision:vision-of-conquest` | Vision of Conquest | `Wake: You win if you hold the **most sites** and at least three visions have been drawn from the world deck.` |
| `vision:vision-of-sanctuary` | Vision of Sanctuary | `Wake: You win if you hold the **most relics** and at least three visions have been drawn from the world deck.` |
| `vision:vision-of-rebellion` | Vision of Rebellion | `Wake: You win if you hold the **People's Favor** and at least three visions have been drawn from the world deck.` |
| `vision:vision-of-faith` | Vision of Faith | `Wake: You win if you hold the **Darkest Secret** and at least three visions have been drawn from the world deck.` |
| `vision:conspiracy` | Conspiracy | `If this card is discarded in a raid campaign, return it to the box.`<br>*(blank line)*<br>`WHEN PLAYED: Take a relic or banner from a player whose pawn is at your site. If you take a banner, adjust its [favor]/[secret] as shown by its right ribbon. Return the Conspiracy to the box.` |

`[favor]` and `[secret]` are both in the sprite, so no new glyph is needed.

The new text states the three-Visions gate, which the engine has always
enforced (`VisionVictoryEligibility.scala:8`). It drops the word "uniquely";
the engine keeps requiring a strict, positive unique leader
(`uniquePositiveLeader`), and that stays as it is — the card says "the most",
and ties are not "the most".

**Test:** a shared-module suite asserting each id's name and text, plus one
frontend assertion that Conspiracy renders as two `p.rules-power` blocks with
two glyphs in the second.

## 2. Vision play offers only what is legal

A Vision may be played faceup, held as a facedown adviser, or discarded. It is
never played to a site, so that is not offered.

> **Correction (review, 2026-09-24):** this section first said a Vision may
> only be played faceup or discarded, and Task 2 removed the facedown play
> with the site. That was wrong: a facedown Vision adviser is how an Exile
> holds a Vision to reveal later, and how Conspiracy is later played faceup
> from the Advisers area (`docs/architecture/bounded-visions-and-conspiracy.md`).
> Search is the only way to draw a Vision, so without the facedown play those
> paths were unreachable. Only the site option is filtered now.

- In `CardPlayProcedure`, the placement options for a card whose id is a
  `VisionId` lose `site`: it disappears from the query rather than being
  rejected after the click. `discard`, `adviser-faceup` and
  `adviser-facedown` stay.
- The `cardplay.replace.*` decision is not built for a Vision. Displacing a
  revealed Vision is forced — there is exactly one card it can replace — so
  the walk plans the displacement itself and discards the old Vision to the
  next region, which is what the facedown-adviser origin already does
  (`CardPlay.scala:287`, `:320-322`). `CardPlay.planVision`'s
  `Origin.TemporaryHand` branch stops requiring `replace == expected` and
  derives it instead.

This is Vision-specific by decision: a general "auto-answer any forced
decision" rule would silently change every walker that parks a single-option
choice, and some of those parks exist so the player sees what happened.

**Tests:** `VisionPlaySuite` gains (a) the placement query for a Vision lists
Discard and both adviser plays, and no site; (b) playing a Vision from the
temporary hand over a revealed Vision completes in one answer, with the
displaced Vision in the next region's discard; (c) a Vision answer naming `site` is rejected,
because the option is not declared.

## 3. The player-area slots row

A title and a Vision are not cards in a hand — they are states of the player.
They get their own row.

```
section.player-board
  h3.player-identity        name · role · resources          (title pill moves out)
  div.player-slots          Oathkeeper pill · Vision pill     (new, omitted when empty)
  div.board-cards           advisers, relics                  (Vision removed)
```

- The Oathkeeper/Usurper badge moves from the identity line into
  `div.player-slots`, keeping its `.player-title` styling.
- The revealed Vision renders as a pill (`.player-slot-vision`) carrying the
  Vision's name, and stops rendering as a `CardFace` in `.board-cards`. One
  card, one place.
- Both pills are buttons. Clicking the Vision opens the existing inspection
  overlay with its real `CardDetails`. Clicking the Oathkeeper opens the
  overlay in a new text mode.

### 3.1 A text mode for the overlay

`CardInspection.open` is typed to `CardDetails` and the overlay always draws
a card face first (`CardInspectionOverlay.scala:35-46`). Add a second entry
point rather than faking a card:

```scala
def openText(title: String, lines: Vector[String], origin: dom.html.Element): Unit
```

`CardInspectionOverlay` gains `showText(title, lines, origin)`, which renders
`h3.card-overlay-name` plus one `p.rules-power` per line through
`RulesTextRenderer`, reusing the same open/close/focus-return machinery. The
`CardInspection.onOpen` handler slot becomes a small sealed request type
(`CardRequest` | `TextRequest`) so the shell keeps wiring exactly one handler.

### 3.2 Oath text

`OathkeeperGoal` carries only a key, and no Oath text exists anywhere in the
codebase. Add `OathkeeperPresentation` beside `VisionCardPresentation` in the
shared projection package — the same shape, for the same reason:

| Goal key | Title | Lines |
|---|---|---|
| `supremacy` | Oathkeeper of Supremacy | `Rules the most sites` / `Successor to the Chancellor: Holds more relics` |
| `protection` | Oathkeeper of Protection | `Holds the most relics` / `Successor to the Chancellor: Holds the People's Favor` |
| `devotion` | Oathkeeper of Devotion | `Holds the Darkest Secret` / `Successor to the Chancellor: Holds the Grand Scepter` |
| `the-people` | Oathkeeper of the People | `Holds the People's Favor` / `Successor to the Chancellor: Holds the Darkest Secret` |

`WorldBoardRenderer.oathName` (which derives "Oath of The People" from the
key) is deleted in favour of these printed titles, including in the shared
bank's `p.oathkeeper-status` line.

**Tests:** `PlayerBoardSuite` — the identity line no longer holds the title
pill; `.player-slots` holds the title and, when one is revealed, the Vision;
`.board-cards` holds no Vision. A new `CardInspectionOverlaySuite` case: text
mode renders the title and both lines and draws no `.card-face`.

## 4. Showing the card a decision is about

Add `subjectCards: Vector[CardDetailsProjection] = Vector.empty` to
`WalkerDecisionProjection` (plural, so a decision about several cards is
expressible without another field later).

The projector fills it for `cardplay.place.*` and `cardplay.replace.*` from
the card the tree closed over, subject to the same disclosure rules every
other card projection obeys (`GamePresentationProjector.identifiesCard`) — a
card the viewer may not identify projects hidden, exactly as it does on a
board. The frontend renders them as `CardFace`s above the option buttons in
`renderChooseOnePanel`.

This covers the facedown adviser the player is placing — which is the case
that shows nothing today, because that card sits in `player.advisers` and not
in `temporaryHands` — and it also covers Search's keep-then-place, where it
replaces reliance on the "Cards in hand" preview surviving the step.

The card is **not** moved into `temporaryHands` to achieve this.
`CardPlayProcedure.heldAtOrigin` keys its re-planning off exactly that
membership, so moving the card would change where a card legally is
mid-action in order to fix a rendering gap.

**Tests:** projector — a placement decision projects the card being placed,
and projects it hidden to a viewer who cannot identify it; frontend — the
placement panel draws one `.card-face` above the buttons.

## 5. Modifier options are cards

Extend the preview payload:

```scala
final case class PreviewModifier(sourceKey: String, handlerId: String,
    description: String, card: Option[CardDetailsProjection] = None,
    modifies: Option[String] = None)
```

- `card` is the source card, resolved server-side the way
  `PhasePowerProjector` already resolves a printed name from the catalog.
- `modifies` is the `MajorActionType` key from `Power.modifier`, falling back
  to the handler's `PowerWindow.associatedMajorAction` when the power itself
  declares none.
- All three route sites fill both fields; `description` keeps its current
  meaning as the human sentence and stops being the handler id.

The ordering panel (`ActionDecisionRenderer.scala:99-135`) renders each
option as a `CardFace` with a caption line `"<Action> Modifier"` — "Travel
Modifier", "Campaign Modifier" — and keeps its ordinal badge and
Earlier/Later controls. Options still submit `handlerId`, so nothing about
the answer changes.

**Tests:** codec round-trip including the new fields; a server-route test
asserting a known modifier arrives with its card name and `modifies`; a
frontend test that an option renders a `.card-face` and the caption "Travel
Modifier" instead of `denizen.vow-of-peace`.

## 6. Battle plans: cards and sides, not multi-select

Battle plans stay a one-at-a-time loop. Multi-select was considered and
rejected: pricing is a dry run against the state each previous plan left,
plans apply as they are chosen, and `reveal` flips a facedown adviser faceup —
so a set of individually-affordable plans has no well-defined joint meaning,
and order changes both legality and outcome.

What changes is the presentation of the same loop:

- `DecisionOptionProjection` gains `badge: Option[String] = None`: one short
  label an option carries, for a client to draw as a chip rather than as part
  of a run-together details line. `CampaignPlans.optionOf` sets it from the
  plan's `sides` — `Attack Plan` for attacker-only, `Defense Plan` for
  defender-only, `Battle Plan` for a plan that can be either. `details` keeps
  carrying the price, unchanged.
- The label renders as a pill (`.plan-side-attack`, `.plan-side-defense`,
  `.plan-side-both`) beside the offer.
- Each offer renders as a `CardFace` where the option is card-shaped, with
  its price beneath (`PlanPrice` already words it).
- A `Plans played` strip lists what this battle has already applied, so the
  loop reads as accumulating rather than resetting.

**Tests:** projector — an attacker-only plan projects its side, a computed
both-sides plan projects both; frontend — the offer draws a card, the pill
text is one of the three, and the played strip lists prior picks.

## 7. Rolls during a walk

Generalise the mid-walk roll payload:

```scala
final case class WalkerRollOutcomeProjection(pool: String,
    faces: Vector[String], score: Int, target: Option[Int] = None,
    detail: Vector[String] = Vector.empty)
```

`difficulty: Int` becomes `target: Option[Int]` (Recover has one, a Campaign
does not), `pool` names which roll it is, and `detail` carries already-worded
consequences such as `"1 skull loss"`. The projector stops gating on
`procedure == ActionRef.Recover` and instead projects the pool the parked
decision declares; Recover and Campaign both flow through it.

`campaign.sacrifice` then splits into three parts:

- a glyph die row, drawn by the existing `DieFace.roll`;
- a line `Attack 3 · 1 skull loss`;
- the prompt `Sacrifice up to 2 warbands for one attack each` above the
  dropdown.

`CampaignBattle.sacrificeHeading` loses the dice words and the totals and
keeps only the prompt. `WalkerPanelSupport.rollOutcomeSummary` — the last
place dice are printed as words (`"Rolled hollow sword, one sword"`) — is
replaced by the same glyph row, with the sentence kept as the accessible
name.

The defence roll gets the same treatment wherever it parks a decision.

**Tests:** `CampaignBattleSuite` — the heading is the prompt alone;
projector — a parked `campaign.sacrifice` projects the attack pool's faces
and score; frontend — the sacrifice panel draws a `.die-faces` row and a
separate totals line, and no panel prints a face as a word.

## Out of scope

- Any change to Vision victory rules. The text now matches what the engine
  already does; the engine is not touched.
- Routing battle plans through the major-action preview machinery (§6).
- Moving Vision definitions into the catalog JSON. The presentation map stays
  the single source for Vision names and text until the catalog gains a
  Vision section for its own reasons.
- Bold/italic support, glyph vocabulary, and the sprite: all already present
  and unchanged.

## Risks

- **§3 changes where a badge lives.** `PlayerBoardSuite` pins the identity
  line's contents, and the pane must not scroll; the slots row adds a line
  per player. If the pane overflows at four players, the row collapses to
  share the identity line's flex wrap rather than growing the board.
- **§4, §5, §6 and §7 all widen wire types** (`subjectCards`, two
  `PreviewModifier` fields, `badge`, the roll payload). Every one is additive
  with a default, but the projection codecs validate with `exact(...)` key
  sets, so each added key must be listed in both the encoder and the
  decoder's set or decoding fails on a payload the server now sends. The
  codec round-trip tests are the guard, and each task adds its key in the
  same commit as its field.
- **§7 removes a field** (`difficulty`) rather than adding one. Recover's
  panel reads it; the plan must convert Recover and Campaign in the same
  task, not across two.
