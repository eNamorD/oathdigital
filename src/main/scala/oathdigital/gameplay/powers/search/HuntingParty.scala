package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{PowerAnswers, SelectedModifier}
import oathdigital.model._

object HuntingPartyCard extends Denizen(DenizenId("122"), "Hunting Party", Suit.Order):
  val power = PrintedPower(PowerId("denizen.hunting-party"),
    persistent = false, cost = Cost.free,
    text = "After searching the world deck, you may campaign, spending " +
      "no Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Hunting Party (card 122), a free selected Search modifier: after a Search
  * of the world deck, a Campaign is owed to its player, spending no Supply
  * (follow-up actions design, ruling 10). A discard pile is still
  * choosable while it is selected, with a warning that it queues nothing.
  */
final case class HuntingParty private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = HuntingPartyCard.id
  def id: PowerId = HuntingParty.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ HuntingParty.queued(ctx.actor, cardId))))

  override def warning(decide: Decide, option: DecisionOptionRef)
      : Option[OptionReason] = option match
    case _: DecisionOptionRef.DiscardPile
        if decide.decisionId == SearchProcedure.sourceDecisionId =>
      Some(OptionReason.Written(HuntingParty.discardWarning))
    case _ => None

object HuntingParty:
  val id: PowerId = HuntingPartyCard.power.id
  val discardWarning: String = "Hunting Party needs the world deck"

  /** The Campaign, when the answered source is the world deck. */
  private def queued(actor: PlayerId, card: DenizenId): Operation =
    Branch((_, pending) =>
      if PowerAnswers.one(pending, SearchProcedure.sourceDecisionId)
          .flatMap(SearchSource.fromRef).contains(SearchSource.WorldDeck)
      then Vector(QueueProcedure(QueuedProcedure(actor, ActionRef.Campaign,
        Vector.empty, Some(PowerSourceRef.Card(card)), QueueKind.Offer)))
      else Vector.empty)

  def forCatalog(catalog: ExecutableCatalog): HuntingParty =
    new HuntingParty(catalog)
