package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class FirebrandSuite extends munit.FunSuite:
  import TargetsFixture._

  private val brand = CatalogNames.denizen("Firebrand")
  private val source = DecisionOptionRef.Denizen(brand)

  /** p1's Act holding Firebrand, the banks holding exactly `favor`, and the
    * People's Favor unheld with `banner` favor. */
  private def staged(banner: Int, favor: (Suit, Int)*): Table =
    (Suit.all.map(_ -> 0) ++ favor).foldLeft(Table.start.turn(p1, Phase.Act)
      .adviser(p1, brand).peoplesFavor(None, banner)) {
      case (table, (suit, n)) => table.bankFavor(suit, n) }

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor
  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  test("it places a secret and offers each stocked bank, then the burn"):
    val t = use(staged(1, Suit.Order -> 2, Suit.Hearth -> 1).ready,
      Firebrand, source).toOption.get
    assert(awaits(t, Firebrand.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(brand), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("favor-bank" -> "order",
      "favor-bank" -> "hearth", "button" -> "burn")))

  test("a bank adds 1 favor to the People's Favor"):
    val ready = staged(1, Suit.Order -> 2, Suit.Hearth -> 1).ready
    val t = use(ready, Firebrand, source).toOption.get
    val done = answer(t, p1, Firebrand.decisionId,
      pick(DecisionOptionRef.FavorBank(Suit.Order))).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals((bank(end, Suit.Order), onBanner(end)), (1, 2))
    assertEquals(NoteText.said(Firebrand, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} moved 1 favor from the Order bank to the People's Favor.",
      covers = false)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("the burn takes 1 favor from the People's Favor"):
    val ready = staged(1, Suit.Order -> 2).ready
    val t = use(ready, Firebrand, source).toOption.get
    val done = answer(t, p1, Firebrand.decisionId, pick(Firebrand.burn))
      .toOption.get
    val end = after(done)
    assertEquals((bank(end, Suit.Order), onBanner(end)), (2, 0))
    assertEquals(NoteText.said(Firebrand, done.events), Vector(NoteText.Said(
      "used.burned", s"${p1.value} burned 1 favor from the People's Favor.",
      covers = false)))

  test("a single option runs without asking, whoever holds the banner"):
    val ready = staged(2).peoplesFavor(Some(p2), 2).ready
    val t = use(ready, Firebrand, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(onBanner(after(t)), 1)

  test("with nothing to move or burn the cost stays paid, and the line " +
      "says so"):
    val t = use(staged(0).ready, Firebrand, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).tokensOn(brand), Tokens(0, 1))
    assertEquals(NoteText.said(Firebrand, t.events), Vector(NoteText.Said(
      "used.empty", "Every favor bank and the People's Favor were empty.",
      covers = false)))
