package oathdigital.application

import oathdigital.model.OathState.Ready
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.rest.{SilverTongue, SilverTongueFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table, TestCards}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.present

class PhasePowerProjectorSuite extends munit.FunSuite:
  private val projector = new GameProjector(catalog)
  private val silverTongueId = CatalogNames.denizen("Silver Tongue").value

  test("a usable REST power is projected and legal for its active player only"):
    // Golem Legions (Arcane) shows at p1's site, and the Arcane bank holds 3
    // favor: Silver Tongue has a bank to take from.
    val ready = SilverTongueFixture.silverTongueRest(Vector("Golem Legions"),
      Set(Suit.Arcane))
    val own =
      projector.project("phase-powers", LoadedGame(Ready(ready), 30L), p1)
    assertEquals(own.phasePowers.map(p => p.powerId -> p.source.id),
      Vector(SilverTongue.id.value -> silverTongueId))
    assert(own.phasePowers.head.rulesText.nonEmpty)
    assert(own.legalControls.contains(
      s"usePower:denizen.silver-tongue:$silverTongueId"))
    assert(own.legalControls.contains("finishRest"))
    val theirs = projector.project("phase-powers", LoadedGame(Ready(ready), 30L),
      p2)
    assertEquals(theirs.phasePowers, Vector.empty)
    assert(!theirs.legalControls.exists(_.startsWith("usePower:")))

  test("a synthetic WAKE or ACTION power is projected and legal only in its phase"):
    import oathdigital.gameplay.PhasePowerFixture.{TestPower, card, holding,
      powerId}
    // TestPower stands in for the power Magician's Code prints, which p1
    // holds faceup.
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
    // p1 holds the Whistle faceup; TestPower stands in for the Whistle's
    // printed power.
    val relic = CatalogNames.relic("Whistle")
    val held = Table.start.relic(p1, relic).ready
    val sources = RuleSourceIndex.enumerate(catalog, held)
    val powerId = present(sources.collectFirst {
      case IndexedRuleSource(RuleSourceRef.Relic(`p1`, `relic`), ids, _, _)
          if ids.nonEmpty => ids.head
    }, "the Whistle's power")
    val printed = present(catalog.relics.find(_.id.value == relic.value),
      "the Whistle")
    val projected = new GameProjector(catalog,
      PhasePowers(Vector(TestPower(powerId, PowerTiming.Act))))
      .project("relic-power", LoadedGame(Ready(held), 30L), p1)
    assertEquals(projected.phasePowers.map(p =>
      (p.powerId, p.source.kind, p.source.id, p.name, p.rulesText)),
      Vector((powerId.value, "relic", relic.value, printed.name,
        present(printed.powers.find(_.id == powerId), "its printed power")
          .rulesText)))
    assert(projected.legalControls.contains(
      s"usePower:${powerId.value}:${relic.value}"))

  test("a phase power used from an edifice at the pawn's site is projected " +
      "and legal with the edifice face's printed name"):
    import oathdigital.gameplay.PhasePowerFixture.{TestPower, card, powerId}
    // Hall of Debate stands at Ancient City, p1's site; the copied catalog
    // grafts onto its intact face the power Magician's Code prints, which
    // TestPower stands in for.
    val id = CatalogNames.edifice("Hall of Debate")
    val edifice = present(catalog.edifices.find(_.id.value == id.value),
      "Hall of Debate")
    val printed = present(present(catalog.denizens.find(_.id.value ==
      card.value), "the test card").powers.find(_.id == powerId),
      "the test power")
    val powered = catalog.copy(edifices = catalog.edifices.map(e =>
      if e.id == edifice.id then TestCards.edificeLike(e)(
        intact = e.intact.powers :+ printed) else e))
    val state = Table.start
      .edifice(id, EdificeSide.Intact, at = "Ancient City").ready
    val projected = new GameProjector(powered,
      PhasePowers(Vector(TestPower(powerId, PowerTiming.Act))))
      .project("edifice-power", LoadedGame(Ready(state), 30L), p1)
    assertEquals(projected.phasePowers.map(p => (p.powerId, p.name)),
      Vector((powerId.value, edifice.intact.name)))
    assert(projected.legalControls.exists(_.startsWith(
      s"usePower:${powerId.value}:")))

  test("an unusable power is neither projected nor legal"):
    // Only the Nomad bank holds favor, and the Arcane bank, Golem Legions'
    // suit, is empty: Silver Tongue has nothing to take.
    val ready = SilverTongueFixture.silverTongueRest(Vector("Golem Legions"),
      Set(Suit.Nomad))
    val own =
      projector.project("phase-powers", LoadedGame(Ready(ready), 30L), p1)
    assertEquals(own.phasePowers, Vector.empty)
    assert(!own.legalControls.exists(_.startsWith("usePower:")))

  test("Rest still offers Finish Rest when catalog drift makes its gate fail"):
    val ready = SilverTongueFixture.silverTongueRest(Vector("Golem Legions"),
      Set(Suit.Arcane))
    val drifted = catalog.copy(denizens = catalog.denizens.filterNot(_.id.value ==
      silverTongueId))
    val projected = new GameProjector(drifted).project("drifted-rest",
      LoadedGame(Ready(ready), 30L), p1)

    assert(projected.legalControls.contains("finishRest"))

  test("a League Treaty park shows the ruler the favor banks or decline and " +
      "makes the active player wait"):
    val ParkedServiceFixture.LeagueTreatyPark(treatyGame, active, ruler) =
      ParkedServiceFixture.leagueTreatyPark("project-league-treaty")
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

  test("a Silver Tongue park offers its owner two favor banks"):
    val ParkedServiceFixture.SilverTonguePark(tongueGame, tongueActor, _) =
      ParkedServiceFixture.silverTonguePark("project-silver-tongue")
    val tonguePark = tongueGame.accepted
    val tongueOwner = projector.project("project-silver-tongue",
      LoadedGame(tonguePark.state, tonguePark.nextSequence), tongueActor)
    // Silver Tongue offers the bank of each card at p1's site: Bandit Chief's
    // Discord and Wrestlers' Order, each holding favor.
    assertEquals(tongueOwner.walkerDecision.flatMap(_.query)
      .map(_.offeredOptions.map(option => (option.kind, option.id))),
      Some(Vector("favor-bank" -> "discord", "favor-bank" -> "order")))
    assertEquals(tongueOwner.legalControls, Vector("resolveWalkerDecision"))

  test("a site's phase power is projected under its power's name and printed text"):
    import oathdigital.gameplay.powers.wake.RiverFixture
    // p1 wakes at Ancient City, a River, with Riverbank and Tidal Marshes in
    // play as two more.
    val ready = RiverFixture.staged(rivers =
      Vector(RiverFixture.riverbank, RiverFixture.tidalMarshes),
      phase = Phase.Wake)
    val ancientCityId = CatalogNames.site("Ancient City").value
    val projected =
      projector.project("river", LoadedGame(Ready(ready), 30L), p1)
    assertEquals(projected.phasePowers.filter(_.powerId ==
      "site.ancient-city.river").map(p =>
      (p.source.kind, p.source.id, p.name, p.rulesText)), Vector(("site",
      ancientCityId, "River",
      "WAKE: You may place your pawn at another River. This is not a Travel action.")))
    assert(projected.legalControls.contains(
      s"usePower:site.ancient-city.river:$ancientCityId"))
