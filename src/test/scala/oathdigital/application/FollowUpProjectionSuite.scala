package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.{FollowUpProjection, GameProjection}
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** What a queued action shows each viewer (follow-up actions design,
  * "Projection and UI"). */
class FollowUpProjectionSuite extends munit.FunSuite:
  private val ancientCity = CatalogNames.site("Ancient City")
  private val knightsCard = CatalogNames.denizen("Knights Errant")
  private val knights = PowerSourceRef.Card(knightsCard)

  private def owed(action: ActionRef): FollowUp = FollowUp(p1, action, knights)

  /** p1 at Ancient City, which two bandits rule, holding Knights Errant
    * faceup; p2 at Broken Peaks and p3 at Buried Giant. `queue` is owed to p1.
    */
  private def table(queue: FollowUp*): Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
    .pawn(p3, at = "Buried Giant").adviser(p1, knightsCard)
    .bandits(ancientCity, 2)
    .update(_.updateCurrent(_.copy(followUps = queue.toVector)))

  private def project(board: Table, viewer: PlayerId): GameProjection =
    new GameProjector(catalog).project("follow-up",
      LoadedGame(Ready(board.ready), 3), viewer)

  test("the head's player gets only the follow-up controls"):
    val shown = project(table(owed(ActionRef.Campaign)), p1)
    assertEquals(shown.legalControls, Vector("beginFollowUp", "skipFollowUp"))
    assertEquals(shown.followUp, Some(FollowUpProjection(p1.value,
      "campaign", "Campaign", "Knights Errant", startable = true)))
    assertEquals(shown.phase, "follow-up-pending")
    assertEquals(shown.minorActions, None)
    assert(!shown.actionSelectionOpen)

  test("another player sees the entry and no controls"):
    val shown = project(table(owed(ActionRef.Campaign)), p2)
    assertEquals(shown.legalControls, Vector.empty)
    assertEquals(shown.followUp.map(_.playerId), Some(p1.value))

  test("the entries behind the head are named"):
    val shown = project(table(owed(ActionRef.Travel), owed(ActionRef.Campaign)),
      p1)
    assertEquals(shown.followUp.map(_.queuedAfter), Some(Vector("Campaign")))

  test("a Campaign with nothing to attack is shown unstartable, with why"):
    val shown = project(table(owed(ActionRef.Campaign)).bandits(ancientCity, 0),
      p1)
    assertEquals(shown.followUp.map(f => (f.startable, f.reason)),
      Some((false, Some("No site to conquer and no enemy pawn to raid"))))
    assertEquals(shown.legalControls, Vector("beginFollowUp", "skipFollowUp"))

  test("a restriction's reason comes before the missing target"):
    val vowed = table(owed(ActionRef.Campaign)).bandits(ancientCity, 0)
      .adviser(p1, "Vow of Peace")
    assertEquals(project(vowed, p1).followUp.flatMap(_.reason),
      Some("Vow of Peace prevents its ruler from campaigning"))

  test("each action names its own reason"):
    val campaign = (refusal: OathViolation) => LegalActionProjector
      .followUpReason(ActionRef.Campaign, Some(refusal))
    assertEquals(campaign(OathViolation.CampaignUnavailable(
      "Vow of Peace prevents its ruler from campaigning")),
      "Vow of Peace prevents its ruler from campaigning")
    assertEquals(campaign(OathViolation.GameNotStarted), "Cannot start now")
    assertEquals(LegalActionProjector.followUpReason(ActionRef.Travel, None),
      "No site to travel to")
    assertEquals(LegalActionProjector.followUpReason(ActionRef.Search, None),
      "Cannot start now")

  test("in Rest, Finish Rest waits for the queue"):
    val resting = table(owed(ActionRef.Campaign)).turn(p1, Phase.Rest)
    assertEquals(project(resting, p1).legalControls,
      Vector("beginFollowUp", "skipFollowUp"))
    assert(project(table().turn(p1, Phase.Rest), p1).legalControls
      .contains("finishRest"))

  test("a finished game shows no entry"):
    val over = table(owed(ActionRef.Campaign))
      .update(_.updateCurrent(_.copy(result = Some(GameResult(p2)))))
    assertEquals(project(over, p1).followUp, None)

  test("every queued action is asked whether it can start"):
    Vector(ActionRef.Search, ActionRef.Muster, ActionRef.Challenge,
      ActionRef.Recover, ActionRef.Trade).foreach { action =>
      val shown = project(table(owed(action)), p1).followUp
      assertEquals(shown.map(_.action), Some(action.key), action)
    }

  test("a site or a banner names the source too"):
    val site = CatalogNames.site("Ancient City")
    val bySite = FollowUp(p1, ActionRef.Campaign, PowerSourceRef.Site(site))
    assertEquals(project(table(bySite), p1).followUp.map(_.source),
      Some("Ancient City"))
    val byBanner = FollowUp(p1, ActionRef.Campaign,
      PowerSourceRef.Banner(Banner.PeoplesFavor))
    assertEquals(project(table(byBanner), p1).followUp.map(_.source),
      Some("People's Favor"))
