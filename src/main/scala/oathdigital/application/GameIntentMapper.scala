package oathdigital.application

import oathdigital.model.PlayerId

/** The single actor-binding boundary shared by development and authenticated HTTP. */
object GameIntentMapper {
  def bind(actorId: PlayerId, intent: GameIntent): GameCommand = {
    val actor = AuthorizedPlayer.forPlayer(actorId)
    intent match {
      case GameIntent.PlacePawn(siteId) => actor.placePawn(siteId)
      case GameIntent.ChooseAdviser(adviserId) => actor.chooseAdviser(adviserId)
      case GameIntent.TakeWealth(resource) => actor.takeWealth(resource)
      case GameIntent.EndWake => actor.endWake
      case GameIntent.BeginRest => actor.beginRest
      case GameIntent.FinishRest => actor.finishRest
      case GameIntent.Travel(destination) => actor.travel(destination)
      case GameIntent.Muster(target) => actor.muster(target)
      case GameIntent.Trade(target, resource) => actor.trade(target, resource)
      case GameIntent.BeginSearch(source) => actor.beginSearch(source)
      case GameIntent.BeginRecover => actor.beginRecover
      case GameIntent.BeginForge => actor.beginForge
      case GameIntent.CompleteForge(decision, assignments) =>
        actor.completeForge(decision, assignments)
      case GameIntent.BeginChallenge(banner) => actor.beginChallenge(banner)
      case GameIntent.ChooseChallengeSecretSite(decision, site) =>
        actor.chooseChallengeSecretSite(decision, site)
      case GameIntent.CompleteChallenge(decision, amount) =>
        actor.completeChallenge(decision, amount)
      case GameIntent.PlaceBannerResource(banner, amount) =>
        actor.placeBannerResource(banner, amount)
      case GameIntent.DiscardFacedownAdviser(adviser) =>
        actor.discardFacedownAdviser(adviser)
      case GameIntent.PlayFacedownAdviser(adviser, placement) =>
        actor.playFacedownAdviser(adviser, placement)
      case GameIntent.RevealVision(vision) => actor.revealVision(vision)
      case GameIntent.PlayConspiracy(target) => actor.playConspiracy(target)
      case GameIntent.ChooseConspiracySecretSite(decision, site) =>
        actor.chooseConspiracySecretSite(decision, site)
      case GameIntent.PeekSiteRelics => actor.peekSiteRelics
      case GameIntent.RevealOwnedRelic(relic) => actor.revealOwnedRelic(relic)
      case GameIntent.MoveWarbands(toSite, amount) => actor.moveWarbands(toSite, amount)
      case GameIntent.BeginNegotiation(participants) => actor.beginNegotiation(participants)
      case GameIntent.ReplaceNegotiationTerms(decision, terms) =>
        actor.replaceNegotiationTerms(decision, terms)
      case GameIntent.AcceptNegotiation(decision) => actor.acceptNegotiation(decision)
      case GameIntent.DeclineNegotiation(decision) => actor.declineNegotiation(decision)
      case GameIntent.AddRecoverDice(decision) => actor.addRecoverDice(decision)
      case GameIntent.StopRecover(decision) => actor.stopRecover(decision)
      case GameIntent.BeginCampaignConquest(targets, dice) =>
        actor.beginCampaignConquest(targets, dice)
      case GameIntent.BeginCampaignRaid(targets, dice) => actor.beginCampaignRaid(targets, dice)
      case GameIntent.ChooseCampaignPlan(decision, source) =>
        actor.chooseCampaignPlan(decision, source)
      case GameIntent.FinishCampaignPlans(decision) => actor.finishCampaignPlans(decision)
      case GameIntent.ChooseCampaignSacrifice(decision, count) =>
        actor.chooseCampaignSacrifice(decision, count)
      case GameIntent.PlaceCampaignForce(decision, allocations) =>
        actor.placeCampaignForce(decision, allocations)
      case GameIntent.RelocateCampaignRaidPawn(decision, destination) =>
        actor.relocateCampaignRaidPawn(decision, destination)
      case GameIntent.ChooseOathkeeperRecipient(decision, recipient) =>
        actor.chooseOathkeeperRecipient(decision, recipient)
      case GameIntent.CompleteSearch(decision, kept, discarded, placement) =>
        actor.completeSearch(decision, kept, discarded, placement)
      case GameIntent.ResolveCardDecision(decision, resolution) =>
        actor.resolveCardDecision(decision, resolution)
    }
  }
}
