package oathdigital.gameplay.actions.campaign

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** Decision ids and pool keys of the Campaign procedure. */
object CampaignIds {
  val kind = "campaign.kind"
  val defender = "campaign.defender"
  val targets = "campaign.targets"
  val force = "campaign.force"
  val attackerPlan = "campaign.attacker-plan"
  val defenderPlan = "campaign.defender-plan"
  val sacrifice = "campaign.sacrifice"
  val placement = "campaign.placement"
  val relocation = "campaign.relocation"
  val all: Set[String] = Set(kind, defender, targets, force, attackerPlan,
    defenderPlan, sacrifice, placement, relocation)

  val attackPool: PoolKey = PoolKey("campaign.attack")
  val defensePool: PoolKey = PoolKey("campaign.defense")
  val SupplyCost = 2

  /** The option that ends a plan window. */
  val finish: DecisionOptionRef.Button = DecisionOptionRef.Button("finish")
}

/** What a Campaign's early answers say. `targetSites` is the mandatory origin
  * plus the answered additions in map order (Conquest); `raidTargets` is the
  * pawn plus the answered relics and banners in canonical order (Raid).
  */
final case class CampaignSetup(actor: PlayerId, kind: CampaignKind,
    origin: SiteId, defender: CampaignDefender, targetSites: Vector[SiteId],
    raidTargets: Vector[CampaignRaidTarget], force: Int)

object CampaignSetup {
  private def playerOf(ready: ReadyGame, id: PlayerId): Option[PlayerState] =
    ready.game.current.players.find(_.player == id)

  /** The actor's pawn site: the mandatory Conquest site, and where a Raid's
    * defender must stand.
    */
  def originOf(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    playerOf(ready, actor).flatMap(_.pawnSite)

  /** Who rules `site` as a Campaign defender. `None` when the site is
    * unruled, Imperial or its rule is corrupt.
    */
  def defenderAt(ready: ReadyGame, site: SiteId): Option[CampaignDefender] =
    ready.game.current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, ready.game.current.players).toOption)
      .flatMap {
        case SiteRuler.Bandits => Some(CampaignDefender.Bandits)
        case SiteRuler.Player(player) => Some(CampaignDefender.Player(player))
        case _ => None
      }

  def conquestDefender(ready: ReadyGame, actor: PlayerId)
      : Option[CampaignDefender] =
    originOf(ready, actor).flatMap(defenderAt(ready, _))
      .filterNot(_ == CampaignDefender.Player(actor))

  def raidDefenders(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    originOf(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site)).map(_.player))

  def legalKinds(ready: ReadyGame, actor: PlayerId): Vector[CampaignKind] =
    Vector(
      Option.when(conquestDefender(ready, actor).nonEmpty)(
        CampaignKind.Conquest: CampaignKind),
      Option.when(raidDefenders(ready, actor).nonEmpty)(
        CampaignKind.Raid: CampaignKind)).flatten

  def kindOptions(ready: ReadyGame, actor: PlayerId): Vector[DecisionOption] =
    legalKinds(ready, actor).map {
      case CampaignKind.Conquest => DecisionOption.Button(
        DecisionOptionRef.Button("conquest"), "Conquest")
      case CampaignKind.Raid => DecisionOption.Button(
        DecisionOptionRef.Button("raid"), "Raid")
    }

  def defenderOptions(ready: ReadyGame, actor: PlayerId): Vector[DecisionOption] =
    raidDefenders(ready, actor).map(id =>
      DecisionOption.Player(DecisionOptionRef.Player(id)))

  /** The optional additions: Conquest sites ruled by the same defender, in map
    * order; a Raid's faceup relics and held banners of the defender.
    */
  def targetOptions(ready: ReadyGame, actor: PlayerId, kind: CampaignKind,
      defender: CampaignDefender): Vector[DecisionOption] = {
    val current = ready.game.current
    (kind, defender) match {
      case (CampaignKind.Conquest, _) =>
        val origin = originOf(ready, actor)
        current.map.inPlay.filter(site => !origin.contains(site) &&
          defenderAt(ready, site).contains(defender)).map(site =>
          DecisionOption.Site(DecisionOptionRef.Site(site)))
      case (CampaignKind.Raid, CampaignDefender.Player(id)) =>
        playerOf(ready, id).toVector.flatMap { held =>
          held.relics.filter(_.orientation == Orientation.FaceUp).map(relic =>
            DecisionOption.Relic(DecisionOptionRef.Relic(relic.id)): DecisionOption) ++
            Banner.all.filter(banner => BannerRules.holder(current, banner)
              .contains(id)).map(banner => DecisionOption.Banner(
                DecisionOptionRef.Banner(banner)): DecisionOption)
        }
      case _ => Vector.empty
    }
  }

  /** The chosen kind: the answer, or the only legal kind when the decision was
    * omitted.
    */
  def kindOf(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[CampaignKind] =
    CampaignAnswers.kind(pending).orElse(legalKinds(ready, actor) match {
      case Vector(only) => Some(only)
      case _ => None
    })

  def defenderOf(ready: ReadyGame, actor: PlayerId, pending: PendingTree,
      kind: CampaignKind): Option[CampaignDefender] = kind match {
    case CampaignKind.Conquest => conquestDefender(ready, actor)
    case CampaignKind.Raid => CampaignAnswers.raidDefender(pending)
      .orElse(raidDefenders(ready, actor) match {
        case Vector(only) => Some(only)
        case _ => None
      }).map(CampaignDefender.Player(_))
  }

  /** The complete setup, once the force is answered. */
  def setup(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[CampaignSetup] = for {
    kind <- kindOf(ready, actor, pending)
    origin <- originOf(ready, actor)
    defender <- defenderOf(ready, actor, pending, kind)
    force <- CampaignAnswers.force(pending)
  } yield {
    val picked = CampaignAnswers.targets(pending)
    kind match {
      case CampaignKind.Conquest =>
        val extras = picked.collect { case DecisionOptionRef.Site(id) => id }.toSet
        CampaignSetup(actor, kind, origin, defender, origin +:
          ready.game.current.map.inPlay.filter(site =>
            site != origin && extras(site)), Vector.empty, force)
      case CampaignKind.Raid =>
        val id = defender match {
          case CampaignDefender.Player(player) => player
          case CampaignDefender.Bandits => actor
        }
        val relics = picked.collect {
          case DecisionOptionRef.Relic(relic) =>
            CampaignRaidTarget.Relic(id, relic): CampaignRaidTarget }
        val banners = picked.collect {
          case DecisionOptionRef.Banner(banner) =>
            CampaignRaidTarget.Banner(id, banner): CampaignRaidTarget }
        CampaignSetup(actor, kind, origin, defender, Vector.empty,
          CampaignRaidTarget.canonical(
            Vector[CampaignRaidTarget](CampaignRaidTarget.Pawn(id)) ++
              relics ++ banners), force)
    }
  }
}

/** Readers over the answers recorded so far. The latest answer to a decision
  * wins, which is what a `Repeat` that re-asks one decision id relies on.
  */
object CampaignAnswers {
  private def latest(pending: PendingTree, id: String): Option[DecisionAnswer] =
    pending.answered.reverse.collectFirst { case Answered(`id`, answer, _) => answer }

  def kind(pending: PendingTree): Option[CampaignKind] =
    latest(pending, CampaignIds.kind).collect {
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button(key)) => key
    }.flatMap {
      case "conquest" => Some(CampaignKind.Conquest)
      case "raid" => Some(CampaignKind.Raid)
      case _ => None
    }

  def raidDefender(pending: PendingTree): Option[PlayerId] =
    latest(pending, CampaignIds.defender).collect {
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(id)) => id
    }

  def targets(pending: PendingTree): Vector[DecisionOptionRef] =
    latest(pending, CampaignIds.targets).toVector.flatMap {
      case DecisionAnswer.ChooseManyAnswer(selected) => selected
      case _ => Vector.empty
    }

  def force(pending: PendingTree): Option[Int] =
    latest(pending, CampaignIds.force).collect {
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount
    }

  def sacrificed(pending: PendingTree): Int =
    latest(pending, CampaignIds.sacrifice).collect {
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount
    }.getOrElse(0)

  /** Every source picked in `decisionId` so far, in order, without Finish. */
  def picks(pending: PendingTree, decisionId: String): Vector[DecisionOptionRef] =
    pending.answered.collect {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _)
          if ref != CampaignIds.finish => ref
    }

  def finished(pending: PendingTree, decisionId: String): Boolean =
    latest(pending, decisionId).contains(
      DecisionAnswer.ChooseOneAnswer(CampaignIds.finish))

  /** The site and count of each placement: one target answers with an amount,
    * several with a distribution.
    */
  def placements(pending: PendingTree, targets: Vector[SiteId])
      : Vector[(SiteId, Int)] = latest(pending, CampaignIds.placement) match {
    case Some(DecisionAnswer.ChooseAmountAnswer(count)) =>
      targets.headOption.toVector.map(_ -> count)
    case Some(DecisionAnswer.DistributeAnswer(amounts)) => amounts.collect {
      case DistributeAmount(DecisionOptionRef.Site(site), count) => site -> count
    }
    case _ => Vector.empty
  }

  def relocation(pending: PendingTree): Option[SiteId] =
    latest(pending, CampaignIds.relocation).collect {
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)) => site
    }
}
