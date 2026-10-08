package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class SingingMaskSuite extends munit.FunSuite:
  import PhasePowerHarness._
  import SingingMask.{amountDecisionId, cardDecisionId, directionDecisionId,
    toBanner}

  private val power = SingingMask.forCatalog(catalog)
  private val mask = CatalogNames.relic("Singing Mask")
  private val source = DecisionOptionRef.Relic(mask)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Beast denizen and a plain Hearth one. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val chooseErrandBoy: (String, DecisionAnswer) =
    cardDecisionId -> pick(DecisionOptionRef.Denizen(errandBoy))

  /** p1's Act at Ancient City holding Singing Mask, beside Errand Boy. The
    * Beast bank holds 3 favor and every other bank none. p2 holds the
    * People's Favor with `onBanner` favor on it. */
  private def staged(onBanner: Int): Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).relic(p1, mask).denizen(errandBoy, at = home)
    .banksHoldingOnly(Suit.Beast -> 3).peoplesFavor(Some(p2), onBanner)

  private def favorOnBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** Each walk in turn: Singing Mask used, then each answer p1 gave. */
  private def walksAnswering(ready: ReadyGame,
      answers: (String, DecisionAnswer)*): Vector[OathTransition] =
    answers.foldLeft(Vector(accepted(use(ready, power, source)))) {
      case (done, (decision, answered)) =>
        done :+ accepted(answer(done.last, p1, decision, answered))
    }

  test("it places a secret and offers the cards at the site whose bank or " +
      "the People's Favor holds favor"):
    val t = accepted(use(staged(onBanner = 0).denizen(landWarden, at = home)
      .ready, power, source))
    assert(awaits(t, cardDecisionId), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> errandBoy.value)))
    assertEquals(Look(after(t)).tokensOn(mask), Tokens(0, 1))

  test("with both stocked it asks the way, then up to 2, and moves favor to " +
      "the People's Favor"):
    val ready = staged(onBanner = 1).ready
    val walks = walksAnswering(ready, chooseErrandBoy,
      directionDecisionId -> pick(toBanner),
      amountDecisionId -> DecisionAnswer.ChooseAmountAnswer(2))
    // walks(n) is the walk after n answers.
    val afterChoosingCard = walks(1)
    val afterDirection = walks(2)
    assert(awaits(afterChoosingCard, directionDecisionId),
      parked.parkedDecision(afterChoosingCard.state).toString)
    assert(awaits(afterDirection, amountDecisionId),
      parked.parkedDecision(afterDirection.state).toString)
    assert(backToActing(walks.last))
    val end = after(walks.last)
    assertEquals((Look(end).bankFavor(Suit.Beast), favorOnBanner(end)), (1, 3))
    val events = walks.flatMap(_.events)
    assertEquals(NoteText.said(power, events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 2 favor from the Beast bank to the People's Favor.",
      covers = false)))
    assertEquals(replayed(ready, events), Right(walks.last.state))
    assertWireRoundTrips(events)

  test("with only the People's Favor stocked it moves favor back without " +
      "asking the way, at most what it holds"):
    val chosen = walksAnswering(staged(onBanner = 1).banksHoldingOnly().ready,
      chooseErrandBoy).last
    assert(awaits(chosen, amountDecisionId),
      parked.parkedDecision(chosen.state).toString)
    assertEquals(answer(chosen, p1, amountDecisionId,
      DecisionAnswer.ChooseAmountAnswer(2)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision $amountDecisionId amount 2 is outside 0..1")))
    val done = accepted(answer(chosen, p1, amountDecisionId,
      DecisionAnswer.ChooseAmountAnswer(1)))
    val end = after(done)
    assertEquals((Look(end).bankFavor(Suit.Beast), favorOnBanner(end)), (1, 0))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.returned",
      "Moved 1 favor from the People's Favor to the Beast bank.",
      covers = false)))

  test("moving 0 changes nothing, and the line says so"):
    val walks = walksAnswering(staged(onBanner = 0).ready, chooseErrandBoy,
      amountDecisionId -> DecisionAnswer.ChooseAmountAnswer(0))
    assert(backToActing(walks.last))
    assertEquals(Look(after(walks.last)).bankFavor(Suit.Beast), 3)
    assertEquals(NoteText.said(power, walks.last.events), Vector(
      NoteText.Said("used.none", "No favor moved.", covers = false)))

  test("with nothing to move it asks nothing, and the line says so"):
    val done = accepted(use(staged(onBanner = 0).banksHoldingOnly().ready,
      power, source))
    assert(backToActing(done))
    assertEquals(NoteText.said(power, done.events), Vector(
      NoteText.Said("used.none", "No favor moved.", covers = false)))
