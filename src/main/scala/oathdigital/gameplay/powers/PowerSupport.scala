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

private[gameplay] object ReviewedPowerInspector extends PowerInspector {
  override def inspect(context: PowerContext): PowerInspection =
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

private[powers] final case class ReviewedWindowInspector(
    activeWindows: Set[PowerWindow]
) extends PowerInspector {
  override def inspect(context: PowerContext): PowerInspection =
    if (activeWindows(context.window)) ReviewedPowerInspector.inspect(context)
    else PowerInspection(applicable = false)
}

private[powers] object ReviewedImplementation extends PowerHandler

private[powers] object PowerRegistration {
  def selected(id: String, modifier: MajorActionType, window: PowerWindow,
      implemented: Boolean = false): RegisteredPower = register(id,
    Some(modifier), Vector(window), PowerResolution.PlayerSelected, implemented)

  def selected(id: String, modifier: Option[MajorActionType],
      windows: Vector[PowerWindow], implemented: Boolean): RegisteredPower =
    register(id, modifier, windows, PowerResolution.PlayerSelected, implemented)

  def automatic(id: String, modifier: Option[MajorActionType],
      windows: Vector[PowerWindow], implemented: Boolean = false)
      : RegisteredPower = register(id, modifier, windows,
        PowerResolution.Automatic, implemented)

  def automaticAt(id: String, modifier: Option[MajorActionType],
      windows: Vector[PowerWindow], activeWindows: Set[PowerWindow])
      : RegisteredPower = RegisteredPower(
    PowerDefinition(PowerId(id), modifier, windows, PowerResolution.Automatic),
    ReviewedWindowInspector(activeWindows), None)

  private def register(id: String, modifier: Option[MajorActionType],
      windows: Vector[PowerWindow], resolution: PowerResolution,
      implemented: Boolean) = RegisteredPower(
    PowerDefinition(PowerId(id), modifier, windows, resolution),
    ReviewedPowerInspector,
    Option.when(implemented)(ReviewedImplementation))
}
