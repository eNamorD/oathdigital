package oathdigital.gameplay

import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.AdviserLimit
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** `PlacementRules`: the adviser limits a card play is planned under, and how
  * two contributors to them compose. The powers here are test doubles.
  */
class PlacementRulesSuite extends munit.FunSuite {
  import PlacementFixture._

  test("the default rules are the printed limit of three and no site discard") {
    assertEquals(PlacementRules.default, PlacementRules(3, 3, false))
    assertEquals(PlacementRules.DefaultAdviserLimit, 3)
  }

  test("a limit only lowers, and a faceup limit leaves the facedown one") {
    assertEquals(PlacementRules.default.limitAdvisers(5), PlacementRules.default)
    assertEquals(PlacementRules.default.limitAdvisers(2),
      PlacementRules(2, 2, false))
    assertEquals(PlacementRules.default.limitFaceupAdvisers(2),
      PlacementRules(2, 3, false))
    assertEquals(PlacementRules.default.adviserLimit(Orientation.FaceUp), 3)
    assertEquals(PlacementRules.default.limitFaceupAdvisers(2)
      .adviserLimit(Orientation.FaceDown), 3)
  }

  test("without a contributor the tree is the play under the default rules") {
    val card = plain(initialReady).head
    val (ready, actor, _) = staged(card, Vector.empty)
    val tree = build(ready, actor, card)
    assertEquals(tree.children.size, 2)
    assert(tree.children.head.isInstanceOf[Decide])
  }

  test("two contributors compose in either order") {
    val card = plain(initialReady).head
    val (ready, actor, _) = staged(card, Vector.empty)
    val tree = build(ready, actor, card)
      .asInstanceOf[CardPlayProcedure.PlacementTree]
    def rulesOf(ops: Vector[Operation]): PlacementRules =
      ops.head.asInstanceOf[CardPlayProcedure.PlacementBody].rules
    val limitFirst = tree.adjust(tree.adjust(tree.children)(_.limitAdvisers(2)))(
      _.withSiteDiscardFirst)
    val discardFirstThenLimit = tree.adjust(tree.adjust(tree.children)(
      _.withSiteDiscardFirst))(_.limitAdvisers(2))
    assertEquals(rulesOf(limitFirst), PlacementRules(2, 2, true))
    assertEquals(rulesOf(discardFirstThenLimit), PlacementRules(2, 2, true))
    assertEquals(limitFirst.size, 1)
  }

  test("a contributed limit reaches the placement: two advisers fill an area " +
      "limited to two") {
    val Vector(card, first, second) = plain(initialReady).take(3)
    val (staged1, actor, _) = staged(card, Vector.empty)
    val held = Vector(first, second)
    val current = staged1.game.current
    val ready = staged1.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(held.contains)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = held.map(id => DenizenState(id,
          Orientation.FaceDown, Tokens.empty))) else p)))
    val tree = build(ready, actor, card)
    // Under the default limit of three there is room, so no discard is asked.
    val open = WalkerPowers.empty
    val opened = answer(ready, tree, park(ready, tree, open), open,
      decisionId(card, "place"), DecisionOptionRef.Button("adviser-faceup"),
      actor)
    assert(opened.isInstanceOf[WalkerOutcome.Finished])
    // Under a limit of two the area is full and a discard is asked first.
    val limited = WalkerPowers(Vector(limitTwo))
    val asked = answer(ready, tree, park(ready, tree, limited), limited,
      decisionId(card, "place"), DecisionOptionRef.Button("adviser-faceup"),
      actor).asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, asked.tree, limited).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(first),
        DecisionOptionRef.Denizen(second)))
  }

  test("Silver Tongue and the adviser-limit read agree on the limit") {
    val tongue = SilverTongue.forCatalog(catalog).get
    val held = base.updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player == actorOf(base).player) p.copy(advisers = p.advisers :+
        DenizenState(tongue.cardId, Orientation.FaceUp, Tokens.empty)) else p)))
    val player = actorOf(held).player
    assertEquals(tongue.limitFor(held, player), Some(SilverTongue.HolderLimit))
    assertEquals(AdviserLimit.of(catalog, held, player), SilverTongue.HolderLimit)
    assertEquals(AdviserLimit.of(catalog, base, player), AdviserLimit.Default)
    assertEquals(tongue.limitFor(base, player), None)
  }

  private def base: ReadyGame = {
    val ready = initialReady
    val tongue = SilverTongue.forCatalog(catalog).get.cardId
    ready.updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
      worldDeck = c.commonCards.worldDeck.filterNot(_ == tongue))))
  }
}
