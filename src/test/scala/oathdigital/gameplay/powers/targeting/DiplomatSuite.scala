package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{PowerCtx, Restriction}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Unwrap.{accepted, as}
import oathdigital.testkit.StartPreview

/** Diplomat: a player who rules no hearth card cannot Raid or Conquer its
  * holder. The kinds and defenders it blocks are hidden, and a Campaign left
  * with nothing to attack is refused when it starts. */
class DiplomatSuite extends munit.FunSuite:
  import CampaignFixture.{Board, board, on, withEnemyAtOrigin}
  import TargetingFixture.{optionsAt, parkedOn, start}

  private val card = CatalogNames.denizen("Diplomat")
  /** A hearth card with no Campaign power. */
  private val hearth = CatalogNames.denizen("Homesteaders")
  private val powers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
  private val conquest = DecisionOptionRef.Button("conquest")
  private val raid = DecisionOptionRef.Button("raid")
  /** What a Campaign with nothing left to attack is refused with. */
  private val protectsEveryDefender = OathViolation.CampaignUnavailable(
    "Diplomat protects every defender this Campaign could have")

  private def startCampaign(b: Board) = start(b.ready, ActionRef.Campaign, b.actor)

  /** The rival rules the origin with two warbands and holds Diplomat faceup;
    * the rival's pawn is elsewhere, so a Conquest is the only kind. */
  private def rivalRulesOriginWithDiplomat: Board =
    val b = board(warbands = 4, supply = 7)
    on(b)(_.warbandsAt(b.origin, b.rival, 2).adviser(b.rival, card))

  /** The rival stands at the origin, which bandits rule, holding Diplomat
    * facedown when `facedown` is set. */
  private def rivalAtOriginWithDiplomat(facedown: Boolean): Board =
    val b = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    on(b)(_.adviser(b.rival, card, facedown = facedown))

  test("an attacker who rules no hearth card cannot Conquer the holder, so " +
      "the Campaign is refused"):
    val b = rivalRulesOriginWithDiplomat
    assertEquals(StartPreview.opens(catalog, b.ready, b.actor,
        ActionRef.Campaign, powers),
      false)
    assertEquals(startCampaign(b).left.toOption, Some(protectsEveryDefender))

  test("a faceup hearth adviser lets the attacker Conquer the holder"):
    val base = rivalRulesOriginWithDiplomat
    val b = on(base)(_.adviser(base.actor, hearth))
    assertEquals(parkedOn(accepted(startCampaign(b))), Some(CampaignIds.force))

  test("a hearth card at a site the attacker rules counts too"):
    val base = board(extras = 1, warbands = 4, supply = 7)
    val site = base.extras.head
    val b = on(base)(_.warbandsAt(base.origin, base.rival, 2)
      .adviser(base.rival, card).warbandsAt(site, base.actor, 2)
      .denizen(hearth, at = site))
    assertEquals(parkedOn(accepted(startCampaign(b))), Some(CampaignIds.force))

  test("a Raid on the holder is hidden while the origin's bandits can still " +
      "be conquered"):
    val b = rivalAtOriginWithDiplomat(facedown = false)
    assertEquals(optionsAt(accepted(startCampaign(b)), ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest))

  test("a second enemy keeps the Raid, and the holder is not offered as its " +
      "defender"):
    val base = rivalAtOriginWithDiplomat(facedown = false)
    val b = on(base)(_.pawn(base.bystander, at = base.origin))
    val started = accepted(startCampaign(b))
    assertEquals(optionsAt(started, ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))
    val kind = accepted(TargetingFixture.rules.resolveWalker(started.state,
      b.actor, CampaignIds.kind, ChooseOneAnswer(raid)))
    assertEquals(optionsAt(kind, ActionRef.Campaign),
      Vector[DecisionOptionRef](DecisionOptionRef.Player(b.bystander)))

  test("a facedown Diplomat protects nobody"):
    val b = rivalAtOriginWithDiplomat(facedown = true)
    assertEquals(optionsAt(accepted(startCampaign(b)), ActionRef.Campaign),
      Vector[DecisionOptionRef](conquest, raid))

  test("a Campaign already under way is never refused by the start rule"):
    val b = rivalRulesOriginWithDiplomat
    val power = Diplomat.forCatalog(catalog)
    val restriction = as[Restriction](
      power.contributions(PowerWindow.CampaignActionEligibility).head)
    def ctx(state: ReadyGame) = PowerCtx(state, b.actor, power.source,
      PowerWindow.CampaignActionEligibility, Vector.empty,
      Sequence(Vector.empty))
    assertEquals(restriction.fn(ctx(b.ready), Sequence(Vector.empty)),
      Some(protectsEveryDefender))
    // The Campaign has answered its force decision: it is under way.
    val underway = Table.from(b.ready).parkedWalker(ActionRef.Campaign,
      path = "2", answered = Vector(Answered(CampaignIds.force,
        DecisionAnswer.ChooseAmountAnswer(0), b.actor))).ready
    assertEquals(restriction.fn(ctx(underway), Sequence(Vector.empty)), None)
