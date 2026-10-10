package oathdigital.application

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.powers.rest.SilverTongueFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, Table, TestCards}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.present

class PhasePowerMenuSuite extends munit.FunSuite:
  private val projector = new GameProjector(catalog)
  private val silverTongueId = CatalogNames.denizen("Silver Tongue").value

  test("a usable REST power is offered in the Rest menu"):
    // Golem Legions (Arcane) shows at p1's site, and the Arcane bank holds 3
    // favor: Silver Tongue has a bank to take from.
    val ready = SilverTongueFixture.silverTongueRest(Vector("Golem Legions"),
      Set(Suit.Arcane))
    val menu = MenuPlay.options(Table.from(ready).phaseMenuParked.state,
      catalog)
    assertEquals(menu.map(option => option.ref -> option.available),
      Vector[(DecisionOptionRef, Boolean)](
        DecisionOptionRef.Denizen(CatalogNames.denizen("Silver Tongue")) ->
          true,
        PhaseMenu.Menu.Rest.endingOption -> true))

  test("a synthetic WAKE or ACTION power is offered only in its phase's " +
      "menu"):
    import oathdigital.gameplay.PhasePowerFixture.{TestPower, card, holding,
      powerId}
    // TestPower stands in for the power Magician's Code prints, which p1
    // holds faceup.
    Vector(PowerTiming.Wake -> Phase.Wake, PowerTiming.Act -> Phase.Act).foreach:
      case (timing, phase) =>
        Vector(Phase.Wake, Phase.Act).foreach { shown =>
          val menu = MenuPlay.options(Table.from(holding(shown))
            .phaseMenuParked.state, catalog, WalkerPowers.empty,
            PhasePowers(Vector(TestPower(powerId, timing))))
          assertEquals(menu.exists(option =>
            option.ref == DecisionOptionRef.Denizen(card) && option.available),
            shown == phase, s"$timing power in $shown")
        }

  test("a phase power used from a held relic is offered at the relic"):
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
    val menu = MenuPlay.options(Table.from(held).phaseMenuParked.state,
      catalog, WalkerPowers.empty,
      PhasePowers(Vector(TestPower(powerId, PowerTiming.Act))))
    assert(menu.exists(option => option.ref == DecisionOptionRef.Relic(relic) &&
      option.available), menu)

  test("a phase power used from an edifice at the pawn's site is offered at " +
      "the edifice"):
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
    val state = Table.start.pawn(p1, "Ancient City")
      .edifice(id, EdificeSide.Intact, at = "Ancient City")
    val menu = MenuPlay.options(state.phaseMenuParked.state, powered,
      WalkerPowers.empty,
      PhasePowers(Vector(TestPower(powerId, PowerTiming.Act))))
    assert(menu.exists(option => option.ref == DecisionOptionRef.Edifice(id) &&
      option.available), menu)

  test("an unusable power is shown unavailable in the Rest menu"):
    // Only the Nomad bank holds favor, and the Arcane bank, Golem Legions'
    // suit, is empty: Silver Tongue has nothing to take.
    val ready = SilverTongueFixture.silverTongueRest(Vector("Golem Legions"),
      Set(Suit.Nomad))
    val menu = MenuPlay.options(Table.from(ready).phaseMenuParked.state,
      catalog)
    assertEquals(menu.map(option => option.ref -> option.available),
      Vector[(DecisionOptionRef, Boolean)](
        DecisionOptionRef.Denizen(CatalogNames.denizen("Silver Tongue")) ->
          false,
        PhaseMenu.Menu.Rest.endingOption -> true))

  test("the Rest menu still offers Finish Rest when catalog drift removes " +
      "the power"):
    val ready = SilverTongueFixture.silverTongueRest(Vector("Golem Legions"),
      Set(Suit.Arcane))
    val drifted = catalog.copy(denizens = catalog.denizens.filterNot(_.id.value ==
      silverTongueId))
    val menu = MenuPlay.options(Table.from(ready).phaseMenuParked.state,
      drifted, WalkerPowers.empty, PhasePowers.empty)

    assertEquals(menu.map(option => option.ref -> option.available),
      Vector[(DecisionOptionRef, Boolean)](
        PhaseMenu.Menu.Rest.endingOption -> true))

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
    assert(tongueOwner.walkerDecision.nonEmpty)

  test("a site's phase power is offered in the Wake menu at its site"):
    import oathdigital.gameplay.powers.wake.RiverFixture
    // p1 wakes at Ancient City, a River, with Riverbank and Tidal Marshes in
    // play as two more.
    val ready = RiverFixture.staged(rivers =
      Vector(RiverFixture.riverbank, RiverFixture.tidalMarshes),
      phase = Phase.Wake)
    val menu = MenuPlay.options(Table.from(ready).phaseMenuParked.state,
      catalog)
    assert(menu.exists(option => option.ref ==
      DecisionOptionRef.Site(CatalogNames.site("Ancient City")) &&
      option.available), menu)
