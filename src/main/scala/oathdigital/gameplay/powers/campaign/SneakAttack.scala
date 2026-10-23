package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.gameplay.PowerAccess
import oathdigital.model._

object SneakAttackCard extends Denizen(DenizenId("98"), "Sneak Attack", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.sneak-attack"),
    persistent = true, cost = Cost.free,
    text = "After another player's campaign, you may campaign, spending " +
      "no Supply, if you declare them as the defender.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Sneak Attack (card 98), a persistent rule of a faceup adviser: "After
  * another player's campaign, you may campaign, spending no Supply, if you
  * declare them as the defender."
  *
  * A `Transform` at the Campaign's root, `CampaignActionEligibility`, which
  * appends a `Branch` after the outcome, as Herald does. So it reads this
  * Campaign's recorded result, and a Campaign that ended before its result
  * (skipped, or left with nothing to attack) never reaches it. It fires
  * when the holder is not the attacker and could campaign against them
  * from the holder's pawn site (`CampaignSetup.kindsAgainst`).
  *
  * It queues an `Offer` at the front of the queue, so the holder is asked
  * before the Campaign's other follow-ups, which go to the back (catalog
  * batch 8, slice 14, ruling 6). The entry names the attacker as its one
  * start argument, the declared defender (`CampaignSetup.queuedDefender`),
  * and the card as its source, so the Campaign spends no Supply. The holder
  * walks it off turn as the attacker.
  */
final case class SneakAttack private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = SneakAttackCard.id
  def id: PowerId = SneakAttack.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ offer(ctx.actor))))

  private def offer(actor: PlayerId): Operation = Branch((ready, _) => (for
    result <- ready.game.current.lastCampaignResult
    if result.attacker == actor
    holder <- holderOf(ready)
    if holder != actor
    if CampaignSetup.kindsAgainst(ready, holder, actor).nonEmpty
  yield Vector(QueueProcedure(QueuedProcedure(holder, ActionRef.Campaign,
      Vector(DecisionOptionRef.Player(actor)),
      Some(PowerSourceRef.Card(cardId)), QueueKind.Offer), atFront = true))
  ).getOrElse(Vector.empty))

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    PowerAccess.faceupAdviserHolder(ready, cardId)

object SneakAttack:
  val id: PowerId = SneakAttackCard.power.id

  def forCatalog(catalog: ExecutableCatalog): SneakAttack =
    new SneakAttack(catalog)
