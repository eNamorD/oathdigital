package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** The Campaign targets question as the table shows it (board decisions
  * design, "Campaign targets"): the target the Campaign already has, the two
  * sums a player weighs, and why each target not on offer is not.
  */
private[campaign] object CampaignTargets:
  val DefenseLabel = "Defense dice (before plans and powers)"
  val ForceLabel = "Defending force"

  /** The mandatory target: a Conquest's origin, a Raid defender's pawn. */
  def lockedOf(kind: CampaignKind, origin: SiteId,
      defender: CampaignDefender): Option[DecisionOption] =
    (kind, defender) match
      case (CampaignKind.Conquest, _) =>
        Some(DecisionOption.Site(DecisionOptionRef.Site(origin)))
      case (CampaignKind.Raid, CampaignDefender.Player(player)) =>
        Some(DecisionOption.Player(DecisionOptionRef.Player(player)))
      case _ => None

  /** The printed defense and the defending force of each target, from the
    * same sources the battle reads (`CampaignBattle`).
    */
  def tallies(catalog: ExecutableCatalog, ready: ReadyGame,
      targets: Vector[DecisionOptionRef]): Vector[DecisionTally] =
    Vector(
      DecisionTally(DefenseLabel, "defense", targets.map(target =>
        target -> CampaignBattle.defenseOf(catalog, target)).toMap),
      DecisionTally(ForceLabel, "warbands", targets.map(target =>
        target -> CampaignBattle.forceOf(ready, target)).toMap))

  def summary(kind: CampaignKind): DecisionSummary = kind match
    case CampaignKind.Conquest => DecisionSummary("Target", "site", "sites")
    case CampaignKind.Raid => DecisionSummary("Raid", "target", "targets")

  /** The confirm button counts the mandatory target with the additions, so
    * it reads "Raid 1 target" before any pick.
    */
  def confirm(kind: CampaignKind): CountedConfirm =
    CountedConfirm(kind match
      case CampaignKind.Conquest => "Conquer"
      case CampaignKind.Raid => "Raid", "target", "targets",
      countsLocked = true)

  /** The targets the Campaign may not add, each with why: in a Conquest,
    * every other site in play; in a Raid, the banners the defender does not
    * hold and other players' faceup relics.
    */
  def withheld(ready: ReadyGame, actor: PlayerId, kind: CampaignKind,
      origin: SiteId, defender: CampaignDefender,
      offered: Vector[DecisionOption]): Vector[DecisionOption] =
    val current = ready.game.current
    val shown = offered.map(_.ref).toSet
    def unavailable(option: DecisionOption, reason: String): DecisionOption =
      DecisionOption.Unavailable(option, OptionReason.Written(reason))
    (kind, defender) match
      case (CampaignKind.Conquest, _) => current.map.inPlay
        .filter(site => site != origin &&
          !shown(DecisionOptionRef.Site(site)))
        .map(site => unavailable(DecisionOption.Site(
          DecisionOptionRef.Site(site)),
          if CampaignSetup.defenderAt(ready, site)
              .contains(CampaignDefender.Player(actor))
          then "You rule this site" else "Not ruled by the defender"))
      case (CampaignKind.Raid, CampaignDefender.Player(target)) =>
        val relics = current.players
          .filter(held => held.player != actor && held.player != target)
          .flatMap(_.relics.filter(_.orientation == Orientation.FaceUp))
          .map(relic => unavailable(DecisionOption.Relic(
            DecisionOptionRef.Relic(relic.id)), "Not held by the defender"))
        val banners = Banner.all
          .filter(banner => !shown(DecisionOptionRef.Banner(banner)))
          .map(banner => unavailable(DecisionOption.Banner(
            DecisionOptionRef.Banner(banner)),
            if BannerRules.holder(current, banner).contains(actor)
            then "You hold this banner" else "Not held by the defender"))
        relics ++ banners
      case _ => Vector.empty
