package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class DarkEnforcerSuite extends munit.FunSuite:
  import TargetsFixture._

  private val enforcer = CatalogNames.denizen("Dark Enforcer")
  private val source = DecisionOptionRef.Denizen(enforcer)
  private val power = DarkEnforcer.forCatalog(catalog).get
  private val home = Table.homeOf(p1)
  private val order = SearchFixture.denizensOf(Suit.Order)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private def edificeOf(suit: Suit): EdificeId =
    EdificeId(catalog.edifices.find(_.suit == suit).get.id.value)

  private def staged(favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, enforcer).favor(p1, favor)

  /** The discard pile of the region after p1's. */
  private def pile(ready: ReadyGame): Vector[WorldCardId] =
    val current = ready.game.current
    current.commonCards.discard(
      current.map.regionOf(home).map(CardPlay.nextRegion).get)

  test("it burns a favor and discards every Order and Hearth card at the " +
      "site, with their returns"):
    val ruined = edificeOf(Suit.Order)
    val ready = staged().denizen(order(0), at = home)
      .denizen(hearth(0), at = home).denizen(beast(0), at = home)
      .edifice(ruined, EdificeSide.Ruined, at = home)
      .tokens(order(0), favor = 1).ready
    val orderBank = ready.banks.favor.getOrElse(Suit.Order, 0)
    val t = use(ready, power, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).favor(p1), 0)
    assertEquals(Look(end).denizens(home), Vector[CardId](beast(0)))
    assertEquals(pile(end).takeRight(2),
      Vector[WorldCardId](order(0), hearth(0)))
    assertEquals(end.game.current.commonCards.edificeDeck.lastOption,
      Some(ruined))
    assertEquals(end.banks.favor.getOrElse(Suit.Order, 0), orderBank + 1)
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"Discarded ${order(0).value}, ${hearth(0).value}, ${ruined.value}.",
      covers = true)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("an intact edifice and a facedown card stay, and the line names " +
      "the site"):
    val ready = staged()
      .edifice(edificeOf(Suit.Hearth), EdificeSide.Intact, at = home)
      .denizen(order(0), at = home, facedown = true).ready
    val t = use(ready, power, source).toOption.get
    assertEquals(Look(after(t)).denizens(home), Look(ready).denizens(home))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no Order or Hearth card to discard.",
      covers = true)))

  test("it is unusable without a favor to burn"):
    val broke = staged(favor = 0).ready
    assert(!usableNow(broke).exists(_.power.id == DarkEnforcer.id))
    assert(use(broke, power, source).isLeft)
