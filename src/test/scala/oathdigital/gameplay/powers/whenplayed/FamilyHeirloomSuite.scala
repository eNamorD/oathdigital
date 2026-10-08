package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.gameplay.walker.WalkerOutcome
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{as, rejected}

class FamilyHeirloomSuite extends munit.FunSuite
    with WalkerRecordedOpsReducer:
  import WhenPlayedHarness._

  private val power = FamilyHeirloom
  private val card = power.cardId
  private val played = hook(card)
  private val top = CatalogNames.relic("Dowsing Sticks")

  /** p1 holds Family Heirloom faceup, and Dowsing Sticks tops the relic
    * deck. */
  private val staged = Table.start.adviser(p1, card).relicDeckTop(top).ready

  private def answer(ready: ReadyGame, tree: PendingTree,
      ref: DecisionOptionRef) =
    resume(ready, power, played, tree, FamilyHeirloom.decisionId,
      DecisionAnswer.ChooseOneAnswer(ref))

  /** The walk parked on the keep-or-bottom choice, and the state the draw
    * reached. */
  private final case class AtChoice(first: WalkerOutcome.Parked,
      state: ReadyGame)

  private def atChoice: AtChoice =
    val first = Outcomes.parked(play(staged, power, card))
    val drawn = foldRecordedOps(staged, first.events, "the draw did not replay")
    AtChoice(first, drawn)

  test("the relic is drawn facedown to the player and the choice is theirs"):
    val at = atChoice
    assert(Look(at.state).player(p1).relics.exists(relic =>
      relic.id == top && relic.orientation == Orientation.FaceDown))
    assert(!at.state.game.current.commonCards.relicDeck.contains(top))
    val decide = question(at.state, power, played, at.first.tree)
    assertEquals(decide.decisionId, FamilyHeirloom.decisionId)
    assertEquals(decide.owner, p1)
    assertEquals(as[DecisionQuery.ChooseOne](decide.query).options.map(_.ref),
      Vector(FamilyHeirloom.keep, FamilyHeirloom.bottom))

  test("taking it keeps the relic"):
    val at = atChoice
    val done = Outcomes.finished(answer(at.state, at.first.tree,
      FamilyHeirloom.keep))
    assert(Look(done.treeless).player(p1).relics.exists(_.id == top))
    assertJournalRebuilds(staged, at.first.events ++ done.events, done.treeless)

  test("putting it on the bottom returns it to the relic deck"):
    val at = atChoice
    val done = Outcomes.finished(answer(at.state, at.first.tree,
      FamilyHeirloom.bottom))
    assert(!Look(done.treeless).player(p1).relics.exists(_.id == top))
    assertEquals(done.treeless.game.current.commonCards.relicDeck.last, top)
    assertJournalRebuilds(staged, at.first.events ++ done.events, done.treeless)

  test("it writes the relic it drew and what became of it, covering the " +
      "burial"):
    Vector(FamilyHeirloom.keep -> "kept it.",
        FamilyHeirloom.bottom -> "put it on the bottom of the relic deck.")
      .foreach { case (choice, end) =>
        val at = atChoice
        assertEquals(said(power, at.first.events),
          Vector.empty)
        val done = Outcomes.finished(answer(at.state, at.first.tree, choice))
        assertEquals(said(power, done.events),
          Vector(NoteText.Said(choice.key,
            s"p1 drew ${top.value} and $end", covers = true)))
      }

  test("an empty relic deck does nothing and asks nothing"):
    val emptied = Table.start.adviser(p1, card).relicDeckEmptied.ready
    val done = Outcomes.finished(play(emptied, power, card))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(Look(done.treeless).player(p1).relics,
      Look(emptied).player(p1).relics)

  test("a choice that is not offered is rejected"):
    val at = atChoice
    assertEquals(rejected(answer(at.state, at.first.tree,
      DecisionOptionRef.Button("elsewhere"))), OathViolation
      .InvalidEventOrder(s"decision ${FamilyHeirloom.decisionId} does not " +
        "offer the selected option"))
