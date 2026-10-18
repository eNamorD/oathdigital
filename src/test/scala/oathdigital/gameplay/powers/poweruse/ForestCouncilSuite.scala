package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.gameplay.powers.action.RovingTerror
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.economy.VowOfBeastkin
import oathdigital.gameplay.powers.whenplayed.{ThreateningRoar,
  WhenPlayedHarness}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Forest Council (card 194): "Enemies of Forest Council's ruler cannot
  * place [favor] / [secret] on [suit-beast] cards or use their
  * non-persistent powers." (catalog batch 8, slice 10, part 2). */
class ForestCouncilSuite extends munit.FunSuite:
  private val council = CatalogNames.denizen("Forest Council")
  private val woods = CatalogNames.site("Deep Woods")
  private val home = CatalogNames.site("Ancient City")
  private val terror = CatalogNames.denizen("Roving Terror")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val errandBoyRef = DecisionOptionRef.Denizen(errandBoy)
  private val power = ForestCouncil.forCatalog(catalog)
  private val source = PowerSourceRef.Card(council)

  /** p1's Act at Ancient City with 4 favor and 7 Supply, beside Errand Boy
    * and Wrestlers, with Forest Council at Deep Woods, which `ruled` gives
    * its ruler: p2 by default. */
  private def councilAtDeepWoods(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(errandBoy, at = home).denizen(wrestlers, at = home)
      .favor(p1, 4).supply(p1, 7).denizen(council, at = woods)).ready

  private def useOf(card: DenizenId, persistent: Boolean): PowerUse =
    PowerUse(p1, PowerId("test.power"), Some(card), persistent,
      costsSecret = false)

  test("it refuses an enemy a Beast card's non-persistent power, waivable " +
      "by the ruler"):
    val refusal = power.refusal(councilAtDeepWoods(),
      useOf(terror, persistent = false))
    assertEquals(refusal.map(_.note), Some(ForestCouncil.usePlain(source)))
    assertEquals(refusal.flatMap(_.reason.waiver).flatMap(_.granter),
      Some(p2))

  test("it refuses neither a Beast card's persistent power that places " +
      "nothing, nor another suit's non-persistent power, nor its ruler"):
    val ready = councilAtDeepWoods()
    assertEquals(power.refusal(ready, useOf(terror, persistent = true)),
      None)
    assertEquals(power.refusal(ready, useOf(wrestlers, persistent = false)),
      None)
    assertEquals(power.refusal(councilAtDeepWoods(_.warbandsAt(woods, p1,
      1)), useOf(terror, persistent = false)), None)

  test("a persistent power whose cost places on a Beast card is refused " +
      "on its user's turn, with the placing reason"):
    val placing = useOf(terror, persistent = true).copy(places = true)
    assertEquals(power.refusal(councilAtDeepWoods(), placing).map(_.note),
      Some(ForestCouncil.placingPlain(source)))
    val offTurn = councilAtDeepWoods().updateCurrent(current =>
      current.copy(turn = current.turn.copy(activePlayer = p2)))
    assertEquals(power.refusal(offTurn, placing), None)

  test("it refuses a payment onto a Beast card on the payer's turn, and " +
      "not off it"):
    val ready = councilAtDeepWoods()
    val pay = PayCost(p1, Location.OnCard(errandBoy), Cost(favor = 1))
    val ban = present(power.operationRestrictions.headOption, "the ban")
    assertEquals(ban.reason(ready, pay).flatMap(_.waiver).map(_.covers),
      Some(ConsentKey(ForestCouncil.id, p1, "power.use", errandBoyRef)))
    assertEquals(ban.reason(ready, pay.copy(placedAt =
      Location.OnCard(wrestlers))), None)
    // Off p1's turn the payment settles to the bank and places nothing.
    val offTurn = ready.updateCurrent(current =>
      current.copy(turn = current.turn.copy(activePlayer = p2)))
    assertEquals(ban.reason(offTurn, pay), None)

  test("it refuses an enemy's gift of favor onto a Beast card, the giver " +
      "placing it"):
    val ban = present(power.operationRestrictions.headOption, "the ban")
    val gift = Give(Piece.Favor(1), p1, Location.PlayArea(p1),
      Location.OnCard(errandBoy))
    assertEquals(ban.reason(councilAtDeepWoods(), gift).flatMap(_.waiver)
      .map(_.covers), Some(ConsentKey(ForestCouncil.id, p1, "power.use",
      errandBoyRef)))

  test("a Muster from a Beast card is askable of the ruler, beside a card " +
      "of another suit"):
    val options = musterSources(councilAtDeepWoods())
    assertEquals(options.find(_.ref == errandBoyRef).map(_.askable),
      Some(true))
    assert(options.exists(option =>
      option.ref == DecisionOptionRef.Denizen(wrestlers) && option.available))

  /** `ready` remembering p2's grant of Errand Boy to p1 this turn. */
  private def grantedByP2(ready: ReadyGame): ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(
      ConsentKey(ForestCouncil.id, p1, "power.use", errandBoyRef) ->
        ConsentAnswer.Granted.by(p2)), turn = Some(ConsentTurn.of(ready))))

  test("a grant remembered this turn lets the Muster place its favor on " +
      "the Beast card"):
    val granted = grantedByP2(councilAtDeepWoods())
    assertEquals(Look(musteredFrom(granted, errandBoyRef)).tokensOn(errandBoy),
      Tokens(1, 0))

  test("a grant stops counting once its granter no longer rules: the new " +
      "ruler is asked, and under bandits the plain reason shows"):
    val p3 = Table.p3
    val p3Rules = grantedByP2(councilAtDeepWoods(_.warbandsAt(woods, p3, 1)))
    musterSources(p3Rules).find(_.ref == errandBoyRef) match
      case Some(DecisionOption.Unavailable(_, ask: OptionReason.Askable)) =>
        assertEquals(ask.asks.map(_.granter), Vector(p3))
      case other => fail(s"expected Errand Boy askable of p3, got $other")
    val banditsRule = grantedByP2(councilAtDeepWoods(_.bandits(woods, 2)))
    assertEquals(musterSources(banditsRule).find(_.ref == errandBoyRef)
      .map(_.askable), Some(false))
    assertEquals(power.refusal(banditsRule, useOf(terror, persistent = false))
      .map(_.power), Some(ForestCouncil.id))

  test("under bandits the Beast source shows the plain reason, as nobody " +
      "may grant it"):
    val options = musterSources(councilAtDeepWoods(_.bandits(woods, 2)))
    assertEquals(options.find(_.ref == errandBoyRef), Some(
      DecisionOption.Unavailable(DecisionOption.Denizen(errandBoyRef),
        OptionReason.Noted(ForestCouncil.id,
          ForestCouncil.placingPlain(source)))))
    assert(options.exists(_.ref == DecisionOptionRef.Denizen(wrestlers)))

  test("the phase menu asks the ruler about an enemy's Beast ACTION, whose " +
      "own cost places on it"):
    // Roving Terror's secret is paid onto it: the placement ban and the
    // use ban are one ask.
    val ready = Table.from(councilAtDeepWoods()).denizen(terror, at = home)
      .denizen(CatalogNames.denizen("Messenger"),
        at = CatalogNames.site("Broken Peaks")).ready
    MenuPlay.menuOptionFor(catalog, ready, ActionRef.UsePower(
        RovingTerror.forCatalog(catalog).id),
        Vector(DecisionOptionRef.Denizen(terror))) match
      case DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
        assertEquals(ask.power, ForestCouncil.id)
      case other => fail(s"expected an askable option, got $other")

  test("an enemy's Beast When Played power does not fire under a bandit " +
      "Forest Council"):
    // Threatening Roar, played as p1's adviser, would discard Errand Boy,
    // a Beast card at p1's site. Forest Council stands outside p1's
    // region, where the Roar does not reach.
    import WhenPlayedHarness.{awaySite, homeSite, p1AtHomeSite}
    val roar = WhenPlayedHarness.registered[ThreateningRoar]
    def errandBoyStays(ruled: Table => Table): Boolean =
      val ready = ruled(p1AtHomeSite.adviser(p1, roar.cardId)
        .denizen(errandBoy, at = homeSite).denizen(council, at = awaySite))
        .ready
      val done = Outcomes.finished(WhenPlayedHarness.playBeside(ready, roar,
        roar.cardId, power))
      Look(done.treeless).cardsAt(homeSite).contains(errandBoy)
    assert(errandBoyStays(_.bandits(awaySite, 1)))
    // Its ruler's own Roar fires.
    assert(!errandBoyStays(_.warbandsAt(awaySite, p1, 1)))

  test("an enemy's Vow of Beastkin adds no warband under a bandit Forest " +
      "Council, and its source limit still holds"):
    // The Vow's extra warband is a use, and Forest Council refuses it. Its
    // source limit is a restriction, not a use, so it holds: Tyrant, an
    // Order adviser, makes Wrestlers (Order) a legal source. Wrestlers is
    // not a Beast card, so Forest Council lets p1 place favor on it.
    val vow = CatalogNames.denizen("Vow of Beastkin")
    val tyrant = CatalogNames.denizen("Tyrant")
    def board(withCouncil: Boolean): ReadyGame =
      val base = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
        .denizen(wrestlers, at = home).adviser(p1, vow).adviser(p1, tyrant)
        .favor(p1, 4).supply(p1, 7).boardWarbands(p1, 3)
      (if withCouncil then base.denizen(council, at = woods)
        .bandits(woods, 1) else base).ready
    def mustered(ready: ReadyGame): Int =
      Look(musteredFrom(ready, DecisionOptionRef.Denizen(wrestlers)))
        .boardWarbands(p1)
    assertEquals(mustered(board(withCouncil = true)),
      mustered(board(withCouncil = false)) - VowOfBeastkin.Warbands)

  /** `ready` with p2's `answer` on p1's use of `card` remembered this
    * turn, against Forest Council. */
  private def rememberingP2sAnswerOn(ready: ReadyGame, card: DenizenId,
      answer: ConsentAnswer): ReadyGame = ready.copy(consent = ConsentState(
    answers = Map(ConsentKey(ForestCouncil.id, p1, PowerUseRules.useKey,
      DecisionOptionRef.Denizen(card)) -> answer.by(p2)),
    turn = Some(ConsentTurn.of(ready))))

  test("an enemy's Beast When Played power waits on Forest Council's " +
      "ruler, whose remembered answer decides it"):
    import WhenPlayedHarness.{awaySite, homeSite, p1AtHomeSite}
    val roar = WhenPlayedHarness.registered[ThreateningRoar]
    val councilRuledByP2 = p1AtHomeSite.adviser(p1, roar.cardId)
      .denizen(errandBoy, at = homeSite).denizen(council, at = awaySite)
      .warbandsAt(awaySite, p2, 1).ready
    def played(ready: ReadyGame) =
      accepted(WhenPlayedHarness.playBeside(ready, roar, roar.cardId, power))
    played(councilRuledByP2) match
      case WalkerOutcome.Parked(pending, _) =>
        assertEquals(pending.answered, Vector.empty)
        assertEquals(pending.at.lastOption, Some("0"))
      case other => fail(s"the walk did not park at the step: $other")
    def errandBoyStays(answer: ConsentAnswer): Boolean =
      played(rememberingP2sAnswerOn(councilRuledByP2, roar.cardId,
        answer)) match
        case WalkerOutcome.Finished(end, _) =>
          Look(end).cardsAt(homeSite).contains(errandBoy)
        case other => fail(s"the walk did not finish: $other")
    assert(errandBoyStays(ConsentAnswer.Refused))
    assert(!errandBoyStays(ConsentAnswer.Granted))

  test("an enemy's Vow of Beastkin adds its warband once Forest Council's " +
      "ruler lets it work"):
    val vow = CatalogNames.denizen("Vow of Beastkin")
    val tyrant = CatalogNames.denizen("Tyrant")
    val musterer = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(wrestlers, at = home).adviser(p1, vow).adviser(p1, tyrant)
      .favor(p1, 4).supply(p1, 7).boardWarbands(p1, 3)
    val councilRuledByP2 = musterer.denizen(council, at = woods)
      .warbandsAt(woods, p2, 1).ready
    def mustered(ready: ReadyGame): Int =
      Look(musteredFrom(ready, DecisionOptionRef.Denizen(wrestlers)))
        .boardWarbands(p1)
    val free = mustered(musterer.ready)
    assertEquals(mustered(rememberingP2sAnswerOn(councilRuledByP2, vow,
      ConsentAnswer.Granted)), free)
    assertEquals(mustered(rememberingP2sAnswerOn(councilRuledByP2, vow,
      ConsentAnswer.Refused)), free - VowOfBeastkin.Warbands)

  /** Every production walker power, as a command with no modifiers picked
    * gets them. */
  private val everyPower: WalkerPowers =
    WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty)

  /** The Muster tree, walked with every production power to its source
    * decision. */
  private final case class ParkedMuster(tree: Operation,
      pending: PendingTree)

  private def parkedMuster(ready: ReadyGame): ParkedMuster =
    val tree = accepted(MusterProcedure.build(catalog, ready, p1))
    ParkedMuster(tree, Outcomes.parked(ProcedureWalker.advance(ready, tree,
      None, everyPower)).tree)

  /** The game after p1 musters from `ref`. */
  private def musteredFrom(ready: ReadyGame, ref: DecisionOptionRef)
      : ReadyGame =
    val walk = parkedMuster(ready)
    Outcomes.finished(ProcedureWalker.resolve(ready, walk.tree,
      walk.pending, Answered(MusterProcedure.decisionId,
        DecisionAnswer.ChooseOneAnswer(ref), p1), everyPower)).treeless

  /** The Muster source decision's options, walked with every production
    * walker power. */
  private def musterSources(ready: ReadyGame)(using munit.Location)
      : Vector[DecisionOption] =
    val walk = parkedMuster(ready)
    present(ProcedureWalker.parkedDecide(ready, walk.tree, walk.pending,
      everyPower), "the source decision").query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")
