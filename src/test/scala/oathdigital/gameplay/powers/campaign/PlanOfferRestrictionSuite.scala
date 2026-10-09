package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.CatalogNames

/** The plan window runs its option restrictions over the plans it is
  * offered (catalog batch 6, "Option restrictions over offers"): a forbidden
  * plan is withheld with the restriction's note, or dropped without one.
  */
class PlanOfferRestrictionSuite extends munit.FunSuite:
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val encirclement = CatalogNames.denizen("Encirclement")
  private val encirclementRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(encirclement)
  private val title: DecisionOptionRef = DecisionOptionRef.Button("title")

  /** "Not this plan." */
  private val barred = NoteKey("barred",
    Vector(NotePart.Text("Not this plan.")))

  /** Forbids the defender's plan `forbidden`, noting why when `noted`. */
  private final case class ForbidsDefenderPlan(forbidden: DecisionOptionRef,
      noted: Boolean) extends ContributingPower:
    val id: PowerId = PowerId("test.forbids-defender-plan")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    override def noteKeys: Vector[NoteKey] = Vector(barred)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignDefenderBattlePlans -> Vector(OptionRestriction(
        (_, ref) => Option.when(ref == forbidden)(
          OathViolation.CampaignUnavailable("forbidden in this suite")),
        (_, ref) => Option.when(noted && ref == forbidden)(
          barred(PowerSourceRef.Card(longbows))))))

  /** The title's defense, Longbows and Encirclement, with `rule`. */
  private def plansWith(rule: ContributingPower): Vector[ContributingPower] =
    Vector(TitleDefensePlan.plan, Longbows, Encirclement, rule)

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * whose defense keeps their window asked, and holds Longbows faceup. */
  private def rivalDefendsWithLongbows: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, longbows, Orientation.FaceUp)

  /** A Conquest of `b` with `powers`, at the defender's plan window. */
  private def defenderWindow(b: Board, powers: Vector[ContributingPower],
      force: Int = 3): Run =
    commit(rulesWith(powers, winningDice), b, force)
      .finishAttackerPlans(b.actor)

  test("a restriction with a note withholds the plan with that note"):
    val b = rivalDefendsWithLongbows
    val rule = ForbidsDefenderPlan(longbowsRef, noted = true)
    val powers = plansWith(rule)
    val run = defenderWindow(b, powers)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assertEquals(run.withheld(b.actor, longbowsRef, WalkerPowers(powers)),
      Some(OptionReason.Noted(rule.id,
        barred(PowerSourceRef.Card(longbows)))))
    assert(run.offered(b.actor, WalkerPowers(powers)).contains(title))

  test("a restriction without a note drops the plan"):
    val b = rivalDefendsWithLongbows
    val powers = plansWith(ForbidsDefenderPlan(longbowsRef, noted = false))
    val shown = defenderWindow(b, powers)
      .options(b.actor, WalkerPowers(powers)).map(_.ref)
    assert(!shown.contains(longbowsRef))
    assert(shown.contains(title))

  test("a forbidden plan cannot be chosen"):
    val b = rivalDefendsWithLongbows
    val run = defenderWindow(b,
      plansWith(ForbidsDefenderPlan(longbowsRef, noted = true)))
    assert(run.refused(b.rival, CampaignIds.defenderPlan,
      ChooseOneAnswer(longbowsRef)).nonEmpty)

  test("a plan its own rule withheld keeps that reason"):
    // Two warbands committed against the rival's two: the forces are equal.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(base, base.rival, encirclement,
      Orientation.FaceUp))(_.favor(base.rival, 1))
    val powers = plansWith(ForbidsDefenderPlan(encirclementRef, noted = true))
    val run = defenderWindow(b, powers, force = 2)
    assertEquals(run.withheld(b.actor, encirclementRef, WalkerPowers(powers)),
      Some(OptionReason.Written(Encirclement.SmallerForce)))

  test("a bandit defender does not apply a forbidden plan"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, longbows)
    val applied = ModifyDicePool(CampaignPlans.appliedMarker(longbowsRef), 1)
    def ops(powers: Vector[ContributingPower]): Vector[CoreOperation] =
      commit(rulesWith(powers, winningDice), b, 3).finishPlanWindows.ops
    assert(ops(Vector(Longbows)).contains(applied))
    assert(!ops(plansWith(ForbidsDefenderPlan(longbowsRef, noted = true)))
      .contains(applied))
