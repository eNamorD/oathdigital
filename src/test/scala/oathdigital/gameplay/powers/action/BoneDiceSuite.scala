package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class BoneDiceSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val bones = RelicId("R24")
  private val source = DecisionOptionRef.Relic(bones)
  private def staged(supply: Int = 2, orientation: Orientation = Orientation.FaceUp) =
    act(withBoard(withRelic(base, bones, orientation))(
      _.copy(supply = SupplyTrack(supply))))
  private def held(state: ReadyGame) = player(state).relics.map(_.id)

  test("no skull: the relic stays, holding the secret, and Supply rises by the swords"):
    val rules0 = rules(attackDice(AttackDieFace.OneSword, AttackDieFace.HollowSword))
    val ready0 = staged()
    val done = use(rules0, ready0, BoneDice.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.supply, SupplyTrack(3))
    assert(held(end).contains(bones))
    assertEquals(tokensOn(end, bones), Tokens(0, 1))
    parked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("two hollow swords score one sword"):
    val rules0 = rules(attackDice(AttackDieFace.HollowSword, AttackDieFace.HollowSword))
    val end = ready(use(rules0, staged(), BoneDice.id, source).toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(3))

  test("a skull counts two swords, and buries the relic with the secret returned facedown"):
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

  test("the gain is clamped at the track maximum, and a skull still buries"):
    val rules0 = rules(attackDice(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.TwoSwordsSkull))
    val end = ready(use(rules0, staged(supply = 6), BoneDice.id, source)
      .toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(7))
    assert(!held(end).contains(bones))

  test("it is unusable without a faceup secret"):
    val noSecret = withBoard(staged())(_.copy(faceUpSecrets = 0))
    assert(!usableIds(noSecret).contains(BoneDice.id))
    assert(use(rules(), noSecret, BoneDice.id, source).isLeft)

  test("it writes its roll and its gain, and no skull writes no bury"):
    val done = use(rules(attackDice(AttackDieFace.OneSword,
      AttackDieFace.HollowSword)), staged(), BoneDice.id, source).toOption.get
    assertEquals(NoteText.said(BoneDice, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 1",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 1 Supply.", covers = false)))

  test("a skull writes the bury in place of the generic line"):
    val done = use(rules(attackDice(AttackDieFace.OneSword,
      AttackDieFace.TwoSwordsSkull)), staged(), BoneDice.id, source).toOption.get
    assertEquals(NoteText.said(BoneDice, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 3",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 3 Supply.", covers = false),
      NoteText.Said("buried", "Buried after a skull.", covers = true)))
