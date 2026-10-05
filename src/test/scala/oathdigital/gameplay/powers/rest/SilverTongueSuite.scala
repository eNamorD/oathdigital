package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay._
import oathdigital.model.OathEvent.BanditsRefilled
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerOutcome, WalkerPowers}
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes, PlacementPicks, Table}
import oathdigital.testkit.Unwrap.{accepted, as, present}

class SilverTongueSuite extends munit.FunSuite:
  import SilverTongueFixture._
  private val actor = Table.p1
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val walkerParked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))
  private val use = ActionRef.UsePower(SilverTongue.id)
  private val source = DecisionOptionRef.Denizen(tongue)
  private def favor(state: OathState) = Outcomes.ready(state).banks.favor

  test("one matching bank with favor gives one favor without a decision, " +
      "once per turn, and runs the action boundary"):
    val ready = silverTongueRest(Vector("Golem Legions", "Rain Boots"),
      Set(Suit.Arcane))
    val used = accepted(
      rules.startWalker(Ready(ready), use, actor, Vector.empty, Vector(source)))
    walkerParked.assertResumed(used.state, Phase.Rest, actor)
    // Each stocked bank holds 3; taking one leaves 2.
    assertEquals(favor(used.state)(Suit.Arcane), 2)
    assert(used.events.exists(_.isInstanceOf[BanditsRefilled]))
    val ref = PowerUseRef(PowerTiming.Rest, PowerSourceRef.Card(tongue),
      SilverTongue.id)
    assertEquals(rules.startWalker(used.state, use, actor, Vector.empty,
      Vector(source)).left.toOption, Some(OathViolation.PowerAlreadyUsed(ref)))

  test("several matching banks with favor ask the player to choose one of them"):
    val ready = silverTongueRest(Vector("Golem Legions", "Rain Boots"),
      Set(Suit.Arcane, Suit.Nomad, Suit.Order))
    val choice = SilverTongue.choiceDecisionId(ready, actor)
    val parked = accepted(
      rules.startWalker(Ready(ready), use, actor, Vector.empty, Vector(source)))
    walkerParked.assertParked(parked.state, use, choice, actor)
    assert(rules.resolveWalker(parked.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order)))
      .isLeft)
    val taken = accepted(rules.resolveWalker(parked.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Nomad))))
    // Each stocked bank holds 3: Nomad gave one, Arcane none.
    assertEquals(favor(taken.state)(Suit.Nomad), 2)
    assertEquals(favor(taken.state)(Suit.Arcane), 3)
    walkerParked.assertResumed(taken.state, Phase.Rest, actor)

  test("without matching favor Silver Tongue is not usable and Rest skips ahead"):
    val ready = silverTongueRest(Vector("Golem Legions"), Set(Suit.Nomad))
    assertEquals(PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty), Vector.empty)
    val act = Table.from(ready).turn(actor, Phase.Act).unchecked
    val rested = accepted(
      rules.startWalker(Ready(act), PhaseTransitionRef.BeginRest, actor))
    val restedReady = Outcomes.ready(rested.state)
    val next = restedReady.game.current.turn.activePlayer
    assertNotEquals(next, actor)
    walkerParked.assertResumed(rested.state, Phase.Wake, next)

  /** The placement options of `played` from hand, with Silver Tongue's power
    * in force. */
  private def placementOptions(ready: ReadyGame, played: DenizenId)
      : Vector[DecisionOption] =
    val tree = accepted(CardPlayProcedure.build(catalog, ready, actor, played,
      CardPlayProcedure.Origin.TemporaryHand))
    val powers = WalkerPowers(Vector(SilverTongue.forCatalog(catalog)))
    val parked = as[WalkerOutcome.Parked](accepted(
      ProcedureWalker.advance(ready, tree, None, powers))).tree
    as[DecisionQuery.ChooseOne](present(
      ProcedureWalker.parkedDecide(ready, tree, parked, powers),
      "the placement decision").query).options

  /** The faceup plays among `options`, each as the card it replaces. */
  private def faceupReplacing(options: Vector[DecisionOption])
      : Vector[Option[CardId]] =
    options.filter(option => CardPlayProcedure.playedAs(option.ref)
      .contains(PlayedAs.Faceup)).map(PlacementPicks.replaces)

  test("Silver Tongue requires replacement when a third adviser is played"):
    // Two unrestricted denizens: the facedown second adviser and the card
    // played from hand.
    val second = CatalogNames.denizen("Threatening Roar")
    val played = CatalogNames.denizen("Fae Merchant")
    val ready = Table.from(silverTongueRest(Vector.empty, Set.empty))
      .turn(actor, Phase.Act).adviser(actor, second, facedown = true)
      .hand(actor, played).unchecked
    val faceup = faceupReplacing(placementOptions(ready, played))
    assert(faceup.nonEmpty && faceup.forall(_.nonEmpty), faceup.toString)
    assert(faceup.contains(Some(second)))

  test("playing Silver Tongue itself as a third adviser requires replacement"):
    // Two facedown advisers, and Silver Tongue in hand.
    val others = Vector("Threatening Roar", "Fae Merchant")
      .map(CatalogNames.denizen(_))
    val resting = Table.from(silverTongueRest(Vector.empty, Set.empty))
      .turn(actor, Phase.Act).hand(actor, tongue)
    val ready = others.foldLeft(resting)(_.adviser(actor, _, facedown = true))
      .unchecked
    val options = placementOptions(ready, tongue)
    assert(faceupReplacing(options).forall(_.nonEmpty))
    assert(faceupReplacing(options).contains(Some(others.head)))
    // Facedown it needs no room under its own limit.
    assert(options.exists(_.ref ==
      DecisionOptionRef.AdviserPlay(actor, 2, faceup = false)))

  test("its take is written as its own line, covering the generic one"):
    val ready = silverTongueRest(Vector("Golem Legions", "Rain Boots"),
      Set(Suit.Arcane))
    val used = accepted(
      rules.startWalker(Ready(ready), use, actor, Vector.empty, Vector(source)))
    assertEquals(NoteText.said(SilverTongue.forCatalog(catalog), used.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from the Arcane bank.", covers = true)))
