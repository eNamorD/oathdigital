package oathdigital.gameplay

import oathdigital.gameplay.phases.{ActOffers, PhaseMenu, PhasePowerProcedure}
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.banner.{WanderingFlameMove,
  WanderingFlamePlace}
import oathdigital.gameplay.powers.rest.Insomnia
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  Transform}
import oathdigital.gameplay.walker.{ProcedureDequeued, WalkerCompleted,
  WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, Outcomes,
  PowerRules, Refusals, Table}
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

  /** p1's Act at Ancient City, with `supply` Supply. */
  private def acting(supply: Int = 7): Table = table.supply(p1, supply)

  private def act(procedure: ActionRef): DecisionOptionRef =
    PhaseMenu.optionFor(procedure, Vector.empty)
      .getOrElse(fail(s"no menu lists ${procedure.key}"))

  private def shown(state: OathState, ref: DecisionOptionRef)
      : DecisionOption = options(state).find(_.ref == ref)
    .getOrElse(fail(s"the menu does not list $ref"))

  test("the Act menu lists the actions, the minor actions, then End Act " +
      "and Rest"):
    // Any Act power's source is listed between them; this table's buttons
    // are the menu's own.
    assertEquals(options(acting().phaseMenuParked.state).map(_.ref)
      .filter(_.isInstanceOf[DecisionOptionRef.Button]),
      ActOffers.all.map(_.ref) :+ PhaseMenu.Menu.Act.endingOption)
    assertEquals(ActOffers.all.map(_.ref.wireId), Vector("search", "travel",
      "campaign", "muster", "trade.favor", "trade.secret", "forge",
      "recover", "challenge", "place-banner-resource", "negotiation",
      "play-facedown-adviser", "peek-site-relics", "reveal-owned-relic",
      "move-warbands"))

  test("an Act option carries its start's price"):
    val state = acting().phaseMenuParked.state
    shown(state, act(ActionRef.Travel)) match
      case DecisionOption.Badged(_, badge) =>
        assert(badge.matches("\\d+\\+ Supply"), badge)
      case other => fail(s"Travel is not priced: $other")

  test("an Act option that cannot start says why"):
    val parked = acting(supply = 1).phaseMenuParked
    // The preconditions: no relic at Ancient City, and none held.
    assertEquals(Look(parked.ready).relicsAt(ancientCity), Vector.empty)
    assertEquals(Look(parked.ready).relics(p1), Vector.empty)
    val state = parked.state
    assertEquals(shown(state, act(ActionRef.PeekSiteRelics)),
      DecisionOption.Unavailable(DecisionOption.Button(
        DecisionOptionRef.Button("peek-site-relics"),
        "Peek at relics at your site"),
        OptionReason.Written("No relics at your site")))
    assertEquals(shown(state, act(ActionRef.RevealOwnedRelic)),
      DecisionOption.Unavailable(DecisionOption.Button(
        DecisionOptionRef.Button("reveal-owned-relic"), "Reveal a relic"),
        OptionReason.Written("You have no facedown relic")))

  test("an Act option only a modifier can open is available"):
    // p1 has no Supply; a stand-in modifier on Wrestlers waives Search's
    // cost.
    val wrestlers = CatalogNames.denizen("Wrestlers")
    val waiver = new ContributingPower:
      val id: PowerId = PowerId("stand.waiver")
      val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        Map(PowerWindow.SearchCost -> Vector(
          Transform((_, _) => Vector.empty)))
      override def resolution: PowerResolution =
        PowerResolution.PlayerSelected
      override def selectionCard: Option[CardId] = Some(wrestlers)
      override def selectionWindows: Set[PowerWindow] =
        Set(PowerWindow.SearchModifierSelection)
    val ready = acting(supply = 0).worldDeckTop("Threatening Roar",
      "Fae Merchant", "Second Chance").adviser(p1, wrestlers).ready
    val search = PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      phasePowers, WalkerPowers.selected(WalkerPowers(Vector(waiver)),
        Vector.empty)).find(_.option.ref == act(ActionRef.Search))
    assert(search.exists(_.option.available), search)

  test("End Act and Rest names the Supply Rest returns"):
    val poor = acting(supply = 1).phaseMenuParked
    val gain = accepted(FinishRestProcedure.supplyGainAtRest(poor.ready, p1))
    assert(gain > 0, "the precondition: Rest returns some Supply")
    assertEquals(shown(poor.state, PhaseMenu.Menu.Act.endingOption),
      DecisionOption.Button(PhaseMenu.Menu.Act.endingOption,
        s"End Act and Rest (+$gain Supply)"))

  test("the Act menu always asks"):
    val ending = PhaseMenu.Offered(QueuedProcedure(p1,
      PhaseTransitionRef.BeginRest, Vector.empty, None, QueueKind.Run),
      DecisionOption.Button(PhaseMenu.Menu.Act.endingOption,
        "End Act and Rest"))
    assert(!PhaseMenu.onlyEnding(PhaseMenu.Menu.Act, Vector(ending)))

  test("an Act pick queues its action, which starts in the same command"):
    val start = acting().phaseMenuParked.state
    val picked = accepted(MenuPlay.picked(rules, start, p1,
      act(ActionRef.Travel)))
    val entry = QueuedProcedure(p1, ActionRef.Travel, Vector.empty, None,
      QueueKind.Run)
    val completed = picked.events.indexOf(
      WalkerCompleted(TriggeredProcedureRef.ActMenu))
    assert(completed >= 0, picked.events)
    assertEquals(picked.events.lift(completed + 1),
      Some(ProcedureDequeued(entry): OathEvent))
    assertEquals(Outcomes.ready(picked.state).game.current.walkerProcedure,
      Some(ActionRef.Travel))
    assertEquals(replayed(start, picked.events), picked.state)

  test("picking End Act and Rest begins the Rest"):
    val rested = accepted(MenuPlay.picked(rules,
      acting().phaseMenuParked.state, p1, PhaseMenu.Menu.Act.endingOption))
    assert(rested.events.contains(WalkerCompleted(
      PhaseTransitionRef.BeginRest)), rested.events)

  test("a source with two powers of one phase lists a button for each"):
    // p1 holds the Wandering Flame and a faceup secret; Broken Peaks holds
    // a secret.
    val flame = acting().darkestSecret(Some(p1), 1,
      face = DarkestSecretFace.WanderingFlame)
      .siteTokens("Broken Peaks", secrets = 1).secrets(p1, faceUp = 1)
    val listed = options(flame.phaseMenuParked.state)
    val moveButton = PhaseMenu.powerButton(WanderingFlameMove.id)
    val placeButton = PhaseMenu.powerButton(WanderingFlamePlace.id)
    assertEquals(moveButton.wireId,
      "use-power:banner.darkest-secret.wandering-flame.move")
    // Placing the secret spends it, so that button carries its badge.
    assertEquals(listed.collect {
      case DecisionOption.Button(ref, label) if ref == moveButton =>
        (ref, label, None)
      case DecisionOption.Badged(DecisionOption.Button(ref, label), badge)
          if ref == placeButton => (ref, label, Some(badge))
    }, Vector[(DecisionOptionRef, String, Option[String])](
      (moveButton, "Wandering Flame: move", None),
      (placeButton, "Wandering Flame: place a secret on your site",
        Some("1 [secret]"))))
    assert(!listed.exists(_.ref.isInstanceOf[DecisionOptionRef.Banner]),
      listed)
    // Picking the second button uses the second power.
    val used = accepted(MenuPlay.picked(rules, flame.phaseMenuParked.state,
      p1, placeButton))
    assert(used.events.contains(
      WalkerCompleted(ActionRef.UsePower(WanderingFlamePlace.id))),
      used.events)

  test("an option of another phase's menu is refused"):
    val refused = Some(Refusals.notOffered(PhaseMenu.decisionId))
    assertEquals(MenuPlay.picked(rules, waking.phaseMenuParked.state, p1,
      PhaseMenu.Menu.Act.endingOption).left.toOption, refused)
    assertEquals(MenuPlay.picked(rules, acting().phaseMenuParked.state, p1,
      favor).left.toOption, refused)
    assertEquals(MenuPlay.picked(rules, resting.phaseMenuParked.state, p1,
      act(ActionRef.Search)).left.toOption, refused)

