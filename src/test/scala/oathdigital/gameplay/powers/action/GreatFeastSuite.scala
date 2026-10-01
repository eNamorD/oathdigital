package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class GreatFeastSuite extends munit.FunSuite:
  import TargetsFixture._

  private val feast = CatalogNames.denizen("Great Feast")
  private val source = DecisionOptionRef.Denizen(feast)
  private val power = GreatFeast.forCatalog(catalog).get
  private val home = Table.homeOf(p1)
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)

  private def staged(supply: Int = 2, favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, feast).supply(p1, supply)
      .favor(p1, favor)

  /** The discard pile of the region after p1's. */
  private def pile(ready: ReadyGame): Vector[WorldCardId] =
    val current = ready.game.current
    current.commonCards.discard(
      current.map.regionOf(home).map(CardPlay.nextRegion).get)

  private def eat(ready: ReadyGame, card: DenizenId) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, GreatFeast.decisionId,
      pick(DecisionOptionRef.Denizen(card))).toOption.get)

  test("it places a favor and offers only the Beast cards at the site"):
    val ready = staged().denizen(beast(0), at = home)
      .denizen(nomad(0), at = home).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, GreatFeast.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(feast), Tokens(1, 0))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> beast(0).value)))

  test("the card is discarded and the player gains 3 Supply"):
    val ready = staged().denizen(beast(0), at = home).ready
    val (t, done) = eat(ready, beast(0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(pile(end).lastOption, Some(beast(0)))
    assertEquals(Look(end).supply(p1), 5)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${beast(0).value} and gained 3 Supply.",
      covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("a nearly full track gains what fits"):
    val ready = staged(supply = 6).denizen(beast(0), at = home).ready
    val (_, done) = eat(ready, beast(0))
    assertEquals(Look(after(done)).supply(p1), 7)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${beast(0).value} and gained 1 Supply.",
      covers = true)))

  test("with nothing to discard the cost stays paid, and the line names " +
      "the site"):
    val t = use(staged().denizen(nomad(0), at = home).ready, power, source)
      .toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no Beast card.", covers = true)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).denizen(beast(0), at = home).ready
    assert(!usableNow(broke).exists(_.power.id == GreatFeast.id))
    assert(use(broke, power, source).isLeft)

  test("a full track gains nothing, and the line says 0 Supply"):
    val ready = staged(supply = 7).denizen(beast(0), at = home).ready
    val (_, done) = eat(ready, beast(0))
    val end = after(done)
    assertEquals(Look(end).supply(p1), 7)
    assertEquals(pile(end).lastOption, Some(beast(0)))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${beast(0).value} and gained 0 Supply.",
      covers = true)))
