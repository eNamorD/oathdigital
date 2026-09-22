package oathdigital.gameplay.powers.recover

import oathdigital.gameplay.CatacombsContributionSuite
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class RelicWorshipSuite extends munit.FunSuite {
  import SearchFixture.rules

  private val worship = DenizenId("173")
  private val modifiers = Vector(RelicWorship.id)
  /** A Recover site with a facedown relic, the actor holding Relic Worship as
    * a faceup adviser, with `secrets` faceup secrets and 4 Supply.
    */
  private def staged(secrets: Int = 2)
      : (ReadyGame, CatacombsContributionSuite.Fixture) = {
    val withRelic = CatacombsContributionSuite.relicSite()
    val ready = PowerFixture.withBoard(PowerFixture.asAdviser(CardStaging
      .without(withRelic.ready, worship), worship))(board => board.copy(
      faceUpSecrets = secrets, supply = SupplyTrack(4)))
    (ready, withRelic.copy(ready = ready))
  }

  /** A whole Recover that succeeds and takes the site's relic. */
  private def recover(ready: ReadyGame, selected: Vector[PowerId],
      site: CatacombsContributionSuite.Fixture): (Vector[OathEvent], ReadyGame) = {
    val actor = PowerFixture.actor
    val started = rules.startWalker(Ready(ready), ActionRef.Recover, actor,
      selected).toOption.get
    val rolled = rules.rollWalkerPrepared(started.state, actor,
      RecoverProcedure.recoverPool)(count => Right(
        Vector.fill(count)(DefenseDieFace.TwoShields))).toOption.get
    val relic = RecoverProcedure.actorFacedownRelics(
      rolled.state.asInstanceOf[Ready].value, actor).head.id
    val done = rules.resolveWalker(rolled.state, actor,
      RecoverProcedure.relicDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Relic(relic))).toOption.get
    (started.events ++ rolled.events ++ done.events,
      done.state.asInstanceOf[Ready].value)
  }

  private def me(ready: ReadyGame): PlayerState = PowerFixture.player(ready)

  test("Relic Worship is a registered selected Recover modifier that costs a secret") {
    val power = RelicWorship.forCatalog(catalog).get
    assertEquals(power.cardId, worship)
    assertEquals(power.actions, Set[MajorActionType](MajorActionType.Recover))
    assertEquals(power.cost, Cost(secret = 1))
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
  }

  test("after recovering a relic the player pays a secret onto the card and " +
      "gains 2 Supply") {
    val (ready, site) = staged()
    val (events, result) = recover(ready, modifiers, site)
    assertEquals(me(result).relics.size, me(ready).relics.size + 1)
    assertEquals(me(result).board.faceUpSecrets, 1)
    assertEquals(me(result).advisers.collectFirst {
      case card: DenizenState if card.id == worship => card.tokens
    }, Some(Tokens(0, 1)))
    // 4 Supply, less 1 for the roll, plus 2.
    assertEquals(me(result).board.supply.supply, 4 - 1 + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, events), result)
    // The dice-pool change every Recover starts with does not round-trip the
    // wire (its window is not kept), so it is left out of the check.
    assert(PaidActionHarness.wireRoundTrips(events.filterNot {
      case step: oathdigital.gameplay.walker.WalkerStepRecorded =>
        step.ops.exists(_.isInstanceOf[ModifyDicePool])
      case _ => false
    }))
  }

  test("without the selection nothing is paid and nothing is gained") {
    val (ready, site) = staged()
    val (_, result) = recover(ready, Vector.empty, site)
    assertEquals(me(result).board.faceUpSecrets, 2)
    assertEquals(me(result).board.supply.supply, 4 - 1)
  }

  test("a Recover that ends without a relic has still paid the secret, and " +
      "gains nothing") {
    val (ready, _) = staged()
    val actor = PowerFixture.actor
    val started = rules.startWalker(Ready(ready), ActionRef.Recover, actor,
      modifiers).toOption.get
    // The secret is paid at the start, before any roll.
    val paid = started.state.asInstanceOf[Ready].value
    assertEquals(me(paid).board.faceUpSecrets, 1)
    assertEquals(me(paid).advisers.collectFirst {
      case card: DenizenState if card.id == worship => card.tokens
    }, Some(Tokens(0, 1)))
    val failed = rules.rollWalkerPrepared(started.state, actor,
      RecoverProcedure.recoverPool)(count => Right(
        Vector.fill(count)(DefenseDieFace.Blank))).toOption.get
    val stopped = rules.resolveWalker(failed.state, actor,
      RecoverProcedure.choiceDecisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("stop"))).toOption.get
    val result = stopped.state.asInstanceOf[Ready].value
    assertEquals(me(result).board.faceUpSecrets, 1)
    assertEquals(me(result).board.supply.supply, 4 - 1)
  }

  test("Catacombs and Relic Worship with one faceup secret are refused at " +
      "selection, and accepted with two") {
    val catacombs = PowerId("denizen.catacombs")
    def attempt(secrets: Int) = {
      val fixture = CatacombsContributionSuite.reliclessSite(secrets)
      val ready = PowerFixture.asAdviser(CardStaging.without(fixture.ready,
        worship), worship)
      rules.startWalker(Ready(ready), ActionRef.Recover, PowerFixture.actor,
        Vector(catacombs, RelicWorship.id))
    }
    val refused = attempt(1)
    assert(refused.left.toOption.exists(_.toString.contains(
      "cannot all be paid together")), refused.toString)
    assert(attempt(2).isRight)
  }

  test("it cannot be selected without a faceup secret, or onto an occupied card") {
    val (broke, _) = staged(secrets = 0)
    assert(rules.startWalker(Ready(broke), ActionRef.Recover, PowerFixture.actor,
      modifiers).isLeft)
    val (ready, _) = staged()
    val occupied = PowerFixture.updateActor(ready)(p => p.copy(advisers =
      p.advisers.map {
        case card: DenizenState if card.id == worship =>
          card.copy(tokens = Tokens(0, 1))
        case other => other
      }))
    assert(rules.startWalker(Ready(occupied), ActionRef.Recover,
      PowerFixture.actor, modifiers).isLeft)
  }

  test("a Recover with the card in reach records no ignored-rule diagnostic") {
    val (ready, site) = staged()
    val (events, _) = recover(ready, Vector.empty, site)
    assert(!events.exists(_.isInstanceOf[OathEvent.IgnoredRulesRecorded]))
  }
}
