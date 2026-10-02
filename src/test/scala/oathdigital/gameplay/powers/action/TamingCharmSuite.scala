package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class TamingCharmSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val charm = CatalogNames.denizen("Taming Charm")
  private val source = DecisionOptionRef.Denizen(charm)
  private val power = TamingCharm.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  // Denizens whose powers these tests do not use, and a Beast edifice.
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val faeMerchant = CatalogNames.denizen("Fae Merchant")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val codeOfHonor = CatalogNames.denizen("Code of Honor")
  private val ruinedBeast = CatalogNames.edifice("Hiding Place")

  /** p1's Act at Ancient City holding Taming Charm, the Beast and Nomad
    * banks holding `beasts` and `nomads`. */
  private def staged(beasts: Int = 5, nomads: Int = 5): Table =
    Table.start.turn(p1, Phase.Act).pawn(p1, home).adviser(p1, charm)
      .bankFavor(Suit.Beast, beasts).bankFavor(Suit.Nomad, nomads)

  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  /** The pile a card discarded at Ancient City goes to. */
  private def pile(ready: ReadyGame): Vector[WorldCardId] =
    Look(ready).discardAfter(home)

  /** The walk Taming Charm started and the walk after `card` was chosen. */
  private case class Tamed(started: OathTransition, answered: OathTransition)

  private def tame(ready: ReadyGame, card: DecisionOptionRef): Tamed =
    val started = accepted(use(ready, power, source))
    Tamed(started, accepted(answer(started, p1, TamingCharm.decisionId,
      pick(card))))

  test("it places a secret and offers the Beast and Nomad cards at the " +
      "site, not others or facedown ones"):
    val ready = staged().denizen(errandBoy, at = home)
      .denizen(wildMounts, at = home).denizen(codeOfHonor, at = home)
      .denizen(faeMerchant, at = home, facedown = true)
      .edifice(ruinedBeast, EdificeSide.Ruined, at = home).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, TamingCharm.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(charm), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> errandBoy.value,
      "denizen" -> wildMounts.value, "edifice" -> ruinedBeast.value)))

  test("a Beast card is discarded, then 2 favor come from the Beast bank"):
    // The Beast bank's 5 less 2 leave 3.
    val ready = staged(beasts = 5).denizen(errandBoy, at = home).ready
    val Tamed(t, done) = tame(ready, DecisionOptionRef.Denizen(errandBoy))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).cardsAt(home), Vector.empty[CardId])
    assertEquals(pile(end).lastOption, Some(errandBoy))
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(bank(end, Suit.Beast), 3)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} discarded ${errandBoy.value} and gained " +
        "2 favor from the Beast bank.", covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("the gain runs after the discard returned its favor to the same " +
      "bank"):
    val ready = staged(beasts = 0).denizen(errandBoy, at = home)
      .tokens(errandBoy, favor = 1).ready
    val done = tame(ready, DecisionOptionRef.Denizen(errandBoy)).answered
    val end = after(done)
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(bank(end, Suit.Beast), 0)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} discarded ${errandBoy.value} and gained " +
        "1 favor from the Beast bank.", covers = true)))

  test("a Nomad card pays from the Nomad bank"):
    // The Nomad bank's 3 less 2 leave 1; the Beast bank keeps its 5.
    val ready = staged(beasts = 5, nomads = 3).denizen(wildMounts,
      at = home).ready
    val done = tame(ready, DecisionOptionRef.Denizen(wildMounts)).answered
    val end = after(done)
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)
    assertEquals((bank(end, Suit.Nomad), bank(end, Suit.Beast)), (1, 5))

  test("a ruined edifice goes to the bottom of the edifice deck"):
    val ready = staged().edifice(ruinedBeast, EdificeSide.Ruined, at = home)
      .ready
    val done = tame(ready, DecisionOptionRef.Edifice(ruinedBeast)).answered
    val end = after(done)
    assertEquals(end.game.current.commonCards.edificeDeck.lastOption,
      Some(ruinedBeast))
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 2)

  test("with the bank empty the card is still discarded, and the line " +
      "says only that"):
    val ready = staged(beasts = 0).denizen(errandBoy, at = home).ready
    val done = tame(ready, DecisionOptionRef.Denizen(errandBoy)).answered
    assertEquals(Look(after(done)).favor(p1), Look(ready).favor(p1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.discarded", s"${p1.value} discarded ${errandBoy.value}.",
      covers = true)))

  test("with nothing to discard the cost stays paid, and the line names " +
      "the site"):
    val ready = staged().denizen(codeOfHonor, at = home).ready
    val t = accepted(use(ready, power, source))
    parked.assertResumed(t.state, Phase.Act, p1)
    assertEquals(Look(after(t)).tokensOn(charm), Tokens(0, 1))
    assertEquals(NoteText.said(power, t.events), Vector(NoteText.Said(
      "used.none", s"${home.value} held no Beast or Nomad card.",
      covers = true)))
