package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.powerresolver.{PowerCtx, Restriction}
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, as}

class FortressRulesSuite extends munit.FunSuite:
  import CampaignFixture.{Board, board, on, withEnemyAtOrigin}
  import TargetingFixture.{challengeTable, fortress, optionsAt, start}

  private val powers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
  private val conquest = DecisionOptionRef.Button("conquest")
  private val raid = DecisionOptionRef.Button("raid")

  /** The Campaign board with the enemy at the actor's origin, the Fortress on
    * `side` there, and the enemy holding the rule of the origin when `ruled`.
    */
  private def fortified(side: EdificeSide, ruled: Boolean): Board =
    val b = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    on(b)(table =>
      val staged = table.edifice(fortress, side, at = b.origin)
      // The enemy rules with 2 warbands; unruled, the origin's bandits go.
      if ruled then staged.warbandsAt(b.origin, b.rival, 2)
      else staged.bandits(b.origin, 0))

  private def startOf(b: Board) = start(b.ready, ActionRef.Campaign, b.actor)

  // ---- Oaken Fortress ----

  test("the Oaken Fortress removes its ruler from a Raid: only the Conquest " +
      "is left"):
    val b = fortified(EdificeSide.Intact, ruled = true)
    val started = accepted(startOf(b))
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest))

  test("without the Fortress the same board offers both"):
    val b = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val ruled = on(b)(_.warbandsAt(b.origin, b.rival, 2))
    assertEquals(optionsAt(accepted(startOf(ruled)), ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))

  test("the Oaken Fortress protects its ruler only while the ruler is at the " +
      "site: a ruler elsewhere may be raided"):
    val b = withEnemyAtOrigin(board(extras = 1, warbands = 4, supply = 7))
    val elsewhere = b.extras.head
    val staged = on(b)(_.edifice(fortress, EdificeSide.Intact, at = elsewhere)
      .warbandsAt(elsewhere, b.rival, 2).warbandsAt(b.origin, b.rival, 2))
    assertEquals(optionsAt(accepted(startOf(staged)), ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))

  test("a second enemy at the site keeps the Raid, and the ruler is not " +
      "offered as its defender"):
    val b = fortified(EdificeSide.Intact, ruled = true)
    val crowded = on(b)(_.pawn(b.bystander, at = b.origin))
    val started = accepted(startOf(crowded))
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
    val kind = accepted(TargetingFixture.rules.resolveWalker(started.state,
      b.actor, CampaignIds.kind, ChooseOneAnswer(raid)))
    assertEquals(optionsAt(kind, ActionRef.Campaign),
      Vector[DecisionOptionRef](DecisionOptionRef.Player(b.bystander)))

  // ---- Rotting Fortress ----

  test("a Raid whose only defenders stand at a Rotting Fortress cannot start"):
    val b = fortified(EdificeSide.Ruined, ruled = false)
    assertEquals(CampaignProcedure.startable(catalog, b.ready, b.actor, powers),
      false)
    assert(startOf(b).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))

  test("a faceup beast adviser lifts the Rotting Fortress's protection; a " +
      "facedown one, or one of another suit, does not"):
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val armed = on(b)(_.adviser(b.actor, "Errand Boy"))  // Beast
    val startable =
      CampaignProcedure.startable(catalog, armed.ready, armed.actor, powers)
    assert(startable)
    assert(startOf(armed).isRight)
    val protectedRaid = Some(OathViolation.CampaignUnavailable(
      "a Fortress protects every player a Raid could target"))
    val hearth = on(b)(_.adviser(b.actor, "Homesteaders"))  // Hearth
    assertEquals(startOf(hearth).left.toOption, protectedRaid)
    val hidden = on(b)(_.adviser(b.actor, "Errand Boy", facedown = true))
    assertEquals(startOf(hidden).left.toOption, protectedRaid)

  test("the Rotting Fortress protects every player at the site, so a second " +
      "enemy does not open the Raid"):
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val crowded = on(b)(_.pawn(b.bystander, at = b.origin))
    assert(startOf(crowded).isLeft)

  test("the Rotting Fortress leaves a Conquest of the same site alone"):
    val b = fortified(EdificeSide.Ruined, ruled = true)
    assertEquals(optionsAt(accepted(startOf(b)), ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest))

  test("a Campaign already under way is never refused by the start rule"):
    val b = fortified(EdificeSide.Ruined, ruled = false)
    val power = RottingFortress.forCatalog(catalog)
    val restriction = as[Restriction](
      power.contributions(PowerWindow.CampaignActionEligibility).head)
    def ctx(state: ReadyGame) = PowerCtx(state, b.actor, power.source,
      PowerWindow.CampaignActionEligibility, Vector.empty,
      Sequence(Vector.empty))
    assert(restriction.fn(ctx(b.ready), Sequence(Vector.empty)).nonEmpty)
    // The Campaign has answered its force decision: it is under way.
    val underway = b.ready.updateCurrent(_.copy(walkerPending =
      Some(PendingTree(Vector("2"), Vector(Answered(CampaignIds.force,
        DecisionAnswer.ChooseAmountAnswer(0), b.actor))))))
    assertEquals(restriction.fn(ctx(underway), Sequence(Vector.empty)), None)

  test("a Fortress that protects nobody in the Raid does not disturb it"):
    val b = withEnemyAtOrigin(board(extras = 1, warbands = 4, supply = 7))
    // The Rotting Fortress stands where no Raid is being made.
    val ready = on(b)(_.edifice(fortress, EdificeSide.Ruined,
      at = b.extras.head))
    val started = accepted(startOf(ready))
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))

  // ---- Challenge ----

  private def challengeBanners(side: EdificeSide, ruled: Boolean)
      : Vector[DecisionOptionRef] =
    // p1 challenges at Ancient City, where p2 stands holding People's Favor.
    val site = Table.quietPawnSite(p1)
    val held = challengeTable.ready
    val staged = Table.from(held).edifice(fortress, side, at = site)
    val ready = (if ruled then staged.warbandsAt(site, p2, 2) else staged)
      .unchecked
    optionsAt(accepted(start(ready, ActionRef.Challenge, p1)),
      ActionRef.Challenge)

  test("a Challenge may not name a banner a protected player holds"):
    val banner = (b: Banner) => DecisionOptionRef.Banner(b): DecisionOptionRef
    // Oaken: the holder rules the site. Rotting: the holder is at the site.
    assertEquals(challengeBanners(EdificeSide.Intact, ruled = true),
      Vector(banner(Banner.DarkestSecret)))
    assertEquals(challengeBanners(EdificeSide.Ruined, ruled = false),
      Vector(banner(Banner.DarkestSecret)))
    // An Oaken Fortress the holder does not rule protects nobody.
    assertEquals(challengeBanners(EdificeSide.Intact, ruled = false).toSet,
      Set(banner(Banner.PeoplesFavor), banner(Banner.DarkestSecret)))

  // ---- Lines ----

  private def hidden(power: FortressRule, from: OathTransition)
      : Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, from.events)
  private val oaken = OakenFortress.forCatalog(catalog)
  private val rotting = RottingFortress.forCatalog(catalog)

  test("a Raid the Oaken Fortress hides writes no line"):
    val b = fortified(EdificeSide.Intact, ruled = true)
    assertEquals(hidden(oaken, accepted(startOf(b))), Vector.empty)

  test("a defender the Oaken Fortress hides writes no line when the Raid stays"):
    val b = fortified(EdificeSide.Intact, ruled = true)
    val crowded = on(b)(_.pawn(b.bystander, at = b.origin))
    val started = accepted(startOf(crowded))
    assertEquals(hidden(oaken, started), Vector.empty)
    val kind = accepted(TargetingFixture.rules.resolveWalker(started.state,
      b.actor, CampaignIds.kind, ChooseOneAnswer(raid)))
    assertEquals(hidden(oaken, kind), Vector.empty)

  test("a Raid the Rotting Fortress hides from several players writes no line"):
    val b = fortified(EdificeSide.Ruined, ruled = true)
    val crowded = on(b)(_.pawn(b.bystander, at = b.origin))
    assertEquals(hidden(rotting, accepted(startOf(crowded))), Vector.empty)

  test("a banner a protected player holds writes no line"):
    // p1 challenges at Ancient City, where p2 stands holding People's Favor.
    val held = challengeTable.ready
    val staged = Table.from(held).edifice(fortress, EdificeSide.Ruined,
      at = Table.quietPawnSite(p1)).unchecked
    assertEquals(
      hidden(rotting, accepted(start(staged, ActionRef.Challenge, p1))),
      Vector.empty)

  test("a Fortress that protects nobody writes nothing"):
    val b = withEnemyAtOrigin(board(extras = 1, warbands = 4, supply = 7))
    val ready = on(b)(_.edifice(fortress, EdificeSide.Ruined,
      at = b.extras.head))
    assertEquals(hidden(rotting, accepted(startOf(ready))), Vector.empty)
