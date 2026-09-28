package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.{CampaignFixture, OathRules}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker, WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class KnightsErrantSuite extends munit.FunSuite:
  private val knights = CatalogNames.denizen("Knights Errant")
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val modifiers = Vector(KnightsErrant.id)
  private val actor = p1
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = CampaignFixture.anyDice)

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
  private def staged(supply: Int = 1, campaignLegal: Boolean = true)
      : ReadyGame =
    Table.start
      .adviser(p1, knights)
      .denizen(alchemist, at = Table.homeOf(p1))
      .bandits(Table.homeOf(p1), if campaignLegal then 2 else 0)
      .supply(p1, supply).favor(p1, 4)
      .ready

  private def ready(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  private def me(state: ReadyGame): PlayerState = Look(state).player(p1)

  /** Starts a Muster with `selected` and answers its source. */
  private def musterFrom(state: ReadyGame, selected: Vector[PowerId])
      : OathTransition =
    val started = rules.startWalker(Ready(state), ActionRef.Muster, actor,
      selected).toOption.get
    val done = rules.resolveWalker(started.state, actor,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist))).toOption.get
    done.copy(events = started.events ++ done.events)

  private def answer(from: OathTransition, id: String, choice: DecisionAnswer)
      : OathTransition =
    val next = rules.resolveWalker(from.state, actor, id, choice).toOption.get
    next.copy(events = from.events ++ next.events)

  private def parkedOn(transition: OathTransition): String =
    parked.parkedDecision(transition.state).fold("")(_.decision)

  private def query(transition: OathTransition): DecisionQuery =
    val state = ready(transition)
    val current = state.game.current
    val tree = WalkerProcedureRegistry.rebuild(ActionRef.Muster, catalog, state,
      actor, current.walkerStartArgs).toOption.get
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      current.walkerModifiers)
    ProcedureWalker.parkedDecide(state, tree, current.walkerPending.get, powers)
      .get.query

  /** Answers the optional-targets decision with none, if it is asked. */
  private def toForce(transition: OathTransition): OathTransition =
    if parkedOn(transition) == CampaignIds.targets then
      answer(transition, CampaignIds.targets, ChooseManyAnswer(Vector.empty))
    else transition

  /** The warbands the actor has once the Muster has gained: the matching-adviser
    * bonus depends on the cards drawn, so it is read from a Muster without the
    * power.
    */
  private def afterMuster: Int =
    me(ready(musterFrom(staged(), Vector.empty))).board.warbands

  private val campaign = ChooseOneAnswer(KnightsErrant.campaignOption)
  private val decline = ChooseOneAnswer(KnightsErrant.declineOption)

  test("Knights Errant is a registered selected Muster modifier"):
    val power = KnightsErrant.forCatalog(catalog).get
    assertEquals(power.cardId, knights)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Muster))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)

  test("after the gain it asks whether to campaign, as a Muster decision"):
    val asked = musterFrom(staged(), modifiers)
    parked.assertParked(asked.state, ActionRef.Muster, KnightsErrant.decisionId,
      actor)
    assertEquals(query(asked).asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](KnightsErrant.campaignOption,
      KnightsErrant.declineOption))
    // The Muster's own gain has already happened.
    assertEquals(me(ready(asked)).board.warbands, afterMuster)

  test("declining ends the Muster with no Campaign"):
    val done = answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, decline)
    assertEquals(ready(done).game.current.walkerProcedure, None)
    assertEquals(ready(done).game.current.lastCampaignResult, None)
    assertEquals(me(ready(done)).board.supply.supply, 0)

  test("nothing is asked when no Campaign is legal"):
    val done = musterFrom(staged(campaignLegal = false), modifiers)
    assertEquals(ready(done).game.current.walkerPending, None)
    assertEquals(me(ready(done)).board.warbands, afterMuster)

  test("without the selection the Muster ends as before"):
    val done = musterFrom(staged(), Vector.empty)
    assertEquals(ready(done).game.current.walkerPending, None)

  test("the Campaign sees the warbands the Muster gained and costs no Supply"):
    val start = staged(supply = 1)
    val forced = toForce(answer(musterFrom(start, modifiers),
      KnightsErrant.decisionId, campaign))
    parked.assertParked(forced.state, ActionRef.Muster, CampaignIds.force, actor)
    assertEquals(query(forced).asInstanceOf[DecisionQuery.ChooseAmount].max,
      afterMuster)
    // 1 Supply less the Muster's 1: the Campaign's 2 was not spent.
    assertEquals(me(ready(forced)).board.supply.supply, 0)

  test("the same Campaign started on its own is refused for want of Supply"):
    val alone = staged(supply = 0)
    assert(rules.startWalker(Ready(alone), ActionRef.Campaign, actor).isLeft)

  test("a Campaign run this way finishes, and the Muster ends after it"):
    val forced = toForce(answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, campaign))
    val done = answer(forced, CampaignIds.force, ChooseAmountAnswer(0))
    val result = ready(done)
    assertEquals(result.game.current.walkerProcedure, None)
    assert(result.game.current.lastCampaignResult.nonEmpty)
    assertEquals(PaidActionHarness.replayed(rules, staged(), done.events),
      result)

  test("it cannot be selected for a Campaign, or for any other action"):
    val state = staged()
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(state,
      actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Campaign).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Trade).contains(KnightsErrant.id))
    assert(rules.startWalker(Ready(state), ActionRef.Campaign, actor,
      modifiers).isLeft)

  // ---- Restrictions on the whole Campaign apply to the nested one ----

  private def campaigning(from: OathTransition)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor, KnightsErrant.decisionId, campaign)

  test("Vow of Peace forbids the nested Campaign, so the look-ahead hides " +
      "the option, and declining is still allowed"):
    val ready = Table.start
      .adviser(p1, knights).adviser(p1, "Vow of Peace")
      .denizen(alchemist, at = Table.homeOf(p1))
      .bandits(Table.homeOf(p1), 2)
      .supply(p1, 1).favor(p1, 4)
      .ready
    val asked = musterFrom(ready, modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    assertEquals(query(asked).asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](KnightsErrant.declineOption))
    assertEquals(campaigning(asked), Left(OathViolation.InvalidEventOrder(
      s"decision ${KnightsErrant.decisionId} does not offer the selected " +
        "option")))
    assert(rules.resolveWalker(asked.state, actor, KnightsErrant.decisionId,
      decline).isRight)

  test("a Fortress that protects every player a Raid could target forbids " +
      "the nested Campaign, so the look-ahead hides the option"):
    // Nobody rules the site, so a Conquest is not legal. An enemy pawn stands
    // there, so a Raid is, and the Rotting Fortress protects that enemy.
    val base = staged(campaignLegal = false)
    val other = p2
    val site = Table.homeOf(p1)
    val fortified = TargetingFixture.fortressAt(
      TargetingFixture.pawnAt(base, other, site), EdificeSide.Ruined, site)
    val asked = musterFrom(fortified, modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    assertEquals(query(asked).asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](KnightsErrant.declineOption))
    assertEquals(campaigning(asked), Left(OathViolation.InvalidEventOrder(
      s"decision ${KnightsErrant.decisionId} does not offer the selected " +
        "option")))
    // The same board without the Fortress lets the Raid start.
    val open = musterFrom(TargetingFixture.pawnAt(base, other, site), modifiers)
    assert(campaigning(open).isRight)

  test("a nested Campaign that is allowed is not stopped by a restriction " +
      "once it is under way"):
    val forced = toForce(answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, campaign))
    // Every later command of the Campaign is checked against its answers too,
    // and none of them is refused.
    val done = answer(forced, CampaignIds.force, ChooseAmountAnswer(0))
    assertEquals(ready(done).game.current.walkerProcedure, None)

  // ---- Lines ----

  private val power = KnightsErrant.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("choosing to campaign writes the Knights' line after the choice"):
    val asked = musterFrom(staged(), modifiers)
    assertEquals(said(asked.events), Vector.empty)
    val chosen = answer(asked, KnightsErrant.decisionId, campaign)
    assertEquals(said(chosen.events), Vector(NoteText.Said("campaigns",
      s"${actor.value} campaigns for no Supply.", covers = false)))

  test("declining writes nothing"):
    assertEquals(said(answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, decline).events), Vector.empty)
