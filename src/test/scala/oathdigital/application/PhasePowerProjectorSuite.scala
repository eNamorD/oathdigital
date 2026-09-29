package oathdigital.application

import oathdigital.model.OathState.Ready
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.rest.{SilverTongue, SilverTongueFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

class PhasePowerProjectorSuite extends munit.FunSuite:
  private val projector = new GameProjector(catalog)

  test("a usable REST power is projected and legal for its active player only"):
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

  test("a synthetic WAKE or ACTION power is projected and legal only in its phase"):
    import oathdigital.gameplay.PhasePowerFixture.{TestPower, card, holding,
      powerId}
    val control = s"usePower:${powerId.value}:${card.value}"
    Vector(PowerTiming.Wake -> Phase.Wake, PowerTiming.Act -> Phase.Act).foreach:
      case (timing, phase) =>
        val synthetic = new GameProjector(catalog,
          PhasePowers(Vector(TestPower(powerId, timing))))
        Vector(Phase.Wake, Phase.Act).foreach { shown =>
          val projected = synthetic.project("synthetic-powers",
            LoadedGame(Ready(holding(shown)), 30L), p1)
          val legal = shown == phase
          assertEquals(projected.phasePowers.map(_.powerId),
            if legal then Vector(powerId.value) else Vector.empty,
            s"$timing power in $shown")
          assertEquals(projected.legalControls.contains(control), legal,
            s"$timing power in $shown")
        }

  test("a phase power used from a held relic is projected and legal with the " +
      "relic's printed name and text"):
    import oathdigital.gameplay.{IndexedRuleSource, RuleSourceIndex}
    import oathdigital.gameplay.PhasePowerFixture.TestPower
    // p1 holds the Whistle faceup; a test power stands in for its printed one.
    val relic = CatalogNames.relic("Whistle")
    val held = Table.start.relic(p1, relic).ready
    val powerId = RuleSourceIndex.enumerate(catalog, held).collectFirst {
      case IndexedRuleSource(RuleSourceRef.Relic(`p1`, `relic`), ids, _, _)
          if ids.nonEmpty => ids.head
    }.get
    val printed = catalog.relics.find(_.id.value == relic.value).get
    val projected = new GameProjector(catalog,
      PhasePowers(Vector(TestPower(powerId, PowerTiming.Act))))
      .project("relic-power", LoadedGame(Ready(held), 30L), p1)
    assertEquals(projected.phasePowers.map(p =>
      (p.powerId, p.source.kind, p.source.id, p.name, p.rulesText)),
      Vector((powerId.value, "relic", relic.value, printed.name,
        printed.powers.find(_.id == powerId).get.rulesText)))
    assert(projected.legalControls.contains(
      s"usePower:${powerId.value}:${relic.value}"))

  test("a phase power used from an edifice at the pawn's site is projected " +
      "and legal with the edifice face's printed name"):
    import oathdigital.gameplay.PhasePowerFixture.{TestPower, card, powerId}
    // Hall of Debate stands at p1's site, printed with the test power's power.
    val id = CatalogNames.edifice("Hall of Debate")
    val edifice = catalog.edifices.find(_.id.value == id.value).get
    val printed = catalog.denizens.find(_.id.value == card.value).get.powers
      .find(_.id == powerId).get
    val powered = catalog.copy(edifices = catalog.edifices.map(e =>
      if e.id == edifice.id then e.copy(
        intact = e.intact.copy(powers = e.intact.powers :+ printed)) else e))
    val state = Table.start
      .edifice(id, EdificeSide.Intact, at = Table.homeOf(p1)).ready
    val projected = new GameProjector(powered,
      PhasePowers(Vector(TestPower(powerId, PowerTiming.Act))))
      .project("edifice-power", LoadedGame(Ready(state), 30L), p1)
    assertEquals(projected.phasePowers.map(p => (p.powerId, p.name)),
      Vector((powerId.value, edifice.intact.name)))
    assert(projected.legalControls.exists(_.startsWith(
      s"usePower:${powerId.value}:")))

  test("an unusable power is neither projected nor legal"):
    val (ready, actor) = SilverTongueFixture.arranged(Vector(Suit.Arcane),
      Set(Suit.Nomad))
    val own = projector.project("phase-powers", LoadedGame(Ready(ready), 30L), actor)
    assertEquals(own.phasePowers, Vector.empty)
    assert(!own.legalControls.exists(_.startsWith("usePower:")))

  test("Rest still offers Finish Rest when catalog drift makes its gate fail"):
    val (ready, actor) = SilverTongueFixture.arranged(Vector(Suit.Arcane),
      Set(Suit.Arcane))
    val drifted = catalog.copy(denizens = catalog.denizens.filterNot(_.id.value ==
      "92"))
    val projected = new GameProjector(drifted).project("drifted-rest",
      LoadedGame(Ready(ready), 30L), actor)

    assert(projected.legalControls.contains("finishRest"))

  test("real League Treaty and Silver Tongue parks project their panels"):
    val (treatyGame, active, ruler) = ParkedServiceFixture.leagueTreatyPark(
      "project-league-treaty")
    val treatyPark = treatyGame.accepted
    val treatyOwner = projector.project("project-league-treaty",
      LoadedGame(treatyPark.state, treatyPark.nextSequence), ruler)
    // The ruler chooses a favor bank to return favor to, or declines.
    assertEquals(treatyOwner.walkerDecision.flatMap(_.query)
      .map(_.offeredOptions.map(option => (option.kind, option.id))),
      Some(Vector("discord", "arcane", "order", "hearth", "beast", "nomad")
        .map("favor-bank" -> _) :+ ("button" -> "decline")))
    val treatyWaiter = projector.project("project-league-treaty",
      LoadedGame(treatyPark.state, treatyPark.nextSequence), active)
    assertEquals(treatyWaiter.walkerDecision, None)
    assertEquals(treatyWaiter.walkerWaiting.map(_.playerId), Some(ruler.value))

    val (tongueGame, tongueActor, _) = ParkedServiceFixture.silverTonguePark(
      "project-silver-tongue")
    val tonguePark = tongueGame.accepted
    val tongueOwner = projector.project("project-silver-tongue",
      LoadedGame(tonguePark.state, tonguePark.nextSequence), tongueActor)
    assertEquals(tongueOwner.walkerDecision.flatMap(_.query)
      .map(_.offeredOptions.map(option => (option.kind, option.id))),
      Some(Vector("favor-bank" -> "discord", "favor-bank" -> "order")))
    assertEquals(tongueOwner.legalControls, Vector("resolveWalkerDecision"))

  test("a site's phase power is projected under its power's name and printed text"):
    import oathdigital.gameplay.powers.wake.RiverFixture
    val ready = RiverFixture.staged()
    val actor = ready.game.current.turn.activePlayer
    val projected = projector.project("river", LoadedGame(Ready(ready), 30L), actor)
    assertEquals(projected.phasePowers.filter(_.powerId ==
      "site.ancient-city.river").map(p =>
      (p.source.kind, p.source.id, p.name, p.rulesText)), Vector(("site",
      "site:ancient-city", "River",
      "WAKE: You may place your pawn at another River. This is not a Travel action.")))
    assert(projected.legalControls.contains(
      "usePower:site.ancient-city.river:site:ancient-city"))
