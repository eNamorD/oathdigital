package oathdigital.gameplay.setup

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{Answers, Situation}

class SetupProcedureSuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val ready = FirstGameSetupFixture.freshReady

  test("the setup walk opens on the first player's pawn placement"):
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    val first = ready.game.current.turn.activePlayer

    val parked1 = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .toOption.get
    val pending1 = parked1 match
      case WalkerOutcome.Parked(pending, _) => pending
      case other => fail(s"expected a park, got $other")
    val decide1 = ProcedureWalker.parkedDecide(ready, tree, pending1,
      WalkerPowers.empty).get
    assertEquals(decide1.decisionId, SetupProcedure.pawnDecisionId(first))
    assertEquals(decide1.owner, first)

  test("the whole procedure finishes in Wake of round 1 with three pawns and three advisers"):
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    val finished = SetupWalkDriver.driveToCompletion(ready, tree, WalkerPowers.empty)
    assertEquals(finished.game.current.turn.phase, Phase.Wake)
    assertEquals(finished.game.current.turn.activePlayer,
      ready.game.current.turn.activePlayer)
    assertEquals(finished.game.current.players.count(_.pawnSite.nonEmpty), 3)
    assertEquals(finished.game.current.players.count(_.advisers.size == 1), 3)
    assert(finished.game.current.temporaryHands.values.forall(_.isEmpty))

  private val revealAll: Answers =
    case park if park.decisionId ==
        SetupProcedure.revealDecisionId(park.awaiting) =>
      val DecisionQuery.ChooseMany(_, _, options, _) = park.decide.query: @unchecked
      DecisionAnswer.ChooseManyAnswer(options.map(_.ref))

  private def advisers(woken: Situation): Vector[AdviserState] =
    woken.ready.game.current.players.flatMap(_.advisers)

  test("Reveal Cards turns a chosen adviser faceup"):
    val woken = Situation.wake(Situation.rules(catalog).withAnswers(revealAll))
    val revealable = advisers(woken).collect { case d: DenizenState => d }
      .filterNot(d => catalog.denizen(d.id).exists(
        _.restrictions == CardRestrictions.SiteOnly))
    assert(revealable.nonEmpty, advisers(woken))
    assert(revealable.forall(_.orientation == Orientation.FaceUp),
      advisers(woken))
    assertNotEquals(woken.ready.game.current.turn.phase, Phase.Setup)

  test("Reveal Cards chosen empty keeps every adviser facedown"):
    val woken = Situation.wake(Situation.rules(catalog))
    assert(advisers(woken).forall {
      case d: DenizenState => d.orientation == Orientation.FaceDown
      case v: VisionState => v.orientation == Orientation.FaceDown
    }, advisers(woken))

  test("Reveal Cards offers facedown advisers except site-only ones, then " +
      "facedown relics"):
    val woken = Situation.wake(Situation.rules(catalog))
    val player = woken.ready.game.current.players.head.player
    val siteOnly = catalog.denizens.find(
      _.restrictions == CardRestrictions.SiteOnly).get.id
    val open = catalog.denizens.find(
      _.restrictions == CardRestrictions.Unrestricted).map(_.id).get
    val faceup = catalog.denizens.filter(
      _.restrictions == CardRestrictions.Unrestricted).map(_.id)(1)
    val relic = FirstGameSetupFixture.relics.head
    val staged = woken.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if p.player != player then p else
        p.copy(
          advisers = Vector(
            DenizenState(DenizenId(siteOnly.value), Orientation.FaceDown, Tokens.empty),
            DenizenState(DenizenId(open.value), Orientation.FaceDown, Tokens.empty),
            DenizenState(DenizenId(faceup.value), Orientation.FaceUp, Tokens.empty)),
          relics = Vector(RelicState(relic, Orientation.FaceDown, Tokens.empty))))))
    assertEquals(SetupProcedure.revealable(catalog, staged, player).map(_.ref),
      Vector(DecisionOptionRef.AdviserSlot(player, 1),
        DecisionOptionRef.RelicSlot(player, 0)))
