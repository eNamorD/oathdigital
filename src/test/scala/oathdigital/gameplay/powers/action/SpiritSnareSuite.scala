package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SpiritSnareSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val snare = DenizenId("33")
  private val source = DecisionOptionRef.Denizen(snare)

  /** The banks hold exactly `favor`, and the actor holds one faceup secret
    * beside a site Spirit Snare.
    */
  private def staged(favor: (Suit, Int)*): ReadyGame =
    val ready = inPhase(withSecrets(atHome(base, snare), actor, 1, 0),
      Phase.Act)
    ready.copy(banks = ready.banks.copy(favor =
      Suit.all.map(_ -> 0).toMap ++ favor))
  private def bank(ready: ReadyGame, suit: Suit) = ready.banks.favor(suit)
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == snare => d }.get
  private def decision(ready: ReadyGame) =
    SpiritSnare.choiceDecisionId(ready, actor)

  test("Spirit Snare is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(SpiritSnare.id).isDefined)

  test("one stocked bank gives a favor without asking"):
    val ready = staged(Suit.Order -> 3)
    val t = use(ready, SpiritSnare, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.favor, player(ready).board.favor + 1)
    assertEquals(bank(after(t), Suit.Order), 2)
    assertEquals(NoteText.said(SpiritSnare, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} took 1 favor from the Order bank.",
      covers = true)))

  test("several stocked banks ask which, and the answer is taken from"):
    val ready = staged(Suit.Arcane -> 2, Suit.Beast -> 1)
    val t = use(ready, SpiritSnare, source).toOption.get
    assert(awaits(t, decision(ready)), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor), Some(Suit.all.filter(
      Set(Suit.Arcane, Suit.Beast)).map(DecisionOptionRef.FavorBank(_))
      .map(r => r.kind -> r.wireId)))
    val done = answer(t, actor, decision(ready),
      pick(DecisionOptionRef.FavorBank(Suit.Beast))).toOption.get
    assertEquals(bank(after(done), Suit.Beast), 0)
    assertEquals(bank(after(done), Suit.Arcane), 2)
    assertEquals(player(after(done)).board.favor, player(ready).board.favor + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertEquals(NoteText.said(SpiritSnare, t.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from the Beast bank.", covers = true)))

  test("with every bank empty the cost is paid and nothing is taken"):
    val ready = staged()
    assert(usableNow(ready).exists(_.power.id == SpiritSnare.id))
    val t = use(ready, SpiritSnare, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.favor, player(ready).board.favor)
    assertEquals(NoteText.said(SpiritSnare, t.events), Vector(NoteText.Said(
      "used.empty", "Every favor bank was empty.", covers = false)))

  test("it is unusable without a faceup secret"):
    val broke = withSecrets(staged(Suit.Order -> 3), actor, 0, 1)
    assert(!usableNow(broke).exists(_.power.id == SpiritSnare.id))
    assert(use(broke, SpiritSnare, source).isLeft)
