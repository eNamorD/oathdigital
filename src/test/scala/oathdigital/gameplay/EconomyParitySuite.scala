package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{Economy, EconomyCommand}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.gameplay.walker.WalkerSimulation.PreviewedOption
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Legacy versus walker, on exile-only boards with the fixed unaltered
  * Foundation profile, the only states the legacy path accepts.
  *
  * A scenario is compared three ways: the candidates each path offers (target,
  * Supply cost and gain, in order), the authoritative state each ends in, and
  * the projections of those states. A scenario the legacy path rejects must be
  * offered by the walker path nowhere: no candidate and no start.
  */
class EconomyParitySuite extends munit.FunSuite {
  import EconomyFixture._

  private val rules = new OathRules(catalog)
  private val projector = new GameProjector(catalog)

  private val boards: Vector[(String, ReadyGame)] = Vector(
    "matching adviser" -> act(advisers = Vector(matchingAdviser)),
    "no adviser" -> act(),
    "warband shortage" -> act(boardWarbands = 14),
    "warband shortage with an adviser" -> act(
      advisers = Vector(matchingAdviser), boardWarbands = 13),
    "bank shortage" -> act(advisers = Vector(matchingAdviser), bank = 1),
    "empty bank" -> act(bank = 0),
    "ruined edifice" -> spring(act(), EdificeSide.Ruined),
    "intact edifice" -> spring(act(), EdificeSide.Intact),
    "no favor" -> act(favor = 0),
    "one favor" -> act(favor = 1),
    "no secrets" -> act(secrets = 0),
    "no supply" -> act(supply = 0),
    "occupied card" -> act(tokens = Tokens(0, 1)))

  private def cardOf(option: DecisionOption): CardId = option.ref match {
    case DecisionOptionRef.Denizen(id) => id
    case DecisionOptionRef.Edifice(id) => id
    case other => fail(s"unexpected source $other")
  }

  private def refOf(target: EconomyTargetRef): DecisionOptionRef = target match {
    case EconomyTargetRef.Denizen(id) => DecisionOptionRef.Denizen(id)
    case EconomyTargetRef.Edifice(id) => DecisionOptionRef.Edifice(id)
  }

  private def supplySpent(operations: Vector[CoreOperation]): Int =
    operations.collect { case SpendSupply(_, amount, _) => amount }.sum

  private def walkerCandidates(previewed: Vector[PreviewedOption])
      (gain: Vector[CoreOperation] => Int): Vector[(CardId, Int, Int)] =
    previewed.collect { case PreviewedOption(option, Right(outcome)) =>
      (cardOf(option), supplySpent(outcome.operations), gain(outcome.operations))
    }

  // The walker records the `Move` a `Gain` expands into, from a bank to the
  // actor's play area, so a gain is read off those moves.
  private def gained(operations: Vector[CoreOperation])
      (amount: PartialFunction[Piece, Int]): Int = operations.collect {
    case Move(piece, PositionedLocation(from, _), PositionedLocation(to, _), _)
        if isBank(from) && isPlayArea(to) && amount.isDefinedAt(piece) =>
      amount(piece)
  }.sum
  private def isBank(location: Location): Boolean = location match {
    case _: Location.FavorBank | Location.SharedBank |
        _: Location.WarbandBank => true
    case _ => false
  }
  private def isPlayArea(location: Location): Boolean =
    location.isInstanceOf[Location.PlayArea]
  private def warbands(operations: Vector[CoreOperation]): Int =
    gained(operations) { case Piece.Warbands(_, amount) => amount }
  private def favor(operations: Vector[CoreOperation]): Int =
    gained(operations) { case Piece.Favor(amount) => amount }
  private def secrets(operations: Vector[CoreOperation]): Int =
    gained(operations) { case Piece.Secrets(amount) => amount }

  private def walkerRun(ready: ReadyGame, action: StartableRef,
      args: Vector[DecisionOptionRef], decisionId: String,
      ref: DecisionOptionRef): Either[OathViolation, OathState] = for {
    started <- rules.startWalker(Ready(ready), action, player(ready).player,
      startArgs = args)
    finished <- rules.resolveWalker(started.state, player(ready).player,
      decisionId, DecisionAnswer.ChooseOneAnswer(ref))
  } yield finished.state

  private def assertSameOutcome(name: String, ready: ReadyGame,
      legacy: OathState, walker: OathState): Unit = {
    assertEquals(walker, legacy, s"$name: authoritative state")
    val Ready(expected) = legacy: @unchecked
    val Ready(actual) = walker: @unchecked
    assertEquals(CardIndex.from(actual.game), CardIndex.from(expected.game),
      s"$name: card index")
    ready.game.current.players.map(_.player).foreach { viewer =>
      assertEquals(projector.project("parity", LoadedGame(walker, 9), viewer),
        projector.project("parity", LoadedGame(legacy, 9), viewer),
        s"$name: projection for ${viewer.value}")
    }
  }

  boards.foreach { case (name, ready) =>
    val actor = player(ready).player

    test(s"Muster parity: $name") {
      val legacy = Economy.legalMuster(catalog, ready, player(ready))
      val walker = walkerCandidates(MusterProcedure.startOptions(catalog, ready,
        actor, WalkerPowers.empty))(warbands)
      assertEquals(walker,
        legacy.map(result => (result.target.id, result.supplySpent,
          result.warbandsGained)), s"$name: candidates")
      if (legacy.isEmpty) assert(rules.startWalker(Ready(ready), ActionRef.Muster,
        actor).isLeft, s"$name: a start must be rejected")
      legacy.foreach { result =>
        val expected = rules.handle(Ready(ready),
          EconomyCommand.Muster(actor, result.target)).toOption.get.state
        val actual = walkerRun(ready, ActionRef.Muster, Vector.empty,
          MusterProcedure.decisionId, refOf(result.target))
          .getOrElse(fail(s"$name: the walker must accept ${result.target}"))
        assertSameOutcome(s"$name muster ${result.target.id.value}", ready,
          expected, actual)
      }
    }

    Vector[(TradeResource, (String, Vector[CoreOperation] => Int))](
      TradeResource.Favor -> (("favor", favor _)),
      TradeResource.Secret -> (("secret", secrets _))).foreach {
      case (resource, (key, gain)) =>
        test(s"Trade for $key parity: $name") {
          val args = Vector[DecisionOptionRef](DecisionOptionRef.Button(key))
          val legacy = Economy.legalTrades(catalog, ready, player(ready))
            .filter(_.resource == resource)
          val walker = walkerCandidates(TradeProcedure.startOptions(catalog,
            ready, actor, resource, WalkerPowers.empty))(gain)
          assertEquals(walker,
            legacy.map(result => (result.target.id, result.supplySpent,
              result.gained)), s"$name: candidates")
          if (legacy.isEmpty) assert(rules.startWalker(Ready(ready),
            ActionRef.Trade, actor, startArgs = args).isLeft,
            s"$name: a start must be rejected")
          legacy.foreach { result =>
            val expected = rules.handle(Ready(ready), EconomyCommand.Trade(actor,
              result.target, resource)).toOption.get.state
            val actual = walkerRun(ready, ActionRef.Trade, args,
              TradeProcedure.decisionId, refOf(result.target))
              .getOrElse(fail(s"$name: the walker must accept ${result.target}"))
            assertSameOutcome(s"$name trade $key ${result.target.id.value}",
              ready, expected, actual)
          }
        }
    }
  }
}
