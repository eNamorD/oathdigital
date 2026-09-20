package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ProcedureWalker
import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.model._

class FamilyHeirloomSuite extends munit.FunSuite
    with WalkerRecordedOpsReducer {
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = FamilyHeirloom.forCatalog(catalog).get
  private val card = power.cardId
  private val staged = asAdviser(base, card)
  private val top = staged.game.current.commonCards.relicDeck.head

  private def answer(ready: ReadyGame, tree: PendingTree, ref: DecisionOptionRef) =
    ProcedureWalker.resolve(ready, hook(card), tree, Answered(
      FamilyHeirloom.decisionId, DecisionAnswer.ChooseOneAnswer(ref), actor),
      powers(power))

  private def atChoice = {
    val first = parked(play(staged, power, card))
    (first, foldRecordedOps(staged, first.events, "the draw did not replay"))
  }

  test("Family Heirloom is in the default walker catalog") {
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
  }

  test("the relic is drawn facedown to the player and the choice is theirs") {
    val (first, state) = atChoice
    assert(player(state).relics.exists(relic =>
      relic.id == top && relic.orientation == Orientation.FaceDown))
    assert(!state.game.current.commonCards.relicDeck.contains(top))
    val decide = ProcedureWalker.parkedDecide(state, hook(card), first.tree,
      powers(power)).get
    assertEquals(decide.decisionId, FamilyHeirloom.decisionId)
    assertEquals(decide.owner, actor)
    assertEquals(decide.query.asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector(FamilyHeirloom.keep, FamilyHeirloom.bottom))
  }

  test("taking it keeps the relic") {
    val (first, state) = atChoice
    val done = finished(answer(state, first.tree, FamilyHeirloom.keep))
    assert(player(done.treeless).relics.exists(_.id == top))
    assertEquals(replayed(staged, first.events ++ done.events), done.treeless)
  }

  test("putting it on the bottom returns it to the relic deck") {
    val (first, state) = atChoice
    val done = finished(answer(state, first.tree, FamilyHeirloom.bottom))
    assert(!player(done.treeless).relics.exists(_.id == top))
    assertEquals(done.treeless.game.current.commonCards.relicDeck.last, top)
    assertEquals(replayed(staged, first.events ++ done.events), done.treeless)
  }

  test("an empty relic deck does nothing and asks nothing") {
    val current = staged.game.current
    val emptied = staged.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))
    val done = finished(play(emptied, power, card))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(player(done.treeless).relics, player(emptied).relics)
  }

  test("a choice that is not offered is rejected") {
    val (first, state) = atChoice
    assert(answer(state, first.tree, DecisionOptionRef.Button("elsewhere")).isLeft)
  }
}
