package oathdigital.gameplay.actions.campaign

import oathdigital.gameplay.actions.{BannerRules, VisionRules}
import oathdigital.model._

/** Step 9 for a Raid victory: the printed transfer (the takes, then the
  * consequences in a window of their own), then the pawn's relocation. All
  * read the durable [[CampaignResult]].
  */
private[campaign] object CampaignRaid:
  def steps(ready: ReadyGame, actor: PlayerId, result: CampaignResult)
      : Vector[Operation] = result.defender match
    case CampaignDefender.Player(defender) => Vector(
      Sequence(Vector[Operation](
        BuildOps((state, _) => takes(state, result)),
        Sequence(Vector[Operation](
          BuildOps((state, _) => consequences(state, result))),
          Some(PowerWindow.CampaignRaidConsequences))),
        Some(PowerWindow.CampaignRaidTransfer)),
      Sequence(Vector[Operation](
        Decide(CampaignIds.relocation, actor, DecisionQuery.ChooseOne(
          relocationSites(ready, defender).map(site =>
            DecisionOption.Site(DecisionOptionRef.Site(site))),
          heading = Some("Move the defeated pawn to another site"),
          confirmVerb = Some("Move pawn to"))),
        BuildOps((state, pending) => relocate(state, defender, pending))),
        Some(PowerWindow.CampaignRaidRelocation)))
    case CampaignDefender.Bandits => Vector.empty

  /** Every in-play site except the one the defender's pawn stands on. The
    * pawn does not move before the relocation, so this reads the same when
    * the decision is selected again after the transfer.
    */
  def relocationSites(ready: ReadyGame, defender: PlayerId): Vector[SiteId] =
    val origin = ready.game.current.players.find(_.player == defender)
      .flatMap(_.pawnSite)
    ready.game.current.map.inPlay.filterNot(site => origin.contains(site))

  private def next(region: Region): Region = region match
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle

  /** The Raid's defender and the region its facedown advisers are
    * discarded to, the one after the origin's, from live state. */
  private def defeated(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, (PlayerState, Region)] =
    val current = ready.game.current
    for
      defenderId <- result.defender match
        case CampaignDefender.Player(player) => Right(player)
        case CampaignDefender.Bandits => Left(OathViolation.InvalidEventOrder(
          "a Raid requires a player defender"))
      defender <- current.players.find(_.player == defenderId).toRight(
        OathViolation.InvalidEventOrder("the Raid's defender is not in the game"))
      origin <- CampaignSetup.originOf(ready, result.attacker).toRight(
        OathViolation.PawnSiteMissing(result.attacker))
      region <- current.map.regionOf(origin).toRight(
        OathViolation.InvalidEventOrder("the Raid's origin has no region"))
    yield (defender, next(region))

  /** What the attacker targeted, taken: the relics, then the banners. A
    * banner's favor and secrets leave with it. */
  def takes(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] =
    defeated(ready, result).map { (defender, _) =>
      val current = ready.game.current
      val defenderId = defender.player
      val attacker = result.attacker
      val relics = result.raidTargets.collect {
        case CampaignRaidTarget.Relic(_, id) => id }
      val banners = result.raidTargets.collect {
        case CampaignRaidTarget.Banner(_, banner) => banner }
      val relicTakes: Vector[CoreOperation] = relics.map(id => Take(
        Piece.Card(id), attacker, Location.PlayArea(defenderId),
        Location.PlayArea(attacker)))
      // A banner's favor and secrets leave as its Take's children, so a
      // refused Take leaves the banner whole.
      val bannerOps: Vector[CoreOperation] = banners.map { banner =>
        val leaving: Vector[CoreOperation] = banner match
          case Banner.PeoplesFavor =>
            val returned = BannerRules.raidFavorReturn(ready.banks.favor,
              BannerRules.resources(current, banner)).groupBy(identity)
              .view.mapValues(_.size).toMap
            Suit.all.flatMap(suit => returned.get(suit).filter(_ > 0).map(count =>
              Move(Piece.Favor(count),
                PositionedLocation(Location.OnBanner(banner)),
                PositionedLocation(Location.FavorBank(suit))): CoreOperation))
          case Banner.DarkestSecret =>
            val secrets = BannerRules.resources(current, banner)
            Option.when(secrets > 0)(Burn.secrets(secrets,
              PositionedLocation(Location.OnBanner(banner)))).toVector
        Take(Piece.Banner(banner), attacker, Location.PlayArea(defenderId),
          Location.PlayArea(attacker), leaving = leaving)
      }
      relicTakes ++ bannerOps
    }

  /** What the defeat costs the defender beyond the takes, in the printed
    * order: the facedown advisers discarded, the facedown relics set
    * aside, half the favor burnt, and a facedown Conspiracy returned to
    * the box. Read after the takes, which change none of it. */
  def consequences(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] =
    defeated(ready, result).map { (defender, discardRegion) =>
      val defenderId = defender.player
      val facedown: Vector[WorldCardId] = defender.advisers.collect:
        case card: DenizenState if card.orientation == Orientation.FaceDown =>
          card.id: WorldCardId
        case vision: VisionState if vision.orientation == Orientation.FaceDown =>
          vision.id: WorldCardId
      val discards: Vector[CoreOperation] = facedown
        .filterNot(_ == VisionRules.Conspiracy).map(id => Move(Piece.Card(id),
          PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.RegionalDiscard(discardRegion),
            StackPosition.Top), resultingOrientation = Some(Orientation.FaceDown)))
      val boxed: Vector[CoreOperation] = facedown
        .filter(_ == VisionRules.Conspiracy).map(id => Move(Piece.Card(id),
          PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.SharedBank)))
      val setAside: Vector[CoreOperation] = defender.relics
        .filter(_.orientation == Orientation.FaceDown).map(relic => Move(
          Piece.Card(relic.id), PositionedLocation(Location.PlayArea(defenderId)),
          PositionedLocation(Location.SetAsideRelics)))
      val burn: Vector[CoreOperation] = Option.when(defender.board.favor / 2 > 0)(
        Burn.favor(defender.board.favor / 2,
          PositionedLocation(Location.PlayArea(defenderId)))).toVector
      discards ++ setAside ++ burn ++ boxed
    }

  private def relocate(ready: ReadyGame, defender: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for
    origin <- ready.game.current.players.find(_.player == defender)
      .flatMap(_.pawnSite).toRight(OathViolation.PawnSiteMissing(defender))
    destination <- CampaignAnswers.relocation(pending).toRight(
      OathViolation.InvalidEventOrder("the Raid's relocation was not answered"))
  yield Vector[CoreOperation](Move(Piece.Pawn(defender),
    PositionedLocation(Location.Site(origin)),
    PositionedLocation(Location.Site(destination))))
