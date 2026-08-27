package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse}

private[frontend] final case class ModifierWorkflow(
    command: GameIntent,
    baseParameters: Map[String, String],
    preview: MajorActionPreviewResponse,
    selection: ModifierSelectionState)

private[frontend] object ModifierWorkflow {
  def action(command: GameIntent): Option[(String, Map[String, String])] = command match {
    case GameIntent.Travel(site) => Some("travel" -> Map("destinationSiteId" -> site))
    case GameIntent.BeginSearch(source) => Some("search" ->
      (Map("source" -> source.source) ++ source.region.map("region" -> _)))
    case GameIntent.BeginCampaignConquest(sites, dice) => Some("campaign" -> Map(
      "kind" -> "conquest", "targets" -> sites.mkString(","),
      "attackDiceCount" -> dice.toString))
    case GameIntent.BeginCampaignRaid(targets, dice) => Some("campaign" -> Map(
      "kind" -> "raid", "targets" -> targets.mkString(","),
      "attackDiceCount" -> dice.toString))
    case GameIntent.Muster(target) => Some("muster" -> Map(
      "targetKind" -> target.kind, "targetId" -> target.id))
    case GameIntent.Trade(target, resource) => Some("trade" -> Map(
      "targetKind" -> target.kind, "targetId" -> target.id, "resource" -> resource))
    case GameIntent.BeginForge => Some("forge" -> Map.empty)
    case GameIntent.BeginRecover => Some("recover" -> Map.empty)
    case _ => None
  }
}
