package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.{OathRules, WalkerDiceFixture}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseAmountAnswer,
  ChooseOneAnswer}
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, as, present}

class KnightsErrantSuite extends munit.FunSuite:
  private val knights = CatalogNames.denizen("Knights Errant")
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val modifiers = Vector(KnightsErrant.id)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)

  /** The parked decision, as this file rebuilds it: the same catalog and
    * walker power catalog `rules` was built with.
    */
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  /** p1 holds Knights Errant and stands at Ancient City with the token-free
    * Alchemist to muster from. Two bandits rule the site, so a Conquest is
    * legal, unless `campaignLegal` is false. p1 has `supply` Supply, 4 favor
    * and 3 warbands.
    */
  private def staged(supply: Int, campaignLegal: Boolean = true): Table =
    Table.start
      .pawn(p1, ancientCity)
      .adviser(p1, knights)
      .denizen(alchemist, at = ancientCity)
      .bandits(ancientCity, if campaignLegal then 2 else 0)
      .supply(p1, supply).favor(p1, 4)

  private def ready(transition: OathTransition): ReadyGame =
    Outcomes.ready(transition.state)

  /** Starts a Muster with `selected` and answers its source. */
  private def musterFrom(state: ReadyGame, selected: Vector[PowerId])
      : OathTransition =
    val started = accepted(rules.startWalker(Ready(state), ActionRef.Muster, p1,
      selected))
    val done = accepted(rules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist))))
    done.copy(events = started.events ++ done.events)

  private def answer(from: OathTransition, id: String, choice: DecisionAnswer)
      : OathTransition =
    val next = accepted(rules.resolveWalker(from.state, p1, id, choice))
    next.copy(events = from.events ++ next.events)

  private def parkedOn(transition: OathTransition): String =
    parked.parkedDecision(transition.state).fold("")(_.decision)

  private def query(transition: OathTransition): DecisionQuery =
    val state = ready(transition)
    val current = state.game.current
    val tree = accepted(WalkerProcedureRegistry.rebuild(ActionRef.Muster,
      catalog, state, p1, current.walkerStartArgs))
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      current.walkerModifiers)
    val pending = present(current.walkerPending, "a parked walker decision")
    present(ProcedureWalker.parkedDecide(state, tree, pending, powers),
      "the parked decision's query").query

  /** Answers the optional-targets decision with none, if it is asked. */
  private def toForce(transition: OathTransition): OathTransition =
    if parkedOn(transition) == CampaignIds.targets then
      answer(transition, CampaignIds.targets, ChooseManyAnswer(Vector.empty))
    else transition

  /** The warbands the actor has once the Muster has gained: the
    * matching-adviser bonus depends on the cards drawn, so it is read from a
    * Muster without the power.
    */
  private def afterMuster: Int =
    val plain = musterFrom(staged(supply = 1).ready, Vector.empty)
    Look(ready(plain)).boardWarbands(p1)

  private val campaign = ChooseOneAnswer(KnightsErrant.campaignOption)
  private val decline = ChooseOneAnswer(KnightsErrant.declineOption)

  test("after the gain it asks whether to campaign, as a Muster decision"):
    val asked = musterFrom(staged(supply = 1).ready, modifiers)
    parked.assertParked(asked.state, ActionRef.Muster, KnightsErrant.decisionId,
      p1)
    val options = as[DecisionQuery.ChooseOne](query(asked)).options.map(_.ref)
    assertEquals(options, Vector[DecisionOptionRef](
      KnightsErrant.campaignOption, KnightsErrant.declineOption))
    // The Muster's own gain has already happened.
    assertEquals(Look(ready(asked)).boardWarbands(p1), afterMuster)

  test("declining ends the Muster with no Campaign"):
    val done = answer(musterFrom(staged(supply = 1).ready, modifiers),
      KnightsErrant.decisionId, decline)
    assertEquals(ready(done).game.current.walkerProcedure, None)
    assertEquals(ready(done).game.current.lastCampaignResult, None)
    // The Muster spends p1's only Supply.
    assertEquals(Look(ready(done)).supply(p1), 0)

  test("nothing is asked when no Campaign is legal"):
    val noCampaign = staged(supply = 1, campaignLegal = false).ready
    val done = musterFrom(noCampaign, modifiers)
    assertEquals(ready(done).game.current.walkerPending, None)
    assertEquals(Look(ready(done)).boardWarbands(p1), afterMuster)

  test("without the selection the Muster ends as before"):
    val done = musterFrom(staged(supply = 1).ready, Vector.empty)
    assertEquals(ready(done).game.current.walkerPending, None)

  test("the Campaign sees the warbands the Muster gained and costs no Supply"):
    val start = staged(supply = 1).ready
    val forced = toForce(answer(musterFrom(start, modifiers),
      KnightsErrant.decisionId, campaign))
    parked.assertParked(forced.state, ActionRef.Muster, CampaignIds.force, p1)
    assertEquals(as[DecisionQuery.ChooseAmount](query(forced)).max,
      afterMuster)
    // 1 Supply less the Muster's 1: the Campaign's 2 was not spent.
    assertEquals(Look(ready(forced)).supply(p1), 0)

  test("the same Campaign started on its own is refused for want of Supply"):
    val alone = staged(supply = 0).ready
    assert(rules.startWalker(Ready(alone), ActionRef.Campaign, p1).isLeft)

  test("a Campaign run this way finishes, and the Muster ends after it"):
    val start = staged(supply = 1).ready
    val forced = toForce(answer(musterFrom(start, modifiers),
      KnightsErrant.decisionId, campaign))
    val done = answer(forced, CampaignIds.force, ChooseAmountAnswer(0))
    val result = ready(done)
    assertEquals(result.game.current.walkerProcedure, None)
    assert(result.game.current.lastCampaignResult.nonEmpty)
    assertEquals(ActPowerHarness.replayed(rules, start, done.events), result)

  test("it cannot be selected for a Campaign, or for any other action"):
    val state = staged(supply = 1).ready
    val offered = (action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(state, p1, action)).map(_.id)
    assert(offered(ActionRef.Muster).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Campaign).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Trade).contains(KnightsErrant.id))

  // ---- Restrictions on the whole Campaign apply to the nested one ----

  private def campaigning(from: OathTransition)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, p1, KnightsErrant.decisionId, campaign)

  test("Vow of Peace forbids the nested Campaign, so the look-ahead hides " +
      "the option, and declining is still allowed"):
    val vowed = staged(supply = 1).adviser(p1, "Vow of Peace").ready
    val asked = musterFrom(vowed, modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    val options = as[DecisionQuery.ChooseOne](query(asked)).options.map(_.ref)
    assertEquals(options,
      Vector[DecisionOptionRef](KnightsErrant.declineOption))
    assertEquals(campaigning(asked), Left(OathViolation.InvalidEventOrder(
      s"decision ${KnightsErrant.decisionId} does not offer the selected " +
        "option")))
    assert(rules.resolveWalker(asked.state, p1, KnightsErrant.decisionId,
      decline).isRight)

  test("a Fortress that protects every player a Raid could target forbids " +
      "the nested Campaign, so the look-ahead hides the option"):
    // Nobody rules the site, so a Conquest is not legal. An enemy pawn stands
    // there, so a Raid is, and the Rotting Fortress protects that enemy.
    val board = staged(supply = 1, campaignLegal = false)
      .pawn(p2, at = ancientCity)
    val base = board.ready
    val fortified = board
      .edifice("Rotting Fortress", EdificeSide.Ruined, at = ancientCity).ready
    val asked = musterFrom(fortified, modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    val options = as[DecisionQuery.ChooseOne](query(asked)).options.map(_.ref)
    assertEquals(options,
      Vector[DecisionOptionRef](KnightsErrant.declineOption))
    assertEquals(campaigning(asked), Left(OathViolation.InvalidEventOrder(
      s"decision ${KnightsErrant.decisionId} does not offer the selected " +
        "option")))
    // The same board without the Fortress lets the Raid start.
    val open = musterFrom(base, modifiers)
    assert(campaigning(open).isRight)

  // ---- Lines ----

  private val power = KnightsErrant.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("choosing to campaign writes the Knights' line after the choice"):
    val asked = musterFrom(staged(supply = 1).ready, modifiers)
    assertEquals(said(asked.events), Vector.empty)
    val chosen = answer(asked, KnightsErrant.decisionId, campaign)
    assertEquals(said(chosen.events), Vector(NoteText.Said("campaigns",
      "p1 campaigns for no Supply.", covers = false)))

  test("declining writes nothing"):
    assertEquals(said(answer(musterFrom(staged(supply = 1).ready, modifiers),
      KnightsErrant.decisionId, decline).events), Vector.empty)
