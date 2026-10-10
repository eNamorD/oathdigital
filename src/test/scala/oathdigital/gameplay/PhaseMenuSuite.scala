package oathdigital.gameplay

import oathdigital.gameplay.phases.{PhaseMenu, PhasePowerProcedure}
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.Insomnia
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureDequeued, WalkerCompleted}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, PowerRules, Refusals,
  Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** A phase's menu (walker-driven game loop design, "The menus"), parked as
  * the driver leaves it. */
class PhaseMenuSuite extends munit.FunSuite:
  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val phasePowers = PhasePowerCatalog.default(catalog)
  private val rules = PowerRules.withWalkerAndPhasePowers
  private val ancientCity = CatalogNames.site("Ancient City")
  private val insomniaCard = CatalogNames.denizen("Insomnia")
  private val insomnia = DecisionOptionRef.Denizen(insomniaCard)
  private val favor = PhaseMenu.takeWealth(WakeResource.Favor)
  private val secret = PhaseMenu.takeWealth(WakeResource.Secret)

  /** p1 at Ancient City, p2 at Broken Peaks and p3 at Buried Giant. */
  private def table: Table = Table.start
    .pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
    .pawn(p3, at = "Buried Giant")

  /** p1's Wake, with one favor and no secret at Ancient City. */
  private def waking: Table = table.turn(p1, Phase.Wake)
    .siteTokens(ancientCity, favor = 1, secrets = 0)

  /** p1's Rest, with Insomnia as an adviser. */
  private def resting: Table = table.adviser(p1, "Insomnia")
    .turn(p1, Phase.Rest)

  /** `at` with `used` spent this turn. */
  private def spent(at: Table, used: PowerUseRef): Table =
    at.update(_.updateCurrent(current => current.copy(
      turn = current.turn.copy(usedPowers = Set(used)))))

  private def options(state: OathState): Vector[DecisionOption] =
    MenuPlay.options(state, catalog, walkerPowers, phasePowers)

  private def replayed(from: OathState, events: Vector[OathEvent])
      : OathState =
    accepted(events.foldLeft[Either[OathViolation, OathState]](Right(from))(
      (state, event) => state.flatMap(rules.evolve(_, event))))

  test("the Wake menu lists Take Wealth, the site's power, then End Wake, " +
      "each with why it cannot start"):
    // Ancient City's River moves a pawn only to another River, and none is
    // in play, so the site is listed as a source with nothing to do.
    assertEquals(options(waking.phaseMenuParked.state), Vector[DecisionOption](
      DecisionOption.Button(favor, "Take Wealth: 1 favor"),
      DecisionOption.Unavailable(
        DecisionOption.Button(secret, "Take Wealth: 1 secret"),
        OptionReason.Written("No secret at your site")),
      DecisionOption.Unavailable(
        DecisionOption.Site(DecisionOptionRef.Site(ancientCity)),
        OptionReason.Written("Nothing to do now")),
      DecisionOption.Button(PhaseMenu.Menu.Wake.endingOption, "End Wake")))

  test("a pick queues its procedure, and the driver starts it in the same " +
      "command"):
    val start = waking.phaseMenuParked.state
    val taken = accepted(MenuPlay.picked(rules, start, p1, favor))
    val entry = QueuedProcedure(p1, ActionRef.TakeWealth,
      TakeWealthProcedure.selection(WakeResource.Favor), None, QueueKind.Run)
    val completed = taken.events.indexOf(
      WalkerCompleted(TriggeredProcedureRef.WakeMenu))
    assert(completed >= 0, taken.events)
    assertEquals(taken.events.lift(completed + 1),
      Some(ProcedureDequeued(entry): OathEvent))
    assert(taken.events.contains(WalkerCompleted(ActionRef.TakeWealth)),
      taken.events)
    assertEquals(replayed(start, taken.events), taken.state)

  test("an option shown unavailable is refused"):
    assertEquals(MenuPlay.picked(rules, waking.phaseMenuParked.state, p1,
      secret).left.toOption, Some(Refusals.notOffered(PhaseMenu.decisionId)))

  test("Take Wealth already used at this site says so"):
    val used = spent(waking, TakeWealthLimit.useRef(ancientCity))
    assertEquals(options(used.phaseMenuParked.state).head,
      DecisionOption.Unavailable(
        DecisionOption.Button(favor, "Take Wealth: 1 favor"),
        OptionReason.Written("Already taken from this site this turn")))

  test("an enemy pawn at the site says so"):
    val shared = waking.pawn(p2, at = ancientCity)
    assertEquals(options(shared.phaseMenuParked.state).head,
      DecisionOption.Unavailable(
        DecisionOption.Button(favor, "Take Wealth: 1 favor"),
        OptionReason.Written("Another player's pawn is at your site")))

  test("the Rest menu lists each REST power by its source, then Finish " +
      "Rest"):
    val listed = options(resting.phaseMenuParked.state)
    assertEquals(listed.map(_.ref), Vector[DecisionOptionRef](insomnia,
      PhaseMenu.Menu.Rest.endingOption))
    assert(listed.forall(_.available), listed)

  test("a REST power used this turn says so"):
    val used = spent(resting,
      PhasePowerProcedure.useRef(Insomnia, PowerSourceRef.Card(insomniaCard)))
    assertEquals(options(used.phaseMenuParked.state).head,
      DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(insomniaCard)),
        OptionReason.Written("Already used this turn")))

  test("picking a REST power uses it from that source"):
    val start = resting.phaseMenuParked.state
    val used = accepted(MenuPlay.picked(rules, start, p1, insomnia))
    assert(used.events.contains(
      WalkerCompleted(ActionRef.UsePower(Insomnia.id))), used.events)
    assertEquals(replayed(start, used.events), used.state)

  test("only the active player answers the menu"):
    assertEquals(MenuPlay.picked(rules, waking.phaseMenuParked.state, p2,
      PhaseMenu.Menu.Wake.endingOption),
      Left(OathViolation.WrongPlayer(p1, p2)))
