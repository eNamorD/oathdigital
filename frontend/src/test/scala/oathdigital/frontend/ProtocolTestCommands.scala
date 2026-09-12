package oathdigital.frontend

import oathdigital.protocol.{GameIntent => Intent, _}

/** Legacy-shaped test builders only; returned values are shared actorless DTOs.
  * Keeping actor parameters here makes existing assertions prove that identity
  * never reaches the encoded payload.
  */
private[frontend] object GameCommand {
  def PlacePawn(actor: String, site: String) = Intent.PlacePawn(site)
  // Take Wealth starts on the generic walker (batch-1 Task 7): the resource
  // rides the start selection as a button, not an intent of its own.
  def TakeWealth(actor: String, resource: String) =
    Intent.StartWalker("take-wealth", Vector.empty,
      Vector(WalkerStartArgWire("button", resource)))
  def EndWake(actor: String) = Intent.EndWake
  def BeginRest(actor: String) = Intent.BeginRest
  def FinishRest(actor: String) = Intent.FinishRest
  def ResolveRestPower(actor: String, id: String,
      allocations: Vector[RestFavorAllocation], bank: String) =
    Intent.ResolveRestPower(id, allocations, bank)
  def DeclineRestPower(actor: String, id: String) = Intent.DeclineRestPower(id)
  // Travel starts on the generic walker (batch-1 Task 5): its destination
  // rides the start selection, not an intent of its own.
  def Travel(actor: String, site: String) = Intent.StartWalker("travel",
    Vector.empty, Vector(WalkerStartArgWire("site", site)))
  def CampaignConquest(actor: String, site: String, count: Int) = Intent.BeginCampaignConquest(Vector(site), count)
  def CampaignConquest(actor: String, sites: Vector[String], count: Int) = Intent.BeginCampaignConquest(sites, count)
  def CampaignRaid(actor: String, targets: Vector[BoardTargetRef], count: Int) = Intent.BeginCampaignRaid(targets.map {
    case BoardTargetRef.PlayerPawn(p) => CampaignRaidTarget.Pawn(p)
    case BoardTargetRef.PlayerRelic(p,r) => CampaignRaidTarget.Relic(p,r)
    case BoardTargetRef.PlayerBanner(p,b) => CampaignRaidTarget.Banner(p,b)
    case other => throw new IllegalArgumentException(other.stableKey)
  }, count)
  def ChooseCampaignPlan(actor: String, id: String, choice: CampaignPlanChoice) = Intent.ChooseCampaignPlan(id, choice.kind match {
    case "adviser" => CampaignPlanSource.Adviser(choice.playerId.get, choice.cardId.get)
    case "site-card" => CampaignPlanSource.SiteCard(choice.siteId.get, choice.cardId.get)
    case "relic" => CampaignPlanSource.Relic(choice.playerId.get, choice.cardId.get)
    case "title" => CampaignPlanSource.Title(choice.playerId.get)
  })
  def FinishCampaignPlans(actor: String, id: String) = Intent.FinishCampaignPlans(id)
  def ChooseCampaignSacrifice(actor: String, id: String, count: Int) = Intent.ChooseCampaignSacrifice(id, count)
  def PlaceCampaignForce(actor: String, id: String, values: Vector[CampaignPlacement]) = Intent.PlaceCampaignForce(id, values.map(v => CampaignForceAllocation(v.siteId, v.count)))
  def RelocateCampaignRaidPawn(actor: String, id: String, site: String) = Intent.RelocateCampaignRaidPawn(id, site)
  def ChooseOathkeeperRecipient(actor: String, id: String, recipient: String) = Intent.ChooseOathkeeperRecipient(id, recipient)
  def RevealVision(actor: String, id: String) = Intent.RevealVision(id)
  def PlayConspiracy(actor: String, target: Option[oathdigital.protocol.ConspiracyTarget]) = Intent.PlayConspiracy(target)
  def Muster(actor: String, target: oathdigital.frontend.EconomyTarget) = Intent.Muster(oathdigital.protocol.EconomyTarget(target.kind, target.id))
  def Trade(actor: String, target: oathdigital.frontend.EconomyTarget, resource: String) = Intent.Trade(oathdigital.protocol.EconomyTarget(target.kind, target.id), resource)
  def BeginSearch(actor: String, source: String, region: Option[String]) = Intent.BeginSearch(SearchSource(source, region))
  def BeginChallenge(actor: String, banner: String) = Intent.BeginChallenge(banner)
  def ChooseChallengeSecretSite(actor: String, id: String, site: String) = Intent.ChooseChallengeSecretSite(id, site)
  def CompleteChallenge(actor: String, id: String, amount: Int) = Intent.CompleteChallenge(id, amount)
  def PeekSiteRelics(actor: String) = Intent.PeekSiteRelics
  def BeginNegotiation(actor: String, participants: Vector[String]) = Intent.BeginNegotiation(participants)
  def MoveWarbands(actor: String, toSite: Boolean, amount: Int) = Intent.MoveWarbands(toSite, amount)
  def ReplaceNegotiationTerms(actor: String, id: String, terms: NegotiationTermsInput) = Intent.ReplaceNegotiationTerms(id, NegotiationTerms(
    terms.transfers.map(v => NegotiationTransfer(v.recipientPlayerId, v.favor, v.relicIds)),
    terms.disclosures.map(v => NegotiationDisclosure(v.recipientPlayerId, v.kind match {
      case "adviser" => NegotiationInformation.Adviser(v.ownerPlayerId.get, WorldCard(v.cardKind.get, v.cardId))
      case "held-relic" => NegotiationInformation.HeldRelic(v.ownerPlayerId.get, v.cardId)
      case "site-relic" => NegotiationInformation.SiteRelic(v.siteId.get, v.cardId)
    }))))
  def ResolveCardDecision(actor: String, id: String, value: DecisionResolution.Value) = Intent.ResolveCardDecision(id, value.intent)
  def StartWalker(actor: String, action: String, modifiers: Vector[String] = Vector.empty) =
    Intent.StartWalker(action, modifiers)
  def RollWalker(actor: String, pool: String) = Intent.RollWalker(pool)
  def ResolveWalker(actor: String, id: String, payload: DecisionAnswerWire) =
    Intent.ResolveWalker(id, payload)
}

private[frontend] object ConspiracyTarget {
  def RelicSlot(owner: String, slot: Int) = oathdigital.protocol.ConspiracyTarget.RelicSlot(owner, slot)
  def Banner(owner: String, banner: String) = oathdigital.protocol.ConspiracyTarget.Banner(owner, banner)
}

private[frontend] object DecisionResolution {
  sealed trait Value { def intent: oathdigital.protocol.DecisionResolution }
  final case class StartingAdviser(id: String) extends Value { val intent = oathdigital.protocol.DecisionResolution.StartingAdviser(id) }
  final case class Search(kept: CardDetails, discarded: Vector[CardDetails], placement: String,
      orientation: Option[String], replacement: Option[CardDetails]) extends Value {
    val intent = oathdigital.protocol.DecisionResolution.Search(
      WorldCard(kept.cardKind, kept.cardId), discarded.map(v => WorldCard(v.cardKind, v.cardId)),
      Placement(orientation.fold(placement)(v => if (v == "face-up") "adviser-face-up" else "adviser-face-down"),
        replacement.map(v => CardRef(v.cardKind, v.cardId))))
  }
}
