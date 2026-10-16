package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object HospitalityCard extends Denizen(DenizenId("171"), "Hospitality",
    Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.hospitality"),
    persistent = false, cost = Cost.free,
    text = "After traveling, gain [favor] from one favor bank that " +
      "matches both a card at your site and one of your advisers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Hospitality (card 171, adviser-only), an automatic rule of a faceup
  * adviser: "After traveling, gain favor from one favor bank that matches
  * both a card at your site and one of your advisers."
  *
  * After the traveller's pawn moves in the cost node, an `AutomaticUse`
  * reads the destination's cards (denizens and edifices) and the
  * traveller's faceup advisers (`EffectiveSuit`, so Marriage counts),
  * unless a power-use restriction refuses it there (catalog batch 8, slice
  * 10). The matching banks are the suits both share. With one that holds
  * favor, 1 favor is gained from it. With two or more, the traveller is
  * asked first, and every matching bank is listed, an empty one unavailable
  * with "This bank is empty". With none holding favor, nothing happens.
  * The rule follows the pawn that moves, so an off-turn Travel counts too.
  * Only Travel walks `TravelCost`.
  *
  * The catalog marks the power `persistent: false`, but it is a must, so it
  * keeps the default automatic resolution, as Tyrant does.
  *
  * It writes no line. The question is under the `travel.` prefix, so it
  * posts no "Chose" line, and the generic gain line names the bank.
  */
final case class Hospitality private (catalog: ExecutableCatalog)
    extends ContributingPower:
  import Hospitality._
  def id: PowerId = Hospitality.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      TravelRoute.pawnMove(ctx.operation).fold(operations)(route =>
        operations :+ AutomaticUse(route.player, id, HospitalityCard.id,
          (live, _) => welcome(live, route.player, route.destination))))))

  override def applicable(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(route =>
      holds(ctx.state, route.player))

  /** The banks matching both a card at `site` and one of `guest`'s faceup
    * advisers, in suit order. */
  private def matching(ready: ReadyGame, guest: PlayerId, site: SiteId)
      : Vector[Suit] =
    val atSite = ready.game.current.map.sites.get(site).toVector
      .flatMap(_.denizens).flatMap(card => catalog.suitOf(card.id)).toSet
    Suit.all.filter(suit => atSite(suit) &&
      EffectiveSuit.count(catalog, ready, guest, suit) > 0)

  private def welcome(ready: ReadyGame, guest: PlayerId, site: SiteId)
      : Vector[Operation] =
    val banks = matching(ready, guest, site)
    banks.filter(stocked(ready, _)) match
      case Vector() => Vector.empty
      case Vector(only) => Vector(gain(guest, _ => Some(only)))
      case _ => Vector(
        Decide(decisionId, guest, DecisionQuery.ChooseOne(
          banks.map(option(ready, _)), heading = Some(heading))),
        gain(guest, chosen))

object Hospitality:
  val id: PowerId = HospitalityCard.power.id
  val Favor: Int = 1
  /** Under the `travel.` prefix, so the Game Log narrates it. */
  val decisionId: String = "travel.hospitality"
  val heading: String = "Hospitality: gain 1 favor from which bank?"
  val emptyBank: String = "This bank is empty"

  def forCatalog(catalog: ExecutableCatalog): Hospitality =
    new Hospitality(catalog)

  private def stocked(ready: ReadyGame, suit: Suit): Boolean =
    ready.banks.favor.getOrElse(suit, 0) > 0

  private def option(ready: ReadyGame, suit: Suit): DecisionOption =
    val shown = DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))
    if stocked(ready, suit) then shown
    else DecisionOption.Unavailable(shown, OptionReason.Written(emptyBank))

  private def chosen(pending: PendingTree): Option[Suit] =
    PowerAnswers.one(pending, decisionId).collect {
      case DecisionOptionRef.FavorBank(suit) => suit }

  /** 1 favor from the bank `bank` names, read when the walk reaches it. */
  private def gain(guest: PlayerId, bank: PendingTree => Option[Suit])
      : Operation = BuildOps((ready, pending) =>
    bank(pending).toRight(PowerAnswers.missing(decisionId)).map(suit =>
      if stocked(ready, suit) then
        Vector[CoreOperation](Gain.Favor(guest, suit, Favor))
      else Vector.empty))

  /** Whether `player` has Hospitality faceup among their advisers. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player)
      .exists(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) =>
          card == HospitalityCard.id
        case _ => false
      })
