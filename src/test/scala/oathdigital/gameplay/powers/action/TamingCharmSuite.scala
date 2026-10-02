package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class TamingCharmSuite extends munit.FunSuite:
  import TargetsFixture._

  private val charm = CatalogNames.denizen("Taming Charm")
  private val source = DecisionOptionRef.Denizen(charm)
  private val power = TamingCharm.forCatalog(catalog)
  private val home = Table.homeOf(p1)
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)
  private val order = SearchFixture.denizensOf(Suit.Order)
  private val ruinedBeast =
    EdificeId(catalog.edifices.find(_.suit == Suit.Beast).get.id.value)

  /** p1's Act holding Taming Charm, the Beast and Nomad banks holding
    * `beasts` and `nomads`. */
  private def staged(beasts: Int = 5, nomads: Int = 5): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, charm)
      .bankFavor(Suit.Beast, beasts).bankFavor(Suit.Nomad, nomads)

  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  /** The discard pile of the region after p1's. */
  private def pile(ready: ReadyGame): Vector[WorldCardId] =
    val current = ready.game.current
    current.commonCards.discard(
      current.map.regionOf(home).map(CardPlay.nextRegion).get)

  private def tame(ready: ReadyGame, card: DecisionOptionRef) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, TamingCharm.decisionId, pick(card)).toOption.get)

  test("it places a secret and offers the Beast and Nomad cards at the " +
      "site, not others or facedown ones"):
    val ready = staged().denizen(beast(0), at = home)
      .denizen(nomad(0), at = home).denizen(order(0), at = home)
      .denizen(beast(1), at = home, facedown = true)
      .edifice(ruinedBeast, EdificeSide.Ruined, at = home).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, TamingCharm.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(charm), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> beast(0).value,
      "denizen" -> nomad(0).value, "edifice" -> ruinedBeast.value)))

  test("a Beast card is discarded, then 2 favor come from the Beast bank"):
    val ready = staged().denizen(beast(0), at = home).ready
    val (t, done) = tame(ready, DecisionOptionRef.Denizen(beast(0)))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).denizens(home), Vector.empty[CardId])
    assertEquals(pile(end).lastOption, Some(beast(0)))
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(bank(end, Suit.Beast), 3)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} discarded ${beast(0).value} and gained " +
        "2 favor from the Beast bank.", covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("the gain runs after the discard returned its favor to the same " +
      "bank"):
    val ready = staged(beasts = 0).denizen(beast(0), at = home)
      .tokens(beast(0), favor = 1).ready
    val (_, done) = tame(ready, DecisionOptionRef.Denizen(beast(0)))
    val end = after(done)
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(bank(end, Suit.Beast), 0)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} discarded ${beast(0).value} and gained " +
        "1 favor from the Beast bank.", covers = true)))

  test("a Nomad card pays from the Nomad bank"):
    val ready = staged(nomads = 3).denizen(nomad(0), at = home).ready
    val (_, done) = tame(ready, DecisionOptionRef.Denizen(nomad(0)))
    val end = after(done)
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)
    assertEquals((bank(end, Suit.Nomad), bank(end, Suit.Beast)), (1, 5))

  test("a ruined edifice goes to the bottom of the edifice deck"):
    val ready = staged().edifice(ruinedBeast, EdificeSide.Ruined, at = home)
      .ready
    val (_, done) = tame(ready, DecisionOptionRef.Edifice(ruinedBeast))
    val end = after(done)
    assertEquals(end.game.current.commonCards.edificeDeck.lastOption,
      Some(ruinedBeast))
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)

  test("with the bank empty the card is still discarded, and the line " +
      "says only that"):
    val ready = staged(beasts = 0).denizen(beast(0), at = home).ready
    val (_, done) = tame(ready, DecisionOptionRef.Denizen(beast(0)))
    assertEquals(Look(after(done)).favor(p1), Look(ready).favor(p1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.discarded", s"${p1.value} discarded ${beast(0).value}.",
      covers = true)))

  test("with nothing to discard the cost stays paid, and the line names " +
      "the site"):
    val ready = staged().denizen(order(0), at = home).ready
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).tokensOn(charm), Tokens(0, 1))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no Beast or Nomad card.",
      covers = true)))
