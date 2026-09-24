# Vision Identity and Modifier Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make five things on the table say what they are — a Vision, the card a placement question is about, a modifier power, a battle plan, and the dice a Campaign just rolled — instead of naming an implementation detail or nothing at all.

**Architecture:** Every change is additive payload on an existing wire type plus the renderer that draws it, with two exceptions that are deliberate deletions: the Vision's site/facedown placement options (removed from the query rather than rejected after the click) and `WalkerRollOutcomeProjection.difficulty` (generalised to `target`). Nothing new is invented on the client: the card a decision is about, the card a modifier is printed on, and the card a battle plan comes from all travel as the same `CardDetailsProjection` every other card projection uses, so disclosure rules are inherited rather than restated, and all three render through the existing `CardFace`.

**Tech Stack:** Scala 2.13 (engine in `src/`, wire protocol in `shared/`, Scala.js client in `frontend/`), munit, jsdom for frontend DOM suites (`Test / jsEnv := JSDOMNodeJSEnv` in `build.sbt`), ujson for the hand-rolled projection codecs, plain CSS in `frontend/styles.css`. Build wrapper is `./sbtw`.

**Spec:** [docs/superpowers/specs/2026-09-24-vision-identity-and-modifier-selection-design.md](../specs/2026-09-24-vision-identity-and-modifier-selection-design.md)

## Global Constraints

- **The gate is `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.** Run it before every commit. At the time this plan was written it was green at 1656 backend tests and 324 frontend tests.
- **Every added wire field must be listed in BOTH the encoder object and the decoder's `exact(...)` key set**, in the same commit as the field. `ProjectionCodecSupport.exact` rejects any key it was not told about, so a field added to the encoder alone makes the client fail to decode a payload the server now sends. This is the single most likely way to break this plan.
- **Every added wire field takes a default** (`= None`, `= Vector.empty`) so existing construction sites keep compiling.
- **Never render a hidden card's identity.** A `CardDetailsProjection` with `hidden = true` carries `name = "Facedown <kind>"` and nothing else; `CardFace.render` already handles it. No test may assert a real name reaching the DOM from a hidden card.
- **Vision rules text, verbatim.** The five strings in Task 1 are printed card text. Copy them exactly, including `**bold**`, the `[favor]`/`[secret]` tokens, the apostrophe in `People's`, and Conspiracy's blank line. Do not reword, do not re-add the word "uniquely".
- **Oathkeeper titles and lines, verbatim.** The eight strings in Task 3 are printed card text. Copy them exactly.
- **The three battle-plan badges are exactly `Attack Plan`, `Defense Plan`, `Battle Plan`.** No fourth value.
- **Glyph tokens are a closed vocabulary of 17**, listed in `frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala`. `favor` and `secret` are in it. Do not invent a token; `relic` does not exist.
- **The engine's Vision victory rules are out of scope.** `VisionVictoryEligibility` and `uniquePositiveLeader` are not touched by any task here.
- **Do not raise the model or effort level of any subagent above this session's** (project `CLAUDE.md`).
- **Commit messages end with** `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- **Other sessions commit to `main` in this repository.** Run `git log --oneline -1` and confirm HEAD is what you expect before any `--amend`, `reset` or `rebase`. Prefer a new commit.
- **`docs/superpowers/` is tracked** in this repository; this plan and its spec are committed on `main`. Commit any edit you make to the plan.

## File Structure

| File | Responsibility |
| --- | --- |
| `shared/.../projection/VisionCardPresentation.scala` | The five Visions' printed names and rules text (Task 1). |
| `shared/.../projection/OathkeeperPresentation.scala` | **New.** The four Oath goals' printed titles and their two lines (Task 3). |
| `shared/.../projection/ActionProjectionDtos.scala` | `subjectCards`, `badge`, `answeredOptions`, the reshaped `WalkerRollOutcomeProjection` (Tasks 5, 7, 8, 9). |
| `shared/.../projection/ActionProjectionCodec.scala` | Encoder + `exact(...)` decoder sets for all of the above. |
| `shared/.../projection/WorldProjectionCodec.scala` | `encodeCard`/`decodeCard`, widened to `private[protocol]` so the preview codec can reuse them (Task 6). |
| `shared/.../MajorActionPreviewProtocol.scala` | `PreviewModifier.card` and `.modifies`, and their codec (Task 6). |
| `src/.../model/Decisions.scala` | `DecisionOption.Badged` (Task 7). |
| `src/.../model/CampaignTypes.scala` | `CampaignPlanOffer.sides` (Task 7). |
| `src/.../gameplay/actions/cardplay/CardPlayProcedure.scala` | A Vision's placement query drops the site option (Task 2; corrected in review -- see spec §2). |
| `src/.../gameplay/actions/CardPlay.scala` | `planVision` derives the displaced Vision instead of requiring it as an answer (Task 2). |
| `src/.../gameplay/actions/campaign/CampaignPlans.scala` | The badge a plan's sides produce (Task 7). |
| `src/.../gameplay/actions/campaign/CampaignBattle.scala` | `sacrificeHeading` reduced to the prompt (Task 9). |
| `src/.../gameplay/walker/WalkerRollFeedback.scala` | **New.** What roll a parked decision wants shown beside it (Task 9). |
| `src/.../gameplay/walker/WalkerProcedureRegistry.scala` | The per-procedure `rollFeedback` entry (Task 9). |
| `src/.../application/WalkerDecisionProjector.scala` | `subjectCards`, `badge`, `answeredOptions`, generic roll feedback (Tasks 5, 7, 8, 9). |
| `src/.../application/PreviewModifierDescriptions.scala` | **New.** Resolves a preview modifier's card and the action it modifies (Task 6). |
| `frontend/.../CardInspection.scala` | A sealed request type so the overlay can be opened with text as well as a card (Task 3). |
| `frontend/.../CardInspectionOverlay.scala` | `showText` (Task 3). |
| `frontend/.../WorldBoardRenderer.scala` | The `div.player-slots` row; `oathName` deleted (Tasks 3, 4). |
| `frontend/.../WalkerPanelSupport.scala` | Subject cards, the badge pill, the played strip, generic roll feedback (Tasks 5, 7, 8, 9). |
| `frontend/.../WalkerSelectionPanels.scala` | The sacrifice panel's three parts (Task 9). |
| `frontend/.../ActionDecisionRenderer.scala` | Modifier options as card faces (Task 6). |
| `frontend/styles.css` | `.player-slots`, `.plan-side-*`, `.plans-played`, `.walker-roll-*`. |

Tests mirror production one-to-one: `src/test/scala/oathdigital/...`, `shared/src/test/scala/oathdigital/protocol/`, `frontend/src/test/scala/oathdigital/frontend/`.

---

### Task 1: Vision card text

The printed text for the five Visions. Pure data in the shared module; the projector already reads this map (`GamePresentationProjector.scala:336`) and `RulesTextRenderer` already handles the markup, so nothing else changes.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/VisionCardPresentation.scala`
- Create: `shared/src/test/scala/oathdigital/protocol/VisionCardPresentationSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/RulesTextRendererSuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `VisionCardPresentation.byId: Map[String, VisionCardPresentation]` keyed by the five ids `vision:vision-of-conquest`, `vision:vision-of-sanctuary`, `vision:vision-of-rebellion`, `vision:vision-of-faith`, `vision:conspiracy`; each value is `VisionCardPresentation(name: String, rulesText: String)`. Unchanged shape.

- [x] **Step 1: Write the failing shared test**

Create `shared/src/test/scala/oathdigital/protocol/VisionCardPresentationSuite.scala`:

```scala
package oathdigital.protocol

import oathdigital.protocol.projection.VisionCardPresentation

/** The five Visions as they are printed. The text is card text: it is asserted
  * verbatim here so a reword has to be a deliberate edit to this suite.
  */
class VisionCardPresentationSuite extends munit.FunSuite {
  private val gate =
    "and at least three visions have been drawn from the world deck."

  test("each Vision carries its printed name and text") {
    assertEquals(VisionCardPresentation.byId("vision:vision-of-conquest"),
      VisionCardPresentation("Vision of Conquest",
        s"Wake: You win if you hold the **most sites** $gate"))
    assertEquals(VisionCardPresentation.byId("vision:vision-of-sanctuary"),
      VisionCardPresentation("Vision of Sanctuary",
        s"Wake: You win if you hold the **most relics** $gate"))
    assertEquals(VisionCardPresentation.byId("vision:vision-of-rebellion"),
      VisionCardPresentation("Vision of Rebellion",
        s"Wake: You win if you hold the **People's Favor** $gate"))
    assertEquals(VisionCardPresentation.byId("vision:vision-of-faith"),
      VisionCardPresentation("Vision of Faith",
        s"Wake: You win if you hold the **Darkest Secret** $gate"))
  }

  test("Conspiracy is two paragraphs and names both banner resources") {
    val card = VisionCardPresentation.byId("vision:conspiracy")
    assertEquals(card.name, "Conspiracy")
    val paragraphs = card.rulesText.split("\n\n").toVector
    assertEquals(paragraphs.size, 2)
    assertEquals(paragraphs.head,
      "If this card is discarded in a raid campaign, return it to the box.")
    assert(paragraphs(1).startsWith("WHEN PLAYED: Take a relic or banner"))
    assert(paragraphs(1).contains("[favor]/[secret]"))
    assert(paragraphs(1).endsWith("Return the Conspiracy to the box."))
  }

  test("no Vision claims a unique leader -- the card says the most") {
    assert(!VisionCardPresentation.byId.values.exists(
      _.rulesText.contains("uniquely")))
  }
}
```

- [x] **Step 2: Run it to make sure it fails**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.VisionCardPresentationSuite"`
Expected: FAIL — the current text reads "Win if you uniquely rule the most sites, and rule at least one site."

- [x] **Step 3: Replace the five entries**

Rewrite the body of `VisionCardPresentation.byId` in `shared/src/main/scala/oathdigital/protocol/projection/VisionCardPresentation.scala`:

```scala
object VisionCardPresentation {
  /** The gate every true Vision carries. The engine has always enforced it
    * (`VisionVictoryEligibility`); the text now says so.
    */
  private val gate =
    "and at least three visions have been drawn from the world deck."

  val byId: Map[String, VisionCardPresentation] = Vector(
    "vision:vision-of-conquest" -> VisionCardPresentation("Vision of Conquest",
      s"Wake: You win if you hold the **most sites** $gate"),
    "vision:vision-of-sanctuary" -> VisionCardPresentation("Vision of Sanctuary",
      s"Wake: You win if you hold the **most relics** $gate"),
    "vision:vision-of-rebellion" -> VisionCardPresentation("Vision of Rebellion",
      s"Wake: You win if you hold the **People's Favor** $gate"),
    "vision:vision-of-faith" -> VisionCardPresentation("Vision of Faith",
      s"Wake: You win if you hold the **Darkest Secret** $gate"),
    "vision:conspiracy" -> VisionCardPresentation("Conspiracy",
      "If this card is discarded in a raid campaign, return it to the box.\n\n" +
        "WHEN PLAYED: Take a relic or banner from a player whose pawn is at " +
        "your site. If you take a banner, adjust its [favor]/[secret] as " +
        "shown by its right ribbon. Return the Conspiracy to the box.")
  ).toMap
}
```

- [x] **Step 4: Run the shared test to verify it passes**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.VisionCardPresentationSuite"`
Expected: PASS

- [x] **Step 5: Write the failing frontend rendering test**

Append to `frontend/src/test/scala/oathdigital/frontend/RulesTextRendererSuite.scala`, inside the existing class:

```scala
  test("Conspiracy renders as two paragraphs with both banner glyphs") {
    val text = oathdigital.protocol.projection.VisionCardPresentation
      .byId("vision:conspiracy").rulesText
    val blocks = RulesTextRenderer.powers(text)
    assertEquals(blocks.size, 2)
    val glyphs = blocks(1).querySelectorAll(".token-glyph").toVector
    assertEquals(glyphs.size, 2)
    assertEquals(glyphs.map(_.asInstanceOf[dom.Element]
      .getAttribute("aria-label")), Vector("favor", "secret"))
  }
```

If `dom` is not already imported in that file, add `import org.scalajs.dom` at the top.

- [x] **Step 6: Run it**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.RulesTextRendererSuite"`
Expected: PASS. `RulesTextRenderer` already splits on blank lines and renders `[token]` glyphs, so this test is a guard on the new text, not a request for new behaviour. If the glyph `aria-label`s differ from `favor`/`secret`, read `TokenSprite.label` and assert what it actually returns rather than changing the renderer.

- [x] **Step 7: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: all green. `GamePresentationProjectorPrintedFacesSuite` reads `VisionCardPresentation.byId` rather than hardcoding strings, so it follows the new text.

- [x] **Step 8: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/VisionCardPresentation.scala shared/src/test/scala/oathdigital/protocol/VisionCardPresentationSuite.scala frontend/src/test/scala/oathdigital/frontend/RulesTextRendererSuite.scala
```

```bash
git commit -m "fix(visions): print the text the Vision cards carry

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: A Vision offers only the plays that are legal

> **Corrected in review (2026-09-24):** as first written, this task also removed
> `adviser-facedown` for a Vision. That play is legal -- it is how an Exile
> holds a Vision to reveal later -- so only `site` is filtered now. See the
> correction note in spec §2. The steps below are kept as they were executed.

A Vision may be played faceup or discarded. Today the placement query offers four buttons and rejects the illegal two after the click, and displacing a revealed Vision asks a one-option "Choose a card to discard". Both go.

Read `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala:124-200` (`childrenFor`) and `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala:228-270` (`planVision`) before starting.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`
- Test: `src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: the decision `cardplay.place.vision.<id>` now declares exactly two `DecisionOption.Button`s, `discard` ("Discard") and `adviser-faceup` ("Play faceup"), and no `cardplay.replace.vision.<id>` decision is ever built.

- [x] **Step 1: Write the failing tests**

Add to `src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala`. Use the suite's existing fixture helpers for building a `ReadyGame` with a Vision in the actor's temporary hand — read the file first and follow whatever it already does to reach a parked `cardplay.place.*` decision; the three assertions below are what must hold:

```scala
  test("a Vision's placement offers exactly Discard and Play faceup") {
    val decision = parkedPlacement(visionInTemporaryHand)
    assertEquals(decision.query match {
      case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
      case other => fail(s"expected a choose-one, got $other")
    }, Vector(DecisionOptionRef.Button("discard"),
      DecisionOptionRef.Button("adviser-faceup")))
  }

  test("playing a Vision over a revealed one asks no discard question") {
    val state = visionInTemporaryHand(revealed = Some(existingVision))
    val after = answerPlacement(state, DecisionOptionRef.Button("adviser-faceup"))
    assertEquals(walkerIsFinished(after), true)
    assertEquals(revealedVisionOf(after).map(_.id), Some(newVision))
    assert(nextRegionDiscards(after).contains(existingVision))
  }

  test("a Vision answer naming a site is rejected -- it is not declared") {
    val state = visionInTemporaryHand()
    assert(answerPlacementResult(state, DecisionOptionRef.Button("site")).isLeft)
  }
```

- [x] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.VisionPlaySuite"`
Expected: FAIL — the first with four options, the second because the walk parks again on `cardplay.replace.vision.*`.

- [x] **Step 3: Narrow the Vision's placement options**

In `CardPlayProcedure.childrenFor`, filter the candidate placements for a Vision immediately after `candidates` is built:

```scala
      // A Vision is played faceup or discarded; nothing else is legal, so
      // nothing else is offered. Rejecting a site or a facedown play after
      // the click told the player only that a button they were given does
      // not work.
      val offered = if (!card.isInstanceOf[VisionId]) candidates
        else candidates.filter(pair => pair._1 == discard ||
          pair._1 == adviserFaceUp)
      val options = offered.map { case (ref, _, _, _) =>
        DecisionOption.Button(ref, label(ref))
      }
```

Then replace every remaining use of `candidates` inside `childrenFor` with `offered` — there is exactly one, in the `Branch` body:

```scala
        val chosen = if (held) offered.find(pair => ref.contains(pair._1))
        else ref.flatMap(settled(_, card, replaced))
```

- [x] **Step 4: Derive the displaced Vision instead of asking for it**

In `CardPlay.planVision`, the `Origin.TemporaryHand if orientation == Orientation.FaceUp` branch currently requires `replace == expected`. Make the replacement derived, so `legalChoices` reports the placement as directly legal and offers no replacement candidates:

```scala
    case Origin.TemporaryHand if orientation == Orientation.FaceUp =>
      // The displacement is forced: a player has at most one revealed Vision,
      // so there is nothing to choose. An answer that names it is still
      // accepted, because a tree settled from earlier answers replays one.
      val expected = player.revealedVision.map(_.id)
      Either.cond(replace.isEmpty || replace == expected, (),
        InvalidSearchPlacement("a Vision replaces only the revealed Vision"))
        .map { _ =>
          val from = keptSource(origin, player.player)
          PlacementPlan(
            Some(Play(id, from, Location.PlayArea(player.player),
              Orientation.FaceUp, required = true)),
            Vector.empty,
            expected.toVector.map(value => value ->
              PositionedLocation(Location.PlayArea(player.player))),
            Vector.empty)
        }
```

- [x] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.VisionPlaySuite"`
Expected: PASS. If the second test still parks, check `CardPlay.legalChoices`: with the placement now directly legal, `direct` is true and `optional` is false, so `replacements` is `Vector.empty` and `CardPlayProcedure` builds no `Decide` for it.

- [x] **Step 6: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green. Suites that drove a Vision play through the replacement answer must be updated to stop answering it — that is the behaviour change, not a regression.

- [x] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/main/scala/oathdigital/gameplay/actions/CardPlay.scala src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala
```

```bash
git commit -m "fix(visions): offer only the plays a Vision has

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The Oath text, and a text mode for the inspection overlay

`OathkeeperGoal` carries a key and nothing else, and `WorldBoardRenderer.oathName` derives "Oath of The People" from it. Replace that with the printed titles, and give the inspection overlay a second entry point so the Oathkeeper pill in Task 4 has somewhere to open.

**Files:**
- Create: `shared/src/main/scala/oathdigital/protocol/projection/OathkeeperPresentation.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala:88-89`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala` (delete `oathName`, use the printed title in `sharedBank`)
- Create: `shared/src/test/scala/oathdigital/protocol/OathkeeperPresentationSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/CardInspectionOverlaySuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces, for Task 4:
  - `OathkeeperPresentation.byGoal: Map[String, OathkeeperPresentation]` keyed by the goal keys `supremacy`, `protection`, `devotion`, `the-people`; `final case class OathkeeperPresentation(title: String, lines: Vector[String])`.
  - `CardInspection.openText(title: String, lines: Vector[String], origin: dom.html.Element): Unit`
  - `CardInspection.open(card: CardDetails, origin: dom.html.Element): Unit` — unchanged signature.
  - `CardInspectionOverlay.showText(title: String, lines: Vector[String], origin: dom.html.Element): Unit`

- [x] **Step 1: Write the failing shared test**

Create `shared/src/test/scala/oathdigital/protocol/OathkeeperPresentationSuite.scala`:

```scala
package oathdigital.protocol

import oathdigital.protocol.projection.OathkeeperPresentation

/** The Oath as the four goal cards print it: the title, the goal the
  * Oathkeeper holds it by, and the successor clause on the purple band.
  */
class OathkeeperPresentationSuite extends munit.FunSuite {
  test("every goal key carries its printed title and both lines") {
    assertEquals(OathkeeperPresentation.byGoal("supremacy"),
      OathkeeperPresentation("Oathkeeper of Supremacy", Vector(
        "Rules the most sites",
        "Successor to the Chancellor: Holds more relics")))
    assertEquals(OathkeeperPresentation.byGoal("protection"),
      OathkeeperPresentation("Oathkeeper of Protection", Vector(
        "Holds the most relics",
        "Successor to the Chancellor: Holds the People's Favor")))
    assertEquals(OathkeeperPresentation.byGoal("devotion"),
      OathkeeperPresentation("Oathkeeper of Devotion", Vector(
        "Holds the Darkest Secret",
        "Successor to the Chancellor: Holds the Grand Scepter")))
    assertEquals(OathkeeperPresentation.byGoal("the-people"),
      OathkeeperPresentation("Oathkeeper of the People", Vector(
        "Holds the People's Favor",
        "Successor to the Chancellor: Holds the Darkest Secret")))
  }

  test("the map covers every goal the model declares") {
    assertEquals(OathkeeperPresentation.byGoal.keySet,
      oathdigital.model.OathkeeperGoal.all.map(_.key).toSet)
  }
}
```

- [x] **Step 2: Run it to verify it fails**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.OathkeeperPresentationSuite"`
Expected: FAIL — `OathkeeperPresentation` does not exist.

- [x] **Step 3: Write the presentation map**

Create `shared/src/main/scala/oathdigital/protocol/projection/OathkeeperPresentation.scala`:

```scala
package oathdigital.protocol.projection

/** The Oath in play, as its goal card is printed: the title, what the
  * Oathkeeper holds it by, and the successor clause on the card's band.
  *
  * Beside [[VisionCardPresentation]] and for the same reason: `OathkeeperGoal`
  * carries a key, the catalog has no Oath section, and a title derived from
  * the key reads "Oath of The People".
  */
final case class OathkeeperPresentation(title: String, lines: Vector[String])

object OathkeeperPresentation {
  private def successor(clause: String): String =
    s"Successor to the Chancellor: $clause"

  val byGoal: Map[String, OathkeeperPresentation] = Map(
    "supremacy" -> OathkeeperPresentation("Oathkeeper of Supremacy",
      Vector("Rules the most sites", successor("Holds more relics"))),
    "protection" -> OathkeeperPresentation("Oathkeeper of Protection",
      Vector("Holds the most relics", successor("Holds the People's Favor"))),
    "devotion" -> OathkeeperPresentation("Oathkeeper of Devotion",
      Vector("Holds the Darkest Secret", successor("Holds the Grand Scepter"))),
    "the-people" -> OathkeeperPresentation("Oathkeeper of the People",
      Vector("Holds the People's Favor", successor("Holds the Darkest Secret"))))

  /** The printed title, or the raw key for a goal this map does not know --
    * the projection carries a string, so a key is never assumed to be one of
    * the four.
    */
  def title(goal: String): String = byGoal.get(goal).fold(goal)(_.title)
}
```

- [x] **Step 4: Run the shared test to verify it passes**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.OathkeeperPresentationSuite"`
Expected: PASS

- [x] **Step 5: Write the failing overlay test**

Append to `frontend/src/test/scala/oathdigital/frontend/CardInspectionOverlaySuite.scala`, inside the existing class:

```scala
  test("text mode renders a title and its lines and draws no card") {
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.showText("Oathkeeper of Supremacy", Vector(
      "Rules the most sites",
      "Successor to the Chancellor: Holds more relics"), opener)
    assert(overlay.isOpen)
    assertEquals(one(root, ".card-overlay-name").map(_.textContent),
      Some("Oathkeeper of Supremacy"))
    assertEquals(all(root, ".rules-power").map(_.textContent), Vector(
      "Rules the most sites",
      "Successor to the Chancellor: Holds more relics"))
    assertEquals(all(root, ".card-face"), Vector.empty)
  }

  test("text mode closes and returns focus like card mode") {
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.showText("Oathkeeper of Devotion",
      Vector("Holds the Darkest Secret"), opener)
    press(root.querySelector(".card-overlay"), "Escape")
    assert(!overlay.isOpen)
    assertEquals(dom.document.activeElement, opener)
  }
```

If the existing suite drives Escape through a different node or handler, copy whatever the card-mode close test does — the point of this test is that text mode reuses that machinery rather than adding its own.

- [x] **Step 6: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.CardInspectionOverlaySuite"`
Expected: FAIL — `showText` is not a member of `CardInspectionOverlay`.

- [x] **Step 7: Add the text mode**

In `CardInspectionOverlay`, extract the open/focus tail the two modes share and add `showText`:

```scala
  def show(card: CardDetails, origin: dom.html.Element): Unit = {
    clear()
    // Inspection reads a card, so a card the viewer is allowed to read is
    // turned over here. One the viewer cannot identify has nothing to turn.
    body.appendChild(CardFace.render(
      if (card.hidden) card else card.copy(orientation = Some("face-up"))))
    if (!card.hidden) body.appendChild(details(card))
    open(origin)
  }

  /** The overlay over something that is not a card: a title and its printed
    * lines. The Oath is one of these -- it is a state of the game with a
    * printed card behind it, and faking a `CardDetails` for it would put a
    * card face on the table that nothing can be played from.
    */
  def showText(title: String, lines: Vector[String],
      origin: dom.html.Element): Unit = {
    clear()
    val panel = element("div", "card-overlay-details")
    panel.appendChild(text("h3", "card-overlay-name", title))
    lines.foreach(line => RulesTextRenderer.powers(line)
      .foreach(panel.appendChild))
    body.appendChild(panel)
    open(origin)
  }

  private def clear(): Unit =
    while (body.firstChild != null) body.removeChild(body.firstChild)

  private def open(origin: dom.html.Element): Unit = {
    opener = Some(origin)
    node.removeAttribute("hidden")
    close.focus()
  }
```

- [x] **Step 8: Widen the handler slot**

Replace the body of `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom

/** One slot connecting every rendered card to whatever is showing overlays.
  *
  * Cards reach `CardFace.render` through six renderer objects, only three of
  * which carry a `ServerUiView`. Threading a callback through the other three
  * for a single handler is more change than the feature is worth, and
  * `GameTableShell` already owns the other document-level chrome.
  */
private[frontend] object CardInspection {
  /** What the slot is asked to show. Sealed rather than two slots: the shell
    * wires exactly one handler, and a second slot could be left unwired.
    */
  sealed trait Request extends Product with Serializable {
    def origin: dom.html.Element
  }
  object Request {
    final case class Card(card: CardDetails, origin: dom.html.Element)
        extends Request
    final case class Text(title: String, lines: Vector[String],
        origin: dom.html.Element) extends Request
  }

  private var handler = Option.empty[Request => Unit]

  def onOpen(value: Request => Unit): Unit = handler = Some(value)

  def clear(): Unit = handler = None

  def open(card: CardDetails, origin: dom.html.Element): Unit =
    handler.foreach(_(Request.Card(card, origin)))

  def openText(title: String, lines: Vector[String],
      origin: dom.html.Element): Unit =
    handler.foreach(_(Request.Text(title, lines, origin)))
}
```

- [x] **Step 9: Rewire the shell**

Replace `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala:89`:

```scala
  CardInspection.onOpen {
    case CardInspection.Request.Card(card, origin) => inspector.show(card, origin)
    case CardInspection.Request.Text(title, lines, origin) =>
      inspector.showText(title, lines, origin)
  }
```

- [x] **Step 10: Print the Oath's real title in the shared bank**

In `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala`, delete the whole `oathName` definition (and its doc comment) at lines 279-285, and change the shared-bank line to read the printed title:

```scala
   value.oathkeeper.foreach(oath =>
     section.appendChild(text("p", "oathkeeper-status",
       s"${oathdigital.protocol.projection.OathkeeperPresentation.title(oath.goal)} · " +
         s"${oath.side.capitalize}: ${oath.holderPlayerId.getOrElse("unheld")}")))
```

Leave the identity-line `badge.setAttribute("title", ...)` call alone for now — Task 4 moves that badge and replaces the attribute with a click. To keep this task compiling, point it at the same helper:

```scala
         badge.setAttribute("title",
           oathdigital.protocol.projection.OathkeeperPresentation.title(oath.goal))
```

- [x] **Step 11: Run the frontend suites**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.CardInspectionOverlaySuite oathdigital.frontend.PlayerBoardSuite"`
Expected: PASS. If `PlayerBoardSuite` asserted the string `Oath of The People`, change the expectation to `Oathkeeper of the People` — that is the point of this task.

- [x] **Step 12: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green.

- [x] **Step 13: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/OathkeeperPresentation.scala shared/src/test/scala/oathdigital/protocol/OathkeeperPresentationSuite.scala frontend/src/main/scala/oathdigital/frontend/CardInspection.scala frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala frontend/src/test/scala/oathdigital/frontend/CardInspectionOverlaySuite.scala
```

```bash
git commit -m "feat(frontend): name the Oath as its card prints it

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: The player-area slots row

A title and a Vision are states of the player, not cards in a hand. Give them a row of their own between the identity line and `.board-cards`, and stop drawing the Vision as a card among the advisers.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:48-84`
- Modify: `frontend/styles.css`
- Test: `frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala`

**Interfaces:**
- Consumes: `CardInspection.openText(title, lines, origin)` and `CardInspection.open(card, origin)` (Task 3); `OathkeeperPresentation.byGoal` / `.title` (Task 3).
- Produces: DOM only. `div.player-slots` holds zero to two `<button>`s: `.player-title` (unchanged class, moved) and `.player-slot-vision`.

- [x] **Step 1: Write the failing test**

Add to `frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala`. Follow the suite's existing fixture for building a `GameProjection` with a `PlayerBoardProjection`; the assertions are:

```scala
  test("the title and the Vision sit in their own row, not among the cards") {
    val vision = CardDetails("vision:vision-of-faith", "vision",
      "Vision of Faith", orientation = Some("face-up"),
      rulesText = Some("Wake: You win if you hold the **Darkest Secret**."))
    val panel = renderBoards(boardFor("red", revealedVision = Some(vision)),
      oathkeeper = Some(OathkeeperStatus("devotion", Some("red"), "oathkeeper",
        usurperLimited = false, None)))
    assertEquals(panel.querySelectorAll(".player-identity .player-title")
      .toVector, Vector.empty)
    val slots = panel.querySelector(".player-slots")
    assertEquals(slots.querySelectorAll(".player-title").toVector.size, 1)
    assertEquals(slots.querySelector(".player-slot-vision").textContent,
      "Vision of Faith")
    assertEquals(panel.querySelectorAll(".board-cards .card-face").toVector
      .map(_.asInstanceOf[dom.Element].getAttribute("data-card-id"))
      .contains("vision:vision-of-faith"), false)
  }

  test("a player with neither a title nor a Vision has no slots row") {
    val panel = renderBoards(boardFor("blue"), oathkeeper = None)
    assertEquals(panel.querySelectorAll(".player-slots").toVector, Vector.empty)
  }

  test("the title pill opens the Oath text and the Vision pill its card") {
    var opened = Vector.empty[String]
    CardInspection.onOpen {
      case CardInspection.Request.Text(title, _, _) => opened = opened :+ title
      case CardInspection.Request.Card(card, _) => opened = opened :+ card.name
    }
    val vision = CardDetails("vision:vision-of-faith", "vision",
      "Vision of Faith", orientation = Some("face-up"))
    val panel = renderBoards(boardFor("red", revealedVision = Some(vision)),
      oathkeeper = Some(OathkeeperStatus("devotion", Some("red"), "oathkeeper",
        usurperLimited = false, None)))
    panel.querySelector(".player-slots .player-title")
      .asInstanceOf[dom.html.Button].click()
    panel.querySelector(".player-slot-vision")
      .asInstanceOf[dom.html.Button].click()
    CardInspection.clear()
    assertEquals(opened, Vector("Oathkeeper of Devotion", "Vision of Faith"))
  }
```

The `data-card-id` attribute in the third assertion is what `CardFace.render` writes; if it writes a different attribute, read `CardFace.scala` and assert on that one instead.

- [x] **Step 2: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.PlayerBoardSuite"`
Expected: FAIL — there is no `.player-slots` element.

- [x] **Step 3: Build the slots row**

In `WorldBoardRenderer`, replace the badge block inside the identity line (lines 52-60) and the `revealedVision` line in `.board-cards` (lines 82-83). The title moves down, the Vision becomes a pill, and the row is only appended when it has something in it:

```scala
     section.appendChild(identity)
     value.playerBoards.find(_.playerId == player.playerId).foreach { board =>
     // ... resources block unchanged ...

     // A title and a Vision are states of the player, not cards in a hand:
     // in the physical game a Vision is played sideways, which is what this
     // row stands in for.
     val slots = element("div", "player-slots")
     value.oathkeeper.filter(_.holderPlayerId.contains(player.playerId))
       .foreach { oath =>
         val presented = oathdigital.protocol.projection
           .OathkeeperPresentation.byGoal.get(oath.goal)
         val pill = button(oath.side.capitalize, s"player-title title-${oath.side}")
         val title = presented.fold(oath.goal)(_.title)
         pill.setAttribute("title", title)
         pill.onclick = _ => CardInspection.openText(title,
           presented.fold(Vector.empty[String])(_.lines), pill)
         slots.appendChild(pill)
       }
     board.revealedVision.foreach { card =>
       val pill = button(card.name, "player-slot-vision")
       pill.onclick = _ => CardInspection.open(card, pill)
       slots.appendChild(pill)
     }
     if (slots.childNodes.length > 0) section.appendChild(slots)

     val cards = element("div", "board-cards")
     cards.setAttribute("aria-label", "Cards in play")
     board.advisers.foreach(card => cards.appendChild(CardFace.render(card)))
     board.relics.foreach(card => cards.appendChild(CardFace.render(card)))
     section.appendChild(cards)
```

`button` is already imported from `ServerUiSupport` in this file. A hidden Vision cannot reach here — `revealedVision` is projected only when it is face up — but if `card.hidden` is somehow true the pill would print `Facedown vision`, which is correct rather than disclosing.

- [x] **Step 4: Style the row**

Append to `frontend/styles.css`, next to the existing `.player-title` rules:

```css
/* A title and a Vision are states of the player, so they get a row of their
   own between the identity line and the cards. It wraps rather than growing
   the board: the player strip does not scroll. */
.player-slots {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4em;
  margin: 0.2em 0; }
.player-slot-vision {
  border: 1px solid var(--pill-border, currentColor);
  border-radius: 1em;
  padding: 0.1em 0.7em;
  font-size: 0.85em;
  background: none;
  color: inherit;
  cursor: pointer; }
```

- [x] **Step 5: Run the test to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.PlayerBoardSuite"`
Expected: PASS

- [x] **Step 6: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green. `SiteBoxLayoutSuite` and `PanelPlacementSuite` may pin the player strip's structure; update expectations to include the new row rather than removing it.

- [x] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala
```

```bash
git commit -m "feat(frontend): give the title and the Vision their own row

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Show the card a placement decision is about

`DecisionOption.Button` projects `card = None`, so a placement panel has no card to draw, and a facedown adviser being placed never enters `temporaryHands` so the "Cards in hand" preview does not cover it either. Add the subject to the decision.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala` (`WalkerDecisionProjection`)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala` (`encodeWalkerDecision`/`decodeWalkerDecision`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala` (`renderChooseOnePanel`)
- Test: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`
- Test: `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `WalkerDecisionProjection.subjectCards: Vector[CardDetailsProjection] = Vector.empty`, wire key `"subjectCards"`, for Tasks 7 and 8 which add further keys to the same two `exact(...)` sets.

- [x] **Step 1: Write the failing codec test**

Add to `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, following its existing round-trip style:

```scala
  test("a walker decision round-trips the cards it is about") {
    val card = CardDetailsProjection("d1", "denizen", "Old Oak",
      orientation = Some("face-down"))
    val decision = WalkerDecisionProjection("search", "cardplay.place.denizen.d1",
      "decide", subjectCards = Vector(card))
    val json = ujson.write(ActionProjectionCodec.encodeWalkerDecision(decision))
    assertEquals(ActionProjectionCodec.decodeWalkerDecision(
      ujson.read(json), "$"), Right(decision))
  }
```

Match the suite's existing imports and its way of reaching `ActionProjectionCodec` — if the codec object is `private[projection]`, add this test beside the existing walker-decision round-trip in whatever file already reaches it rather than widening visibility.

- [x] **Step 2: Run it to verify it fails**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: FAIL — `subjectCards` is not a member of `WalkerDecisionProjection`.

- [x] **Step 3: Add the field and its codec keys**

In `ActionProjectionDtos.scala`, extend `WalkerDecisionProjection` and document it:

```scala
final case class WalkerDecisionProjection(
    action: String,
    decisionId: String,
    kind: String,
    pool: Option[String] = None,
    count: Option[Int] = None,
    query: Option[DecisionQueryProjection] = None,
    rollOutcome: Option[WalkerRollOutcomeProjection] = None,
    /** The cards this decision is ABOUT, as opposed to the cards its options
      * name: the card being placed by a `cardplay.place.*` question, which is
      * neither an option nor in the temporary hand. Plural so a decision about
      * several cards needs no second field. Projected under the same
      * disclosure rules as every other card, so a viewer who may not identify
      * one receives it hidden.
      */
    subjectCards: Vector[CardDetailsProjection] = Vector.empty
)
```

In `ActionProjectionCodec.scala`, add the key to both sides:

```scala
  def encodeWalkerDecision(value: WalkerDecisionProjection): ujson.Value = ujson.Obj(
    "action" -> value.action, "decisionId" -> value.decisionId, "kind" -> value.kind,
    "pool" -> stringOption(value.pool), "count" -> intOption(value.count),
    "query" -> option(value.query)(encodeDecisionQuery),
    "rollOutcome" -> option(value.rollOutcome)(encodeRollOutcome),
    "subjectCards" -> encoded(value.subjectCards)(encodeCard))
  def decodeWalkerDecision(raw: ujson.Value, path: String): Result[WalkerDecisionProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("action", "decisionId", "kind", "pool", "count",
      "query", "rollOutcome", "subjectCards"), path)
    action <- string(value, "action", path); decision <- string(value, "decisionId", path)
    kind <- string(value, "kind", path); pool <- optionalString(value, "pool", path)
    count <- optionalInt(value, "count", path)
    query <- optionalAbsent(value, "query", path)(decodeDecisionQuery)
    rollOutcome <- optionalAbsent(value, "rollOutcome", path)(decodeRollOutcome)
    subjectRaws <- array(value, "subjectCards", path)
    subjects <- traverse(subjectRaws, s"$path.subjectCards")(decodeCard)
  } yield WalkerDecisionProjection(action, decision, kind, pool, count, query,
    rollOutcome, subjects)
```

`array`/`traverse`/`encoded` are the helpers `decodeMinorActions` already uses in this file (`ActionProjectionCodec.scala:114-115`); the encoder always writes the key, so `array` (which requires it) is correct rather than a defaulting read.

- [x] **Step 4: Run the codec test to verify it passes**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS

- [x] **Step 5: Write the failing projector test**

Add to `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`, using the suite's existing fixture for a parked `PlayFacedownAdviser` walk:

```scala
  test("a placement decision projects the card being placed") {
    val projected = project(facedownAdviserPlacement, viewer = Some(owner))
    assertEquals(projected.map(_.decisionId),
      Some("cardplay.place.denizen.denizen:vow-of-peace"))
    assertEquals(projected.toVector.flatMap(_.subjectCards).map(_.cardId),
      Vector("denizen:vow-of-peace"))
  }

  test("a viewer who cannot identify the placed card gets it hidden") {
    val projected = project(facedownAdviserPlacement, viewer = Some(opponent))
    assertEquals(projected.toVector.flatMap(_.subjectCards).map(_.hidden),
      Vector(true))
  }
```

The second case reaches the projector only if the opponent is a co-owner of the decision; if the suite has no such fixture, drop that test and instead assert `subjectCards` is empty for a decision id that is not a `cardplay.` one:

```scala
  test("a decision that is about no card projects no subject") {
    assertEquals(project(recoverRelicPick, viewer = Some(owner))
      .toVector.flatMap(_.subjectCards), Vector.empty)
  }
```

- [x] **Step 6: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: FAIL — `subjectCards` is empty.

- [x] **Step 7: Fill it in the projector**

In `WalkerDecisionProjector.parked`, the `case None =>` branch, pass the subjects alongside the query:

```scala
          queryProjection(ready, viewer, query, details).map(projected =>
            WalkerDecisionProjection(procedure.key, decide.decisionId, "decide",
              query = Some(projected),
              rollOutcome = Option.when(procedure == ActionRef.Recover)(
                rollOutcome(ready, awaited)).flatten,
              subjectCards = subjectCards(ready, viewer, decide.decisionId)))
```

and add the resolver next to `card`:

```scala
  /** The cards a decision is about, read from its own id.
    *
    * A card-play decision is the one question in the walker whose subject is
    * not among its options: `cardplay.place.*` offers four buttons and names
    * the card only in the id it was built with. The id is the tree's own
    * spelling of `WorldCardId` (`kind` then `value`), so this parses what the
    * procedure wrote rather than reaching into the tree for it.
    *
    * A card that cannot be found, or that this viewer may not identify,
    * projects hidden rather than being dropped: unlike an option, a subject
    * carries no reference the client answers with, so showing a back is both
    * honest and useful.
    */
  private def subjectCards(ready: ReadyGame, viewer: Option[PlayerId],
      decisionId: String): Vector[CardDetailsProjection] = {
    val index = CardIndex.from(ready.game).toOption
    val prefixes = Vector("cardplay.place.", "cardplay.replace.")
    prefixes.find(decisionId.startsWith).toVector.flatMap { prefix =>
      decisionId.stripPrefix(prefix).split("\\.", 2).toVector match {
        case Vector("denizen", value) => Vector(DenizenId(value): CardId)
        case Vector("vision", value) => Vector(VisionId(value): CardId)
        case _ => Vector.empty[CardId]
      }
    }.flatMap { id =>
      index.flatMap(_.get(id)).toVector.map { located =>
        val orientation = orientationOf(located.state)
        presentation.cardDetails(id, orientation, hidden = !presentation
          .identifiesCard(ready, viewer, id, orientation,
            located.location.container))
      }
    }
  }
```

Confirm the id spelling before writing the parse: `CardPlayProcedure` builds `s"cardplay.place.${card.kind}.${card.value}"`, and `DenizenId.kind`/`VisionId.kind` are `"denizen"` and `"vision"`. If a `WorldCardId.value` can itself contain a dot, the `split("\\.", 2)` above is already correct because it splits once.

- [x] **Step 8: Run the projector test**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS

- [x] **Step 9: Write the failing frontend test**

Add to `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`:

```scala
  test("the card a placement is about is drawn above the buttons") {
    val subject = CardDetails("denizen:vow-of-peace", "denizen", "Vow of Peace",
      orientation = Some("face-down"))
    val place = WalkerDecisionState("play-facedown-adviser",
      "cardplay.place.denizen.denizen:vow-of-peace", "decide",
      query = Some(DecisionQueryState("choose-one", Vector(
        DecisionOptionState("button", "discard", "Discard"),
        DecisionOptionState("button", "adviser-faceup", "Play faceup")),
        heading = Some("Play or discard card"))),
      subjectCards = Vector(subject))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(place))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".decision-subject .card-face").size, 1)
    assertEquals(all(panel, ".walker-choice").map(_.textContent),
      Vector("Discard", "Play faceup"))
  }
```

- [x] **Step 10: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: FAIL — no `.decision-subject` element.

- [x] **Step 11: Draw the subjects**

In `WalkerPanelSupport.renderChooseOnePanel`, between the heading and the options:

```scala
        panel.appendChild(text("h2", "", decisionHeading(query)))
        // The card the question is about. A placement asks about a card that
        // is neither an option nor in the temporary hand, so without this the
        // player answers about a card they cannot see.
        if (decision.subjectCards.nonEmpty) {
          val subjects = element("div", "decision-subject")
          decision.subjectCards.foreach(card =>
            subjects.appendChild(CardFace.render(card)))
          panel.appendChild(subjects)
        }
        query.options.foreach { option =>
```

Add to `frontend/styles.css`, beside `.decision-cards`:

```css
/* The card a decision is about, above the answers. */
.decision-subject {
  display: flex;
  gap: 0.4em;
  margin-bottom: 0.4em; }
```

- [x] **Step 12: Run the frontend test**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: PASS

- [x] **Step 13: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green.

- [x] **Step 14: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala
```

```bash
git commit -m "feat(walker): show the card a placement question is about

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Modifier options are cards

All three route sites build `PreviewModifier(sourceKey, handlerId, handlerId)`, so the ordering panel offers a button reading `denizen.vow-of-peace`. The server knows the card and the action the power modifies; send both.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/MajorActionPreviewProtocol.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala` (visibility)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ProjectionCodecSupport.scala` (visibility)
- Create: `src/main/scala/oathdigital/application/PreviewModifierDescriptions.scala`
- Modify: `src/main/scala/oathdigital/application/GameApplicationService.scala`
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala:61-63`
- Modify: `src/main/scala/oathdigital/server/AuthenticatedGameRoutes.scala:102-104`
- Modify: `src/main/scala/oathdigital/server/TrustedGameGateway.scala:54`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:99-135`
- Test: `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `PreviewModifier(sourceKey: String, handlerId: String, description: String, card: Option[CardDetailsProjection] = None, modifies: Option[String] = None)`
  - `MajorActionPreviewAccepted.modifiers: Vector[PreviewModifier] = Vector.empty` — what all three routes now send instead of mapping `options` themselves.

- [x] **Step 1: Write the failing codec test**

Add to `shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala`, beside the existing preview round-trip at line 182:

```scala
  test("a preview modifier round-trips its card and the action it modifies") {
    val card = oathdigital.protocol.projection.CardDetailsProjection(
      "denizen:vow-of-peace", "denizen", "Vow of Peace",
      suit = Some("order"), orientation = Some("face-up"))
    val response = MajorActionPreviewResponse(4L, "travel",
      Vector(PreviewModifier("adviser:p1:denizen:vow-of-peace",
        "denizen.vow-of-peace", "Vow of Peace", Some(card), Some("travel"))),
      Vector.empty, Vector.empty)
    val json = MajorActionPreviewCodec.encodeResponse(response)
    assertEquals(MajorActionPreviewCodec.decodeResponse(json), Right(response))
  }
```

- [x] **Step 2: Run it to verify it fails**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.CommandProtocolSuite"`
Expected: FAIL — `PreviewModifier` takes three arguments.

- [x] **Step 3: Widen the card codec's visibility**

The preview codec lives in `oathdigital.protocol` and the card codec in `oathdigital.protocol.projection`; both already fail with `ProtocolDecodeFailure`, so only visibility is in the way. Change two declarations:

- `shared/.../projection/ProjectionCodecSupport.scala:6`: `private[projection] object ProjectionCodecSupport` → `private[protocol] object ProjectionCodecSupport`
- `shared/.../projection/WorldProjectionCodec.scala:5`: `private[projection] object WorldProjectionCodec` → `private[protocol] object WorldProjectionCodec`

- [x] **Step 4: Extend the payload and its codec**

In `MajorActionPreviewProtocol.scala`:

```scala
import oathdigital.protocol.projection.CardDetailsProjection

/** One offered modifier. `description` is the human sentence for the option --
  * the printed card's name where there is a card -- and never the handler id.
  * `card` is the card the power is printed on, absent for a power with no card
  * (a banner face, a game rule). `modifies` is the major action's key, from
  * the power's own declaration.
  */
final case class PreviewModifier(sourceKey: String, handlerId: String,
    description: String, card: Option[CardDetailsProjection] = None,
    modifies: Option[String] = None)
```

In `encodeResponse`, the modifiers block becomes:

```scala
    "modifiers" -> ujson.Arr.from(value.modifiers.map(v => ujson.Obj(
      "sourceKey" -> v.sourceKey, "handlerId" -> v.handlerId,
      "description" -> v.description,
      "card" -> v.card.fold[ujson.Value](ujson.Null)(
        oathdigital.protocol.projection.WorldProjectionCodec.encodeCard),
      "modifies" -> v.modifies.fold[ujson.Value](ujson.Null)(ujson.Str(_))))),
```

and in `decodeResponse`:

```scala
        modifiers <- array(root, "modifiers", "$.modifiers") { (value, path) => for {
          source <- nonEmpty(value, "sourceKey", s"$path.sourceKey")
          handler <- nonEmpty(value, "handlerId", s"$path.handlerId")
          description <- nonEmpty(value, "description", s"$path.description")
          card <- value.value.get("card") match {
            case None | Some(ujson.Null) => Right(None)
            case Some(raw) => oathdigital.protocol.projection
              .WorldProjectionCodec.decodeCard(raw, s"$path.card").map(Some(_))
          }
          modifies <- value.value.get("modifies") match {
            case None | Some(ujson.Null) => Right(None)
            case Some(ujson.Str(text)) => Right(Some(text))
            case Some(_) => Left(InvalidValue(s"$path.modifies", "expected string"))
          }
        } yield PreviewModifier(source, handler, description, card, modifies) }
```

This codec is hand-rolled and does not use `exact(...)`, so no key set needs updating here — but `decodeCard` does use it, which is why the card must be encoded by `encodeCard` and not by hand.

- [x] **Step 5: Run the codec test**

Run: `./sbtw "sharedJVM/testOnly oathdigital.protocol.CommandProtocolSuite"`
Expected: PASS

- [x] **Step 6: Describe the modifiers server-side**

Create `src/main/scala/oathdigital/application/PreviewModifierDescriptions.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.powerresolver.Power
import oathdigital.model._
import oathdigital.protocol.PreviewModifier

/** What an offered modifier is, for the panel that orders them: the card it is
  * printed on and the major action it modifies.
  *
  * The route sites used to send `description = handlerId`, so the panel drew a
  * button reading `denizen.vow-of-peace`. Everything needed to do better is
  * already in hand at preview time -- the rule source names a card and the
  * power declares its `modifier` -- so it is resolved here, once, rather than
  * three times at three route sites.
  */
private[application] final class PreviewModifierDescriptions(
    presentation: GamePresentationProjector) {

  def describe(ready: ReadyGame, powers: Vector[Power],
      options: Vector[OrderedRuleInvocation]): Vector[PreviewModifier] =
    options.map { option =>
      val power = powers.find(_.id.value == option.handlerId)
      val card = cardOf(option.source).map(id =>
        presentation.cardDetails(id, Some(Orientation.FaceUp), hidden = false))
      PreviewModifier(option.source.stableKey, option.handlerId,
        card.map(_.name).getOrElse(option.handlerId), card,
        modifies(power).map(_.key))
    }

  /** The power's own declaration, falling back to the major action its
    * handlers' windows are associated with. `Power.validate` already requires
    * the two to agree when both exist, so the fallback can never contradict
    * the declaration.
    */
  private def modifies(power: Option[Power]): Option[MajorActionType] =
    power.flatMap(value => value.modifier.orElse(
      value.handlers.flatMap(_.window.associatedMajorAction).headOption))

  /** The card a rule source is printed on. A banner face, a foundation and a
    * game rule have none.
    */
  private def cardOf(source: RuleSourceRef): Option[CardId] = source match {
    case RuleSourceRef.Adviser(_, id) => Some(id)
    case RuleSourceRef.SiteCard(_, id) => Some(id)
    case RuleSourceRef.Relic(_, id) => Some(id)
    case RuleSourceRef.SiteRelic(_, id) => Some(id)
    case RuleSourceRef.Edifice(_, id) => Some(id)
    case _ => None
  }
}
```

If `presentation.cardDetails` is not visible from this package, mirror the accessor `WalkerDecisionProjector` uses (`GamePresentationProjector.cardDetails`) — both classes are in `oathdigital.application`, so it already is.

- [x] **Step 7: Carry the descriptions through the preview result**

In `GameApplicationService.scala`, add the field to the result type:

```scala
final case class MajorActionPreviewAccepted(loaded: LoadedGame,
    options: Vector[OrderedRuleInvocation],
    ignored: Vector[IgnoredRuleDiagnostic],
    targets: Vector[PreviewTarget] = Vector.empty,
    modifiers: Vector[PreviewModifier] = Vector.empty)
```

and fill it in `preview`, in both branches, after `acceptPreview` has returned:

```scala
          case Some(actionRef) => for {
            offerable <- rules.offerableWalkerPowers(ready, actor, actionRef)
              .left.map(CommandRejected)
            options = offerable.map(power =>
              OrderedRuleInvocation(power.source, power.id.value))
            accepted <- acceptPreview(loaded, options, selected, Vector.empty,
              walkerTargets(actionRef, ready, actor, selected))
          } yield accepted.copy(modifiers = descriptions.describe(ready,
            offerable, accepted.options))
          case None => for {
            options <- PowerRuntime.options(catalog, ready, actor, action)
              .left.map(CommandRejected)
            ignored <- PowerRuntime.ignored(catalog, ready, actor, action)
              .left.map(CommandRejected)
            accepted <- acceptPreview(loaded, options, selected, ignored)
          } yield accepted.copy(modifiers = descriptions.describe(ready,
            Vector.empty, accepted.options))
```

`descriptions` is a `private val descriptions = new PreviewModifierDescriptions(presentation)` on the service, built from whatever `GamePresentationProjector` the service already holds; if it holds none, construct one the same way the projector it already owns does. The legacy branch passes no powers, so a legacy modifier gets its card and name but no `modifies` — that branch serves actions that are not on the walker, and the frontend falls back to the previewed action's own label.

- [x] **Step 8: Send the described modifiers from all three routes**

Replace the `accepted.options.map(v => PreviewModifier(...))` expression with `accepted.modifiers` at:

- `src/main/scala/oathdigital/server/GameRoutes.scala:62-63`
- `src/main/scala/oathdigital/server/AuthenticatedGameRoutes.scala:102-104`
- `src/main/scala/oathdigital/server/TrustedGameGateway.scala:54`

Then delete the now-unused `PreviewModifier` import from any of those three files that no longer names the type.

- [x] **Step 9: Run the backend suites**

Run: `./sbtw "test"`
Expected: green. A route suite asserting `description == handlerId` must be updated to assert the card's name — that is the change.

- [x] **Step 10: Write the failing frontend test**

Add to `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala` (or the suite that renders the ordering panel, if that is a different file — search for `modifier-toggle` in `frontend/src/test`):

```scala
  test("a modifier option draws its card and names the action it modifies") {
    val card = CardDetails("denizen:vow-of-peace", "denizen", "Vow of Peace",
      orientation = Some("face-up"))
    val modifier = PreviewModifier("adviser:p1:denizen:vow-of-peace",
      "denizen.vow-of-peace", "Vow of Peace", Some(card), Some("travel"))
    val panel = renderOrderingPanel(Vector(modifier))
    assertEquals(panel.querySelectorAll(".modifier-option .card-face")
      .toVector.size, 1)
    assertEquals(panel.querySelector(".modifier-modifies").textContent,
      "Travel Modifier")
    assertEquals(panel.textContent.contains("denizen.vow-of-peace"), false)
  }
```

`renderOrderingPanel` is whatever fixture the suite already uses to reach the `currentModifierWorkflow.exists(_.ordering)` branch of `ActionDecisionRenderer`; reuse it rather than writing a new one.

- [x] **Step 11: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierSelectionStateSuite"`
Expected: FAIL — the option is a plain button labelled with the handler id.

- [x] **Step 12: Render the option as a card**

In `ActionDecisionRenderer`, inside `workflow.selection.candidates.foreach`, replace the `choose` button's construction so the card and caption go inside it and the ordinal prefix stays:

```scala
         val row = element("div", "modifier-option")
         val ordinal = workflow.selection.ordinal(modifier)
         val choose = button(ordinal.fold(modifier.description)(n =>
           s"$n. ${modifier.description}"), "modifier-toggle")
         choose.setAttribute("aria-pressed", ordinal.nonEmpty.toString); choose
           .setAttribute("data-source-key", modifier.sourceKey)
         // The card, then what it modifies. The description stays as the
         // control's accessible name so a reader who cannot see the face
         // still hears which power this is.
         modifier.card.foreach { card =>
           choose.setAttribute("aria-label", ordinal.fold(modifier.description)(
             n => s"$n. ${modifier.description}"))
           choose.textContent = ""
           ordinal.foreach(n => choose.appendChild(
             text("span", "modifier-ordinal-prefix", s"$n.")))
           choose.appendChild(CardFace.render(card))
           choose.appendChild(text("span", "modifier-modifies",
             s"${actionLabel(modifier.modifies.getOrElse(workflow.preview.action))} Modifier"))
         }
```

`actionLabel` already maps `"travel"` to `"Travel"` (`ServerUiSupport.scala:272`) and returns the key unchanged for anything else; add the remaining major actions to it if the caption reads `campaign Modifier` in the gate run:

```scala
  private[frontend] def actionLabel(kind: String): String = kind match {
    case "travel" => "Travel"
    case "challenge" => "Challenge"
    case "peoples-favor" => "People's Favor"
    case "darkest-secret" => "Darkest Secret"
    case "search" => "Search"
    case "campaign" => "Campaign"
    case "muster" => "Muster"
    case "trade" => "Trade"
    case "forge" => "Forge"
    case "recover" => "Recover"
    case other => other
  }
```

Add to `frontend/styles.css`:

```css
/* A modifier option is the card it is printed on, with the action it
   modifies under it. */
.modifier-toggle .card-face { pointer-events: none; }
.modifier-modifies {
  display: block;
  font-size: 0.8em;
  opacity: 0.85; }
```

`pointer-events: none` on the nested face is what keeps a click on the card toggling the modifier instead of opening the inspector — the ordering panel is a selection, not a reading surface.

- [x] **Step 13: Run the frontend test**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierSelectionStateSuite"`
Expected: PASS

- [x] **Step 14: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green.

- [x] **Step 15: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/MajorActionPreviewProtocol.scala shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala shared/src/main/scala/oathdigital/protocol/projection/ProjectionCodecSupport.scala shared/src/test/scala/oathdigital/protocol/CommandProtocolSuite.scala src/main/scala/oathdigital/application/PreviewModifierDescriptions.scala src/main/scala/oathdigital/application/GameApplicationService.scala src/main/scala/oathdigital/server frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala
```

```bash
git commit -m "feat(powers): offer a modifier as its card and the action it changes

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Battle plans carry their side

Battle plans stay a one-at-a-time loop — pricing is a dry run against the state each previous plan left, and plans apply as they are chosen, so a set of individually-affordable plans has no joint meaning. What changes is that an offer says which side it is for and the option draws as a card.

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala` (`CampaignPlanOffer`)
- Modify: `src/main/scala/oathdigital/model/Decisions.scala` (`DecisionOption.Badged`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (`optionProjection`)
- Modify: `shared/.../projection/ActionProjectionDtos.scala` + `ActionProjectionCodec.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala`
- Modify: `frontend/styles.css`
- Test: `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`

**Interfaces:**
- Consumes: `WalkerDecisionProjection.subjectCards` exists (Task 5) — this task adds a key to the same `exact(...)` sets, so rebase on it.
- Produces:
  - `CampaignPlanOffer(source, label, costs, effects, sides: Set[CampaignPlanSide] = Set.empty)`
  - `DecisionOption.Badged(option: DecisionOption, badge: String)` with `ref = option.ref`
  - `CampaignPlans.badgeOf(sides: Set[CampaignPlanSide]): Option[String]` returning exactly `Some("Attack Plan")`, `Some("Defense Plan")`, `Some("Battle Plan")` or `None`
  - `DecisionOptionProjection.badge: Option[String] = None`, wire key `"badge"`

- [x] **Step 1: Write the failing engine test**

Add to `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala`:

```scala
  test("a plan's badge names the side or sides that may use it") {
    import oathdigital.model.CampaignPlanSide._
    assertEquals(CampaignPlans.badgeOf(Set(Attacker)), Some("Attack Plan"))
    assertEquals(CampaignPlans.badgeOf(Set(Defender)), Some("Defense Plan"))
    assertEquals(CampaignPlans.badgeOf(Set(Attacker, Defender)),
      Some("Battle Plan"))
    assertEquals(CampaignPlans.badgeOf(Set.empty), None)
  }

  test("the option a plan offers carries that badge") {
    val offered = OfferedPlan(PowerId("relic.sticky-fire"),
      CampaignPlanOffer(CampaignPlanSource.Relic(PlayerId("red"),
        RelicId("relic:sticky-fire")), "Sticky Fire", Vector.empty,
        Vector.empty, Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)))
    assertEquals(CampaignPlans.optionOf(offered), DecisionOption.Badged(
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId("relic:sticky-fire"))),
      "Battle Plan"))
  }
```

- [x] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPlansSuite"`
Expected: FAIL — `badgeOf` and `Badged` do not exist.

- [x] **Step 3: Let an offer carry its sides**

In `src/main/scala/oathdigital/model/CampaignTypes.scala`:

```scala
/** ... existing doc ...
  *
  * `sides` is who may use the plan, copied in by `BattlePlan` from its own
  * declaration rather than written by each power: a power already says which
  * windows it offers at, and repeating that in every `plan` body is how the
  * two would drift.
  */
final case class CampaignPlanOffer(source: CampaignPlanSource, label: String,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect],
    sides: Set[CampaignPlanSide] = Set.empty)
```

In `BattlePlan.contributions`, stamp it on every offer:

```scala
      side => BattlePlan.windowOf(side) -> Vector[Contribution](Offer(ctx =>
        PlanContext.of(ctx).filter(_.side == side).flatMap(plan)
          .map(_.copy(sides = sides))))
```

- [x] **Step 4: Add the badged option**

In `src/main/scala/oathdigital/model/Decisions.scala`, beside `Priced`:

```scala
  /** An option carrying one short label a client draws as a chip: what kind of
    * thing the option is, as opposed to what choosing it costs. Wrapping for
    * the same reason `Priced` wraps -- `ref` is the wrapped option's, so an
    * answer names the same thing badge or no badge, and the badge never
    * affects legality.
    */
  final case class Badged(option: DecisionOption, badge: String)
      extends DecisionOption {
    def ref: DecisionOptionRef = option.ref
  }
```

- [x] **Step 5: Set the badge from the sides**

In `CampaignPlans`:

```scala
  /** The chip an offer's sides produce. A plan usable by either side is a
    * "Battle Plan"; one side's plan says which.
    */
  def badgeOf(sides: Set[CampaignPlanSide]): Option[String] =
    if (sides.size > 1) Some("Battle Plan")
    else sides.headOption.map {
      case CampaignPlanSide.Attacker => "Attack Plan"
      case CampaignPlanSide.Defender => "Defense Plan"
    }

  /** A card is named by the projector; the title has no card, so its button
    * carries the label its offer authored. Either way the option says which
    * side's plan it is.
    */
  def optionOf(offered: OfferedPlan): DecisionOption = {
    val inner = offered.offer.source match {
      case CampaignPlanSource.Title(_) => DecisionOption.Button(
        DecisionOptionRef.Button("title"), offered.offer.label)
      case source => DecisionOption.forRef(refOf(source)).getOrElse(
        throw new IllegalStateException(s"no option for plan source $source"))
    }
    badgeOf(offered.offer.sides).fold(inner)(DecisionOption.Badged(inner, _))
  }
```

- [x] **Step 6: Run the engine test**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignPlansSuite"`
Expected: PASS

- [x] **Step 7: Write the failing projection test**

Add to `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`:

```scala
  test("a badged option projects its badge and keeps its price") {
    val option = DecisionOption.Badged(DecisionOption.Priced(
      DecisionOption.Relic(DecisionOptionRef.Relic(RelicId("relic:sticky-fire"))),
      OptionPrice(favor = 1)), "Battle Plan")
    val projected = projector.optionProjection(ready, Some(owner),
      CardIndex.from(ready.game).toOption, option)
    assertEquals(projected.flatMap(_.badge), Some("Battle Plan"))
    assert(projected.exists(_.details.nonEmpty))
  }
```

Use whatever `ready`/`owner` fixture the suite already builds with a relic in play; `optionProjection` is `private[application]` so the suite can call it directly, as it already does elsewhere.

- [x] **Step 8: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: FAIL — `badge` is not a member of `DecisionOptionProjection`.

- [x] **Step 9: Add the wire field**

In `ActionProjectionDtos.scala`:

```scala
/** ... existing doc ...
  *
  * `badge` is one short label the option carries -- what kind of thing it is,
  * such as `Attack Plan` -- for a client to draw as a chip. It is separate
  * from `details`, which carries consequences and runs together into one line.
  */
final case class DecisionOptionProjection(kind: String, id: String,
    label: String, card: Option[CardDetailsProjection] = None,
    details: Vector[String] = Vector.empty,
    badge: Option[String] = None)
```

In `ActionProjectionCodec.scala`:

```scala
  private def encodeOptionRow(row: DecisionOptionProjection): ujson.Value =
    ujson.Obj("kind" -> row.kind, "id" -> row.id, "label" -> row.label,
      "card" -> option(row.card)(encodeCard),
      "details" -> encoded(row.details)(ujson.Str(_)),
      "badge" -> stringOption(row.badge))

  private[projection] def decodeOptionRow(raw: ujson.Value, child: String)
      : Result[DecisionOptionProjection] = for {
    row <- obj(raw, child)
    _ <- exact(row, Set("kind", "id", "label", "card", "details", "badge"), child)
    kind <- string(row, "kind", child); id <- string(row, "id", child)
    label <- string(row, "label", child)
    card <- optionalAbsent(row, "card", child)(decodeCard)
    details <- stringsOrEmpty(row, "details", child)
    badge <- optionalString(row, "badge", child)
  } yield DecisionOptionProjection(kind, id, label, card, details, badge)
```

- [x] **Step 10: Project the badge**

In `WalkerDecisionProjector.optionProjection`, add the case beside `Priced` and thread it through `row`:

```scala
    def row(label: String, card: Option[CardDetailsProjection] = None,
        extra: Vector[String] = Vector.empty, badge: Option[String] = None) =
      Some(DecisionOptionProjection(ref.kind, ref.wireId, label, card,
        details ++ extra, badge))
    option match {
      case DecisionOption.Priced(inner, price) => optionProjection(ready,
        viewer, index, inner, details ++ PriceDetails.of(price))
      // The badge is set after the wrapped option is described, so it survives
      // whichever order a caller wraps price and badge in.
      case DecisionOption.Badged(inner, badge) => optionProjection(ready,
        viewer, index, inner, details).map(_.copy(badge = Some(badge)))
      case DecisionOption.Button(_, label) => row(label)
```

The other `row(...)` call sites keep their current arguments — `badge` defaults to `None`.

- [x] **Step 11: Run the projector test**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS

- [x] **Step 12: Write the failing frontend test**

Add to `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`:

```scala
  test("a battle-plan offer draws its card and its side as a chip") {
    val card = CardDetails("relic:sticky-fire", "relic", "Sticky Fire",
      orientation = Some("face-up"))
    val plan = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      Some(card), Vector("1 Favor"), Some("Battle Plan"))
    val query = DecisionQueryState("choose-one", Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val parked = WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".walker-choice .card-face").size, 1)
    assertEquals(all(panel, ".plan-side-both").map(_.textContent),
      Vector("Battle Plan"))
    assertEquals(all(panel, ".walker-choice-details").map(_.textContent),
      Vector("1 Favor"))
  }
```

- [x] **Step 13: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: FAIL — `DecisionOptionState` takes five arguments and no chip is drawn.

- [x] **Step 14: Draw the card and the chip**

In `WalkerPanelSupport.renderChooseOnePanel`, inside `query.options.foreach`, after the favor-bank glyph block:

```scala
          // An option that carries a card IS the card: a plan is chosen by
          // reading what it does, which the face already says.
          option.card.foreach { card =>
            choose.textContent = ""
            choose.setAttribute("aria-label", label)
            choose.appendChild(CardFace.render(
              card.copy(orientation = Some("face-up"))))
          }
          option.badge.foreach { badge =>
            val chip = text("span", s"option-badge ${badgeClass(badge)}", badge)
            choose.appendChild(chip)
          }
```

and add the class mapping beside it:

```scala
  /** The three battle-plan chips, so a side reads as a colour as well as a
    * word. An unknown badge gets the neutral class rather than none.
    */
  private def badgeClass(badge: String): String = badge match {
    case "Attack Plan" => "plan-side-attack"
    case "Defense Plan" => "plan-side-defense"
    case _ => "plan-side-both"
  }
```

Guard the card branch so it applies only where the option is card-shaped and the panel is a plan-style list — `option.card` is already `None` for every button option, and the Recover panel has its own renderer, so no guard beyond `option.card.foreach` is needed. Run the gate in step 16 to confirm no other choose-one panel regresses; if one does (an option that carried a card and wanted a text button), assert what it needs in its own suite and gate the card branch on `option.badge.nonEmpty`.

Add to `frontend/styles.css`:

```css
/* Which side a battle plan is for, as a chip beside the offer. */
.option-badge {
  display: inline-block;
  border-radius: 1em;
  padding: 0 0.6em;
  font-size: 0.75em; }
.plan-side-attack { background: var(--suit-discord); color: #fff; }
.plan-side-defense { background: var(--suit-order); color: #fff; }
.plan-side-both {
  background: linear-gradient(90deg, var(--suit-discord), var(--suit-order));
  color: #fff; }
```

- [x] **Step 15: Run the frontend test**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: PASS

- [x] **Step 16: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green.

- [x] **Step 17: Commit**

```bash
git add src/main/scala/oathdigital/model/CampaignTypes.scala src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/styles.css src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala
```

```bash
git commit -m "feat(campaign): say whether a battle plan attacks or defends

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: The plans this battle has already played

The plan window is a `Repeat`, so each pass re-asks with the chosen plans removed and the panel looks like it resets. List what has been applied.

**Files:**
- Modify: `shared/.../projection/ActionProjectionDtos.scala` + `ActionProjectionCodec.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala`
- Modify: `frontend/styles.css`
- Test: `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`

**Interfaces:**
- Consumes: `DecisionOptionProjection.badge` (Task 7), `WalkerDecisionProjection.subjectCards` (Task 5) — this adds a third key to the same `exact(...)` sets.
- Produces: `WalkerDecisionProjection.answeredOptions: Vector[DecisionOptionProjection] = Vector.empty`, wire key `"answeredOptions"`.

- [x] **Step 1: Write the failing projector test**

Add to `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`:

```scala
  test("a repeated decision lists what has already been answered at it") {
    val projected = project(campaignWithOnePlanPlayed, viewer = Some(attacker))
    assertEquals(projected.map(_.decisionId), Some("campaign.attacker-plan"))
    assertEquals(projected.toVector.flatMap(_.answeredOptions).map(_.label),
      Vector("Sticky Fire"))
  }
```

Build `campaignWithOnePlanPlayed` from the suite's existing Campaign fixture by answering one plan and re-parking; if the suite has no Campaign fixture, `src/test/scala/oathdigital/gameplay/CampaignFixture.scala` has one and the projector suite may use it.

- [x] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: FAIL — `answeredOptions` is not a member.

- [x] **Step 3: Add the wire field**

In `ActionProjectionDtos.scala`, extend `WalkerDecisionProjection` after `subjectCards`:

```scala
    /** The answers already recorded at THIS decision id, described as options.
      *
      * A decision inside a `Repeat` -- the battle-plan window is the only one
      * today -- re-asks with the chosen answers removed, so without this the
      * panel reads as resetting rather than accumulating.
      *
      * A reference whose option the projector cannot present from state alone
      * is omitted: a button's label is authored by the query, and a spent
      * answer has no query left to read it from. In the plan window that
      * leaves out only the title's own plan.
      */
    answeredOptions: Vector[DecisionOptionProjection] = Vector.empty
```

In `ActionProjectionCodec.scala`, add `"answeredOptions"` to `encodeWalkerDecision`, to the `exact(...)` set in `decodeWalkerDecision`, and decode it:

```scala
    "answeredOptions" -> encoded(value.answeredOptions)(encodeOptionRow))
```

```scala
    _ <- exact(value, Set("action", "decisionId", "kind", "pool", "count",
      "query", "rollOutcome", "subjectCards", "answeredOptions"), path)
    ...
    answeredRaws <- array(value, "answeredOptions", path)
    answered <- traverse(answeredRaws, s"$path.answeredOptions")(decodeOptionRow)
  } yield WalkerDecisionProjection(action, decision, kind, pool, count, query,
    rollOutcome, subjects, answered)
```

- [x] **Step 4: Fill it in the projector**

In `WalkerDecisionProjector.parked`'s `case None =>` branch, add the argument:

```scala
              subjectCards = subjectCards(ready, viewer, decide.decisionId),
              answeredOptions = answeredOptions(ready, viewer, pending,
                decide.decisionId)))
```

and the resolver:

```scala
  /** Every answer already recorded at `decisionId`, in answer order, described
    * the way the decision's own options are. See `answeredOptions`' doc on the
    * projection for why a button is dropped rather than labelled.
    */
  private def answeredOptions(ready: ReadyGame, viewer: Option[PlayerId],
      pending: PendingTree, decisionId: String)
      : Vector[DecisionOptionProjection] = {
    val index = CardIndex.from(ready.game).toOption
    pending.answered.collect {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    }.flatMap(DecisionOption.forRef)
      .flatMap(optionProjection(ready, viewer, index, _))
  }
```

`pending.answered` is a `Vector[Answered]`; confirm the `Answered` case class's shape in `src/main/scala/oathdigital/model/` before writing the pattern — `WalkerDecisionProjector` does not currently destructure it, but `CardPlayProcedure.scala:159` does, as `Answered(id, DecisionAnswer.ChooseOneAnswer(value), _)`.

- [x] **Step 5: Run the projector test**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS

- [x] **Step 6: Write the failing frontend test**

Add to `frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala`:

```scala
  test("the plans already played are listed above the remaining offers") {
    val played = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      None, Vector.empty, Some("Battle Plan"))
    val offer = DecisionOptionState("denizen", "denizen:longbows", "Longbows")
    val query = DecisionQueryState("choose-one", Vector(offer),
      heading = Some("Choose a battle plan, or finish"))
    val parked = WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query), answeredOptions = Vector(played))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".plans-played li").map(_.textContent),
      Vector("Sticky Fire"))
  }
```

`WalkerDecisionState` takes its arguments positionally in the other tests; pass `answeredOptions` by name as above so the two new fields cannot be swapped.

- [x] **Step 7: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: FAIL — no `.plans-played` element.

- [x] **Step 8: Draw the strip**

In `WalkerPanelSupport.renderChooseOnePanel`, after the subject-cards block from Task 5 and before `query.options.foreach`:

```scala
        // What this loop has already applied. A `Repeat` re-asks with the
        // chosen answers removed, so the panel otherwise reads as resetting.
        if (decision.answeredOptions.nonEmpty) {
          val played = element("div", "plans-played")
          played.appendChild(text("h3", "", "Plans played"))
          val list = element("ul", "")
          decision.answeredOptions.foreach(option =>
            list.appendChild(text("li", "", option.label)))
          played.appendChild(list)
          panel.appendChild(played)
        }
```

Add to `frontend/styles.css`:

```css
/* What the battle has already applied, above what is still on offer. */
.plans-played { margin-bottom: 0.4em; font-size: 0.85em; }
.plans-played ul { margin: 0; padding-left: 1.2em; }
```

- [x] **Step 9: Run the frontend test**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerChoicePanelRenderSuite"`
Expected: PASS

- [x] **Step 10: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green.

- [x] **Step 11: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/WalkerChoicePanelRenderSuite.scala
```

```bash
git commit -m "feat(campaign): list the battle plans already played

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Rolls during a walk

Mid-walk roll feedback exists only for Recover, gated on `procedure == ActionRef.Recover`, and `WalkerRollOutcomeProjection` is shaped for it (`difficulty: Int`). A Campaign's attack roll reaches the client only inside `campaign.sacrifice`'s heading, as prose. Generalise the payload and split the sacrifice panel into three parts.

**This task removes `difficulty` rather than adding a field.** Recover's panel reads it, so Recover and Campaign convert together, in this one task — that is why it is not two.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/walker/WalkerRollFeedback.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerProcedureRegistry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignBattle.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `shared/.../projection/ActionProjectionDtos.scala` + `ActionProjectionCodec.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala`
- Modify: `frontend/styles.css`
- Test: `src/test/scala/oathdigital/gameplay/CampaignBattleSuite.scala`, `src/test/scala/oathdigital/gameplay/CampaignProcedureSuite.scala`, `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/RecoverPanelSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala`

**Interfaces:**
- Consumes: `WalkerDecisionProjection` (Tasks 5 and 8 added keys to its `exact(...)` sets; this task changes the nested roll payload's keys, not those).
- Produces:
  - `WalkerRollOutcomeProjection(pool: String, faces: Vector[String], score: Int, target: Option[Int] = None, detail: Vector[String] = Vector.empty)` — wire keys `pool`, `faces`, `score`, `target`, `detail`. `difficulty` is gone.
  - `final case class WalkerRollFeedback(pool: PoolKey, target: Option[Int] = None, detail: Vector[String] = Vector.empty)` in `oathdigital.gameplay.walker`.
  - `WalkerProcedureRegistry.Entry.rollFeedback: (ExecutableCatalog, ReadyGame, PlayerId, String) => Option[WalkerRollFeedback]`, defaulting to `(_, _, _, _) => None`.
  - `CampaignBattle.sacrificeHeading(max: Int): String` — one argument, the prompt alone.

- [x] **Step 1: Write the failing engine test**

Replace the two `sacrificeHeading` cases in `src/test/scala/oathdigital/gameplay/CampaignBattleSuite.scala` with:

```scala
  test("the sacrifice heading is the prompt alone -- the dice are glyphs") {
    assertEquals(CampaignBattle.sacrificeHeading(1),
      "Sacrifice up to 1 warband for one attack each")
    assertEquals(CampaignBattle.sacrificeHeading(2),
      "Sacrifice up to 2 warbands for one attack each")
  }
```

- [x] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignBattleSuite"`
Expected: FAIL — `sacrificeHeading` takes four arguments.

- [x] **Step 3: Reduce the heading to the prompt**

In `CampaignBattle.scala`, delete `faceName` and replace `sacrificeHeading`:

```scala
  /** The question alone. The dice, the attack total and the skull loss are
    * projected as a roll outcome and drawn as glyphs beside this, so writing
    * them here again would print the same facts twice, once as words.
    */
  def sacrificeHeading(max: Int): String =
    s"Sacrifice up to $max warband${if (max == 1) "" else "s"} for one attack each"
```

In `CampaignProcedure.sacrificeStep`, drop the now-unused attack lookup:

```scala
  private def sacrificeStep(actor: PlayerId): Operation =
    Branch((ready, pending) => CampaignSetup.setup(ready, actor, pending)
      .filter(setup => CampaignBattle.sacrificeMax(ready, setup) > 0).map { setup =>
        val max = CampaignBattle.sacrificeMax(ready, setup)
        Vector[Operation](Decide(CampaignIds.sacrifice, actor,
          DecisionQuery.ChooseAmount(0, max,
            Some(CampaignBattle.sacrificeHeading(max)), "Sacrifice"),
          window = Some(PowerWindow.CampaignSacrificeSelection)))
      }.getOrElse(Vector.empty))
```

Update `CampaignProcedureSuite.scala:419` to the one-argument call. If `AttackDieFace` is now unimported in either file, remove the import.

- [x] **Step 4: Run the engine suites**

Run: `./sbtw "testOnly oathdigital.gameplay.CampaignBattleSuite oathdigital.gameplay.CampaignProcedureSuite"`
Expected: PASS

- [x] **Step 5: Write the failing feedback test**

Add to `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`:

```scala
  test("a parked sacrifice projects the attack pool's faces, score and losses") {
    val projected = project(campaignAtSacrifice, viewer = Some(attacker))
    val outcome = projected.flatMap(_.rollOutcome)
    assertEquals(outcome.map(_.pool), Some("campaign.attack"))
    assertEquals(outcome.map(_.faces), Some(Vector("two-swords-skull", "one-sword")))
    assertEquals(outcome.map(_.score), Some(3))
    assertEquals(outcome.flatMap(_.target), None)
    assertEquals(outcome.map(_.detail), Some(Vector("1 skull loss")))
  }

  test("a parked Recover still projects its pool and its target") {
    val outcome = project(recoverRelicPick, viewer = Some(owner))
      .flatMap(_.rollOutcome)
    assertEquals(outcome.map(_.pool), Some("recover"))
    assertEquals(outcome.flatMap(_.target).isDefined, true)
    assertEquals(outcome.map(_.detail), Some(Vector.empty))
  }
```

Build `campaignAtSacrifice` from `CampaignFixture` by rolling the attack pool with `TwoSwordsSkull, OneSword` and walking to the sacrifice park.

- [x] **Step 6: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: FAIL — `pool` is not a member of `WalkerRollOutcomeProjection`, and a Campaign projects no outcome at all.

- [x] **Step 7: Reshape the wire payload**

In `ActionProjectionDtos.scala`:

```scala
/** The roll a parked decision wants shown beside it.
  *
  * `pool` names which roll it is (`"recover"`, `"campaign.attack"`), so the
  * client can word the total without guessing from the action. `faces` are
  * display-ready die-face labels in roll order across every roll of that pool
  * so far; `score` is the derived total (a `Doubler` on a later roll
  * multiplies earlier shields, so this is not a per-face sum). `target` is the
  * number the score must reach where there is one -- a Recover site's
  * difficulty -- and `None` where there is not, as in a Campaign. `detail`
  * carries already-worded consequences such as `"1 skull loss"`.
  */
final case class WalkerRollOutcomeProjection(
    pool: String,
    faces: Vector[String],
    score: Int,
    target: Option[Int] = None,
    detail: Vector[String] = Vector.empty
)
```

In `ActionProjectionCodec.scala`:

```scala
  def encodeRollOutcome(value: WalkerRollOutcomeProjection): ujson.Value = ujson.Obj(
    "pool" -> value.pool, "faces" -> encoded(value.faces)(ujson.Str(_)),
    "score" -> value.score, "target" -> intOption(value.target),
    "detail" -> encoded(value.detail)(ujson.Str(_)))
  def decodeRollOutcome(raw: ujson.Value, path: String)
      : Result[WalkerRollOutcomeProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("pool", "faces", "score", "target", "detail"), path)
    pool <- string(value, "pool", path)
    faces <- strings(value, "faces", path)
    score <- int(value, "score", path)
    target <- optionalInt(value, "target", path)
    detail <- stringsOrEmpty(value, "detail", path)
  } yield WalkerRollOutcomeProjection(pool, faces, score, target, detail)
```

- [x] **Step 8: Declare what feedback a procedure wants**

Create `src/main/scala/oathdigital/gameplay/walker/WalkerRollFeedback.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.model.PoolKey

/** The roll a parked decision wants shown beside it: which pool, the number
  * the score is measured against where there is one, and any consequence the
  * procedure has already worded.
  *
  * A `Decide` carries no pool of its own, and a projector that matched on the
  * procedure to decide what to show was exactly the drift the walker registry
  * exists to end -- so the procedure declares it, keyed by decision id, in the
  * same registry entry that already declares its roll decision id and its
  * continuations.
  */
final case class WalkerRollFeedback(pool: PoolKey, target: Option[Int] = None,
    detail: Vector[String] = Vector.empty)
```

In `WalkerProcedureRegistry.Entry`, add the field last so every existing entry keeps compiling:

```scala
      requiresPlayableOption: Boolean = false,
      /** The roll to show beside a parked decision of this procedure, by that
        * decision's id. `None` for a decision no roll belongs beside, which is
        * the default and every procedure that rolls nothing.
        */
      rollFeedback: (ExecutableCatalog, ReadyGame, PlayerId, String) =>
        Option[WalkerRollFeedback] = (_, _, _, _) => None)
```

Add the accessor beside the existing `rollDecisionId`/`modifierWindow` ones:

```scala
  def rollFeedback(procedure: ProcedureRef, catalog: ExecutableCatalog,
      ready: ReadyGame, actor: PlayerId, decisionId: String,
      registrations: Map[ProcedureRef, Entry] = entries)
      : Option[WalkerRollFeedback] =
    registrations.get(procedure).flatMap(_.rollFeedback(catalog, ready, actor,
      decisionId))
```

- [x] **Step 9: Declare Recover's and Campaign's feedback**

In `RecoverProcedure`, add:

```scala
  /** Every park of a Recover shows the same thing: the pool rolled so far and
    * the site's difficulty, which is worth showing before the first roll too.
    */
  def rollFeedback(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, decisionId: String): Option[WalkerRollFeedback] =
    Option.when(decisionId == rollDecisionId ||
        decisionId == relicDecisionId || decisionId == choiceDecisionId)(
      WalkerRollFeedback(recoverPool, target = actorSite(ready, actor)
        .flatMap(RecoverRules.difficulty(catalog, _)))).filter(_.target.nonEmpty)
```

In `CampaignProcedure`, add:

```scala
  /** The sacrifice question is asked about the attack roll, and the placement
    * and relocation questions are asked after the defense roll, so each shows
    * the roll it is about. A Campaign has no target: the two scores are
    * compared to each other, not to a difficulty.
    */
  def rollFeedback(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, decisionId: String): Option[WalkerRollFeedback] =
    decisionId match {
      case CampaignIds.sacrifice =>
        val skulls = ready.game.current.rollOutcomes
          .get(CampaignIds.attackPool).fold(0)(_.skulls)
        Some(WalkerRollFeedback(CampaignIds.attackPool, detail =
          if (skulls == 0) Vector.empty
          else Vector(s"$skulls skull loss${if (skulls == 1) "" else "es"}")))
      case CampaignIds.placement | CampaignIds.relocation =>
        Some(WalkerRollFeedback(CampaignIds.defensePool))
      case _ => None
    }
```

Wire both into their registry entries by adding `rollFeedback = RecoverProcedure.rollFeedback` and `rollFeedback = CampaignProcedure.rollFeedback` to `ActionRef.Recover` and `ActionRef.Campaign` in `WalkerProcedureRegistry.entries`.

- [x] **Step 10: Project it generically**

In `WalkerDecisionProjector`, replace both `Option.when(procedure == ActionRef.Recover)(rollOutcome(ready, awaited)).flatten` expressions:

```scala
      case Some((pool, count)) =>
        WalkerProcedureRegistry.rollDecisionId(procedure).toOption.map(
          rollId => WalkerDecisionProjection(procedure.key, rollId, "roll",
            pool = Some(pool.value), count = Some(count),
            rollOutcome = rollOutcome(procedure, ready, awaited, rollId)))
```

```scala
              rollOutcome = rollOutcome(procedure, ready, awaited,
                decide.decisionId),
```

and replace the Recover-specific `rollOutcome` helper with:

```scala
  /** The roll the parked decision declares it is about, with the faces
    * accumulated in that pool so far.
    *
    * The procedure says which pool and why (`WalkerRollFeedback`); this reads
    * the outcome and spells the faces. `RecoverRules` and `ActionRef.Recover`
    * no longer appear here, which is the point: the last action-specific
    * expression in this file moved into the registry beside the procedure's
    * other declarations.
    */
  private def rollOutcome(procedure: ProcedureRef, ready: ReadyGame,
      actor: PlayerId, decisionId: String)
      : Option[WalkerRollOutcomeProjection] =
    WalkerProcedureRegistry.rollFeedback(procedure, catalog, ready, actor,
      decisionId).map { feedback =>
      val outcome = ready.game.current.rollOutcomes.get(feedback.pool)
      WalkerRollOutcomeProjection(
        pool = feedback.pool.value,
        faces = outcome.fold(Vector.empty[DieFace])(_.faces).flatMap(faceName),
        score = outcome.fold(0)(_.score),
        target = feedback.target,
        detail = feedback.detail)
    }

  /** Local duplicate of `WalkerEventCodec`'s (serialization-layer) face
    * vocabulary: the application layer may not import the serialization layer
    * (`BackendArchitectureSuite`), and `CampaignResultProjector` follows the
    * same precedent for Campaign's dice.
    */
  private def faceName(value: DieFace): Vector[String] = value match {
    case DefenseDieFace.Blank => Vector("blank")
    case DefenseDieFace.OneShield => Vector("one-shield")
    case DefenseDieFace.TwoShields => Vector("two-shields")
    case DefenseDieFace.Doubler => Vector("doubler")
    case AttackDieFace.HollowSword => Vector("hollow-sword")
    case AttackDieFace.OneSword => Vector("one-sword")
    case AttackDieFace.TwoSwordsSkull => Vector("two-swords-skull")
    case _ => Vector.empty
  }
```

The `_ => Vector.empty` case exists because `DieFace` may have a variant neither pool rolls; if the compiler reports the match as exhaustive without it, delete it. Confirm the face names match `CampaignResultProjector.scala:27-29` and `DieFace.scala:15-18` exactly — the client looks glyphs up by these strings.

- [x] **Step 11: Run the backend tests**

Run: `./sbtw "test"`
Expected: green.

- [x] **Step 12: Write the failing frontend tests**

In `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala`:

```scala
  test("the sacrifice panel draws dice, then totals, then the prompt") {
    val outcome = WalkerRollOutcomeState("campaign.attack",
      Vector("two-swords-skull", "one-sword"), 3, None, Vector("1 skull loss"))
    val query = DecisionQueryState("choose-amount", Vector.empty,
      heading = Some("Sacrifice up to 2 warbands for one attack each"),
      confirmLabel = Some("Sacrifice"), minimum = Some(0), maximum = Some(2))
    val parked = WalkerDecisionState("campaign", "campaign.sacrifice", "decide",
      query = Some(query), rollOutcome = Some(outcome))
    val panel = renderAmountPanel(parked, query)
    assertEquals(panel.querySelectorAll(".die-faces").toVector.size, 1)
    assertEquals(panel.querySelector(".walker-roll-totals").textContent,
      "Attack 3 · 1 skull loss")
    assertEquals(panel.querySelector("h2").textContent,
      "Sacrifice up to 2 warbands for one attack each")
    assertEquals(panel.textContent.contains("two swords and a skull"), false)
  }
```

`.die-faces` is the class `DieFace.roll` produces — read `DieFace.scala:47` and assert on whatever it actually writes. `renderAmountPanel` is the suite's existing helper for reaching `WalkerSelectionPanels.render` with a `WalkerAmountDraft`; extend it to take the parked decision.

In `frontend/src/test/scala/oathdigital/frontend/RecoverPanelSuite.scala`, update the existing roll-feedback assertions to the new copy:

```scala
  test("Recover shows its dice and how close the score is to the target") {
    val outcome = WalkerRollOutcomeState("recover",
      Vector("one-shield", "two-shields"), 3, Some(4), Vector.empty)
    val panel = renderRecover(outcome)
    assertEquals(panel.querySelector(".walker-roll-totals").textContent,
      "Shields 3 · need 4")
    assertEquals(panel.querySelector(".walker-roll-faces")
      .getAttribute("aria-label"),
      "Rolled one-shield, two-shields -- 3 shields so far (need 4).")
  }
```

- [x] **Step 13: Run them to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerSelectionPanelsSuite oathdigital.frontend.RecoverPanelSuite"`
Expected: FAIL — `WalkerRollOutcomeState` takes three arguments and the amount panel draws no dice.

- [x] **Step 14: Make the roll feedback generic and reuse it**

In `WalkerPanelSupport`, replace `rollOutcomeSummary` and `rollFeedback`:

```scala
  /** The sentence the glyph row is read as, for a reader who cannot see the
    * symbols. It is the accessible name of the row, never printed.
    */
  private[frontend] def rollOutcomeSummary(outcome: WalkerRollOutcomeState): String =
    if (outcome.faces.isEmpty)
      outcome.target.fold(s"No dice rolled for ${outcome.pool}.")(target =>
        s"Need $target to succeed.")
    else s"Rolled ${outcome.faces.mkString(", ")} -- ${outcome.score} " +
      s"shields so far${outcome.target.fold("")(t => s" (need $t)")}."

  /** What a roll is called in a total line. A pool key is a wire string, so an
    * unknown one is printed as it arrives rather than guessed at.
    */
  private[frontend] def poolLabel(pool: String): String = pool match {
    case "recover" => "Shields"
    case "campaign.attack" => "Attack"
    case "campaign.defense" => "Defense"
    case other => other
  }

  /** The faces as the symbols printed on them, then one line of totals: what
    * the roll came to, the number it is measured against where there is one,
    * and any consequence the engine already worded.
    */
  private[frontend] def rollFeedback(decision: WalkerDecisionState,
      panel: dom.Element): Unit =
    decision.rollOutcome.foreach { outcome =>
      if (outcome.faces.nonEmpty) {
        val row = element("p", "walker-roll-faces")
        row.setAttribute("aria-label", rollOutcomeSummary(outcome))
        row.appendChild(DieFace.roll(outcome.faces))
        panel.appendChild(row)
      }
      val parts = Vector(s"${poolLabel(outcome.pool)} ${outcome.score}") ++
        outcome.target.map(target => s"need $target") ++ outcome.detail
      panel.appendChild(text("p", "walker-roll-totals", parts.mkString(" · ")))
    }
```

Delete the old `.recover-roll-outcome` rule from `frontend/styles.css` and add:

```css
/* A roll during a walk: the faces, then one line of totals. */
.walker-roll-faces { margin: 0; }
.walker-roll-totals { margin: 0 0 0.3em; font-size: 0.9em; }
```

If `.recover-roll-outcome` is styled anywhere else, replace those selectors with the two above rather than keeping a dead class.

- [x] **Step 15: Show it on the amount panel**

In `WalkerSelectionPanels`, pass the decision into `renderAmount` and call the feedback before the heading:

```scala
        case draft: WalkerAmountDraft =>
          renderAmount(decision, query, draft, canControl, panel, ui)
```

```scala
  private def renderAmount(decision: WalkerDecisionState,
      query: DecisionQueryState, draft: WalkerAmountDraft,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit = {
    // The roll first, then what it came to, then the question about it: the
    // sacrifice question is only answerable by reading the attack.
    WalkerPanelSupport.rollFeedback(decision, panel)
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
```

The rest of `renderAmount` is unchanged.

- [x] **Step 16: Run the frontend tests**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.WalkerSelectionPanelsSuite oathdigital.frontend.RecoverPanelSuite"`
Expected: PASS

- [x] **Step 17: Run the gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green.

- [x] **Step 18: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker src/main/scala/oathdigital/gameplay/actions/recover/RecoverProcedure.scala src/main/scala/oathdigital/gameplay/actions/campaign src/main/scala/oathdigital/application/WalkerDecisionProjector.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala frontend/styles.css src/test/scala/oathdigital frontend/src/test/scala/oathdigital/frontend
```

```bash
git commit -m "feat(walker): show a roll as dice wherever a walk parks on one

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Finishing

After Task 9, the branch is complete. Announce and use `superpowers:finishing-a-development-branch`: verify the gate is green, present the integration options, and follow the user's choice.

## Execution record

All nine tasks are done and merged to `main` (2026-09-24). Tasks 1–8 ran on the
`vision-identity` worktree branch; Task 9 and the review ran in a follow-up
session. The final gate was green at 1683 backend and 349 frontend tests.

A review of the whole branch against this plan and the spec found, and fixed:

- **Task 2** had removed a legal play: a Vision may be held as a facedown
  adviser. Only the site option is filtered now (spec §2 correction).
- **Task 6** named no card for a walker modifier: those powers' sources are
  `GameRule`s, so `cardOf` never matched. `PreviewModifierDescriptions` now
  finds the card in play that carries the power.
- **Task 7/8**'s plan card was nested inside the button that commits the plan,
  so reading the card chose it. The card now sits above its own button.
- **Task 9** drew the defense roll only on the amount panel; the choose-one
  and distribute panels draw it too. Muster declares Campaign's feedback for
  Knights Errant.

Smaller fixes and the gaps deliberately left are listed in the spec's
"As built" section.

## Self-Review Notes

Checked against the spec, section by section:

| Spec section | Task |
| --- | --- |
| §1 Vision card text | Task 1 |
| §2 Vision play offers only what is legal | Task 2 |
| §3 The player-area slots row | Task 4 |
| §3.1 A text mode for the overlay | Task 3 |
| §3.2 Oath text | Task 3 |
| §4 Showing the card a decision is about | Task 5 |
| §5 Modifier options are cards | Task 6 |
| §6 Battle plans: cards and sides | Tasks 7 and 8 |
| §7 Rolls during a walk | Task 9 |

Two places where the plan makes a decision the spec left open, both stated in the task that makes them:

- §6's "Plans played" strip needed a wire field. It is `WalkerDecisionProjection.answeredOptions`, filled generically from the answers recorded at the parked decision id rather than from anything Campaign-specific, and an option whose label cannot be resolved from state (a button — in practice only the title's own plan) is omitted rather than labelled with its wire key.
- §7's "the pool the parked decision declares" needed a mechanism, since a `Decide` carries no pool. It is a `rollFeedback` function on the walker registry `Entry`, beside the `rollDecisionId` and `continuationFor` declarations it matches in shape, so the projector keeps no `ActionRef` comparison of its own.

One ordering constraint: Tasks 5, 7 and 8 each add a key to `WalkerDecisionProjection`'s or `DecisionOptionProjection`'s `exact(...)` sets. Run them in order; a later task rebased over an earlier one must keep the earlier key in both sets.
