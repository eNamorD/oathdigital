package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The battle arithmetic and its operations. */
object CampaignBattle {
  /** The printed defense dice of the targets: a Conquest's sites, or a Raid's
    * pawn (2), each targeted relic's printed defense and each banner (3).
    */
  def printedDefense(catalog: ExecutableCatalog, setup: CampaignSetup): Int =
    setup.kind match {
      case CampaignKind.Conquest => setup.targetSites
        .flatMap(site => catalog.sites.find(_.id == site)).map(_.defense).sum
      case CampaignKind.Raid => setup.raidTargets.map {
        case _: CampaignRaidTarget.Pawn => 2
        case CampaignRaidTarget.Relic(_, relic) => catalog.relics
          .find(_.id.value == relic.value).map(_.defense).getOrElse(0)
        case _: CampaignRaidTarget.Banner => 3
      }.sum
    }

  /** Both pools, gathered once the force is known. A pool of zero is not
    * created: an empty pool is never rolled.
    */
  def gatherPools(catalog: ExecutableCatalog, setup: CampaignSetup)
      : Vector[CoreOperation] = {
    val printed = printedDefense(catalog, setup)
    Vector[Option[CoreOperation]](
      Option.when(setup.force > 0)(
        ModifyDicePool(CampaignIds.attackPool, setup.force)),
      Option.when(printed > 0)(
        ModifyDicePool(CampaignIds.defensePool, printed))).flatten
  }

  /** The attack after the skull cap: a skull removes one force warband and its
    * two swords count only when that loss can be paid; skulls beyond the force
    * add nothing; Outriders ignores every skull. Returns (score, skulls lost).
    */
  def attackResult(faces: Vector[AttackDieFace], force: Int,
      ignoreSkulls: Boolean): (Int, Int) = {
    val rolled = AttackDieFace.skulls(faces)
    if (ignoreSkulls) AttackDieFace.score(faces) -> 0
    else {
      val payable = math.min(rolled, force)
      (AttackDieFace.score(faces) - (rolled - payable) * 2) -> payable
    }
  }

  private def attackFacesOf(ready: ReadyGame): Vector[AttackDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
      .flatMap(_.faces.collect { case face: AttackDieFace => face })

  /** Writes the capped attack over the rolled one. A pool that never rolled has
    * no outcome, and a missing outcome already reads as zero.
    */
  def attackResultOps(catalog: ExecutableCatalog, ready: ReadyGame,
      setup: CampaignSetup, pending: PendingTree): Vector[CoreOperation] =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector.map { _ =>
      val ignore = CampaignPlans.ignoresSkulls(catalog,
        CampaignAnswers.picks(pending, CampaignIds.attackerPlan))
      val (score, skulls) = attackResult(attackFacesOf(ready), setup.force, ignore)
      ModifyRollOutcome(CampaignIds.attackPool, Some(skulls), Some(score))
    }

  /** The force a defender adds to its dice: the warbands at every target, or a
    * Raid defender's board.
    */
  def defenderForce(ready: ReadyGame, setup: CampaignSetup): Int = {
    val current = ready.game.current
    setup.kind match {
      case CampaignKind.Conquest => setup.targetSites.flatMap(current.map.sites.get)
        .map(_.forces match {
          case SiteForces.Occupied(_, count) => count
          case SiteForces.Empty => 0
        }).sum
      case CampaignKind.Raid => setup.defender match {
        case CampaignDefender.Player(player) => current.players
          .find(_.player == player).fold(0)(_.board.warbands)
        case CampaignDefender.Bandits => 0
      }
    }
  }

  /** The defense is the dice score plus the defender's force. It is written
    * here, before any warband dies, so the victor never reads the board.
    */
  def defenseResultOps(ready: ReadyGame, setup: CampaignSetup)
      : Vector[CoreOperation] = {
    val dice = ready.game.current.rollOutcomes.get(CampaignIds.defensePool)
      .fold(0)(_.score)
    Vector(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(dice + defenderForce(ready, setup))))
  }

  /** How many force warbands the attacker may still sacrifice. */
  def sacrificeMax(ready: ReadyGame, setup: CampaignSetup): Int =
    setup.force - ready.game.current.rollOutcomes.get(CampaignIds.attackPool)
      .fold(0)(_.skulls)

  private def faceName(face: AttackDieFace): String = face match {
    case AttackDieFace.HollowSword => "hollow sword"
    case AttackDieFace.OneSword => "one sword"
    case AttackDieFace.TwoSwordsSkull => "two swords and a skull"
  }

  def sacrificeHeading(faces: Vector[AttackDieFace], score: Int, skulls: Int,
      max: Int): String =
    s"Attack roll: ${if (faces.isEmpty) "no dice" else faces.map(faceName).mkString(", ")}. " +
      s"Attack $score with $skulls skull loss${if (skulls == 1) "" else "es"}. " +
      s"Sacrifice up to $max warband${if (max == 1) "" else "s"} for one attack each."

  /** The durable record of this battle, built from the recorded outcomes and
    * the answers, before any warband dies.
    */
  def result(ready: ReadyGame, setup: CampaignSetup, pending: PendingTree)
      : CampaignResult = {
    val outcomes = ready.game.current.rollOutcomes
    val attack = outcomes.get(CampaignIds.attackPool)
    val defense = outcomes.get(CampaignIds.defensePool)
    val sacrificed = CampaignAnswers.sacrificed(pending)
    val attackScore = attack.fold(0)(_.score)
    val defenseScore = defense.fold(0)(_.score)
    CampaignResult(setup.actor, setup.kind, setup.defender, setup.targetSites,
      setup.raidTargets, setup.force, attackFacesOf(ready), attackScore,
      attack.fold(0)(_.skulls), sacrificed,
      defense.toVector.flatMap(_.faces.collect { case face: DefenseDieFace => face }),
      defenseScore, attackScore + sacrificed > defenseScore)
  }

  /** Step 8: kill the defeated warbands. Attacker deaths are the skull and
    * sacrifice losses, plus half the survivors on a defeat; on a victory every
    * warband at every target dies, a player defender's survivors return from
    * the supply to their board, and a Raid defender loses half its board.
    */
  def losses(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    current.players.find(_.player == result.attacker).toRight(
      OathViolation.InvalidEventOrder("the Campaign's attacker is not in the game"))
      .map { attacker =>
        val survivors = result.force - result.skullLosses - result.sacrificed
        val deaths = result.skullLosses + result.sacrificed +
          (if (result.victorious) 0 else survivors / 2)
        val own: Vector[CoreOperation] = Option.when(deaths > 0)(Kill(
          Piece.Warbands(ForceKind.Exile(attacker.lineage), deaths),
          PositionedLocation(Location.PlayArea(result.attacker)))).toVector
        own ++ (if (!result.victorious) Vector.empty
          else result.kind match {
            case CampaignKind.Conquest => conquestLosses(ready, result)
            case CampaignKind.Raid => raidBoardLosses(ready, result)
          })
      }
  }

  private def conquestLosses(ready: ReadyGame, result: CampaignResult)
      : Vector[CoreOperation] = {
    val sites = result.targetSites.flatMap(site =>
      ready.game.current.map.sites.get(site).map(state => site -> state.forces))
    val kills: Vector[CoreOperation] = sites.collect {
      case (site, SiteForces.Occupied(force, count)) if count > 0 => Kill(
        Piece.Warbands(force, count), PositionedLocation(Location.Site(site)))
    }
    val returned: Vector[CoreOperation] = result.defender match {
      case CampaignDefender.Player(player) =>
        val total = sites.collect { case (_, SiteForces.Occupied(_, n)) => n }.sum
        val back = total - total / 2
        sites.collectFirst { case (_, SiteForces.Occupied(force, _)) => force }
          .filter(_ => back > 0).toVector.map(force => Move(
            Piece.Warbands(force, back),
            PositionedLocation(Location.WarbandBank(force)),
            PositionedLocation(Location.PlayArea(player))))
      case CampaignDefender.Bandits => Vector.empty
    }
    kills ++ returned
  }

  private def raidBoardLosses(ready: ReadyGame, result: CampaignResult)
      : Vector[CoreOperation] = result.defender match {
    case CampaignDefender.Player(player) =>
      ready.game.current.players.find(_.player == player).toVector.flatMap {
        defender =>
          val killed = defender.board.warbands / 2
          Option.when(killed > 0)(Kill(Piece.Warbands(
            ForceKind.Exile(defender.lineage), killed),
            PositionedLocation(Location.PlayArea(player)))).toVector
      }
    case CampaignDefender.Bandits => Vector.empty
  }
}
