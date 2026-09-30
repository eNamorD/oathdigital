package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class FaeMerchantSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val walkerParked = new ParkedDecisionAssertions(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val fae = DenizenId("180")
  private val source = DecisionOptionRef.Denizen(fae)
  private val scepter = RelicId("grand-scepter")
  private val held1 = RelicId("R08")
  private def relicRef(id: RelicId) = DecisionOptionRef.Relic(id)
  private def staged(held: Vector[RelicId] = Vector(held1), secrets: Int = 2) =
    val ready0 = held.foldLeft(atHome(base, fae))((r, id) => withRelic(r, id))
    act(withBoard(ready0)(_.copy(faceUpSecrets = secrets)))
  private def relicIds(state: ReadyGame) = player(state).relics.map(_.id)

  test("it draws a relic, then asks which relic to put on the bottom"):
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = restrictedRules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    val mid = ready(parked.state)
    assertEquals(relicIds(mid), Vector(held1, top))
    assertEquals(player(mid).relics.last.orientation, Orientation.FaceDown)
    assertEquals(tokensOn(mid, fae), Tokens(0, 1))

    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(top))
    assertEquals(end.game.current.commonCards.relicDeck.last, held1)
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)
    assert(wireRoundTrips(parked.events ++ done.events))

  test("the relic just taken may be the one put back"):
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = restrictedRules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)).toOption.get.state)
    assertEquals(relicIds(end), Vector(held1))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)

  test("the Grand Scepter is never offered, and cannot be chosen"):
    val ready0 = staged(Vector(scepter, held1))
    val rules0 = restrictedRules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    assert(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(scepter)).isLeft)
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get.state)
    assert(relicIds(end).contains(scepter))

  test("with only the Grand Scepter held, the drawn relic is offered alone " +
      "for the player to confirm"):
    val ready0 = staged(Vector(scepter))
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = restrictedRules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    assert(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(scepter)).isLeft)
    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)

  test("with no other relic the drawn one is offered alone, and confirming " +
      "puts it back"):
    val ready0 = staged(Vector.empty)
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = restrictedRules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck.last, top)

  test("a secret on the relic put back returns to its holder facedown"):
    val ready0 = staged(Vector(held1)).updateCurrent(c => c.copy(players =
      c.players.map(p => if p.player != actor then p else p.copy(relics =
        p.relics.map(r => r.copy(tokens = Tokens(0, 1)))))))
    val rules0 = restrictedRules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    val end = ready(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get.state)
    assertEquals(player(end).board.faceDownSecrets,
      player(ready0).board.faceDownSecrets + 1)

  /** `ready0` with the relic deck moved to the reliquary. */
  private def emptied(ready0: ReadyGame): ReadyGame =
    val current = ready0.game.current
    ready0.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))

  test("an empty relic deck still offers the one held relic, and confirming " +
      "puts it on the bottom"):
    val ready0 = emptied(staged(Vector(held1)))
    val rules0 = restrictedRules()
    val parked = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    val done = answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck, Vector(held1))

  test("with only the Grand Scepter held and an empty relic deck, it asks " +
      "nothing and puts nothing back"):
    val ready0 = emptied(staged(Vector(scepter)))
    assert(usableIds(ready0).contains(FaeMerchant.id))
    val rules0 = restrictedRules()
    val done = use(rules0, ready0, FaeMerchant.id, source).toOption.get
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck, Vector.empty[RelicId])
    assertEquals(tokensOn(end, fae), Tokens(0, 1))
    assertEquals(replayed(rules0, ready0, done.events), end)

  test("it is unusable without a faceup secret"):
    assert(!usableIds(staged(secrets = 0)).contains(FaeMerchant.id))

  test("it writes the relic it drew, then the relic it put back in place of the generic line"):
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val merchant = FaeMerchant
    val parked = use(restrictedRules(), ready0, FaeMerchant.id, source).toOption.get
    assertEquals(NoteText.said(merchant, parked.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} drew ${top.value} facedown.",
        covers = false)))
    val done = answer(restrictedRules(), parked.state, FaeMerchant.decisionId,
      relicRef(held1)).toOption.get
    assertEquals(NoteText.said(merchant, done.events), Vector(
      NoteText.Said("returned",
        s"${actor.value} put ${held1.value} on the bottom of the relic deck.",
        covers = true)))
