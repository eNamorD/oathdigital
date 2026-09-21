package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class DowsingSticksSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val sticks = RelicId("R09")
  private val source = DecisionOptionRef.Relic(sticks)
  private def staged(secrets: Int = 3) = act(withBoard(withRelic(base, sticks))(
    _.copy(faceUpSecrets = secrets)))

  test("Dowsing Sticks is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(DowsingSticks.id).isDefined)
  }

  test("it places 1 secret, burns 2 and takes the top relic facedown") {
    val ready0 = staged()
    val top = ready0.game.current.commonCards.relicDeck.head
    val rules0 = rules()
    val done = use(rules0, ready0, DowsingSticks.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).relics.find(_.id == top).map(_.orientation),
      Some(Orientation.FaceDown))
    assert(!end.game.current.commonCards.relicDeck.contains(top))
    assertEquals(tokensOn(end, sticks), Tokens(0, 1))
    assertEquals(player(end).board.faceUpSecrets, 0)
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))
  }

  test("an empty relic deck pays the cost and draws nothing") {
    val ready0 = staged()
    val current = ready0.game.current
    val emptied = ready0.updateCurrent(_.copy(commonCards =
      current.commonCards.copy(relicDeck = Vector.empty)))
      .updateCampaign(c => c.copy(reliquary = c.reliquary ++
        current.commonCards.relicDeck))
    val done = use(rules(), emptied, DowsingSticks.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).relics.map(_.id), Vector(sticks))
    assertEquals(tokensOn(end, sticks), Tokens(0, 1))
    assertEquals(player(end).board.faceUpSecrets, 0)
  }

  test("it is unusable with fewer than 3 faceup secrets") {
    assert(!usableIds(staged(secrets = 2)).contains(DowsingSticks.id))
    assert(use(rules(), staged(secrets = 2), DowsingSticks.id, source).isLeft)
  }

  test("it is unusable while a secret already rests on the relic") {
    val done = use(rules(), staged(secrets = 6), DowsingSticks.id, source)
      .toOption.get
    assert(!usableIds(ready(done.state)).contains(DowsingSticks.id))
  }

  test("a facedown Dowsing Sticks cannot be used") {
    val facedown = act(withBoard(withRelic(base, sticks, Orientation.FaceDown))(
      _.copy(faceUpSecrets = 3)))
    assert(!usableIds(facedown).contains(DowsingSticks.id))
  }
}
