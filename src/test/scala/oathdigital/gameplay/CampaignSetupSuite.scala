package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture.{board, on, withEnemyAtOrigin}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.testkit.CatalogNames

class CampaignSetupSuite extends munit.FunSuite:
  private def pending(answers: (String, DecisionAnswer)*) = PendingTree(
    Vector("0"), answers.toVector.map { case (id, a) =>
      Answered(id, a, PlayerId("actor")) })

  test("legal kinds follow the pawn site's ruler and the enemy pawns"):
    // Two bandits rule p1's site and p2 and p3 stand elsewhere, so only a
    // Conquest is legal; once p2 joins p1, a Raid is too.
    val b = board(warbands = 5, supply = 7)
    assertEquals(CampaignSetup.legalKinds(b.ready, b.actor),
      Vector(CampaignKind.Conquest))
    assertEquals(CampaignSetup.legalKinds(withEnemyAtOrigin(b).ready, b.actor),
      Vector(CampaignKind.Conquest, CampaignKind.Raid))

  test("a player never conquers their own site"):
    val b = board(warbands = 5, supply = 7)
    val own = on(b)(_.warbandsAt(b.origin, b.actor, 1))
    assertEquals(CampaignSetup.conquestDefender(own.ready, b.actor), None)

  test("the setup is the mandatory site plus the answered extras, in map " +
      "order, with the force"):
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

  test("a Raid setup is the pawn first, then the chosen relic and banner in " +
      "canonical order"):
    val b = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val relic = CatalogNames.relic("Cursed Cauldron")
    // The rival holds Cursed Cauldron faceup and the People's Favor.
    val armed = on(b)(_.relic(b.rival, relic)
      .peoplesFavor(Some(b.rival), favor = 1)).ready
    val setup = CampaignSetup.setup(armed, b.actor, pending(
      CampaignIds.kind -> ChooseOneAnswer(DecisionOptionRef.Button("raid")),
      CampaignIds.targets -> ChooseManyAnswer(Vector(
        DecisionOptionRef.Banner(Banner.PeoplesFavor),
        DecisionOptionRef.Relic(relic))),
      CampaignIds.force -> ChooseAmountAnswer(2))).get
    assertEquals(setup.raidTargets, Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(b.rival),
      CampaignRaidTarget.Relic(b.rival, relic),
      CampaignRaidTarget.Banner(b.rival, Banner.PeoplesFavor)))
    assertEquals(setup.defender, CampaignDefender.Player(b.rival))

  test("Raid target options are the defender's faceup relics and held " +
      "banners only"):
    val b = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    // The rival holds Cursed Cauldron faceup, Dowsing Sticks facedown, and the
    // Darkest Secret.
    val up = CatalogNames.relic("Cursed Cauldron")
    val armed = on(b)(_.relic(b.rival, up)
      .relic(b.rival, "Dowsing Sticks", facedown = true)
      .darkestSecret(Some(b.rival), secrets = 1)).ready
    assertEquals(CampaignSetup.targetOptions(armed, b.actor, CampaignKind.Raid,
      CampaignDefender.Player(b.rival)), Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(up)),
      DecisionOption.Banner(DecisionOptionRef.Banner(Banner.DarkestSecret))))

  test("there is no setup before the force is answered"):
    val b = board(warbands = 5, supply = 7)
    assertEquals(CampaignSetup.setup(b.ready, b.actor, pending()), None)

  test("the declared pair is the only legal kind and its defender"):
    // Two bandits rule the actor's site and no rival pawn stands there, so
    // only a Conquest is legal and its defender settles with no answer.
    val b = board(warbands = 5, supply = 7)
    assertEquals(CampaignSetup.declaredAt(b.ready, b.actor, pending()),
      Some((CampaignKind.Conquest, CampaignDefender.Bandits)))

  test("the declared pair follows the answered kind to the raided player"):
    // The rival joins the actor at the origin, so a Raid is legal too; the
    // answer picks it and the defender is the pawn standing there.
    val b = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    assertEquals(CampaignSetup.declaredAt(b.ready, b.actor, pending(
      CampaignIds.kind -> ChooseOneAnswer(DecisionOptionRef.Button("raid")))),
      Some((CampaignKind.Raid, CampaignDefender.Player(b.rival))))

  test("no pair is declared once the attacker skipped"):
    val b = board(warbands = 5, supply = 7)
    val skipped = pending(CampaignIds.kind ->
      ChooseOneAnswer(CampaignIds.skip))
    assertEquals(CampaignSetup.declaredAt(b.ready, b.actor, skipped), None)
