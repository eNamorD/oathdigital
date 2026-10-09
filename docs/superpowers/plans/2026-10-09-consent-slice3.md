# Consent System, Slice 3 (Hard-Block Cards) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An enemy blocked by Secret Police, or by the Hall of Ministers on any discard they choose, can ask the card's ruler for consent. Forgotten Vault stays unwaivable.

**Architecture:**

- **Secret Police** joins the tree path that slice 1 built. Its faceup-Vision `Restriction` declares a `Waiver`, so the look-ahead offers to ask about a hidden faceup play, and a grant answers the placement with it.
- **Hall of Ministers** is an operation restriction, not a walker power, so it gets its own consent route:
  - Its refusal (`OperationReason`) names an `OperationWaiver`: the power, the granter, and the key a grant is remembered under. The key names the discarded card, not a decision, so a grant covers that card's discard for the rest of the turn on whichever path discards it.
  - One ledger check on this path: `ConsentedRestriction` wraps each printed restriction in `OperationRestrictions.active`. It drops a refusal once its grant is remembered.
  - The asks are judged where the options are built, from the refusals of each option's plan (`OperationConsent`):
    - card play's Replace site slots and their follow-up (`CardSlots`);
    - the site-card questions of Roving Terror, Taming Charm and Great Feast (`SiteCards`).
  - `OptionReason.Askable` and `ConsentRequest` gain `covers`, the keys a grant settles when they are not the asked option's.
  - `ConsentPowers` registers the Hall, so its ruler gets the grid, the stale-preference check and the log.
- **Frontend.** A board decision's Decide buttons (the Conspiracy's "Play") ask when askable. A site card that prints a power the viewer rules opens with their preferences grid; the preferences projection gains the card's id.

**Tech Stack:** Scala 3 on the JVM (server, munit) and Scala.js 3 (frontend, scalajs-dom, munit on jsdom), built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-08-consent-system-design.md`. Read the whole spec before starting. Skim the decisions of the slice 1 and slice 2 plans (`docs/superpowers/plans/2026-10-09-consent-slice1.md` and `-slice2.md`, "Plan decisions beyond the spec"). This plan covers the spec's "Slices" item 3. The spec designed Hall of Ministers only as a "cannot" power. Its consent route below is this plan's design, and Task 5 records it in the spec.

## Plan decisions beyond the spec

The code forced these, or the spec left them open. Task 5 records each one in the spec.

1. **Forgotten Vault is never waivable.** All of its effect is at a Campaign's target selection (catalog batch 3 rulings, "Target protections corrected"). Ruling 2 allows no consent inside a Campaign. The user confirmed on 2026-10-09 that the batch 3 ruling stands, so a played Conspiracy may still take the Vault ruler's relics.
2. **Secret Police stays on slice 1's tree path, untied.** A Search keeps one card, and a facedown-adviser play plays one, so a walk answers one placement. Slice 1's per-walk ledger match is exact there. Tying tree-path grants to their decision stays with slice 4.
3. **The Hall's refusal declares its waiver.** `OperationReason` gains `waiver: Option[OperationWaiver]`. The refusal is the one place the operation path knows the power, the granter and the card. The Hall stays an `OperationRestriction`.
4. **A Hall grant covers one card's discard, not a decision.** Its key is `ConsentKey(Hall, requester, "operation.discard", the card)`. The operation check never sees the decision that led to the discard, so the key cannot name it. A grant to discard Errand Boy therefore holds for the rest of the turn, by that player, on any path; a refusal likewise. `Askable.covers` and `ConsentRequest.covers` carry these keys, and `ConsentRequest.keys` settles them instead of the asked option's. The journal writes `covers` only when it is not empty, so older journals read back unchanged.
   - **Amended after execution (2026-10-09).** An official ruling makes enforcement all-or-nothing: a waived Hall lets every discard of the same moment through. A batch of operations is that moment. `OperationConsent.together` lifts the power for the whole batch once any of its operations has a grant, in `OperationPipeline.run` and when options are judged. A grant for a Replace slot's card therefore frees the follow-up's other site cards.
5. **One ledger check on the operation path.** `ConsentedRestriction` wraps each printed restriction in `OperationRestrictions.active`. Every walker step, every operation screen and every card-play judgement reads that set, so they all agree.
6. **Asks are judged where options are built, with no search.** `OperationConsent.ask` turns an option whose plan only waivable refusals stop into `Askable`. Any other refusal keeps its written reason (`Protected by the Hall of Ministers`, `Locked: cannot be discarded`).
   - The plan judged is the plan that runs, and nothing after the discard in these trees reads the grant. So a grant opens the option.
   - The placement is a choose-one, so the grant also answers it. If a later power ever adds a tree restriction there, the answer would be refused; the requester can still withdraw with Undo.
7. **The site-card questions now show a protected card instead of hiding it.** Roving Terror, Taming Charm and Great Feast used to hide a card the Hall protects. Now it shows as unavailable, with its reason, or as askable.
8. **A site-card question is asked only when some card may be chosen.** A choose-one has no empty answer. If the only candidate were askable, a refusal would leave the walk parked with nothing to choose, and nothing would resume it. So when the Hall protects every candidate, the question is not asked, as before, and "No card could be replaced." follows. Asking in that case is deferred. Card play is unaffected: its placement always offers the discard pile, and its follow-up is a choose-many that may be left empty.
9. **The Hall's other paths stay enforced without consent.** They have no option to ask from:
   - Dark Enforcer's discards and the region discards (Dazzle, Threatening Roar, Riots) skip a protected card silently;
   - Mounted Library's self-discard has no consent point;
   - setup discards involve no enemy's choice;
   - a Campaign plan's discard falls under ruling 2.

   Slice 4's pay-or-ask step could later reach the first two.
10. **The Hall's consent note is a game-rule note.** The Hall is not a walker power, so `RuleNotes.all` registers its note key for the hover reason and the log. Its ordinary reason stays card play's written `Protected by the Hall of Ministers`.
11. **Frontend reach.**
    - The board decision's Decide buttons ask when askable. That covers the Conspiracy's "Play", and closes that part of the spec's last deferred item.
    - A site card opens with the grid when the viewer rules a power it prints. `ConsentPreferencesProjection` gains `cardId` to match it by.
    - The revealed Vision spot, site cards and site slots already ask, through `BoardOptions`.
12. **No browser acceptance run in this slice.** The reason is the same as slice 1 decision 9: no player-ruled board can be seeded yet. Task 5 extends the roadmap's consent play-test item.

## Global Constraints

- **Build rules.** Every project builds with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import, private member or local fails the build. Delete whatever the compiler names. A match on a sealed type must stay exhaustive.
- **File size.** Every Scala file, production or test, stays at or under 800 lines (`scripts/check-architecture.py`, `BackendArchitectureSuite`). Current sizes:
  - `WalkerDecisionProjector.scala` 690, `OathRulesWalker.scala` 625, `Decisions.scala` 577;
  - `ServerUiSupport.scala` 468, `WalkerPowerGather.scala` 443, `CardSlotsSuite.scala` 414.

  Keep each addition to what the task shows.
- **Architecture guard.** `scripts/check-architecture.py` reads a `Map(` call as a lookup table. Build maps the way the surrounding code does. A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. No power name appears in walker sources.
- **Ids and wording, exactly:**
  - Secret Police's consent note key is `no-faceup-consent`, worded `Cannot play a Vision faceup without ruler's consent.` The reason shown is `Secret Police: Cannot play a Vision faceup without ruler's consent.` The ordinary reason stays `no-faceup`.
  - The Hall's consent note key is `no-discard-consent`, worded `Cannot discard cards from its ruler's sites without ruler's consent.` The reason shown is `Hall of Ministers: Cannot discard cards from its ruler's sites without ruler's consent.` The ordinary reason stays `Protected by the Hall of Ministers`.
  - The Hall's power id is `edifice.e16.intact`. The decision slot of its keys is `operation.discard`.
  - The journal field is `covers`, a list of `{powerId, requester, decisionId, target}`. The projection field is `cardId`.
  - Every other id, command, event, log line and UI string is slice 1's.
- **Rulings** (spec, "Rulings"). These apply: 1, 2, 3, 4, 5, 8, 10, 12, 13, 14 and 15–19. No option can carry blocks from two rulers yet, so ruling 9's several granters still cannot arise. `OperationConsent` groups by granter anyway.
- **Never touch the live database `var/oathdigital`.** Never run `preview_start`.
- **Concurrent sessions.** Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree on branch `feat/consent-slice3`.
  - `EnterWorktree` branches from `origin`, which lags local `main`, so fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
  - Copy `.superpowers/` from the main checkout if a ledger is in use.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Verification commands:**
  - One server suite: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.SecretPoliceConsentSuite"` (any fully qualified suite name).
  - One frontend suite: `./sbtw "frontend/testOnly oathdigital.frontend.BoardDecisionPanelSuite"`.
  - One shared suite: `./sbtw "sharedJVM/testOnly oathdigital.protocol.ProjectionProtocolSuite"`. If sbt names the project differently, use the name `./sbtw projects` lists for `shared`.
  - Whole server or frontend: `./sbtw test` or `./sbtw frontend/test`.
  - Full gate (Task 5):
    1. `./sbtw "test" "frontend/test"`
    2. `./sbtw frontend/fullOptJS`
    3. `./sbtw coverage test coverageReport`
    4. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
    5. `git diff --check`

## File map

Created:

- `src/main/scala/oathdigital/gameplay/operations/ConsentedRestriction.scala`: the operation path's ledger check.
- `src/main/scala/oathdigital/gameplay/operations/OperationConsent.scala`: refusals of a plan, and the ask they allow.
- `src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceConsentSuite.scala`
- `src/test/scala/oathdigital/gameplay/operations/HallOfMinistersConsentSuite.scala`
- `src/test/scala/oathdigital/application/HallOfMinistersConsentServiceSuite.scala`

Modified:

- Model:
  - `model/OperationReason.scala`: `OperationWaiver`, and `OperationReason.waiver`.
  - `model/Decisions.scala`: `Askable.covers`.
  - `model/Consent.scala`: `ConsentRequest.covers` and `keys`.
- Operations:
  - `gameplay/operations/HallOfMinisters.scala`: the waiver, the granter and the note.
  - `gameplay/operations/OperationRestrictions.scala`: `active` wraps the printed restrictions.
- Powers and actions:
  - `gameplay/powers/cardplay/SecretPolice.scala`: the waiver and `noFaceupConsent`.
  - `gameplay/powers/ConsentPowers.scala`: registers the Hall.
  - `gameplay/actions/RuleNotes.scala`: the Hall's note key.
  - `gameplay/actions/cardplay/CardSlots.scala`: the public `judge`, with asks.
  - `gameplay/powers/action/SiteCards.scala` and `RovingTerror.scala`: judged options.
- Walker: `gameplay/OathRulesWalker.scala`: `consentAsk`.
- Application:
  - `application/ConsentDesk.scala`: requests carry `covers`.
  - `application/WalkerDecisionProjector.scala`: four-field `Askable` patterns.
  - `application/ConsentProjector.scala`: `cardId`.
- Serialization: `serialization/ConsentEventCodec.scala`, for `covers`.
- Shared protocol: `ConsentProjectionDtos.scala` and `ConsentProjectionCodec.scala`, for `cardId`.
- Frontend: `BoardDecisionPanel.scala`, `CardInspection.scala`, `CardInspectionOverlay.scala`, `GameTableShell.scala`, `SitePowerLinks.scala`, `BoardOptions.scala` and `ServerUiSupport.scala`.
- Tests:
  - server: `ConsentPowersSuite`, `OperationRestrictionsSuite`, `ConsentEvolutionSuite`, `ConsentEventWireSuite`, `CardSlotsSuite`, `ConsentProjectionSuite`, `NarrowPassConsentSuite` and `NarrowPassCampaignConsentSuite`;
  - shared: `ProjectionProtocolSuite`;
  - frontend: `BoardDecisionPanelSuite`, `SitePowerGridSuite` and `PlayerBoardSuite`.
- Docs: the spec, `CONTEXT.md`, `docs/ROADMAP.md` and `docs/rules/implementation-traceability.md`.

All `src/main/scala/...` paths above are under `src/main/scala/oathdigital/`.

---

### Task 1: Secret Police is waivable

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceConsentSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/SecretPolice.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala`

**Interfaces:**
- Consumes (slices 1 and 2): `Waiver(granter: ReadyGame => Option[PlayerId], source: PowerSourceRef, note: (PowerCtx, DecisionOptionRef) => PowerNote)`; `Restriction(fn, note, waiver)`; `OptionReason.Askable(power, note, asks)`; `OathRules.consentAsks(state, requester, decisionId, target)`; `VisionPlayFixture.searched(ready, vision)` and `fromAdvisers(ready, vision)`; `PhasePowerHarness.queryOf(transition, viewer)`.
- Produces: `SecretPolice.noFaceupConsent: NoteKey`; Secret Police registered in `ConsentPowers.default`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceConsentSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.{CardPlayProcedure, CardSlots}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.ConsentAskProjection
import oathdigital.testkit.{CatalogNames, PhasePowerHarness, PlacementPicks,
  Table}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Which faceup plays Secret Police makes askable (consent design, ruling 1
  * and slice 3): an enemy at a site the Police's ruler rules may ask that
  * ruler for consent to play a Vision faceup. */
class SecretPoliceConsentSuite extends munit.FunSuite:
  import PowerFixture.actor
  import VisionPlayFixture.{fromAdvisers, searched}

  private val police = CatalogNames.denizen("Secret Police")
  private val vow = CatalogNames.denizen("Vow of Obedience")
  private val other = Table.p2
  private val home = CatalogNames.site("Ancient City")
  private val faith = VisionRules.Faith
  private val placement = CardPlayProcedure.placementDecisionId(faith)
  private val spot = DecisionOptionRef.RevealedVision(actor)
  private val asksOther = Vector(ConsentAsk(other, Vector(SecretPolice.id)))

  /** A Search that will draw `vision`, with p1 at Ancient City beside the
    * Police; p2 rules Ancient City unless `arrange` says otherwise. */
  private def board(arrange: Table => Table = _.warbandsAt(home, other, 2),
      vision: VisionId = faith): ReadyGame =
    arrange(Table.from(SearchFixture.staged(Vector(vision), supply = 5))
      .pawn(actor, home).denizen(police, at = home)).unchecked

  /** `ready` remembering `answer` for the faceup Faith, this turn. */
  private def remembering(ready: ReadyGame, answer: ConsentAnswer)
      : ReadyGame =
    ready.copy(consent = ConsentState(
      answers = Map(ConsentKey(SecretPolice.id, actor, placement, spot) ->
        answer),
      turn = Some(ConsentTurn.of(ready))))

  private def faceup(from: OathTransition, ref: DecisionOptionRef = spot)(
      using munit.Location): DecisionOption =
    present(PlacementPicks.parkedOptions(from.state,
      WalkerPowerCatalog.default(catalog)).find(_.ref == ref),
      "the faceup play")

  private def assertNotedFaceup(option: DecisionOption)(
      using munit.Location): Unit = option match
    case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
      assertEquals(note.key, "no-faceup")
    case other => fail(s"expected a plain unavailable faceup play, got $other")

  private def askable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case _ => false

  test("an enemy's faceup Vision is askable of the Police's ruler"):
    faceup(searched(board(), faith)) match
      case DecisionOption.Unavailable(_, OptionReason.Askable(power, note,
          asks)) =>
        assertEquals(power, SecretPolice.id)
        assertEquals(note, SecretPolice.noFaceupConsent(
          PowerSourceRef.Card(police)))
        assertEquals(asks, asksOther)
      case other => fail(s"expected an askable faceup play, got $other")

  test("the player sees it askable, with the consent reason and whom to " +
      "ask"):
    val row = present(PhasePowerHarness.queryOf(searched(board(), faith),
      actor).toVector.flatMap(_.offeredOptions)
      .find(_.kind == "revealed-vision"), "the revealed Vision row")
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Secret Police: Cannot play a Vision " +
      "faceup without ruler's consent."))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Secret Police"))))

  test("under bandit rule the faceup play keeps its ordinary reason: " +
      "bandits never consent"):
    assertNotedFaceup(faceup(searched(board(_.bandits(home, 2)), faith)))

  test("a Vow of Obedience the player holds keeps it unavailable, not " +
      "askable"):
    val vowed = board(_.warbandsAt(home, other, 2).adviser(actor, vow))
    assertNotedFaceup(faceup(searched(vowed, faith)))

  test("the Conspiracy's Play is askable too"):
    val drawn = searched(board(vision = VisionRules.Conspiracy),
      VisionRules.Conspiracy)
    assert(askable(faceup(drawn, CardSlots.play)))

  test("a facedown Vision played from the advisers is askable too"):
    val ready = Table.start.pawn(actor, home).denizen(police, at = home)
      .warbandsAt(home, other, 2).ready
    assert(askable(faceup(fromAdvisers(ready, faith))))

  test("a grant remembered for the placement opens the faceup play, and the " +
      "Vision is revealed"):
    val parked = searched(remembering(board(), ConsentAnswer.Granted), faith)
    assert(faceup(parked).available)
    val placed = accepted(SearchFixture.place(parked, faith, PlayedAs.Faceup))
    assertEquals(SearchFixture.after(placed).game.current.players
      .find(_.player == actor).flatMap(_.revealedVision).map(_.id),
      Some(faith))

  test("a refusal remembered this turn leaves the ordinary reason, and the " +
      "faceup play is refused"):
    val parked = searched(remembering(board(), ConsentAnswer.Refused), faith)
    assertNotedFaceup(faceup(parked))
    assert(SearchFixture.place(parked, faith, PlayedAs.Faceup).isLeft)

  test("the asks of the faceup play"):
    assertEquals(SearchFixture.rules.consentAsks(searched(board(), faith)
      .state, actor, placement, spot), Right(asksOther))
```

If `revealedVision` holds a state whose card field is not `id`, use the field `CardSlots.faceup` reads (`held.id`).

Add to `ConsentPowersSuite.scala`. Add imports for `SecretPolice` (from `oathdigital.gameplay.powers.cardplay`) and `CatalogNames` and `Table` (from `oathdigital.testkit`):

```scala
  test("Secret Police is waivable, its granter the player who rules its " +
      "site, and nobody under bandits"):
    val police = CatalogNames.denizen("Secret Police")
    val home = CatalogNames.site("Ancient City")
    val policed = Table.start.denizen(police, at = home)
    assertEquals(powers.source(SecretPolice.id),
      Some(PowerSourceRef.Card(police)))
    assertEquals(powers.granter(policed.warbandsAt(home, p2, 2).ready,
      SecretPolice.id), Some(p2))
    assertEquals(powers.granter(policed.bandits(home, 2).ready,
      SecretPolice.id), None)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.SecretPoliceConsentSuite oathdigital.gameplay.powers.ConsentPowersSuite"`
Expected: compilation FAILS, because `SecretPolice.noFaceupConsent` does not exist.

- [ ] **Step 3: Declare the Police's waiver**

In `SecretPolice.scala`:

1. Add `Waiver` to the imports (`oathdigital.gameplay.powerresolver._` already covers it).
2. Append to the class doc comment, before its last line, the sentence `Its ruler may consent to a faceup play (consent design, slice 3); bandits and the Empire never do.`
3. Replace the body of `case object SecretPolice`, from `override def noteKeys` through the `contributions` map, with:

```scala
  /** "Cannot play a Vision faceup without ruler's consent.": the reason of
    * a faceup play the Police's ruler may be asked about. */
  val noFaceupConsent: NoteKey = NoteKey("no-faceup-consent", Vector(
    NotePart.Text("Cannot play a Vision faceup without ruler's consent.")))

  override def noteKeys: Vector[NoteKey] =
    Vector(VisionPlay.noFaceup, noFaceupConsent)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx), VisionPlay.note(cardId),
        Some(waiver))))

  /** "Enemies cannot" (consent design, ruling 1): the player who rules the
    * Police's site may waive it. */
  private def waiver: Waiver = Waiver(
    ready => SiteRulers.rulerOfCard(ready, cardId).collect {
      case SiteRuler.Player(player) => player },
    PowerSourceRef.Card(cardId),
    (_, _) => noFaceupConsent(PowerSourceRef.Card(cardId)))
```

Keep `def source` and `private def blocked` as they are.

- [ ] **Step 4: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.SecretPoliceConsentSuite oathdigital.gameplay.powers.ConsentPowersSuite oathdigital.gameplay.powers.cardplay.SecretPoliceSuite"`
Expected: PASS, with 9, 6 and 9 tests.

`SecretPoliceSuite`'s first test still sees no faceup play offered. A choosable play is offered, and the askable one is unavailable. Placing faceup is still refused as not offered. If that refusal's message changes, the askable option was answered directly: check that `PlacementPicks.find` prefers a choosable option.

- [ ] **Step 5: Run the whole server**

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/SecretPolice.scala \
  src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceConsentSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala
git commit -m "feat(consent): let Secret Police's ruler consent to a faceup Vision"
```

---

### Task 2: The Hall's refusal names who may waive it, and grants reach it

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/ConsentedRestriction.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/OperationConsent.scala`
- Modify: `src/main/scala/oathdigital/model/OperationReason.scala`
- Modify: `src/main/scala/oathdigital/model/Decisions.scala:337-341`
- Modify: `src/main/scala/oathdigital/model/Consent.scala:56-63`
- Modify: `src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala:24-27`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/RuleNotes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/OathRulesWalker.scala:302-320`
- Modify: `src/main/scala/oathdigital/application/ConsentDesk.scala` (`request`)
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:354,576`
- Modify: `src/main/scala/oathdigital/serialization/ConsentEventCodec.scala`
- Test: `OperationRestrictionsSuite`, `ConsentEvolutionSuite`, `ConsentEventWireSuite`, `ConsentPowersSuite`, `NarrowPassConsentSuite`, `NarrowPassCampaignConsentSuite`, `SecretPoliceConsentSuite`

**Interfaces:**
- Consumes (Task 1): nothing new.
- Produces:
  - `OperationWaiver(power: PowerId, granter: Option[PlayerId], covers: ConsentKey, note: PowerNote)`;
  - `OperationReason(code, detail, kind, waiver: Option[OperationWaiver] = None)`;
  - `OptionReason.Askable(power, note, asks, covers: Vector[ConsentKey] = Vector.empty)`;
  - `ConsentRequest(requester, decisionId, target, asks, covers: Vector[ConsentKey] = Vector.empty)`;
  - `HallOfMinisters.id`, `.source`, `.discardKey`, `.noDiscardConsent` and `.granter(ready)`;
  - `OperationConsent.refusals(ready, operations, restrictions): Vector[OperationReason]` and `OperationConsent.ask(ready, actor, refusals): Option[OptionReason.Askable]`;
  - `OathRules.consentAsk(state, requester, decisionId, target): Either[OathViolation, OptionReason.Askable]`.

- [ ] **Step 1: Write the failing tests**

Add to `OperationRestrictionsSuite.scala`. Add imports for `OperationConsent` (beside the other `operations` imports) and for `oathdigital.testkit.Table.p3` (extend the `Table.{p1, p2}` import):

```scala
  /** p1 rules Ancient City, where the intact Hall and the plain card stand. */
  private def hallRuledByP1: ReadyGame = Table.start.pawn(p1, at = hallSite)
    .edifice(hall, EdificeSide.Intact, at = hallSite)
    .warbandsAt(hallSite, p1, 1).denizen(plainCard, at = hallSite).ready

  private val coversPlainCard = ConsentKey(HallOfMinisters.id, p2,
    HallOfMinisters.discardKey, DecisionOptionRef.Denizen(plainCard))

  private def waiverOf(ready: ReadyGame, operation: CoreOperation)
      : Option[OperationWaiver] =
    set.active(Vector.empty, Vector.empty).flatMap(_.reason(ready, operation))
      .headOption.flatMap(_.waiver)

  test("the Hall's refusal names its ruler as the one who may consent, and " +
      "the card a grant covers"):
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    assertEquals(waiverOf(hallRuledByP1, byEnemy), Some(OperationWaiver(
      HallOfMinisters.id, Some(p1), coversPlainCard,
      HallOfMinisters.noDiscardConsent(HallOfMinisters.source))))

  test("under bandit rule the Hall's refusal names nobody to ask"):
    val banditHall = Table.start.edifice(hall, EdificeSide.Intact,
      at = hallSite).bandits(hallSite, 2).denizen(plainCard, at = hallSite)
      .ready
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    assertEquals(waiverOf(banditHall, byEnemy).map(_.granter), Some(None))

  test("a grant remembered for the card lifts the Hall's refusal; a grant " +
      "for another card does not"):
    val ready = hallRuledByP1
    val granted = ready.copy(consent = ConsentState(
      answers = Map(coversPlainCard -> ConsentAnswer.Granted),
      turn = Some(ConsentTurn.of(ready))))
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    assertEquals(refusal(granted, byEnemy), None)
    assertEquals(refusal(granted, byEnemy.copy(actingPlayer = p3)),
      Some("discard-immune"))

  test("only waivable refusals make an option askable, of other players, " +
      "with what they cover"):
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    val restrictions = set.active(Vector.empty, Vector.empty)
    val refusals = OperationConsent.refusals(hallRuledByP1, Vector(byEnemy),
      restrictions)
    assertEquals(OperationConsent.ask(hallRuledByP1, p2, refusals),
      Some(OptionReason.Askable(HallOfMinisters.id,
        HallOfMinisters.noDiscardConsent(HallOfMinisters.source),
        Vector(ConsentAsk(p1, Vector(HallOfMinisters.id))),
        Vector(coversPlainCard))))
    val locked = OperationReason("locked", "locked",
      OperationReasonKind.Impossible)
    assertEquals(OperationConsent.ask(hallRuledByP1, p2, refusals :+ locked),
      None)
    val refused = hallRuledByP1.copy(consent = ConsentState(
      answers = Map(coversPlainCard -> ConsentAnswer.Refused),
      turn = Some(ConsentTurn.of(hallRuledByP1))))
    assertEquals(OperationConsent.ask(refused, p2, refusals), None)
```

Add to `ConsentEvolutionSuite.scala`:

```scala
  test("a request that covers other keys settles those, not its option's"):
    val covered = ConsentKey(PowerId("edifice.e16.intact"), p1,
      "operation.discard", DecisionOptionRef.Denizen(DenizenId("7")))
    val after = folded(ConsentRequested(request.copy(covers = Vector(covered))),
      ConsentAnswered(p2, true))
    val now = ConsentTurn.of(after)
    assertEquals(after.consent.remembered(covered, now),
      Some(ConsentAnswer.Granted))
    assertEquals(after.consent.remembered(
      ConsentKey(pass, p1, "travel.destination", dunes), now), None)
```

Add to `ConsentEventWireSuite.scala`:

```scala
  test("a request's covers round-trip, and a request without them writes " +
      "none"):
    val covered = ConsentRequested(request.copy(covers = Vector(ConsentKey(
      PowerId("edifice.e16.intact"), p1, "operation.discard",
      DecisionOptionRef.Denizen(DenizenId("7"))))))
    assertEquals(roundTrip(covered), Right(covered))
    assertEquals(GameEventWire.encodeEvent("g", 0L, ConsentRequested(request))
      .map(_("payload").obj.contains("covers")), Right(false))
```

Add to `ConsentPowersSuite.scala`. Add imports for `HallOfMinisters` (from `oathdigital.gameplay.operations`):

```scala
  test("the Hall of Ministers is waivable, its granter the player who rules " +
      "its site"):
    val hall = CatalogNames.edifice("Hall of Ministers")
    val woods = CatalogNames.site("Deep Woods")
    val ready = Table.start.edifice(hall, EdificeSide.Intact, at = woods)
      .warbandsAt(woods, p2, 1).ready
    assertEquals(powers.source(HallOfMinisters.id),
      Some(PowerSourceRef.Card(hall)))
    assertEquals(powers.granter(ready, HallOfMinisters.id), Some(p2))
    assertEquals(powers.ruledBy(ready, p2), Vector(HallOfMinisters.id))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite oathdigital.gameplay.consent.ConsentEvolutionSuite oathdigital.serialization.ConsentEventWireSuite oathdigital.gameplay.powers.ConsentPowersSuite"`
Expected: compilation FAILS. `OperationWaiver`, `OperationConsent`, `HallOfMinisters.id` and `ConsentRequest.covers` do not exist yet.

- [ ] **Step 3: Give refusals, asks and requests what a grant covers**

In `model/OperationReason.scala`, replace the `OperationReason` case class with:

```scala
/** `waiver` is set when the refusal comes from a power on its ruler's
  * enemies that the ruler may waive (consent design, slice 3). */
final case class OperationReason(code: String, detail: String,
    kind: OperationReasonKind = OperationReasonKind.Invalid,
    waiver: Option[OperationWaiver] = None)

/** How an operation's refusal may be waived by consent (consent design,
  * slice 3). `power` names the restriction. `granter` is the player who may
  * consent, `None` when bandits, the Empire or nobody rules it. `covers` is
  * the key a grant or a refusal is remembered under. `note` is the hover
  * reason of an option its granter may be asked about. */
final case class OperationWaiver(power: PowerId, granter: Option[PlayerId],
    covers: ConsentKey, note: PowerNote)
```

In `model/Decisions.scala`, replace the `Askable` doc and case class with:

```scala
  /** Blocked only by waivable powers whose rulers may be asked (consent
    * design, ruling 8). `power` and `note` are the first such power and its
    * consent-worded reason; `asks` are the granters with their powers.
    * `covers` are the keys a grant is remembered under when they are not
    * the asked option's: an operation restriction's grant covers the
    * operation it refuses (slice 3). */
  final case class Askable(power: PowerId, note: PowerNote,
      asks: Vector[ConsentAsk], covers: Vector[ConsentKey] = Vector.empty)
      extends OptionReason
```

In `model/Consent.scala`, replace `ConsentRequest` with:

```scala
final case class ConsentRequest(requester: PlayerId, decisionId: String,
    target: DecisionOptionRef, asks: Vector[ConsentAsk],
    covers: Vector[ConsentKey] = Vector.empty):
  /** Every key the request settles: what it covers when it names any (an
    * operation restriction's grant, slice 3), else each asked power for this
    * option. */
  def keys: Vector[ConsentKey] =
    if covers.nonEmpty then covers
    else asks.flatMap(ask =>
      ask.powers.map(ConsentKey(_, requester, decisionId, target)))

  /** The asks still waiting on their granter. */
  def open: Vector[ConsentAsk] = asks.filter(_.answer.isEmpty)
```

- [ ] **Step 4: Name the Hall's waiver on its refusal**

Replace `case object HallOfMinisters` and everything after it in `HallOfMinisters.scala` with:

```scala
/** The Hall of Ministers (edifice E16, intact): an enemy of the Hall's ruler
  * acts as if the denizens and relics at the ruler's sites were locked, so it
  * cannot discard them. The actor is the discard's own acting player; a
  * Vision's discard names none, so it is the active player's.
  *
  * Its ruler may consent (consent design, slice 3). The refusal names the
  * ruler and the card, and a grant covers that card's discard by that player
  * for the turn, on whichever path discards it.
  *
  * There is one Hall of Ministers, so the restriction names its card and
  * holds whichever edifices a catalog lists.
  */
case object HallOfMinisters extends OperationRestriction:
  val id: PowerId = HallOfMinistersCard.intact.power.id
  val source: PowerSourceRef = PowerSourceRef.Card(HallOfMinistersCard.id)

  /** The decision slot of a Hall grant's key. The grant covers a card's
    * discard, which the operation check sees without its decision, so the
    * key names the discard instead. */
  val discardKey: String = "operation.discard"

  /** "Cannot discard cards from its ruler's sites without ruler's
    * consent.": the reason of an option the Hall's ruler may be asked
    * about. */
  val noDiscardConsent: NoteKey = NoteKey("no-discard-consent", Vector(
    NotePart.Text("Cannot discard cards from its ruler's sites without " +
      "ruler's consent.")))

  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    discardAt(ready, operation).flatMap { case (site, actor, card) =>
      val current = ready.game.current
      val actorSide = current.players.find(_.player == actor).flatMap { player =>
        ready.game.campaign.lineages.get(player.lineage).map { lineage =>
          if lineage.role.isImperial then SiteRuler.Empire
          else SiteRuler.Player(actor)
        }
      }
      for
        sourceSide <- actorSide
        ruler <- siteRuler(ready, site)
        if SiteRule.enemies(sourceSide, ruler)
        if hallRuler(ready).contains(ruler)
      yield OperationReason("discard-immune",
        s"cards at site ${site.value} cannot be discarded by ${actor.value}",
        OperationReasonKind.Impossible, Some(OperationWaiver(id,
          granter(ready), ConsentKey(id, actor, discardKey, card),
          noDiscardConsent(source))))
    }

  /** The player who rules the intact Hall, who may consent; `None` when
    * bandits, the Empire or nobody rules it. */
  def granter(ready: ReadyGame): Option[PlayerId] =
    hallRuler(ready).collect { case SiteRuler.Player(player) => player }

  /** The ruler of the site where the intact Hall stands. */
  private def hallRuler(ready: ReadyGame): Option[SiteRuler] =
    ready.game.current.map.sites.collectFirst {
      case (site, state) if state.denizens.exists(intactHall) => site
    }.flatMap(siteRuler(ready, _))

  private def siteRuler(ready: ReadyGame, site: SiteId): Option[SiteRuler] =
    val current = ready.game.current
    current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, current.players).toOption)

  private def intactHall(card: SiteDenizenState): Boolean = card match
    case edifice: EdificeState =>
      edifice.side == EdificeSide.Intact &&
        edifice.id == HallOfMinistersCard.id
    case _ => false

  /** The site a discard takes a card from, who discards it, and the card. */
  private def discardAt(ready: ReadyGame, operation: CoreOperation)
      : Option[(SiteId, PlayerId, DecisionOptionRef)] =
    val active = ready.game.current.turn.activePlayer
    val source = operation match
      case value: Discard.Denizen => Some((value.from.location,
        value.actingPlayer, DecisionOptionRef.Denizen(value.card)))
      case value: Discard.Vision => Some((value.from.location, active,
        DecisionOptionRef.Vision(value.card)))
      case value: Discard.RuinedEdifice => Some((value.from.location,
        value.actingPlayer, DecisionOptionRef.Edifice(value.card)))
      case value: Discard.Relic => Some((value.from.location,
        value.actingPlayer, DecisionOptionRef.Relic(value.card)))
      case _ => None
    source.collect { case (Location.Site(site), actor, card) =>
      (site, actor, card) }
```

- [ ] **Step 5: Add the operation path's ledger check and asks**

Create `src/main/scala/oathdigital/gameplay/operations/ConsentedRestriction.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** `inner`, lifted where consent was given (consent design, slice 3). A
  * refusal that names a waiver is dropped once a grant for what it covers is
  * remembered this turn. This is the operation path's one ledger check:
  * `OperationRestrictions.active` wraps every printed restriction in it, so
  * the walker's steps, its screens and card play's judgements agree.
  */
final case class ConsentedRestriction(inner: OperationRestriction)
    extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    inner.reason(ready, operation).filterNot(_.waiver.exists(waiver =>
      ready.consent.remembered(waiver.covers, ConsentTurn.of(ready))
        .contains(ConsentAnswer.Granted)))
```

Create `src/main/scala/oathdigital/gameplay/operations/OperationConsent.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** Consent on the operation path (consent design, slice 3): the refusals an
  * option's plan meets, and the ask they allow. */
object OperationConsent:
  /** Every refusal `restrictions` give `operations`, in plan order. */
  def refusals(ready: ReadyGame, operations: Vector[CoreOperation],
      restrictions: Vector[OperationRestriction]): Vector[OperationReason] =
    operations.flatMap(operation =>
      restrictions.flatMap(_.reason(ready, operation)))

  /** The ask for an option whose plan only `refusals` stop. It exists when
    * every refusal is waivable by a player other than `actor` and nothing it
    * covers was refused this turn (ruling 8). Its reason is the first
    * refusal's note; it asks each granter about all of their powers, and it
    * covers what each refusal names. */
  def ask(ready: ReadyGame, actor: PlayerId,
      refusals: Vector[OperationReason]): Option[OptionReason.Askable] =
    val waivers = refusals.flatMap(_.waiver)
    val granters = waivers.flatMap(_.granter).filter(_ != actor)
    val refused = waivers.exists(waiver => ready.consent.remembered(
      waiver.covers, ConsentTurn.of(ready)).contains(ConsentAnswer.Refused))
    if refusals.isEmpty || waivers.size < refusals.size ||
        granters.size < waivers.size || refused then None
    else waivers.headOption.map { first =>
      val owed = waivers.map(_.power).zip(granters)
      OptionReason.Askable(first.power, first.note,
        granters.distinct.map(granter => ConsentAsk(granter,
          owed.collect { case (power, `granter`) => power }.distinct)),
        waivers.map(_.covers).distinct)
    }
```

In `OperationRestrictions.scala`:

1. Replace `active` with:

```scala
  def active(powerRestrictions: Vector[OperationRestriction],
      modifiers: Vector[PowerId]): Vector[OperationRestriction] =
    printed.map(ConsentedRestriction(_)) ++ catalog
      .filter(_ => modifiers.nonEmpty).map(ActiveModifier(_, modifiers))
      .toVector ++ powerRestrictions
```

2. Append to the class doc comment: `` `active` reads each printed restriction through [[ConsentedRestriction]], so a remembered grant lifts the refusal it covers (consent design, slice 3).``

- [ ] **Step 6: Register the Hall for the grid, the log and the hover reason**

In `ConsentPowers.scala`:

1. Add `import oathdigital.gameplay.operations.HallOfMinisters`.
2. Give the class a second parameter, and fold it into the map:

```scala
final class ConsentPowers(powers: Vector[ContributingPower],
    operations: Vector[(PowerId, Waiver)] = Vector.empty):
  private val waivers: Map[PowerId, Waiver] = (for
    power <- powers
    waiver <- power.contributions.values.flatten.collectFirst {
      case Restriction(_, _, Some(waiver)) => waiver
      case OptionRestriction(_, _, Some(waiver)) => waiver }
  yield power.id -> waiver).toMap ++ operations
```

3. Update the class comment's first sentence to `every power with a contribution, of either restriction kind, that declares a Waiver, and the operation restrictions in `operations` (the Hall of Ministers, slice 3).`
4. Replace `default` with:

```scala
  def default(catalog: ExecutableCatalog): ConsentPowers =
    new ConsentPowers(WalkerPowerCatalog.default(catalog).powers,
      Vector(HallOfMinisters.id -> Waiver(HallOfMinisters.granter,
        HallOfMinisters.source,
        (_, _) => HallOfMinisters.noDiscardConsent(HallOfMinisters.source))))
```

In `RuleNotes.scala`, add `import oathdigital.gameplay.operations.HallOfMinisters`. Then extend `all`:

```scala
  /** The game rules' keys, and the Hall of Ministers' consent note: an
    * operation restriction is not a power either (consent slice 3). */
  val all: Vector[(PowerId, Vector[NoteKey])] =
    Vector(homelandDiscard -> Vector(CardPlayRules.discardFirst),
      HallOfMinisters.id -> Vector(HallOfMinisters.noDiscardConsent))
```

- [ ] **Step 7: Requests carry what the ask covers**

In `OathRulesWalker.scala`, replace `consentAsks` (its doc comment and body) with:

```scala
  /** The ask a consent request for `target` sends (consent design,
    * "Commands"). `target` must be an askable option of the parked
    * `decisionId`, which `requester` owns; the look-ahead decided that, so
    * nothing here re-derives who may consent. */
  def consentAsk(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef)
      : Either[OathViolation, OptionReason.Askable] =
    walkerResumeContext(state).flatMap:
      case (ready, _, tree, pending, powers, _, _) =>
        walkerCall(Right(ProcedureWalker.parkedDecide(ready, tree, pending,
          powers))).flatMap(decide => decide
          .filter(found => found.decisionId == decisionId &&
            found.owner == requester)
          .flatMap(found => offered(found).collectFirst {
            case DecisionOption.Unavailable(option,
                ask: OptionReason.Askable) if option.ref == target => ask
          })
          .toRight(ConsentUnavailable(
            s"${target.wireId} is not an option to ask consent for")))

  /** The granters [[consentAsk]] would ask, with their powers. */
  def consentAsks(state: OathState, requester: PlayerId, decisionId: String,
      target: DecisionOptionRef): Either[OathViolation, Vector[ConsentAsk]] =
    consentAsk(state, requester, decisionId, target).map(_.asks)
```

In `ConsentDesk.scala`, in `request`, replace the two lines from `asks <- rules.consentAsks(...)` through `request = ConsentRequest(...)` with:

```scala
    ask <- rules.consentAsk(state, requester, decisionId, target)
    request = ConsentRequest(requester, decisionId, target, ask.asks,
      ask.covers)
```

Leave `answer`'s `ask` local as it is: it is a different value, in a different method.

- [ ] **Step 8: Journal the covers**

In `ConsentEventCodec.scala`:

1. In `consentEncoder`, replace the `ConsentRequested` case with:

```scala
      case ConsentRequested(request) =>
        val encoded = ujson.Obj(
          "requester" -> request.requester.value,
          "decisionId" -> request.decisionId,
          "target" -> DecisionAnswerCodec.encodeRef(request.target),
          "asks" -> ujson.Arr.from(request.asks.map(ask => ujson.Obj(
            "granter" -> ask.granter.value,
            "powers" -> stringArray(ask.powers.map(_.value))))))
        // Written only when set, so a request from before slice 3 reads
        // back as it was written.
        if request.covers.nonEmpty then
          encoded("covers") = ujson.Arr.from(request.covers.map(key =>
            ujson.Obj("powerId" -> key.power.value,
              "requester" -> key.requester.value,
              "decisionId" -> key.decisionId,
              "target" -> DecisionAnswerCodec.encodeRef(key.target))))
        encoded
```

2. In `consentDecode`, add a local beside `player`:

```scala
    def covers: Either[WireError, Vector[ConsentKey]] =
      payload.obj.get("covers").fold[Either[WireError, Vector[ConsentKey]]](
        Right(Vector.empty))(raw => raw.arr.toVector.zipWithIndex
        .foldLeft[Either[WireError, Vector[ConsentKey]]](Right(Vector.empty)) {
          case (done, (key, index)) => for
            keys <- done
            target <- DecisionAnswerCodec.decodeRef(key("target"),
              s"$path.covers[$index].target")
          yield keys :+ ConsentKey(PowerId(key("powerId").str),
            PlayerId(key("requester").str), key("decisionId").str, target)
        })
```

3. Replace the `ConsentRequestedType` case with:

```scala
        case ConsentRequestedType =>
          for
            target <- DecisionAnswerCodec.decodeRef(payload("target"),
              s"$path.target")
            covered <- covers
          yield ConsentRequested(ConsentRequest(player("requester"),
            payload("decisionId").str, target,
            payload("asks").arr.toVector.map(ask => ConsentAsk(
              PlayerId(ask("granter").str),
              ask("powers").arr.toVector.map(power => PowerId(power.str)))),
            covered))
```

- [ ] **Step 9: Update the three-field `Askable` patterns**

Add a fourth `_` to each three-field `OptionReason.Askable` pattern:

- `WalkerDecisionProjector.scala:354`: `reason @ OptionReason.Askable(_, _, asks, _)`.
- `WalkerDecisionProjector.scala:576`: `case OptionReason.Askable(power, note, _, _) =>`.
- `NarrowPassConsentSuite.scala:73-74` and `NarrowPassCampaignConsentSuite.scala:60-61`: `OptionReason.Askable(power, note, asks, _)`.
- `NarrowPassCampaignConsentSuite.scala:139`: `OptionReason.Askable(_, _, asks, _)`.
- `SecretPoliceConsentSuite.scala` (Task 1): `OptionReason.Askable(power, note, asks, _)`.

The compiler names any pattern this list misses. `DecisionQuerySuite.scala:446` constructs an `Askable` rather than matching one, so it compiles as it is.

- [ ] **Step 10: Run the new tests and their neighbours**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite oathdigital.gameplay.consent.* oathdigital.serialization.ConsentEventWireSuite oathdigital.gameplay.powers.ConsentPowersSuite oathdigital.gameplay.OperationResolutionSuite oathdigital.gameplay.GlobalRestrictionsWalkerSuite oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.powers.whenplayed.DazzleSuite oathdigital.application.Consent*"`
Expected: PASS.

`CardSlotsSuite`'s Hall test still passes, with the written reason: `CardSlots` does not ask yet. If a suite compares a whole Hall `OperationReason`, add the waiver it now carries.

- [ ] **Step 11: Run the whole server**

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add src/main/scala/oathdigital/model/OperationReason.scala \
  src/main/scala/oathdigital/model/Decisions.scala \
  src/main/scala/oathdigital/model/Consent.scala \
  src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala \
  src/main/scala/oathdigital/gameplay/operations/ConsentedRestriction.scala \
  src/main/scala/oathdigital/gameplay/operations/OperationConsent.scala \
  src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala \
  src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala \
  src/main/scala/oathdigital/gameplay/actions/RuleNotes.scala \
  src/main/scala/oathdigital/gameplay/OathRulesWalker.scala \
  src/main/scala/oathdigital/application/ConsentDesk.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/serialization/ConsentEventCodec.scala \
  src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala \
  src/test/scala/oathdigital/gameplay/consent/ConsentEvolutionSuite.scala \
  src/test/scala/oathdigital/serialization/ConsentEventWireSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/ConsentPowersSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassConsentSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/travel/NarrowPassCampaignConsentSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/cardplay/SecretPoliceConsentSuite.scala
git commit -m "feat(consent): name the Hall of Ministers' ruler on its refusal, and lift it on a grant"
```

---

### Task 3: The Hall is askable wherever an enemy chooses the discard

**Files:**
- Create: `src/test/scala/oathdigital/gameplay/operations/HallOfMinistersConsentSuite.scala`
- Create: `src/test/scala/oathdigital/application/HallOfMinistersConsentServiceSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala:60-136`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala:56-62`
- Test: `CardSlotsSuite`

**Interfaces:**
- Consumes (Task 2): `OperationConsent.refusals` and `.ask`; `HallOfMinisters.id`, `.source`, `.discardKey` and `.noDiscardConsent`; `OptionReason.Askable(..., covers)`; `ConsentRequest(..., covers)`.
- Produces:
  - `CardSlots.judge(catalog, ready, actor, option, planned): Option[DecisionOption]`;
  - `SiteCards.judged(ready, actor, site, card, suit): Option[DecisionOption]`;

- [ ] **Step 1: Write the failing tests**

In `CardSlotsSuite.scala`, replace the test "the Hall of Ministers protects its ruler's site from an enemy, and says so" (the whole test) with the two tests below. Add imports for `HallOfMinisters` (from `oathdigital.gameplay.operations`).

```scala
  /** p1 stands at Deep Woods with Threatening Roar, a Beast card, so Deep
    * Woods' Homeland permits a discard; the intact Hall and two plain cards
    * stand there, with `ruled` deciding who rules it. */
  private def guarded(ruled: Table => Table): ReadyGame =
    val roar = CatalogNames.denizen("Threatening Roar")
    ruled(Vector("Fae Merchant", "Second Chance").map(CatalogNames.denizen(_))
      .foldLeft(Table.start.pawn(p1, at = woods).hand(p1, roar))(
        _.denizen(_, at = woods))
      .edifice(CatalogNames.edifice("Hall of Ministers"), EdificeSide.Intact,
        at = woods)).ready

  private def askingP2(replaced: DenizenId): OptionReason =
    OptionReason.Askable(HallOfMinisters.id,
      HallOfMinisters.noDiscardConsent(HallOfMinisters.source),
      Vector(ConsentAsk(p2, Vector(HallOfMinisters.id))),
      Vector(ConsentKey(HallOfMinisters.id, p1, HallOfMinisters.discardKey,
        DecisionOptionRef.Denizen(replaced))))

  test("the Hall of Ministers makes its ruler's site cards askable of that " +
      "ruler, protects them from everyone under bandits, and binds no ruler " +
      "of its own"):
    val roar = CatalogNames.denizen("Threatening Roar")
    val fillers = Vector("Fae Merchant", "Second Chance")
      .map(CatalogNames.denizen(_))
    val hall = CatalogNames.edifice("Hall of Ministers")
    def slotsAt(ruled: Table => Table) =
      atSite(slotsOf(guarded(ruled), roar))
    assertEquals(slotsAt(_.warbandsAt(woods, p2, 1)), Vector(
      DecisionOption.Unavailable(replacing(siteSlot(0, woods), fillers(0)),
        askingP2(fillers(0))),
      DecisionOption.Unavailable(replacing(siteSlot(1, woods), fillers(1)),
        askingP2(fillers(1))),
      locked(replacing(siteSlot(2, woods), hall))))
    val hallProtects = "Protected by the Hall of Ministers"
    assertEquals(slotsAt(_.bandits(woods, 2)), Vector(
      refused(replacing(siteSlot(0, woods), fillers(0)), hallProtects),
      refused(replacing(siteSlot(1, woods), fillers(1)), hallProtects),
      locked(replacing(siteSlot(2, woods), hall))))
    assertEquals(slotsAt(_.warbandsAt(woods, p1, 1)), Vector(
      replacing(siteSlot(0, woods), fillers(0)),
      replacing(siteSlot(1, woods), fillers(1)),
      locked(replacing(siteSlot(2, woods), hall))))

  test("a grant remembered for a site card opens its Replace slot; a " +
      "refusal leaves the Hall's reason"):
    val roar = CatalogNames.denizen("Threatening Roar")
    val fae = CatalogNames.denizen("Fae Merchant")
    val ready = guarded(_.warbandsAt(woods, p2, 1))
    def remembering(answer: ConsentAnswer) = ready.copy(consent =
      ConsentState(answers = Map(ConsentKey(HallOfMinisters.id, p1,
        HallOfMinisters.discardKey, DecisionOptionRef.Denizen(fae)) ->
        answer), turn = Some(ConsentTurn.of(ready))))
    assertEquals(atSite(slotsOf(remembering(ConsentAnswer.Granted), roar))
      .head, replacing(siteSlot(0, woods), fae))
    assertEquals(atSite(slotsOf(remembering(ConsentAnswer.Refused), roar))
      .head, refused(replacing(siteSlot(0, woods), fae),
        "Protected by the Hall of Ministers"))
```

If `guarded`'s board is refused by `.ready` (the bandits case on a site that already has bandits), use `.unchecked` there, as other suites do.

Create `src/test/scala/oathdigital/gameplay/operations/HallOfMinistersConsentSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.powers.action.RovingTerror
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.ConsentAskProjection
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** The Hall of Ministers on a power's site-card question (consent design,
  * slice 3): Roving Terror, which discards a card at another site, asks the
  * Hall's ruler before it takes a card the Hall protects. */
class HallOfMinistersConsentSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val terror = CatalogNames.denizen("Roving Terror")
  private val source = DecisionOptionRef.Denizen(terror)
  private val power = RovingTerror.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val errandBoyRef = DecisionOptionRef.Denizen(errandBoy)
  private val covers = ConsentKey(HallOfMinisters.id, p1,
    HallOfMinisters.discardKey, errandBoyRef)

  /** p1's Act at Ancient City beside Roving Terror. Errand Boy stands at
    * Deep Woods with the intact Hall, which `ruled` gives its ruler, and,
    * when `beside`, Wrestlers stands at Broken Peaks, which the Hall does
    * not protect. */
  private def guarded(ruled: Table => Table = _.warbandsAt(woods, p2, 1),
      beside: Boolean = true): ReadyGame =
    val table = ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(terror, at = home).denizen(errandBoy, at = woods)
      .edifice(hall, EdificeSide.Intact, at = woods))
    (if beside then table.denizen(wrestlers, at = peaks) else table).ready

  private def remembering(ready: ReadyGame, answer: ConsentAnswer)
      : ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(covers -> answer),
      turn = Some(ConsentTurn.of(ready))))

  private def errandBoyRow(t: OathTransition)(using munit.Location) =
    present(queryOf(t, p1).toVector.flatMap(_.offeredOptions)
      .find(_.id == errandBoy.value), "the Errand Boy row")

  test("a card the Hall protects for a player is askable of that player, " +
      "beside a card it does not protect"):
    val t = accepted(use(guarded(), power, source))
    assert(awaits(t, RovingTerror.decisionId),
      parked.parkedDecision(t.state).toString)
    val row = errandBoyRow(t)
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Hall of Ministers: Cannot discard cards " +
      "from its ruler's sites without ruler's consent."))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Hall of Ministers"))))

  test("the ask names its granter and covers the card"):
    val t = accepted(use(guarded(), power, source))
    assertEquals(rules.consentAsk(t.state, p1, RovingTerror.decisionId,
      errandBoyRef), Right(OptionReason.Askable(HallOfMinisters.id,
        HallOfMinisters.noDiscardConsent(HallOfMinisters.source),
        Vector(ConsentAsk(p2, Vector(HallOfMinisters.id))), Vector(covers))))

  test("under bandits a protected card shows the Hall's reason"):
    val row = errandBoyRow(accepted(use(guarded(_.bandits(woods, 2)), power,
      source)))
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some("Protected by the Hall of Ministers"))

  test("when the Hall protects every candidate the question is not asked: " +
      "a refusal would leave nothing to choose"):
    assert(backToActing(accepted(use(guarded(beside = false), power,
      source))))

  test("the Hall's own ruler takes the card freely"):
    val t = accepted(use(guarded(_.warbandsAt(woods, p1, 1)), power, source))
    assertEquals(errandBoyRow(t).state, "available")

  test("a grant remembered for the card lets Roving Terror take it"):
    val ready = remembering(guarded(), ConsentAnswer.Granted)
    val t = accepted(use(ready, power, source))
    assertEquals(errandBoyRow(t).state, "available")
    val done = accepted(answer(t, p1, RovingTerror.decisionId,
      pick(errandBoyRef)))
    assert(Look(after(done)).cardsAt(woods).contains(terror))
    assert(Look(after(done)).discardAfter(woods).contains(errandBoy))

  test("a refusal remembered this turn leaves the Hall's reason"):
    val ready = remembering(guarded(), ConsentAnswer.Refused)
    val row = errandBoyRow(accepted(use(ready, power, source)))
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some("Protected by the Hall of Ministers"))
```

Notes for this suite:

- If an available row's `state` is not `"available"`, use the value `RovingTerrorSuite`'s projected rows carry.
- If `Look.cardsAt` returns a type without `contains`, compare as `RovingTerrorSuite` does (`Vector[CardId](terror)`).

Create `src/test/scala/oathdigital/application/HallOfMinistersConsentServiceSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.operations.HallOfMinisters
import oathdigital.gameplay.powers.action.RovingTerror
import oathdigital.model._
import oathdigital.model.OathEvent.{CommandAccepted, ConsentAnswered,
  ConsentRequested}
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Asking the Hall of Ministers' ruler through the service (consent design,
  * slice 3): a grant covers the card, and Roving Terror takes it as p1's
  * own undoable step. */
class HallOfMinistersConsentServiceSuite extends munit.FunSuite:
  private val gameId = "hall-consent"
  private val terror = CatalogNames.denizen("Roving Terror")
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val errandBoyRef = DecisionOptionRef.Denizen(errandBoy)
  /** Errand Boy at the Hall's site, which p2 rules; Wrestlers at Broken
    * Peaks, which the Hall does not protect, so the question is asked. */
  private val board = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
    .denizen(terror, at = home).denizen(errandBoy, at = woods)
    .edifice(CatalogNames.edifice("Hall of Ministers"), EdificeSide.Intact,
      at = woods).warbandsAt(woods, p2, 1)
    .denizen(CatalogNames.denizen("Wrestlers"),
      at = CatalogNames.site("Broken Peaks"))
  private val useTerror = GameCommand.StartWalker(
    ActionRef.UsePower(RovingTerror.id),
    StartPayload(p1, startArgs = Vector(DecisionOptionRef.Denizen(terror))))
  private val request = ConsentRequest(p1, RovingTerror.decisionId,
    errandBoyRef, Vector(ConsentAsk(p2, Vector(HallOfMinisters.id))),
    Vector(ConsentKey(HallOfMinisters.id, p1, HallOfMinisters.discardKey,
      errandBoyRef)))

  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val used = accepted(service.handle(gameId, 0L, useTerror))
    accepted(service.handle(gameId, used.nextSequence,
      GameCommand.RequestConsent(p1, RovingTerror.decisionId, errandBoyRef)))

  test("Yes lets Roving Terror take the card, as p1's own step, and the " +
      "journal reads back the same"):
    val (service, repository) = board.service()
    val asked = asking(service)
    assertEquals(asked.events, Vector(ConsentRequested(request)))
    val yes = accepted(service.handle(gameId, asked.nextSequence,
      GameCommand.AnswerConsent(p2, ConsentChoice.Yes)))
    assertEquals(yes.events.take(2), Vector(ConsentAnswered(p2, true),
      CommandAccepted(p1)))
    assert(Look(Outcomes.ready(yes.state)).discardAfter(woods)
      .contains(errandBoy))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(game =>
      (game.state, game.undo))), Right(Some((yes.state, yes.undo))))

  test("No leaves the card where it is, and p1 still chooses"):
    val (service, _) = board.service()
    val no = accepted(service.handle(gameId, asking(service).nextSequence,
      GameCommand.AnswerConsent(p2, ConsentChoice.No)))
    assertEquals(no.events, Vector(ConsentAnswered(p2, false)))
    assert(Outcomes.ready(no.state).game.current.walkerPending.nonEmpty)
    assert(Look(Outcomes.ready(no.state)).cardsAt(woods).contains(errandBoy))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.operations.HallOfMinistersConsentSuite oathdigital.application.HallOfMinistersConsentServiceSuite"`
Expected: FAIL.
- `CardSlotsSuite`'s new tests fail: the slots still carry the written reason.
- Most Roving Terror tests fail: Errand Boy is still hidden, so its row is missing and no request can name it. The test where the Hall protects every candidate and the Hall's own ruler's test already pass.

- [ ] **Step 3: Judge card play's options for asks**

In `CardSlots.scala`:

1. Add `OperationConsent` to the `oathdigital.gameplay.operations` import.
2. Replace the private `judged` (its doc and body) with a public `judge`:

```scala
  /** `option` as the restrictions in force judge the plan it stands for,
    * played by `actor`. It is kept as it is when nothing refuses. When only
    * waivable refusals stop it, it is kept askable of their granters
    * (consent design, slice 3). Otherwise it is shown unavailable with a
    * protection's reason, or left out. */
  def judge(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      option: DecisionOption,
      planned: Either[OathViolation, Vector[CoreOperation]])
      : Option[DecisionOption] =
    if !option.available then Some(option)
    else planned.toOption.flatMap { operations =>
      val restrictions = OperationRestrictions.forCatalog(catalog)
        .active(Vector.empty, ready.game.current.walkerModifiers)
      val refusals = OperationConsent.refusals(ready, operations,
        restrictions)
      if refusals.isEmpty then Some(option)
      else OperationConsent.ask(ready, actor, refusals)
        .map(DecisionOption.Unavailable(option, _))
        .orElse(refusals.headOption.flatMap(refusal => reasons
          .get(refusal.code).map(reason =>
            DecisionOption.Unavailable(option, OptionReason.Written(reason)))))
    }
```

3. In `slots`, replace `judged(catalog, ready, option, ...)` with `judge(catalog, ready, actor, option, ...)`. In `moreDiscards`, replace `judged(catalog, ready, optionOf(other), ...)` with `judge(catalog, ready, actor, optionOf(other), ...)`.
4. Update the class doc's last paragraph, from `Each option is planned` to `apply.`:

```
  * Each option is planned with `CardPlay.operations`, and the printed
  * restrictions in force judge the plan (`judge`): an option only a waivable
  * protection refuses is askable of its ruler, an option another protection
  * refuses is shown unavailable with its reason, and an option the planner
  * or another restriction refuses is left out. Powers' own restrictions (Vow
  * of Obedience) are the walker look-ahead's to apply.
```

- [ ] **Step 4: Judge the site-card questions for asks**

In `SiteCards.scala`:

1. Add `import oathdigital.gameplay.actions.cardplay.CardSlots`.
2. After `cardOf`, add:

```scala
  /** `card`'s option as the printed restrictions judge `actor`'s discard of
    * it from `site` (consent design, slice 3). It is kept as it is when
    * nothing refuses, askable when only waivable refusals stop it, shown
    * unavailable with a protection's reason, or left out. */
  def judged(ready: ReadyGame, actor: PlayerId, site: SiteId,
      card: SiteDenizenState, suit: Suit): Option[DecisionOption] =
    CardSlots.judge(catalog, ready, actor, option(card),
      discard(ready, site, card, suit, actor, required = true).map(Vector(_)))
```

3. Replace `ask` (its doc and body) with:

```scala
  /** The question over the cards at the player's site, each judged for the
    * player's discard. It is asked only when some card may be chosen: a
    * choose-one has no empty answer, so a question with only askable cards
    * would wait with nothing to choose after a refusal (consent slice 3). */
  def ask(ready: ReadyGame, player: PlayerId, decisionId: String,
      heading: String): Vector[Operation] =
    val found = PowerAccess.pawnSite(ready, player).toVector.flatMap(site =>
      at(ready, site).flatMap((card, suit) =>
        judged(ready, player, site, card, suit)))
    if !found.exists(_.available) then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(found,
      heading = Some(heading)), passWhenEmpty = true))
```

In `RovingTerror.scala`, replace `ask` with:

```scala
  /** The question over the candidates, each judged for the actor's
    * discard; asked only when some card may be chosen (`SiteCards.ask`). */
  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    val found = candidates(ready).flatMap(c =>
      cards.judged(ready, actor, c.site, c.card, c.suit))
    if !found.exists(_.available) then Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(found,
      heading = Some("Roving Terror: discard a card at another site and " +
        "move there")), passWhenEmpty = true))
```

In `RovingTerror`'s class doc, replace `so a card whose discard a restriction refuses (the Hall of Ministers) is hidden, and the question passes when nothing is left.` with `so a card the Hall of Ministers protects is shown with its reason, or askable of the Hall's ruler (consent slice 3), and the question passes when no card may be chosen.`

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.operations.HallOfMinistersConsentSuite oathdigital.application.HallOfMinistersConsentServiceSuite oathdigital.gameplay.powers.action.*"`
Expected: PASS. The other action-power suites (Roving Terror, Taming Charm, Great Feast, Dark Enforcer) are unchanged: none of them stands a Hall on its board.

- [ ] **Step 6: Run the whole server**

Run: `./sbtw test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SiteCards.scala \
  src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala \
  src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala \
  src/test/scala/oathdigital/gameplay/operations/HallOfMinistersConsentSuite.scala \
  src/test/scala/oathdigital/application/HallOfMinistersConsentServiceSuite.scala
git commit -m "feat(consent): ask the Hall of Ministers' ruler from card play and the site-card questions"
```

---

### Task 4: The board's Decide buttons ask, and a site card opens its ruler's grid

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala:21-22`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionCodec.scala:42-60`
- Modify: `src/main/scala/oathdigital/application/ConsentProjector.scala:50-57`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala:120-130`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala:134-144`
- Modify: `frontend/src/main/scala/oathdigital/frontend/SitePowerLinks.scala:20-26`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala:60-63`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:55-61`
- Test: `shared/.../ProjectionProtocolSuite.scala`, `src/test/.../application/ConsentProjectionSuite.scala`, `frontend/.../BoardDecisionPanelSuite.scala`, `frontend/.../SitePowerGridSuite.scala`, `frontend/.../PlayerBoardSuite.scala`

**Interfaces:**
- Consumes (Tasks 1 and 2): Secret Police and the Hall in `ConsentPowers.default`, with sources `PowerSourceRef.Card(...)`.
- Produces:
  - `ConsentPreferencesProjection(powerId, power, rows, cardId: Option[String] = None)`;
  - `CardInspection.Request.RuledCard(card, grid, origin)` and `CardInspection.openRuled`;
  - `SitePowerLinks.cardGrid(card): Option[ConsentGrid]`;
  - `BoardOptions.card(face, details, keys, open = None)`.

- [ ] **Step 1: Write the failing tests**

In `ProjectionProtocolSuite.scala`, in the test "the consent parts of a projection round-trip", add a second preferences entry to the `consentPreferences` vector:

```scala
        ConsentPreferencesProjection("denizen.secret-police",
          "Secret Police", Vector(ConsentPreferenceRowProjection("p1",
            "never")), Some("113"))
```

Add to `ConsentProjectionSuite.scala`. Add imports for `CatalogNames` and `Table` (from `oathdigital.testkit`) and `OathState`:

```scala
  test("a card's waivable power carries the card's id; a site's carries " +
      "none"):
    val police = CatalogNames.denizen("Secret Police")
    val home = CatalogNames.site("Ancient City")
    val ready = Table.start.denizen(police, at = home)
      .warbandsAt(home, p2, 2).ready
    assertEquals(projector.project(gameId, LoadedGame(OathState.Ready(ready),
      0L), p2).consentPreferences.map(entry => (entry.powerId, entry.cardId)),
      Vector(("denizen.secret-police", Some(police.value))))
```

If `LoadedGame` takes other parameters, build it the way `PhasePowerHarness.queryOf` does.

Add to `BoardDecisionPanelSuite.scala`:

```scala
  test("an askable Decide button asks before it sends"):
    val ui = new RecordingControls()
    var opened = Vector.empty[CardInspection.Request]
    CardInspection.onOpen(request => opened :+= request)
    try
      val play = DecisionOptionState("button", "play", "Play",
        state = "askable", reason = Some("Secret Police: Cannot play a " +
          "Vision faceup without ruler's consent."),
        consent = Some(ConsentAskState(Vector("red"), Vector("Secret Police"))))
      val placement = DecisionQueryState.ChooseOne(Vector(option("hills"),
        play))
      val panel = pane(ui, placement, decision.copy(decisionId =
        "cardplay.place.vision.vision:conspiracy"))
      val decide = as[dom.html.Button](one(panel, ".board-decision-decide"))
      assert(decide.classList.contains("board-option-askable"))
      assert(!decide.disabled)
      decide.click()
      assertEquals(ui.submitted, Vector.empty)
      opened match
        case Vector(confirm: CardInspection.Request.Confirm) =>
          confirm.confirm()
        case other => fail(s"expected one confirmation, got $other")
      assertEquals(ui.submitted, Vector(Intent.RequestConsent(
        "cardplay.place.vision.vision:conspiracy",
        WalkerStartArgWire("button", "play"))))
    finally CardInspection.clear()
```

Add `WalkerStartArgWire` to the `oathdigital.protocol` import.

Add to `SitePowerGridSuite.scala`:

```scala
  private val policeCard = CardDetails("113", "denizen", "Secret Police")
  private val policed = table.copy(world = Vector(GameRegion("provinces",
    Vector(narrowPass.copy(denizens = Vector(GameSiteCard("113",
      "Secret Police", Some(policeCard))))))))

  private def face(world: dom.Element): dom.html.Element =
    world.querySelector("[data-denizen-id='113']")
      .asInstanceOf[dom.html.Element]

  test("a site card whose power the viewer rules opens with their grid"):
    var sent = Vector.empty[Intent]
    val ruled = policed.copy(consentPreferences = Vector(
      ConsentPreferencesState("denizen.secret-police", "Secret Police",
        Vector(ConsentPreferenceRowState("blue", "case-by-case")),
        Some("113"))))
    face(WorldBoardRenderer.world(ruled, BoardOptions.none,
      Some(command => sent :+= command))).click()
    opened match
      case Vector(CardInspection.Request.RuledCard(card, grid, _)) =>
        assertEquals(card.cardId, "113")
        assertEquals(grid.rows, Vector(ConsentGridRow("blue", "Blue",
          "case-by-case")))
        grid.choose.foreach(_("blue", "never"))
      case other => fail(s"expected one ruled card, got $other")
    assertEquals(sent, Vector(Intent.SetConsentPreference(
      "denizen.secret-police", "blue", "never")))

  test("a site card whose power the viewer does not rule opens as a card"):
    face(WorldBoardRenderer.world(policed, BoardOptions.none)).click()
    assert(opened match
      case Vector(_: CardInspection.Request.Card) => true
      case _ => false)
```

Notes for these tests:

- If `quietSite` gives Narrow Pass no room for a denizen, also set `denizenCapacity = 1` in the `copy`.
- If `CardDetails` needs more arguments, follow `PlayerBoardSuite`'s vision card.

In `PlayerBoardSuite.scala`, `opensOnClick`, add a case to the exhaustive match:

```scala
      case CardInspection.Request.RuledCard(card, _, _) =>
        opened = opened :+ card.name
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardDecisionPanelSuite oathdigital.frontend.SitePowerGridSuite" "testOnly oathdigital.application.ConsentProjectionSuite"`
Expected: compilation FAILS. `ConsentPreferencesProjection` takes no `cardId`, and `CardInspection.Request.RuledCard` does not exist.

- [ ] **Step 3: Project and carry the card's id**

In `ConsentProjectionDtos.scala`, replace `ConsentPreferencesProjection` with:

```scala
/** One waivable power the viewer rules, with their setting for each other
  * player. `cardId` names the card that prints it, when a card does, so the
  * card can open the grid (consent slice 3). */
final case class ConsentPreferencesProjection(powerId: String, power: String,
    rows: Vector[ConsentPreferenceRowProjection],
    cardId: Option[String] = None)
```

If the case class already has a doc comment, keep its wording and append the `cardId` sentence.

In `ConsentProjectionCodec.scala`:

1. Replace `encodePreferences` with:

```scala
  def encodePreferences(value: ConsentPreferencesProjection): ujson.Value =
    ujson.Obj("powerId" -> value.powerId, "power" -> value.power,
      "rows" -> encoded(value.rows)(row => ujson.Obj(
        "playerId" -> row.playerId, "setting" -> row.setting)),
      "cardId" -> stringOption(value.cardId))
```

2. In `decodePreferences`:
   - change the `exact` set to `Set("powerId", "power", "rows", "cardId")`;
   - add `cardId <- optionalAbsent(row, "cardId", path)(string)` after `rows <- ...`;
   - yield `ConsentPreferencesProjection(powerId, power, rows, cardId)`.

In `ConsentProjector.scala`, `preferences`, pass the card's id as the fourth argument:

```scala
    powers.ruledBy(ready, viewer).map(power => ConsentPreferencesProjection(
      power.value, names.power(power),
      ready.game.current.players.map(_.player).filter(_ != viewer).map(
        other => ConsentPreferenceRowProjection(other.value,
          ready.consent.preference(viewer, power, other)
            .fold(ConsentSetting.CaseByCase.key)(_.key))),
      powers.source(power).collect { case PowerSourceRef.Card(card) =>
        card.value }))
```

- [ ] **Step 4: Ask from an askable Decide button**

In `BoardDecisionPanel.scala`, replace the `decides.foreach` block with:

```scala
      decides.foreach { option =>
        val decide = button(option.label, "board-decision-decide")
        decide.disabled = !canControl
        // An askable button asks its granters first (consent design,
        // "Frontend"), as the Conspiracy's Play does under Secret Police.
        val askable = option.state == "askable"
        if askable then decide.classList.add("board-option-askable")
        decide.onclick = _ =>
          if askable then ask(option, decide)
          else controls.submitCommand(
            WalkerPanelSupport.resolveChooseOneCommand(decision, option))
        panel.appendChild(decide)
        WalkerPanelSupport.withholds(decide, option, panel)
      }
```

`ask` is the `ConsentAsking.asker` value defined above the loose options. Move its `val` above the `loose.foreach` line only if it is not already above the `decides` block.

- [ ] **Step 5: Open a ruled site card with its grid**

In `CardInspection.scala`:

1. Add a request case beside `Power`:

```scala
    /** A card that prints a power the viewer rules, with their consent
      * preferences for it (consent slice 3). */
    final case class RuledCard(card: CardDetails, grid: ConsentGrid,
        origin: dom.html.Element) extends Request
```

2. Add an opener beside `openPower`:

```scala
  def openRuled(card: CardDetails, grid: ConsentGrid,
      origin: dom.html.Element): Unit =
    handler.foreach(_(Request.RuledCard(card, grid, origin)))
```

In `CardInspectionOverlay.scala`, after `showPower`, add:

```scala
  /** A card, and below it the viewer's consent preferences for the power it
    * prints that they rule (consent slice 3). */
  def showRuled(card: CardDetails, grid: ConsentGrid,
      origin: dom.html.Element): Unit =
    show(card, origin)
    body.appendChild(ConsentGridView.render(grid))
```

In `GameTableShell.scala`, add to the `CardInspection.onOpen` match:

```scala
    case CardInspection.Request.RuledCard(card, grid, origin) =>
      inspector.showRuled(card, grid, origin)
```

In `SitePowerLinks.scala`, replace `grid` with:

```scala
  def grid(power: SitePower): Option[ConsentGrid] =
    value.flatMap(_.consentPreferences.find(_.powerId == power.id))
      .flatMap(gridOf)

  /** The viewer's grid for the power `card` prints, when they rule it
    * (consent slice 3). */
  def cardGrid(card: CardDetails): Option[ConsentGrid] =
    value.flatMap(_.consentPreferences.find(_.cardId.contains(card.cardId)))
      .flatMap(gridOf)

  private def gridOf(preferences: ConsentPreferencesState)
      : Option[ConsentGrid] =
    value.map(projection => ConsentGrid(preferences.rows.map(row =>
        ConsentGridRow(row.playerId, ConsentAsking.name(projection,
          row.playerId), row.setting)),
      submit.map(send => (requester, setting) =>
        send(GameCommand.SetConsentPreference(preferences.powerId, requester,
          setting)))))
```

Update the class comment: `What a site power's label, or a site card, opens (consent design, "Frontend"; slice 3)`.

In `BoardOptions.scala`, replace `card` with:

```scala
  /** A card face, marked when it is an option. A card the viewer may not
    * identify has nothing to read and gets no inspect button. `open`
    * replaces the plain inspector, for a card that opens with a grid. */
  def card(face: dom.html.Button, details: CardDetails,
      keys: Vector[String],
      open: Option[dom.html.Element => Unit] = None): dom.Element =
    control(face, keys, Option.unless(details.hidden)(
      details.name -> open.getOrElse(CardInspection.open(details, _))))
```

In `ServerUiSupport.scala`, `siteDetails`, replace the `case Some(details) =>` branch with:

```scala
        case Some(details) =>
          val face = CardFace.render(details)
          face.setAttribute("data-denizen-id", denizen.denizenId)
          // A card printing a power the viewer rules opens with their
          // consent grid (consent slice 3).
          val ruled = links.cardGrid(details).map(grid =>
            (origin: dom.html.Element) =>
              CardInspection.openRuled(details, grid, origin))
          ruled.foreach(open => face.onclick = _ => open(face))
          cards.appendChild(options.card(face, details,
            Vector(BoardOptions.cardKey(details), siteSlot(site, at)), ruled))
```

If `CardFace.render` returns a type without `onclick`, cast as the face's own click handler does in `CardFace.scala:120`.

- [ ] **Step 6: Run the suites**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardDecisionPanelSuite oathdigital.frontend.SitePowerGridSuite oathdigital.frontend.PlayerBoardSuite oathdigital.frontend.Consent*" "testOnly oathdigital.application.ConsentProjectionSuite"`
Expected: PASS.

Run the shared suite with the command in Global Constraints, for `oathdigital.protocol.ProjectionProtocolSuite`.
Expected: PASS.

- [ ] **Step 7: Run the whole server and frontend**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ConsentProjectionCodec.scala \
  shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala \
  src/main/scala/oathdigital/application/ConsentProjector.scala \
  src/test/scala/oathdigital/application/ConsentProjectionSuite.scala \
  frontend/src/main/scala/oathdigital/frontend/BoardDecisionPanel.scala \
  frontend/src/main/scala/oathdigital/frontend/CardInspection.scala \
  frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala \
  frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala \
  frontend/src/main/scala/oathdigital/frontend/SitePowerLinks.scala \
  frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala \
  frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/SitePowerGridSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala
git commit -m "feat(consent): ask from a board Decide button, and open a ruled site card with its grid"
```

---

### Task 5: The docs and the gate

**Files:**
- Modify: `docs/superpowers/specs/2026-10-08-consent-system-design.md`
- Modify: `CONTEXT.md:80-86`
- Modify: `docs/ROADMAP.md:75-78` and `:202-203`
- Modify: `docs/rules/implementation-traceability.md:133`

**Interfaces:**
- Consumes: everything above.
- Produces: nothing new.

- [ ] **Step 1: Record slice 3 in the spec**

In `docs/superpowers/specs/2026-10-08-consent-system-design.md`, make these replacements. Check that each replaced string is found exactly once before replacing it.

1. In the status line, replace

   `Slices 1 and 2 are implemented ([slice 1 plan](../plans/2026-10-09-consent-slice1.md), [slice 2 plan](../plans/2026-10-09-consent-slice2.md)), and their plans' decisions are folded in below.`

   with

   `Slices 1 to 3 are implemented ([slice 1 plan](../plans/2026-10-09-consent-slice1.md), [slice 2 plan](../plans/2026-10-09-consent-slice2.md), [slice 3 plan](../plans/2026-10-09-consent-slice3.md)), and their plans' decisions are folded in below.`

2. In ruling 1, replace `"cannot" powers (the Pass, Secret Police, Hall of Ministers, Forgotten
   Vault),` with `"cannot" powers (the Pass, Secret Police, Hall of Ministers),`.

3. In ruling 2, replace

   `Tamer) are never waivable. Forgotten Vault is waivable only outside a
   campaign.`

   with

   `Tamer) are never waivable. Forgotten Vault is never waivable either:
   "target" means a Campaign's target selection only (catalog batch 3
   rulings), so all of its effect is inside a Campaign.`

4. Under "State", after the `answers` bullet's sentence `The decision id keeps Deep Woods as a travel destination apart from Deep Woods in a toll step.`, append: `An operation restriction's grant is keyed by what it refuses instead: the Hall of Ministers' key is the requester, `operation.discard` and the card, so it covers that card's discard on any path for the turn.`

5. Under "State", in the `pending` bullet, replace `requester, decision id, target and `asks: Vector[ConsentAsk]`.` with `requester, decision id, target, `asks: Vector[ConsentAsk]` and `covers`, the keys it settles when they are not the asked option's.`

6. After the "Toll and penalty steps" paragraph, add a paragraph:

   ```
   **Operation restrictions.** The Hall of Ministers is an operation
   restriction, which refuses a discard wherever it runs. Its refusal names
   an `OperationWaiver`: the power, the granter and the key a grant covers.
   `ConsentedRestriction` wraps each printed restriction in
   `OperationRestrictions.active` and drops a refusal whose grant is
   remembered, the operation path's one ledger check. Card play's Replace
   site slots and their follow-up, and the site-card questions of Roving
   Terror, Taming Charm and Great Feast, judge each option's plan:
   `OperationConsent.ask` makes it askable when only waivable refusals stop
   it, with no search. A site-card question is asked only when some card
   may be chosen. Discards with no option behind them (Dark Enforcer,
   region discards, Mounted Library, setup, a Campaign plan) stay enforced
   without consent.
   ```

7. Under "Reasons", after the `no-target-consent` sentence, add: `Secret Police gets `no-faceup-consent`: "Cannot play a Vision faceup without ruler's consent." The Hall of Ministers gets `no-discard-consent`: "Cannot discard cards from its ruler's sites without ruler's consent.", registered as a game-rule note.`

8. Under "Projection", in the `consentPreferences` bullet, after `with a row per other player.`, add: `A power a card prints carries the card's id, so the card opens the grid.`

9. Under "Frontend", in the bullet that begins `- Site power labels become clickable`, append: `A site card that prints a power the viewer rules opens with the grid too.`

10. In "Slices", replace

    `3. **Hard-block cards.** Secret Police, Hall of Ministers, and Forgotten
       Vault outside campaigns.`

    with

    `3. **Hard-block cards.** Secret Police on the tree path, and the Hall of
       Ministers through its operation refusals. Forgotten Vault is never
       waivable (ruling 2).`

11. Under "Deferred", in the tree-path item, replace `The Pass's Travel
  destination is the only tree-path decision so far; slice 4 must tie
  them.` with `The Pass's Travel destination and Secret Police's placement are each
  the only such decision in their walk; slice 4 must tie them.`

12. In the "Deferred" item that begins `- Askable options on the client's shared board homes`, replace `shared board homes, variant toggles and
  board-decision buttons, and on a choose-many drawn in the panel.` with `shared board homes and variant toggles, and on a choose-many drawn in the
  panel.`

13. Add a "Deferred" item:

    ```
    - The Hall of Ministers on discards with no option to ask from: Dark
      Enforcer and the region discards (Dazzle, Threatening Roar, Riots)
      skip a protected card, and Mounted Library's self-discard has no
      consent point. Slice 4's pay-or-ask step could reach them.
    - Asking from a site-card question whose every candidate the Hall
      protects. A choose-one has no empty answer, so after a refusal it
      would wait with nothing to choose; the question is not asked.
    ```

- [ ] **Step 2: Update the vocabulary, the roadmap and the traceability**

In `CONTEXT.md`, replace the **Askable option** definition's text (the five lines after `**Askable option**:`) with:

```
An option of a parked choose-one, or of a choose-many whose options a power
forbids one by one, blocked only by waivable powers whose granters are other
players, with nothing refused for it this turn, which a grant would open. An
option whose plan only a waivable operation restriction refuses (the Hall of
Ministers) is askable too. Choosing it asks the granters first. A grant
answers a choose-one with it, and only opens it in a choose-many.
```

In `docs/ROADMAP.md`:

- Replace

  ```
  - [ ] 3. Hard-block cards: Secret Police, Hall of Ministers, and Forgotten
    Vault outside campaigns.
  ```

  with

  ```
  - [x] 3. Hard-block cards: Secret Police and Hall of Ministers; Forgotten
    Vault is never waivable
    ([plan](superpowers/plans/2026-10-09-consent-slice3.md)).
  ```

- In the "Play-test consent in a browser" item, replace `The amber destination and campaign target, the` with `The amber destination, campaign target, faceup Vision and Hall-protected card, the`. Then replace `player-ruled Narrow Pass needs` with `player-ruled Narrow Pass, Secret Police or Hall of Ministers needs`.

In `docs/rules/implementation-traceability.md`, row "“Enemies cannot” consent enforcement":

- Replace `Narrow Pass is waivable on Travel and on campaign targeting: a destination or extra target only it blocks is askable,` with `Narrow Pass is waivable on Travel and on campaign targeting, Secret Police on a faceup Vision, and the Hall of Ministers on any discard an enemy chooses: an option only they block is askable,`.
- Replace `(`model/Consent.scala`, `application/ConsentDesk.scala`, `gameplay/walker/WalkerPowerGather.scala`)` with `(`model/Consent.scala`, `application/ConsentDesk.scala`, `gameplay/walker/WalkerPowerGather.scala`, `gameplay/operations/ConsentedRestriction.scala`, `gameplay/operations/OperationConsent.scala`)`.
- Replace `Tests: `NarrowPassConsentSuite`, `NarrowPassCampaignConsentSuite`, `CampaignConsentServiceSuite`,` with `Tests: `NarrowPassConsentSuite`, `NarrowPassCampaignConsentSuite`, `CampaignConsentServiceSuite`, `SecretPoliceConsentSuite`, `HallOfMinistersConsentSuite`, `HallOfMinistersConsentServiceSuite`,`.
- Replace `Secret Police, Hall of Ministers, Forgotten Vault and the toll and penalty cards are consent slices 3 and 4;` with `The toll and penalty cards are consent slice 4; Forgotten Vault is never waivable (campaign-only); the Hall on Dark Enforcer, region discards and Mounted Library has no consent point;`.

- [ ] **Step 3: Run the full gate**

Run, in order:
1. `./sbtw "test" "frontend/test"`: expected PASS.
2. `./sbtw frontend/fullOptJS`: expected success.
3. `./sbtw coverage test coverageReport`: expected PASS, with the coverage gate met.
4. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`: expected no findings.
5. `git diff --check`: expected no output.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-10-08-consent-system-design.md \
  docs/superpowers/plans/2026-10-09-consent-slice3.md \
  CONTEXT.md docs/ROADMAP.md docs/rules/implementation-traceability.md
git commit -m "docs(consent): record slice 3 in the spec, the vocabulary, the roadmap and the traceability"
```
