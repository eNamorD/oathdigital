package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object SecondWindCard extends Denizen(DenizenId("16"), "Second Wind", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.second-wind"),
    persistent = false, cost = Cost(secret = 1, favorBurnt = 1),
    text = "After you're victorious, you may travel and then may " +
      "campaign, spending no Supply for either.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Second Wind (card 16), a selected Campaign modifier paid at the start (1
  * secret, 1 favor burnt): once its holder's Campaign is won, a Travel and
  * then a Campaign are owed, each spending no Supply (follow-up actions
  * design, rulings 9 and 11). The two entries are independent: skipping the
  * Travel still offers the Campaign. It may be selected again on the queued
  * Campaign, so a chain ends only when its cost cannot be paid.
  *
  * It reads the outcome at the end of the Campaign's root, after the
  * Conquest or Raid, as Herald does. A selected modifier is offered only on
  * its holder's own Campaign, so "victorious" is a win as attacker.
  */
final case class SecondWind private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = SecondWindCard.id
  def id: PowerId = SecondWind.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Campaign)
  override def cost: Cost = SecondWindCard.power.cost

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility -> Vector(Transform(
      (ctx, children) => children :+ SecondWind.queued(ctx.activePlayer,
        cardId))))

object SecondWind:
  val id: PowerId = SecondWindCard.power.id

  /** The Travel and the Campaign, when `actor` won as attacker. */
  private def queued(actor: PlayerId, card: DenizenId): Operation =
    Branch((ready, _) =>
      if ready.game.current.lastCampaignResult.exists(result =>
          result.attacker == actor && result.attackerWins)
      then Vector(ActionRef.Travel, ActionRef.Campaign).map(action =>
        QueueFollowUp(FollowUp(actor, action, PowerSourceRef.Card(card))))
      else Vector.empty)

  def forCatalog(catalog: ExecutableCatalog): SecondWind =
    new SecondWind(catalog)
