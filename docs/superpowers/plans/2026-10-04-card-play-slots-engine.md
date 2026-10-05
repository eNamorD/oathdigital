# Card Play Through Card Slots: Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A card play is one choose-one over card slots (empty site and adviser slots, Replace slots, the revealed Vision spot, the discard pile), shaped by the powers that change card play, with reasons on the options a protection refuses; the client shows the slots as labelled panel rows until plan 2 puts them on the board.

**Architecture:** The decision vocabulary gains four slot refs and a `Replacing` mark. `PlacementRules` becomes `CardPlayRules`, whose site discard permissions stack. A new `CardSlots` builds the placement options from the card, its origin, the actor's state and the rules, over a rule-free `CardPlay.operations` planner; `CardPlayProcedure` asks the slot choice, a follow-up choose-many when a Replace site slot leaves permissions unspent, and credits each discard to the permission that allowed it. Tests and the Game Log name a play by how it plays the card (`CardPlayProcedure.PlayedAs`), so most suites move to the new refs through one classifier.

**Tech Stack:** Scala 3, Scala.js, ujson with hand-written `exact` codecs, munit. Build through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-04-card-play-slots-design.md`. Read it whole before Task 1. This plan is its plan 1 (Engine). Its board work (homes, placeholders, the facing toggle, the Replace badge, the facedown-adviser board pick) is plan 2 and is out of scope here.

## Global Constraints

- Never touch the live database `var/oathdigital`. Browser checks use a scratch copy only, on port 8081, started through Bash, never through `preview_start`.
- Work in a git worktree: from the main checkout run `git worktree add -b card-play-slots .claude/worktrees/card-play-slots main`, then `ln -s /Users/roman/projects/oathdigital/.tooling .claude/worktrees/card-play-slots/.tooling`, then `EnterWorktree`. Inside the worktree run `git merge --ff-only main` first. Before merging from the main checkout, `ExitWorktree(keep)`.
- Inside the worktree keep Bash plain: no `cd` chained with git, no heredocs. Write any helper script into the session scratchpad and run it with `python3`. Use `sed -i ''` on macOS.
- Other sessions commit to `main` while you work, and HEAD can move. Check `git log -1` before any amend or merge. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`.
- Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` unless the committing model is another one, in which case use that model's own line.
- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects. An unused import or private member fails the build, and so does a non-exhaustive match.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `shared/src/main` may import only `oathdigital.protocol`. `application` must not import `persistence`, `serialization` or `server`. The model may not import gameplay.
- Codec rule: every new projection field is written always and decoded with a default, so a payload from before this plan still decodes; every `exact` field set gains the new name.
- Visibility rule: the one function `GamePresentationProjector.identifiesCard` decides whether a viewer sees a card's face. No other test of knowledge.
- Names rule (`model/Decisions.scala` header): a game object's name never enters a query, an answer or a validator. The projector resolves names from refs.
- Copy rule: player text is plain sentences, no em dashes, numbers as digits.
- Old in-progress journals are not migrated. Decision ids and button keys change freely (spec, Rulings).
- Gates, in order, before the final commit of each task: the task's own `testOnly` runs, then `./sbtw "test"` (and `"frontend/test"` when the task touched `shared` or `frontend`), then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`. The last task also runs `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

### Decisions this plan makes that the spec left open or worded differently

1. **Headings name the card in the projector.** The names rule keeps card and site names out of a query, so the engine's headings are "Play the card" and "Discard more cards from the site", and `WalkerDecisionProjector` replaces them with "Play {card}" and "Discard more cards from {site}" for the decision's owner.
2. **`replaces` carries card details, not a new ref type.** `DecisionOptionProjection.replaces: Option[CardDetailsProjection]` is the replaced card as the viewer may see it, redacted through the visibility rule like any other card.
3. **The played card joins the end of the row.** `Play` has no slot position, so a replaced card is discarded and the new card is appended to the site's card row or the adviser row. "Takes that card's place" holds for what is discarded, not for the index. A positioned play is a follow-up, not part of this plan.
4. **The discard pile comes first.** Option order is discard pile, site slots (the empty one, then Replace slots in row order), faceup plays, facedown plays: today's Discard, Site, Faceup, Facedown order, so `Situation.defaultAnswer` (first available option) still discards and the golden logs do not move.
5. **A line credits a discard made, never one declined.** Each site card discarded writes the line of the permission it spent. Choosing the empty slot where a Replace slot was offered writes nothing; today a declined Homeland discard still wrote the Homeland line.
6. **The Hall of Ministers gives a reason.** It is a power, so an option it refuses is shown with "Protected by the Hall of Ministers". Grand Scepter and any other restriction code leave the option out with no reason.
7. **A Vision from a facedown adviser goes to the revealed Vision spot.** The spec's "that card's own slot, faceup" is the rule for a denizen; a Vision played faceup always becomes the revealed Vision, and a Conspiracy is "Play".
8. **A facedown adviser turned faceup must fit the limit after the play.** Its own slot is offered only while the actor's adviser count is at most that limit (its own, if it is Silver Tongue or Insomnia), which is what Silver Tongue's and Insomnia's guard enforced before it was removed.
9. **The follow-up's maximum is the smaller of the permissions left and the cards offered.** Every permission covers any site card (an edifice is a denizen for placement), so every other site card is offered; the count is checked again when the play runs.
10. **`MinorAdviserProjection.placements` and `CardResolutionProjection` go outright.** The projection is computed live and the client ships with the server; the server's facedown-adviser preview check reads only whether the player has facedown advisers. Discarding is always legal, so a facedown adviser is always playable.
11. **The planner keeps the refusals no rule changes.** `CardPlay.operations` refuses a Vision or an adviser-only card at a site, a site-only card faceup, a facedown adviser played facedown or replacing an adviser, a full site with nothing discarded, and a discard of a card that is not there. Permissions and limits are `CardSlots`'s alone.

### File structure

Engine and protocol:

- `src/main/scala/oathdigital/model/Decisions.scala`: `SiteSlot`, `AdviserPlay`, `RevealedVision`, `DiscardPile` refs and options, `Replacing`.
- `src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala` (renamed from `PlacementRules.scala`): `CardPlayRules`, `DiscardPermission`, the Homeland permission and the `discard-first` note key.
- `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`: the rule-free planner `operations`, `discardRegion`, `homelandSuit`, `playedSource`, `nextRegion`.
- `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala` (new): `CardSlot`, `MoreDiscards`, the slot builder, permission matching, the follow-up and the credits.
- `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`: `PlayedAs`, `playedAs`, `placementOptions`, the placement and follow-up decisions, the settled shape.
- `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`: the not-kept discards through `CardPlay.operations`.
- `src/main/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMob.scala`, `powers/search/CropRotation.scala`, `powers/rest/HolderAdviserLimit.scala`, `powers/rest/SilverTongue.scala`, `powers/rest/Insomnia.scala`, `powers/AdviserLimit.scala`, `actions/RuleNotes.scala`: the rules' new shape.
- `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`: slot rows, `replaces`, card play headings.
- `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala`, `ActionLines.scala`, `DetailLines.scala`: the new refs and `PlayedAs`.
- `src/main/scala/oathdigital/application/GamePresentationProjector.scala`, `LegalActionProjector.scala`: `adviserLimit`; no placements.
- `src/main/scala/oathdigital/server/GameRoutes.scala`: the facedown-adviser preview check.
- `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala`, `ActionProjectionCodec.scala`, `WorldProjectionCodec.scala`: `replaces`, `adviserLimit`, no `CardResolutionProjection`.
- `frontend/src/main/scala/oathdigital/frontend/frontend.scala`: drop the resolution aliases.

Tests:

- `src/test/scala/oathdigital/testkit/PlacementPicks.scala` (new): picks a placement answer by `PlayedAs`.
- `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala` (new), `CardPlayRulesSuite.scala` (renamed from `PlacementRulesSuite.scala`), `application/SlotOptionRowsSuite.scala` (new), `application/AdviserLimitProjectionSuite.scala` (new), `gameplay/CardPlayReasonsSuite.scala` (new).
- Deleted: `gameplay/HomelandRuleSuite.scala`, `gameplay/CardPlayRestrictionsSuite.scala` (their cases move to `CardSlotsSuite`).
- Moved to the new refs: the card play, Search, minor action, power, log and service suites each task names.

---

### Task 1: Card slot refs and the Replace mark

Spec, Protocol. Four new refs and a `Replacing` wrapper, presented as labelled rows, named in the log, and carried over the wire. Nothing offers them yet.

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala:36-145,156-229`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:310-375`
- Modify: `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala:16-55`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:165-178`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala:363-383`
- Test: `src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala`, `src/test/scala/oathdigital/serialization/WalkerDecisionWireSuite.scala`, `src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala` (new), `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `DecisionOptionRef.SiteSlot(site: SiteId, index: Int)`, kind `site-slot`, wire id `{site}:{index}`.
  - `DecisionOptionRef.AdviserPlay(owner: PlayerId, index: Int, faceup: Boolean)`, kind `adviser-play`, wire id `{owner}:{index}:faceup|facedown`.
  - `DecisionOptionRef.RevealedVision(owner: PlayerId)`, kind `revealed-vision`, wire id `{owner}`.
  - `DecisionOptionRef.DiscardPile(region: Region)`, kind `discard-pile`, wire id `{region.key}`.
  - `DecisionOption.SiteSlot`, `.AdviserPlay`, `.RevealedVision`, `.DiscardPile`, one per ref, and `DecisionOption.Replacing(option: DecisionOption, card: CardId)`.
  - `DecisionOptionProjection.replaces: Option[CardDetailsProjection] = None`.

- [ ] **Step 1: Write the failing ref tests**

Append to `src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala`, inside the class:

```scala
  private val siteSlot = DecisionOptionRef.SiteSlot(
    CatalogNames.site("Ancient City"), 2)
  private val adviserPlay =
    DecisionOptionRef.AdviserPlay(PlayerId("blue"), 1, faceup = false)
  private val revealed = DecisionOptionRef.RevealedVision(PlayerId("blue"))
  private val pile = DecisionOptionRef.DiscardPile(Region.Provinces)

  test("the card slot refs spell themselves as kind and id pairs and parse back"):
    assertEquals((siteSlot.kind, siteSlot.wireId),
      ("site-slot", s"${siteSlot.site.value}:2"))
    assertEquals((adviserPlay.kind, adviserPlay.wireId),
      ("adviser-play", "blue:1:facedown"))
    assertEquals((revealed.kind, revealed.wireId), ("revealed-vision", "blue"))
    assertEquals((pile.kind, pile.wireId), ("discard-pile", "provinces"))
    Vector[DecisionOptionRef](siteSlot, adviserPlay,
      adviserPlay.copy(faceup = true), revealed, pile).foreach(ref =>
      assertEquals(DecisionOptionRef.fromWire(ref.kind, ref.wireId), Some(ref),
        ref.toString))

  test("a card slot's site or owner may contain a colon and still round trips"):
    Vector[DecisionOptionRef](DecisionOptionRef.SiteSlot(SiteId("a:b"), 0),
      DecisionOptionRef.AdviserPlay(PlayerId("a:b"), 2, faceup = true),
      DecisionOptionRef.RevealedVision(PlayerId("a:b"))).foreach(ref =>
      assertEquals(DecisionOptionRef.fromWire(ref.kind, ref.wireId), Some(ref),
        ref.toString))

  test("a malformed card slot ref is not a reference"):
    Vector("x", "x:", ":2", "x:-1", "x:y").foreach(id =>
      assertEquals(DecisionOptionRef.fromWire("site-slot", id), None, id))
    Vector("blue:1", "blue:1:up", "blue:-1:faceup", ":1:faceup",
      "blue::faceup").foreach(id =>
      assertEquals(DecisionOptionRef.fromWire("adviser-play", id), None, id))
    assertEquals(DecisionOptionRef.fromWire("discard-pile", "elsewhere"), None)
    assertEquals(DecisionOptionRef.fromWire("revealed-vision", " "), None)

  test("the card slot refs are presentable from the reference alone"):
    assertEquals(DecisionOption.forRef(siteSlot),
      Some(DecisionOption.SiteSlot(siteSlot)))
    assertEquals(DecisionOption.forRef(adviserPlay),
      Some(DecisionOption.AdviserPlay(adviserPlay)))
    assertEquals(DecisionOption.forRef(revealed),
      Some(DecisionOption.RevealedVision(revealed)))
    assertEquals(DecisionOption.forRef(pile),
      Some(DecisionOption.DiscardPile(pile)))

  test("a Replace mark keeps the wrapped option's reference and availability"):
    val marked = DecisionOption.Replacing(DecisionOption.SiteSlot(siteSlot),
      CatalogNames.denizen("Wrestlers"))
    assertEquals(marked.ref, siteSlot)
    assert(marked.available)
    assert(!DecisionOption.Unavailable(marked, OptionReason.Written("No"))
      .available)
```

In `src/test/scala/oathdigital/serialization/WalkerDecisionWireSuite.scala`, add the new refs to the `refs` of "every option reference kind round trips through a recorded answer", after the `AdviserSlot` line:

```scala
      DecisionOptionRef.SiteSlot(SiteId("site:s1"), 2),
      DecisionOptionRef.AdviserPlay(PlayerId("blue"), 0, faceup = true),
      DecisionOptionRef.AdviserPlay(PlayerId("blue"), 2, faceup = false),
      DecisionOptionRef.RevealedVision(PlayerId("blue")),
      DecisionOptionRef.DiscardPile(Region.Hinterland),
```

- [ ] **Step 2: Write the failing row tests**

Create `src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.protocol.projection.DecisionOptionProjection
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.present

/** How a card slot option reads in the Action panel. Plan 1 shows the slots
  * as labelled rows; plan 2's board summary reuses the same words.
  */
class SlotOptionRowsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)
  private val projector = new WalkerDecisionProjector(catalog, presentation,
    WalkerPowers(Vector.empty))
  /** Cradle, room for three, no Homeland. */
  private val home = CatalogNames.site("Ancient City")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val roar = CatalogNames.denizen("Threatening Roar")

  /** Wrestlers lies at Ancient City; p1 holds Threatening Roar facedown and
    * Conquest as their revealed Vision. */
  private lazy val board: ReadyGame = Table.start.denizen(wrestlers, at = home)
    .adviser(p1, roar, facedown = true)
    .revealedVision(p1, VisionRules.Conquest).ready

  private def rowOf(option: DecisionOption, viewer: PlayerId = p1)(
      using munit.Location): DecisionOptionProjection =
    present(projector.optionProjection(board, Some(viewer),
      CardIndex.from(board.game).toOption, option), "a projected row")

  private def site: String = presentation.siteLabel(home)

  test("an empty site slot names the site"):
    val row = rowOf(DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals((row.kind, row.label, row.replaces),
      ("site-slot", s"Empty slot at $site", None))

  test("a Replace site slot names the card it discards and carries it"):
    val row = rowOf(DecisionOption.Replacing(
      DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 0)), wrestlers))
    assertEquals(row.label, s"Replace Wrestlers at $site")
    assertEquals(row.replaces.map(_.cardId), Some(wrestlers.value))

  test("a site slot past the site's capacity is not presentable"):
    assertEquals(projector.optionProjection(board, Some(p1), None,
      DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 3))), None)

  test("an adviser play names its slot and its facing"):
    assertEquals(rowOf(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 1, faceup = true))).label,
      "Adviser slot 2, faceup")
    assertEquals(rowOf(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 1, faceup = false))).label,
      "Adviser slot 2, facedown")

  test("a Replace adviser play names a facedown adviser to its owner only"):
    val option = DecisionOption.Replacing(DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(p1, 0, faceup = false)), roar)
    assertEquals(rowOf(option).label, "Replace Threatening Roar, facedown")
    val seenByOther = rowOf(option, viewer = p2)
    assert(seenByOther.replaces.exists(_.hidden))
    assert(!seenByOther.label.contains("Threatening Roar"))

  test("the revealed Vision spot, and Replace naming the Vision there"):
    val spot = DecisionOption.RevealedVision(
      DecisionOptionRef.RevealedVision(p1))
    assertEquals(rowOf(spot).label, "Revealed Vision")
    assertEquals(rowOf(DecisionOption.Replacing(spot, VisionRules.Conquest))
      .label, s"Replace ${presentation.cardLabel(board, VisionRules.Conquest)}" +
        " as revealed Vision")

  test("the discard pile names its region"):
    assertEquals(rowOf(DecisionOption.DiscardPile(
      DecisionOptionRef.DiscardPile(Region.Provinces))).label,
      "Discard to the Provinces discard")
```

- [ ] **Step 3: Write the failing codec test**

Append to `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, inside the class (it already has `projection`, `known` and `assertRoundTrips`):

```scala
  test("an option's replaced card round-trips beside an option with none"):
    val replacing = DecisionOptionProjection("site-slot", "site:a:0",
      "Replace Known at A", replaces = Some(known))
    val pile = DecisionOptionProjection("discard-pile", "cradle",
      "Discard to the Cradle discard")
    assertRoundTrips(projection.copy(walkerDecision = Some(
      WalkerDecisionProjection("search", "cardplay.place.denizen.known",
        "decide", query = Some(DecisionQueryProjection.ChooseOne(
          Vector(replacing, pile)))))))
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.model.DecisionOptionRefSuite oathdigital.serialization.WalkerDecisionWireSuite oathdigital.application.SlotOptionRowsSuite oathdigital.protocol.ProjectionProtocolSuite"`
Expected: compilation fails: `SiteSlot`, `AdviserPlay`, `RevealedVision`, `DiscardPile`, `Replacing` and `replaces` are not members.

- [ ] **Step 5: Add the refs to `Decisions.scala`**

In the `DecisionOptionRef` trait doc, replace `/** Which of the twelve variants this is, as a stable wire string. */` with `/** Which of the sixteen variants this is, as a stable wire string. */`, and in the `fromWire` doc replace `Total over the twelve variants` with `Total over the sixteen variants`.

Replace the `AdviserSlot` doc comment with:

```scala
  /** One adviser slot of a player, named by its position in their advisers
    * rather than by the card, so a facedown adviser's identity is never
    * disclosed by an option. A slot below the printed capacity of three may
    * be empty; the powers that name a held adviser name an occupied one. The
    * owner may contain a colon; the slot is always the text after the last
    * one.
    */
```

After the `FavorBank` case class, add:

```scala
  /** Slot `index` of `site`'s card row, below the site's capacity: the empty
    * slot a card is played to, or the card a play there replaces. The site
    * may contain a colon; the index is always the text after the last one.
    */
  final case class SiteSlot(site: SiteId, index: Int) extends DecisionOptionRef:
    require(index >= 0, "a site slot must be non-negative")
    val kind: String = "site-slot"
    def wireId: String = s"${site.value}:$index"
  /** Playing the card to `owner`'s adviser slot `index`, faceup or facedown.
    * A choose-one answer is one ref, so the ref carries the facing, and an
    * adviser slot is offered as two options. The owner may contain a colon;
    * the facing follows the last one and the index the one before it.
    */
  final case class AdviserPlay(owner: PlayerId, index: Int, faceup: Boolean)
      extends DecisionOptionRef:
    require(index >= 0, "an adviser slot must be non-negative")
    val kind: String = "adviser-play"
    def wireId: String =
      s"${owner.value}:$index:${if faceup then "faceup" else "facedown"}"
  /** Playing a Vision faceup as `owner`'s revealed Vision. A faceup
    * Conspiracy takes no slot, so it never names this. */
  final case class RevealedVision(owner: PlayerId) extends DecisionOptionRef:
    val kind: String = "revealed-vision"
    def wireId: String = owner.value
  /** Discarding the played card to `region`'s discard pile. */
  final case class DiscardPile(region: Region) extends DecisionOptionRef:
    val kind: String = "discard-pile"
    def wireId: String = region.key
```

In `fromWire`, after the `"favor-bank"` case, add:

```scala
      case "site-slot" => indexOf(wireId).map((site, index) =>
        SiteSlot(SiteId(site), index))
      case "adviser-play" => facingOf(wireId).flatMap((slot, faceup) =>
        slotOf(slot).map((owner, index) => AdviserPlay(owner, index, faceup)))
      case "revealed-vision" => Some(RevealedVision(PlayerId(wireId)))
      case "discard-pile" => Region.all.find(_.key == wireId).map(DiscardPile(_))
```

Replace the `slotOf` helper with:

```scala
  /** The owner and non-negative slot of a slot reference's wire id. */
  private def slotOf(wireId: String): Option[(PlayerId, Int)] =
    indexOf(wireId).map((owner, slot) => PlayerId(owner) -> slot)

  /** The text before the last colon, which may hold colons itself, and the
    * non-negative index after it. */
  private def indexOf(wireId: String): Option[(String, Int)] =
    val at = wireId.lastIndexOf(':')
    Option.when(at > 0)(wireId.take(at)).filter(_.trim.nonEmpty)
      .flatMap(owner => wireId.drop(at + 1).toIntOption
        .filter(_ >= 0).map(owner -> _))

  /** An adviser play's slot part and facing: the facing follows the last
    * colon. */
  private def facingOf(wireId: String): Option[(String, Boolean)] =
    val at = wireId.lastIndexOf(':')
    Option.when(at > 0)(wireId.take(at)).flatMap(slot =>
      wireId.drop(at + 1) match
        case "faceup" => Some(slot -> true)
        case "facedown" => Some(slot -> false)
        case _ => None)
```

In `object DecisionOption`, after the `FavorBank` case class, add:

```scala
  final case class SiteSlot(ref: DecisionOptionRef.SiteSlot)
      extends DecisionOption
  final case class AdviserPlay(ref: DecisionOptionRef.AdviserPlay)
      extends DecisionOption
  final case class RevealedVision(ref: DecisionOptionRef.RevealedVision)
      extends DecisionOption
  final case class DiscardPile(ref: DecisionOptionRef.DiscardPile)
      extends DecisionOption
```

After the `Unavailable` case class, add:

```scala
  /** An option whose play discards `card` to make room: the card in that
    * site slot or adviser slot, or the revealed Vision a new one displaces.
    * Wrapping for the reason `Priced` wraps: `ref` is the wrapped option's,
    * so the mark never affects legality. The projector names the card.
    */
  final case class Replacing(option: DecisionOption, card: CardId)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = option.available
```

In `forRef`, after the `FavorBank` case, add:

```scala
    case value: DecisionOptionRef.SiteSlot => Some(SiteSlot(value))
    case value: DecisionOptionRef.AdviserPlay => Some(AdviserPlay(value))
    case value: DecisionOptionRef.RevealedVision => Some(RevealedVision(value))
    case value: DecisionOptionRef.DiscardPile => Some(DiscardPile(value))
```

- [ ] **Step 6: Carry `replaces` in the projection**

In `ActionProjectionDtos.scala`, add to the doc comment above `DecisionOptionProjection`, after the `state` paragraph:

```scala
  * `replaces` is the card choosing the option discards to make room (a
  * Replace slot), as the viewer may see it.
```

and add the field after `reason`:

```scala
    reason: Option[String] = None,
    replaces: Option[CardDetailsProjection] = None)
```

In `ActionProjectionCodec.scala`, `encodeOptionRow` gains `"replaces" -> option(row.replaces)(encodeCard)` after `"reason"`. In `decodeOptionRow`, add `"replaces"` to the `exact` set, add `replaces <- optionalAbsent(row, "replaces", child)(decodeCard)` after `reason`, and yield `DecisionOptionProjection(kind, id, label, card, details, badge, state, reason, replaces)`.

- [ ] **Step 7: Present the slot options in the projector**

In `WalkerDecisionProjector.optionProjection`, after the `DecisionOption.Unavailable` case, add:

```scala
      // Named after the wrapped option is described, so the label says what
      // the play discards and where the card goes.
      case DecisionOption.Replacing(inner, card) =>
        val replaced = shown(ready, viewer, index, card)
        optionProjection(ready, viewer, index, inner, details).map(row =>
          row.copy(label = replacingLabel(inner.ref, replaced.name),
            replaces = Some(replaced)))
```

After the `DecisionOption.FavorBank` case, add:

```scala
      case DecisionOption.SiteSlot(slot) =>
        val fits = catalog.site(slot.site).exists(slot.index < _.capacity)
        if fits && ready.game.current.map.sites.contains(slot.site) then
          row(s"Empty slot at ${presentation.siteLabel(slot.site)}")
        else None
      case DecisionOption.AdviserPlay(play) =>
        if ready.game.current.players.exists(_.player == play.owner) then
          row(s"Adviser slot ${play.index + 1}, ${facing(play.faceup)}")
        else None
      case DecisionOption.RevealedVision(spot) =>
        if ready.game.current.players.exists(_.player == spot.owner) then
          row("Revealed Vision")
        else None
      case DecisionOption.DiscardPile(pile) =>
        row(s"Discard to the ${pile.region.key.capitalize} discard")
```

Add below `optionProjection`:

```scala
  /** A Replace option's label: the card it discards, and where the played
    * card goes. */
  private def replacingLabel(ref: DecisionOptionRef, name: String): String =
    ref match
      case DecisionOptionRef.SiteSlot(site, _) =>
        s"Replace $name at ${presentation.siteLabel(site)}"
      case DecisionOptionRef.AdviserPlay(_, _, faceup) =>
        s"Replace $name, ${facing(faceup)}"
      case _: DecisionOptionRef.RevealedVision =>
        s"Replace $name as revealed Vision"
      case _ => s"Replace $name"

  private def facing(faceup: Boolean): String =
    if faceup then "faceup" else "facedown"
```

- [ ] **Step 8: Name the new refs in the log**

In `ChoiceWords.option`, after the `FavorBank` case, add:

```scala
    case DecisionOptionRef.SiteSlot(site, slot) =>
      Vector(Text(s"slot ${slot + 1} at "), words.site(site))
    case DecisionOptionRef.AdviserPlay(owner, slot, faceup) =>
      Vector(words.player(owner), Text(s"'s adviser slot ${slot + 1}, " +
        (if faceup then "faceup" else "facedown")))
    case DecisionOptionRef.RevealedVision(owner) =>
      Vector(words.player(owner), Text("'s revealed Vision"))
    case DecisionOptionRef.DiscardPile(region) =>
      Vector(Text(s"the ${region.key.capitalize} discard"))
```

In `labelled`, after the `Unavailable` case, add `case DecisionOption.Replacing(inner, _) => labelled(ref)(inner)`.

No log test drives these cases: the card play decisions that offer the new refs are narrated by `ActionLines` and listed in `DetailLines.NarratedPrefixes`, so the generic "chose" line never prints them. The match must still be total, which `-Werror` checks.

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.model.DecisionOptionRefSuite oathdigital.serialization.WalkerDecisionWireSuite oathdigital.application.SlotOptionRowsSuite oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS. Then run the gates (`./sbtw "test" "frontend/test"`, the two scripts). Expected: PASS. Record the server test count; later tasks compare against it.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala src/test/scala/oathdigital/serialization/WalkerDecisionWireSuite.scala src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala
git commit -m "feat(decisions): add card slot refs and the Replace mark"
```

---

### Task 2: Card play rules with stacked site discard permissions

Spec, Engine, "Card play rules". `PlacementRules` becomes `CardPlayRules`; the Mob and Crop Rotation each add a `DiscardPermission`; Silver Tongue and Insomnia lower the limit and append no check; each player board says its adviser limit. Card play still reads the rules the old way until Task 6: any permission makes the site discard possible, and the first permission's note is written.

**Files:**
- Rename and rewrite: `src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala` to `src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/RuleNotes.scala:15`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala` (rules type and reads)
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala:5,17-22,52-86,148-151,226-249`
- Modify: `src/main/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMob.scala`, `src/main/scala/oathdigital/gameplay/powers/search/CropRotation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/rest/HolderAdviserLimit.scala`, `SilverTongue.scala:28`, `Insomnia.scala:34-35`
- Modify: `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:305-327`, `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala:159-198`
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala:167-207`
- Rename and modify: `src/test/scala/oathdigital/gameplay/PlacementRulesSuite.scala` to `src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala`
- Modify tests: `src/test/scala/oathdigital/gameplay/PlacementFixture.scala`, `SiteDiscardFirstSuite.scala`, `HomelandLineSuite.scala`, `gameplay/powers/search/CropRotationSuite.scala`, `gameplay/powers/banner/PeoplesFavorMobSuite.scala`, `application/gamelog/GameLogPowerLinesSuite.scala`
- Test: `src/test/scala/oathdigital/application/AdviserLimitProjectionSuite.scala` (new), `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `final case class DiscardPermission(source: PowerId, note: Note)`: one discard of any site card.
  - `final case class CardPlayRules(adviserLimit: Int = 3, playedCardLimit: Int = 3, siteDiscards: Vector[DiscardPermission] = Vector.empty)` with `limitAfter(faceup: Boolean): Int`, `limitAdvisers(limit: Int)`, `limitPlayedCard(limit: Int)`, `permitSiteDiscard(permission: DiscardPermission)`.
  - `CardPlayRules.DefaultAdviserLimit: Int`, `CardPlayRules.default`, `CardPlayRules.discardFirst: NoteKey`, `CardPlayRules.homeland(site: SiteId, actor: PlayerId): DiscardPermission`.
  - `HolderAdviserLimit(cardId: DenizenId, limit: Int)`, no `name`.
  - `PlayerBoardProjection.adviserLimit: Int = 3`.
  - Test fixture `PlacementFixture.testPermission: DiscardPermission` (source `test.discard-first`).

- [ ] **Step 1: Rename the rules suite and write the failing tests**

Run `git mv src/test/scala/oathdigital/gameplay/PlacementRulesSuite.scala src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala` and `git mv src/main/scala/oathdigital/gameplay/actions/PlacementRules.scala src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala`.

In `CardPlayRulesSuite.scala`, rename the class to `CardPlayRulesSuite`, replace the imports with:

```scala
import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.AdviserLimit
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.as
```

replace the class doc with:

```scala
/** `CardPlayRules`: the adviser limits and site discard permissions a card
  * play is planned under, and how two contributors to them compose.
  * `limitTwo` is PlacementFixture's stub power that lowers the adviser
  * limit to two, and `testPermission` its plain site discard permission.
  */
```

change the `import PlacementFixture.{...}` line to also import `testPermission`, and replace the tests "a limit only lowers, and a faceup limit leaves the facedown one" and "two contributors compose in either order" with:

```scala
  test("one limit counts every adviser, faceup or facedown, and only lowers"):
    val rules = CardPlayRules.default.limitAdvisers(2)
    assertEquals(rules.limitAfter(faceup = true), 2)
    assertEquals(rules.limitAfter(faceup = false), 2)
    assertEquals(CardPlayRules.default.limitAdvisers(5), CardPlayRules.default)

  test("the played card's own limit counts only when it is played faceup"):
    // The rulebook checks a play that changes the adviser limit against the
    // new limit. Silver Tongue played facedown has no power, so the old limit
    // holds for that play.
    val rules = CardPlayRules.default.limitPlayedCard(2)
    assertEquals(rules.limitAfter(faceup = true), 2)
    assertEquals(rules.limitAfter(faceup = false),
      CardPlayRules.DefaultAdviserLimit)

  test("site discard permissions stack in the order they are given"):
    val second = DiscardPermission(PowerId("test.second-discard"),
      Note(PowerId("test.second-discard"), _ => None))
    val stacked = CardPlayRules.default.permitSiteDiscard(testPermission)
      .permitSiteDiscard(second)
    assertEquals(stacked.siteDiscards.map(_.source),
      Vector(testPermission.source, second.source))

  test("two contributors compose in either order"):
    val tree = as[CardPlayProcedure.PlacementTree](
      build(Table.start.hand(p1, card).ready, p1, card))
    def rulesOf(ops: Vector[Operation]): CardPlayRules =
      as[CardPlayProcedure.PlacementBody](ops.head).rules
    val limitFirst = tree.adjust(tree.adjust(tree.children)(
      _.limitAdvisers(2)))(_.permitSiteDiscard(testPermission))
    val permitFirst = tree.adjust(tree.adjust(tree.children)(
      _.permitSiteDiscard(testPermission)))(_.limitAdvisers(2))
    Vector(limitFirst, permitFirst).foreach { ops =>
      assertEquals(rulesOf(ops).adviserLimit, 2)
      assertEquals(rulesOf(ops).siteDiscards.map(_.source),
        Vector(testPermission.source))
      // Both contributors fold into the one placement body.
      assertEquals(ops.size, 1)
    }
```

Leave the other tests of the suite as they are.

- [ ] **Step 2: Write the failing projection tests**

Create `src/test/scala/oathdigital/application/AdviserLimitProjectionSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}

/** Each player board says how many adviser slots to draw (card play slots
  * design, "Projection"). */
class AdviserLimitProjectionSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)

  test("a board's adviser limit is 3, and 2 while its player holds Silver " +
      "Tongue faceup; a facedown Insomnia sets none"):
    val ready = Table.start.adviser(p1, "Silver Tongue")
      .adviser(p2, "Insomnia", facedown = true).ready
    val limits = presentation.playerBoards(ready, Some(p1))
      .map(board => board.playerId -> board.adviserLimit).toMap
    assertEquals(limits(p1.value), 2)
    assertEquals(limits(p2.value), 3)
```

Append to `ProjectionProtocolSuite`:

```scala
  test("a board's adviser limit round-trips"):
    assertRoundTrips(projection.copy(playerBoards =
      projection.playerBoards.map(_.copy(adviserLimit = 2))))
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayRulesSuite oathdigital.application.AdviserLimitProjectionSuite oathdigital.protocol.ProjectionProtocolSuite"`
Expected: compilation fails: `CardPlayRules`, `DiscardPermission`, `testPermission` and `adviserLimit` are not found.

- [ ] **Step 4: Write `CardPlayRules.scala`**

Replace the whole of `src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala` with:

```scala
package oathdigital.gameplay.actions

import oathdigital.model._

/** Leave to discard one card from the actor's site before a play there. Any
  * card of the site's card list may go: an edifice is a denizen for
  * placement, and an intact edifice is refused only as a locked card.
  * `source` is the power or rule that gives the leave, and `note` the line
  * card play writes when a discard it allowed is made.
  */
final case class DiscardPermission(source: PowerId, note: Note)

/** The rules a card play is planned under. A power changes them through the
  * `SearchPlayAdviser` window, and card play reads nothing else, so the play
  * never names a power.
  *
  *  - `adviserLimit` is the one limit on all of the player's advisers,
  *    whatever their facing, set by the advisers they hold.
  *    `playedCardLimit` is the limit the played card sets once faceup. The
  *    rulebook checks a play that changes the limit against the new limit,
  *    so a faceup play counts against the lower of the two and a facedown
  *    play, whose card has no power, against `adviserLimit` alone.
  *  - `siteDiscards` are the permissions to discard a card from the site a
  *    card is played to, one card each. They stack. The Homeland rule's is
  *    not among them: card play adds it at the Homeland of the played card's
  *    suit (`homeland`).
  *
  * Every change lowers a limit or adds a permission, so contributions
  * compose in any order. (The limit cards implemented so far only lower.)
  */
final case class CardPlayRules(
    adviserLimit: Int = CardPlayRules.DefaultAdviserLimit,
    playedCardLimit: Int = CardPlayRules.DefaultAdviserLimit,
    siteDiscards: Vector[DiscardPermission] = Vector.empty):

  /** The limit a play counts against: the new one when the played card goes
    * faceup. */
  def limitAfter(faceup: Boolean): Int =
    if faceup then math.min(adviserLimit, playedCardLimit) else adviserLimit

  /** Lowers the player's limit to at most `limit`. */
  def limitAdvisers(limit: Int): CardPlayRules =
    copy(adviserLimit = math.min(adviserLimit, limit))

  /** The played card sets a limit of `limit` once faceup. */
  def limitPlayedCard(limit: Int): CardPlayRules =
    copy(playedCardLimit = math.min(playedCardLimit, limit))

  /** Adds one site discard. */
  def permitSiteDiscard(permission: DiscardPermission): CardPlayRules =
    copy(siteDiscards = siteDiscards :+ permission)

object CardPlayRules:
  val DefaultAdviserLimit: Int = 3
  val default: CardPlayRules = CardPlayRules()

  /** "{Red} may discard a card at their site first.": the line of the power
    * or rule that let a play to a site discard a card there. */
  val discardFirst: NoteKey = NoteKey("discard-first", Vector(NotePart.Arg(0),
    NotePart.Text(" may discard a card at their site first.")))

  /** The Homeland rule's permission (CR p. 31) for `actor`'s play to `site`,
    * the Homeland of the played card's suit: one discard of any card there,
    * whatever that card's suit. */
  def homeland(site: SiteId, actor: PlayerId): DiscardPermission =
    DiscardPermission(RuleNotes.homelandDiscard,
      Note(RuleNotes.homelandDiscard, _ => Some(discardFirst(
        PowerSourceRef.Site(site), NoteArg.Player(actor)))))
```

In `RuleNotes.scala`, replace `PlacementRules.discardFirst` with `CardPlayRules.discardFirst`.

- [ ] **Step 5: Read the new rules in card play**

In `CardPlay.scala`, run:

```bash
sed -i '' -e 's/PlacementRules\.siteDiscardFirst/CardPlayRules.siteDiscards/g' -e 's/PlacementRules/CardPlayRules/g' -e 's/rules\.siteDiscardFirst/rules.siteDiscards.nonEmpty/g' -e 's/rules\.adviserLimit(orientation)/rules.limitAfter(orientation == Orientation.FaceUp)/g' src/main/scala/oathdigital/gameplay/actions/CardPlay.scala
```

In `CardPlayProcedure.scala`:
- the import becomes `import oathdigital.gameplay.actions.{CardPlay, CardPlayRules}` (`RuleNotes` is no longer read here);
- every `PlacementRules` becomes `CardPlayRules`;
- the `noReplacement` doc reads "offered only when a site discard permission (`CardPlayRules.siteDiscards`) or the Homeland of the card's suit makes the discard optional.";
- the notice line `if rules.siteDiscardFirst then rules.siteDiscardNote.toVector` becomes `if rules.siteDiscards.nonEmpty then rules.siteDiscards.headOption.map(_.note).toVector`;
- `homelandNote` becomes:

```scala
  /** The Homeland rule's line, "{site}: {Red} may discard a card at their
    * site first.": without a power's permission, only the Homeland of the
    * card's suit asks for a discard at a site. */
  private def homelandNote(ready: ReadyGame, actor: PlayerId): Option[Note] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .map(site => CardPlayRules.homeland(site, actor).note)
```

Until Task 6 the play still discards at most one site card, and with several permissions it writes the first one's line.

- [ ] **Step 6: Give the Mob and Crop Rotation a permission each**

In `PeoplesFavorMob.scala`, the import of `PlacementRules` becomes `import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission}`, `noteKeys` reads `Vector(CardPlayRules.discardFirst)`, the transform's body reads `tree.adjust(children)(_.permitSiteDiscard(permission(ctx.activePlayer)))`, and `note` is replaced with:

```scala
  /** One discard of any site card, credited with the Mob's line. */
  private def permission(actor: PlayerId): DiscardPermission =
    DiscardPermission(id, Note(id, _ => Some(
      CardPlayRules.discardFirst(PowerSourceRef.Banner(Banner.PeoplesFavor),
        NoteArg.Player(actor)))))
```

Replace the last paragraph of its doc ("Its line ... travels in the rules, and card play writes it after the discard answer.") with "Its line, "{Red} may discard a card at their site first.", travels in the permission, and card play writes it for the discard it allowed."

In `CropRotation.scala` make the same changes with `PowerSourceRef.Card(cardId)`:

```scala
  /** One discard of a site card, credited with Crop Rotation's line. An
    * edifice is a denizen for placement, so a ruined one may go. */
  private def permission(actor: PlayerId): DiscardPermission =
    DiscardPermission(id, Note(id, _ => Some(
      CardPlayRules.discardFirst(PowerSourceRef.Card(cardId),
        NoteArg.Player(actor)))))
```

Replace its doc's last two paragraphs with:

```scala
  * It adds one site discard permission through the same
  * `SearchPlayAdviser` window as the Mob's, so the two stack with each other
  * and with the Homeland rule, each allowing one discard. The
  * Play-Facedown-Adviser action selects its modifiers at the Search's window,
  * so it applies there too. An edifice is a denizen for placement, so a
  * ruined one may go. The generic discard rules decide which cards may go:
  * an intact edifice is locked, and Crop Rotation itself, being selected, is
  * refused by the active-modifier restriction.
  *
  * Its line, "{Red} may discard a card at their site first.", travels in the
  * permission, and card play writes it for the discard it allowed.
```


- [ ] **Step 7: Lower the limit with no check after the play**

Replace `HolderAdviserLimit.scala`'s class doc and body with:

```scala
/** "You can only have two advisers", printed on an adviser: refactor P4 of
  * catalog batch 3, shared by Silver Tongue and Insomnia.
  *
  * It is a transform at `SearchPlayAdviser`. While `cardId` is faceup in the
  * acting player's advisers, it lowers that player's limit to `limit`. While
  * `cardId` is the card being played, it sets the played card's own limit
  * (the rulebook checks such a play against the new limit), so playing
  * it faceup needs room under the limit. Card play offers no adviser slot
  * past a limit, so nothing is checked after the play, and the limit writes
  * no line. A limit only lowers, so two such cards compose and the lowest
  * holds.
  */
final case class HolderAdviserLimit(cardId: DenizenId, limit: Int):
  /** `limit`, when `player` holds `cardId` as a faceup adviser. */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    ready.game.current.players.find(_.player == player)
      .filter(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      }).map(_ => limit)

  val contribution: Contribution = Transform((ctx, children) =>
    ctx.operation match {
      case tree: CardPlayProcedure.PlacementTree
          if limitFor(ctx.state, ctx.activePlayer).nonEmpty =>
        tree.adjust(children)(_.limitAdvisers(limit))
      case tree: CardPlayProcedure.PlacementTree if tree.card == cardId =>
        tree.adjust(children)(_.limitPlayedCard(limit))
      case _ => children
    })
```

In `SilverTongue.scala` the field becomes `private val limit = HolderAdviserLimit(cardId, HolderLimit)`, and in `Insomnia.scala` `private val limit = HolderAdviserLimit(cardId, Insomnia.HolderLimit)`.

In `AdviserLimit.scala`, `PlacementRules` becomes `CardPlayRules` in the import, the doc and `Default`.

- [ ] **Step 8: Project each board's adviser limit**

In `ActionProjectionDtos.scala`, add to `PlayerBoardProjection` after `restSupplyBase: Int = 0`:

```scala
    restSupplyBase: Int = 0,
    /** How many advisers this player may hold: 3, or 2 under Silver Tongue
      * or Insomnia. The board draws that many adviser slots. */
    adviserLimit: Int = 3
```

In `WorldProjectionCodec.scala`, `encodeBoard` gains `"adviserLimit" -> value.adviserLimit` after `"restSupplyBase"`; `decodeBoard` adds `"adviserLimit"` to its `exact` set, reads `limit <- intOr(value, "adviserLimit", path, 3)` after `restBase`, and yields `PlayerBoardProjection(player, warbands, favor, up, down, committed, total, supply, pawn, advisers, relics, vision, banners, banked, printed, atSites, restBase, limit)`.

In `GamePresentationProjector.playerBoards`, add `import oathdigital.gameplay.powers.AdviserLimit` to the file's imports and pass the limit as the last argument: `banked, printed, atSites, restBase, AdviserLimit.of(catalog, ready, player.player))`.

- [ ] **Step 9: Move the test fixtures and suites to the new rules**

Replace `PlacementFixture.scala`'s `PlacementRules` import with `import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission}`, its two doc mentions of `PlacementRules` with `CardPlayRules`, and `rulePower`, `discardFirst` and `limitTwo` with:

```scala
  /** Stub of ContributingPower: changes the rules every play is planned
    * under (`CardPlayRules`), nothing else. */
  def rulePower(name: String)(
      change: CardPlayRules => CardPlayRules): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, ops) =>
          ctx.operation match {
            case tree: CardPlayProcedure.PlacementTree =>
              tree.adjust(ops)(change)
            case _ => ops
          })))

  /** A plain site discard permission, of any card, whose line says nothing. */
  val testPermission: DiscardPermission = DiscardPermission(
    PowerId("test.discard-first"),
    Note(PowerId("test.discard-first"), _ => None))

  /** Stub of ContributingPower: adds `testPermission`. */
  val discardFirst: ContributingPower =
    rulePower("test.discard-first")(_.permitSiteDiscard(testPermission))
  /** Stub of ContributingPower: lowers the adviser limit to two. */
  val limitTwo: ContributingPower =
    rulePower("test.limit-two")(_.limitAdvisers(2))
```

Then, in the suites:
- `SiteDiscardFirstSuite.scala`: the import becomes `import oathdigital.gameplay.actions.{CardPlay, CardPlayRules}`; add `testPermission` to its `import PlacementFixture.{...}`; replace `PlacementRules.default.withSiteDiscardFirst` with `CardPlayRules.default.permitSiteDiscard(testPermission)`, every other `PlacementRules` with `CardPlayRules`, and the doc's first line with "/** A site discard permission (`CardPlayRules.siteDiscards`): a play to a site may first discard one".
- `PeoplesFavorMobSuite.scala`: replace `oathdigital.gameplay.actions.PlacementRules.default.withSiteDiscardFirst` with `oathdigital.gameplay.actions.CardPlayRules.default.permitSiteDiscard(PlacementFixture.testPermission)`.
- `CropRotationSuite.scala`, `HomelandLineSuite.scala`, `GameLogPowerLinesSuite.scala`: replace `PlacementRules` with `CardPlayRules` (imports and `discardFirst`).

Check with `grep -rn "PlacementRules\|withSiteDiscardFirst\|siteDiscardNote\|siteDiscardFirst" src shared frontend`: no match.

- [ ] **Step 10: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayRulesSuite oathdigital.application.AdviserLimitProjectionSuite oathdigital.protocol.ProjectionProtocolSuite oathdigital.gameplay.SiteDiscardFirstSuite oathdigital.gameplay.powers.rest.SilverTongueSuite oathdigital.gameplay.powers.rest.InsomniaSuite oathdigital.gameplay.powers.banner.PeoplesFavorMobSuite oathdigital.gameplay.powers.search.CropRotationSuite oathdigital.gameplay.HomelandLineSuite"`
Expected: PASS. The Silver Tongue and Insomnia suites passing unchanged show the removed check was redundant: the planner already refused every play past the limit. Then run the gates. Expected: PASS, with the server count up by the new tests.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala src/main/scala/oathdigital/gameplay/actions/RuleNotes.scala src/main/scala/oathdigital/gameplay/actions/CardPlay.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/main/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMob.scala src/main/scala/oathdigital/gameplay/powers/search/CropRotation.scala src/main/scala/oathdigital/gameplay/powers/rest/HolderAdviserLimit.scala src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala src/main/scala/oathdigital/gameplay/powers/rest/Insomnia.scala src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala src/main/scala/oathdigital/application/GamePresentationProjector.scala src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala src/test/scala/oathdigital/gameplay/PlacementFixture.scala src/test/scala/oathdigital/gameplay/SiteDiscardFirstSuite.scala src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala src/test/scala/oathdigital/gameplay/powers/search/CropRotationSuite.scala src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogPowerLinesSuite.scala src/test/scala/oathdigital/application/AdviserLimitProjectionSuite.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala
git commit -m "refactor(cardplay): stack site discard permissions in CardPlayRules and project the adviser limit"
```

`git mv` already staged the deletions of `PlacementRules.scala` and `PlacementRulesSuite.scala`.

---

### Task 3: Name a play by what it does

Preparation for Task 6, with no change in behaviour. The Game Log and the tests stop spelling the placement buttons: `CardPlayProcedure.playedAs` says how an answer plays the card, the log reads that, and a test picks its answer through `PlacementPicks`. When Task 6 changes the options, only `playedAs` and the suites that inspect the old replacement decision change.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala` (add `PlayedAs`, `playedAs`)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala:212-250`, `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:56-62,153-162`
- Create: `src/test/scala/oathdigital/testkit/PlacementPicks.scala`
- Modify tests: `gameplay/powers/SearchFixture.scala`, `gameplay/powers/cardplay/VisionPlayFixture.scala`, `gameplay/CardPlayProcedureSuite.scala`, `gameplay/CardPlayHooksSuite.scala`, `gameplay/SearchProcedureSuite.scala`, `gameplay/MinorActionsSuite.scala`, `gameplay/VisionPlaySuite.scala`, `gameplay/powers/action/OracleSuite.scala`, `gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`, every caller of `SearchFixture.place`, `.play` and `.playFacedown`, `application/GameApplicationServiceActionsSuite.scala`, `application/GameApplicationServiceStreamSuite.scala`, `application/gamelog/LogScripts.scala`, `application/gamelog/GameLogDetailSuite.scala`, `application/gamelog/GameLogActionLineSuite.scala` (all under `src/test/scala/oathdigital/`)

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `CardPlayProcedure.PlayedAs` enum: `Discarded`, `AtSite`, `Faceup`, `Facedown`.
  - `CardPlayProcedure.playedAs(ref: DecisionOptionRef): Option[PlayedAs]`.
  - `testkit.PlacementPicks.offered(options: Vector[DecisionOption]): Vector[PlayedAs]`, `.find(options, way: PlayedAs): Option[DecisionOptionRef]`, `.pick(options, way)`, `.pick(decide: Decide, way)`, `.parkedOptions(state: OathState, powers: WalkerPowers, phasePowers: PhasePowers = PhasePowers.empty): Vector[DecisionOption]`, `.parkedAnswer(state, way, powers, phasePowers = PhasePowers.empty): DecisionAnswer.ChooseOneAnswer`.
  - `SearchFixture.place(from, card, way: PlayedAs)`, `.play(ready, modifiers, kept, way: PlayedAs)`, `.playFacedown(ready, modifiers, card, way: PlayedAs)`.
  - `LogScripts.facedownAdviser(name, placement: Option[PlayedAs])`, `LogScripts.oracle(name, placement: PlayedAs)`.

- [ ] **Step 1: Write the failing test**

Add to `CardPlayProcedureSuite` (import `oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs`):

```scala
  test("playedAs names how each placement button plays the card, and the " +
      "replacement decision's answers play nothing"):
    val keys = Vector("discard", "site", "adviser-faceup", "adviser-facedown")
    assertEquals(keys.map(key =>
      CardPlayProcedure.playedAs(DecisionOptionRef.Button(key))),
      Vector(Some(PlayedAs.Discarded), Some(PlayedAs.AtSite),
        Some(PlayedAs.Faceup), Some(PlayedAs.Facedown)))
    // The replacement decision answers with the card to discard, or declines.
    assertEquals(CardPlayProcedure.playedAs(DecisionOptionRef.Denizen(card)),
      None)
    assertEquals(CardPlayProcedure.playedAs(
      CardPlayProcedure.noReplacement.ref), None)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayProcedureSuite"`
Expected: compilation fails: `PlayedAs` and `playedAs` are not members of `CardPlayProcedure`.

- [ ] **Step 3: Add `PlayedAs` to the procedure**

In `CardPlayProcedure`, after `label`, add:

```scala
  /** How a placement answer plays the card, for the readers that name a play
    * by what it does rather than by its option: the Game Log, and tests. A
    * Vision played faceup and a played Conspiracy are `Faceup`.
    */
  enum PlayedAs:
    case Discarded, AtSite, Faceup, Facedown

  /** How the placement option `ref` plays the card; `None` for a ref no
    * placement decision offers. */
  def playedAs(ref: DecisionOptionRef): Option[PlayedAs] = ref match
    case `discard` => Some(PlayedAs.Discarded)
    case `site` => Some(PlayedAs.AtSite)
    case `adviserFaceUp` => Some(PlayedAs.Faceup)
    case `adviserFaceDown` => Some(PlayedAs.Facedown)
    case _ => None
```

- [ ] **Step 4: Read placements in the log through `playedAs`**

In `ActionLines.scala`, import `oathdigital.gameplay.actions.cardplay.CardPlayProcedure` and `CardPlayProcedure.PlayedAs`. In `playedAdviser`, the `collectFirst` case becomes:

```scala
          case (index, WalkerStepRecorded(_, ChoicePayload(id,
              ChooseOneAnswer(ref), _), _, _))
              if id.startsWith(PlacePrefix) &&
                CardPlayProcedure.playedAs(ref).nonEmpty =>
            (index, id.stripPrefix(PlacePrefix), ref)
```

and the `for` reads `(_, subject, ref) <- placed`, `card <- subjectCard(subject)`, `way <- CardPlayProcedure.playedAs(ref)`, and yields `played(card, way, before, after, run.actor, viewer)`. Replace `played` with:

```scala
  private def played(card: CardId, way: PlayedAs, before: ReadyGame,
      after: ReadyGame, actor: PlayerId, viewer: Option[PlayerId]): Posted =
    val named = words.one(words.card(card, before, after, viewer))
    way match
      // Named with its pile, as every other discard line is.
      case PlayedAs.Discarded => action(Vector(Text("Discarded ")) ++ named ++
        CardIndex.from(after.game).toOption.flatMap(_.get(card))
          .map(_.location.container).collect {
            case CardContainer.RegionalDiscard(region) =>
              Text(s" to the ${region.key.capitalize} discard")
          }.toVector)
      case PlayedAs.AtSite => action(Vector(Text("Played ")) ++ named ++
        LogJournal.pawnSite(before, actor).toVector.flatMap(site =>
          Vector(Text(" to "), words.site(site))))
      // A Vision played faceup takes the revealed Vision slot, not an
      // adviser's.
      case PlayedAs.Faceup if card.isInstanceOf[VisionId] =>
        action(Vector(Text("Revealed ")) ++ named)
      case _ => action(Vector(Text("Played ")) ++ named ++
        Vector(Text(" as an adviser")))
```

In `DetailLines.scala`, import the same two names, change the two calls to `placement(journal, run, at, PlayedAs.Discarded)` and `placement(journal, run, at, PlayedAs.Faceup)`, and replace `placement` with:

```scala
  /** The card Card Play's last placement answer played `way`, which its
    * action line already tells: "Discarded {card}" for the discard, and
    * "Played {card} as an adviser" for a facedown adviser turned faceup. */
  private def placement(journal: LogJournal, run: Run, at: Int, way: PlayedAs)
      : Option[CardId] =
    journal.answers(run, at).reverse.collectFirst {
      case Answered(id, ChooseOneAnswer(ref), _)
          if id.startsWith(ActionLines.PlacePrefix) &&
            CardPlayProcedure.playedAs(ref).contains(way) =>
        ActionLines.subjectCard(id.stripPrefix(ActionLines.PlacePrefix))
    }.flatten
```

Remove any import these edits leave unused (`DecisionOptionRef` in `DetailLines` if nothing else reads it).

- [ ] **Step 5: Add `PlacementPicks`**

Create `src/test/scala/oathdigital/testkit/PlacementPicks.scala`:

```scala
package oathdigital.testkit

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

/** A card play's placement answer, picked by how it plays the card. A test
  * says "play it faceup"; which option of the placement decision does that is
  * the engine's to say (`CardPlayProcedure.playedAs`), so a test that only
  * needs the play made never spells an option.
  */
object PlacementPicks:
  /** How each option of `options` that may be chosen plays the card, in
    * order. */
  def offered(options: Vector[DecisionOption]): Vector[PlayedAs] =
    options.filter(_.available).flatMap(option =>
      CardPlayProcedure.playedAs(option.ref))

  /** The first option of `options` that plays the card `way`, preferring one
    * that may be chosen: a test that expects a refusal still reaches the
    * engine, with the option it shows unavailable. */
  def find(options: Vector[DecisionOption], way: PlayedAs)
      : Option[DecisionOptionRef] =
    val matching = options.filter(option =>
      CardPlayProcedure.playedAs(option.ref).contains(way))
    matching.find(_.available).orElse(matching.headOption).map(_.ref)

  /** As `find`, failing the test when no option plays the card `way`. */
  def pick(options: Vector[DecisionOption], way: PlayedAs)(
      using munit.Location): DecisionOptionRef =
    find(options, way).getOrElse(munit.Assertions.fail(
      s"no option plays the card $way; offered ${options.map(_.ref)}"))

  /** As `pick`, over the choose-one `decide` asks. */
  def pick(decide: Decide, way: PlayedAs)(using munit.Location)
      : DecisionOptionRef =
    pick(chooseOne(decide), way)

  /** The options of the choose-one `state` is parked on, after the
    * restriction look-ahead, rebuilt with the powers the rules that parked it
    * were given; empty when it is parked on no choose-one. */
  def parkedOptions(state: OathState, powers: WalkerPowers,
      phasePowers: PhasePowers = PhasePowers.empty)(using munit.Location)
      : Vector[DecisionOption] =
    ParkedNode.of(state, FirstGameSetupFixture.catalog, powers,
        phasePowers) match
      case Left(reason) => munit.Assertions.fail(reason)
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query match
        case DecisionQuery.ChooseOne(options, _) => options
        case _ => Vector.empty
      case Right(_) => Vector.empty

  /** The answer that plays the card `way` at the placement `state` is parked
    * on. */
  def parkedAnswer(state: OathState, way: PlayedAs, powers: WalkerPowers,
      phasePowers: PhasePowers = PhasePowers.empty)(using munit.Location)
      : DecisionAnswer.ChooseOneAnswer =
    DecisionAnswer.ChooseOneAnswer(
      pick(parkedOptions(state, powers, phasePowers), way))

  private def chooseOne(decide: Decide)(using munit.Location)
      : Vector[DecisionOption] = decide.query match
    case DecisionQuery.ChooseOne(options, _) => options
    case other => munit.Assertions.fail(s"not a choose-one: $other")
```

- [ ] **Step 6: Move the shared fixtures to `PlayedAs`**

In `SearchFixture.scala` (import `CardPlayProcedure.PlayedAs`, `PlacementPicks` and `oathdigital.gameplay.walker.WalkerPowers`):

```scala
  private val walkerPowers: WalkerPowers = WalkerPowerCatalog.default(catalog)

  val rules: OathRules = new OathRules(catalog,
    walkerPowerCatalog = walkerPowers)
```

and replace `place`, the `button` parameters of `play` and `playFacedown`, and their docs:

```scala
  /** Places the card the way `way` names: the parked placement's first
    * option that plays it so, preferring one that may be chosen. A placement
    * with no such option refuses, as the engine would. */
  def place(from: OathTransition, card: WorldCardId, way: PlayedAs)(
      using munit.Location): Either[OathViolation, OathTransition] =
    PlacementPicks.find(PlacementPicks.parkedOptions(from.state, walkerPowers),
        way).toRight(OathViolation.InvalidSearchPlacement(
        s"the placement offers no way to play the card $way"))
      .flatMap(ref => rules.resolveWalker(from.state, actor,
        PlacementFixture.cardPlayDecisionId(card, "place"),
        DecisionAnswer.ChooseOneAnswer(ref)))
```

`play(ready, modifiers, kept, way: PlayedAs)` and `playFacedown(ready, modifiers, card, way: PlayedAs)` pass `way` to `place`; both gain `(using munit.Location)`. Leave `replace` alone until Task 6.

In `VisionPlayFixture.scala`, replace `offered` with:

```scala
  /** How the options of the placement the walk is parked on play the card,
    * after the restriction look-ahead. */
  def offered(from: OathTransition)(using munit.Location): Vector[PlayedAs] =
    PlacementPicks.offered(PlacementPicks.parkedOptions(from.state,
      WalkerPowerCatalog.default(catalog)))
```

- [ ] **Step 7: Move the callers**

Every quoted placement key handed to these helpers becomes its `PlayedAs`:

| Key | `PlayedAs` |
| --- | --- |
| `"discard"` | `PlayedAs.Discarded` |
| `"site"` | `PlayedAs.AtSite` |
| `"adviser-faceup"` | `PlayedAs.Faceup` |
| `"adviser-facedown"` | `PlayedAs.Facedown` |

Compile with `./sbtw "Test/compile"` and fix each error the signature changes report. Change only the keys handed to placement helpers: a Search partition section is also called `"discard"`, and `MusterProcedureSuite` hands `DecisionOptionRef.Button("site")` to a Muster source, which is not a card play. Then:

- `VowOfObedienceSuite`, `SecretPoliceSuite`, `SacredGroundSuite`: `offered(x).contains("adviser-faceup")` becomes `offered(x).contains(PlayedAs.Faceup)`, and likewise for the other keys.
- `OracleSuite`: `place(from, button: String)` becomes `place(from, way: PlayedAs) = rules.resolveWalker(from.state, actor, placement, PlacementPicks.parkedAnswer(from.state, way, WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog)))`; `buttons` becomes `PlacementPicks.offered(PlacementPicks.parkedOptions(from.state, WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog)))`, returning `Vector[PlayedAs]`, and its callers compare against `PlayedAs` values.
- `CardPlayHooksSuite.play(ready, played, button: String, origin)` takes `way: PlayedAs` and answers with `PlacementPicks.pick(present(ProcedureWalker.parkedDecide(ready, tree, parked, powers), "the placement decision"), way)`.
- `ConspiracyWhenPlayedSuite`, `SearchProcedureSuite`, `MinorActionsSuite`, `VisionPlaySuite`: each `ChooseOneAnswer(DecisionOptionRef.Button(key))` answering a `cardplay.place.*` decision becomes `PlacementPicks.parkedAnswer(<the state the command answers>, PlayedAs.X, <the walker powers of the rules issuing the command>)`. The powers are `WalkerPowerCatalog.default(catalog)` for rules built with it and `WalkerPowers.empty` for `new OathRules(catalog)`. A `val faceup = DecisionAnswer.ChooseOneAnswer(...)` shared by several tests becomes a `def faceup(state: OathState) = PlacementPicks.parkedAnswer(state, PlayedAs.Faceup, ...)`. Leave `VisionPlaySuite`'s three tests from "a Vision's placement offers Discard and both adviser plays" onward as they are; Task 6 rewrites them.
- `GameApplicationServiceActionsSuite`, `GameApplicationServiceStreamSuite`: the same, with the service's state (`started.state`, `chosen.state`, `position.state`) and `WalkerPowerCatalog.default(catalog)`.
- `LogScripts.scala`: `facedownAdviser(name, placement: Option[PlayedAs])` and `oracle(name, placement: PlayedAs)` answer the placement park with `ChooseOneAnswer(PlacementPicks.pick(park.decide, way))`; `oracle` with no arguments passes `PlayedAs.Facedown`; `familyHeirloom` answers with `PlayedAs.Faceup`. Update the callers in `GameLogDetailSuite` and `GameLogActionLineSuite` (`Some("adviser-faceup")` becomes `Some(PlayedAs.Faceup)`, `Some("site")` becomes `Some(PlayedAs.AtSite)`, `"adviser-faceup"` becomes `PlayedAs.Faceup`).

Leave these suites alone; Task 6 rewrites the parts that walk the old replacement decision: `CardPlayProcedureSuite` (apart from Step 1's test), `CardPlayRulesSuite`, `SiteDiscardFirstSuite`, `HomelandRuleSuite`, `HomelandLineSuite`, `CardPlayRestrictionsSuite`, `SilverTongueSuite`, `InsomniaSuite`, and the `PlacementFixture.answer` calls in `PeoplesFavorMobSuite`.

Check with `grep -rln "adviser-faceup\|adviser-facedown" src/test`: only the suites in the paragraph above and `VisionPlaySuite` (its last three tests) remain.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "test"`
Expected: PASS, with the server count up by one. The golden logs pass unchanged: the default answer still discards, and the log lines read the same answers.

Then run the two scripts. Expected: PASS.

- [ ] **Step 9: Commit**

Compare `git status --short` with the list below; stage a file Step 7's compile errors led to that is not listed, by its path.

```bash
git add src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/test/scala/oathdigital/testkit/PlacementPicks.scala
git add src/test/scala/oathdigital/gameplay/powers/SearchFixture.scala src/test/scala/oathdigital/gameplay/powers/cardplay/VisionPlayFixture.scala src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala src/test/scala/oathdigital/gameplay/CardPlayHooksSuite.scala src/test/scala/oathdigital/gameplay/SearchProcedureSuite.scala src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala
git add src/test/scala/oathdigital/gameplay/powers/action/OracleSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedDecisionAfterPlaySuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfObedienceSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/SacredGroundSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/SaddleMakersSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/BookBindersSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/WildCrySuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/GossipSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/WelcomingPartySuite.scala
git add src/test/scala/oathdigital/gameplay/powers/search/AugurySuite.scala src/test/scala/oathdigital/gameplay/powers/search/TruthfulHarpSuite.scala src/test/scala/oathdigital/gameplay/powers/search/CropRotationSuite.scala src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala src/test/scala/oathdigital/application/GameApplicationServiceActionsSuite.scala src/test/scala/oathdigital/application/GameApplicationServiceStreamSuite.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogDetailSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala
git commit -m "refactor(cardplay): name a placement by how it plays the card in the log and the tests"
```

---

### Task 4: The slot builder

Spec, Engine, "Slot builder". `CardSlots` builds the placement options over a rule-free planner, `CardPlay.operations`. Card play still asks the old buttons until Task 6, through a legacy section of `CardPlay` that checks the old rules and then calls the new planner; Task 6 deletes that section.

**Files:**
- Rewrite: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`
- Create: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala`
- Test: `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala` (new)

**Interfaces:**
- Consumes: Task 1's refs and `Replacing`; Task 2's `CardPlayRules`, `DiscardPermission`, `CardPlayRules.homeland`; `PlacementFixture.testPermission`.
- Produces:
  - `CardPlay.operations(catalog, ready, playerId: PlayerId, card: WorldCardId, placement: SearchPlacement, origin: CardPlay.Origin, more: Vector[CardId] = Vector.empty): Either[OathViolation, Vector[CoreOperation]]`.
  - `CardPlay.discardRegion(ready: ReadyGame, player: PlayerId): Option[Region]`.
  - `final case class CardSlot(option: DecisionOption, placement: SearchPlacement)` with `ref` and `replacedAtSite: Option[CardId]`.
  - `final case class MoreDiscards(locked: DecisionOption, options: Vector[DecisionOption], most: Int)`.
  - `CardSlots.play: DecisionOptionRef.Button` (`"play"`).
  - `CardSlots.slots(catalog, ready, actor, card, origin: CardPlay.Origin, rules: CardPlayRules): Vector[CardSlot]`.
  - `CardSlots.permissions(catalog, ready, actor, card, rules): Vector[DiscardPermission]`.
  - `CardSlots.moreDiscards(catalog, ready, actor, card, origin, rules, slot: CardSlot): Option[MoreDiscards]`.
  - `CardSlots.credits(catalog, ready, actor, card, rules, discarded: Vector[CardId]): Either[OathViolation, Vector[Note]]`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, CardPlayRules,
  DiscardPermission, RuleNotes, VisionRules}
import oathdigital.gameplay.actions.cardplay.{CardSlot, CardSlots,
  MoreDiscards}
import oathdigital.gameplay.powers.search.CropRotation
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table, TestCards}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** `CardSlots`: where a card may be played, as the options of its placement
  * decision (card play slots design, "Slot builder"). p1 stands at Ancient
  * City (Cradle, room for three, no Homeland) on the quiet table, with no
  * adviser, unless a test says otherwise; a discard there goes to the
  * Provinces.
  */
class CardSlotsSuite extends munit.FunSuite:
  import PlacementFixture.testPermission

  private val home = CatalogNames.site("Ancient City")
  /** The Beast Homeland, room for three. */
  private val woods = CatalogNames.site("Deep Woods")
  /** The plain Beast card p1 plays unless a test says otherwise. */
  private val card = CatalogNames.denizen("Errand Boy")
  // Plain cards of other suits.
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  private val binding = CatalogNames.denizen("Ancient Binding")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** Locked and adviser-only. */
  private val sealingWard = CatalogNames.denizen("Sealing Ward")
  /** Site-only. */
  private val curfew = CatalogNames.denizen("Curfew")
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val pile = DecisionOption.DiscardPile(
    DecisionOptionRef.DiscardPile(Region.Provinces))
  private val play = DecisionOption.Button(CardSlots.play, "Play")
  private val spot = DecisionOption.RevealedVision(
    DecisionOptionRef.RevealedVision(p1))
  /** A second plain site discard permission, besides `testPermission`. */
  private val second = DiscardPermission(PowerId("test.second-discard"),
    Note(PowerId("test.second-discard"), _ => None))
  private val permitted = CardPlayRules.default.permitSiteDiscard(testPermission)

  private def slotsOf(ready: ReadyGame, played: WorldCardId = card,
      origin: CardPlay.Origin = CardPlay.Origin.TemporaryHand,
      rules: CardPlayRules = CardPlayRules.default): Vector[CardSlot] =
    CardSlots.slots(catalog, ready, p1, played, origin, rules)

  private def options(slots: Vector[CardSlot]): Vector[DecisionOption] =
    slots.map(_.option)

  private def siteSlot(index: Int, at: SiteId = home): DecisionOption =
    DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(at, index))

  private def adviserPlay(index: Int, faceup: Boolean): DecisionOption =
    DecisionOption.AdviserPlay(DecisionOptionRef.AdviserPlay(p1, index, faceup))

  private def replacing(option: DecisionOption, replaced: CardId)
      : DecisionOption = DecisionOption.Replacing(option, replaced)

  private def refused(option: DecisionOption, reason: String): DecisionOption =
    DecisionOption.Unavailable(option, OptionReason.Written(reason))

  private def locked(option: DecisionOption): DecisionOption =
    refused(option, "Locked: cannot be discarded")

  /** The site slot options of `slots`, in order. */
  private def atSite(slots: Vector[CardSlot]): Vector[DecisionOption] =
    options(slots).filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])

  /** The adviser play options of `slots` with that facing, in order. */
  private def advisers(slots: Vector[CardSlot], faceup: Boolean)
      : Vector[DecisionOption] =
    options(slots).filter(_.ref match
      case DecisionOptionRef.AdviserPlay(_, _, up) => up == faceup
      case _ => false)

  /** p1 holds Errand Boy in hand at Ancient City, which holds `cards`. */
  private def atHome(cards: DenizenId*): Table =
    cards.foldLeft(Table.start.hand(p1, card))((table, held) =>
      table.denizen(held, at = home))

  /** p1 holds Errand Boy in hand and `held` as facedown advisers. */
  private def holding(held: DenizenId*): Table =
    held.foldLeft(Table.start.hand(p1, card))(_.adviser(p1, _, facedown = true))

  /** p1 rules Deep Woods, stands there with `played` in hand, and Deep Woods
    * holds `cards`. */
  private def atWoods(played: DenizenId, cards: DenizenId*): ReadyGame =
    cards.foldLeft(Table.start.pawn(p1, at = woods).warbandsAt(woods, p1, 1)
      .hand(p1, played))((table, held) => table.denizen(held, at = woods)).ready

  test("an empty table offers the discard pile, the first empty site slot " +
      "and the first adviser slot each way, in that order"):
    assertEquals(options(slotsOf(atHome().ready)), Vector(pile, siteSlot(0),
      adviserPlay(0, faceup = true), adviserPlay(0, faceup = false)))

  test("a site with room offers only its first empty slot when nothing " +
      "permits a discard"):
    assertEquals(atSite(slotsOf(atHome(rainBoots, binding).ready)),
      Vector(siteSlot(2)))

  test("a permission marks each site card Replace, after the empty slot"):
    val slots = slotsOf(atHome(rainBoots, binding).ready, rules = permitted)
    assertEquals(atSite(slots), Vector(siteSlot(2),
      replacing(siteSlot(0), rainBoots), replacing(siteSlot(1), binding)))
    assertEquals(slots.find(_.option == replacing(siteSlot(0), rainBoots))
      .map(_.placement), Some(SearchPlacement.Site(Some(rainBoots))))

  test("a full site is offered only as Replace slots, and not at all " +
      "without a permission"):
    val full = atHome(rainBoots, binding, wrestlers).ready
    assertEquals(atSite(slotsOf(full)), Vector.empty)
    assertEquals(atSite(slotsOf(full, rules = permitted)), Vector(
      replacing(siteSlot(0), rainBoots), replacing(siteSlot(1), binding),
      replacing(siteSlot(2), wrestlers)))

  test("the Homeland suit is the one the site prints"):
    assertEquals(CardPlay.homelandSuit(catalog, woods), Some(Suit.Beast))
    assertEquals(CardPlay.homelandSuit(catalog, home), None)
    // A variant Ancient City printing an Order Homeland keeps its handlers.
    val city = present(catalog.site(home), "Ancient City")
    val variant = catalog.copy(sites = catalog.sites.map(site =>
      if site.id == city.id then
        TestCards.siteLike(city)(homeland = Some(Suit.Order))
      else site))
    assertEquals(CardPlay.homelandSuit(variant, city.id), Some(Suit.Order))

  test("playing a card of the Homeland's suit there may replace any card at " +
      "that site, whatever its suit"):
    // Errand Boy is Beast and Deep Woods the Beast Homeland. Rain Boots is
    // Nomad and Wolves Beast: the replaced card's suit plays no part.
    val wolves = CatalogNames.denizen("Wolves")
    val beast = atWoods(card, rainBoots, wolves)
    assertEquals(atSite(slotsOf(beast)), Vector(siteSlot(2, woods),
      replacing(siteSlot(0, woods), rainBoots),
      replacing(siteSlot(1, woods), wolves)))
    assertEquals(CardSlots.permissions(catalog, beast, p1, card,
      CardPlayRules.default).map(_.source), Vector(RuleNotes.homelandDiscard))

  test("playing a card of another suit at a Homeland may replace nothing"):
    // Rain Boots is Nomad. The Beast Wolves at the site lends it no suit.
    val wolves = CatalogNames.denizen("Wolves")
    assertEquals(atSite(slotsOf(atWoods(rainBoots, wolves), rainBoots)),
      Vector(siteSlot(1, woods)))

  test("an intact edifice and a faceup locked adviser are shown Locked; a " +
      "facedown locked adviser is not"):
    val site = atHome(rainBoots).edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).ready
    assertEquals(atSite(slotsOf(site, rules = permitted)), Vector(siteSlot(2),
      replacing(siteSlot(0), rainBoots), locked(replacing(siteSlot(1), hall))))
    def full(lockedFacedown: Boolean) = holding(rainBoots, binding)
      .adviser(p1, sealingWard, facedown = lockedFacedown).ready
    assertEquals(advisers(slotsOf(full(lockedFacedown = false)), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding),
        locked(replacing(adviserPlay(2, faceup = true), sealingWard))))
    assert(advisers(slotsOf(full(lockedFacedown = true)), faceup = true)
      .forall(_.available))

  test("a card in use as a modifier shows why it stays"):
    val crop = CatalogNames.denizen("Crop Rotation")
    val ready = atHome(crop).update(_.updateCurrent(
      _.copy(walkerModifiers = Vector(CropRotation.id)))).ready
    assertEquals(atSite(slotsOf(ready, rules = permitted)), Vector(siteSlot(1),
      refused(replacing(siteSlot(0), crop), "In use as a modifier this action")))

  test("the Hall of Ministers protects its ruler's site from an enemy, and " +
      "says so"):
    // Threatening Roar is Beast, so Deep Woods' Homeland permits a discard.
    val roar = CatalogNames.denizen("Threatening Roar")
    val fillers = Vector("Fae Merchant", "Second Chance")
      .map(CatalogNames.denizen(_))
    def ruledBy(ruler: PlayerId) = fillers.foldLeft(Table.start
      .pawn(p1, at = woods).hand(p1, roar))(_.denizen(_, at = woods))
      .edifice(hall, EdificeSide.Intact, at = woods)
      .warbandsAt(woods, ruler, 1).ready
    val hallProtects = "Protected by the Hall of Ministers"
    assertEquals(atSite(slotsOf(ruledBy(p2), roar)), Vector(
      refused(replacing(siteSlot(0, woods), fillers(0)), hallProtects),
      refused(replacing(siteSlot(1, woods), fillers(1)), hallProtects),
      locked(replacing(siteSlot(2, woods), hall))))
    assert(atSite(slotsOf(ruledBy(p1), roar)).take(2).forall(_.available))

  test("a full adviser area is offered only as Replace, each adviser, each " +
      "facing"):
    val ready = holding(rainBoots, binding, wrestlers).ready
    Vector(true, false).foreach { faceup =>
      assertEquals(advisers(slotsOf(ready), faceup), Vector(
        replacing(adviserPlay(0, faceup), rainBoots),
        replacing(adviserPlay(1, faceup), binding),
        replacing(adviserPlay(2, faceup), wrestlers)), s"faceup = $faceup")
    }

  test("a lowered limit offers fewer slots, and nothing at or past it"):
    val limited = CardPlayRules.default.limitAdvisers(2)
    val two = holding(rainBoots, binding)
    assertEquals(advisers(slotsOf(two.ready), faceup = true),
      Vector(adviserPlay(2, faceup = true)))
    assertEquals(advisers(slotsOf(two.ready, rules = limited), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding)))
    val three = two.adviser(p1, wrestlers, facedown = true).ready
    Vector(true, false).foreach(faceup =>
      assertEquals(advisers(slotsOf(three, rules = limited), faceup),
        Vector.empty, s"faceup = $faceup"))

  test("the played card's own limit lowers only its faceup plays"):
    val rules = CardPlayRules.default.limitPlayedCard(2)
    val two = holding(rainBoots, binding).ready
    assertEquals(advisers(slotsOf(two, rules = rules), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), rainBoots),
        replacing(adviserPlay(1, faceup = true), binding)))
    assertEquals(advisers(slotsOf(two, rules = rules), faceup = false),
      Vector(adviserPlay(2, faceup = false)))

  test("a Vision never goes to a site, and faceup it goes to the revealed " +
      "Vision spot, Replace when one is there"):
    val faith = VisionRules.Faith
    assertEquals(options(slotsOf(Table.start.hand(p1, faith).ready, faith)),
      Vector(pile, spot, adviserPlay(0, faceup = false)))
    val over = Table.start.hand(p1, faith)
      .revealedVision(p1, VisionRules.Conquest).ready
    assertEquals(options(slotsOf(over, faith)).filter(_.ref == spot.ref),
      Vector(replacing(spot, VisionRules.Conquest)))

  test("a faceup Conspiracy is Play, and takes no slot"):
    val conspiracy = VisionRules.Conspiracy
    val ready = Table.start.hand(p1, conspiracy)
      .revealedVision(p1, VisionRules.Faith).ready
    val slots = slotsOf(ready, conspiracy)
    assertEquals(options(slots), Vector(pile, play,
      adviserPlay(0, faceup = false)))
    val played = present(slots.find(_.ref == CardSlots.play), "Play")
    assertEquals(CardPlay.operations(catalog, ready, p1, conspiracy,
      played.placement, CardPlay.Origin.TemporaryHand), Right(Vector.empty))

  test("a site-only card goes to advisers facedown only; an adviser-only " +
      "card never to a site"):
    assertEquals(options(slotsOf(Table.start.hand(p1, curfew).ready, curfew)),
      Vector(pile, siteSlot(0), adviserPlay(0, faceup = false)))
    assertEquals(options(slotsOf(Table.start.hand(p1, sealingWard).ready,
      sealingWard)), Vector(pile, adviserPlay(0, faceup = true),
      adviserPlay(0, faceup = false)))

  test("a facedown adviser is offered its own slot faceup, and no other " +
      "adviser slot"):
    val ready = Table.start.adviser(p1, wrestlers, facedown = true)
      .adviser(p1, rainBoots, facedown = true).ready
    assertEquals(options(slotsOf(ready, rainBoots,
      CardPlay.Origin.FacedownAdviser)),
      Vector(pile, siteSlot(0), adviserPlay(1, faceup = true)))

  test("a facedown Vision goes to the revealed Vision spot, and a facedown " +
      "Conspiracy is Play"):
    val ready = Table.start.adviser(p1, VisionRules.Faith, facedown = true)
      .adviser(p1, VisionRules.Conspiracy, facedown = true)
      .revealedVision(p1, VisionRules.Conquest).ready
    assertEquals(options(slotsOf(ready, VisionRules.Faith,
      CardPlay.Origin.FacedownAdviser)),
      Vector(pile, replacing(spot, VisionRules.Conquest)))
    assertEquals(options(slotsOf(ready, VisionRules.Conspiracy,
      CardPlay.Origin.FacedownAdviser)), Vector(pile, play))

  test("a facedown adviser turned faceup must fit its own limit"):
    val rules = CardPlayRules.default.limitPlayedCard(2)
    val three = Vector(rainBoots, binding, wrestlers)
      .foldLeft(Table.start)(_.adviser(p1, _, facedown = true)).ready
    assertEquals(advisers(slotsOf(three, wrestlers,
      CardPlay.Origin.FacedownAdviser, rules), faceup = true), Vector.empty)
    val two = Vector(rainBoots, binding)
      .foldLeft(Table.start)(_.adviser(p1, _, facedown = true)).ready
    assertEquals(advisers(slotsOf(two, binding,
      CardPlay.Origin.FacedownAdviser, rules), faceup = true),
      Vector(adviserPlay(1, faceup = true)))

  /** Ancient City full: Rain Boots, Ancient Binding and the ruined Hall. */
  private def fullWithHall: ReadyGame = atHome(rainBoots, binding)
    .edifice(hall, EdificeSide.Ruined, at = home).ready
  private val two = permitted.permitSiteDiscard(second)

  private def replacingBoots(ready: ReadyGame, rules: CardPlayRules)
      : CardSlot = present(slotsOf(ready, rules = rules)
    .find(_.option == replacing(siteSlot(0), rainBoots)), "the Replace slot")

  test("after a Replace site slot, the other site cards are offered, an " +
      "edifice included, up to the permissions left"):
    val ready = fullWithHall
    assertEquals(CardSlots.moreDiscards(catalog, ready, p1, card,
      CardPlay.Origin.TemporaryHand, two, replacingBoots(ready, two)),
      Some(MoreDiscards(DecisionOption.Denizen(
        DecisionOptionRef.Denizen(rainBoots)), Vector(
          DecisionOption.Denizen(DecisionOptionRef.Denizen(binding)),
          DecisionOption.Edifice(DecisionOptionRef.Edifice(hall))), 1)))
    // A single permission is spent by the slot itself.
    assertEquals(CardSlots.moreDiscards(catalog, ready, p1, card,
      CardPlay.Origin.TemporaryHand, permitted,
      replacingBoots(ready, permitted)), None)

  test("the credits name the permission each discard spent, in discard " +
      "order, and refuse more discards than permissions"):
    val ready = fullWithHall
    assertEquals(CardSlots.credits(catalog, ready, p1, card, two,
      Vector(hall, rainBoots)), Right(Vector(testPermission.note,
        second.note)))
    assert(CardSlots.credits(catalog, ready, p1, card, permitted,
      Vector(rainBoots, binding)).isLeft)

  test("a play discards every site card named, the replaced one first, " +
      "before the play"):
    val ready = atHome(rainBoots, binding).ready
    val operations = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(rainBoots)), CardPlay.Origin.TemporaryHand,
      Vector(binding)))
    assertEquals(operations.collect { case discard: Discard.Denizen =>
      discard.card }, Vector[CardId](rainBoots, binding))
    assert(operations.indexWhere(_.isInstanceOf[Play]) >
      operations.lastIndexWhere(_.isInstanceOf[Discard.Denizen]))
    assert(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(rainBoots)), CardPlay.Origin.TemporaryHand,
      Vector(rainBoots)).isLeft, "a card is discarded twice")

  test("a ruined edifice is discarded back to the edifice deck, not buried"):
    val ready = atHome(rainBoots).edifice(hall, EdificeSide.Ruined, at = home)
      .ready
    val operations = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(Some(hall)), CardPlay.Origin.TemporaryHand))
    assert(operations.exists(_.isInstanceOf[Discard.RuinedEdifice]))
    assert(!operations.exists(_.isInstanceOf[Bury]))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite"`
Expected: compilation fails: `CardSlots`, `CardSlot`, `MoreDiscards`, `CardPlay.operations` and `CardPlay.discardRegion` are not found.

- [ ] **Step 3: Rewrite `CardPlay.scala`**

Replace the whole file with the following. The section after the `// ---- Until` line keeps today's `legalChoices` and `plannedOperations` working for `CardPlayProcedure` and the old suites; Task 6 deletes it.

```scala
package oathdigital.gameplay.actions

import oathdigital.catalog.{AdviserOnly, ExecutableCatalog, SiteOnly}
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model.OathViolation._
import oathdigital.model._

/** The operations of one card play, planned from where the card goes.
  *
  * Which plays the rules allow is `cardplay.CardSlots`'s to say: it reads the
  * site discard permissions and the adviser limits. This refuses only what no
  * rule changes: a Vision or an adviser-only card at a site, a site-only card
  * faceup, a facedown adviser played facedown or replacing an adviser, a full
  * site with nothing discarded, and a discard of a card that is not there. It
  * plans the rest as core operations: the play, its favor, and every card it
  * discards.
  */
object CardPlay:
  enum Origin { case FacedownAdviser, TemporaryHand }

  /** The suit whose Homeland `site` is, as the site prints it (CR p. 31). */
  def homelandSuit(catalog: ExecutableCatalog, site: SiteId): Option[Suit] =
    catalog.site(site).flatMap(_.homeland)

  /** The region whose discard pile takes a card `player` discards in a play:
    * the one after their pawn's. */
  def discardRegion(ready: ReadyGame, player: PlayerId): Option[Region] =
    ready.game.current.players.find(_.player == player).flatMap(_.pawnSite)
      .flatMap(ready.game.current.map.regionOf).map(nextRegion)

  /** The operations of playing `card` from `origin` to `placement`, which
    * also discards `more` from the actor's site. Site discards run first,
    * then play-area discards, then the play, so the played card can take a
    * slot they free; the favor comes last.
    */
  def operations(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, more: Vector[CardId] = Vector.empty)
      : Either[OathViolation, Vector[CoreOperation]] = for
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(InvalidSearchPlacement("player is not in the game"))
    _ <- validateOrigin(player, card, origin)
    plan <- plan(catalog, ready, player, card, placement, origin)
    extra <- atSite(ready, player, more)
    _ <- Either.cond(!extra.exists(plan.siteDiscards.contains), (),
      InvalidSearchPlacement("a site card is discarded twice"))
    operations <- operationsOf(catalog, ready, player, card, placement,
      origin, plan.copy(siteDiscards = plan.siteDiscards ++ extra))
  yield operations

  def playedSource(ready: ReadyGame, playerId: PlayerId, card: WorldCardId,
      placement: SearchPlacement): Option[RuleSourceRef] = placement match
    case SearchPlacement.Adviser(Orientation.FaceUp, _) =>
      Some(RuleSourceRef.Adviser(playerId, card))
    case SearchPlacement.Site(_) => ready.game.current.players
      .find(_.player == playerId).flatMap(_.pawnSite)
      .map(RuleSourceRef.SiteCard(_, card))
    case _ => None

  private def keptSource(origin: Origin, player: PlayerId): PositionedLocation =
    origin match
      case Origin.TemporaryHand =>
        PositionedLocation(Location.Hand(player))
      case Origin.FacedownAdviser => PositionedLocation(Location.PlayArea(player))

  private def validateOrigin(player: PlayerState, card: WorldCardId,
      origin: Origin): Either[OathViolation, Unit] = origin match
    case Origin.TemporaryHand => Right(())
    case Origin.FacedownAdviser =>
      player.advisers.find(_.id == card).filter {
        case DenizenState(_, Orientation.FaceDown, _) => true
        case VisionState(_, Orientation.FaceDown) => true
        case _ => false
      }.toRight(MinorActionUnavailable(
        "adviser is not held facedown by the actor")).map(_ => ())

  /** What one play moves: the card it keeps in play, the favor it gains, the
    * play-area cards it discards (a replaced adviser, or the revealed Vision a
    * new one displaces) and the site cards it discards. */
  private final case class PlacementPlan(kept: Option[CoreOperation],
      favor: Vector[CoreOperation], fromPlayArea: Vector[WorldCardId],
      siteDiscards: Vector[SiteDenizenState])

  private val nothing =
    PlacementPlan(None, Vector.empty, Vector.empty, Vector.empty)

  private def plan(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin): Either[OathViolation, PlacementPlan] = placement match
    case SearchPlacement.Discard => Right(nothing)
    case SearchPlacement.Site(replace) => card match
      case _: VisionId =>
        Left(InvalidSearchPlacement("Visions cannot be played to sites"))
      case id: DenizenId =>
        for
          definition <- catalog.denizen(id).toRight(UnknownWorldCard(id))
          _ <- Either.cond(!definition.isInstanceOf[AdviserOnly], (),
            InvalidSearchPlacement(
              "adviser-only card cannot be played to a site"))
          siteId <- player.pawnSite.toRight(PawnSiteMissing(player.player))
          site <- ready.game.current.map.sites.get(siteId)
            .toRight(InvalidSearchPlacement("pawn site is not in play"))
          replaced <- atSite(ready, player, replace.toVector)
          capacity = catalog.site(siteId).map(_.capacity).getOrElse(0)
          _ <- Either.cond(site.denizens.size - replaced.size < capacity, (),
            InvalidSearchPlacement("a full site requires a site-card discard"))
        yield sitePlan(ready, origin, player, id, siteId, definition.suit,
          replaced)
    case SearchPlacement.Adviser(orientation, replace) => card match
      case id: DenizenId =>
        for
          definition <- catalog.denizen(id).toRight(UnknownWorldCard(id))
          _ <- Either.cond(origin != Origin.FacedownAdviser ||
            (orientation == Orientation.FaceUp && replace.isEmpty), (),
            InvalidSearchPlacement(
              "facedown adviser must be played faceup to advisers or the pawn's site"))
          _ <- Either.cond(!(orientation == Orientation.FaceUp &&
            definition.isInstanceOf[SiteOnly]), (),
            InvalidSearchPlacement(
              "site-only card can only be held as a facedown adviser"))
          removed <- adviserHeld(player, replace)
        yield adviserPlan(origin, player, id, orientation, removed)
      case id: VisionId =>
        if !FirstGameRulesData.visions.contains(id) then Left(UnknownWorldCard(id))
        else planVision(player, origin, id, orientation, replace)

  private def sitePlan(ready: ReadyGame, origin: Origin, player: PlayerState,
      id: DenizenId, siteId: SiteId, suit: Suit,
      replaced: Vector[SiteDenizenState]): PlacementPlan =
    val kept = Some(Play(id, keptSource(origin, player.player),
      Location.Site(siteId), Orientation.FaceUp, required = true))
    val gain = LimitedResource.clamp(ready.banks.favor.getOrElse(suit, 0), 1)
    val favor = if gain == 1 then
      Vector(Gain.Favor(player.player, suit, 1)) else Vector.empty
    PlacementPlan(kept, favor, Vector.empty, replaced)

  private def adviserPlan(origin: Origin, player: PlayerState, id: DenizenId,
      orientation: Orientation, removed: Option[WorldCardId]): PlacementPlan =
    PlacementPlan(Some(Play(id, keptSource(origin, player.player),
      Location.PlayArea(player.player), orientation, required = true)),
      Vector.empty, removed.toVector, Vector.empty)

  private def planVision(player: PlayerState, origin: Origin, id: VisionId,
      orientation: Orientation, replace: Option[CardId])
      : Either[OathViolation, PlacementPlan] = origin match
    case _ if id == VisionRules.Conspiracy && orientation == Orientation.FaceUp =>
      // A played Conspiracy is boxed by its WHEN PLAYED power, so from either
      // origin it takes no slot and replaces nothing.
      Either.cond(replace.isEmpty, nothing,
        InvalidSearchPlacement("a played Conspiracy replaces nothing"))
    case Origin.FacedownAdviser =>
      Either.cond(orientation == Orientation.FaceUp && replace.isEmpty,
        revealed(player, origin, id), InvalidSearchPlacement(
          "facedown adviser must be played faceup to advisers or the pawn's site"))
    case Origin.TemporaryHand if orientation == Orientation.FaceUp =>
      // The displacement is forced: a player has at most one revealed Vision.
      // An answer that names it is still accepted.
      val expected = player.revealedVision.map(_.id)
      Either.cond(replace.isEmpty || replace == expected,
        revealed(player, origin, id),
        InvalidSearchPlacement("a Vision replaces only the revealed Vision"))
    case Origin.TemporaryHand =>
      adviserHeld(player, replace).map(removed => PlacementPlan(
        Some(Play(id, keptSource(origin, player.player),
          Location.PlayArea(player.player), Orientation.FaceDown,
          required = true)), Vector.empty, removed.toVector, Vector.empty))

  /** `id` played faceup as the revealed Vision, displacing the one there. */
  private def revealed(player: PlayerState, origin: Origin, id: VisionId)
      : PlacementPlan =
    PlacementPlan(Some(Play(id, keptSource(origin, player.player),
      Location.PlayArea(player.player), Orientation.FaceUp, required = true)),
      Vector.empty, player.revealedVision.toVector.map(_.id), Vector.empty)

  /** The adviser `replace` names, which the actor must hold. */
  private def adviserHeld(player: PlayerState, replace: Option[CardId])
      : Either[OathViolation, Option[WorldCardId]] =
    replace.fold[Either[OathViolation, Option[WorldCardId]]](Right(None)) {
      id => player.advisers.find(_.id == id)
        .toRight(InvalidSearchPlacement("replacement adviser is not held"))
        .flatMap(held => held.id match
          case world: WorldCardId => Right(Some(world))
          case _ => Left(InvalidSearchPlacement(
            "replacement adviser is not a world card")))
    }

  /** The cards `ids` names at the actor's site, in that order, each once. */
  private def atSite(ready: ReadyGame, player: PlayerState, ids: Vector[CardId])
      : Either[OathViolation, Vector[SiteDenizenState]] =
    val held = player.pawnSite.flatMap(ready.game.current.map.sites.get)
      .fold(Vector.empty[SiteDenizenState])(_.denizens)
    ids.foldLeft[Either[OathViolation, Vector[SiteDenizenState]]](
        Right(Vector.empty)) { (found, id) =>
      found.flatMap(cards => held.find(_.id == id).filterNot(cards.contains)
        .toRight(InvalidSearchPlacement(
          "a discarded card is not at the site, or is named twice"))
        .map(cards :+ _))
    }

  private def operationsOf(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, plan: PlacementPlan)
      : Either[OathViolation, Vector[CoreOperation]] =
    val playArea = PositionedLocation(Location.PlayArea(player.player))
    val played = if placement == SearchPlacement.Discard then
      Vector(card -> keptSource(origin, player.player)) else Vector.empty
    for
      siteId <- player.pawnSite.toRight(PawnSiteMissing(player.player))
      region <- ready.game.current.map.regionOf(siteId).map(nextRegion)
        .toRight(InvalidSearchPlacement("pawn site is not in play"))
      site = PositionedLocation(Location.Site(siteId))
      fromSite <- each(plan.siteDiscards)(
        siteDiscard(catalog, player.player, site, region))
      fromPlay <- each(plan.fromPlayArea.map(_ -> playArea) ++ played) {
        (id, from) => selectedDiscard(catalog, ready, player.player, id, from,
          region)
      }
    yield fromSite ++ fromPlay ++ plan.kept.toVector ++ plan.favor

  private def each[A](items: Vector[A])(
      discard: A => Either[OathViolation, CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    items.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
      Right(Vector.empty))((done, item) =>
      done.flatMap(operations => discard(item).map(operations :+ _)))

  /** A site card's discard: a denizen to `region`'s pile with its tokens
    * returned, an edifice back to the edifice deck. */
  private def siteDiscard(catalog: ExecutableCatalog, actor: PlayerId,
      site: PositionedLocation, region: Region)(card: SiteDenizenState)
      : Either[OathViolation, CoreOperation] =
    suitOf(catalog, card.id).map(suit => card match
      case denizen: DenizenState => Discard.Denizen(denizen.id, site, region,
        suit, denizen.tokens.favor, denizen.tokens.secrets, actor,
        required = true)
      case edifice: EdificeState => Discard.RuinedEdifice(edifice.id, site,
        suit, edifice.tokens.favor, edifice.tokens.secrets, actor,
        required = true))

  private def suitOf(catalog: ExecutableCatalog, id: CardId)
      : Either[OathViolation, Suit] =
    catalog.suitOf(id)
      .toRight(InvalidSearchPlacement("catalog suit is unknown"))

  private def selectedDiscard(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, from: PositionedLocation,
      destination: Region): Either[OathViolation, CoreOperation] = card match
    case id: VisionId => Right(Discard.Vision(id, from, destination,
      required = true))
    case id: DenizenId =>
      val suit = catalog.suitOf(id).toRight(UnknownWorldCard(id))
      val held = ready.game.current.players.find(_.player == actor)
        .toVector.flatMap(_.advisers).collectFirst:
          case value: DenizenState if value.id == id => value.tokens
      val tokens = held.getOrElse(Tokens.empty)
      suit.map(value => Discard.Denizen(id, from, destination, value,
        tokens.favor, tokens.secrets, actor, required = true))

  /** The region whose discard pile receives a card discarded at a site of
    * `region`.
    */
  def nextRegion(region: Region): Region = region match
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle

  // ---- Until card play offers card slots (Task 6): the old choices. ----

  /** One legal placement. `replacements` are the cards the play may discard
    * first. They are required when the placement is otherwise impossible, and
    * `replacementOptional` says the play is also legal with no discard.
    */
  final case class Choice(placement: SearchPlacement,
      replacements: Vector[CardId], replacementOptional: Boolean = false)

  def legalChoices(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: Origin,
      rules: CardPlayRules = CardPlayRules.default): Vector[Choice] =
    val player = ready.game.current.players.find(_.player == actor)
    val restrictions = OperationRestrictions.forCatalog(catalog)
      .active(Vector.empty, ready.game.current.walkerModifiers)
    def permitted(operations: Vector[CoreOperation]): Boolean =
      operations.forall(operation =>
        restrictions.forall(_.reason(ready, operation).isEmpty))
    val placements = Vector[SearchPlacement](SearchPlacement.Discard,
      SearchPlacement.Site(None),
      SearchPlacement.Adviser(Orientation.FaceUp, None),
      SearchPlacement.Adviser(Orientation.FaceDown, None))
    placements.flatMap { placement =>
      val direct = plannedOperations(catalog, ready, actor, card,
        placement, origin, rules).exists(permitted)
      val candidateIds: Vector[CardId] = placement match
        case _: SearchPlacement.Site => player.toVector.flatMap(_.pawnSite)
          .flatMap(ready.game.current.map.sites.get)
          .flatMap(_.denizens.map(_.id))
        case SearchPlacement.Adviser(Orientation.FaceUp, _)
            if card.isInstanceOf[VisionId] =>
          player.toVector.flatMap(_.revealedVision).map(_.id)
        case _: SearchPlacement.Adviser => player.toVector.flatMap(_.advisers)
          .filterNot(value => origin == Origin.FacedownAdviser &&
            value.id == card).map(_.id)
        case SearchPlacement.Discard => Vector.empty
      val optional = direct && (placement match
        case _: SearchPlacement.Site => player.flatMap(_.pawnSite).exists(site =>
          catalog.suitOf(card).exists(siteDiscardAllowed(catalog, site, _, rules)))
        case _ => false)
      val replacements = if direct && !optional then Vector.empty
      else candidateIds.filter { id =>
        val selected = placement match
          case _: SearchPlacement.Site => SearchPlacement.Site(Some(id))
          case value: SearchPlacement.Adviser => value.copy(replace = Some(id))
          case SearchPlacement.Discard => SearchPlacement.Discard
        plannedOperations(catalog, ready, actor, card, selected,
          origin, rules).exists(permitted)
      }
      Option.when(direct || replacements.nonEmpty)(Choice(placement,
        replacements, replacementOptional = optional && replacements.nonEmpty))
    }

  /** `operations`, refused unless the old placement rules allow it. */
  def plannedOperations(catalog: ExecutableCatalog, ready: ReadyGame,
      playerId: PlayerId, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, rules: CardPlayRules = CardPlayRules.default)
      : Either[OathViolation, Vector[CoreOperation]] = for
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(InvalidSearchPlacement("player is not in the game"))
    _ <- oldRules(catalog, ready, player, card, placement, origin, rules)
    operations <- operations(catalog, ready, playerId, card, placement, origin)
  yield operations

  private def siteDiscardAllowed(catalog: ExecutableCatalog, site: SiteId,
      suit: Suit, rules: CardPlayRules): Boolean =
    rules.siteDiscards.nonEmpty || homelandSuit(catalog, site).contains(suit)

  /** The site discard and adviser limit rules `operations` leaves to its
    * caller, as card play read them before card slots. */
  private def oldRules(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, placement: SearchPlacement,
      origin: Origin, rules: CardPlayRules): Either[OathViolation, Unit] =
    placement match
      case SearchPlacement.Site(replace) =>
        (for
          siteId <- player.pawnSite
          site <- ready.game.current.map.sites.get(siteId)
          suit <- catalog.suitOf(card)
        yield
          val capacity = catalog.site(siteId).map(_.capacity).getOrElse(0)
          if siteDiscardAllowed(catalog, siteId, suit, rules) then Right(())
          else if site.denizens.size >= capacity then Left(
            InvalidSearchPlacement(
              "a full site takes a card only at the Homeland of its suit"))
          else if replace.nonEmpty then Left(InvalidSearchPlacement(
            "a site discard needs the Homeland of the card's suit or a power's permission"))
          else Right(())).getOrElse(Right(()))
      case SearchPlacement.Adviser(orientation, replace) =>
        val limited = card match
          case _: DenizenId => true
          case _: VisionId => origin == Origin.TemporaryHand &&
            orientation == Orientation.FaceDown
        val remaining = if origin == Origin.FacedownAdviser then
          player.advisers.filter(_.id != card) else player.advisers
        val mustReplace = remaining.size >=
          rules.limitAfter(orientation == Orientation.FaceUp)
        if !limited || mustReplace == replace.nonEmpty then Right(())
        else Left(InvalidSearchPlacement(
          if mustReplace then "a full adviser area requires a discard"
          else "an adviser cannot be discarded when there is free capacity"))
      case SearchPlacement.Discard => Right(())
```

The planner's operation order is today's: a site discard (edifice or denizen) runs before a play-area discard, which runs before the play and its favor. A site denizen's discard reads its tokens from its own state, as today's token path did.

- [ ] **Step 4: Write `CardSlots.scala`**

Create `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala`:

```scala
package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{CardPlay, CardPlayRules,
  DiscardPermission, VisionRules}
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.model._

/** One way to play a card: the option the placement decision offers, and
  * where the planner puts the card when it is chosen. A Replace option's
  * placement names the card it discards.
  */
final case class CardSlot(option: DecisionOption, placement: SearchPlacement):
  def ref: DecisionOptionRef = option.ref

  /** The site card this play discards to make room, for a Replace site
    * slot. */
  def replacedAtSite: Option[CardId] = placement match
    case SearchPlacement.Site(replaced) => replaced
    case _ => None

/** The follow-up of a Replace site slot: the replaced card, shown chosen; the
  * other site cards the permissions left may still cover; and how many more
  * may go. */
final case class MoreDiscards(locked: DecisionOption,
    options: Vector[DecisionOption], most: Int)

/** Where a card may be played, as card slots (card play slots design, "Slot
  * builder").
  *
  * The options, in order: the discard pile; the site's first empty slot,
  * then, when a permission allows a site discard, each site card marked
  * Replace; the faceup plays;
  * the facedown plays. An adviser play offers the first empty slot below the
  * limit or, with none, every held adviser marked Replace, and nothing at or
  * past the limit. A facedown adviser offers only its own slot, faceup. A
  * Vision played faceup goes to the revealed Vision spot, and a Conspiracy
  * played faceup is the "Play" button.
  *
  * Each option is planned with `CardPlay.operations`, and the printed
  * restrictions in force judge the plan: an option a protection refuses is
  * shown unavailable with its reason; an option the planner or another
  * restriction refuses is left out. Powers' own restrictions (Vow of
  * Obedience) are the walker look-ahead's to apply.
  */
object CardSlots:
  /** The answer that plays a faceup Conspiracy, which takes no slot. */
  val play: DecisionOptionRef.Button = DecisionOptionRef.Button("play")

  /** Why a printed protection refuses a play, by its restriction's code. */
  private val reasons: Map[String, String] = Map(
    "locked" -> "Locked: cannot be discarded",
    "active-modifier" -> "In use as a modifier this action",
    "discard-immune" -> "Protected by the Hall of Ministers")

  def slots(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: CardPlay.Origin, rules: CardPlayRules)
      : Vector[CardSlot] =
    ready.game.current.players.find(_.player == actor).toVector.flatMap {
      player =>
        val candidates = discardPile(ready, actor).toVector ++
          siteSlots(catalog, ready, player, card, rules) ++
          faceup(player, card, origin, rules) ++
          facedown(player, card, origin, rules)
        candidates.flatMap { (option, placement) =>
          judged(catalog, ready, option, CardPlay.operations(catalog, ready,
            actor, card, placement, origin)).map(CardSlot(_, placement))
        }
    }

  /** The site discard permissions of `actor`'s play of `card`: the Homeland
    * rule's first, when the actor's site is the Homeland of the card's suit,
    * then the powers'. */
  def permissions(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, rules: CardPlayRules)
      : Vector[DiscardPermission] =
    val homeland = for
      site <- ready.game.current.players.find(_.player == actor)
        .flatMap(_.pawnSite)
      suit <- catalog.suitOf(card)
      if CardPlay.homelandSuit(catalog, site).contains(suit)
    yield CardPlayRules.homeland(site, actor)
    homeland.toVector ++ rules.siteDiscards

  /** The follow-up of `slot`, a Replace site slot: the other site cards,
    * an edifice included, and how many more the permissions left allow;
    * `None` when nothing more may be discarded. */
  def moreDiscards(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: CardPlay.Origin,
      rules: CardPlayRules, slot: CardSlot): Option[MoreDiscards] =
    val permitted = permissions(catalog, ready, actor, card, rules)
    val held = siteCards(ready, actor)
    for
      replacedId <- slot.replacedAtSite
      replaced <- held.find(_.id == replacedId)
      if permitted.size > 1
      options = held.filter(_.id != replacedId).flatMap(other =>
        judged(catalog, ready, optionOf(other), CardPlay.operations(catalog,
          ready, actor, card, slot.placement, origin, Vector(other.id))))
      most = math.min(permitted.size - 1, options.count(_.available))
      if most >= 1
    yield MoreDiscards(optionOf(replaced), options, most)

  /** The line of the permission each discarded site card spent, in discard
    * order: permissions are spent in the order `permissions` gives them, and
    * every one covers any site card. Refused when more cards are discarded
    * than there are permissions. */
  def credits(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, rules: CardPlayRules, discarded: Vector[CardId])
      : Either[OathViolation, Vector[Note]] =
    val permitted = permissions(catalog, ready, actor, card, rules)
    Either.cond(discarded.size <= permitted.size,
      permitted.take(discarded.size).map(_.note),
      OathViolation.InvalidSearchPlacement(
        "more site cards are discarded than the permissions in play allow"))

  /** `option` as the restrictions in force leave its plan: as it is, shown
    * unavailable with a protection's reason, or left out. */
  private def judged(catalog: ExecutableCatalog, ready: ReadyGame,
      option: DecisionOption,
      planned: Either[OathViolation, Vector[CoreOperation]])
      : Option[DecisionOption] =
    planned.toOption.flatMap { operations =>
      val restrictions = OperationRestrictions.forCatalog(catalog)
        .active(Vector.empty, ready.game.current.walkerModifiers)
      operations.iterator.flatMap(operation => restrictions.iterator
        .flatMap(_.reason(ready, operation))).nextOption() match
        case None => Some(option)
        case Some(refusal) => reasons.get(refusal.code).map(reason =>
          DecisionOption.Unavailable(option, OptionReason.Written(reason)))
    }

  private def discardPile(ready: ReadyGame, actor: PlayerId)
      : Option[(DecisionOption, SearchPlacement)] =
    CardPlay.discardRegion(ready, actor).map(region =>
      DecisionOption.DiscardPile(DecisionOptionRef.DiscardPile(region)) ->
        SearchPlacement.Discard)

  private def siteSlots(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    (for
      siteId <- player.pawnSite
      site <- ready.game.current.map.sites.get(siteId)
      capacity <- catalog.site(siteId).map(_.capacity)
    yield
      def slot(index: Int): DecisionOption =
        DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(siteId, index))
      val permitted = permissions(catalog, ready, player.player, card, rules)
      val empty = Option.when(site.denizens.size < capacity)(
        slot(site.denizens.size) -> SearchPlacement.Site(None))
      val replacing = if permitted.isEmpty then Vector.empty
        else site.denizens.zipWithIndex.map((held, index) =>
          DecisionOption.Replacing(slot(index), held.id) ->
            SearchPlacement.Site(Some(held.id)))
      empty.toVector ++ replacing).getOrElse(Vector.empty)

  private def faceup(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    val placement = SearchPlacement.Adviser(Orientation.FaceUp, None)
    card match
      case VisionRules.Conspiracy =>
        Vector(DecisionOption.Button(play, "Play") -> placement)
      case _: VisionId =>
        val spot = DecisionOption.RevealedVision(
          DecisionOptionRef.RevealedVision(player.player))
        Vector(player.revealedVision.fold[DecisionOption](spot)(held =>
          DecisionOption.Replacing(spot, held.id)) -> placement)
      case _ => adviserSlots(player, card, origin, Orientation.FaceUp, rules)

  private def facedown(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] = origin match
    case CardPlay.Origin.FacedownAdviser => Vector.empty
    case CardPlay.Origin.TemporaryHand =>
      adviserSlots(player, card, origin, Orientation.FaceDown, rules)

  private def adviserSlots(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, orientation: Orientation, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    val limit = rules.limitAfter(orientation == Orientation.FaceUp)
    val held = player.advisers
    def slot(index: Int): DecisionOption = DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(player.player, index,
        orientation == Orientation.FaceUp))
    origin match
      case CardPlay.Origin.FacedownAdviser =>
        // The card turns faceup where it lies, so the count stays.
        val own = held.indexWhere(_.id == card)
        if own < 0 || held.size > limit then Vector.empty
        else Vector(slot(own) -> SearchPlacement.Adviser(orientation, None))
      case CardPlay.Origin.TemporaryHand =>
        if held.size < limit then
          Vector(slot(held.size) -> SearchPlacement.Adviser(orientation, None))
        else if held.size == limit then held.zipWithIndex.map(
          (adviser, index) => DecisionOption.Replacing(slot(index), adviser.id) ->
            SearchPlacement.Adviser(orientation, Some(adviser.id)))
        else Vector.empty

  private def siteCards(ready: ReadyGame, actor: PlayerId)
      : Vector[SiteDenizenState] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(ready.game.current.map.sites.get)
      .fold(Vector.empty[SiteDenizenState])(_.denizens)

  private def optionOf(card: SiteDenizenState): DecisionOption = card match
    case denizen: DenizenState =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(denizen.id))
    case edifice: EdificeState =>
      DecisionOption.Edifice(DecisionOptionRef.Edifice(edifice.id))
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite"`
Expected: PASS. If the Hall of Ministers test's denizen reason differs, print the `OperationReason` code the restriction returned before changing `reasons`: the test pins the code the Hall's restriction gives today, "discard-immune".

Then run the gates. Expected: PASS: card play still runs through the legacy section, whose choices match today's.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/CardPlay.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala
git commit -m "feat(cardplay): build card play options as card slots over a rule-free planner"
```

---

### Task 5: Minor actions list facedown advisers only

Spec, Protocol, "Projection". The minor actions stop pre-computing each facedown adviser's placements: the placement decision lists them once the play starts, and discarding is always legal. This removes the last reader of `CardPlay.legalChoices` outside card play.

**Files:**
- Modify: `src/main/scala/oathdigital/application/LegalActionProjector.scala:191-229`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:31-36,328-329`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala:43-75`
- Modify: `src/main/scala/oathdigital/server/GameRoutes.scala:106-111`
- Modify: `frontend/src/main/scala/oathdigital/frontend/frontend.scala:38-45`
- Test: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala:53-55`; `frontend/src/test/scala/oathdigital/frontend/WalkerDecisionDraftSuite.scala:70-95`, `SessionDraftsSuite.scala:15`, `CardChoicePanelSuite.scala:15-16`, `ModifierFlowSuite.scala:21`

**Interfaces:**
- Consumes: nothing.
- Produces: `MinorAdviserProjection(card: CardDetailsProjection)`; no `CardResolutionProjection`, `CardResolution` or `MinorAdviserPlacement`.

- [ ] **Step 1: Write the failing codec test**

In `ProjectionProtocolSuite`, the fixture's `minorActions` becomes `Some(MinorActionsProjection(Vector(MinorAdviserProjection(known)), true, Vector(hidden), Some("site:a"), 1, 1))`. Append:

```scala
  test("a minor adviser written with its old placements still decodes"):
    val json = ujson.read(GameProjectionCodec.encode(projection))
    json("minorActions")("advisers")(0)("placements") = ujson.Arr()
    assertEquals(GameProjectionCodec.decode(ujson.write(json)),
      Right(projection))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: compilation fails: `MinorAdviserProjection` takes two arguments.

- [ ] **Step 3: Drop the placements**

In `ActionProjectionDtos.scala`, delete `CardResolutionProjection` (with its `replacement` accessor) and replace `MinorAdviserProjection` with:

```scala
/** A facedown adviser the player may play. Where it can go is the placement
  * decision's to say once the play starts; discarding it is always legal. */
final case class MinorAdviserProjection(card: CardDetailsProjection)
```

In `ActionProjectionCodec.scala`, delete `encodeResolution` and `decodeResolution`. `encodeMinor`'s adviser entry becomes `ujson.Obj("card" -> encodeCard(entry.card))`, and `decodeMinor`'s adviser decoder becomes:

```scala
    advisers <- traverse(adviserRaws, s"$path.advisers") { (raw, child) => for
      // A projection from before card slots also wrote each adviser's
      // placements; they are read past, so its payload still decodes.
      row <- obj(raw, child); _ <- exact(row, Set("card", "placements"), child)
      cardRaw <- field(row, "card", child); card <- decodeCard(cardRaw, s"$child.card")
    yield MinorAdviserProjection(card) }
```

In `LegalActionProjector.scala`, delete `adviserPlacements` and build each adviser as `MinorAdviserProjection(presentation.cardDetails(id, Some(Orientation.FaceDown), hidden = false))`. Remove the imports this leaves unused (`CardPlay`, `SearchPlacement`, `CardResolutionProjection`).

In `GameRoutes.scala`, the facedown-adviser preview check reads `projection.minorActions.exists(_.advisers.nonEmpty)`, and its message stays "no facedown adviser can legally be resolved".

In `frontend.scala`, delete the `CardResolution` and `MinorAdviserPlacement` aliases and the `MinorAdviserPlacement` object.

- [ ] **Step 4: Move the frontend suites**

- `WalkerDecisionDraftSuite.scala`: delete `placements`; `MinorAdviser(adviser, placements)` becomes `MinorAdviser(adviser)` (and likewise for `otherAdviser`); delete `oneAdviserWithNoPlacement` and the two assertions that read it. A facedown adviser is always playable, so that case no longer exists.
- `SessionDraftsSuite.scala`, `CardChoicePanelSuite.scala`, `ModifierFlowSuite.scala`: `MinorAdviser(card, ...)` becomes `MinorAdviser(card)`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite" "frontend/test"`
Expected: PASS. Then run the gates. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/LegalActionProjector.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala src/main/scala/oathdigital/server/GameRoutes.scala frontend/src/main/scala/oathdigital/frontend/frontend.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala frontend/src/test/scala/oathdigital/frontend/WalkerDecisionDraftSuite.scala frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala frontend/src/test/scala/oathdigital/frontend/CardChoicePanelSuite.scala frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala
git commit -m "refactor(minor-actions): list facedown advisers without precomputed placements"
```

---

### Task 6: Card play through card slots

Spec, Engine, "Placement procedure" and "Entry points". The placement decision offers `CardSlots`; a Replace site slot that leaves permissions asks a follow-up choose-many; each site discard writes the line of the permission it spent; the projector names the card in the heading. The legacy section of `CardPlay` goes, and the suites that walked the old replacement decision move to the slots.

**Files:**
- Rewrite: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala` (delete the legacy section and the `OperationRestrictions` import)
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala:116-125`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:160-172,425-440`
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala:180-184`
- Modify tests: `testkit/PlacementPicks.scala`, `gameplay/PlacementFixture.scala`, `gameplay/powers/SearchFixture.scala`
- Rewrite tests: `gameplay/CardPlayProcedureSuite.scala`, `gameplay/SiteDiscardFirstSuite.scala`, `gameplay/HomelandLineSuite.scala`, `gameplay/powers/banner/PeoplesFavorMobSuite.scala`, `gameplay/powers/search/CropRotationSuite.scala`
- Modify tests: `gameplay/CardPlayRulesSuite.scala`, `gameplay/powers/rest/SilverTongueSuite.scala`, `gameplay/powers/rest/InsomniaSuite.scala`, `gameplay/VisionPlaySuite.scala`, `gameplay/powers/cardplay/WildCrySuite.scala`, `gameplay/powers/whenplayed/WhenPlayedDecisionAfterPlaySuite.scala`, `application/WalkerDecisionProjectorSuite.scala`
- Delete tests: `gameplay/HomelandRuleSuite.scala`, `gameplay/CardPlayRestrictionsSuite.scala` (their cases are in `CardSlotsSuite`)

(Test paths are under `src/test/scala/oathdigital/`.)

**Interfaces:**
- Consumes: Task 4's `CardSlots.slots`, `moreDiscards`, `credits`, `CardSlots.play`, `CardSlot.replacedAtSite`, `CardPlay.operations`; Task 3's `PlayedAs`, `PlacementPicks`.
- Produces:
  - `CardPlayProcedure.discardDecisionId(card: WorldCardId): String`, `cardplay.discard.{kind}.{value}`.
  - `CardPlayProcedure.playedAs` over the slot refs: `DiscardPile` is `Discarded`; `SiteSlot` is `AtSite`; `AdviserPlay` faceup, `RevealedVision` and `CardSlots.play` are `Faceup`; `AdviserPlay` facedown is `Facedown`.
  - Engine headings "Play the card" and "Discard more cards from the site"; projected headings "Play {card}" and "Discard more cards from {site}".
  - `PlacementPicks.replaces(option: DecisionOption): Option[CardId]`, `PlacementPicks.findReplacing(options, way: PlayedAs, card: CardId): Option[DecisionOptionRef]`.
  - `PlacementFixture.placementOptions(ready, tree, pending, powers): Vector[DecisionOption]`.
  - `SearchFixture.placeReplacing(from, card, way: PlayedAs, replaced: CardId)`, `SearchFixture.discardMore(from, card, more: Vector[CardId])`; `SearchFixture.replace` is gone.

- [ ] **Step 1: Extend the test helpers**

Append to `PlacementPicks`:

```scala
  /** The card `option` discards to make room, read through any
    * availability, price or badge mark. */
  def replaces(option: DecisionOption): Option[CardId] = option match
    case DecisionOption.Replacing(_, card) => Some(card)
    case DecisionOption.Unavailable(inner, _) => replaces(inner)
    case DecisionOption.Priced(inner, _) => replaces(inner)
    case DecisionOption.Badged(inner, _) => replaces(inner)
    case _ => None

  /** The option of `options` that plays the card `way` in the place of
    * `card`, whether or not it may be chosen. */
  def findReplacing(options: Vector[DecisionOption], way: PlayedAs,
      card: CardId): Option[DecisionOptionRef] =
    options.find(option => CardPlayProcedure.playedAs(option.ref)
      .contains(way) && replaces(option).contains(card)).map(_.ref)
```

In `PlacementFixture`, replace `options` with:

```scala
  /** The options of the decision the walk is parked on. */
  def placementOptions(ready: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers)(using munit.Location): Vector[DecisionOption] =
    val decide = present(
      ProcedureWalker.parkedDecide(ready, tree, pending, powers),
      "the decision the walk is parked on")
    as[DecisionQuery.ChooseOne](decide.query).options

  /** The references of the options of the decision the walk is parked on. */
  def options(ready: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers)(using munit.Location): Vector[DecisionOptionRef] =
    placementOptions(ready, tree, pending, powers).map(_.ref)
```

and change `cardPlayDecisionId`'s doc to "The id of a card play's `kind` decision ("place" or "discard") for `card`."

In `SearchFixture`, delete `replace` and add:

```scala
  /** Places the card `way` in the place of `replaced`: the Replace option
    * that discards it, whether or not it may be chosen. */
  def placeReplacing(from: OathTransition, card: WorldCardId, way: PlayedAs,
      replaced: CardId)(using munit.Location)
      : Either[OathViolation, OathTransition] =
    PlacementPicks.findReplacing(PlacementPicks.parkedOptions(from.state,
        walkerPowers), way, replaced).toRight(
        OathViolation.InvalidSearchPlacement(
          s"no option plays the card $way in the place of $replaced"))
      .flatMap(ref => rules.resolveWalker(from.state, actor,
        PlacementFixture.cardPlayDecisionId(card, "place"),
        DecisionAnswer.ChooseOneAnswer(ref)))

  /** Answers the follow-up of a Replace site slot: discard `more` as well. */
  def discardMore(from: OathTransition, card: WorldCardId,
      more: Vector[CardId]): Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor,
      PlacementFixture.cardPlayDecisionId(card, "discard"),
      DecisionAnswer.ChooseManyAnswer(more.map {
        case id: EdificeId => DecisionOptionRef.Edifice(id)
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case other => DecisionOptionRef.Button(other.value)
      }))
```

- [ ] **Step 2: Rewrite `CardPlayProcedureSuite`**

Replace the whole file with:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, CardPlayRules, VisionRules}
import oathdigital.gameplay.actions.cardplay.{CardPlayProcedure, CardSlots}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerOutcome, WalkerPowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, as, present}

/** The card play walk (card play slots design, "Placement procedure"): the
  * slot choice, the follow-up discard and the play. p1 stands at Ancient City
  * (Cradle, room for three, no Homeland) on the quiet table; a discard there
  * goes to the Provinces.
  */
class CardPlayProcedureSuite extends munit.FunSuite:
  import PlacementFixture.{build, cardPlayDecisionId, park, rulePower,
    testPermission}

  private val actor = p1
  private val home = CatalogNames.site("Ancient City")
  // An unrestricted denizen whose power these tests do not use.
  private val card = CatalogNames.denizen("Errand Boy")
  // Three more unrestricted denizens, for full adviser areas and sites.
  private val others = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))
  // Each is printed Locked and adviser-only.
  private val locked = Vector("Faithful Friend", "A Small Favor",
    "Family Heirloom").map(CatalogNames.denizen(_))
  private val noPowers = WalkerPowers.empty
  private val placeId = cardPlayDecisionId(card, "place")
  private val discardId = cardPlayDecisionId(card, "discard")
  /** Two site discard permissions at once. */
  private val twoPermissions = rulePower("test.two-discards")(
    _.permitSiteDiscard(testPermission).permitSiteDiscard(testPermission))

  /** p1 holds Errand Boy in a temporary hand, on the quiet table. */
  private def handState: ReadyGame = Table.start.hand(actor, card).ready

  /** p1 holds Errand Boy in hand, and Ancient City is full of `others`. */
  private def fullSite: ReadyGame = others.foldLeft(Table.start
    .hand(actor, card))(_.denizen(_, at = home)).ready

  /** Errand Boy's card play, under `powers`, parked on its placement. */
  private final case class PlacementPark(ready: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers):
    def resolve(at: PendingTree, id: String, answer: DecisionAnswer)
        : Either[OathViolation, WalkerOutcome] =
      ProcedureWalker.resolve(ready, tree, at, Answered(id, answer, actor),
        powers)

    def place(ref: DecisionOptionRef): Either[OathViolation, WalkerOutcome] =
      resolve(pending, placeId, DecisionAnswer.ChooseOneAnswer(ref))

  private def parkOnPlacement(ready: ReadyGame,
      powers: WalkerPowers = noPowers)(using munit.Location): PlacementPark =
    val tree = build(ready, actor, card)
    PlacementPark(ready, tree, park(ready, tree, powers), powers)

  private def placementDecision(tree: Operation): Decide =
    as[Decide](present(tree.children.headOption, "the placement decision"))

  test("the placement decision offers the card's slots"):
    val ready = handState
    assertEquals(placementDecision(build(ready, actor, card)).query,
      DecisionQuery.ChooseOne(CardSlots.slots(catalog, ready, actor, card,
        CardPlay.Origin.TemporaryHand, CardPlayRules.default).map(_.option),
        heading = Some("Play the card")))

  test("card absent from temporary hand cannot build card play"):
    val absent = Table.start.ready
    assertEquals(CardPlayProcedure.build(catalog, absent, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).left.toOption,
      Some(OathViolation.InvalidSearchPlacement(
        "card is not held at the selected origin")))

  test("an empty site slot plays the card there and empties the hand"):
    val done = Outcomes.finished(parkOnPlacement(handState)
      .place(DecisionOptionRef.SiteSlot(home, 0)))
    assertEquals(Look(done.treeless).cardsAt(home), Vector[CardId](card))
    assertEquals(done.treeless.game.current.temporaryHands(actor),
      Vector.empty)

  test("the discard pile discards the card to the region after the pawn's"):
    val done = Outcomes.finished(parkOnPlacement(handState)
      .place(DecisionOptionRef.DiscardPile(Region.Provinces)))
    assertEquals(done.treeless.game.current.commonCards
      .discard(Region.Provinces).last, card)

  test("a Replace adviser slot discards that adviser and plays the card, " +
      "asking nothing more"):
    val full = others.foldLeft(Table.start.hand(actor, card))(
      _.adviser(actor, _, facedown = true)).ready
    val done = Outcomes.finished(parkOnPlacement(full)
      .place(DecisionOptionRef.AdviserPlay(actor, 0, faceup = true)))
    val advisers = Look(done.treeless).player(actor).advisers
    assertEquals(advisers.size, 3)
    assert(advisers.exists(_.id == card))
    assert(!advisers.exists(_.id == others.head))

  test("an unavailable Replace slot is refused"):
    val full = locked.foldLeft(Table.start.hand(actor, card))(
      _.adviser(actor, _)).ready
    assert(parkOnPlacement(full)
      .place(DecisionOptionRef.AdviserPlay(actor, 0, faceup = true)).isLeft)

  test("a Replace site slot with a permission left asks for more discards, " +
      "the replaced card locked"):
    val parked = parkOnPlacement(fullSite, WalkerPowers(Vector(twoPermissions)))
    val asked = Outcomes.parked(parked.place(DecisionOptionRef.SiteSlot(home, 0)))
    val more = present(ProcedureWalker.parkedDecide(parked.ready, parked.tree,
      asked.tree, parked.powers), "the follow-up")
    assertEquals(more.decisionId, discardId)
    def option(id: DenizenId) = DecisionOption.Denizen(DecisionOptionRef.Denizen(id))
    assertEquals(more.query, DecisionQuery.ChooseMany(0, 1,
      others.tail.map(option),
      heading = Some("Discard more cards from the site"),
      locked = Vector(option(others.head))))

  test("the follow-up's discards go before the play, and choosing none " +
      "discards only the replaced card"):
    val parked = parkOnPlacement(fullSite, WalkerPowers(Vector(twoPermissions)))
    val asked = Outcomes.parked(parked.place(DecisionOptionRef.SiteSlot(home, 0)))
    val more = Outcomes.finished(parked.resolve(asked.tree, discardId,
      DecisionAnswer.ChooseManyAnswer(Vector(
        DecisionOptionRef.Denizen(others(1))))))
    assertEquals(Look(more.treeless).cardsAt(home),
      Vector[CardId](others(2), card))
    val none = Outcomes.finished(parked.resolve(asked.tree, discardId,
      DecisionAnswer.ChooseManyAnswer(Vector.empty)))
    assertEquals(Look(none.treeless).cardsAt(home),
      Vector[CardId](others(1), others(2), card))

  test("a Replace site slot that spends the only permission asks nothing more"):
    val powers = WalkerPowers(Vector(PlacementFixture.discardFirst))
    val done = Outcomes.finished(parkOnPlacement(fullSite, powers)
      .place(DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals(Look(done.treeless).cardsAt(home),
      Vector[CardId](others(0), others(2), card))

  test("facedown adviser starts the shared walker placement tree"):
    // p1 holds a facedown Wrestlers to play.
    val adviser = CatalogNames.denizen("Wrestlers")
    val ready = Table.start.adviser(actor, adviser, facedown = true).ready
    val rules = new OathRules(catalog)
    val walkerParked = new ParkedDecisionAssertions(catalog)
    val started = rules.startWalker(OathState.Ready(ready),
      ActionRef.PlayFacedownAdviser, actor,
      startArgs = Vector(DecisionOptionRef.Denizen(adviser)))
    walkerParked.assertParked(accepted(started).state,
      ActionRef.PlayFacedownAdviser,
      PlacementFixture.cardPlayDecisionId(adviser, "place"), actor)

  test("playedAs names how each placement option plays the card"):
    val refs = Vector[DecisionOptionRef](
      DecisionOptionRef.DiscardPile(Region.Cradle),
      DecisionOptionRef.SiteSlot(home, 0),
      DecisionOptionRef.AdviserPlay(actor, 0, faceup = true),
      DecisionOptionRef.RevealedVision(actor), CardSlots.play,
      DecisionOptionRef.AdviserPlay(actor, 0, faceup = false))
    assertEquals(refs.map(CardPlayProcedure.playedAs), Vector(
      Some(PlayedAs.Discarded), Some(PlayedAs.AtSite), Some(PlayedAs.Faceup),
      Some(PlayedAs.Faceup), Some(PlayedAs.Faceup), Some(PlayedAs.Facedown)))
    // The follow-up answers with site cards, which play nothing.
    assertEquals(CardPlayProcedure.playedAs(
      DecisionOptionRef.Denizen(others.head)), None)

  /** A pending walk that answered decision `id` with the discard pile. */
  private def pendingFor(id: String): PendingTree =
    PendingTree(at = Vector.empty, answered = Vector(Answered(id,
      DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.DiscardPile(Region.Provinces)), actor)))

  test("placementDecisionId and placedCard round-trip a denizen, and the " +
      "follow-up's id is spelled the same way"):
    val denizen = CatalogNames.denizen("Book Binders")
    val denizenId = CardPlayProcedure.placementDecisionId(denizen)
    assertEquals(denizenId, "cardplay.place.denizen.140")
    assertEquals(CardPlayProcedure.placedCard(pendingFor(denizenId)),
      Some(denizen))
    assertEquals(CardPlayProcedure.discardDecisionId(denizen),
      "cardplay.discard.denizen.140")

  test("placementDecisionId and placedCard round-trip a Vision whose value " +
      "contains ':'"):
    val vision = VisionRules.Faith
    val visionId = CardPlayProcedure.placementDecisionId(vision)
    assertEquals(visionId, s"cardplay.place.vision.${vision.value}")
    assertEquals(CardPlayProcedure.placedCard(pendingFor(visionId)),
      Some(vision))

  test("placementDecisionId and placedCard round-trip a value that " +
      "contains '.'"):
    // Only the FIRST '.' after the prefix is the kind/value split, since no
    // kind contains one.
    val dotted = VisionId("vision:has.a.dot")
    val dottedId = CardPlayProcedure.placementDecisionId(dotted)
    assertEquals(dottedId, s"cardplay.place.vision.${dotted.value}")
    assertEquals(CardPlayProcedure.placedCard(pendingFor(dottedId)),
      Some(dotted))
```

`WalkerOutcome` is used only through `Outcomes`; drop it from the import if `-Werror` reports it unused.

- [ ] **Step 3: Rewrite the walker-level discard suites**

Replace `SiteDiscardFirstSuite.scala` with:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.as

/** A site discard permission through the walker: a Replace site slot
  * discards its card with the standard returns, at any capacity.
  * `discardFirst` is PlacementFixture's stub power that adds
  * `testPermission`, and `limitTwo` its stub that lowers the adviser limit to
  * two. The slot builder's own cases are `CardSlotsSuite`'s.
  */
class SiteDiscardFirstSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, cardPlayDecisionId, discardFirst,
    limitTwo, options, park}

  private val powers = WalkerPowers(Vector(discardFirst))
  /** No Homeland, room for three, in the Cradle. */
  private val home = CatalogNames.site("Ancient City")
  /** The Beast card p1 plays. The other cards are plain and of other suits. */
  private val card = CatalogNames.denizen("Errand Boy")
  private val kept = CatalogNames.denizen("Rain Boots")
  private val other = CatalogNames.denizen("Ancient Binding")
  private val hall = CatalogNames.edifice("Hall of Ministers")

  /** p1 stands at Ancient City, which holds `cards`, with Errand Boy in hand. */
  private def playing(cards: DenizenId*): Table =
    cards.foldLeft(Table.start.hand(p1, card))((table, held) =>
      table.denizen(held, at = home))

  private def slot(index: Int): DecisionOptionRef =
    DecisionOptionRef.SiteSlot(home, index)

  /** Plays Errand Boy to `ref` under `under`, returning the finished game. */
  private def playedTo(ready: ReadyGame, ref: DecisionOptionRef,
      under: WalkerPowers = powers): ReadyGame =
    val tree = build(ready, p1, card)
    as[WalkerOutcome.Finished](answer(ready, tree, park(ready, tree, under),
      under, cardPlayDecisionId(card, "place"), ref, p1)).treeless

  test("the empty slot plays the card and discards nothing"):
    val done = playedTo(playing(kept).ready, slot(1))
    assertEquals(Look(done).cardsAt(home), Vector[CardId](kept, card))
    assertEquals(done.game.current.temporaryHands(p1), Vector.empty)

  test("a Replace slot discards its card with the standard returns"):
    val ready = playing(kept).tokens(kept, favor = 1, secrets = 1).ready
    val done = playedTo(ready, slot(0))
    assertEquals(Look(done).cardsAt(home), Vector[CardId](card))
    // Ancient City is in the Cradle, so the discard goes to the Provinces.
    assertEquals(done.game.current.commonCards.discard(Region.Provinces).last,
      kept)
    // The favor on the discarded card returns to its suit bank (Rain Boots is
    // Nomad), its secret to the player facedown.
    assertEquals(done.banks.favor(Suit.Nomad), ready.banks.favor(Suit.Nomad) + 1)
    assertEquals(Look(done).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)

  test("a full site takes the play only through a Replace slot, and only " +
      "under the permission"):
    val fillers = Vector(kept, other, CatalogNames.denizen("Wrestlers"))
    val ready = playing(fillers*).ready
    val tree = build(ready, p1, card)
    def siteSlots(under: WalkerPowers) = options(ready, tree,
      park(ready, tree, under), under)
      .filter(_.isInstanceOf[DecisionOptionRef.SiteSlot])
    assertEquals(siteSlots(WalkerPowers.empty), Vector.empty)
    assertEquals(siteSlots(powers), Vector(slot(0), slot(1), slot(2)))
    val done = playedTo(ready, slot(0))
    assertEquals(Look(done).cardsAt(home).size, 3)
    assert(Look(done).cardsAt(home).contains(card))

  test("a ruined edifice may be replaced, and it goes back to the edifice deck"):
    val ready = playing(kept).edifice(hall, EdificeSide.Ruined, at = home)
      .warbandsAt(home, p1, 1).ready
    val done = playedTo(ready, slot(1))
    assertEquals(Look(done).cardsAt(home), Vector[CardId](kept, card))
    assertEquals(done.game.current.commonCards.edificeDeck.last, hall)

  test("the permission composes with an adviser limit through the walker"):
    val ready = playing(kept).adviser(p1, "Wrestlers", facedown = true)
      .adviser(p1, "Battle Honors", facedown = true).ready
    val both = WalkerPowers(Vector(discardFirst, limitTwo))
    val tree = build(ready, p1, card)
    val offered = options(ready, tree, park(ready, tree, both), both)
    // A limit of two with two advisers held: a faceup play replaces one.
    assertEquals(offered.filter(ref => CardPlayProcedure.playedAs(ref)
      .contains(PlayedAs.Faceup)), Vector[DecisionOptionRef](
        DecisionOptionRef.AdviserPlay(p1, 0, faceup = true),
        DecisionOptionRef.AdviserPlay(p1, 1, faceup = true)))
    // And the site still offers its empty slot and the Replace slot.
    assertEquals(offered.filter(_.isInstanceOf[DecisionOptionRef.SiteSlot]),
      Vector(slot(1), slot(0)))
```

Replace `HomelandLineSuite.scala` with:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlayRules, RuleNotes}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.as

/** The Homeland rule's line (power log lines design, "Game rules"): a play to
  * the Homeland of the card's suit may replace a card there, and the Homeland
  * says why when it does. A play that replaces nothing writes nothing.
  */
class HomelandLineSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, cardPlayDecisionId, options, park}

  private val noPowers = WalkerPowers.empty
  private val actor = p1
  /** The Beast Homeland, with room for three cards. */
  private val homeSite = CatalogNames.site("Deep Woods")
  // A Beast card, matching the Homeland, and a Nomad card, which does not.
  private val matching = CatalogNames.denizen("Errand Boy")
  private val otherSuit = CatalogNames.denizen("Rain Boots")

  /** p1 stands at Deep Woods, which p1 rules, with `card` in hand. Deep Woods
    * holds three other-suit cards if `full`, else one. */
  private def atHomeland(card: DenizenId, full: Boolean = true): ReadyGame =
    val fillers = Vector("Ancient Binding", "Wrestlers", "Battle Honors")
      .take(if full then 3 else 1)
    fillers.foldLeft(Table.start
      .pawn(actor, at = homeSite).warbandsAt(homeSite, actor, 1)
      .hand(actor, card))((table, held) => table.denizen(held, at = homeSite))
      .ready

  /** Plays `card` to Deep Woods' slot `index`. */
  private def playedTo(ready: ReadyGame, card: DenizenId, index: Int)
      : WalkerOutcome.Finished =
    val tree = build(ready, actor, card)
    as[WalkerOutcome.Finished](answer(ready, tree, park(ready, tree, noPowers),
      noPowers, cardPlayDecisionId(card, "place"),
      DecisionOptionRef.SiteSlot(homeSite, index), actor))

  /** The Homeland's line: the actor may discard a site card first. */
  private val discardFirstLine = Vector(NoteText.Said("discard-first",
    s"${actor.value} may discard a card at their site first.", covers = false))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(RuleNotes.homelandDiscard, Vector(CardPlayRules.discardFirst),
      events)

  test("a play replacing a card at a full matching Homeland writes the " +
      "Homeland's line"):
    assertEquals(said(playedTo(atHomeland(matching), matching, 0).events),
      discardFirstLine)

  test("a full Homeland of another suit offers no site slot, and writes " +
      "nothing"):
    val ready = atHomeland(otherSuit)
    val tree = build(ready, actor, otherSuit)
    val parked = Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, noPowers))
    assert(!options(ready, tree, parked.tree, noPowers)
      .exists(_.isInstanceOf[DecisionOptionRef.SiteSlot]))
    assertEquals(said(parked.events), Vector.empty)

  test("a play replacing a card at a matching Homeland with room writes " +
      "the line"):
    assertEquals(said(playedTo(atHomeland(matching, full = false), matching, 0)
      .events), discardFirstLine)

  test("a play to the empty slot of a matching Homeland discards nothing and " +
      "writes nothing"):
    val done = playedTo(atHomeland(matching, full = false), matching, 1)
    assert(Look(done.treeless).cardsAt(homeSite).contains(matching))
    assertEquals(said(done.events), Vector.empty)
```

Delete `HomelandRuleSuite.scala` and `CardPlayRestrictionsSuite.scala` with `git rm`: `CardSlotsSuite` covers the Homeland suit, the Homeland permission, a full Homeland, the intact and ruined edifice, and the faceup and facedown locked adviser, and `CardPlayProcedureSuite` covers an unavailable Replace slot being refused.

In `CardPlayRulesSuite`, replace the test "a contributed limit reaches the placement: two advisers fill an area limited to two" with:

```scala
  test("a contributed limit reaches the placement: two advisers fill an " +
      "area limited to two"):
    val first = CatalogNames.denizen("Wrestlers")
    val second = CatalogNames.denizen("Battle Honors")
    val ready = Table.start.hand(p1, card)
      .adviser(p1, first, facedown = true).adviser(p1, second, facedown = true)
      .ready
    val tree = build(ready, p1, card)
    def faceup(powers: WalkerPowers): Vector[DecisionOptionRef] =
      options(ready, tree, park(ready, tree, powers), powers).filter(ref =>
        CardPlayProcedure.playedAs(ref).contains(PlayedAs.Faceup))
    // Under the default limit of three there is room.
    assertEquals(faceup(WalkerPowers.empty), Vector[DecisionOptionRef](
      DecisionOptionRef.AdviserPlay(p1, 2, faceup = true)))
    // Under a limit of two the area is full: each adviser is a Replace slot.
    assertEquals(faceup(WalkerPowers(Vector(limitTwo))),
      Vector[DecisionOptionRef](
        DecisionOptionRef.AdviserPlay(p1, 0, faceup = true),
        DecisionOptionRef.AdviserPlay(p1, 1, faceup = true)))
```

and adjust its imports (`CardPlayProcedure.PlayedAs` in; `answer`, `cardPlayDecisionId` and `WalkerOutcome` out if unused).

- [ ] **Step 4: Rewrite the Mob and Crop Rotation suites**

Replace `PeoplesFavorMobSuite.scala` with:

```scala
package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Table.{p1, p3}
import oathdigital.testkit.Unwrap.accepted

class PeoplesFavorMobSuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture.{keep, place, placeReplacing, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val ancientCity = CatalogNames.site("Ancient City")
  /** The Beast card each Search keeps and plays, above two Arcane cards. */
  private val played = CatalogNames.denizen("Errand Boy")
  private val drawn = Vector[WorldCardId](played,
    CatalogNames.denizen("Ward of Silence"),
    CatalogNames.denizen("Arcane Brokers"))
  // Hearth denizens whose powers these tests do not use.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val newsFromAfar = CatalogNames.denizen("News from Afar")
  private val capacity = 3 // Ancient City's printed capacity

  /** p1's Act at Ancient City, which holds `siteCards`, searching a world
    * deck topped by Errand Boy, Ward of Silence and Arcane Brokers with 5
    * Supply. The Banner of the People's Favor is as `favor` arranges it: held
    * by p1, on the Mob face, unless a test says otherwise.
    */
  private def searchAtAncientCityHolding(siteCards: Vector[DenizenId],
    favor: ReadyGame => ReadyGame = (r => Table.from(r)
        .peoplesFavor(Some(actor), 1).unchecked)): ReadyGame =
    favor(
      siteCards.foldLeft(SearchFixture.staged(drawn, supply = 5))(atActorsSite))

  /** Searches with `selected` and keeps the played card, parked on its
    * placement. The events are the whole Search's so far. */
  private def toPlacement(ready: ReadyGame,
      selected: Vector[PowerId] = Vector.empty): OathTransition = (for
    started <- start(ready, selected)
    kept <- keep(started, played)
  yield kept.copy(events = started.events ++ kept.events))
    .fold(error => throw new AssertionError(error.toString), identity)

  /** The parked placement's site slots, each as the card it replaces. */
  private def siteSlots(at: OathTransition): Vector[Option[CardId]] =
    PlacementPicks.parkedOptions(at.state, walkerPowers)
      .filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])
      .map(PlacementPicks.replaces)

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    Look(ready).cardsAt(ancientCity)

  test("the source index lists Mob on the Mob face and the Grand Council's " +
      "own power on the other"):
    def favor(ready: ReadyGame) = oathdigital.gameplay.RuleSourceIndex
      .enumerate(catalog, ready).find(_.source ==
        RuleSourceRef.Banner(Banner.PeoplesFavor.key))
          .getOrElse(fail("no People's Favor source"))
    assertEquals(favor(quietTable).powerIds, Vector(PeoplesFavorMob.id))
    assertEquals(favor(Table.from(quietTable).peoplesFavor(Some(actor), 1,
      PeoplesFavorFace.GrandCouncil).unchecked)
      .handlerIds, Vector("banner.peoples-favor.grand-council"))

  test("its holder is offered each site card as Replace beside the empty " +
      "slot, and the empty slot discards nothing"):
    val (kept, other) = (homesteaders, landWarden)
    val ready = searchAtAncientCityHolding(Vector(kept, other))
    assert(capacity > 2, s"the pawn site must have room, capacity $capacity")
    val asked = toPlacement(ready)
    assertEquals(siteSlots(asked), Vector(None, Some(kept), Some(other)))
    val done = accepted(place(asked, played, PlayedAs.AtSite))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](kept, other, played))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)

  test("an empty site has nothing to replace"):
    val asked = toPlacement(searchAtAncientCityHolding(Vector.empty))
    assertEquals(siteSlots(asked), Vector(None))
    assertEquals(siteCards(SearchFixture.after(
      accepted(place(asked, played, PlayedAs.AtSite)))), Vector[CardId](played))

  test("a Replace slot discards by the standard discard: facedown to the " +
      "next region's pile, its favor to the bank, its secret to the holder"):
    val kept = homesteaders
    val ready = Table.from(searchAtAncientCityHolding(Vector(kept)))
      .tokens(kept, favor = 1, secrets = 1).unchecked
    val asked = toPlacement(ready)
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite, kept))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](played))
    assertEquals(Look(end).discardAfter(ancientCity).last, kept)
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0),
      ready.banks.favor.getOrElse(Suit.Hearth, 0) + 1)
    assertEquals(Look(end).player(actor).board.faceDownSecrets,
      Look(ready).player(actor).board.faceDownSecrets + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)

  test("a full site with no matching homeland takes the play only through a " +
      "Replace slot, and only for the holder"):
    // Ancient City is no suit's homeland, so the Beast card matches none.
    assertEquals(catalog.site(ancientCity).flatMap(_.homeland), None)
    val fillers = Vector(homesteaders, landWarden, newsFromAfar)
    assertEquals(fillers.size, capacity)
    val asked = toPlacement(searchAtAncientCityHolding(fillers))
    assertEquals(siteSlots(asked), fillers.map(Some(_)))
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      fillers.head))
    assertEquals(siteCards(SearchFixture.after(done)).size, capacity)
    assert(siteCards(SearchFixture.after(done)).contains(played))
    val without = toPlacement(searchAtAncientCityHolding(fillers, identity))
    assert(place(without, played, PlayedAs.AtSite).isLeft,
      "a full site accepts no play without Mob")

  test("nobody else may replace: not another holder, not the Grand Council " +
      "face, not the other banner, not an unheld banner"):
    val kept = homesteaders
    val unheld: Vector[(String, ReadyGame => ReadyGame)] = Vector(
      "held by p3" -> (r => Table.from(r).peoplesFavor(Some(p3), 1).unchecked),
      "Grand Council face" -> (r => Table.from(r)
      .peoplesFavor(Some(actor), 1, PeoplesFavorFace.GrandCouncil).unchecked),
      "unheld" -> (r => Table.from(r).peoplesFavor(None, 1).unchecked),
      "Wandering Flame" -> (r => Table.from(r).darkestSecret(Some(actor), 1)
      .unchecked))
    unheld.foreach { case (label, favor) =>
      val asked = toPlacement(searchAtAncientCityHolding(Vector(kept), favor))
      assertEquals(siteSlots(asked), Vector(None), label)
      assertEquals(siteCards(SearchFixture.after(
        accepted(place(asked, played, PlayedAs.AtSite)))),
        Vector[CardId](kept, played), label)
    }

  test("it applies to a facedown adviser played to a site as well"):
    val kept = homesteaders
    val ready = asAdviser(searchAtAncientCityHolding(Vector(kept)), played,
      Orientation.FaceDown)
    val started = accepted(
      rules.startWalker(OathState.Ready(ready), ActionRef.PlayFacedownAdviser,
        actor, Vector.empty, Vector(DecisionOptionRef.Denizen(played))))
    assert(siteSlots(started).contains(Some(kept)))

  test("it composes with Silver Tongue's limit of two advisers, both from " +
      "the production walker powers"):
    val tongue = CatalogNames.denizen("Silver Tongue")
    val (kept, adviser) = (homesteaders, landWarden)
    // p1 plays `played` from hand at Ancient City, which holds `kept`, while
    // holding the People's Favor on Mob, a faceup Silver Tongue and a
    // facedown `adviser`.
    val who = actor
    val ready = Table.start.hand(who, played).pawn(who, ancientCity)
      .denizen(kept, at = ancientCity).adviser(who, tongue)
      .adviser(who, adviser, facedown = true).peoplesFavor(Some(who), 1).ready
    val tree = PlacementFixture.build(ready, who, played)
    val shown = PlacementFixture.placementOptions(ready, tree,
      PlacementFixture.park(ready, tree, walkerPowers), walkerPowers)
    // Two advisers held under Silver Tongue: a faceup play replaces one, and
    // the faceup Silver Tongue is a locked card, so only the other may go.
    assertEquals(shown.filter(option => option.available &&
      CardPlayProcedure.playedAs(option.ref).contains(PlayedAs.Faceup))
      .map(PlacementPicks.replaces), Vector(Some(adviser)))
    // The site still offers the Mob's Replace slot beside the empty one.
    assertEquals(shown.filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])
      .map(PlacementPicks.replaces), Vector(None, Some(kept)))
    assert(walkerPowers.powers.exists(_.id == SilverTongue.id))

  test("it leaves a selected card's power alone: Welcoming Party still pays " +
      "once, and the selected card itself cannot be replaced"):
    val party = CatalogNames.denizen("Welcoming Party")
    val kept = homesteaders
    val ready = atActorsSite(searchAtAncientCityHolding(Vector(kept)), party)
    val selected = Vector(oathdigital.gameplay.powers.cardplay.WelcomingParty.id)
    val asked = toPlacement(ready, selected)
    assert(placeReplacing(asked, played, PlayedAs.AtSite, party).isLeft,
      "a card that prints a selected power cannot be discarded")
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite, kept))
    val end = SearchFixture.after(done)
    val hearth = (s: ReadyGame) => s.banks.favor.getOrElse(Suit.Hearth, 0)
    // The play gains 1 favor from the played card's own bank, Welcoming Party
    // gains 1 from the Hearth bank.
    assertEquals(Look(end).player(actor).board.favor, Look(ready).player(actor)
      .board.favor + 2)
    assertEquals(hearth(end), hearth(ready) - 1)
    assertEquals(siteCards(end), Vector[CardId](party, played))

  // ---- Lines ----

  private def mobSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(PeoplesFavorMob.id, PeoplesFavorMob.noteKeys, events)

  test("the discard the Mob permits writes its line when it is made"):
    val asked = toPlacement(
      searchAtAncientCityHolding(Vector(homesteaders, landWarden)))
    assertEquals(mobSaid(asked.events), Vector.empty)
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      homesteaders))
    assertEquals(mobSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))

  test("a play to the empty slot writes no Mob line"):
    val asked = toPlacement(searchAtAncientCityHolding(Vector(homesteaders)))
    assertEquals(mobSaid(accepted(place(asked, played, PlayedAs.AtSite))
      .events), Vector.empty)
```

The two edifice tests this suite had ("an intact edifice is never offered", "a ruined edifice is offered") are `CardSlotsSuite`'s cases now ("an intact edifice and a faceup locked adviser are shown Locked", "after a Replace site slot..."), with a plain permission in place of the Mob's.

Replace `CropRotationSuite.scala` with:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.banner.PeoplesFavorMob
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present}

class CropRotationSuite extends munit.FunSuite:
  import SearchFixture.{discardMore, keep, place, placeReplacing, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val crop = CatalogNames.denizen("Crop Rotation")
  private val modifiers = Vector(CropRotation.id)
  /** The Beast card each Search keeps and plays. */
  private val played = CatalogNames.denizen("Errand Boy")
  // Order denizens whose powers these tests do not use, to fill the site.
  private val fillers = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))
  private val home = CatalogNames.site("Ancient City")
  private val capacity = 3 // Ancient City's printed capacity

  /** p1 in Act at Ancient City with 5 Supply, the world deck topped by
    * `played`, and Ancient City holding `site`. p1 holds Crop Rotation as a
    * faceup adviser, or it stands at the site when `atSite`. */
  private def staged(site: Vector[DenizenId], atSite: Boolean = false)
      : ReadyGame =
    val table = Table.start.pawn(p1, home).worldDeckTop(played).supply(p1, 5)
    val held = if atSite then table.denizen(crop, at = home)
      else table.adviser(p1, crop)
    site.foldLeft(held)((t, card) => t.denizen(card, at = home)).ready

  /** Searches with `selected` and keeps `played`, parked on its placement. */
  private def toPlacement(ready: ReadyGame,
      selected: Vector[PowerId] = modifiers): OathTransition = accepted(for
    started <- start(ready, selected)
    kept <- keep(started, played)
  yield kept.copy(events = started.events ++ kept.events))

  /** The parked placement's site slots, each as the card it replaces. */
  private def siteSlots(at: OathTransition): Vector[Option[CardId]] =
    PlacementPicks.parkedOptions(at.state, walkerPowers)
      .filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])
      .map(PlacementPicks.replaces)

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    Look(ready).cardsAt(home)

  private def cropSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(CropRotation.id, Vector(CardPlayRules.discardFirst), events)

  test("selected, it offers each site denizen as Replace beside the empty " +
      "slot, and the empty slot discards nothing"):
    val ready = staged(Vector(fillers(0)))
    val asked = toPlacement(ready)
    assertEquals(siteSlots(asked), Vector(None, Some(fillers(0))))
    val done = accepted(place(asked, played, PlayedAs.AtSite))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](fillers(0), played))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("a Replace slot discards the card before the play"):
    val done = accepted(placeReplacing(toPlacement(staged(Vector(fillers(0)))),
      played, PlayedAs.AtSite, fillers(0)))
    assertEquals(siteCards(SearchFixture.after(done)), Vector[CardId](played))

  test("without the selection nothing may be replaced"):
    assertEquals(siteSlots(toPlacement(staged(Vector(fillers(0))),
      Vector.empty)), Vector(None))

  test("a full site takes the play only through a Replace slot"):
    val full = staged(fillers.take(capacity))
    assert(place(toPlacement(full, Vector.empty), played, PlayedAs.AtSite)
      .isLeft, "a full site accepts no play without a permission")
    val asked = toPlacement(full)
    assertEquals(siteSlots(asked), fillers.take(capacity).map(Some(_)))
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      fillers(0)))
    assert(siteCards(SearchFixture.after(done)).contains(played))

  test("Crop Rotation itself, selected at the site, is shown in use and " +
      "cannot be replaced"):
    val asked = toPlacement(staged(Vector(fillers(0)), atSite = true))
    val cropSlot = present(PlacementPicks.parkedOptions(asked.state,
      walkerPowers).find(option =>
        PlacementPicks.replaces(option).contains(crop)), "Crop Rotation's slot")
    assert(!cropSlot.available)
    assert(placeReplacing(asked, played, PlayedAs.AtSite, crop).isLeft,
      "a card that prints a selected power cannot be discarded")
    assert(placeReplacing(asked, played, PlayedAs.AtSite, fillers(0)).isRight)

  test("it covers a ruined edifice too: an edifice is a denizen for placement"):
    val hall = CatalogNames.edifice("Hall of Ministers")
    val ready = Table.from(staged(Vector(fillers(0))))
      .edifice(hall, EdificeSide.Ruined, at = home).unchecked
    assertEquals(siteSlots(toPlacement(ready)),
      Vector(None, Some(fillers(0)), Some(hall)))

  test("it applies to a facedown adviser played to a site"):
    val ready = Table.start.adviser(p1, crop)
      .adviser(p1, played, facedown = true)
      .denizen(fillers(0), at = home).ready
    val started = accepted(rules.startWalker(Ready(ready),
      ActionRef.PlayFacedownAdviser, p1, modifiers,
      Vector(DecisionOptionRef.Denizen(played))))
    assert(siteSlots(started).contains(Some(fillers(0))))

  test("its line is written for the discard it allowed"):
    val asked = toPlacement(staged(Vector(fillers(0))))
    assertEquals(cropSaid(asked.events), Vector.empty)
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      fillers(0)))
    assertEquals(cropSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${p1.value} may discard a card at their site first.", covers = false)))

  test("with the Mob as well, two cards may go, each credited to the " +
      "permission it spent"):
    val ready = Table.from(staged(Vector(fillers(0), fillers(1))))
      .peoplesFavor(Some(p1), 1).unchecked
    val replaced = accepted(placeReplacing(toPlacement(ready), played,
      PlayedAs.AtSite, fillers(0)))
    // The slot spent one permission; the other is offered as a follow-up.
    val done = accepted(discardMore(replaced, played, Vector(fillers(1))))
    assertEquals(siteCards(SearchFixture.after(done)), Vector[CardId](played))
    val events = replaced.events ++ done.events
    assertEquals(cropSaid(events).size, 1)
    assertEquals(NoteText.said(PeoplesFavorMob.id, PeoplesFavorMob.noteKeys,
      events).size, 1)
```

- [ ] **Step 5: Move the remaining suites to the slots**

In `SilverTongueSuite`, replace `FaceupPlacement`, `playAsFaceupAdviser`, `optionRefs` and the two tests that use them with (import `CardPlayProcedure.PlayedAs` and `oathdigital.testkit.PlacementPicks`):

```scala
  /** The placement options of `played` from hand, with Silver Tongue's power
    * in force. */
  private def placementOptions(ready: ReadyGame, played: DenizenId)
      : Vector[DecisionOption] =
    val tree = accepted(CardPlayProcedure.build(catalog, ready, actor, played,
      CardPlayProcedure.Origin.TemporaryHand))
    val powers = WalkerPowers(Vector(SilverTongue.forCatalog(catalog)))
    val parked = as[WalkerOutcome.Parked](accepted(
      ProcedureWalker.advance(ready, tree, None, powers))).tree
    as[DecisionQuery.ChooseOne](present(
      ProcedureWalker.parkedDecide(ready, tree, parked, powers),
      "the placement decision").query).options

  /** The faceup plays among `options`, each as the card it replaces. */
  private def faceupReplacing(options: Vector[DecisionOption])
      : Vector[Option[CardId]] =
    options.filter(option => CardPlayProcedure.playedAs(option.ref)
      .contains(PlayedAs.Faceup)).map(PlacementPicks.replaces)

  test("Silver Tongue requires replacement when a third adviser is played"):
    // Two unrestricted denizens: the facedown second adviser and the card
    // played from hand.
    val second = CatalogNames.denizen("Threatening Roar")
    val played = CatalogNames.denizen("Fae Merchant")
    val ready = Table.from(silverTongueRest(Vector.empty, Set.empty))
      .turn(actor, Phase.Act).adviser(actor, second, facedown = true)
      .hand(actor, played).unchecked
    val faceup = faceupReplacing(placementOptions(ready, played))
    assert(faceup.nonEmpty && faceup.forall(_.nonEmpty), faceup.toString)
    assert(faceup.contains(Some(second)))

  test("playing Silver Tongue itself as a third adviser requires replacement"):
    // Two facedown advisers, and Silver Tongue in hand.
    val others = Vector("Threatening Roar", "Fae Merchant")
      .map(CatalogNames.denizen(_))
    val resting = Table.from(silverTongueRest(Vector.empty, Set.empty))
      .turn(actor, Phase.Act).hand(actor, tongue)
    val ready = others.foldLeft(resting)(_.adviser(actor, _, facedown = true))
      .unchecked
    val options = placementOptions(ready, tongue)
    assert(faceupReplacing(options).forall(_.nonEmpty))
    assert(faceupReplacing(options).contains(Some(others.head)))
    // Facedown it needs no room under its own limit.
    assert(options.exists(_.ref ==
      DecisionOptionRef.AdviserPlay(actor, 2, faceup = false)))
```

In `InsomniaSuite`, replace `Placement`, `placed` and the three tests that use them with the following (import `CardPlayProcedure.PlayedAs` and `oathdigital.testkit.PlacementPicks`; drop the imports this leaves unused, such as `WalkerOutcome` and `as`):

```scala
  /** The placement options of `card` from p1's hand, under the production
    * powers. */
  private def placementOptions(ready: ReadyGame, card: DenizenId)
      : Vector[DecisionOption] =
    val tree = PlacementFixture.build(ready, p1, card)
    PlacementFixture.placementOptions(ready, tree,
      PlacementFixture.park(ready, tree, powers), powers)

  private def playing(options: Vector[DecisionOption], way: PlayedAs)
      : Vector[DecisionOption] =
    options.filter(option => CardPlayProcedure.playedAs(option.ref)
      .contains(way))

  test("its holder may have only two advisers: a third faceup adviser " +
      "replaces one, and the locked Insomnia cannot go"):
    val ready = Table.start.hand(p1, homesteaders).adviser(p1, insomnia)
      .adviser(p1, landWarden, facedown = true).ready
    val faceup = playing(placementOptions(ready, homesteaders), PlayedAs.Faceup)
    assertEquals(faceup.filter(_.available).map(PlacementPicks.replaces),
      Vector(Some(landWarden)))
    assertEquals(faceup.filterNot(_.available).map(PlacementPicks.replaces),
      Vector(Some(insomnia)))
    assertEquals(AdviserLimit.of(catalog, ready, p1), Insomnia.HolderLimit)

  test("playing Insomnia faceup needs room under its own limit"):
    // Two advisers and a third would fit the default limit of three.
    val ready = Table.start.hand(p1, insomnia)
      .adviser(p1, homesteaders, facedown = true)
        .adviser(p1, landWarden, facedown = true).ready
    val options = placementOptions(ready, insomnia)
    assertEquals(playing(options, PlayedAs.Faceup).map(PlacementPicks.replaces)
      .toSet, Set(Some(homesteaders), Some(landWarden)))
    assertEquals(playing(options, PlayedAs.Facedown).map(_.ref),
      Vector[DecisionOptionRef](
        DecisionOptionRef.AdviserPlay(p1, 2, faceup = false)))

  test("with Silver Tongue as well the limit stays 2, and the Mob's discard " +
      "stays"):
    val ready = Table.start.hand(p1, homesteaders).pawn(p1, "Ancient City")
      .denizen(newsFromAfar, at = "Ancient City").adviser(p1, tongue)
      .adviser(p1, insomnia).peoplesFavor(Some(p1), 1).ready
    assertEquals(AdviserLimit.of(catalog, ready, p1), 2)
    assert(placementOptions(ready, homesteaders).exists(option =>
      PlacementPicks.replaces(option).contains(newsFromAfar)))
```

In `VisionPlaySuite`, replace the last three tests with:

```scala
  test("a Vision's placement offers the discard pile, the revealed Vision " +
      "spot and a facedown adviser slot, never a site"):
    val decision = parkedPlacement(visionInTemporaryHand())
    val offered = decision.query match
      case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
      case other => fail(s"expected a choose-one, got $other")
    assertEquals(offered, Vector[DecisionOptionRef](
      DecisionOptionRef.DiscardPile(Region.Provinces),
      DecisionOptionRef.RevealedVision(actor),
      DecisionOptionRef.AdviserPlay(actor, 0, faceup = false)))

  test("playing a Vision over a revealed one asks nothing more"):
    val state = visionInTemporaryHand(revealed = Some(existingVision))
    val after = answerPlacement(state, DecisionOptionRef.RevealedVision(actor))
    assertEquals(walkerIsFinished(after), true)
    assertEquals(revealedVisionOf(after).map(_.id), Some(newVision))
    assert(provincesDiscard(after).contains(existingVision))

  test("a Vision answer naming a site slot is rejected -- it is not offered"):
    val state = visionInTemporaryHand()
    assertEquals(answerPlacementResult(state, DecisionOptionRef.SiteSlot(
      CatalogNames.site("Ancient City"), 0)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${placeId(newVision)} does not offer the selected option")))
```

(import `CatalogNames`).

In `WildCrySuite`, the test "Wild Cry cannot be discarded while it is selected: it is not offered as the replacement of a faceup adviser" becomes "Wild Cry cannot be replaced while it is selected", with its last lines:

```scala
    val started = accepted(start(holding, modifiers))
    val chosen = accepted(keep(started, kept))
    assert(placeReplacing(chosen, kept, PlayedAs.Faceup, wildCry).isLeft,
      "Wild Cry was discardable")
    val done = accepted(placeReplacing(chosen, kept, PlayedAs.Faceup, extra))
```

(import `placeReplacing` from `SearchFixture`), keeping the asserts after `done` as they are.

In `WhenPlayedDecisionAfterPlaySuite`, the test "a replacement is answered before the card is played, and the question after the play still resolves" becomes "a Replace slot plays the card in the place of an adviser, and the question after the play still resolves", with:

```scala
    val replaced = accepted(for
      started <- start(full)
      chosen <- keep(started, heirloom)
      placed <- SearchFixture.placeReplacing(chosen, heirloom, PlayedAs.Faceup,
        wardOfSilence)
    yield placed)
    val done = accepted(answer(replaced, take))
```

keeping the asserts after `done`.

In `WalkerDecisionProjectorSuite`, after "a placement decision names the card being placed", add:

```scala
  test("a placement decision's heading names the card being placed"):
    val projected = present(projector.project(facedownAdviserPlacement),
      "the projected placement")
    assertEquals(projected.query.collect {
      case one: DecisionQueryProjection.ChooseOne => one.heading
    }.flatten, Some("Play Vow of Peace"))
```

`projector` is the suite's production projector, the one its subject-card tests call; if it is not, build one with `new WalkerDecisionProjector(catalog, new GamePresentationProjector(catalog))`.

- [ ] **Step 6: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayProcedureSuite"`
Expected: compilation fails: `discardDecisionId` is not a member of `CardPlayProcedure`.

- [ ] **Step 7: Rewrite `CardPlayProcedure.scala`**

Replace the whole file with:

```scala
package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.actions.{CardPlay, CardPlayRules}
import oathdigital.model._

/** The embeddable card play (card play slots design, "Placement procedure"):
  *
  *  1. `cardplay.place.{kind}.{value}`, a choose-one of the card's slots
  *     (`CardSlots`).
  *  2. When the answer was a Replace site slot and site discard permissions
  *     remain, `cardplay.discard.{kind}.{value}`: a choose-many of the other
  *     site cards, from none up to the permissions left, the replaced card
  *     locked.
  *  3. The line of each permission a site discard spent, the play's
  *     operations, then the `CardPlayedFacedown` or `CardPlayedFaceup` hook.
  *
  * The headings name neither the card nor the site; the projector does.
  */
object CardPlayProcedure:
  enum Origin { case TemporaryHand, FacedownAdviser }

  /** How a placement answer plays the card, for the readers that name a play
    * by what it does rather than by its option: the Game Log, and tests. A
    * Vision played faceup and a played Conspiracy are `Faceup`.
    */
  enum PlayedAs:
    case Discarded, AtSite, Faceup, Facedown

  /** How the placement option `ref` plays the card; `None` for a ref no
    * placement decision offers. */
  def playedAs(ref: DecisionOptionRef): Option[PlayedAs] = ref match
    case _: DecisionOptionRef.DiscardPile => Some(PlayedAs.Discarded)
    case _: DecisionOptionRef.SiteSlot => Some(PlayedAs.AtSite)
    case DecisionOptionRef.AdviserPlay(_, _, faceup) =>
      Some(if faceup then PlayedAs.Faceup else PlayedAs.Facedown)
    case _: DecisionOptionRef.RevealedVision | CardSlots.play =>
      Some(PlayedAs.Faceup)
    case _ => None

  def buildFacedown(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
    card <- facedownCard(args)
    tree <- build(catalog, ready, actor, card, Origin.FacedownAdviser)
  yield tree

  def rebuildFacedown(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] =
    facedownCard(args).map(unchecked(catalog, ready, actor, _,
      Origin.FacedownAdviser))

  private def facedownCard(args: Vector[DecisionOptionRef])
      : Either[OathViolation, WorldCardId] = args match
    case Vector(DecisionOptionRef.Denizen(id)) => Right(id)
    case Vector(DecisionOptionRef.Vision(id)) => Right(id)
    case _ => Left(OathViolation.InvalidEventOrder(
      "facedown-adviser play requires exactly one held card"))

  /** The placement subtree planned under one set of [[CardPlayRules]]. */
  final class PlacementBody private[cardplay](val rules: CardPlayRules,
      childrenAt: CardPlayRules => Vector[Operation]) extends Operation:
    override val children: Vector[Operation] = childrenAt(rules)
    private[cardplay] def adjust(change: CardPlayRules => CardPlayRules)
        : PlacementBody = new PlacementBody(change(rules), childrenAt)

  /** Generic rules-aware placement seam. A power changes the rules its play is
    * planned under without placing its identity in the card-play code.
    *
    * `children` is the play under the default rules. A `Transform` at
    * `SearchPlayAdviser` calls [[adjust]] with the children it was handed and
    * the change it wants. Every contributor's change is applied to the same
    * rules, so contributors compose in any order, and the result is the play
    * planned under all of them. A node a contributor appends after the body
    * is kept by the later ones.
    */
  final class PlacementTree private[cardplay](val card: WorldCardId,
      childrenAt: CardPlayRules => Vector[Operation])
      extends Operation:
    override val window: Option[PowerWindow] =
      Some(PowerWindow.SearchPlayAdviser)
    override val children: Vector[Operation] =
      childrenAt(CardPlayRules.default)

    def adjust(current: Vector[Operation])(
        change: CardPlayRules => CardPlayRules): Vector[Operation] =
      // An earlier contributor may have appended nodes after the body:
      // change the body in place and keep them.
      if current.exists(_.isInstanceOf[PlacementBody]) then current.map {
        case body: PlacementBody => body.adjust(change)
        case other => other
      }
      else Vector(new PlacementBody(change(CardPlayRules.default), childrenAt))

  /** Whether `card` is still at the origin the play started from. Once it is
    * not, the play has been made and the tree is settled from the answers.
    */
  private def heldAtOrigin(ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Boolean =
    val player = ready.game.current.players.find(_.player == actor)
    origin match
      case Origin.TemporaryHand =>
        ready.game.current.temporaryHands.getOrElse(actor, Vector.empty).contains(card)
      case Origin.FacedownAdviser => player.exists(_.advisers.exists {
        case DenizenState(id, Orientation.FaceDown, _) => id == card
        case VisionState(id, Orientation.FaceDown) => id == card
        case _ => false
      })

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Either[OathViolation, Operation] =
    if !heldAtOrigin(ready, actor, card, origin) then Left(
      OathViolation.InvalidSearchPlacement(
        "card is not held at the selected origin"))
    else Right(unchecked(catalog, ready, actor, card, origin))

  /** The tree with no check that the card is at its origin: a walker that
    * resumes rebuilds the tree from the state after the play, when it is not.
    */
  def unchecked(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: Origin): Operation =
    new PlacementTree(card, rules =>
      childrenFor(catalog, ready, actor, card, origin, rules))

  private val placementPrefix = "cardplay.place."
  private val discardPrefix = "cardplay.discard."

  /** `card`'s placement decision id, `cardplay.place.{kind}.{value}` -- the
    * one place that spells it, so every reader agrees with `childrenFor`.
    */
  def placementDecisionId(card: WorldCardId): String =
    s"$placementPrefix${card.kind}.${card.value}"

  /** `card`'s follow-up discard decision id,
    * `cardplay.discard.{kind}.{value}`. */
  def discardDecisionId(card: WorldCardId): String =
    s"$discardPrefix${card.kind}.${card.value}"

  /** The card whose placement was answered in `pending`, inverting
    * [[placementDecisionId]]. The parse splits the remainder at the first
    * '.' after the prefix: safe because no card kind contains a '.', whereas
    * the value that follows may contain anything, '.' and ':' included.
    * Takes the first recorded placement answer, which is right because a
    * single-card Search (the settle path this exists for) records exactly
    * one. `None` when nothing has answered a placement decision yet.
    */
  def placedCard(pending: PendingTree): Option[WorldCardId] =
    pending.answered.flatMap(answered => cardFor(answered.decisionId))
      .headOption

  private def cardFor(decisionId: String): Option[WorldCardId] =
    Option.when(decisionId.startsWith(placementPrefix))(
      decisionId.stripPrefix(placementPrefix)).flatMap { rest =>
      val dot = rest.indexOf('.')
      Option.when(dot >= 0)(rest.take(dot) -> rest.drop(dot + 1))
    }.flatMap {
      case ("denizen", value) => Some(DenizenId(value))
      case ("vision", value) => Some(VisionId(value))
      case _ => None
    }

  private def cardPlayOrigin(origin: Origin): CardPlay.Origin = origin match
    case Origin.TemporaryHand => CardPlay.Origin.TemporaryHand
    case Origin.FacedownAdviser => CardPlay.Origin.FacedownAdviser

  private def childrenFor(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: Origin,
      rules: CardPlayRules): Vector[Operation] =
    val from = cardPlayOrigin(origin)
    val held = heldAtOrigin(ready, actor, card, origin)
    val slots = if held then CardSlots.slots(catalog, ready, actor, card, from,
      rules) else Vector.empty
    val placeId = placementDecisionId(card)
    val discardId = discardDecisionId(card)
    val choose = Decide(placeId, actor, DecisionQuery.ChooseOne(
      slots.map(_.option), heading = Some("Play the card")))
    val selected = Branch((_, pending) => {
      val answer = pending.answered.collectFirst {
        case Answered(`placeId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
      }
      val more = pending.answered.collectFirst {
        case Answered(`discardId`, DecisionAnswer.ChooseManyAnswer(refs), _) =>
          refs
      }
      // Once the card has left its origin its slots can no longer be built:
      // the tree is settled from the answers instead, keeping the shape it
      // had when the decisions were asked.
      if !held then answer.toVector.flatMap(ref =>
        settled(ready, actor, card, ref, askedMore = more.nonEmpty))
      else answer.flatMap(ref => slots.find(_.ref == ref)).toVector.flatMap(
        slot => played(catalog, ready, actor, card, from, rules, slot, more))
    })
    Vector(choose, selected)

  /** The play of `slot`: the follow-up when it is a Replace site slot that
    * leaves permissions, the line of each permission its site discards
    * spent, the operations, and the hook. */
  private def played(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, from: CardPlay.Origin,
      rules: CardPlayRules, slot: CardSlot,
      more: Option[Vector[DecisionOptionRef]]): Vector[Operation] =
    val followUp = CardSlots.moreDiscards(catalog, ready, actor, card, from,
      rules, slot).map(offer => Decide(discardDecisionId(card), actor,
        DecisionQuery.ChooseMany(0, offer.most, offer.options,
          heading = Some("Discard more cards from the site"),
          locked = Vector(offer.locked))))
    val extra = more.getOrElse(Vector.empty).flatMap(siteCard)
    val apply = BuildOps((state, _) => CardPlay.operations(catalog, state,
      actor, card, slot.placement, from, extra))
    // The lines come after the last answer and before the play, joining the
    // play's own node so the hook keeps its place.
    val credited: Operation = CardSlots.credits(catalog, ready, actor, card,
        rules, slot.replacedAtSite.toVector ++ extra) match
      case Left(error) => BuildOps((_, _) => Left(error))
      case Right(notes) if notes.isEmpty => apply
      case Right(notes) => Sequence(notes :+ apply)
    followUp.toVector ++ Vector(credited) ++ hook(ready, actor, card,
      slot.placement)

  /** The shape of a play once the card has left its origin: the follow-up if
    * it was asked, the play, and the hook. A resumed walk starts past the
    * decisions and the play, so neither runs again. */
  private def settled(ready: ReadyGame, actor: PlayerId, card: WorldCardId,
      ref: DecisionOptionRef, askedMore: Boolean): Vector[Operation] =
    playedAs(ref).map(placementOf).toVector.flatMap { placement =>
      val followUp = Option.when(askedMore)(Decide(discardDecisionId(card),
        actor, DecisionQuery.ChooseMany(0, 0, Vector.empty)))
      val play = BuildOps((_, _) => Left(OathViolation.InvalidSearchPlacement(
        "the card has already been played")))
      followUp.toVector ++ Vector(play) ++ hook(ready, actor, card, placement)
    }

  private def placementOf(way: PlayedAs): SearchPlacement = way match
    case PlayedAs.Discarded => SearchPlacement.Discard
    case PlayedAs.AtSite => SearchPlacement.Site(None)
    case PlayedAs.Faceup => SearchPlacement.Adviser(Orientation.FaceUp, None)
    case PlayedAs.Facedown => SearchPlacement.Adviser(Orientation.FaceDown, None)

  private def hook(ready: ReadyGame, actor: PlayerId, card: WorldCardId,
      placement: SearchPlacement): Vector[Operation] = placement match
    case SearchPlacement.Adviser(Orientation.FaceDown, _) =>
      Vector(CardPlayedFacedown(card, actor))
    case _ => CardPlay.playedSource(ready, actor, card, placement)
      .map(CardPlayedFaceup(card, _)).toVector

  private def siteCard(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None
```

The settled follow-up's empty choose-many is never walked: the resume cursor starts past it. It stands in so the play and the hook keep their indices.

- [ ] **Step 8: Delete the legacy planner and move Search's discards**

In `CardPlay.scala`, delete everything from the `// ---- Until card play offers card slots (Task 6): the old choices. ----` line to the end of the file, and the `OperationRestrictions` import. `grep -rn "legalChoices\|plannedOperations\|CardPlay.Choice" src frontend shared` must find nothing.

In `SearchProcedure.scala`, `removeOthers` calls `CardPlay.operations(catalog, state, actor, card, SearchPlacement.Discard, CardPlay.Origin.TemporaryHand)` in place of `CardPlay.plannedOperations(...)`.

- [ ] **Step 9: Name the card in the projected heading**

In `WalkerDecisionProjector.parked`, the `queryProjection(...).map(projected => WalkerDecisionProjection(...))` passes `query = Some(headed(ready, viewer, decide.decisionId, projected))`. In `subjectCards`, the prefixes become `Vector("cardplay.place.", "cardplay.discard.")`. Add below `subjectCards`:

```scala
  /** A card play's heading names its card or its site, which the engine's
    * may not (a game object's name is resolved here): "Play {card}" and
    * "Discard more cards from {site}". Every other decision keeps its own.
    */
  private def headed(ready: ReadyGame, viewer: Option[PlayerId],
      decisionId: String, query: DecisionQueryProjection)
      : DecisionQueryProjection =
    val heading =
      if decisionId.startsWith("cardplay.place.") then
        subjectCards(ready, viewer, decisionId).headOption
          .filterNot(_.hidden).map(card => s"Play ${card.name}")
      else if decisionId.startsWith("cardplay.discard.") then
        viewer.flatMap(player => ready.game.current.players
          .find(_.player == player)).flatMap(_.pawnSite).map(site =>
            s"Discard more cards from ${presentation.siteLabel(site)}")
      else None
    heading.fold(query)(text => query match
      case one: DecisionQueryProjection.ChooseOne =>
        one.copy(heading = Some(text))
      case many: DecisionQueryProjection.ChooseMany =>
        many.copy(heading = Some(text))
      case other => other)
```

Update `subjectCards`' doc: the decisions it reads are `cardplay.place.*` and `cardplay.discard.*`, and a placement now offers card slots rather than buttons.

In `DetailLines.NarratedPrefixes`, replace `"cardplay.replace."` with `"cardplay.discard."`, and in its doc "the discard line of a replaced adviser" with "the follow-up discard of a Replace site slot". The discards themselves are delta lines.

- [ ] **Step 10: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayProcedureSuite oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.SiteDiscardFirstSuite oathdigital.gameplay.HomelandLineSuite oathdigital.gameplay.CardPlayRulesSuite oathdigital.gameplay.VisionPlaySuite oathdigital.gameplay.powers.banner.PeoplesFavorMobSuite oathdigital.gameplay.powers.search.CropRotationSuite oathdigital.gameplay.powers.rest.SilverTongueSuite oathdigital.gameplay.powers.rest.InsomniaSuite oathdigital.gameplay.powers.cardplay.WildCrySuite oathdigital.gameplay.powers.whenplayed.WhenPlayedDecisionAfterPlaySuite oathdigital.application.WalkerDecisionProjectorSuite"`
Expected: PASS.

Then run `./sbtw "test" "frontend/test"`. Expected: PASS. Every suite Task 3 moved to `PlayedAs` passes unchanged, and the golden logs pass: the default answer is still the discard pile, and the log reads plays through `playedAs`. A suite that fails here asserted the old buttons or the old replacement decision in a way the grep in Task 3 missed; move it to `PlacementPicks` the same way. Then run the two scripts.

- [ ] **Step 11: Commit**

```bash
git rm src/test/scala/oathdigital/gameplay/HomelandRuleSuite.scala src/test/scala/oathdigital/gameplay/CardPlayRestrictionsSuite.scala
git add src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/main/scala/oathdigital/gameplay/actions/CardPlay.scala src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/test/scala/oathdigital/testkit/PlacementPicks.scala src/test/scala/oathdigital/gameplay/PlacementFixture.scala src/test/scala/oathdigital/gameplay/powers/SearchFixture.scala src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala src/test/scala/oathdigital/gameplay/SiteDiscardFirstSuite.scala src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala src/test/scala/oathdigital/gameplay/powers/search/CropRotationSuite.scala src/test/scala/oathdigital/gameplay/powers/rest/SilverTongueSuite.scala src/test/scala/oathdigital/gameplay/powers/rest/InsomniaSuite.scala src/test/scala/oathdigital/gameplay/powers/cardplay/WildCrySuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedDecisionAfterPlaySuite.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala
git commit -m "feat(cardplay): play a card to a card slot, with a follow-up discard and credited lines"
```

---

### Task 7: Reasons end to end, the roadmap, and a browser check

Spec, Rulings ("Reasons") and Testing. The powers that forbid a faceup play (Vow of Obedience, Sacred Ground) mark the revealed Vision spot unavailable through the walker's look-ahead, and a printed protection's reason reaches the projection. Then the roadmap records what landed, and a scratch game plays a Search through the panel rows.

**Files:**
- Test: `src/test/scala/oathdigital/application/CardPlayReasonsSuite.scala` (new)
- Modify: `docs/ROADMAP.md` (Phase - Card play UI; the deferred **walker-native card play through card slots** item)

**Interfaces:**
- Consumes: Tasks 1 to 6.
- Produces: nothing new.

- [ ] **Step 1: Write the end-to-end reason tests**

Create `src/test/scala/oathdigital/application/CardPlayReasonsSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.cardplay.{SacredGround, SacredGroundCard,
  VisionPlayFixture, VowOfObedience}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.DecisionOptionProjection
import oathdigital.testkit.{CatalogNames, Outcomes, PlacementPicks, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present}

/** Why a card play option is not on offer, from the engine to the projected
  * row (card play slots design, "Rulings"): a power's note for the faceup
  * plays it forbids, a printed protection's words for a discard it refuses.
  */
class CardPlayReasonsSuite extends munit.FunSuite:
  private val projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog))
  private val powers = WalkerPowerCatalog.default(catalog)
  private val spot = DecisionOptionRef.RevealedVision(p1)

  /** The rows of the decision p1 is shown at `state`. */
  private def rows(state: OathState)(using munit.Location)
      : Vector[DecisionOptionProjection] =
    present(projector.project(ScopedProjectionContext(Outcomes.ready(state),
      Some(p1))), "the projected decision").query.toVector
      .flatMap(_.offeredOptions)

  /** A Search by p1 that drew Faith, parked on its placement, on the table
    * `arrange` sets. */
  private def searchedFaith(arrange: Table => Table): OathTransition =
    VisionPlayFixture.searched(arrange(Table.from(
      SearchFixture.staged(Vector(VisionRules.Faith), supply = 5))).unchecked,
      VisionRules.Faith)

  /** The power whose note hid the revealed Vision spot at `state`. */
  private def hiddenBy(state: OathState)(using munit.Location)
      : Option[PowerId] =
    PlacementPicks.parkedOptions(state, powers).collectFirst {
      case DecisionOption.Unavailable(option, OptionReason.Noted(power, _))
          if option.ref == spot => power
    }

  private def assertSpotRefused(state: OathState)(using munit.Location): Unit =
    val shown = present(rows(state).find(_.kind == spot.kind),
      "the revealed Vision row")
    assertEquals(shown.state, "unavailable")
    assert(shown.reason.exists(_.contains("cannot play a Vision faceup")),
      shown.reason.toString)

  test("a Vow of Obedience holder sees the revealed Vision spot refused, " +
      "with the Vow's reason"):
    val parked = searchedFaith(_.adviser(p1,
      CatalogNames.denizen("Vow of Obedience")))
    assertEquals(hiddenBy(parked.state), Some(VowOfObedience.id))
    assertSpotRefused(parked.state)

  test("a player away from Sacred Ground sees the revealed Vision spot " +
      "refused, with its reason"):
    val parked = searchedFaith(_.edifice(SacredGroundCard.id,
      EdificeSide.Intact, at = CatalogNames.site("Broken Peaks")))
    assertEquals(hiddenBy(parked.state), Some(SacredGround.id))
    assertSpotRefused(parked.state)

  test("a faceup locked adviser's Replace rows say Locked"):
    val sealingWard = CatalogNames.denizen("Sealing Ward")
    val errandBoy = CatalogNames.denizen("Errand Boy")
    val ready = Vector("Rain Boots", "Ancient Binding").foldLeft(Table.from(
      SearchFixture.staged(Vector(errandBoy), supply = 5)))(
      _.adviser(p1, _, facedown = true)).adviser(p1, sealingWard).unchecked
    // The Search may draw more than one card: keep Errand Boy.
    val parked = accepted(SearchFixture.start(ready)
      .flatMap(SearchFixture.keep(_, errandBoy)))
    val replacing = rows(parked.state).filter(row =>
      row.kind == "adviser-play" &&
        row.replaces.exists(_.cardId == sealingWard.value))
    assert(replacing.nonEmpty)
    assert(replacing.forall(row => row.state == "unavailable" &&
      row.reason.contains("Locked: cannot be discarded")), replacing.toString)
```

- [ ] **Step 2: Run them**

Run: `./sbtw "testOnly oathdigital.application.CardPlayReasonsSuite"`
Expected: PASS: the reasons come from Task 4's slot builder and spec 1's look-ahead, which this task only verifies. If a test fails, fix the engine, not the test: a Vow test that finds the spot available with no note means the look-ahead no longer reaches the `CardPlayedFaceup` hook behind `RevealedVision`.

- [ ] **Step 3: Record the engine in the roadmap**

In `docs/ROADMAP.md`, under **Phase - Card play UI**, after its "Settled already" list, add:

```markdown
Plan 1 of the [card play slots spec](superpowers/specs/2026-10-04-card-play-slots-design.md)
landed the engine: card play offers card slots, shown as Action panel rows
until plan 2 puts them on the board.

- [ ] **A replacing play takes the replaced card's slot.** `Play` has no
  slot position, so a card played over another joins the end of the site's
  card row or the adviser row instead of taking the replaced card's index.
```

In the deferred **walker-native card play through card slots** item, replace the sentence that begins "Until then Mob uses a single `PlacementRules` value" with "Plan 1 of that spec built the engine; plan 2 puts the slots on the board and marks this done." Run `python3 scripts/check-markdown-links.py`. Expected: PASS.

- [ ] **Step 4: Play a Search through the panel rows in the browser**

From the worktree root, with `S` the session scratchpad plus `/db-card-play-slots`:

```bash
mkdir -p $S && cp var/oathdigital.script var/oathdigital.properties var/oathdigital.log $S/ && cp -r var/oathdigital.lobs $S/
```

```bash
./sbtw "frontend/fastLinkJS" "runMain oathdigital.server.OathServer --port 8081 --database-path $S/oathdigital"
```

Run the second command through Bash with `run_in_background` and wait for "Start completed" in its log. Open `http://127.0.0.1:8081/?mode=server` in the built-in browser at 1440×900, press "New game", and play Setup through to the first Act with the default choices. Then, in one batched pass with `read_page` and one screenshot per check:

1. Search the world deck. If more than one card is drawn, keep one. The Action panel shows the card being played, the heading "Play {card}", and rows "Discard to the {region} discard", "Empty slot at {site}", "Adviser slot 1, faceup" and "Adviser slot 1, facedown" (a Vision shows "Revealed Vision" in place of the faceup row; a site-only card has no faceup row).
2. Click "Adviser slot 1, faceup": the card joins the player's advisers faceup, and the Game Log reads "Played {card} as an adviser".
3. `read_console_messages` with `onlyErrors` shows nothing new.

Fix every failure in one batch, reload, confirm once, and stop the server with `lsof -ti :8081 | xargs kill`. Reset the window with `resize_window` preset `desktop`.

- [ ] **Step 5: Run the final gates**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
Expected: PASS. Compare the server test count with Task 1's: it rises by the new suites less the two deleted ones.

- [ ] **Step 6: Commit**

```bash
git add src/test/scala/oathdigital/application/CardPlayReasonsSuite.scala docs/ROADMAP.md
git commit -m "test(cardplay): reasons for card slots end to end, and record the engine in the roadmap"
```

Then finish the branch with superpowers:finishing-a-development-branch: `ExitWorktree(keep)`, check `git log -1` on `main`, and merge `card-play-slots` into local `main` with `--ff-only` after `git merge main` in the worktree if `main` moved.
