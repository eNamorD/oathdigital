package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class BoneDiceSuite extends munit.FunSuite {
  import PaidActionHarness._
  import PowerFixture._

  private val bones = RelicId("R24")
  private val source = DecisionOptionRef.Relic(bones)
  private def staged(supply: Int = 2, orientation: Orientation = Orientation.FaceUp) =
    act(withBoard(withRelic(base, bones, orientation))(
      _.copy(supply = SupplyTrack(supply))))
  private def held(state: ReadyGame) = player(state).relics.map(_.id)

  test("Bone Dice is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(BoneDice.id).isDefined)
  }

  test("no skull: the relic stays, holding the secret, and Supply rises by the swords") {
    val rules0 = rules(attackDice(AttackDieFace.OneSword, AttackDieFace.HollowSword))
    val ready0 = staged()
    val done = use(rules0, ready0, BoneDice.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.supply, SupplyTrack(3))
    assert(held(end).contains(bones))
    assertEquals(tokensOn(end, bones), Tokens(0, 1))
    assertEquals(done.continue, OathContinue.ActActionSelection(actor))
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))
  }

  test("two hollow swords score one sword") {
    val rules0 = rules(attackDice(AttackDieFace.HollowSword, AttackDieFace.HollowSword))
    val end = ready(use(rules0, staged(), BoneDice.id, source).toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(3))
  }

  test("a skull counts two swords, and buries the relic with the secret returned facedown") {
    val rules0 = rules(attackDice(AttackDieFace.OneSword, AttackDieFace.TwoSwordsSkull))
    val ready0 = staged()
    val done = use(rules0, ready0, BoneDice.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.supply, SupplyTrack(5))
    assert(!held(end).contains(bones))
    assertEquals(end.game.current.commonCards.relicDeck.last, bones)
    assertEquals(player(end).board.faceDownSecrets,
      player(ready0).board.faceDownSecrets + 1)
    assertEquals(player(end).board.faceUpSecrets,
      player(ready0).board.faceUpSecrets - 1)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))
  }

  test("the gain is clamped at the track maximum, and a skull still buries") {
    val rules0 = rules(attackDice(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.TwoSwordsSkull))
    val end = ready(use(rules0, staged(supply = 6), BoneDice.id, source)
      .toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(7))
    assert(!held(end).contains(bones))
  }

  test("it is unusable without a faceup secret, with a secret already on the relic, or facedown") {
    val noSecret = withBoard(staged())(_.copy(faceUpSecrets = 0))
    assert(!usableIds(noSecret).contains(BoneDice.id))
    assert(use(rules(), noSecret, BoneDice.id, source).isLeft)
    val occupied = staged().updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player != actor) p else p.copy(relics = p.relics.map(r =>
        r.copy(tokens = Tokens(0, 1)))))))
    assert(!usableIds(occupied).contains(BoneDice.id))
    val facedown = staged(orientation = Orientation.FaceDown)
    assert(!usableIds(facedown).contains(BoneDice.id))
  }
}
