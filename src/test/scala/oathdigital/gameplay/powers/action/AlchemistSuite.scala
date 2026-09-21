package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class AlchemistSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val alchemist = DenizenId("9")
  private val source = DecisionOptionRef.Denizen(alchemist)

  /** The banks hold exactly `favor`, and the actor holds two faceup secrets
    * beside a site Alchemist.
    */
  private def staged(favor: (Suit, Int)*) = {
    val ready = inPhase(withSecrets(atHome(base, alchemist), actor, 2, 0),
      Phase.Act)
    ready.copy(banks = ready.banks.copy(favor =
      Suit.all.map(_ -> 0).toMap ++ favor))
  }
  private def bank(ready: ReadyGame, suit: Suit) = ready.banks.favor(suit)
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == alchemist => d }.get
  private def rows(amounts: (Suit, Int)*) = DecisionAnswer.DistributeAnswer(
    amounts.toVector.map { case (suit, n) =>
      DistributeAmount(DecisionOptionRef.FavorBank(suit), n) })

  test("Alchemist is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(Alchemist.id).isDefined)
  }

  test("the cost is one secret placed and one burnt") {
    val ready = staged(Suit.Nomad -> 9)
    val t = use(ready, Alchemist, source).toOption.get
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.faceUpSecrets, 0)
  }

  test("one bank holding all the favor gives four from it without a decision") {
    val ready = staged(Suit.Nomad -> 9)
    val t = use(ready, Alchemist, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assertEquals(player(after(t)).board.favor, player(ready).board.favor + 4)
    assertEquals(bank(after(t), Suit.Nomad), 5)
  }

  test("four or fewer in all takes everything from every bank, unasked") {
    val ready = staged(Suit.Arcane -> 2, Suit.Discord -> 1)
    val t = use(ready, Alchemist, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision])
    assertEquals(player(after(t)).board.favor, player(ready).board.favor + 3)
    assertEquals(bank(after(t), Suit.Arcane), 0)
    assertEquals(bank(after(t), Suit.Discord), 0)
  }

  test("empty banks give nothing, and the cost stays paid") {
    val ready = staged()
    val t = use(ready, Alchemist, source).toOption.get
    assertEquals(player(after(t)).board.favor, player(ready).board.favor)
    assertEquals(cardOf(after(t)).tokens, Tokens(0, 1))
  }

  test("several banks holding more than four ask for a distribution of four") {
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3, Suit.Nomad -> 2)
    val t = use(ready, Alchemist, source).toOption.get
    assert(awaits(t, Alchemist.decisionId), t.continue.toString)
    val query = queryOf(t, actor).get
    assertEquals(query.form, "distribute")
    assertEquals(query.slots.map(s => (s.option.id, s.minimum, s.maximum)),
      Suit.all.filter(Set(Suit.Arcane, Suit.Discord, Suit.Nomad))
        .map(suit => (suit.key, 0, math.min(ready.banks.favor(suit), 4))))
    assertEquals((query.minTotal, query.maxTotal), (Some(4), Some(4)))
    val done = answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 3, Suit.Discord -> 1, Suit.Nomad -> 0)).toOption.get
    assertEquals(player(after(done)).board.favor, player(ready).board.favor + 4)
    assertEquals(Vector(Suit.Arcane, Suit.Discord, Suit.Nomad)
      .map(bank(after(done), _)), Vector(0, 2, 2))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
  }

  test("a distribution must total four and respect each bank") {
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3, Suit.Nomad -> 2)
    val t = use(ready, Alchemist, source).toOption.get
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 3, Suit.Discord -> 0, Suit.Nomad -> 0)).isLeft)
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 1, Suit.Discord -> 0, Suit.Nomad -> 3)).isLeft)
    assert(answer(t, actor, Alchemist.decisionId,
      rows(Suit.Arcane -> 4, Suit.Discord -> 0, Suit.Nomad -> 0)).isLeft)
  }

  test("only the acting player answers") {
    val ready = staged(Suit.Arcane -> 3, Suit.Discord -> 3)
    val t = use(ready, Alchemist, source).toOption.get
    assert(answer(t, others(ready).head, Alchemist.decisionId,
      rows(Suit.Arcane -> 2, Suit.Discord -> 2)).isLeft)
  }

  test("the cost needs two faceup secrets, and facedown ones do not count") {
    Vector((1, 0), (1, 5), (0, 5)).foreach { case (up, down) =>
      val ready = withSecrets(staged(Suit.Nomad -> 9), actor, up, down)
      assertEquals(usableNow(ready), Vector.empty, s"$up up, $down down")
      assert(use(ready, Alchemist, source).isLeft)
    }
  }
}
