package oathdigital.gameplay.phases.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathViolation._
import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.model._

/** Finish Rest (rest-walker spec, `FinishRest`).
  *
  * {{{
  * Sequence(
  *   BuildOps(cleanup)        // window = RestReturnFavor
  *   BuildOps(supplyRefresh)
  *   BuildOps(beginNextTurn))
  * }}}
  *
  * Every leaf derives its operations at walk time, so a power folded in
  * front of cleanup (League Treaty) changes what cleanup finds. The gate
  * holds for the whole procedure: nothing before `BeginTurn` leaves Rest, so
  * resume rebuilds through the same function.
  */
object FinishRestProcedure {
  val ExileSupply: SupplyRules = SupplyRules(SupplyTrack.Maximum, Vector(
    SupplyRefreshBand(InclusiveIntRange(9, Int.MaxValue), 6),
    SupplyRefreshBand(InclusiveIntRange(4, 8), 5),
    SupplyRefreshBand(InclusiveIntRange(0, 3), 4)))

  def gate(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Either[OathViolation, ReadyGame] = {
    val current = ready.game.current
    if (current.result.nonEmpty) Left(GameEnded)
    else if (current.turn.activePlayer != player)
      Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Rest)
      Left(WrongPhase(Phase.Rest, current.turn.phase))
    else current.pending match {
      case Some(value) => Left(PendingProcedureBlocksAction(value.decision))
      case None => BeginRestProcedure.validateSupportedState(catalog, ready)
        .map(_ => ready)
    }
  }

  def build(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- Either.cond(args.isEmpty, (), InvalidEventOrder(
      "Finish Rest selects nothing"))
    _ <- gate(catalog, ready, player)
  } yield Sequence(Vector(
    BuildOps((state, _) => cleanup(catalog, state, player),
      window = Some(PowerWindow.RestReturnFavor)),
    BuildOps((state, _) => supplyRefresh(state, player)),
    BuildOps((state, _) => beginNextTurn(state, player))))

  /** Card favor to its printed suit bank, card secrets to the resting
    * player, and the resting player's facedown stash flipped faceup: the
    * operations legacy `Rest.applyCompletion` ran.
    */
  private def cleanup(catalog: ExecutableCatalog, ready: ReadyGame,
      resting: PlayerId): Either[OathViolation, Vector[CoreOperation]] = for {
    plan <- RestCleanupPlan.derive(catalog, ready, resting)
      .left.map(UnsupportedRestState)
    player <- ready.game.current.players.find(_.player == resting)
      .toRight(UnsupportedRestState(s"unknown resting player $resting"))
  } yield {
    val from = (id: CardId) => PositionedLocation(Location.OnCard(id))
    val favor = plan.cards.collect {
      case card if card.suit.nonEmpty && card.favor > 0 =>
        Move(Piece.Favor(card.favor), from(card.id),
          PositionedLocation(Location.FavorBank(card.suit.get)))
    }
    val secrets = plan.cards.collect {
      case card if card.secrets > 0 => Move(Piece.Secrets(card.secrets),
        from(card.id), PositionedLocation(Location.PlayArea(resting)))
    }
    val reveal = Vector(player.board.faceDownSecrets).filter(_ > 0).map(count =>
      FlipSecrets(resting, count, SecretSide.FaceDown, SecretSide.FaceUp))
    favor ++ secrets ++ reveal
  }

  private def supplyRefresh(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    for {
      player <- current.players.find(_.player == resting)
        .toRight(UnsupportedRestState(s"unknown resting player $resting"))
      kind = ForceKind.Exile(player.lineage)
      supply <- ready.banks.warbandSupply.get(kind)
        .toRight(UnsupportedRestState(s"no bounded warband supply for $kind"))
      siteWarbands = current.map.sites.valuesIterator.map(_.forces).collect {
        case SiteForces.Occupied(ForceKind.Exile(owner), count)
            if owner == player.lineage => count
      }.sum
      banked = math.max(0, supply - player.board.warbands - siteWarbands)
      refreshed <- ExileSupply.refresh(banked, player.board.supply.supply)
        .toRight(UnsupportedRestState(s"no Supply band for $banked banked warbands"))
    } yield {
      val change = refreshed.supply - player.board.supply.supply
      if (change > 0) Vector(GainSupply(resting, change))
      else if (change < 0) Vector(SpendSupply(resting, -change))
      else Vector.empty
    }
  }

  private def beginNextTurn(ready: ReadyGame, resting: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val order = turnOrder(ready)
    val index = order.indexOf(resting)
    if (index < 0) Left(UnsupportedRestState(s"$resting is not in turn order"))
    else if (index == order.size - 1)
      Right(Vector(BeginTurn(order.head, Phase.RoundEnd)))
    else Right(Vector(BeginTurn(order(index + 1), Phase.Wake)))
  }

  def turnOrder(ready: ReadyGame): Vector[PlayerId] = {
    val participants = ready.game.current.players.map(_.player)
    val index = participants.indexOf(ready.setup.firstPlayer)
    participants.drop(index) ++ participants.take(index)
  }
}
