package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.{CampaignFixture, EconomyFixture, OathRules}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.model.OathState.Ready

class KnightsErrantSuite extends munit.FunSuite {
  import EconomyFixture.plainId

  private val knights = DenizenId("120")
  private val modifiers = Vector(KnightsErrant.id)
  private val actor = PowerFixture.actor
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = CampaignFixture.anyDice)

  /** The actor holds Knights Errant and stands at a site with a token-free card
    * to muster from. The site is ruled by bandits, so a Conquest is legal,
    * unless `campaignLegal` is false. The actor has `supply` Supply and 3
    * warbands.
    */
  private def staged(supply: Int = 1, campaignLegal: Boolean = true)
      : ReadyGame = {
    val ready = PowerFixture.asAdviser(CardStaging.without(
      EconomyFixture.act(supply = supply, favor = 4, boardWarbands = 3), knights),
      knights)
    val site = PowerFixture.home(ready)
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites = c.map.sites.updated(
      site, c.map.sites(site).copy(forces =
        if (campaignLegal) SiteForces.Occupied(ForceKind.Bandit, 2)
        else SiteForces.Empty)))))
  }

  private def ready(transition: OathTransition): ReadyGame =
    transition.state.asInstanceOf[Ready].value

  private def me(state: ReadyGame): PlayerState = PowerFixture.player(state)

  /** Starts a Muster with `selected` and answers its source. */
  private def musterFrom(state: ReadyGame, selected: Vector[PowerId])
      : OathTransition = {
    val started = rules.startWalker(Ready(state), ActionRef.Muster, actor,
      selected).toOption.get
    val done = rules.resolveWalker(started.state, actor,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(plainId))).toOption.get
    done.copy(events = started.events ++ done.events)
  }

  private def answer(from: OathTransition, id: String, given: DecisionAnswer)
      : OathTransition = {
    val next = rules.resolveWalker(from.state, actor, id, given).toOption.get
    next.copy(events = from.events ++ next.events)
  }

  private def parkedOn(transition: OathTransition): String =
    ready(transition).game.current.walkerPending
      .fold("")(_ => transition.continue match {
        case OathContinue.AwaitingEconomyDecision(_, id) => id.value
        case OathContinue.AwaitingCampaignDecision(_, id) => id.value
        case other => other.toString
      })

  private def query(transition: OathTransition): DecisionQuery = {
    val state = ready(transition)
    val current = state.game.current
    val tree = WalkerProcedureRegistry.rebuild(ActionRef.Muster, catalog, state,
      actor, current.walkerStartArgs).toOption.get
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      current.walkerModifiers)
    ProcedureWalker.parkedDecide(state, tree, current.walkerPending.get, powers)
      .get.query
  }

  /** Answers the optional-targets decision with none, if it is asked. */
  private def toForce(transition: OathTransition): OathTransition =
    if (parkedOn(transition) == CampaignIds.targets)
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

  test("Knights Errant is a registered selected Muster modifier") {
    val power = KnightsErrant.forCatalog(catalog).get
    assertEquals(power.cardId, knights)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Muster))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("after the gain it asks whether to campaign, as a Muster decision") {
    val asked = musterFrom(staged(), modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    assertEquals(asked.continue, OathContinue.AwaitingEconomyDecision(actor,
      DecisionId(KnightsErrant.decisionId)))
    assertEquals(query(asked).asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](KnightsErrant.campaignOption,
      KnightsErrant.declineOption))
    // The Muster's own gain has already happened.
    assertEquals(me(ready(asked)).board.warbands, afterMuster)
  }

  test("declining ends the Muster with no Campaign") {
    val done = answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, decline)
    assertEquals(ready(done).game.current.walkerProcedure, None)
    assertEquals(ready(done).game.current.lastCampaignResult, None)
    assertEquals(me(ready(done)).board.supply.supply, 0)
  }

  test("nothing is asked when no Campaign is legal") {
    val done = musterFrom(staged(campaignLegal = false), modifiers)
    assertEquals(ready(done).game.current.walkerPending, None)
    assertEquals(me(ready(done)).board.warbands, afterMuster)
  }

  test("without the selection the Muster ends as before") {
    val done = musterFrom(staged(), Vector.empty)
    assertEquals(ready(done).game.current.walkerPending, None)
  }

  test("the Campaign sees the warbands the Muster gained and costs no Supply") {
    val start = staged(supply = 1)
    val forced = toForce(answer(musterFrom(start, modifiers),
      KnightsErrant.decisionId, campaign))
    assertEquals(parkedOn(forced), CampaignIds.force)
    assertEquals(forced.continue, OathContinue.AwaitingCampaignDecision(actor,
      DecisionId(CampaignIds.force)))
    assertEquals(query(forced).asInstanceOf[DecisionQuery.ChooseAmount].max,
      afterMuster)
    // 1 Supply less the Muster's 1: the Campaign's 2 was not spent.
    assertEquals(me(ready(forced)).board.supply.supply, 0)
  }

  test("the same Campaign started on its own is refused for want of Supply") {
    val alone = staged(supply = 0)
    assert(rules.startWalker(Ready(alone), ActionRef.Campaign, actor).isLeft)
  }

  test("a Campaign run this way finishes, and the Muster ends after it") {
    val forced = toForce(answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, campaign))
    val done = answer(forced, CampaignIds.force, ChooseAmountAnswer(0))
    val result = ready(done)
    assertEquals(result.game.current.walkerProcedure, None)
    assert(result.game.current.lastCampaignResult.nonEmpty)
    assertEquals(PaidActionHarness.replayed(rules, staged(), done.events),
      result)
  }

  test("it cannot be selected for a Campaign, or for any other action") {
    val state = staged()
    val offered = (action: ActionRef) => rules.offerableWalkerPowers(state,
      actor, action).toOption.get.map(_.id)
    assert(offered(ActionRef.Muster).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Campaign).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Trade).contains(KnightsErrant.id))
    assert(rules.startWalker(Ready(state), ActionRef.Campaign, actor,
      modifiers).isLeft)
  }

  // ---- Restrictions on the whole Campaign apply to the nested one ----

  private def campaigning(from: OathTransition)
      : Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor, KnightsErrant.decisionId, campaign)

  test("Vow of Peace forbids the nested Campaign, and declining is still allowed") {
    val vow = DenizenId(catalog.denizens.find(_.powers.exists(
      _.id.value == "denizen.vow-of-peace")).get.id.value)
    val ready = PowerFixture.asAdviser(CardStaging.without(staged(), vow), vow)
    val asked = musterFrom(ready, modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    assert(campaigning(asked).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
    assert(rules.resolveWalker(asked.state, actor, KnightsErrant.decisionId,
      decline).isRight)
  }

  test("a Fortress that protects every player a Raid could target forbids the " +
      "nested Campaign") {
    // Nobody rules the site, so a Conquest is not legal. An enemy pawn stands
    // there, so a Raid is, and the Rotting Fortress protects that enemy.
    val base = staged(campaignLegal = false)
    val other = base.game.current.players.map(_.player).find(_ != actor).get
    val site = PowerFixture.home(base)
    val fortified = TargetingFixture.fortressAt(
      TargetingFixture.pawnAt(base, other, site), EdificeSide.Ruined, site)
    val asked = musterFrom(fortified, modifiers)
    assertEquals(parkedOn(asked), KnightsErrant.decisionId)
    assert(campaigning(asked).left.toOption.exists(
      _.isInstanceOf[OathViolation.CampaignUnavailable]))
    // The same board without the Fortress lets the Raid start.
    val open = musterFrom(TargetingFixture.pawnAt(base, other, site), modifiers)
    assert(campaigning(open).isRight)
  }

  test("a nested Campaign that is allowed is not stopped by a restriction " +
      "once it is under way") {
    val forced = toForce(answer(musterFrom(staged(), modifiers),
      KnightsErrant.decisionId, campaign))
    // Every later command of the Campaign is checked against its answers too,
    // and none of them is refused.
    val done = answer(forced, CampaignIds.force, ChooseAmountAnswer(0))
    assertEquals(ready(done).game.current.walkerProcedure, None)
  }
}
