package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.{CampaignFixture, ChallengeFixture}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.powerresolver.{PowerCtx, Restriction}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

class FortressRulesSuite extends munit.FunSuite {
  import CampaignFixture._
  import TargetingFixture._

  private val powers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
  private val conquest = DecisionOptionRef.Button("conquest")
  private val raid = DecisionOptionRef.Button("raid")

  private def player(b: Board, id: PlayerId): PlayerState = playerOf(b.ready, id)
  private def third(b: Board): PlayerId = b.ready.game.current.players
    .map(_.player).find(id => id != b.actor && id != b.other).get

  /** The Campaign board with the enemy at the actor's origin, the Fortress on
    * `side` there, and the enemy holding the rule of the origin when `ruled`.
    */
  private def fortified(side: EdificeSide, ruled: Boolean): Board = {
    val b = withEnemyAtOrigin(board(warbands = 4))
    val staged = fortressAt(b.ready, side, b.origin)
    b.copy(ready = if (ruled) ruledBy(staged, b.origin, b.other)
      else unruled(staged, b.origin))
  }

  private def startOf(b: Board) = start(b.ready, ActionRef.Campaign, b.actor)

  test("the Fortress faces are registered persistent rules, so they are automatic") {
    assertEquals(OakenFortress.forCatalog(catalog).get.resolution,
      PowerResolution.Automatic)
    assertEquals(RottingFortress.forCatalog(catalog).get.resolution,
      PowerResolution.Automatic)
    assertEquals(OakenFortress.forCatalog(catalog).get.id,
      PowerId("edifice.e28.intact"))
  }

  // ---- Oaken Fortress ----

  test("the Oaken Fortress removes its ruler from a Raid: only the Conquest is left") {
    val b = fortified(EdificeSide.Intact, ruled = true)
    val started = startOf(b).toOption.get
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest))
  }

  test("without the Fortress the same board offers both") {
    val b = withEnemyAtOrigin(board(warbands = 4))
    val ruled = b.copy(ready = ruledBy(b.ready, b.origin, b.other))
    assertEquals(optionsAt(startOf(ruled).toOption.get, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
  }

  test("the Oaken Fortress protects its ruler only while the ruler is at the " +
      "site: a ruler elsewhere may be raided") {
    val b = withEnemyAtOrigin(board(extras = 1, warbands = 4))
    val elsewhere = b.extras.head
    val staged = ruledBy(fortressAt(b.ready, EdificeSide.Intact, elsewhere),
      elsewhere, b.other)
    assertEquals(optionsAt(start(ruledBy(staged, b.origin, b.other),
      ActionRef.Campaign, b.actor).toOption.get, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
  }

  test("a second enemy at the site keeps the Raid, and the ruler is not " +
      "offered as its defender") {
    val b = fortified(EdificeSide.Intact, ruled = true)
    val crowded = pawnAt(b.ready, third(b), b.origin)
    val started = start(crowded, ActionRef.Campaign, b.actor).toOption.get
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
    val kind = TargetingFixture.rules.resolveWalker(started.state, b.actor, CampaignIds.kind,
      ChooseOneAnswer(raid)).toOption.get
    assertEquals(optionsAt(kind, ActionRef.Campaign),
      Vector[DecisionOptionRef](DecisionOptionRef.Player(third(b))))
  }

  // ---- Rotting Fortress ----

  test("a Raid whose only defenders stand at a Rotting Fortress cannot start") {
    val b = fortified(EdificeSide.Ruined, ruled = false)
    assertEquals(CampaignProcedure.startable(catalog, b.ready, b.actor, powers),
      false)
    assert(startOf(b).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
  }

  test("a faceup beast adviser lifts the Rotting Fortress's protection") {
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val armed = b.copy(ready = adviserOf(b.ready, b.actor, Suit.Beast))
    assert(CampaignProcedure.startable(catalog, armed.ready, armed.actor, powers))
    assert(startOf(armed).isRight)
    val facedown = b.copy(ready = adviserOf(b.ready, b.actor, Suit.Hearth))
    assert(startOf(facedown).isLeft)
  }

  test("the Rotting Fortress protects every player at the site, so a second " +
      "enemy does not open the Raid") {
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val crowded = b.copy(ready = pawnAt(b.ready, third(b), b.origin))
    assert(startOf(crowded).isLeft)
  }

  test("the Rotting Fortress leaves a Conquest of the same site alone") {
    val b = fortified(EdificeSide.Ruined, ruled = true)
    assertEquals(optionsAt(startOf(b).toOption.get, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest))
  }

  test("a Campaign already under way is never refused by the start rule") {
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val power = RottingFortress.forCatalog(catalog).get
    val restriction = power.contributions(PowerWindow.CampaignActionEligibility)
      .head.asInstanceOf[Restriction]
    def ctx(state: ReadyGame) = PowerCtx(state, b.actor, power.source,
      PowerWindow.CampaignActionEligibility, Vector.empty,
      Sequence(Vector.empty))
    assert(restriction.fn(ctx(b.ready), Sequence(Vector.empty)).nonEmpty)
    // The Campaign has answered its force decision: it is under way.
    val underway = b.ready.updateCurrent(_.copy(walkerPending =
      Some(PendingTree(Vector("2"), Vector(Answered(CampaignIds.force,
        DecisionAnswer.ChooseAmountAnswer(0), b.actor))))))
    assertEquals(restriction.fn(ctx(underway), Sequence(Vector.empty)), None)
  }

  test("a Fortress that protects nobody in the Raid does not disturb it") {
    val b = withEnemyAtOrigin(board(extras = 1, warbands = 4))
    // The Rotting Fortress stands where no Raid is being made.
    val ready = fortressAt(b.ready, EdificeSide.Ruined, b.extras.head)
    val started = start(ready, ActionRef.Campaign, b.actor).toOption.get
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
  }

  // ---- Challenge ----

  private def challengeBanners(side: EdificeSide, ruled: Boolean)
      : Vector[DecisionOptionRef] = {
    val (base, _) = ChallengeFixture.ready(resources = 2)
    val actor = ChallengeFixture.active(base)
    val held = ChallengeFixture.enemyHolds(base, Banner.PeoplesFavor, 2)
    val enemy = ChallengeFixture.enemy(held).player
    val site = playerOf(held, actor).pawnSite.get
    val staged = fortressAt(held, side, site)
    val ready = if (ruled) ruledBy(staged, site, enemy) else staged
    optionsAt(start(ready, ActionRef.Challenge, actor).toOption.get,
      ActionRef.Challenge)
  }

  test("a Challenge may not name a banner a protected player holds") {
    val banner = (b: Banner) => DecisionOptionRef.Banner(b): DecisionOptionRef
    // Oaken: the holder rules the site. Rotting: the holder is at the site.
    assertEquals(challengeBanners(EdificeSide.Intact, ruled = true),
      Vector(banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(EdificeSide.Ruined, ruled = false),
      Vector(banner(Banner.DarkestSecret)))
    // An Oaken Fortress the holder does not rule protects nobody.
    assertEquals(challengeBanners(EdificeSide.Intact, ruled = false).toSet,
      Set(banner(Banner.PeoplesFavor), banner(Banner.DarkestSecret)))
  }
}
