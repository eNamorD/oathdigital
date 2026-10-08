package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Unwrap.accepted

class SpiritSnareSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val snare = CatalogNames.denizen("Spirit Snare")
  private val source = DecisionOptionRef.Denizen(snare)

  /** p1's Act at Ancient City with Spirit Snare at the site and one faceup
    * secret. The favor banks hold exactly `favor`, and every other bank
    * none.
    */
  private def staged(favor: (Suit, Int)*): ReadyGame =
    val banks = favor.toMap
    val table = Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .denizen(snare, at = "Ancient City").secrets(actor, 1)
    Suit.all.foldLeft(table)((t, suit) =>
      t.bankFavor(suit, banks.getOrElse(suit, 0))).ready
  private def bank(ready: ReadyGame, suit: Suit) = ready.banks.favor(suit)
  private def decision(ready: ReadyGame) =
    SpiritSnare.choiceDecisionId(ready, actor)

  test("one stocked bank gives a favor without asking"):
    val ready = staged(Suit.Order -> 3)
    val t = accepted(use(ready, SpiritSnare, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).tokensOn(snare), Tokens(0, 1))
    assertEquals(Look(after(t)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor + 1)
    assertEquals(bank(after(t), Suit.Order), 2)
    assertEquals(NoteText.said(SpiritSnare, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took 1 favor from the Order bank.",
      covers = true)))

  test("a bank holding one favor gives it and is left empty"):
    val ready = staged(Suit.Hearth -> 1)
    val t = accepted(use(ready, SpiritSnare, source))
    assertEquals(bank(after(t), Suit.Hearth), 0)
    assertEquals(Look(after(t)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor + 1)
    assertEquals(NoteText.said(SpiritSnare, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took 1 favor from the Hearth bank.",
      covers = true)))

  test("several stocked banks ask which, and the answer is taken from"):
    val ready = staged(Suit.Arcane -> 2, Suit.Beast -> 1)
    val t = accepted(use(ready, SpiritSnare, source))
    assert(awaits(t, decision(ready)), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor), Some(Suit.all.filter(
      Set(Suit.Arcane, Suit.Beast)).map(DecisionOptionRef.FavorBank(_))
      .map(r => r.kind -> r.wireId)))
    val done = accepted(answer(t, actor, decision(ready),
      pick(DecisionOptionRef.FavorBank(Suit.Beast))))
    assertEquals(bank(after(done), Suit.Beast), 0)
    assertEquals(bank(after(done), Suit.Arcane), 2)
    assertEquals(Look(after(done)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertEquals(NoteText.said(SpiritSnare, t.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from the Beast bank.", covers = true)))

  test("with every bank empty the cost is paid and nothing is taken"):
    val ready = staged()
    assert(usableNow(ready).exists(_.power.id == SpiritSnare.id))
    val t = accepted(use(ready, SpiritSnare, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).tokensOn(snare), Tokens(0, 1))
    assertEquals(Look(after(t)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor)
    assertEquals(NoteText.said(SpiritSnare, t.events), Vector(NoteText.Said(
      "used.empty", "Every favor bank was empty.", covers = false)))

  test("it is unusable without a faceup secret"):
    val broke = Table.from(staged(Suit.Order -> 3)).secrets(actor, 0, 1)
      .unchecked
    assert(!usableNow(broke).exists(_.power.id == SpiritSnare.id))
    assertEquals(use(broke, SpiritSnare, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))
