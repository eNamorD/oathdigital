package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** League Treaty (card 237): before Rest cleanup, the ruler of the treaty's
  * site may send the region's card favor to one bank instead of each card's
  * printed bank.
  *
  * The transform inserts two decisions and zero or more moves ahead of cleanup.
  * Both decisions come before anything it changes, so the transform sees the
  * same state, and folds to the same vector, while either is parked.
  */
final case class LeagueTreatyContribution private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  import LeagueTreatyContribution._

  def id: PowerId = LeagueTreatyContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.RestReturnFavor -> Vector(Transform((ctx, ops) =>
      treaty(ctx.state).fold(ops)(inserted(ctx.state, ctx.activePlayer, _) ++ ops))))

  /** One region card holding favor, in map site order then site card order. */
  private final case class Holding(card: CardId, suit: Suit, favor: Int)
  private final case class Treaty(site: SiteId, ruler: PlayerId,
      holdings: Vector[Holding]) {
    def favorOf(suit: Suit): Int = holdings.filter(_.suit == suit).map(_.favor).sum
    def suits: Vector[Suit] = Suit.all.filter(favorOf(_) > 0)
    def total: Int = holdings.map(_.favor).sum
  }

  private def treaty(ready: ReadyGame): Option[Treaty] = {
    val current = ready.game.current
    for {
      site <- current.map.inPlay.find(id => current.map.sites.get(id).exists(
        _.denizens.exists {
          case DenizenState(`cardId`, Orientation.FaceUp, _) => true
          case _ => false
        }))
      ruler <- SiteRule.ruler(current.map.sites(site).forces, current.players)
        .toOption.collect { case SiteRuler.Player(player) => player }
      region <- current.map.regionOf(site)
      holdings = current.map.inPlay.filter(current.map.regionOf(_)
          .contains(region)).flatMap { id =>
        current.map.sites(id).denizens.collect {
          case DenizenState(card, Orientation.FaceUp, tokens) if tokens.favor > 0 =>
            card -> tokens.favor
          case EdificeState(card, _, tokens) if tokens.favor > 0 =>
            card -> tokens.favor
        }.flatMap { case (card, favor) =>
          catalog.suitOf(card).map(Holding(card, _, favor))
        }
      }
      if holdings.nonEmpty
    } yield Treaty(site, ruler, holdings)
  }

  private def inserted(ready: ReadyGame, rester: PlayerId,
      treaty: Treaty): Vector[Operation] = {
    val destination = destinationDecisionId(ready, rester, treaty.site, cardId)
    val distribution = distributionDecisionId(ready, rester, treaty.site, cardId)
    Vector(
      Decide(destination, treaty.ruler, DecisionQuery.ChooseOne(
        Suit.all.map(suit => DecisionOption.FavorBank(
          DecisionOptionRef.FavorBank(suit))) :+
          DecisionOption.Button(DecisionOptionRef.Button(Decline), "Decline"),
        heading = Some("League Treaty: send this region's favor to one bank"))),
      Branch((_, pending) => chosenBank(pending, destination)
        .filter(bank => treaty.suits.exists(_ != bank))
        .map(bank => Decide(distribution, treaty.ruler, query(treaty, bank)))
        .toVector),
      BuildOps((_, pending) => chosenBank(pending, destination) match {
        case Some(bank) if treaty.suits.exists(_ != bank) =>
          amounts(pending, distribution).map(moves(treaty, bank, _))
        case _ => Right(Vector.empty)
      }))
  }

  private def query(treaty: Treaty, bank: Suit): DecisionQuery.Distribute =
    DecisionQuery.Distribute(
      treaty.suits.filter(_ != bank).map { suit =>
        val maximum = treaty.favorOf(suit)
        DistributeSlot(DecisionOptionRef.FavorBank(suit), 0, maximum,
          Some(maximum))
      } :+ DistributeSlot(DecisionOptionRef.FavorBank(bank),
        treaty.favorOf(bank), treaty.total, Some(treaty.favorOf(bank))),
      total = treaty.total,
      heading = Some("League Treaty: how much favor stays with its own bank?"),
      confirmLabel = "Send favor")

  /** For each source suit, what the ruler did not leave behind moves to the
    * destination, taken card by card in holding order.
    */
  private def moves(treaty: Treaty, bank: Suit,
      kept: Map[Suit, Int]): Vector[CoreOperation] =
    treaty.suits.filter(_ != bank).flatMap { suit =>
      val cards = treaty.holdings.filter(_.suit == suit)
      cards.foldLeft((treaty.favorOf(suit) - kept.getOrElse(suit, 0),
          Vector.empty[CoreOperation])) { case ((left, ops), holding) =>
        val taken = math.min(left, holding.favor)
        (left - taken, if (taken == 0) ops else ops :+ Move(Piece.Favor(taken),
          PositionedLocation(Location.OnCard(holding.card)),
          PositionedLocation(Location.FavorBank(bank))))
      }._2
    }
}

object LeagueTreatyContribution {
  val id: PowerId = PowerId("denizen.league-treaty")
  private val Decline = "decline"

  def forCatalog(catalog: ExecutableCatalog): Option[LeagueTreatyContribution] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(d => new LeagueTreatyContribution(DenizenId(d.id.value), catalog))

  private def stem(ready: ReadyGame, rester: PlayerId, site: SiteId,
      card: DenizenId) = s"rest-${ready.game.current.tracks.round}-" +
    s"${rester.value}-${id.value}-${site.value}-${card.value}"
  def destinationDecisionId(ready: ReadyGame, rester: PlayerId, site: SiteId,
      card: DenizenId): String = stem(ready, rester, site, card) + "-destination"
  def distributionDecisionId(ready: ReadyGame, rester: PlayerId, site: SiteId,
      card: DenizenId): String = stem(ready, rester, site, card) + "-distribution"

  private def chosenBank(pending: PendingTree, decision: String): Option[Suit] =
    pending.answered.collectFirst {
      case Answered(`decision`, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.FavorBank(suit)), _) => suit
    }

  private def amounts(pending: PendingTree, decision: String)
      : Either[OathViolation, Map[Suit, Int]] = pending.answered.collectFirst {
    case Answered(`decision`, DecisionAnswer.DistributeAnswer(rows), _) =>
      rows.collect { case DistributeAmount(DecisionOptionRef.FavorBank(suit), n) =>
        suit -> n }.toMap
  }.toRight(OathViolation.InvalidEventOrder(
    s"no League Treaty distribution answer is recorded for $decision"))
}
