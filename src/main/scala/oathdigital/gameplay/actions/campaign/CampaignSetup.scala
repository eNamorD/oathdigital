package oathdigital.gameplay.actions.campaign

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** Decision ids and pool keys of the Campaign procedure. */
object CampaignIds:
  val kind = "campaign.kind"
  val defender = "campaign.defender"
  val targets = "campaign.targets"
  val force = "campaign.force"
  val attackerPlan = "campaign.attacker-plan"
  val defenderPlan = "campaign.defender-plan"
  val sacrifice = "campaign.sacrifice"
  val placement = "campaign.placement"
  val relocation = "campaign.relocation"
  /** Which of a defender's forces pays a plan's warband sacrifice. */
  val planSacrifice = "campaign.plan-sacrifice"
  /** How many of the skull losses die at a queued origin. */
  val skullOriginDeaths = "campaign.skull-origin-deaths"
  /** How many of the sacrificed warbands die at a queued origin. */
  val sacrificeOriginDeaths = "campaign.sacrifice-origin-deaths"
  /** How many of the defeat's half die at a queued origin. */
  val originDeaths = "campaign.origin-deaths"
  val all: Set[String] = Set(kind, defender, targets, force, attackerPlan,
    defenderPlan, sacrifice, placement, relocation, planSacrifice,
    skullOriginDeaths, sacrificeOriginDeaths, originDeaths)

  /** The decision a side's plan window asks. */
  def planDecision(side: CampaignPlanSide): String = side match
    case CampaignPlanSide.Attacker => attackerPlan
    case CampaignPlanSide.Defender => defenderPlan

  val attackPool: PoolKey = PoolKey("campaign.attack")
  val defensePool: PoolKey = PoolKey("campaign.defense")

  /** What each sacrificed warband adds, recorded by a plan when it is
    * applied (Zealots); one while the pool is absent. It is never rolled,
    * and it is cleared with the others when the Campaign ends. */
  val sacrificeWorth: PoolKey = PoolKey("campaign.sacrifice-worth")

  /** Recorded by an attacker's plan that spares a defeated force
    * (Billowing Fog, Traveling Doctor), and by Peace Envoy on either side:
    * the losses then kill none of the attacker's force. It is never
    * rolled, and it is cleared with the others when the Campaign ends. */
  val attackerSpared: PoolKey = PoolKey("campaign.attacker-spared")

  val SupplyCost = 2

  /** The kind question's last option when a power at the Campaign's start
    * offered it: the attacker ends the Campaign instead. */
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")

  /** Recorded by a power that moved pieces at the Campaign's start (Vow of
    * Union), so the kind question offers `skip`. It is never rolled, and it
    * is cleared with the other pools when the Campaign ends. */
  val skipOffered: PoolKey = PoolKey("campaign.skip-offered")

  /** The option that ends a plan window. */
  val finish: DecisionOptionRef.Button = DecisionOptionRef.Button("finish")

/** What a Campaign's early answers say. `targetSites` is the mandatory origin
  * plus the answered additions in map order (Conquest); `raidTargets` is the
  * pawn plus the answered relics and banners in canonical order (Raid).
  */
final case class CampaignSetup(actor: PlayerId, kind: CampaignKind,
    origin: SiteId, defender: CampaignDefender, targetSites: Vector[SiteId],
    raidTargets: Vector[CampaignRaidTarget], force: Int)

object CampaignSetup:
  private def playerOf(ready: ReadyGame, id: PlayerId): Option[PlayerState] =
    ready.game.current.players.find(_.player == id)

  /** The `Run` entry a power queued for this Campaign's walk, while it
    * runs for `actor`. A menu Campaign has none. */
  private def queuedRun(ready: ReadyGame, actor: PlayerId)
      : Option[QueuedProcedure] =
    ready.game.current.walkerEntry.filter(entry =>
      entry.procedure == ActionRef.Campaign && entry.actor == actor &&
        entry.source.nonEmpty && entry.kind == QueueKind.Run)

  /** The origin a power's queued Campaign names, while that Campaign's
    * walk runs for `actor` (catalog batch 8, slice 13): its one start
    * argument, a site. Only a `Run` entry with a source carries one; a menu
    * Campaign never does. */
  def queuedOrigin(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    queuedRun(ready, actor).flatMap(_.startArgs match
      case Vector(DecisionOptionRef.Site(site)) => Some(site)
      case _ => None)

  /** The defender a power's queued Campaign declares, while that
    * Campaign's walk runs for `actor` (catalog batch 8, slice 14): Sneak
    * Attack's earlier attacker, its one start argument. Only that player
    * may defend: the Conquest needs them ruling the origin, and the Raid
    * needs their pawn there. */
  def queuedDefender(ready: ReadyGame, actor: PlayerId): Option[PlayerId] =
    queuedRun(ready, actor).flatMap(_.startArgs match
      case Vector(DecisionOptionRef.Player(player)) => Some(player)
      case _ => None)

  /** Where `actor` campaigns from: a queued origin, else their pawn site.
    * The mandatory Conquest site, and where a Raid's defender must stand.
    * For any other player it is their pawn site. */
  def originOf(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    queuedOrigin(ready, actor).orElse(playerOf(ready, actor)
      .flatMap(_.pawnSite))

  /** The actor's warbands at a queued origin, which join the force. A
    * Campaign from the pawn site adds none. */
  def atOrigin(ready: ReadyGame, actor: PlayerId): Int = (for
    site <- queuedOrigin(ready, actor)
    player <- playerOf(ready, actor)
    kind <- PlayerForceKind.of(ready, player)
    state <- ready.game.current.map.sites.get(site)
  yield state.forces match
    case SiteForces.Occupied(`kind`, count) => count
    case _ => 0).getOrElse(0)

  /** The origin's warbands and the board's as they stand, when a queued
    * origin's warbands joined the force: what each early kill splits over,
    * and what the result records for the losses. */
  def originForce(ready: ReadyGame, actor: PlayerId): Option[OriginForce] =
    for
      site <- queuedOrigin(ready, actor)
      atSite = atOrigin(ready, actor)
      if atSite > 0
      player <- playerOf(ready, actor)
    yield OriginForce(site, atSite, player.board.warbands)

  /** Who rules `site` as a Campaign defender. `None` when the site is
    * unruled, Imperial or its rule is corrupt.
    */
  def defenderAt(ready: ReadyGame, site: SiteId): Option[CampaignDefender] =
    ready.game.current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, ready.game.current.players).toOption)
      .flatMap:
        case SiteRuler.Bandits => Some(CampaignDefender.Bandits)
        case SiteRuler.Player(player) => Some(CampaignDefender.Player(player))
        case _ => None

  def conquestDefender(ready: ReadyGame, actor: PlayerId)
      : Option[CampaignDefender] =
    originOf(ready, actor).flatMap(defenderAt(ready, _))
      .filterNot(_ == CampaignDefender.Player(actor))
      .filter(defender => queuedDefender(ready, actor)
        .forall(player => defender == CampaignDefender.Player(player)))

  def raidDefenders(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    originOf(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site) &&
        queuedDefender(ready, actor).forall(_ == p.player)).map(_.player))

  /** The kinds `actor` could campaign with against `defender` from their
    * own pawn site, with no Campaign of theirs under way: whether Sneak
    * Attack's Campaign has a target (catalog batch 8, slice 14, ruling 9).
    * In Conquest order, then Raid. */
  def kindsAgainst(ready: ReadyGame, actor: PlayerId, defender: PlayerId)
      : Vector[CampaignKind] =
    val site = playerOf(ready, actor).flatMap(_.pawnSite)
    Vector(
      Option.when(site.flatMap(defenderAt(ready, _))
          .contains(CampaignDefender.Player(defender)))(
        CampaignKind.Conquest: CampaignKind),
      Option.when(site.exists(at => playerOf(ready, defender)
          .exists(_.pawnSite.contains(at))))(
        CampaignKind.Raid: CampaignKind)).flatten

  def legalKinds(ready: ReadyGame, actor: PlayerId): Vector[CampaignKind] =
    Vector(
      Option.when(conquestDefender(ready, actor).nonEmpty)(
        CampaignKind.Conquest: CampaignKind),
      Option.when(raidDefenders(ready, actor).nonEmpty)(
        CampaignKind.Raid: CampaignKind)).flatten

  def kindOptions(ready: ReadyGame, actor: PlayerId): Vector[DecisionOption] =
    legalKinds(ready, actor).map:
      case CampaignKind.Conquest => DecisionOption.Button(
        DecisionOptionRef.Button("conquest"), "Conquest")
      case CampaignKind.Raid => DecisionOption.Button(
        DecisionOptionRef.Button("raid"), "Raid")

  def defenderOptions(ready: ReadyGame, actor: PlayerId): Vector[DecisionOption] =
    raidDefenders(ready, actor).map(id =>
      DecisionOption.Player(DecisionOptionRef.Player(id)))

  /** The optional additions: Conquest sites ruled by the same defender, in map
    * order; a Raid's faceup relics and held banners of the defender.
    */
  def targetOptions(ready: ReadyGame, actor: PlayerId, kind: CampaignKind,
      defender: CampaignDefender): Vector[DecisionOption] =
    val current = ready.game.current
    (kind, defender) match
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

  /** Whether a power at this Campaign's start offered the Skip. */
  def skipOffered(ready: ReadyGame): Boolean =
    ready.game.current.rollPools.contains(CampaignIds.skipOffered)

  /** The chosen kind: the pinned kind once the pools are gathered; before
    * that, the answer, or the only legal kind when the decision was omitted.
    * No kind is settled once the attacker skipped, nor by the one legal kind
    * while the Skip is on offer.
    */
  def kindOf(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[CampaignKind] =
    if CampaignAnswers.skipped(pending) then None
    else pinned(ready).map(_._1).orElse(
      CampaignAnswers.kind(pending).orElse(legalKinds(ready, actor) match {
        case Vector(only) if !skipOffered(ready) => Some(only)
        case _ => None
      }))

  /** The defender: the pinned one once the pools are gathered; before that,
    * the ruler of the origin (Conquest) or the pawn answered or alone there
    * (Raid).
    */
  def defenderOf(ready: ReadyGame, actor: PlayerId, pending: PendingTree,
      kind: CampaignKind): Option[CampaignDefender] =
    pinned(ready).map(_._2).orElse(kind match {
      case CampaignKind.Conquest => conquestDefender(ready, actor)
      case CampaignKind.Raid => CampaignAnswers.raidDefender(pending)
        .orElse(raidDefenders(ready, actor) match {
          case Vector(only) => Some(only)
          case _ => None
        }).map(CampaignDefender.Player(_))
    })

  /** This Campaign's kind and defender at one point of its run: the pair
    * pinned when the pools were gathered, else the pair the answers so far
    * and the board settle. `None` until both are settled, and once the
    * attacker skipped. One name for a read that three callers used to
    * compose by hand.
    */
  def declaredAt(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[(CampaignKind, CampaignDefender)] = for
    kind <- kindOf(ready, actor, pending)
    defender <- defenderOf(ready, actor, pending, kind)
  yield (kind, defender)

  /** The pool that pins the kind and the defender, written when the pools are
    * gathered, before any battle plan. The live board names them only until
    * then: a plan can change it, as Wrestlers empties the origin by
    * sacrificing its last warband, and the Campaign still goes on against the
    * defender it was declared against. The pool is never rolled, and it is
    * cleared with the others when the Campaign ends.
    */
  def pin(setup: CampaignSetup): ModifyDicePool =
    val kind = setup.kind match
      case CampaignKind.Conquest => "conquest"
      case CampaignKind.Raid => "raid"
    val defender = setup.defender match
      case CampaignDefender.Bandits => "bandits"
      case CampaignDefender.Player(player) => s"player.${player.value}"
    ModifyDicePool(PoolKey(s"$PinPrefix$kind.$defender"), 1)

  /** The kind and defender `pin` recorded, or `None` before the pools are
    * gathered. A player id may hold dots, so it is the rest of the key.
    */
  def pinned(ready: ReadyGame): Option[(CampaignKind, CampaignDefender)] =
    ready.game.current.rollPools.keys.map(_.value)
      .filter(_.startsWith(PinPrefix)).flatMap(key =>
        key.stripPrefix(PinPrefix).split("\\.", 3).toList match
          case kind :: "bandits" :: Nil => kindNamed(kind)
            .map(_ -> (CampaignDefender.Bandits: CampaignDefender))
          case kind :: "player" :: player :: Nil => kindNamed(kind)
            .map(_ -> (CampaignDefender.Player(PlayerId(player)): CampaignDefender))
          case _ => None).headOption

  private val PinPrefix = "campaign.setup."

  private def kindNamed(name: String): Option[CampaignKind] = name match
    case "conquest" => Some(CampaignKind.Conquest)
    case "raid" => Some(CampaignKind.Raid)
    case _ => None

  /** The complete setup, once the force is answered. */
  def setup(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[CampaignSetup] = for
    (kind, defender) <- declaredAt(ready, actor, pending)
    origin <- originOf(ready, actor)
    force <- CampaignAnswers.force(pending)
  yield
    val picked = CampaignAnswers.targets(pending)
    kind match
      case CampaignKind.Conquest =>
        val extras = picked.collect { case DecisionOptionRef.Site(id) => id }.toSet
        CampaignSetup(actor, kind, origin, defender, origin +:
          ready.game.current.map.inPlay.filter(site =>
            site != origin && extras(site)), Vector.empty, force)
      case CampaignKind.Raid =>
        val id = defender match
          case CampaignDefender.Player(player) => player
          case CampaignDefender.Bandits => actor
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

/** Readers over the answers recorded so far. The latest answer to a decision
  * wins, which is what a `Repeat` that re-asks one decision id relies on.
  */
object CampaignAnswers:
  private def latest(pending: PendingTree, id: String): Option[DecisionAnswer] =
    pending.answered.reverse.collectFirst { case Answered(`id`, answer, _) => answer }

  /** Whether the attacker answered the kind question with Skip. */
  def skipped(pending: PendingTree): Boolean =
    latest(pending, CampaignIds.kind).contains(
      DecisionAnswer.ChooseOneAnswer(CampaignIds.skip))

  def kind(pending: PendingTree): Option[CampaignKind] =
    latest(pending, CampaignIds.kind).collect {
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button(key)) => key
    }.flatMap:
      case "conquest" => Some(CampaignKind.Conquest)
      case "raid" => Some(CampaignKind.Raid)
      case _ => None

  def raidDefender(pending: PendingTree): Option[PlayerId] =
    latest(pending, CampaignIds.defender).collect:
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(id)) => id

  def targets(pending: PendingTree): Vector[DecisionOptionRef] =
    latest(pending, CampaignIds.targets).toVector.flatMap:
      case DecisionAnswer.ChooseManyAnswer(selected) => selected
      case _ => Vector.empty

  def force(pending: PendingTree): Option[Int] =
    latest(pending, CampaignIds.force).collect:
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount

  /** The answer to one of the origin split questions, `id`. */
  def originDeaths(pending: PendingTree, id: String): Option[Int] =
    latest(pending, id).collect:
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount

  def sacrificed(pending: PendingTree): Int =
    latest(pending, CampaignIds.sacrifice).collect {
      case DecisionAnswer.ChooseAmountAnswer(amount) => amount
    }.getOrElse(0)

  /** Every source picked in `decisionId` so far, in order, without Finish. */
  def picks(pending: PendingTree, decisionId: String): Vector[DecisionOptionRef] =
    pending.answered.collect:
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _)
          if ref != CampaignIds.finish => ref

  def finished(pending: PendingTree, decisionId: String): Boolean =
    latest(pending, decisionId).contains(
      DecisionAnswer.ChooseOneAnswer(CampaignIds.finish))

  /** The site and count of each placement: one target answers with an amount,
    * several with a distribution.
    */
  def placements(pending: PendingTree, targets: Vector[SiteId])
      : Vector[(SiteId, Int)] = latest(pending, CampaignIds.placement) match
    case Some(DecisionAnswer.ChooseAmountAnswer(count)) =>
      targets.headOption.toVector.map(_ -> count)
    case Some(DecisionAnswer.DistributeAnswer(amounts)) => amounts.collect:
      case DistributeAmount(DecisionOptionRef.Site(site), count) => site -> count
    case _ => Vector.empty

  /** The latest source picked in `decisionId`, or `None` when the latest answer
    * was Finish or there is none.
    */
  def lastPick(pending: PendingTree, decisionId: String): Option[DecisionOptionRef] =
    latest(pending, decisionId).collect:
      case DecisionAnswer.ChooseOneAnswer(ref) if ref != CampaignIds.finish => ref

  def relocation(pending: PendingTree): Option[SiteId] =
    latest(pending, CampaignIds.relocation).collect:
      case DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)) => site
