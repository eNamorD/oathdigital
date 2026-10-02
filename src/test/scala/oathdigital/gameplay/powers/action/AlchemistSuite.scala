package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Unwrap.accepted

class AlchemistSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val alchemist = CatalogNames.denizen("Alchemist")
  private val source = DecisionOptionRef.Denizen(alchemist)

  /** p1's Act at Ancient City with two faceup secrets and Alchemist at the
    * site. The favor banks hold exactly `favor`, and every other bank none.
    */
  private def staged(favor: (Suit, Int)*) =
    val banks = favor.toMap
    val table = Table.start.pawn(actor, "Ancient City")
      .denizen("Alchemist", at = "Ancient City").secrets(actor, 2)
    Suit.all.foldLeft(table)((t, suit) =>
      t.bankFavor(suit, banks.getOrElse(suit, 0))).ready
  private def bank(ready: ReadyGame, suit: Suit) = ready.banks.favor(suit)
  private def rows(amounts: (Suit, Int)*) = DecisionAnswer.DistributeAnswer(
    amounts.toVector.map { case (suit, n) =>
      DistributeAmount(DecisionOptionRef.FavorBank(suit), n) })

  test("the cost is one secret placed and one burnt"):
    val ready = staged(Suit.Nomad -> 9)
    val t = accepted(use(ready, Alchemist, source))
    assertEquals(Look(after(t)).tokensOn(alchemist), Tokens(0, 1))
    assertEquals(Look(after(t)).player(actor).board.faceUpSecrets, 0)

  test("one bank holding all the favor gives four from it without a decision"):
    val ready = staged(Suit.Nomad -> 9)
    val t = accepted(use(ready, Alchemist, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor + 4)
    assertEquals(bank(after(t), Suit.Nomad), 5)

  test("four or fewer in all takes everything from every bank, unasked"):
    val ready = staged(Suit.Arcane -> 2, Suit.Discord -> 1)
    val t = accepted(use(ready, Alchemist, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor + 3)
    assertEquals(bank(after(t), Suit.Arcane), 0)
    assertEquals(bank(after(t), Suit.Discord), 0)

  test("empty banks give nothing, and the cost stays paid"):
    val ready = staged()
    val t = accepted(use(ready, Alchemist, source))
    assertEquals(Look(after(t)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor)
    assertEquals(Look(after(t)).tokensOn(alchemist), Tokens(0, 1))

  test("several banks holding more than four ask for a distribution of four"):
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3, Suit.Nomad -> 2)
    val t = accepted(use(ready, Alchemist, source))
    assert(awaits(t, Alchemist.decisionId),
      parked.parkedDecision(t.state).toString)
    val query = queryOf(t, actor) match
      case Some(distribute: DecisionQueryProjection.Distribute) => distribute
      case other => fail(s"expected a distribution, got $other")
    assertEquals(query.slots.map(s => (s.option.id, s.minimum, s.maximum)),
      Suit.all.filter(Set(Suit.Arcane, Suit.Discord, Suit.Nomad))
        .map(suit => (suit.key, 0, math.min(ready.banks.favor(suit), 4))))
    assertEquals((query.minTotal, query.maxTotal), (4, 4))
    val done = accepted(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 3, Suit.Discord -> 1, Suit.Nomad -> 0)))
    assertEquals(Look(after(done)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor + 4)
    assertEquals(Vector(Suit.Arcane, Suit.Discord, Suit.Nomad)
      .map(bank(after(done), _)), Vector(0, 2, 2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))

  test("a distribution must total four and respect each bank"):
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3, Suit.Nomad -> 2)
    val t = accepted(use(ready, Alchemist, source))
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 3, Suit.Discord -> 0, Suit.Nomad -> 0)).isLeft)
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 1, Suit.Discord -> 0, Suit.Nomad -> 3)).isLeft)
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 4, Suit.Discord -> 0, Suit.Nomad -> 0)).isLeft)

  test("only the acting player answers"):
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3)
    val t = accepted(use(ready, Alchemist, source))
    assert(answer(t, others(ready).head, Alchemist.decisionId,
      rows(Suit.Arcane -> 2, Suit.Discord -> 2)).isLeft)

  test("the cost needs two faceup secrets, and facedown ones do not count"):
    Vector((1, 0), (1, 5), (0, 5)).foreach { case (up, down) =>
      val ready = Table.from(staged(Suit.Nomad -> 9)).secrets(actor, up, down)
        .unchecked
      assertEquals(usableNow(ready), Vector.empty, s"$up up, $down down")
      assert(use(ready, Alchemist, source).isLeft)
    }

  test("it writes the favor it gained, beside each bank's own line"):
    val t = accepted(use(staged(Suit.Nomad -> 9), Alchemist, source))
    assertEquals(NoteText.said(Alchemist, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 4 favor.", covers = false)))

  test("a distribution writes its total once the player answers"):
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3, Suit.Nomad -> 2)
    val t = accepted(use(ready, Alchemist, source))
    assertEquals(NoteText.said(Alchemist, t.events), Vector.empty)
    val done = accepted(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 3, Suit.Discord -> 1, Suit.Nomad -> 0)))
    assertEquals(NoteText.said(Alchemist, done.events).map(_.text),
      Vector(s"${actor.value} gained 4 favor."))

  test("empty banks give nothing, so nothing is written"):
    assertEquals(NoteText.said(Alchemist,
      accepted(use(staged(), Alchemist, source)).events), Vector.empty)
