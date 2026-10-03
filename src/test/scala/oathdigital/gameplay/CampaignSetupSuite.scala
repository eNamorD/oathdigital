package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{board, withEnemyAtOrigin}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._

class CampaignSetupSuite extends munit.FunSuite:
  private def pending(answers: (String, DecisionAnswer)*) = PendingTree(
    Vector("0"), answers.toVector.map { case (id, a) =>
      Answered(id, a, PlayerId("actor")) })

  test("legal kinds follow the pawn site's ruler and the enemy pawns"):
    val b = board(warbands = 5, supply = 7)
    assertEquals(CampaignSetup.legalKinds(b.ready, b.actor),
      Vector(CampaignKind.Conquest))
    assertEquals(CampaignSetup.legalKinds(withEnemyAtOrigin(b).ready, b.actor),
      Vector(CampaignKind.Conquest, CampaignKind.Raid))

  test("a player never conquers their own site"):
    val b = board(warbands = 5, supply = 7)
    val lineage = b.player(b.actor).lineage
    val own = b.ready.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(b.origin,
        current.map.sites(b.origin).copy(forces =
          SiteForces.Occupied(ForceKind.Exile(lineage), 1))))))
    assertEquals(CampaignSetup.conquestDefender(own, b.actor), None)

  test("the setup is the mandatory site plus the answered extras, in map order, with the force"):
    val b = board(extras = 2, warbands = 5, supply = 7)
    val last = DecisionOptionRef.Site(b.extras.last)
    val first = DecisionOptionRef.Site(b.extras.head)
    val setup = CampaignSetup.setup(b.ready, b.actor, pending(
      CampaignIds.targets -> ChooseManyAnswer(Vector(last, first)),
      CampaignIds.force -> ChooseAmountAnswer(4))).get
    assertEquals(setup.kind, CampaignKind.Conquest)
    assertEquals(setup.defender, CampaignDefender.Bandits)
    assertEquals(setup.targetSites, b.origin +: b.extras)
    assertEquals(setup.force, 4)

  test("a Raid setup is the pawn first, then the chosen relic and banner in canonical order"):
    val b = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val relic = RelicId("r-raid")
    val armed = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if p.player == b.rival then p.copy(
        relics = Vector(RelicState(relic, Orientation.FaceUp, Tokens.empty)))
      else p),
      banners = current.banners.copy(peoplesFavor =
        current.banners.peoplesFavor.copy(holder = Some(b.rival)))))
    val setup = CampaignSetup.setup(armed, b.actor, pending(
      CampaignIds.kind -> ChooseOneAnswer(DecisionOptionRef.Button("raid")),
      CampaignIds.targets -> ChooseManyAnswer(Vector(
        DecisionOptionRef.Banner(Banner.PeoplesFavor),
        DecisionOptionRef.Relic(relic))),
      CampaignIds.force -> ChooseAmountAnswer(2))).get
    assertEquals(setup.raidTargets, Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(b.rival), CampaignRaidTarget.Relic(b.rival, relic),
      CampaignRaidTarget.Banner(b.rival, Banner.PeoplesFavor)))
    assertEquals(setup.defender, CampaignDefender.Player(b.rival))

  test("Raid target options are the defender's faceup relics and held banners only"):
    val b = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val up = RelicState(RelicId("r-up"), Orientation.FaceUp, Tokens.empty)
    val down = RelicState(RelicId("r-down"), Orientation.FaceDown, Tokens.empty)
    val armed = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p => if p.player == b.rival then
        p.copy(relics = Vector(up, down)) else p),
      banners = current.banners.copy(darkestSecret =
        current.banners.darkestSecret.copy(holder = Some(b.rival)))))
    assertEquals(CampaignSetup.targetOptions(armed, b.actor, CampaignKind.Raid,
      CampaignDefender.Player(b.rival)), Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(up.id)),
      DecisionOption.Banner(DecisionOptionRef.Banner(Banner.DarkestSecret))))

  test("there is no setup before the force is answered"):
    val b = board(warbands = 5, supply = 7)
    assertEquals(CampaignSetup.setup(b.ready, b.actor, pending()), None)
