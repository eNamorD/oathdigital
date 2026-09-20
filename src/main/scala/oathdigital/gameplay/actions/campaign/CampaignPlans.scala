package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** One battle plan a player could choose now. `ref` is what a decision option
  * names: a card, or the title's button. */
final case class CampaignPlanOption(ref: DecisionOptionRef,
    source: CampaignPlanSource, handlerId: String, side: CampaignPlanSide,
    costs: Vector[CampaignPlanCost], effects: Vector[CampaignPlanEffect],
    order: Int, label: String) {
  /** The option the plan's decision offers. A card is named by the projector;
    * the title has no card, so its button carries the authored label. */
  def queryOption: DecisionOption = source match {
    case CampaignPlanSource.Title(_) =>
      DecisionOption.Button(DecisionOptionRef.Button("title"), label)
    case _ => DecisionOption.forRef(ref).getOrElse(
      throw new IllegalStateException(s"no option for plan ref $ref"))
  }
}

/** The registered Campaign battle plans: what a source offers, what choosing
  * it costs and what it changes. Handlers own printed availability, costs and
  * effects; Campaign orchestrates the windows.
  */
object CampaignPlans {
  val Outriders = "denizen.outriders"
  val BrassArmy = "relic.brass-army.campaign"
  val Title = "title.oathkeeper-defense"
  val Watchdog = "denizen.watchdog"

  private final case class Found(source: CampaignPlanSource,
      handlers: Vector[String], facedown: Boolean, order: Int)

  private def denizenHandlers(catalog: ExecutableCatalog, id: DenizenId) =
    catalog.denizens.find(_.id.value == id.value).toVector.flatMap(_.handlers)
  private def relicHandlers(catalog: ExecutableCatalog, id: RelicId) =
    catalog.relics.find(_.id.value == id.value).toVector.flatMap(_.handlers)

  /** Every card `owner` could use as a plan source: their advisers, their
    * faceup relics, and the denizens at the origin and at sites they rule.
    */
  private def found(catalog: ExecutableCatalog, ready: ReadyGame,
      owner: PlayerId, origin: SiteId): Vector[Found] = {
    val current = ready.game.current
    val player = current.players.find(_.player == owner)
    val advisers = player.toVector.flatMap(_.advisers.collect {
      case card: DenizenState => Found(CampaignPlanSource.Adviser(owner, card.id),
        denizenHandlers(catalog, card.id),
        card.orientation == Orientation.FaceDown, 100)
    })
    val relics = player.toVector.flatMap(_.relics
      .filter(_.orientation == Orientation.FaceUp).map(relic => Found(
        CampaignPlanSource.Relic(owner, relic.id),
        relicHandlers(catalog, relic.id), false, 200)))
    val ruled = current.map.inPlay.filter(site => SiteRule.ruledBy(
      current.map.sites(site).forces, current.players, owner).getOrElse(false))
    val siteCards = (origin +: ruled).distinct.flatMap(site =>
      current.map.sites.get(site).toVector.flatMap(_.denizens.collect {
        case card: DenizenState => Found(CampaignPlanSource.SiteCard(site, card.id),
          denizenHandlers(catalog, card.id),
          card.orientation == Orientation.FaceDown, 300)
      }))
    advisers ++ relics ++ siteCards
  }

  private def refOf(source: CampaignPlanSource): DecisionOptionRef = source match {
    case CampaignPlanSource.Adviser(_, id) => DecisionOptionRef.Denizen(id)
    case CampaignPlanSource.SiteCard(_, id) => DecisionOptionRef.Denizen(id)
    case CampaignPlanSource.Relic(_, id) => DecisionOptionRef.Relic(id)
    case CampaignPlanSource.Title(_) => DecisionOptionRef.Button("title")
  }

  private def inCradle(ready: ReadyGame, setup: CampaignSetup): Boolean =
    setup.targetSites.exists(site =>
      ready.game.current.map.regionOf(site).contains(Region.Cradle))

  private def plan(ready: ReadyGame, setup: CampaignSetup, side: CampaignPlanSide,
      owner: PlayerId, from: Found, handler: String): Option[CampaignPlanOption] = {
    def option(label: String, costs: Vector[CampaignPlanCost],
        effects: Vector[CampaignPlanEffect]) = CampaignPlanOption(
      refOf(from.source), from.source, handler, side, costs, effects,
      from.order, label)
    handler match {
      case Outriders if side == CampaignPlanSide.Attacker && owner == setup.actor =>
        Some(option("Outriders: ignore all attack skulls", Vector.empty,
          (if (from.facedown) Vector[CampaignPlanEffect](
            CampaignPlanEffect.RevealSource) else Vector.empty) :+
            CampaignPlanEffect.IgnoreAttackSkulls))
      case BrassArmy if side == CampaignPlanSide.Attacker && owner == setup.actor =>
        from.source match {
          case CampaignPlanSource.Relic(player, relicId)
              if ready.game.current.players.find(_.player == player).exists(p =>
                p.board.faceUpSecrets >= 1 && p.relics.exists(r =>
                  r.id == relicId && r.orientation == Orientation.FaceUp &&
                    r.tokens.isEmpty)) =>
            Some(option("Brass Army: add 4 attack dice",
              Vector(CampaignPlanCost.Secret(1)),
              Vector(CampaignPlanEffect.AddAttackDice(4))))
          case _ => None
        }
      case Watchdog if side == CampaignPlanSide.Defender && inCradle(ready, setup) =>
        Some(option("Watchdog: add 1 defense die", Vector.empty,
          Vector(CampaignPlanEffect.AddDefenseDice(1))))
      case _ => None
    }
  }

  private def title(ready: ReadyGame, setup: CampaignSetup, owner: PlayerId)
      : Option[CampaignPlanOption] = {
    val held = ready.game.current.title
    Option.when(held.holder.contains(owner) &&
        setup.defender == CampaignDefender.Player(owner)) {
      val dice = held.side match {
        case TitleSide.Oathkeeper => 1
        case TitleSide.Usurper => 2
      }
      CampaignPlanOption(DecisionOptionRef.Button("title"),
        CampaignPlanSource.Title(owner), Title, CampaignPlanSide.Defender,
        Vector.empty, Vector(CampaignPlanEffect.AddDefenseDice(dice)), 0,
        s"${held.side} title: add $dice defense ${if (dice == 1) "die" else "dice"}")
    }
  }

  /** The plans `owner` could choose on `side`, in stable order. A defender plan
    * with a cost is never offered: none is registered, and a defender has no
    * cost-paying step.
    */
  def options(catalog: ExecutableCatalog, ready: ReadyGame, setup: CampaignSetup,
      side: CampaignPlanSide, owner: PlayerId): Vector[CampaignPlanOption] = {
    val printed = found(catalog, ready, owner, setup.origin).flatMap(source =>
      source.handlers.flatMap(handler =>
        plan(ready, setup, side, owner, source, handler)))
    val all = (if (side == CampaignPlanSide.Defender)
      title(ready, setup, owner).toVector else Vector.empty) ++ printed
    all.filter(o => o.side == CampaignPlanSide.Attacker || o.costs.isEmpty)
      .sortBy(o => (o.order, o.source.stableKey, o.handlerId))
  }

  /** The plans still unchosen: a source may be chosen once. */
  def available(catalog: ExecutableCatalog, ready: ReadyGame, setup: CampaignSetup,
      side: CampaignPlanSide, owner: PlayerId, picked: Vector[DecisionOptionRef])
      : Vector[CampaignPlanOption] =
    options(catalog, ready, setup, side, owner).filterNot(o => picked.contains(o.ref))

  /** A bandit defender uses every cost-free plan of a faceup bandit-ruled site
    * card, without choosing.
    */
  def banditPlans(catalog: ExecutableCatalog, ready: ReadyGame,
      setup: CampaignSetup): Vector[CampaignPlanOption] =
    if (setup.defender != CampaignDefender.Bandits) Vector.empty
    else {
      val current = ready.game.current
      current.map.inPlay.filter(site => CampaignSetup.defenderAt(ready, site)
        .contains(CampaignDefender.Bandits)).flatMap(site =>
        current.map.sites(site).denizens.collect {
          case card: DenizenState if card.orientation == Orientation.FaceUp =>
            Found(CampaignPlanSource.SiteCard(site, card.id),
              denizenHandlers(catalog, card.id), false, 300)
        }).flatMap(source => source.handlers.flatMap(handler => plan(ready,
        setup, CampaignPlanSide.Defender, setup.actor, source, handler)))
        .filter(_.costs.isEmpty)
        .sortBy(o => (o.order, o.source.stableKey, o.handlerId))
    }

  /** Whether an answered pick was Outriders. Read from the answers because the
    * plan's own option changes once it is paid.
    */
  def ignoresSkulls(catalog: ExecutableCatalog,
      picks: Vector[DecisionOptionRef]): Boolean = picks.exists {
    case DecisionOptionRef.Denizen(id) =>
      denizenHandlers(catalog, id).contains(Outriders)
    case _ => false
  }

  /** The operations that pay for and apply a plan: the cost moves onto the
    * source card, a facedown source is revealed, and dice join their pool.
    */
  def apply(option: CampaignPlanOption, owner: PlayerId): Vector[CoreOperation] = {
    val card: Option[CardId] = option.source match {
      case CampaignPlanSource.Adviser(_, id) => Some(id)
      case CampaignPlanSource.SiteCard(_, id) => Some(id)
      case CampaignPlanSource.Relic(_, id) => Some(id)
      case CampaignPlanSource.Title(_) => None
    }
    val payments: Vector[CoreOperation] = card.toVector.flatMap(target =>
      option.costs.map {
        case CampaignPlanCost.Favor(count) => Move(Piece.Favor(count),
          PositionedLocation(Location.PlayArea(owner)),
          PositionedLocation(Location.OnCard(target)))
        case CampaignPlanCost.Secret(count) => Move(Piece.Secrets(count),
          PositionedLocation(Location.PlayArea(owner)),
          PositionedLocation(Location.OnCard(target)))
      })
    val reveal: Vector[CoreOperation] =
      if (!option.effects.contains(CampaignPlanEffect.RevealSource)) Vector.empty
      else option.source match {
        case CampaignPlanSource.Adviser(player, id) => Vector(Move(Piece.Card(id),
          PositionedLocation(Location.PlayArea(player)),
          PositionedLocation(Location.PlayArea(player)),
          resultingOrientation = Some(Orientation.FaceUp)))
        case CampaignPlanSource.SiteCard(site, id) =>
          Vector(Reveal(id, Location.Site(site)))
        case _ => Vector.empty
      }
    val dice: Vector[CoreOperation] = option.effects.collect {
      case CampaignPlanEffect.AddAttackDice(count) =>
        ModifyDicePool(CampaignIds.attackPool, count): CoreOperation
      case CampaignPlanEffect.AddDefenseDice(count) =>
        ModifyDicePool(CampaignIds.defensePool, count): CoreOperation
    }
    payments ++ reveal ++ dice
  }
}
