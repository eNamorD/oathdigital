package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The battle arithmetic and its operations. */
object CampaignBattle:
  /** The printed defense dice of the targets: a Conquest's sites, or a Raid's
    * pawn (2), each targeted relic's printed defense and each banner (3).
    */
  def printedDefense(catalog: ExecutableCatalog, setup: CampaignSetup): Int =
    targetRefs(setup).map(defenseOf(catalog, _)).sum

  /** The printed defense one target adds; the targets question tallies the
    * same numbers.
    */
  def defenseOf(catalog: ExecutableCatalog, target: DecisionOptionRef): Int =
    target match
      case DecisionOptionRef.Site(site) => catalog.site(site).fold(0)(_.defense)
      case _: DecisionOptionRef.Player => 2
      case DecisionOptionRef.Relic(relic) => catalog.relics
        .find(_.id.value == relic.value).fold(0)(_.defense)
      case _: DecisionOptionRef.Banner => 3
      case _ => 0

  /** The targets of `setup`, as the options that name them. */
  def targetRefs(setup: CampaignSetup): Vector[DecisionOptionRef] =
    setup.kind match
      case CampaignKind.Conquest => setup.targetSites.map(DecisionOptionRef.Site(_))
      case CampaignKind.Raid => setup.raidTargets.map:
        case CampaignRaidTarget.Pawn(player) => DecisionOptionRef.Player(player)
        case CampaignRaidTarget.Relic(_, relic) => DecisionOptionRef.Relic(relic)
        case CampaignRaidTarget.Banner(_, banner) =>
          DecisionOptionRef.Banner(banner)

  /** Both pools, gathered once the force is known. A pool of zero is not
    * created: an empty pool is never rolled.
    */
  def gatherPools(catalog: ExecutableCatalog, setup: CampaignSetup)
      : Vector[CoreOperation] =
    val printed = printedDefense(catalog, setup)
    Vector[Option[CoreOperation]](
      Option.when(setup.force > 0)(
        ModifyDicePool(CampaignIds.attackPool, setup.force)),
      Option.when(printed > 0)(
        ModifyDicePool(CampaignIds.defensePool, printed))).flatten

  /** The attack after the skull cap: a skull removes one force warband and its
    * two swords count only when that loss can be paid; skulls beyond the force
    * add nothing; Outriders ignores every skull. Returns (score, skulls lost).
    */
  def attackResult(faces: Vector[AttackDieFace], force: Int,
      ignoreSkulls: Boolean): (Int, Int) =
    val rolled = AttackDieFace.skulls(faces)
    if ignoreSkulls then AttackDieFace.score(faces) -> 0
    else
      val payable = math.min(rolled, force)
      (AttackDieFace.score(faces) - (rolled - payable) * 2) -> payable

  /** The faces the attack pool rolled, before any cap. */
  def attackFacesOf(ready: ReadyGame): Vector[AttackDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector
      .flatMap(_.faces.collect { case face: AttackDieFace => face })

  /** Writes the capped attack over the rolled one. A pool that never rolled has
    * no outcome, and a missing outcome already reads as zero. A plan that
    * ignores the skulls writes its own result afterwards, in the attack result
    * window.
    */
  def attackResultOps(ready: ReadyGame, setup: CampaignSetup)
      : Vector[CoreOperation] =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool).toVector.map { _ =>
      val (score, skulls) = attackResult(attackFacesOf(ready), setup.force,
        ignoreSkulls = false)
      ModifyRollOutcome(CampaignIds.attackPool, Some(skulls), Some(score))
    }

  /** The force a defender adds to its dice: the warbands at every target, or a
    * Raid defender's board.
    */
  def defenderForce(ready: ReadyGame, setup: CampaignSetup): Int =
    setup.kind match
      case CampaignKind.Conquest => setup.targetSites
        .map(site => forceOf(ready, DecisionOptionRef.Site(site))).sum
      case CampaignKind.Raid => setup.defender match
        case CampaignDefender.Player(player) =>
          forceOf(ready, DecisionOptionRef.Player(player))
        case CampaignDefender.Bandits => 0

  /** The warbands one target brings to the defense: those at a site, or a
    * Raid defender's board for their pawn. A relic or a banner brings none.
    */
  def forceOf(ready: ReadyGame, target: DecisionOptionRef): Int =
    val current = ready.game.current
    target match
      case DecisionOptionRef.Site(site) => current.map.sites.get(site)
        .fold(0)(_.forces match
          case SiteForces.Occupied(_, count) => count
          case SiteForces.Empty => 0)
      case DecisionOptionRef.Player(player) => current.players
        .find(_.player == player).fold(0)(_.board.warbands)
      case _ => 0

  /** The defense is the dice score plus the defender's force. It is written
    * here, before any defender warband dies, so the victor never reads the
    * board.
    */
  def defenseResultOps(ready: ReadyGame, setup: CampaignSetup)
      : Vector[CoreOperation] =
    val dice = ready.game.current.rollOutcomes.get(CampaignIds.defensePool)
      .fold(0)(_.score)
    Vector(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(dice + defenderForce(ready, setup))))

  /** How many force warbands the attacker may still sacrifice. */
  def sacrificeMax(ready: ReadyGame, setup: CampaignSetup): Int =
    setup.force - ready.game.current.rollOutcomes.get(CampaignIds.attackPool)
      .fold(0)(_.skulls)

  /** What each sacrificed warband adds to the attack: one, or what a plan
    * recorded when it was applied (Zealots). */
  def sacrificeWorth(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(CampaignIds.sacrificeWorth)
      .fold(1)(_.count)

  /** The question alone. The dice, the attack total and the skull loss are
    * projected as a roll outcome and drawn as glyphs beside this, so writing
    * them here again would print the same facts twice, once as words.
    */
  def sacrificeHeading(max: Int, worth: Int = 1): String =
    s"Sacrifice up to $max warband${if max == 1 then "" else "s"} for " +
      s"${worthWord(worth)} attack each"

  private def worthWord(worth: Int): String = worth match
    case 1 => "one"
    case 3 => "three"
    case other => other.toString

  /** The durable record of this battle, built from the recorded outcomes and
    * the answers, after the skull and sacrifice losses and before the
    * losses.
    */
  def result(ready: ReadyGame, setup: CampaignSetup, pending: PendingTree)
      : CampaignResult =
    val outcomes = ready.game.current.rollOutcomes
    val attack = outcomes.get(CampaignIds.attackPool)
    val defense = outcomes.get(CampaignIds.defensePool)
    val sacrificed = CampaignAnswers.sacrificed(pending)
    val worth = sacrificeWorth(ready)
    val attackScore = attack.fold(0)(_.score)
    val defenseScore = defense.fold(0)(_.score)
    CampaignResult(setup.actor, setup.kind, setup.defender, setup.targetSites,
      setup.raidTargets, setup.force, attackFacesOf(ready), attackScore,
      attack.fold(0)(_.skulls), sacrificed,
      defense.toVector.flatMap(_.faces.collect { case face: DefenseDieFace => face }),
      defenseScore, attackScore + sacrificed * worth > defenseScore, worth,
      CampaignSetup.originForce(ready, setup.actor))

  /** Whether an attacker's plan spared a defeated force. */
  def attackerSpared(ready: ReadyGame): Boolean =
    ready.game.current.rollPools.contains(CampaignIds.attackerSpared)

  /** The skull losses, which die at the attack result: the attack's final
    * count, after every plan in the attack result window rewrote it. */
  def skullDeaths(ready: ReadyGame): Int =
    ready.game.current.rollOutcomes.get(CampaignIds.attackPool)
      .fold(0)(_.skulls)

  /** The attacker's dead at the losses: half the survivors, rounded down,
    * on a defeat, unless a plan spared them. The skull and sacrifice
    * losses died in their own windows (item 13b). */
  def attackerDeaths(result: CampaignResult, spared: Boolean): Int =
    val survivors = result.force - result.skullLosses - result.sacrificed
    if result.attackerWins || spared then 0 else survivors / 2

  /** How many of `deaths` may come from the origin: at least what the
    * board cannot hold, at most what the site holds. A toll beyond both
    * takes all the site holds. */
  def originDeathRange(origin: OriginForce, deaths: Int): (Int, Int) =
    val most = math.min(deaths, origin.atSite)
    (math.min(math.max(0, deaths - origin.onBoard), most), most)

  /** The question `id` that splits `deaths` of the attacker's dead between
    * their board and a queued origin standing as `origin`, asked only when
    * the split is a real choice. */
  def originSplit(catalog: ExecutableCatalog, actor: PlayerId, id: String,
      origin: Option[OriginForce], deaths: Int): Vector[Operation] =
    origin.toVector.flatMap { at =>
      val (least, most) = originDeathRange(at, deaths)
      val name = catalog.site(at.site).fold(at.site.value)(_.name)
      val dead = if deaths == 1 then "warband" else "warbands"
      Option.when(least < most)(Decide(id, actor,
        DecisionQuery.ChooseAmount(least, most, Some(s"Choose how many of " +
          s"the $deaths dead $dead come from $name"), "Choose",
          suggested = Some(least)))).toVector
    }

  /** The kill of `deaths` of the attacker's warbands: as many at the origin
    * as the answer to `id` says, within the range, else the least, and the
    * rest from the board. */
  def attackerKills(ready: ReadyGame, attacker: PlayerId, id: String,
      origin: Option[OriginForce], deaths: Int, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.players.find(_.player == attacker).toRight(
      OathViolation.InvalidEventOrder(
        "the Campaign's attacker is not in the game")).map { player =>
      val kind = PlayerForceKind.of(ready, player)
        .getOrElse(ForceKind.Exile(player.lineage))
      val fromSite = origin.fold(0) { at =>
        val (least, most) = originDeathRange(at, deaths)
        CampaignAnswers.originDeaths(pending, id)
          .filter(n => n >= least && n <= most).getOrElse(least)
      }
      val board: Vector[CoreOperation] =
        Option.when(deaths - fromSite > 0)(Kill(
          Piece.Warbands(kind, deaths - fromSite),
          PositionedLocation(Location.PlayArea(attacker)))).toVector
      val site: Vector[CoreOperation] = origin.filter(_ => fromSite > 0)
        .toVector.map(at => Kill(Piece.Warbands(kind, fromSite),
          PositionedLocation(Location.Site(at.site))))
      board ++ site
    }

  /** A kill of the attacker's force in its own window, before the outcome:
    * the split question `id` over the board and the origin as they stand,
    * then the kill. Nothing runs between the two, so both read the same
    * board, and a resume never selects the question again once the kill
    * has run. `deaths` reads the walk. */
  def earlyKill(catalog: ExecutableCatalog, actor: PlayerId, id: String,
      deaths: (ReadyGame, PendingTree) => Int): Vector[Operation] = Vector(
    Branch((ready, pending) => originSplit(catalog, actor, id,
      CampaignSetup.originForce(ready, actor), deaths(ready, pending))),
    BuildOps((ready, pending) => attackerKills(ready, actor, id,
      CampaignSetup.originForce(ready, actor), deaths(ready, pending),
      pending)))

  /** The split of the defeat's half, read from the durable result, so the
    * step keeps its place when the losses have changed the board. */
  def originDeathsStep(catalog: ExecutableCatalog, actor: PlayerId,
      result: CampaignResult, spared: Boolean): Vector[Operation] =
    originSplit(catalog, actor, CampaignIds.originDeaths, result.originForce,
      attackerDeaths(result, spared))

  /** Step 8: kill the defeated warbands. The attacker loses half the
    * survivors on a defeat unless a plan spared them, split by the answer
    * to `originDeaths` when a queued origin's warbands are in the force.
    * On a victory every warband at every target dies, a player defender's
    * survivors return from the supply to their board, and a Raid defender
    * loses half its board.
    */
  def losses(ready: ReadyGame, result: CampaignResult, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    attackerKills(ready, result.attacker, CampaignIds.originDeaths,
      result.originForce, attackerDeaths(result, attackerSpared(ready)),
      pending).map(_ ++ (if !result.attackerWins then Vector.empty
        else result.kind match {
          case CampaignKind.Conquest => conquestLosses(ready, result)
          case CampaignKind.Raid => raidBoardLosses(ready, result)
        }))

  private def conquestLosses(ready: ReadyGame, result: CampaignResult)
      : Vector[CoreOperation] =
    val sites = result.targetSites.flatMap(site =>
      ready.game.current.map.sites.get(site).map(state => site -> state.forces))
    val kills: Vector[CoreOperation] = sites.collect:
      case (site, SiteForces.Occupied(force, count)) if count > 0 => Kill(
        Piece.Warbands(force, count), PositionedLocation(Location.Site(site)))
    val returned: Vector[CoreOperation] = result.defender match
      case CampaignDefender.Player(player) =>
        val total = sites.collect { case (_, SiteForces.Occupied(_, n)) => n }.sum
        val back = total - total / 2
        sites.collectFirst { case (_, SiteForces.Occupied(force, _)) => force }
          .filter(_ => back > 0).toVector.map(force => Move(
            Piece.Warbands(force, back),
            PositionedLocation(Location.WarbandBank(force)),
            PositionedLocation(Location.PlayArea(player))))
      case CampaignDefender.Bandits => Vector.empty
    kills ++ returned

  private def raidBoardLosses(ready: ReadyGame, result: CampaignResult)
      : Vector[CoreOperation] = result.defender match
    case CampaignDefender.Player(player) =>
      ready.game.current.players.find(_.player == player).toVector.flatMap:
        defender =>
          val killed = defender.board.warbands / 2
          Option.when(killed > 0)(Kill(Piece.Warbands(
            ForceKind.Exile(defender.lineage), killed),
            PositionedLocation(Location.PlayArea(player)))).toVector
    case CampaignDefender.Bandits => Vector.empty
