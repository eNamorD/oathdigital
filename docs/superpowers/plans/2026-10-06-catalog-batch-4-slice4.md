# Catalog Batch 4, Slice 4 (When Played) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement seven WHEN PLAYED powers: Revelation (63), Blackmail (82), Dissent (84), Wizard's Conclave (208), Bandit Chief (100), A Round of Ale (129) and Call for Help (246).

**Architecture:**

- **Each card is a `WhenPlayedPower`** in `gameplay/powers/whenplayed/`, registered in `WhenPlayedPowers.forCatalog`. Each card's object moves out of `catalog/holding/` into its power's file, and its `ActionPowers` entry flips to implemented.
- **Two powers park a question another player owns:** each player's burn in Revelation, and the holder's answer in Blackmail. This is the first WHEN PLAYED to do so. The engine already supports it, and Revelation gets a test through the whole rules path.
- **Approved change outside `gameplay/powers/`:** the Rest cleanup moves out of `FinishRestProcedure` into `RestCleanup.operations`, beside `RestCleanupPlan`, so that A Round of Ale can run it. Finish Rest's behavior is unchanged.
- **Powers-side additions:**
  - `PowerAnswers.amounts` reads every answer to an amount that a `Repeat` asks again (Call for Help).
  - The test harness `WhenPlayedHarness` learns to answer another player's question and to walk a power to its end.
- **No engine change.** No new operation, window, query, option kind, `NoteArg` kind, protocol change or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`: the sections "The bar", "Seams used for the first time", "Implementation notes from the verification", "Log lines", "Slice 4: when played", "Testing" and "Verify at plan time".
- `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md`: "Rules that apply to the whole batch" and the per-card rulings under "Slice 4: when played".

The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- **Slice order.** Slices 1a, 1b, 2 and 3 are on `main` (slice 3 merged as e2f0814f). Check with `git log main --oneline | grep "record catalog batch 4 slice 3"`. This slice uses slice 3's `PowerAnswers.last`, `LostTongue#protects` and `PowerAnswersSuite`.
- **Build rules.** `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails the build.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` (such as `val Vector(a, b) = ...`) also fails, so bind each value separately. A tuple pattern over a tuple type is irrefutable and is fine.
- **File size.** Production and test Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Importing `gameplay.phases` is allowed.
  - No power name appears in walker sources.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Note templates.** A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite`). A WHEN PLAYED power is not a phase power, so it needs no `used` key.
- **Log lines, exactly** (spec, "Log lines", "Slice 4: when played", with plan-time rulings 1 and 8). Revelation, Dissent and Wizard's Conclave name every player, the actor included, because each line is about several players. Bandit Chief names whose warband died, the actor's included. Blackmail's `took` line and Call for Help's lines do not name the actor.

  | Card | Key | Line | When |
  |---|---|---|---|
  | Revelation | `burned` | Revelation: {Blue} burned {n favor} and gained {n secrets}. | one per player who burned; covers that step's Gain line |
  | Revelation | `declined` | Revelation: {Blue} did not burn any favor. | one per player asked who burned none |
  | Revelation | `none` | Revelation: Nobody had favor to burn. | nobody asked |
  | Blackmail | `paid` | Blackmail: {Blue} gave {3 favor} to keep {relic}. | the holder paid |
  | Blackmail | `took` | Blackmail: Took {relic} from {Blue}. | the relic was taken |
  | Blackmail | `none` | Blackmail: No relic could be taken. | nothing to choose |
  | Dissent | `placed` | Dissent: {Blue} placed {n favor} on it. | one per player who placed |
  | Dissent | `none` | Dissent: Nobody placed favor. | nobody placed |
  | Wizard's Conclave | `took` | Wizard's Conclave: {Blue} took the {Darkest Secret}. | the banner moved |
  | Wizard's Conclave | `kept` | Wizard's Conclave: {Blue} kept the {Darkest Secret}. | the winner already held it |
  | Wizard's Conclave | `none` | Wizard's Conclave: The {Darkest Secret} did not move. | Lost Tongue refused the take |
  | Bandit Chief | `killed` | Bandit Chief: Killed {n} {Blue} warband at {site}. | one per site, in map order |
  | Bandit Chief | `none` | Bandit Chief: No warband was killed. | nothing to kill |
  | A Round of Ale | `cleanup` | A Round of Ale: Triggering resource cleanup. | always, before the cleanup |
  | Call for Help | `revealed` | Call for Help: Revealed and discarded {cards}, and gained {n warbands}. | one per draw that gained warbands; covers that draw's Revealed lines |
  | Call for Help | `discarded` | Call for Help: Revealed and discarded {cards}. | one per draw that gained none; covers that draw's Revealed lines |
  | Call for Help | `none` | Call for Help: Nothing was revealed. | nothing drawn |

  Every decision whose answer a line names is declared narrated, so it writes no generic "Chose" line: Blackmail's two and Wizard's Conclave's tie. An amount writes no "Chose" line of its own. Call for Help's "Keep drawing" or "Stop" is not named by a line, so it keeps its generic "Chose" line.
- **Batch rulings that every task applies** (rulings, "Rules that apply to the whole batch"):
  - **"Your site" and "your region"** mean the pawn's site and region.
  - **Turn order** for a WHEN PLAYED power that asks every player starts with the first player (`FinishRestProcedure.turnOrder`).
  - **Card backs are public.** Offering a facedown relic's slot discloses nothing.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare against them at the end. This plan adds 27 server tests and no frontend test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one says what it costs if it is wrong.

1. **Generic lines** (the spec's "verify" items, checked in `DetailLines.delta`).
   - A favor `Burn` writes no line. A `Gain.Secrets` writes "{Blue} gained {n} secrets", or "Gained ..." for the actor. So Revelation's `burned` covers the Gain line only.
   - Favor moving between boards or onto a card writes no line, nor does a relic's or a banner's `Take`. So Blackmail, Dissent and Wizard's Conclave cover nothing.
   - A `Kill` writes no line, so Bandit Chief covers nothing.
   - A card moving between discard piles writes no line, and `Reveal` writes "Revealed {card}" per card. So Call for Help covers its draw's Revealed lines.
   - The Rest cleanup's moves (card favor to a bank, card secrets to a board) and its `FlipSecrets` write no line. A Round of Ale's line comes first, and nothing follows it.
   - Task 8 corrects the spec's table. Cost if wrong: a generic line shows twice.
2. **Questions another player owns.**
   - Revelation's burns and Blackmail's holder answer are `Decide`s owned by that player. The walker checks the submitter against the owner (`ProcedureWalker.resolve`), and Task 1 tests a wrong submitter through the rules.
   - The walker resumes from the parked node, expanding each `Branch` on the way with the live state. So Revelation gives every player in turn order the same two `Branch`es whether or not they are asked, and a later player's question keeps its place after an earlier player's burn changes the state.
3. **Turn order.** Revelation asks, and Dissent places, in `FinishRestProcedure.turnOrder`, which starts with the first player. Dissent asks nobody, but its lines follow the same order.
4. **Blackmail's relics.**
   - The relics are those of every other player whose pawn is at the actor's site, in seat then relic order, faceup or facedown.
   - A faceup relic is offered as itself (`DecisionOption.Relic`), and a facedown one by its slot (`DecisionOption.RelicSlot`).
   - Verified: `WalkerDecisionProjector` shows a `RelicSlot` as "{owner} facedown relic" with no card, so a facedown relic discloses nothing. A faceup relic offered by slot would wrongly read "facedown", which is why it is offered as itself.
   - A relic Lost Tongue protects is not offered (slice 3's `LostTongue#protects`). The holder is asked only with 3 favor or more; otherwise the relic is taken.
   - Cost if wrong: none for play.
5. **Wizard's Conclave.**
   - The Arcane cards are counted with `RuledCards.of`. When nobody rules one, every player ties.
   - The tie question is asked only when two or more players tie, and it is the actor's.
   - The winner takes the banner by a `Take` with no `leaving` operations, so its secrets stay on it. The `Take` is from the holder's play area, or from `Location.SharedBank` when nobody holds the banner, as Challenge takes an unheld banner.
   - The `Take` is not required, so Lost Tongue's refusal skips it (`OperationPipeline`'s skipped operations) and the `none` line says the banner did not move.
6. **Bandit Chief.**
   - The sites are those in play whose forces are not bandits, in map order. Each loses one warband by a `Kill`, in a step of its own, with its own line.
   - The owner is the player whose force kind the warbands are. Under the all-Exile rule every non-bandit kind is a player's; a kind no player owns would die with no line.
   - The bandit refill after each command (`StateBasedEvaluation.banditRefill`) refills an emptied site. Task 5 tests it through a real Search.
7. **A Round of Ale** runs `RestCleanup.operations` with the actor as the resting player, in one `BuildOps` that declares no window, so League Treaty's `RestReturnFavor` contribution never applies. Task 6 tests that with every production power registered.
8. **Call for Help's draws.**
   - A `Repeat` runs a draw per pass. Each pass asks an amount from 0 to the smaller of what is left of 6 and the pile's size, moves that many cards off the top, and then asks "Keep drawing" or "Stop".
   - "Keep drawing" is asked only when fewer than 6 are drawn, the pile is not empty and the pass's amount was not 0, so an amount of 0 ends the drawing.
   - `PowerAnswers.amounts` reads every pass's amount: the latest is the last, and their sum is what has been drawn.
   - A pile stores its top last (`CardZones.pile`), and a `Move` to a pile's `StackPosition.Top` appends. Each card moves in turn, so the first drawn ends deepest (the spec's "verify at plan time").
   - A card entering a discard pile is forgotten (`CardKnowledgeMoves`), so each card is revealed after its move, in the destination pile, as Inquisitor does. Every player then knows it.
   - One line per draw. The spec has one template, which would read "gained 0 warbands" when no Nomad card was drawn, so a draw that gains none uses a second key, `discarded`. Task 8 adds it to the spec. Cost if wrong: renaming a key later orphans the notes of saved games, so decide before the slice merges.
9. **Dissent** places by a `Give` from each player's board onto the card (`Location.OnCard`), as Whispering Leaves does. The amount is the number of suits among the cards the player rules, or all the favor they hold. The Rest cleanup returns the favor to the Discord bank; Task 3 pins that through `RestCleanupPlan`.
10. **Test filler.**
    - Four suites use Revelation as an unimplemented WHEN PLAYED card: `PowerImplementationStatusSuite`, `GamePresentationProjectorImplementedSuite`, `MinorActionsSuite` and `GameApplicationServiceStreamSuite`. Task 1 moves them to Bewitch (67), a Citizen-related card that stays unimplemented, as the spec's "Test filler" note says.
    - Verified for the stream suite: the fixture's world deck holds the first ten Arcane denizens in catalog order, and neither Revelation nor Bewitch is among them. So the suite's deck swap still overwrites p2's dealt Wizard's Conclave, and no seat plays Wizard's Conclave.
    - Wizard's Conclave is filler in several application and log suites. Most hold it facedown or discard it. `GameLogActionLineSuite` and `GameLogDetailSuite` play it faceup through `LogScripts`, whose `Situation` answers any park with its first option. Task 4 runs them.
    - Bandit Chief and Blackmail are filler only at sites, in piles or in a drawn hand. None of those plays them, so their powers never fire.
11. **Registration and pins.**
    - Each suite reads its power through `WhenPlayedHarness.registered`, which looks it up in `WalkerPowerCatalog.default`, so an unregistered power fails its suite.
    - `PowerKindsCatalogSuite` lists no WHEN PLAYED power.

## File Structure

Production, under `src/main/scala/oathdigital/`:

| File | Change | Responsibility | Task |
|---|---|---|---|
| `gameplay/powers/whenplayed/Revelation.scala` | create | Revelation's card and power | 1 |
| `gameplay/powers/whenplayed/Blackmail.scala` | create | Blackmail's card and power | 2 |
| `gameplay/powers/whenplayed/Dissent.scala` | create | Dissent's card and power | 3 |
| `gameplay/powers/whenplayed/WizardsConclave.scala` | create | Wizard's Conclave's card and power | 4 |
| `gameplay/powers/whenplayed/BanditChief.scala` | create | Bandit Chief's card and power | 5 |
| `gameplay/phases/RestCleanup.scala` | modify | `RestCleanup.operations`, the Rest cleanup's operations | 6 |
| `gameplay/phases/rest/FinishRestProcedure.scala` | modify | runs the cleanup through `RestCleanup` | 6 |
| `gameplay/powers/whenplayed/ARoundOfAle.scala` | create | A Round of Ale's card and power | 6 |
| `gameplay/powers/PowerAnswers.scala` | modify | `amounts` | 7 |
| `gameplay/powers/whenplayed/CallForHelp.scala` | create | Call for Help's card and power | 7 |
| `gameplay/powers/whenplayed/WhenPlayedPowers.scala` | modify | registers each power | 1–7 |
| `gameplay/powers/ActionPowers.scala` | modify | flips each card to implemented | 1–7 |
| `catalog/holding/HoldingArcaneDenizens.scala` | modify | Revelation and Wizard's Conclave leave | 1, 4 |
| `catalog/holding/HoldingDiscordDenizens.scala` | modify | Blackmail, Dissent and Bandit Chief leave | 2, 3, 5 |
| `catalog/holding/HoldingHearthDenizens.scala` | modify | A Round of Ale leaves | 6 |
| `catalog/holding/HoldingNomadDenizens.scala` | modify | Call for Help leaves | 7 |
| `gameplay/cards/NewFoundations.scala` | modify | imports each moved card from `gameplay.powers.whenplayed` | 1–7 |

Tests, under `src/test/scala/oathdigital/`:

| File | Change | Tests | Task |
|---|---|---|---|
| `gameplay/powers/whenplayed/WhenPlayedHarness.scala` | modify | | 1, 4, 6 |
| `gameplay/powers/whenplayed/RevelationSuite.scala` | create | 4 | 1 |
| `gameplay/powers/PowerImplementationStatusSuite.scala` | modify | | 1 |
| `application/GamePresentationProjectorImplementedSuite.scala` | modify | | 1 |
| `gameplay/MinorActionsSuite.scala` | modify | | 1 |
| `application/GameApplicationServiceStreamSuite.scala` | modify | | 1 |
| `gameplay/powers/whenplayed/BlackmailSuite.scala` | create | 4 | 2 |
| `gameplay/powers/whenplayed/DissentSuite.scala` | create | 2 | 3 |
| `gameplay/powers/whenplayed/WizardsConclaveSuite.scala` | create | 5 | 4 |
| `gameplay/powers/whenplayed/BanditChiefSuite.scala` | create | 3 | 5 |
| `gameplay/powers/whenplayed/ARoundOfAleSuite.scala` | create | 3 | 6 |
| `gameplay/powers/PowerAnswersSuite.scala` | modify | 1 | 7 |
| `gameplay/powers/whenplayed/CallForHelpSuite.scala` | create | 5 | 7 |

Docs: `docs/ROADMAP.md` and the batch 4 design spec, in Task 8.

**Moving a card out of its holding file.** Each task names the object. Delete the whole `object ...Card` definition, its lines and the blank line after it, and paste it unchanged at the top of the new power file, as the code blocks below show. In `NewFoundations.scala`, add the card's name to the `import oathdigital.gameplay.powers.whenplayed.{...}` list where each task says, keeping the list's line wrap within 80 columns. The card lists below it name the object already and do not change. Every holding file keeps a use of each name it imports, except where Task 5 says otherwise.

**Registering a power.** In `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`, the list ends with a line that has no `++`. Add ` ++` to the end of that line and put the task's `Vector(...)` on a new line below it. In `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`, change the card's `played` to `playedDone`.

---

### Task 1: Harness, Revelation, and its filler

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Revelation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `RevelationCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `RevelationCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/whenplayed/RevelationSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`
- Modify: `src/test/scala/oathdigital/application/GamePresentationProjectorImplementedSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala`
- Modify: `src/test/scala/oathdigital/application/GameApplicationServiceStreamSuite.scala`

**Interfaces:**
- Consumes: `FinishRestProcedure.turnOrder(ready): Vector[PlayerId]`, `NoteSupport.board`, `NoteSupport.favor`, `NoteSupport.secrets`, `SearchFixture`.
- Produces:
  - `WhenPlayedHarness.resumeBy(ready, power, hook, tree, decisionId, answer, by: PlayerId)`.
  - `WhenPlayedHarness.Walked(asked: Vector[Decide], events: Vector[OathEvent], end: ReadyGame)` and `WhenPlayedHarness.walkAnswering(ready, power, hook, answers: DecisionAnswer*): Walked`, which Tasks 2 and 7 use.
  - `case object Revelation` with `decisionId = "cardplay.revelation.burn"` and the keys `burned`, `declined`, `none`.

- [ ] **Step 1: Teach the harness to answer for other players and to walk to the end**

In `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`, replace the `resume` method (its doc comment and body) with:

```scala
  /** Resumes `tree` with the actor's `answer` to `decisionId`. */
  def resume(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, tree: PendingTree, decisionId: String,
      answer: DecisionAnswer): Either[OathViolation, WalkerOutcome] =
    resumeBy(ready, power, hook, tree, decisionId, answer, actor)

  /** Resumes `tree` with `by`'s `answer` to `decisionId`: a question another
    * player owns, such as each player's burn in Revelation. */
  def resumeBy(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, tree: PendingTree, decisionId: String,
      answer: DecisionAnswer, by: PlayerId)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(ready, hook, tree,
      Answered(decisionId, answer, by), powers(power))

  /** A walk answered to its end: each question in the order it was asked,
    * the events of every command, and the state the walk finished in. */
  final case class Walked(asked: Vector[Decide], events: Vector[OathEvent],
      end: ReadyGame)

  /** Fires `hook` from `ready`, then answers each question the walk parks at
    * with the next of `answers`, as the question's owner, from the state the
    * walk has reached. Fails when a question finds no answer left, or when
    * answers are left over. */
  def walkAnswering(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, answers: DecisionAnswer*)(
      using munit.Location): Walked =
    def go(outcome: WalkerOutcome, events: Vector[OathEvent],
        asked: Vector[Decide], left: List[DecisionAnswer]): Walked =
      outcome match
        case WalkerOutcome.Finished(end, more) =>
          if left.nonEmpty then munit.Assertions.fail(
            s"${left.size} answers were left over")
          Walked(asked, events ++ more, end)
        case WalkerOutcome.Parked(tree, more) =>
          val reached = events ++ more
          val at = reachedFrom(ready, reached)
          val parked = question(at, power, hook, tree)
          val next = left.headOption.getOrElse(munit.Assertions.fail(
            s"no answer is left for ${parked.decisionId}"))
          go(accepted(resumeBy(at, power, hook, tree, parked.decisionId,
            next, parked.owner)), reached, asked :+ parked, left.drop(1))
    go(accepted(fire(ready, power, hook)), Vector.empty, Vector.empty,
      answers.toList)

  /** The state `events` reach from `ready`: `ready` itself while no step
    * has run, since `replayed` refuses an empty batch. */
  private def reachedFrom(ready: ReadyGame, events: Vector[OathEvent])
      : ReadyGame =
    if PhasePowerHarness.ops(events).isEmpty then ready
    else replayed(ready, events)
```

The harness already imports everything this needs: `ProcedureWalker`, `WalkerOutcome`, `oathdigital.model._`, `PhasePowerHarness`, and `accepted`.

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/RevelationSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class RevelationSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Revelation.type]
  private val revelation = power.cardId
  private val played = hook(revelation)

  /** Revelation as p1's adviser, with p1, p2 and p3 holding the favor given
    * and the quiet table's one faceup secret each. */
  private def holdingFavor(p1Favor: Int, p2Favor: Int, p3Favor: Int): Table =
    Table.start.adviser(p1, revelation).favor(p1, p1Favor)
      .favor(p2, p2Favor).favor(p3, p3Favor)

  private def burn(amount: Int): DecisionAnswer =
    DecisionAnswer.ChooseAmountAnswer(amount)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def burned(player: PlayerId, n: Int): NoteText.Said =
    NoteText.Said("burned", s"${player.value} burned $n favor and gained " +
      s"$n ${if n == 1 then "secret" else "secrets"}.", covers = true)

  private def mostAsked(walked: Walked): Vector[Int] =
    walked.asked.map(_.query).collect {
      case query: DecisionQuery.ChooseAmount => query.max }

  test("each player with favor, in turn order from the first player, burns " +
      "what they choose and gains as many secrets"):
    // p2 is the first player, so the order is p2, p3, then p1.
    val ready = holdingFavor(2, 1, 3).update(game => game.copy(
      setup = game.setup.copy(firstPlayer = p2))).ready
    val walked = walkAnswering(ready, power, played, burn(1), burn(2),
      burn(0))
    assertEquals(walked.asked.map(_.owner), Vector(p2, p3, p1))
    assertEquals(mostAsked(walked), Vector(1, 3, 2))
    val end = Look(walked.end)
    assertEquals(Vector(p1, p2, p3).map(end.favor), Vector(2, 0, 1))
    assertEquals(Vector(p1, p2, p3).map(end.faceUpSecrets), Vector(1, 2, 3))
    assertEquals(said(walked.events), Vector(burned(p2, 1), burned(p3, 2),
      NoteText.Said("declined", s"${p1.value} did not burn any favor.",
        covers = false)))
    assertEquals(replayed(ready, walked.events), walked.end)
    assertWireRoundTrips(walked.events)

  test("a player with no favor is not asked"):
    val walked = walkAnswering(holdingFavor(1, 0, 1).ready, power, played,
      burn(1), burn(1))
    assertEquals(walked.asked.map(_.owner), Vector(p1, p3))
    assertEquals(said(walked.events), Vector(burned(p1, 1), burned(p3, 1)))

  test("with nobody holding favor nothing is asked, and the line says so"):
    val done = Outcomes.finished(play(holdingFavor(0, 0, 0).ready, power,
      revelation))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "Nobody had favor to burn.", covers = false)))

  test("played from a Search, each question waits for its own player, and " +
      "no other player may answer it"):
    val ready = SearchFixture.staged(Vector(revelation), supply = 5)
    val placed = SearchFixture.play(ready, Vector.empty, revelation,
      PlayedAs.Faceup)
    def answer(from: OathTransition, by: PlayerId, amount: Int) =
      SearchFixture.rules.resolveWalker(from.state, by, Revelation.decisionId,
        burn(amount))
    val p1Burnt = accepted(answer(placed, p1, 1))
    assert(answer(p1Burnt, p1, 0).isLeft)
    val p2Declined = accepted(answer(p1Burnt, p2, 0))
    val done = accepted(answer(p2Declined, p3, 1))
    val end = Look(SearchFixture.after(done))
    // Each started with 1 favor and 1 faceup secret.
    assertEquals(Vector(p1, p2, p3).map(end.favor), Vector(0, 1, 0))
    assertEquals(Vector(p1, p2, p3).map(end.faceUpSecrets), Vector(2, 1, 2))
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.RevelationSuite"`
Expected: compile failure, `Revelation` is not found.

- [ ] **Step 4: Write Revelation**

Remove `object RevelationCard` from `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/Revelation.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object RevelationCard extends Denizen(DenizenId("63"), "Revelation", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.revelation"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** each player, following turn order, may " +
      "burn any number of [favor] to gain an equal number of " +
      "[secret].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Revelation (card 63), WHEN PLAYED: each player, following turn order, may
  * burn any number of favor to gain an equal number of secrets.
  *
  * Turn order starts with the first player (`FinishRestProcedure.turnOrder`),
  * wherever the actor sits. Each player who holds favor is asked, as the
  * owner of their own question, for an amount from 0 to their favor; a
  * player with none is not asked. The burn and the gain are one step, so
  * each player answers after the players before them have burned. Every
  * player keeps the same place in the tree whether or not they are asked,
  * so a later player's question resumes where it parked.
  *
  * One line per player who burned covers that step's Gain line, and one
  * names each player who was asked and burned none. With nobody asked, one
  * line says nobody had favor.
  */
case object Revelation extends WhenPlayedPower:
  val cardId: DenizenId = RevelationCard.id
  val id: PowerId = RevelationCard.power.id
  val decisionId: String = "cardplay.revelation.burn"
  /** "{Blue} burned {n favor} and gained {n secrets}." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(" and gained "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Blue} did not burn any favor." */
  val declined: NoteKey = NoteKey("declined", Vector(NotePart.Arg(0),
    NotePart.Text(" did not burn any favor.")))
  /** "Nobody had favor to burn." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Nobody had favor to burn.")))

  override def noteKeys: Vector[NoteKey] = Vector(burned, declined, none)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    FinishRestProcedure.turnOrder(ctx.state).flatMap(turn) :+
      Note(id, noneNote)

  /** One player's place: their question while they hold favor, then what
    * their answer does. */
  private def turn(player: PlayerId): Vector[Operation] = Vector(
    Branch((live, _) => ask(live, player)),
    Branch((_, pending) => settle(pending.answered, player)))

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    NoteSupport.board(ready, player).map(_.favor).filter(_ > 0).toVector
      .map(favor => Decide(decisionId, player, DecisionQuery.ChooseAmount(0,
        favor, Some("Revelation: burn any favor to gain as many secrets"),
        "Burn favor")))

  /** The burn and gain `player` answered, with its line; the line that they
    * burned none; or nothing when they were not asked. */
  private def settle(answered: Vector[Answered], player: PlayerId)
      : Vector[Operation] = burnt(answered, player) match
    case Some(0) => Vector(Note(id, _ =>
      Some(declined(cardSource, NoteArg.Player(player)))))
    case Some(amount) => Vector(
      BuildOps((_, _) => Right(Vector(
        Burn.favor(amount, PositionedLocation(Location.PlayArea(player))),
        Gain.Secrets(player, amount)))),
      Note(id, burnedNote(_, player), covers = true))
    case None => Vector.empty

  /** The amount `player` answered, if they were asked. */
  private def burnt(answered: Vector[Answered], player: PlayerId)
      : Option[Int] = answered.collectFirst {
    case Answered(`decisionId`, DecisionAnswer.ChooseAmountAnswer(amount),
        `player`) => amount }

  /** The favor `player` lost and the secrets they gained in the step
    * before. */
  private def burnedNote(states: NoteStates, player: PlayerId)
      : Option[PowerNote] = for
    step <- states.previous
    lost = -NoteSupport.favor(step, player)
    if lost > 0
  yield burned(cardSource, NoteArg.Player(player),
    NoteArg.Amount(lost, NoteUnit.Favor),
    NoteArg.Amount(NoteSupport.secrets(step, player), NoteUnit.Secret))

  /** "Nobody had favor to burn." when nobody was asked. */
  private def noneNote(states: NoteStates): Option[PowerNote] =
    Option.when(!states.answered.exists(_.decisionId == decisionId))(
      none(cardSource))
```

- [ ] **Step 5: Register it**

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`:

- Register `Vector(Revelation)`, as "Registering a power" above says.
- In the doc comment, change "The When Played powers of catalog batch 3," to "The When Played powers of catalog batches 3 and 4,". Keep the comment's line wrap within 80 columns.

In `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`, change `object Revelation extends ReviewedPower("denizen.revelation", None, played)` to end in `playedDone)`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `RevelationCard` to the `gameplay.powers.whenplayed` import, after `KeyToTheCityCard`.

- [ ] **Step 6: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.RevelationSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 4 tests in `RevelationSuite`.

- [ ] **Step 7: Move the filler suites to Bewitch**

Revelation is implemented now, so four suites that need an unimplemented WHEN PLAYED card move to Bewitch (plan-time ruling 10).

In `src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala`:

- In the class doc comment, change "Dazzle,\n  * Revelation, Catacombs and Silver Tongue" to "Dazzle,\n  * Bewitch, Catacombs and Silver Tongue".
- Change `assert(!implemented(PowerId("denizen.revelation")))` to `assert(!implemented(PowerId("denizen.bewitch")))`.

In `src/test/scala/oathdigital/application/GamePresentationProjectorImplementedSuite.scala`:

- In the class doc comment, change "Dazzle, Revelation, and Catacombs" to "Dazzle, Bewitch, and Catacombs".
- In the test "a denizen whose power is reviewed but not yet built reads as not implemented", replace its two lines with:

```scala
    val bewitch = denizenWith("denizen.bewitch")
    assert(!projector.cardDetails(bewitch, None, hidden = false).implemented)
```

In `src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala`, in the test "source-scoped fallback and replay use the recorded off-turn actor", replace these lines:

```scala
    // p2, not the active player, holds Revelation faceup.
    val revelation = CatalogNames.denizen("Revelation")
    val changed = minorBoard.adviser(p2, revelation).ready
    val source = RuleSourceRef.Adviser(p2, revelation)
    val expected = accepted(PowerRuntime.ignoredAtSource(catalog, changed, p2,
      ActionKind.WhenPlayed, source))
    assertEquals(expected.map(_.handlerId), Vector("denizen.revelation"))
```

with:

```scala
    // p2, not the active player, holds Bewitch faceup.
    val bewitch = CatalogNames.denizen("Bewitch")
    val changed = minorBoard.adviser(p2, bewitch).ready
    val source = RuleSourceRef.Adviser(p2, bewitch)
    val expected = accepted(PowerRuntime.ignoredAtSource(catalog, changed, p2,
      ActionKind.WhenPlayed, source))
    assertEquals(expected.map(_.handlerId), Vector("denizen.bewitch"))
```

In `src/test/scala/oathdigital/application/GameApplicationServiceStreamSuite.scala`, in the test "all-Exile powered game persists and replays through round-eight victory", change `PowerId("denizen.revelation")` to `PowerId("denizen.bewitch")`.

- [ ] **Step 8: Run the filler suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerImplementationStatusSuite oathdigital.application.GamePresentationProjectorImplementedSuite oathdigital.gameplay.MinorActionsSuite oathdigital.application.GameApplicationServiceStreamSuite"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/Revelation.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/RevelationSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/PowerImplementationStatusSuite.scala \
  src/test/scala/oathdigital/application/GamePresentationProjectorImplementedSuite.scala \
  src/test/scala/oathdigital/gameplay/MinorActionsSuite.scala \
  src/test/scala/oathdigital/application/GameApplicationServiceStreamSuite.scala
git commit -m "feat(whenplayed): Revelation lets each player burn favor for secrets in turn order"
```

---

### Task 2: Blackmail

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Blackmail.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `BlackmailCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `BlackmailCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/whenplayed/BlackmailSuite.scala`

**Interfaces:**
- Consumes: `LostTongue.forCatalog(catalog).protects(ready, taker, owner)` (slice 3), `PowerAnswers.one`, `PowerAccess.pawnSite`, `NoteSupport.board`, `NoteSupport.favor`, Task 1's `walkAnswering`.
- Produces: `final case class Blackmail` with `Blackmail.forCatalog(catalog)`, `relicDecisionId = "cardplay.blackmail.relic"`, `answerDecisionId = "cardplay.blackmail.answer"`, `Price = 3`, the buttons `pay` and `handOver`, and the keys `paid`, `took`, `none`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/BlackmailSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}

class BlackmailSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Blackmail]
  private val blackmail = power.cardId
  private val played = hook(blackmail)
  private val horn = CatalogNames.relic("Cracked Horn")
  private val ring = CatalogNames.relic("Ring of Devotion")
  private val trumpet = CatalogNames.relic("Painted Trumpet")

  /** Blackmail as p1's adviser. p2 stands at p1's site holding the Cracked
    * Horn, faceup unless `facedown`, and `favor`. p1 holds the Ring of
    * Devotion, and p3, away at Buried Giant, the Painted Trumpet. */
  private def p2HoldingTheHorn(favor: Int, facedown: Boolean = false): Table =
    Table.start.adviser(p1, blackmail).pawn(p2, homeSite)
      .relic(p2, horn, facedown).favor(p2, favor)
      .relic(p1, ring).relic(p3, trumpet)

  private def chose(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def tookTheHorn: NoteText.Said = NoteText.Said("took",
    s"Took ${horn.value} from ${p2.value}.", covers = false)

  private def options(asked: Decide)(using munit.Location)
      : Vector[DecisionOption] = asked.query match
    case query: DecisionQuery.ChooseOne => query.options
    case other => fail(s"expected a ChooseOne, got $other")

  test("the holder gives 3 favor to keep their relic, and only other " +
      "players' relics at p1's site are offered"):
    val ready = p2HoldingTheHorn(favor = 4).ready
    val walked = walkAnswering(ready, power, played,
      chose(DecisionOptionRef.Relic(horn)), chose(Blackmail.pay))
    assertEquals(walked.asked.map(_.owner), Vector(p1, p2))
    assertEquals(options(walked.asked.head), Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(horn))))
    val end = Look(walked.end)
    // p1's 1 favor and the 3 p2 gave.
    assertEquals(end.favor(p1), 1 + 3)
    assertEquals(end.favor(p2), 4 - 3)
    assertEquals(end.relics(p2), Vector(horn))
    assertEquals(said(walked.events), Vector(NoteText.Said("paid",
      s"${p2.value} gave 3 favor to keep ${horn.value}.", covers = false)))
    assertEquals(replayed(ready, walked.events), walked.end)
    assertWireRoundTrips(walked.events)

  test("the holder may hand the relic over instead"):
    val walked = walkAnswering(p2HoldingTheHorn(favor = 4).ready, power,
      played, chose(DecisionOptionRef.Relic(horn)), chose(Blackmail.handOver))
    val end = Look(walked.end)
    assertEquals(end.relics(p1), Vector(ring, horn))
    assertEquals(end.relics(p2), Vector.empty)
    assertEquals(end.favor(p2), 4)
    assertEquals(said(walked.events), Vector(tookTheHorn))

  test("a facedown relic is offered by its slot, and a holder with fewer " +
      "than 3 favor is not asked"):
    val slot = DecisionOptionRef.RelicSlot(p2, 0)
    val walked = walkAnswering(
      p2HoldingTheHorn(favor = 2, facedown = true).ready, power, played,
      chose(slot))
    assertEquals(walked.asked.map(_.owner), Vector(p1))
    assertEquals(options(walked.asked.head),
      Vector(DecisionOption.RelicSlot(slot)))
    val end = Look(walked.end)
    assertEquals(end.relics(p1), Vector(ring, horn))
    assertEquals(end.favor(p2), 2)
    assertEquals(said(walked.events), Vector(tookTheHorn))

  test("a relic Lost Tongue protects is not offered, and with none left the " +
      "line says so"):
    // p2 holds Lost Tongue faceup, and p1 rules no Nomad card.
    val ready = p2HoldingTheHorn(favor = 4).adviser(p2, "Lost Tongue").ready
    val done = Outcomes.finished(play(ready, power, blackmail))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No relic could be taken.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.BlackmailSuite"`
Expected: compile failure, `Blackmail` is not found.

- [ ] **Step 3: Write Blackmail**

Remove `object BlackmailCard` from `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/Blackmail.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.gameplay.powers.targeting.LostTongue
import oathdigital.model._

object BlackmailCard extends Denizen(DenizenId("82"), "Blackmail", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.blackmail"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** choose a relic held by a player whose pawn " +
      "is at your site. You take the relic unless they give you " +
      "[favor] [favor] [favor].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Blackmail (card 82), WHEN PLAYED: choose a relic held by a player whose
  * pawn is at your site. You take the relic unless they give you 3 favor.
  *
  * The relics are read live: those of every other player whose pawn stands
  * at the actor's pawn site, in seat then relic order, faceup or facedown. A
  * faceup relic is offered as itself and a facedown one by its slot, which
  * shows every viewer only "{owner} facedown relic". A relic Lost Tongue
  * keeps the actor from taking is not offered. Its holder then chooses, as
  * the owner of the question, to give 3 favor or hand the relic over; a
  * holder with fewer than 3 favor is not asked, and the relic is taken. The
  * favor moves by a `Give` and the relic by a `Take`. With no relic to
  * choose, one line says so.
  *
  * No generic line exists for either move, so the lines cover nothing.
  */
final case class Blackmail private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = BlackmailCard.id
  import Blackmail._
  def id: PowerId = Blackmail.id

  private val lostTongue = LostTongue.forCatalog(catalog)

  override def noteKeys: Vector[NoteKey] = Vector(paid, took, none)
  override def narratedDecisions: Set[String] =
    Set(relicDecisionId, answerDecisionId)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      Branch((live, _) => askRelic(live, actor)),
      Branch((live, pending) => askHolder(live, actor, pending)),
      Branch((live, pending) => settle(live, actor, pending)))

  /** One relic the actor may choose: its holder, its slot and its state. */
  private final case class Target(owner: PlayerId, slot: Int,
      relic: RelicState):
    def ref: DecisionOptionRef = relic.orientation match
      case Orientation.FaceUp => DecisionOptionRef.Relic(relic.id)
      case Orientation.FaceDown => DecisionOptionRef.RelicSlot(owner, slot)
    def option: DecisionOption = relic.orientation match
      case Orientation.FaceUp =>
        DecisionOption.Relic(DecisionOptionRef.Relic(relic.id))
      case Orientation.FaceDown =>
        DecisionOption.RelicSlot(DecisionOptionRef.RelicSlot(owner, slot))

  /** The relics of the other players at the actor's site that Lost Tongue
    * leaves takeable, in seat then relic order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[Target] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site => for
      other <- ready.game.current.players
      if other.player != actor && other.pawnSite.contains(site) &&
        !lostTongue.protects(ready, actor, other.player)
      (relic, slot) <- other.relics.zipWithIndex
    yield Target(other.player, slot, relic))

  private def askRelic(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    targets(ready, actor) match
      case Vector() => Vector(Note(id, _ => Some(none(cardSource))))
      case found => Vector(Decide(relicDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(_.option), heading = Some(
          "Blackmail: choose a relic held by a player at your site"))))

  private def chosen(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[Target] = PowerAnswers.one(pending, relicDecisionId)
    .flatMap(ref => targets(ready, actor).find(_.ref == ref))

  /** The holder's question, when they hold the price. */
  private def askHolder(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    chosen(ready, actor, pending).filter(target =>
        NoteSupport.board(ready, target.owner).exists(_.favor >= Price))
      .toVector.map(target => Decide(answerDecisionId, target.owner,
        DecisionQuery.ChooseOne(Vector(
          DecisionOption.Button(pay, s"Give $Price favor"),
          DecisionOption.Button(handOver, "Hand over the relic")),
          heading = Some(s"Blackmail: give $Price favor to keep your relic, " +
            "or hand it over"))))

  /** The favor given, or the relic taken, then its line. */
  private def settle(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[Operation] = chosen(ready, actor, pending).toVector.flatMap {
    target =>
      if PowerAnswers.one(pending, answerDecisionId).contains(pay) then Vector(
        BuildOps((_, _) => Right(Vector(Give(Piece.Favor(Price), target.owner,
          Location.PlayArea(target.owner), Location.PlayArea(actor))))),
        Note(id, paidNote(_, actor, target)))
      else Vector(
        BuildOps((_, _) => Right(Vector(Take(Piece.Card(target.relic.id),
          actor, Location.PlayArea(target.owner),
          Location.PlayArea(actor))))),
        Note(id, tookNote(_, actor, target)))
  }

  /** The favor the actor gained in the step before. */
  private def paidNote(states: NoteStates, actor: PlayerId, target: Target)
      : Option[PowerNote] = for
    step <- states.previous
    gained = NoteSupport.favor(step, actor)
    if gained > 0
  yield paid(cardSource, NoteArg.Player(target.owner),
    NoteArg.Amount(gained, NoteUnit.Favor), NoteArg.Card(target.relic.id))

  /** That the actor now holds the relic. */
  private def tookNote(states: NoteStates, actor: PlayerId, target: Target)
      : Option[PowerNote] = Option.when(states.now.game.current.players
    .exists(held => held.player == actor &&
      held.relics.exists(_.id == target.relic.id)))(
    took(cardSource, NoteArg.Card(target.relic.id),
      NoteArg.Player(target.owner)))

object Blackmail:
  val id: PowerId = BlackmailCard.power.id
  val relicDecisionId: String = "cardplay.blackmail.relic"
  val answerDecisionId: String = "cardplay.blackmail.answer"
  /** The favor that keeps the relic. */
  val Price: Int = 3
  val pay: DecisionOptionRef.Button = DecisionOptionRef.Button("pay")
  val handOver: DecisionOptionRef.Button =
    DecisionOptionRef.Button("hand-over")
  /** "{Blue} gave {3 favor} to keep {relic}." */
  val paid: NoteKey = NoteKey("paid", Vector(NotePart.Arg(0),
    NotePart.Text(" gave "), NotePart.Arg(1), NotePart.Text(" to keep "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Took {relic} from {Blue}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Text("Took "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "No relic could be taken." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No relic could be taken.")))

  def forCatalog(catalog: ExecutableCatalog): Blackmail =
    new Blackmail(catalog)
```

- [ ] **Step 4: Register it**

Register `Vector(Blackmail.forCatalog(catalog))` in `WhenPlayedPowers.scala`, as "Registering a power" above says. In `ActionPowers.scala`, change `Blackmail`'s `played` to `playedDone`. In `NewFoundations.scala`, add `BlackmailCard` to the `gameplay.powers.whenplayed` import, after `BanditPrinceCard`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.BlackmailSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.powers.search.TruthfulHarpSuite oathdigital.gameplay.powers.search.AugurySuite"`
Expected: PASS, 4 tests in `BlackmailSuite`. The Search suites draw Blackmail as filler and never play it.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/Blackmail.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/BlackmailSuite.scala
git commit -m "feat(whenplayed): Blackmail takes a relic at the site unless its holder pays 3 favor"
```

---

### Task 3: Dissent

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dissent.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `DissentCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `DissentCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/whenplayed/DissentSuite.scala`

**Interfaces:**
- Consumes: `FinishRestProcedure.turnOrder`, `BannerRules.holder`, `RuledCards.of`, `NoteSupport.board`, `NoteSupport.favor`, `RestCleanupPlan.derive`.
- Produces: `final case class Dissent` with `Dissent.forCatalog(catalog)` and the keys `placed`, `none`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/DissentSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class DissentSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Dissent]
  private val dissent = power.cardId

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def placed(player: PlayerId, n: Int): NoteText.Said =
    NoteText.Said("placed", s"${player.value} placed $n favor on it.",
      covers = false)

  test("each player but the People's Favor's holder places one favor per " +
      "suit they rule, or all they hold"):
    // p1 rules Dissent (Discord) and Homesteaders (Hearth) and holds 3
    // favor. p2 rules Land Warden (Hearth) and, through its warband there,
    // Disciples (Arcane) at another Cradle site, and holds 1. p3 holds the
    // People's Favor, and Defame (Discord), with 2 favor.
    val ready = Table.start.adviser(p1, dissent).adviser(p1, "Homesteaders")
      .favor(p1, 3)
      .adviser(p2, "Land Warden").warbandsAt(nearSite, p2, 1)
      .denizen("Disciples", at = nearSite).favor(p2, 1)
      .adviser(p3, "Defame").favor(p3, 2).peoplesFavor(Some(p3), 1).ready
    val done = Outcomes.finished(play(ready, power, dissent))
    val end = Look(done.treeless)
    assertEquals(Vector(p1, p2, p3).map(end.favor), Vector(1, 0, 2))
    assertEquals(end.tokensOn(dissent), Tokens(3, 0))
    // The Rest cleanup returns it all to the Discord bank.
    assertEquals(RestCleanupPlan.derive(catalog, done.treeless, p1)
      .map(_.returnedFavor), Right(Map(Suit.Discord -> 3)))
    assertEquals(said(done.events), Vector(placed(p1, 2), placed(p2, 1)))
    assertEquals(replayed(ready, done.events), done.treeless)

  test("with nobody able to place, the line says so"):
    // p1 rules Dissent but holds no favor; p2 and p3 rule nothing.
    val ready = Table.start.adviser(p1, dissent).favor(p1, 0).ready
    val done = Outcomes.finished(play(ready, power, dissent))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "Nobody placed favor.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.DissentSuite"`
Expected: compile failure, `Dissent` is not found.

- [ ] **Step 3: Write Dissent**

Remove `object DissentCard` from `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/Dissent.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, RuledCards}
import oathdigital.model._

object DissentCard extends Denizen(DenizenId("84"), "Dissent", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.dissent"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** each player, except the holder of the " +
      "People's Favor, places one [favor] on this card for each " +
      "suit of card they rule.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Dissent (card 84), WHEN PLAYED: each player, except the holder of the
  * People's Favor, places one favor on this card for each suit of card they
  * rule.
  *
  * The players are read live, in turn order from the first player, the
  * actor included. Each counts the suits among the cards they rule
  * ([[RuledCards]]) and places that many favor, or all they hold, by a
  * `Give` onto this card wherever it lies. Nobody is asked. The Rest cleanup
  * returns the favor to the Discord bank, as it returns any card's favor to
  * its suit's bank. One line per player who placed; with nobody placing,
  * one line says so.
  *
  * No generic line exists for favor placed on a card, so the lines cover
  * nothing.
  */
final case class Dissent private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = DissentCard.id
  import Dissent._
  def id: PowerId = Dissent.id

  override def noteKeys: Vector[NoteKey] = Vector(placed, none)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(Branch((live, _) => placeAll(live)))

  /** What each player places, in turn order: one favor per suit they rule,
    * or all they hold. A player who places nothing is left out. */
  private def placings(ready: ReadyGame): Vector[(PlayerId, Int)] =
    val holder = BannerRules.holder(ready.game.current, Banner.PeoplesFavor)
    FinishRestProcedure.turnOrder(ready).filterNot(holder.contains)
      .flatMap { player =>
        val suits = Suit.all.count(suit => RuledCards.of(catalog, ready,
          SiteRuler.Player(player), suit).nonEmpty)
        val amount = math.min(suits,
          NoteSupport.board(ready, player).fold(0)(_.favor))
        Option.when(amount > 0)(player -> amount)
      }

  private def placeAll(ready: ReadyGame): Vector[Operation] =
    placings(ready) match
      case Vector() => Vector(Note(id, _ => Some(none(cardSource))))
      case found => found.flatMap { (player, amount) => Vector(
        BuildOps((_, _) => Right(Vector(Give(Piece.Favor(amount), player,
          Location.PlayArea(player), Location.OnCard(cardId))))),
        Note(id, placedNote(_, player))) }

  /** The favor `player` lost in the step before. */
  private def placedNote(states: NoteStates, player: PlayerId)
      : Option[PowerNote] = for
    step <- states.previous
    lost = -NoteSupport.favor(step, player)
    if lost > 0
  yield placed(cardSource, NoteArg.Player(player),
    NoteArg.Amount(lost, NoteUnit.Favor))

object Dissent:
  val id: PowerId = DissentCard.power.id
  /** "{Blue} placed {n favor} on it." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Arg(0),
    NotePart.Text(" placed "), NotePart.Arg(1), NotePart.Text(" on it.")))
  /** "Nobody placed favor." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Nobody placed favor.")))

  def forCatalog(catalog: ExecutableCatalog): Dissent = new Dissent(catalog)
```

- [ ] **Step 4: Register it**

Register `Vector(Dissent.forCatalog(catalog))` in `WhenPlayedPowers.scala`. In `ActionPowers.scala`, change `Dissent`'s `played` to `playedDone`. In `NewFoundations.scala`, add `DissentCard` to the `gameplay.powers.whenplayed` import, after `DazzleCard`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.DissentSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 2 tests in `DissentSuite`.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/Dissent.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/DissentSuite.scala
git commit -m "feat(whenplayed): Dissent takes one favor per ruled suit from all but the People's Favor's holder"
```

---

### Task 4: Wizard's Conclave

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WizardsConclave.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `WizardsConclaveCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `WizardsConclaveCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/whenplayed/WizardsConclaveSuite.scala`

**Interfaces:**
- Consumes: `RuledCards.of`, `BannerRules.holder`, `PowerAnswers.one`, `LostTongue.forCatalog`.
- Produces:
  - `WhenPlayedHarness.playBeside(ready, power, card, others: ContributingPower*)`.
  - `final case class WizardsConclave` with `WizardsConclave.forCatalog(catalog)`, `decisionId = "cardplay.wizards-conclave.winner"` and the keys `took`, `kept`, `none`.

- [ ] **Step 1: Let the harness register a second power**

In `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`, add after `play`:

```scala
  /** `play`, with `others` registered beside `power`: a test that needs
    * another power's rule, such as Lost Tongue's, names it here. */
  def playBeside(ready: ReadyGame, power: ContributingPower, card: DenizenId,
      others: ContributingPower*): Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook(card), None,
      WalkerPowers(power +: others.toVector,
        restrictionSet = OperationRestrictions.forCatalog(catalog)))
```

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/WizardsConclaveSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.targeting.LostTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class WizardsConclaveSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[WizardsConclave]
  private val conclave = power.cardId
  private val played = hook(conclave)

  private def heldBy(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.banners.darkestSecret.holder

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def took(player: PlayerId): NoteText.Said = NoteText.Said("took",
    s"${player.value} took the Darkest Secret.", covers = false)

  private def playerOptions(players: PlayerId*): Vector[DecisionOption] =
    players.toVector.map(player =>
      DecisionOption.Player(DecisionOptionRef.Player(player)))

  private def options(asked: Decide)(using munit.Location)
      : Vector[DecisionOption] = asked.query match
    case query: DecisionQuery.ChooseOne => query.options
    case other => fail(s"expected a ChooseOne, got $other")

  private def chose(player: PlayerId): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(player))

  test("the player ruling the most Arcane cards takes the Darkest Secret, " +
      "its secrets with it"):
    // p1 rules Wizard's Conclave; p2 rules Disciples and Glamor.
    val ready = Table.start.adviser(p1, conclave).adviser(p2, "Disciples")
      .adviser(p2, "Glamor").darkestSecret(Some(p3), 2).ready
    val done = Outcomes.finished(play(ready, power, conclave))
    assertEquals(heldBy(done.treeless), Some(p2))
    assertEquals(done.treeless.game.current.banners.darkestSecret.secrets, 2)
    assertEquals(said(done.events), Vector(took(p2)))
    assertEquals(replayed(ready, done.events), done.treeless)

  test("p1 decides a tie among the tied players, themselves included"):
    // The quiet table's Darkest Secret is unheld.
    val ready = Table.start.adviser(p1, conclave).adviser(p2, "Glamor").ready
    val first = Outcomes.parked(play(ready, power, conclave))
    val asked = question(ready, power, played, first.tree)
    assertEquals(asked.owner, p1)
    assertEquals(options(asked), playerOptions(p1, p2))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      WizardsConclave.decisionId, chose(p1)))
    assertEquals(heldBy(done.treeless), Some(p1))
    assertEquals(said(done.events), Vector(took(p1)))

  test("when nobody rules an Arcane card, every player ties"):
    // Wizard's Conclave stands at Buried Giant, which nobody rules.
    val giant = CatalogNames.site("Buried Giant")
    val atGiant = hookAt(conclave, giant)
    val ready = Table.start.denizen(conclave, at = giant).ready
    val first = Outcomes.parked(fire(ready, power, atGiant))
    assertEquals(options(question(ready, power, atGiant, first.tree)),
      playerOptions(p1, p2, p3))
    val done = Outcomes.finished(resume(ready, power, atGiant, first.tree,
      WizardsConclave.decisionId, chose(p3)))
    assertEquals(heldBy(done.treeless), Some(p3))

  test("a winner who already holds the Darkest Secret keeps it"):
    val ready = Table.start.adviser(p1, conclave).adviser(p2, "Disciples")
      .adviser(p2, "Glamor").darkestSecret(Some(p2), 1).ready
    val done = Outcomes.finished(play(ready, power, conclave))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("kept",
      s"${p2.value} kept the Darkest Secret.", covers = false)))

  test("when Lost Tongue refuses the take, the Darkest Secret does not move"):
    // p2 holds the Darkest Secret and Lost Tongue faceup; p1, the winner,
    // rules no Nomad card.
    val ready = Table.start.adviser(p1, conclave).adviser(p2, "Lost Tongue")
      .darkestSecret(Some(p2), 1).ready
    val done = Outcomes.finished(playBeside(ready, power, conclave,
      LostTongue.forCatalog(catalog)))
    assertEquals(heldBy(done.treeless), Some(p2))
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "The Darkest Secret did not move.", covers = false)))
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.WizardsConclaveSuite"`
Expected: compile failure, `WizardsConclave` is not found.

- [ ] **Step 4: Write Wizard's Conclave**

Remove `object WizardsConclaveCard` from `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/WizardsConclave.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PowerAnswers, RuledCards}
import oathdigital.model._

object WizardsConclaveCard extends Denizen(DenizenId("208"), "Wizard's Conclave", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.wizard-s-conclave"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** the player who rules the most " +
      "[suit-arcane] cards takes the Darkest Secret. You decide " +
      "ties. _Don't remove secrets from it._")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wizard's Conclave (card 208), WHEN PLAYED: the player who rules the most
  * Arcane cards takes the Darkest Secret. You decide ties. Don't remove
  * secrets from it.
  *
  * Each player's Arcane cards are counted as [[RuledCards]] counts them;
  * when nobody rules one, every player ties. A tie is the actor's question,
  * among the tied players, themselves included. The winner takes the banner
  * by a `Take`, from its holder or, unheld, from the bank, and its secrets
  * stay on it. When the winner already holds it, nothing moves. The take is
  * not required: when Lost Tongue refuses it, it is skipped and the banner
  * stays where it was.
  *
  * The line names the winner, so the tie question is narrated. No generic
  * line exists for a banner's take.
  */
final case class WizardsConclave private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = WizardsConclaveCard.id
  import WizardsConclave._
  def id: PowerId = WizardsConclave.id

  override def noteKeys: Vector[NoteKey] = Vector(took, kept, none)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)
  private val banner = NoteArg.Banner(Banner.DarkestSecret)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      Branch((live, _) => ask(live, actor)),
      Branch((live, pending) => award(live, pending)))

  /** The players ruling the most Arcane cards, in seat order: every player
    * when nobody rules one. */
  private def leaders(ready: ReadyGame): Vector[PlayerId] =
    val counts = ready.game.current.players.map(seat => seat.player ->
      RuledCards.of(catalog, ready, SiteRuler.Player(seat.player),
        Suit.Arcane).size)
    val most = counts.map(_._2).maxOption.getOrElse(0)
    counts.collect { case (player, count) if count == most => player }

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    leaders(ready) match
      case found if found.size > 1 => Vector(Decide(decisionId, actor,
        DecisionQuery.ChooseOne(found.map(player =>
          DecisionOption.Player(DecisionOptionRef.Player(player))),
          heading = Some("Wizard's Conclave: choose which tied player " +
            "takes the Darkest Secret"))))
      case _ => Vector.empty

  /** The one leader, or the leader the actor chose. */
  private def winner(ready: ReadyGame, pending: PendingTree)
      : Option[PlayerId] = leaders(ready) match
    case Vector(only) => Some(only)
    case found => PowerAnswers.one(pending, decisionId).collect {
      case DecisionOptionRef.Player(player) if found.contains(player) =>
        player }

  /** The take and its line, or the line that the winner kept it. */
  private def award(ready: ReadyGame, pending: PendingTree)
      : Vector[Operation] = winner(ready, pending).toVector.flatMap { taker =>
    val holder = BannerRules.holder(ready.game.current, Banner.DarkestSecret)
    if holder.contains(taker) then Vector(Note(id, _ =>
      Some(kept(cardSource, NoteArg.Player(taker), banner))))
    else Vector(
      BuildOps((_, _) => Right(Vector(Take(Piece.Banner(Banner.DarkestSecret),
        taker, holder.fold[Location](Location.SharedBank)(Location.PlayArea(_)),
        Location.PlayArea(taker))))),
      Note(id, takenNote(_, taker)))
  }

  /** Whether the winner holds the banner now, or Lost Tongue kept it. */
  private def takenNote(states: NoteStates, taker: PlayerId)
      : Option[PowerNote] = Some(
    if BannerRules.holder(states.now.game.current, Banner.DarkestSecret)
        .contains(taker)
    then took(cardSource, NoteArg.Player(taker), banner)
    else none(cardSource, banner))

object WizardsConclave:
  val id: PowerId = WizardsConclaveCard.power.id
  val decisionId: String = "cardplay.wizards-conclave.winner"
  /** "{Blue} took the {Darkest Secret}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Arg(0),
    NotePart.Text(" took the "), NotePart.Arg(1), NotePart.Text(".")))
  /** "{Blue} kept the {Darkest Secret}." */
  val kept: NoteKey = NoteKey("kept", Vector(NotePart.Arg(0),
    NotePart.Text(" kept the "), NotePart.Arg(1), NotePart.Text(".")))
  /** "The {Darkest Secret} did not move." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Text("The "),
    NotePart.Arg(0), NotePart.Text(" did not move.")))

  def forCatalog(catalog: ExecutableCatalog): WizardsConclave =
    new WizardsConclave(catalog)
```

- [ ] **Step 5: Register it**

Register `Vector(WizardsConclave.forCatalog(catalog))` in `WhenPlayedPowers.scala`. In `ActionPowers.scala`, change `WizardsConclave`'s `played` to `playedDone`. In `NewFoundations.scala`, add `WizardsConclaveCard` to the end of the `gameplay.powers.whenplayed` import, after `TwinBrotherCard`.

- [ ] **Step 6: Run the suite and the suites that use it as filler**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.WizardsConclaveSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.application.gamelog.GameLogActionLineSuite oathdigital.application.gamelog.GameLogDetailSuite oathdigital.application.GameApplicationServiceActionsSuite oathdigital.application.GameApplicationServiceReopenSuite oathdigital.application.GameApplicationServiceProjectionSuite oathdigital.application.PresentationLabelsSuite oathdigital.server.GameRoutesSuite oathdigital.server.AuthenticatedGameRoutesSuite oathdigital.gameplay.powers.action.TavernSongsSuite"`
Expected: PASS, 5 tests in `WizardsConclaveSuite`. If a filler suite fails, read its assertion before changing anything: plan-time ruling 10 expects each to pass unchanged, so a failure means the ruling missed something. Report it rather than editing the filler.

- [ ] **Step 7: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WizardsConclave.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/WizardsConclaveSuite.scala
git commit -m "feat(whenplayed): Wizard's Conclave gives the Darkest Secret to whoever rules the most Arcane cards"
```

---

### Task 5: Bandit Chief

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditChief.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `BanditChiefCard`; drop `SiteOnly` from its import)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `BanditChiefCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditChiefSuite.scala`

**Interfaces:**
- Consumes: `PlayerFacts.forceKind`, `SearchFixture`.
- Produces: `case object BanditChief` with the keys `killed`, `none`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditChiefSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.{p1, p2}

class BanditChiefSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[BanditChief.type]
  private val chief = power.cardId
  private val atHome = hookAt(chief, homeSite)
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("one warband dies at every site not ruled by bandits, p1's own " +
      "included, with one line per site in map order"):
    // Bandit Chief stands at p1's site, where p1 has 2 warbands. p2 has 1
    // at Broken Peaks, and bandits hold Buried Giant.
    val ready = Table.start.denizen(chief, at = homeSite)
      .warbandsAt(homeSite, p1, 2).warbandsAt(peaks, p2, 1)
      .bandits(giant, 2).ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    val end = Look(done.treeless)
    assertEquals(end.forces(homeSite),
      SiteForces.Occupied(end.forceKind(p1), 1))
    assertEquals(end.forces(peaks), SiteForces.Empty)
    assertEquals(end.forces(giant), SiteForces.Occupied(ForceKind.Bandit, 2))
    val mapOrder = ready.game.current.map.inPlay
    assertEquals(said(done.events), Vector(homeSite -> p1, peaks -> p2)
      .sortBy(entry => mapOrder.indexOf(entry._1))
      .map((site, owner) => NoteText.Said("killed",
        s"Killed 1 ${owner.value} warband at ${site.value}.",
        covers = false)))
    assertEquals(replayed(ready, done.events), done.treeless)

  test("with only bandits and empty sites nothing dies, and the line says " +
      "so"):
    val ready = Table.start.denizen(chief, at = homeSite).bandits(giant, 2)
      .ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No warband was killed.", covers = false)))

  test("played from a Search, a site the kill empties is refilled with " +
      "bandits"):
    val ready = Table.from(SearchFixture.staged(Vector(chief), supply = 5))
      .warbandsAt(peaks, p2, 1).ready
    val placed = SearchFixture.play(ready, Vector.empty, chief,
      PlayedAs.AtSite)
    val capacity = catalog.site(peaks).fold(0)(_.capacity)
    assertEquals(Look(SearchFixture.after(placed)).forces(peaks),
      SiteForces.Occupied(ForceKind.Bandit, capacity))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.BanditChiefSuite"`
Expected: compile failure, `BanditChief` is not found.

- [ ] **Step 3: Write Bandit Chief**

Remove `object BanditChiefCard` from `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`. It was that file's last site-only card, so change the file's catalog import from:

```scala
import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower,
  SiteOnly}
```

to:

```scala
import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
```

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditChief.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

object BanditChiefCard extends Denizen(DenizenId("100"), "Bandit Chief", Suit.Discord) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.bandit-chief"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** kill one warband at each site not ruled by " +
      "the bandits.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Bandit Chief (card 100, site-only), WHEN PLAYED: kill one warband at each
  * site not ruled by the bandits.
  *
  * The sites are read live, in map order: every site in play whose warbands
  * are not bandits, the actor's own included. Each loses one warband by a
  * `Kill`, in a step of its own, with a line naming whose warband died. The
  * refill after the command puts bandits on a site the kill empties. Nothing
  * is asked. With no such site, one line says so.
  *
  * No generic line exists for a kill, so the lines cover nothing.
  */
case object BanditChief extends WhenPlayedPower:
  val cardId: DenizenId = BanditChiefCard.id
  val id: PowerId = BanditChiefCard.power.id
  /** "Killed {n} {Blue} warband at {site}." */
  val killed: NoteKey = NoteKey("killed", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "No warband was killed." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No warband was killed.")))

  override def noteKeys: Vector[NoteKey] = Vector(killed, none)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(Branch((live, _) => killAll(live)))

  /** The sites in play held by warbands other than bandits, in map order,
    * with the kind that holds each. */
  private def targets(ready: ReadyGame): Vector[(SiteId, ForceKind)] =
    val map = ready.game.current.map
    map.inPlay.flatMap(site => map.sites.get(site).map(_.forces).collect {
      case SiteForces.Occupied(kind, count)
          if kind != ForceKind.Bandit && count > 0 => site -> kind })

  private def killAll(ready: ReadyGame): Vector[Operation] =
    targets(ready) match
      case Vector() => Vector(Note(id, _ => Some(none(cardSource))))
      case found => found.flatMap { (site, kind) => Vector(
        BuildOps((_, _) => Right(Vector(Kill(Piece.Warbands(kind, 1),
          PositionedLocation(Location.Site(site)))))),
        Note(id, killedNote(_, site, kind))) }

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The warbands of `kind` the step before killed at `site`, and whose. */
  private def killedNote(states: NoteStates, site: SiteId, kind: ForceKind)
      : Option[PowerNote] = for
    (before, after) <- states.previous
    lost = held(before, site, kind) - held(after, site, kind)
    if lost > 0
    owner <- after.game.current.players.find(seat =>
      PlayerFacts.forceKind(after, seat.player).toOption.contains(kind))
  yield killed(cardSource, NoteArg.Number(lost), NoteArg.Player(owner.player),
    NoteArg.Site(site))
```

- [ ] **Step 4: Register it**

Register `Vector(BanditChief)` in `WhenPlayedPowers.scala`. In `ActionPowers.scala`, change `BanditChief`'s `played` to `playedDone`. In `NewFoundations.scala`, add `BanditChiefCard` to the `gameplay.powers.whenplayed` import, after `AnimalHostCard`.

- [ ] **Step 5: Run the suite and the suites that use it as filler**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.BanditChiefSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.PendingWalkerRulesSuite oathdigital.gameplay.ForgeProcedureSuite oathdigital.gameplay.powers.action.ScryerSuite oathdigital.gameplay.powers.rest.LeagueTreatySuite oathdigital.application.PhasePowerProjectorSuite"`
Expected: PASS, 3 tests in `BanditChiefSuite`. The filler suites hold Bandit Chief at a site or in a pile and never play it.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/whenplayed/BanditChief.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/BanditChiefSuite.scala
git commit -m "feat(whenplayed): Bandit Chief kills one warband at every site the bandits do not rule"
```

---

### Task 6: The Rest cleanup, and A Round of Ale

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/phases/RestCleanup.scala` (add `object RestCleanup`)
- Modify: `src/main/scala/oathdigital/gameplay/phases/rest/FinishRestProcedure.scala` (run the cleanup through it)
- Modify: `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ARoundOfAle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `ARoundOfAleCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `ARoundOfAleCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/whenplayed/ARoundOfAleSuite.scala`

**Interfaces:**
- Consumes: `RestCleanupPlan.derive`, `LeagueTreatyFixture.leagueTreatyAct`.
- Produces:
  - `RestCleanup.operations(catalog: ExecutableCatalog, ready: ReadyGame, resting: PlayerId): Either[OathViolation, Vector[CoreOperation]]`.
  - `WhenPlayedHarness.fireWithEveryPower(ready, hook)`.
  - `final case class ARoundOfAle` with `ARoundOfAle.forCatalog(catalog)` and the key `cleanup`.

- [ ] **Step 1: Extract the cleanup**

In `src/main/scala/oathdigital/gameplay/phases/RestCleanup.scala`, append:

```scala

/** The Rest cleanup's operations: favor on every card in play to its
  * printed suit bank, secrets on cards to the resting player, and the
  * resting player's facedown secrets flipped faceup -- the operations legacy
  * `Rest.applyCompletion` ran. Finish Rest runs them at the Rest favor
  * window; A Round of Ale runs them in an action, outside that window.
  */
object RestCleanup:
  def operations(catalog: ExecutableCatalog, ready: ReadyGame,
      resting: PlayerId): Either[OathViolation, Vector[CoreOperation]] = for
    plan <- RestCleanupPlan.derive(catalog, ready, resting)
      .left.map(OathViolation.UnsupportedRestState.apply)
    player <- ready.game.current.players.find(_.player == resting)
      .toRight(OathViolation.UnsupportedRestState(
        s"unknown resting player $resting"))
  yield
    val from = (id: CardId) => PositionedLocation(Location.OnCard(id))
    val favor = plan.cards.collect:
      case card if card.suit.nonEmpty && card.favor > 0 =>
        Move(Piece.Favor(card.favor), from(card.id),
          PositionedLocation(Location.FavorBank(card.suit.get)))
    val secrets = plan.cards.collect:
      case card if card.secrets > 0 => Move(Piece.Secrets(card.secrets),
        from(card.id), PositionedLocation(Location.PlayArea(resting)))
    val reveal = Vector(player.board.faceDownSecrets).filter(_ > 0).map(count =>
      FlipSecrets(resting, count, SecretSide.FaceDown, SecretSide.FaceUp))
    favor ++ secrets ++ reveal
```

The file already imports `ExecutableCatalog` and `oathdigital.model._`.

In `src/main/scala/oathdigital/gameplay/phases/rest/FinishRestProcedure.scala`:

- Change `import oathdigital.gameplay.phases.RestCleanupPlan` to `import oathdigital.gameplay.phases.RestCleanup`.
- In `build`, change `BuildOps((state, _) => cleanup(catalog, state, player),` to `BuildOps((state, _) => RestCleanup.operations(catalog, state, player),`.
- Delete the private `cleanup` method and its doc comment ("Card favor to its printed suit bank, ...").

Run: `./sbtw "testOnly oathdigital.gameplay.RestSuite oathdigital.gameplay.PendingWalkerRulesSuite oathdigital.gameplay.StateBasedEvaluationSuite oathdigital.gameplay.powers.rest.*"`
Expected: PASS. The extraction changes no behavior, so no test changes.

- [ ] **Step 2: Let the harness walk with every production power**

In `src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala`, add after `fire`:

```scala
  /** Walks `hook` with every production walker power registered, as a real
    * card play would: for a test that pins what another power does not do. */
  def fireWithEveryPower(ready: ReadyGame, hook: CardPlayedFaceup)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook, None,
      WalkerPowerCatalog.default(catalog))
```

- [ ] **Step 3: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/ARoundOfAleSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.rest.LeagueTreatyFixture.leagueTreatyAct
import oathdigital.gameplay.walker.{PowerNoted, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class ARoundOfAleSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[ARoundOfAle]
  private val ale = power.cardId
  private val atHome = hookAt(ale, homeSite)
  private val peaks = CatalogNames.site("Broken Peaks")
  private val cleanupLine = NoteText.Said("cleanup",
    "Triggering resource cleanup.", covers = false)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("favor on every card returns to its bank, card secrets go to p1, and " +
      "p1's facedown secrets turn faceup, after its line"):
    // Homesteaders (Hearth), p2's adviser, holds 2 favor. Defame (Discord)
    // at Broken Peaks holds 1 favor and 1 secret. p3's Painted Trumpet holds
    // 1 secret. p1 holds 1 faceup and 2 facedown secrets, p2 1 facedown.
    val ready = Table.start.denizen(ale, at = homeSite)
      .adviser(p2, "Homesteaders").tokens("Homesteaders", favor = 2)
      .denizen("Defame", at = peaks)
      .tokens("Defame", favor = 1, secrets = 1)
      .relic(p3, "Painted Trumpet").tokens("Painted Trumpet", secrets = 1)
      .secrets(p1, faceUp = 1, faceDown = 2)
      .secrets(p2, faceUp = 0, faceDown = 1).ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    val (before, end) = (Look(ready), Look(done.treeless))
    assertEquals(end.bankFavor(Suit.Hearth), before.bankFavor(Suit.Hearth) + 2)
    assertEquals(end.bankFavor(Suit.Discord),
      before.bankFavor(Suit.Discord) + 1)
    Vector("Homesteaders", "Defame", "Painted Trumpet").foreach(card =>
      assertEquals(end.tokensOn(card), Tokens.empty, card))
    // 1 faceup, 2 turned faceup, and 1 each from Defame and the Trumpet.
    assertEquals((end.faceUpSecrets(p1), end.faceDownSecrets(p1)), (5, 0))
    assertEquals((end.faceUpSecrets(p2), end.faceDownSecrets(p2)), (0, 1))
    assertEquals(said(done.events), Vector(cleanupLine))
    // The cleanup is the walk's last step, and the line comes before it.
    val notedAt = done.events.indexWhere(_.isInstanceOf[PowerNoted])
    val cleanedAt =
      done.events.lastIndexWhere(_.isInstanceOf[WalkerStepRecorded])
    assert(notedAt >= 0 && notedAt < cleanedAt, done.events.toString)
    assertEquals(replayed(ready, done.events), done.treeless)

  test("League Treaty does not redirect the favor, since no Rest favor " +
      "window opens"):
    // League Treaty at Ancient City, ruled by p2, would ask p2 where the
    // Cradle's card favor goes at Rest.
    val ready = leagueTreatyAct(Some(p2), ("Defame", "Broken Peaks", 2),
      ("Homesteaders", "Ancient City", 2)).denizen(ale, at = peaks).ready
    val done = Outcomes.finished(fireWithEveryPower(ready,
      hookAt(ale, peaks)))
    Vector(Suit.Discord, Suit.Hearth).foreach(suit => assertEquals(
      Look(done.treeless).bankFavor(suit), Look(ready).bankFavor(suit) + 2,
      suit.toString))

  test("with nothing to clean up it writes its line and changes nothing"):
    val ready = Table.start.denizen(ale, at = homeSite).ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(cleanupLine))
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.ARoundOfAleSuite"`
Expected: compile failure, `ARoundOfAle` is not found.

- [ ] **Step 5: Write A Round of Ale**

Remove `object ARoundOfAleCard` from `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala`. Hearts and Minds still uses `SiteOnly` there, so its import stays.

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/ARoundOfAle.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.phases.RestCleanup
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

object ARoundOfAleCard extends Denizen(DenizenId("129"), "A Round of Ale", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.a-round-of-ale"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** return all [favor] and [secret], and flip " +
      "your [secret], as in the Rest Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

/** A Round of Ale (card 129, site-only), WHEN PLAYED: return all favor and
  * secrets, and flip your secrets, as in the Rest Phase.
  *
  * It runs the Rest cleanup ([[RestCleanup]]) with the actor as the resting
  * player, in one step: favor on every card in play to its suit's bank,
  * secrets on cards to the actor, and the actor's facedown secrets flipped
  * faceup. The step declares no Rest favor window, so League Treaty does not
  * redirect the favor. Cards cleared of tokens may be used again; powers
  * used this turn stay used. Its line comes first, and the cleanup writes
  * no generic line.
  */
final case class ARoundOfAle private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = ARoundOfAleCard.id
  import ARoundOfAle._
  def id: PowerId = ARoundOfAle.id

  override def noteKeys: Vector[NoteKey] = Vector(cleanup)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      Note(id, _ => Some(cleanup(PowerSourceRef.Card(cardId)))),
      BuildOps((ready, _) => RestCleanup.operations(catalog, ready, actor)))

object ARoundOfAle:
  val id: PowerId = ARoundOfAleCard.power.id
  /** "Triggering resource cleanup." */
  val cleanup: NoteKey = NoteKey("cleanup", Vector(
    NotePart.Text("Triggering resource cleanup.")))

  def forCatalog(catalog: ExecutableCatalog): ARoundOfAle =
    new ARoundOfAle(catalog)
```

- [ ] **Step 6: Register it**

Register `Vector(ARoundOfAle.forCatalog(catalog))` in `WhenPlayedPowers.scala`. In `ActionPowers.scala`, change `ARoundOfAle`'s `played` to `playedDone`. In `NewFoundations.scala`, add `ARoundOfAleCard` to the start of the `gameplay.powers.whenplayed` import, before `ASmallFavorCard`.

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.ARoundOfAleSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 3 tests in `ARoundOfAleSuite`.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/phases/RestCleanup.scala \
  src/main/scala/oathdigital/gameplay/phases/rest/FinishRestProcedure.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedHarness.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/ARoundOfAle.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/ARoundOfAleSuite.scala
git commit -m "feat(whenplayed): A Round of Ale runs the Rest cleanup for its player"
```

---

### Task 7: `PowerAnswers.amounts` and Call for Help

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala` (add `amounts`)
- Modify: `src/test/scala/oathdigital/gameplay/powers/PowerAnswersSuite.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/whenplayed/CallForHelp.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `CallForHelpCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `CallForHelpCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/whenplayed/CallForHelpSuite.scala`

**Interfaces:**
- Consumes: `PowerAnswers.last` (slice 3), `CardPlay.nextRegion`, `PowerAccess.pawnSite`, `PlayerFacts.forceKind`, `PlayerFacts.banked`, `NoteSupport.warbands`, Task 1's `walkAnswering`.
- Produces:
  - `PowerAnswers.amounts(answered: Vector[Answered], decision: String): Vector[Int]`.
  - `final case class CallForHelp` with `CallForHelp.forCatalog(catalog)`, `amountDecisionId = "cardplay.call-for-help.amount"`, `continueDecisionId = "cardplay.call-for-help.continue"`, `Most = 6`, `PerNomad = 2`, the buttons `keep` and `stop`, and the keys `revealed`, `discarded`, `none`.

- [ ] **Step 1: Write the failing `amounts` test**

In `src/test/scala/oathdigital/gameplay/powers/PowerAnswersSuite.scala`, append:

```scala

  test("amounts reads every answer to an amount a loop asks again, in " +
      "order"):
    def amount(decision: String, count: Int): Answered =
      Answered(decision, DecisionAnswer.ChooseAmountAnswer(count), p1)
    val answered = Vector(amount("a", 3), chose("b", "keep"), amount("a", 1))
    assertEquals(PowerAnswers.amounts(answered, "a"), Vector(3, 1))
    assertEquals(PowerAnswers.amounts(answered, "c"), Vector.empty)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerAnswersSuite"`
Expected: compile failure, `amounts` is not a member of `PowerAnswers`.

- [ ] **Step 3: Add `amounts`**

In `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala`, add after `amount`:

```scala
  /** Every amount `decision` was answered with, in answer order. A `Repeat`
    * asks its decision once per pass and `amount` reads only the first, so
    * a loop reads its passes with this: the latest is the last, and the sum
    * is what every pass chose in all. */
  def amounts(answered: Vector[Answered], decision: String): Vector[Int] =
    answered.collect {
      case Answered(`decision`, DecisionAnswer.ChooseAmountAnswer(count), _) =>
        count }
```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerAnswersSuite"`
Expected: PASS, 3 tests.

- [ ] **Step 4: Write the failing Call for Help suite**

Create `src/test/scala/oathdigital/gameplay/powers/whenplayed/CallForHelpSuite.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}

class CallForHelpSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[CallForHelp]
  private val help = power.cardId
  private val played = hook(help)
  /** p1's pawn stands in the Cradle. */
  private val cradle = Region.Cradle
  private val next = CardPlay.nextRegion(cradle)

  /** Call for Help as p1's adviser, with `cards` in the Cradle discard pile,
    * the last named on top. */
  private def cradlePile(cards: String*): Table =
    Table.start.adviser(p1, help).discarded(cradle, cards*)

  private def amount(count: Int): DecisionAnswer =
    DecisionAnswer.ChooseAmountAnswer(count)

  private def chose(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  private def named(cards: String*): Vector[WorldCardId] =
    cards.toVector.map(CatalogNames.worldCard(_))

  private def pileOf(ready: ReadyGame, region: Region): Vector[WorldCardId] =
    ready.game.current.commonCards.discard(region)

  private def knows(ready: ReadyGame, viewer: PlayerId, card: WorldCardId)
      : Boolean =
    ready.knowledge.advisers.getOrElse(viewer, Vector.empty).contains(card)

  private def mostAsked(walked: Walked): Vector[Int] =
    walked.asked.map(_.query).collect {
      case query: DecisionQuery.ChooseAmount => query.max }

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private val nothingLine = NoteText.Said("none", "Nothing was revealed.",
    covers = false)

  test("draws off the top one at a time to the next region's pile, first " +
      "drawn deepest, shows each to every player, and gains 2 warbands per " +
      "Nomad card"):
    // Wild Mounts and Mountain Giant are Nomad; Mountain Giant is on top.
    val ready = cradlePile("Old Songs", "Spinning Bee", "Wild Mounts",
      "Mountain Giant").ready
    val walked = walkAnswering(ready, power, played, amount(3),
      chose(CallForHelp.stop))
    val drawn = named("Mountain Giant", "Wild Mounts", "Spinning Bee")
    assertEquals(mostAsked(walked), Vector(4))
    // A pile stores its top last, so the first drawn is deepest.
    assertEquals(pileOf(walked.end, next), drawn)
    assertEquals(pileOf(walked.end, cradle), named("Old Songs"))
    // The quiet table's 3 warbands, and 2 for each of the two Nomad cards.
    assertEquals(Look(walked.end).boardWarbands(p1), 3 + 4)
    for card <- drawn; viewer <- Vector(p1, p2, p3) do
      assert(knows(walked.end, viewer, card), s"$viewer does not know $card")
    assertEquals(said(walked.events), Vector(NoteText.Said("revealed",
      s"Revealed and discarded ${drawn.map(_.value).mkString(", ")}, and " +
        "gained 4 warbands.", covers = true)))
    assertEquals(replayed(ready, walked.events), walked.end)
    assertWireRoundTrips(walked.events)

  test("keeping on drawing asks again, for no more than what is left of six"):
    val ready = cradlePile("Old Songs", "Spinning Bee", "Deed Writer",
      "Land Warden", "Defame", "Second Wind", "Zealots", "Unstable Summon")
      .ready
    val walked = walkAnswering(ready, power, played, amount(5),
      chose(CallForHelp.keep), amount(1))
    // Six drawn, so no third question.
    assertEquals(mostAsked(walked), Vector(6, 1))
    assertEquals(pileOf(walked.end, cradle), named("Old Songs",
      "Spinning Bee"))
    assertEquals(said(walked.events).map(_.key),
      Vector("discarded", "discarded"))
    assertEquals(Look(walked.end).boardWarbands(p1), 3)

  test("the warbands gained stop at what p1's bank holds"):
    val ready = cradlePile("Wild Mounts").warbandsInBank(p1, 1).ready
    val walked = walkAnswering(ready, power, played, amount(1))
    assertEquals(Look(walked.end).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 1)
    assertEquals(said(walked.events), Vector(NoteText.Said("revealed",
      s"Revealed and discarded ${named("Wild Mounts").head.value}, and " +
        "gained 1 warband.", covers = true)))

  test("an amount of 0 reveals nothing and asks nothing more"):
    val ready = cradlePile("Old Songs", "Spinning Bee").ready
    val walked = walkAnswering(ready, power, played, amount(0))
    assertEquals(PhasePowerHarness.ops(walked.events), Vector.empty)
    assertEquals(pileOf(walked.end, cradle), named("Old Songs",
      "Spinning Bee"))
    assertEquals(said(walked.events), Vector(nothingLine))

  test("with the region's pile empty nothing is asked, and the line says so"):
    val done = Outcomes.finished(play(Table.start.adviser(p1, help).ready,
      power, help))
    assertEquals(said(done.events), Vector(nothingLine))
```

- [ ] **Step 5: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.CallForHelpSuite"`
Expected: compile failure, `CallForHelp` is not found.

- [ ] **Step 6: Write Call for Help**

Remove `object CallForHelpCard` from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/whenplayed/CallForHelp.scala`:

```scala
package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

object CallForHelpCard extends Denizen(DenizenId("246"), "Call for Help", Suit.Nomad) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.call-for-help"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** reveal and discard up to 6 cards from your " +
      "region's discard pile. Gain 2 warbands for each revealed " +
      "[suit-nomad] card.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Call for Help (card 246, adviser-only, locked), WHEN PLAYED: reveal and
  * discard up to 6 cards from your region's discard pile. Gain 2 warbands
  * for each revealed Nomad card.
  *
  * Your region is the pawn's. The actor draws in passes of a `Repeat`. Each
  * pass asks for an amount from 0 to the smaller of what is left of 6 and
  * the pile's size, then moves that many cards off the top, one at a time,
  * each to the top of the next region's pile, so the first drawn ends
  * deepest. Each card is revealed there, so every player knows it: a card
  * entering a pile is forgotten, so the reveal follows the move. The actor
  * gains 2 warbands per Nomad card drawn, up to their bank, in the same
  * step. While fewer than 6 are drawn, the pile is not empty and the pass's
  * amount was not 0, the actor chooses "Keep drawing", which runs another
  * pass, or "Stop".
  *
  * One line per draw covers that draw's Revealed lines. With nothing drawn,
  * one line says so.
  */
final case class CallForHelp private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = CallForHelpCard.id
  import CallForHelp._
  def id: PowerId = CallForHelp.id

  override def noteKeys: Vector[NoteKey] = Vector(revealed, discarded, none)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      Repeat((_, pending) => drawing(pending.answered),
        Sequence(Vector[Operation](
          Branch((live, pending) => askAmount(live, actor, pending)),
          BuildOps((live, pending) => Right(draw(live, actor, pending))),
          Note(id, drawnNote(_, actor), covers = true),
          Branch((live, pending) => askContinue(live, actor, pending))))),
      Note(id, nothingNote))

  /** Each pass's amount, in order. */
  private def amounts(answered: Vector[Answered]): Vector[Int] =
    PowerAnswers.amounts(answered, amountDecisionId)

  /** Whether another pass runs: none has yet, or the actor chose to keep
    * drawing after the latest one. */
  private def drawing(answered: Vector[Answered]): Boolean =
    val passes = amounts(answered).size
    passes == 0 ||
      (answered.count(_.decisionId == continueDecisionId) == passes &&
        PowerAnswers.last(answered, continueDecisionId).contains(keep))

  private def home(ready: ReadyGame, actor: PlayerId): Option[Region] =
    PowerAccess.pawnSite(ready, actor).flatMap(ready.game.current.map.regionOf)

  private def pile(ready: ReadyGame, actor: PlayerId): Vector[WorldCardId] =
    home(ready, actor).fold(Vector.empty[WorldCardId])(
      ready.game.current.commonCards.discard)

  private def askAmount(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    val most = math.min(Most - amounts(pending.answered).sum,
      pile(ready, actor).size)
    if most <= 0 then Vector.empty
    else Vector(Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(0,
      most, Some("Call for Help: reveal and discard cards from your " +
        "region's discard pile"), "Reveal and discard")))

  /** The pass's cards off the top of the pile, top first, each moved to the
    * top of the next region's pile and revealed there, and the warbands
    * their Nomad cards give. Nothing for an amount of 0. */
  private def draw(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[CoreOperation] =
    amounts(pending.answered).lastOption.filter(_ > 0)
      .zip(home(ready, actor)).toVector.flatMap { (amount, region) =>
        val next = CardPlay.nextRegion(region)
        val drawn = ready.game.current.commonCards.discard(region)
          .takeRight(amount).reverse
        drawn.flatMap(card => Vector[CoreOperation](
          Move(Piece.Card(card),
            PositionedLocation(Location.RegionalDiscard(region)),
            PositionedLocation(Location.RegionalDiscard(next),
              StackPosition.Top)),
          Reveal(card, Location.RegionalDiscard(next)))) ++
          gain(ready, actor, drawn)
      }

  /** 2 warbands per Nomad card drawn, up to the actor's bank. */
  private def gain(ready: ReadyGame, actor: PlayerId,
      drawn: Vector[WorldCardId]): Vector[CoreOperation] =
    val earned = PerNomad *
      drawn.count(card => catalog.suitOf(card).contains(Suit.Nomad))
    PlayerFacts.forceKind(ready, actor).toOption.toVector.flatMap { kind =>
      val amount = math.min(earned, PlayerFacts.banked(ready, kind))
      Option.when(amount > 0)(Gain.Warbands(actor, kind, amount)) }

  /** "Keep drawing" or "Stop", while fewer than 6 are drawn, the pile holds
    * cards and the pass drew some. */
  private def askContinue(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    val drawn = amounts(pending.answered)
    if drawn.lastOption.forall(_ == 0) || drawn.sum >= Most ||
        pile(ready, actor).isEmpty then Vector.empty
    else Vector(Decide(continueDecisionId, actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(keep, "Keep drawing"),
        DecisionOption.Button(stop, "Stop")),
      heading = Some(s"Call for Help: ${drawn.sum} of $Most drawn"))))

  /** The cards the step before drew, read from the pile before it, and the
    * warbands it gave. */
  private def drawnNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    amount <- amounts(states.answered).lastOption
    if amount > 0
    step <- states.previous
    region <- home(step._1, actor)
  yield
    val cards = NoteArg.Cards(step._1.game.current.commonCards
      .discard(region).takeRight(amount).reverse)
    val gained = NoteSupport.warbands(step, actor)
    if gained > 0 then revealed(cardSource, cards,
      NoteArg.Amount(gained, NoteUnit.Warband))
    else discarded(cardSource, cards)

  /** "Nothing was revealed." when no pass drew a card. */
  private def nothingNote(states: NoteStates): Option[PowerNote] =
    Option.when(amounts(states.answered).sum == 0)(none(cardSource))

object CallForHelp:
  val id: PowerId = CallForHelpCard.power.id
  val amountDecisionId: String = "cardplay.call-for-help.amount"
  val continueDecisionId: String = "cardplay.call-for-help.continue"
  /** The most cards drawn in all. */
  val Most: Int = 6
  /** The warbands each Nomad card drawn gives. */
  val PerNomad: Int = 2
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep-drawing")
  val stop: DecisionOptionRef.Button = DecisionOptionRef.Button("stop")
  /** "Revealed and discarded {cards}, and gained {n warbands}." */
  val revealed: NoteKey = NoteKey("revealed", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text(", and gained "), NotePart.Arg(1), NotePart.Text(".")))
  /** "Revealed and discarded {cards}." */
  val discarded: NoteKey = NoteKey("discarded", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text(".")))
  /** "Nothing was revealed." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Nothing was revealed.")))

  def forCatalog(catalog: ExecutableCatalog): CallForHelp =
    new CallForHelp(catalog)
```

- [ ] **Step 7: Register it**

Register `Vector(CallForHelp.forCatalog(catalog))` in `WhenPlayedPowers.scala`. In `ActionPowers.scala`, change `CallForHelp`'s `played` to `playedDone`. In `NewFoundations.scala`, add `CallForHelpCard` to the `gameplay.powers.whenplayed` import, after `BlackmailCard`.

- [ ] **Step 8: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.whenplayed.CallForHelpSuite oathdigital.gameplay.powers.PowerAnswersSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 5 tests in `CallForHelpSuite`.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala \
  src/test/scala/oathdigital/gameplay/powers/PowerAnswersSuite.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/CallForHelp.scala \
  src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/whenplayed/CallForHelpSuite.scala
git commit -m "feat(whenplayed): Call for Help reveals and discards the region's pile for warbands"
```

---

### Task 8: Gates and docs

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`

- [ ] **Step 1: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. The server count is the baseline plus 27; the frontend count is unchanged.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

If a suite outside this slice fails, it most likely holds one of these cards as filler (plan-time ruling 10). Read the failing assertion. Change the filler to another card only if the test is about something else, and say so in the commit message.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:

- In the status paragraph, change "slices\n1a, 1b, 2 and 3 are done." to "slices\n1a, 1b, 2, 3 and 4 are done." Keep the existing line wrap.
- Delete the bullet that starts "- [ ] 4. When played:" and its continuation line.

- [ ] **Step 3: Make the spec's slice 4 log table exact**

In `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`, under "### Slice 4: when played", replace the table (its header row through the row "| Call for Help, key `none` | ...") with:

```markdown
| Card | Line | Covers |
|---|---|---|
| Revelation, key `burned`, one line per player who burned | Revelation: {Blue} burned {n favor} and gained {n secrets}. | the Gain line |
| Revelation, key `declined`, one line per player who was asked and burned none | Revelation: {Blue} did not burn any favor. | |
| Revelation, key `none` | Revelation: Nobody had favor to burn. | |
| Blackmail, key `paid` | Blackmail: {Blue} gave {3 favor} to keep {relic}. | |
| Blackmail, key `took` | Blackmail: Took {relic} from {Blue}. | |
| Blackmail, key `none` | Blackmail: No relic could be taken. | |
| Dissent, key `placed`, one line per player who placed | Dissent: {Blue} placed {n favor} on it. | |
| Dissent, key `none` | Dissent: Nobody placed favor. | |
| Wizard's Conclave, key `took` | Wizard's Conclave: {Blue} took the {Darkest Secret}. | |
| Wizard's Conclave, key `kept` | Wizard's Conclave: {Blue} kept the {Darkest Secret}. | |
| Wizard's Conclave, key `none` | Wizard's Conclave: The {Darkest Secret} did not move. | |
| Bandit Chief, key `killed`, one line per site | Bandit Chief: Killed {n} {Blue} warband at {site}. | |
| Bandit Chief, key `none` | Bandit Chief: No warband was killed. | |
| A Round of Ale, key `cleanup` | A Round of Ale: Triggering resource cleanup. | |
| Call for Help, key `revealed`, one line per draw that gained warbands | Call for Help: Revealed and discarded {cards}, and gained {n warbands}. | that draw's Revealed lines |
| Call for Help, key `discarded`, one line per draw that gained none | Call for Help: Revealed and discarded {cards}. | that draw's Revealed lines |
| Call for Help, key `none` | Call for Help: Nothing was revealed. | |
```

Then, in the paragraph below the table, replace "A Round of Ale's line\ncomes before the cleanup, whose own lines follow unchanged." (however it wraps) with "A Round of Ale's line comes before the cleanup, which writes no generic line. Bandit Chief names whose warband died, the actor's included." Rewrap the paragraph within 80 columns. Then add this paragraph after it:

```markdown
No generic line exists for a favor burn, favor moving between boards or
onto a card, a relic's or a banner's take, a kill, or a card moving between
discard piles, so those rows cover nothing. Call for Help reveals each card
in its new pile, which writes one Revealed line per card; a draw's line
covers them. A draw that gains no warband uses its own key rather than
saying it gained none.
```

- [ ] **Step 4: Record the slice's implementation notes in the spec**

In the same file's "Implementation notes from the verification" list, add after the "**Inquisitor's right guess**" bullet:

```markdown
- **Questions another player owns.** Revelation's burns and Blackmail's
  holder answer belong to that player, and the walker refuses an answer
  from anyone else. The walker resumes at the parked node, expanding each
  `Branch` on the way with the live state, so Revelation gives every
  player the same place in its tree whether or not they are asked.
- **Blackmail's relics.** A faceup relic is offered as itself and a
  facedown one by its slot. A `RelicSlot` option always reads "{owner}
  facedown relic", so a faceup relic offered by slot would read wrongly.
- **Wizard's Conclave's take** is not required, so Lost Tongue's refusal
  skips it, and the line says the Darkest Secret did not move.
- **Call for Help's draws** are passes of a `Repeat`, each read with
  `PowerAnswers.amounts`. An amount of 0 ends the drawing.
```

In the "**Test filler.**" bullet of the same list, change "move to a Citizen-related one, since those are implemented last." to "move to a Citizen-related one, Bewitch, since those are implemented last." Rewrap the bullet within 80 columns.

- [ ] **Step 5: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md
git commit -m "docs: record catalog batch 4 slice 4"
```
