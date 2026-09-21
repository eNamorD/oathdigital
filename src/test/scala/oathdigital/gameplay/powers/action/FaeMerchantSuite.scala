package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class FaeMerchantSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val fae = DenizenId("180")
  private val source = DecisionOptionRef.Denizen(fae)
  private val scepter = RelicId("grand-scepter")
  private val held1 = RelicId("R08")
  private val held2 = RelicId("R01")
  private def relicRef(id: RelicId) = DecisionOptionRef.Relic(id)
  private def staged(held: Vector[RelicId] = Vector(held1), secrets: Int = 2) = {
    val ready0 = held.foldLeft(atHome(base, fae))((r, id) => withRelic(r, id))
    act(withBoard(ready0)(_.copy(faceUpSecrets = secrets)))
  }
  private def relicIds(state: ReadyGame) = player(state).relics.map(_.id)

  test("Fae Merchant is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(FaeMerchant.id).isDefined)
  }

  test("it draws a relic, then asks which relic to put on the bottom") {
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingPowerDecision(actor,
      DecisionId(FaeMerchant.decisionId)))
    val mid = ready(parked.state)
    assertEquals(relicIds(mid), Vector(held1, top))
    assertEquals(player(mid).relics.last.orientation, Orientation.FaceDown)
    assertEquals(tokensOn(mid, fae), Tokens(0, 1))

    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(top))
    assertEquals(end.game.current.commonCards.relicDeck.last, held1)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)
    assert(wireRoundTrips(parked.events ++ done.events))
  }

  test("the relic just taken may be the one put back") {
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)).toOption.get.state)
    assertEquals(relicIds(end), Vector(held1))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
  }

  test("the Grand Scepter is never offered, and cannot be chosen") {
    val ready0 = staged(Vector(scepter, held1))
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    assert(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(scepter)).isLeft)
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get.state)
    assert(relicIds(end).contains(scepter))
  }

  test("with only the Grand Scepter held, the drawn relic is the one relic eligible and goes straight back") {
    val ready0 = staged(Vector(scepter))
    val top = ready0.game.current.commonCards.relicDeck.head
    val done = use(rules(), ready0, FaeMerchant.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
  }

  test("with no other relic the drawn one is the only candidate and no decision is asked") {
    val ready0 = staged(Vector.empty)
    val top = ready0.game.current.commonCards.relicDeck.head
    val done = use(rules(), ready0, FaeMerchant.id, source).toOption.get
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
  }

  test("a secret on the relic put back returns to its holder facedown") {
    val ready0 = staged(Vector(held1)).updateCurrent(c => c.copy(players =
      c.players.map(p => if (p.player != actor) p else p.copy(relics =
        p.relics.map(r => r.copy(tokens = Tokens(0, 1)))))))
    val rules0 = rules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get.state)
    assertEquals(player(end).board.faceDownSecrets,
      player(ready0).board.faceDownSecrets + 1)
  }

  test("an empty relic deck still puts one held relic on the bottom") {
    val ready0 = staged(Vector(held1))
    val current = ready0.game.current
    val emptied = ready0.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))
    val done = use(rules(), emptied, FaeMerchant.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck, Vector(held1))
  }

  test("it is unusable without a faceup secret or with a secret already on the card") {
    assert(!usableIds(staged(secrets = 0)).contains(FaeMerchant.id))
    val rules0 = rules()
    val parked = use(rules0, staged(Vector(held1, held2), secrets = 3),
      FaeMerchant.id, source).toOption.get
    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    assert(!usableIds(ready(done.state)).contains(FaeMerchant.id))
  }
}
