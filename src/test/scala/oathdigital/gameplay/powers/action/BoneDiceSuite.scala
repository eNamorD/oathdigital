package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class BoneDiceSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val bones = CatalogNames.relic("Bone Dice")
  private val source = DecisionOptionRef.Relic(bones)
  /** p1's Act holding Bone Dice, with `supply` Supply and the quiet table's
    * one faceup secret. */
  private def staged(supply: Int) =
    Table.start.turn(actor, Phase.Act)
      .relic(actor, bones).supply(actor, supply).ready
  private def held(state: ReadyGame) = Look(state).player(actor)
    .relics.map(_.id)

  test("no skull: the relic stays, holding the secret, and Supply rises by the swords"):
    val rules0 = rules(attackDice(AttackDieFace.OneSword, AttackDieFace.HollowSword))
    // 2 Supply and 1 sword make 3.
    val ready0 = staged(supply = 2)
    val done = accepted(use(rules0, ready0, BoneDice.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(3))
    assert(held(end).contains(bones))
    assertEquals(Look(end).tokensOn(bones), Tokens(0, 1))
    parked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("two hollow swords score one sword"):
    val rules0 = rules(attackDice(AttackDieFace.HollowSword, AttackDieFace.HollowSword))
    // 2 Supply and 1 sword make 3.
    val end = ready(accepted(use(rules0, staged(supply = 2), BoneDice.id,
      source)).state)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(3))

  test("a skull counts two swords, and buries the relic with the secret returned facedown"):
    val rules0 = rules(attackDice(AttackDieFace.OneSword, AttackDieFace.TwoSwordsSkull))
    // 2 Supply and 1 + 2 swords make 5.
    val ready0 = staged(supply = 2)
    val done = accepted(use(rules0, ready0, BoneDice.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(5))
    assert(!held(end).contains(bones))
    assertEquals(end.game.current.commonCards.relicDeck.last, bones)
    assertEquals(Look(end).player(actor).board.faceDownSecrets,
      Look(ready0).player(actor).board.faceDownSecrets + 1)
    assertEquals(Look(end).player(actor).board.faceUpSecrets,
      Look(ready0).player(actor).board.faceUpSecrets - 1)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("the gain is clamped at the track maximum, and a skull still buries"):
    val rules0 = rules(attackDice(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.TwoSwordsSkull))
    val end = ready(accepted(use(rules0, staged(supply = 6), BoneDice.id,
      source)).state)
    assertEquals(Look(end).player(actor).board.supply, SupplyTrack(7))
    assert(!held(end).contains(bones))

  test("it is unusable without a faceup secret"):
    val noSecret = Table.from(staged(supply = 2)).secrets(actor, 0).unchecked
    assert(!usableIds(noSecret).contains(BoneDice.id))
    assert(use(rules(), noSecret, BoneDice.id, source).isLeft)

  test("it writes its roll and its gain, and no skull writes no bury"):
    val done = accepted(use(rules(attackDice(AttackDieFace.OneSword,
      AttackDieFace.HollowSword)), staged(supply = 2), BoneDice.id, source))
    assertEquals(NoteText.said(BoneDice, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 1",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 1 Supply.", covers = false)))

  test("a skull writes the bury in place of the generic line"):
    val done = accepted(use(rules(attackDice(AttackDieFace.OneSword,
      AttackDieFace.TwoSwordsSkull)), staged(supply = 2), BoneDice.id, source))
    assertEquals(NoteText.said(BoneDice, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 3",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 3 Supply.", covers = false),
      NoteText.Said("buried", "Buried after a skull.", covers = true)))
