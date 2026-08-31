package oathdigital.gameplay.powers

import oathdigital.gameplay._
import oathdigital.gameplay.powerresolver._
import oathdigital.model.{PlayerId, PowerId}

final case class ReviewedPowerFacts(
    catalog: oathdigital.catalog.ExecutableCatalog,
    ready: ReadyGame,
    actor: PlayerId,
    sources: Map[RuleSourceRef, IndexedRuleSource]
) extends PowerFacts

private[gameplay] object ReviewedPowerInspector {
  def inspect(context: PowerContext): PowerInspection =
    context.facts match {
      case facts: ReviewedPowerFacts => PowerInspection(
        applicable = facts.sources.get(context.source).exists(source =>
          accessible(context.window, context.source, source, facts)),
        eligiblePlayer = Some(facts.actor),
        decisionPlayer = Some(facts.actor))
      case _ => PowerInspection(applicable = false)
    }

  private def accessible(window: PowerWindow, ref: RuleSourceRef,
      source: IndexedRuleSource, facts: ReviewedPowerFacts): Boolean = {
    val player = facts.ready.game.current.players.find(_.player == facts.actor)
    val pawn = player.flatMap(_.pawnSite)
    ref match {
      case RuleSourceRef.Site(id) => pawn.contains(id)
      case RuleSourceRef.SiteCard(id, _) =>
        pawn.contains(id) && source.face == RuleSourceFace.FaceUp
      case RuleSourceRef.SiteRelic(id, _) =>
        pawn.contains(id) && source.face == RuleSourceFace.FaceUp
      case RuleSourceRef.Edifice(id, _) =>
        pawn.contains(id) && source.face == RuleSourceFace.Intact
      case RuleSourceRef.Adviser(owner, _) => owner == facts.actor &&
        (source.face == RuleSourceFace.FaceUp ||
          (window == PowerWindow.ActionCardPlayed &&
            source.face == RuleSourceFace.FaceDown))
      case RuleSourceRef.Relic(owner, _) =>
        owner == facts.actor && source.face == RuleSourceFace.FaceUp
      case RuleSourceRef.Banner(_) | RuleSourceRef.Foundation(_) => true
      case RuleSourceRef.Legacy(lineage, _) =>
        player.exists(_.lineage == lineage) && source.face == RuleSourceFace.Active
      case _ => false
    }
  }
}

private[powers] abstract class ReviewedPower(
    idValue: String,
    val modifier: Option[MajorActionType],
    val handlers: Vector[PowerHandler]
) extends Power {
  final val id: PowerId = PowerId(idValue)
}

private[powers] object ReviewedHandler {
  private val reviewed = PowerInspector(ReviewedPowerInspector.inspect)
  private val inactive = PowerInspector(_ => PowerInspection(applicable = false))

  def automatic(window: PowerWindow, implemented: Boolean = false,
      active: Boolean = true): PowerHandler = PowerHandlers.automatic(window,
    implemented)(if (active) reviewed else inactive)
  def selected(window: PowerWindow, implemented: Boolean = false): PowerHandler =
    PowerHandlers.selected(window, implemented)(reviewed)
}
