package oathdigital.application

import oathdigital.model.OathState.Ready
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.rest.{SilverTongue, SilverTongueFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class PhasePowerProjectorSuite extends munit.FunSuite {
  private val projector = new GameProjector(catalog)

  test("a usable REST power is projected and legal for its active player only") {
    val (ready, actor) = SilverTongueFixture.arranged(Vector(Suit.Arcane),
      Set(Suit.Arcane))
    val own = projector.project("phase-powers", LoadedGame(Ready(ready), 30L), actor)
    assertEquals(own.phasePowers.map(p => p.powerId -> p.source.id),
      Vector(SilverTongue.id.value -> "92"))
    assert(own.phasePowers.head.rulesText.nonEmpty)
    assert(own.legalControls.contains("usePower:denizen.silver-tongue:92"))
    assert(own.legalControls.contains("finishRest"))
    val other = ready.game.current.players.map(_.player).find(_ != actor).get
    val theirs = projector.project("phase-powers", LoadedGame(Ready(ready), 30L),
      other)
    assertEquals(theirs.phasePowers, Vector.empty)
    assert(!theirs.legalControls.exists(_.startsWith("usePower:")))
  }

  test("a synthetic WAKE or ACTION power is projected and legal only in its phase") {
    import oathdigital.gameplay.PhasePowerFixture.{TestPower, actor, card,
      inPhase, powerId}
    val control = s"usePower:${powerId.value}:${card.value}"
    Vector(PowerTiming.Wake -> Phase.Wake, PowerTiming.Act -> Phase.Act).foreach {
      case (timing, phase) =>
        val synthetic = new GameProjector(catalog,
          PhasePowers(Vector(TestPower(powerId, timing))))
        Vector(Phase.Wake, Phase.Act).foreach { shown =>
          val projected = synthetic.project("synthetic-powers",
            LoadedGame(Ready(inPhase(shown)), 30L), actor)
          val legal = shown == phase
          assertEquals(projected.phasePowers.map(_.powerId),
            if (legal) Vector(powerId.value) else Vector.empty,
            s"$timing power in $shown")
          assertEquals(projected.legalControls.contains(control), legal,
            s"$timing power in $shown")
        }
    }
  }

  test("a phase power used from a held relic is projected and legal with the " +
      "relic's printed name and text") {
    import oathdigital.gameplay.{IndexedRuleSource, RuleSourceIndex}
    import oathdigital.gameplay.PhasePowerFixture.{TestPower, actor, base}
    val current = base.game.current
    val relic = current.commonCards.relicDeck.find(id => catalog.relics.exists(
      r => r.id.value == id.value && r.powers.nonEmpty)).get
    val held = base.copy(game = base.game.copy(current = current.copy(
      turn = TurnState(actor, Phase.Act, Set.empty),
      players = current.players.map(p => if (p.player != actor) p else
        p.copy(relics = p.relics :+ RelicState(relic, Orientation.FaceUp,
          Tokens.empty))),
      commonCards = current.commonCards.copy(relicDeck =
        current.commonCards.relicDeck.filterNot(_ == relic)))))
    val powerId = RuleSourceIndex.enumerate(catalog, held).collectFirst {
      case IndexedRuleSource(RuleSourceRef.Relic(`actor`, `relic`), ids, _, _)
          if ids.nonEmpty => ids.head
    }.get
    val printed = catalog.relics.find(_.id.value == relic.value).get
    val projected = new GameProjector(catalog,
      PhasePowers(Vector(TestPower(powerId, PowerTiming.Act))))
      .project("relic-power", LoadedGame(Ready(held), 30L), actor)
    assertEquals(projected.phasePowers.map(p =>
      (p.powerId, p.source.kind, p.source.id, p.name, p.rulesText)),
      Vector((powerId.value, "relic", relic.value, printed.name,
        printed.powers.find(_.id == powerId).get.rulesText)))
    assert(projected.legalControls.contains(
      s"usePower:${powerId.value}:${relic.value}"))
  }

  test("an unusable power is neither projected nor legal") {
    val (ready, actor) = SilverTongueFixture.arranged(Vector(Suit.Arcane),
      Set(Suit.Nomad))
    val own = projector.project("phase-powers", LoadedGame(Ready(ready), 30L), actor)
    assertEquals(own.phasePowers, Vector.empty)
    assert(!own.legalControls.exists(_.startsWith("usePower:")))
  }

  test("Rest still offers Finish Rest when catalog drift makes its gate fail") {
    val (ready, actor) = SilverTongueFixture.arranged(Vector(Suit.Arcane),
      Set(Suit.Arcane))
    val drifted = catalog.copy(denizens = catalog.denizens.filterNot(_.id.value ==
      "92"))
    val projected = new GameProjector(drifted).project("drifted-rest",
      LoadedGame(Ready(ready), 30L), actor)

    assert(projected.legalControls.contains("finishRest"))
  }

  test("real League Treaty and Silver Tongue parks project their panels") {
    val treatyRepository = new InMemoryEventStreamRepository
    val treatyService = new GameApplicationService(catalog, treatyRepository)
    val (treatyPark, active, ruler) = ParkedServiceFixture.leagueTreatyPark(
      treatyService, treatyRepository, "project-league-treaty")
    val treatyOwner = projector.project("project-league-treaty",
      LoadedGame(treatyPark.state, treatyPark.nextSequence), ruler)
    assertEquals(treatyOwner.walkerDecision.map(_.query.map(_.form)),
      Some(Some("choose-one")))
    val treatyWaiter = projector.project("project-league-treaty",
      LoadedGame(treatyPark.state, treatyPark.nextSequence), active)
    assertEquals(treatyWaiter.walkerDecision, None)
    assertEquals(treatyWaiter.walkerWaiting.map(_.playerId), Some(ruler.value))

    val tongueRepository = new InMemoryEventStreamRepository
    val tongueService = new GameApplicationService(catalog, tongueRepository)
    val (tonguePark, actor, _) = ParkedServiceFixture.silverTonguePark(
      tongueService, tongueRepository, "project-silver-tongue")
    val tongueOwner = projector.project("project-silver-tongue",
      LoadedGame(tonguePark.state, tonguePark.nextSequence), actor)
    assertEquals(tongueOwner.walkerDecision.map(_.query.map(_.form)),
      Some(Some("choose-one")))
    assertEquals(tongueOwner.legalControls, Vector("resolveWalkerDecision"))
  }
}
