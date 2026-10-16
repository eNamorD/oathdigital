package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class BogSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val bog = CatalogNames.denizen("Bog")
  private val source = DecisionOptionRef.Denizen(bog)
  private val scepter = RelicId("grand-scepter")
  private val whistle = CatalogNames.relic("Whistle")

  /** p1's Act at Ancient City beside a site Bog, holding `relics` faceup,
    * with `beast` favor in the Beast bank. Bog is site-only. */
  private def staged(relics: Vector[RelicId] = Vector(whistle),
      beast: Int = 5): ReadyGame =
    relics.foldLeft(Table.start.turn(p1, Phase.Act).pawn(p1, "Ancient City")
      .denizen(bog, at = "Ancient City").bankFavor(Suit.Beast, beast))(
      _.relic(p1, _)).ready

  private def beastBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Beast, 0)
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))
  private def used(ready: ReadyGame): OathTransition =
    val t = accepted(use(ready, Bog, source))
    accepted(answer(t, p1, Bog.decisionId, relicRef(whistle)))

  test("it costs nothing and asks which held relic to discard"):
    val t = accepted(use(staged(), Bog, source))
    assert(awaits(t, Bog.decisionId), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("relic" -> whistle.value)))
    assertEquals(Look(after(t)).tokensOn(bog), Tokens.empty)

  test("the relic is discarded and 3 favor come from the Beast bank"):
    // 5 in the Beast bank less the 3 taken leave 2.
    val ready = staged(beast = 5)
    val t = accepted(use(ready, Bog, source))
    val done = accepted(answer(t, p1, Bog.decisionId, relicRef(whistle)))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector.empty[RelicId])
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 3)
    assertEquals(beastBank(end), 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("its line names the relic and the favor, covering the gain line"):
    assertEquals(NoteText.said(Bog, used(staged()).events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} discarded ${whistle.value} " +
        "and gained 3 favor from the Beast bank.", covers = true)))

  test("a Beast bank holding one favor gives one"):
    val done = used(staged(beast = 1))
    assertEquals(beastBank(after(done)), 0)
    assertEquals(NoteText.said(Bog, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} discarded ${whistle.value} " +
        "and gained 1 favor from the Beast bank.", covers = true)))

  test("an empty Beast bank: the relic is still discarded, and the line " +
      "names only the relic"):
    val ready = staged(beast = 0)
    val done = used(ready)
    assertEquals(Look(after(done)).relics(p1), Vector.empty[RelicId])
    assertEquals(Look(after(done)).favor(p1), Look(ready).favor(p1))
    assertEquals(NoteText.said(Bog, done.events), Vector(
      NoteText.Said("used.discarded",
        s"${p1.value} discarded ${whistle.value}.", covers = true)))

  test("holding only the Grand Scepter, it shows Continue and writes " +
      "nothing"):
    val ready = staged(Vector(scepter), beast = 5)
    val asked = accepted(use(ready, Bog, source))
    assert(awaits(asked, Bog.decisionId))
    val done = accepted(continuedPast(asked, Bog.decisionId))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).relics(p1), Vector(scepter))
    assertEquals(beastBank(after(done)), 5)
    assertEquals(NoteText.said(Bog, done.events), Vector.empty)

  test("holding no relic, nothing happens and the line says so"):
    val done = accepted(use(staged(Vector.empty), Bog, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(Bog, done.events), Vector(
      NoteText.Said("used.none", s"${p1.value} held no relic.",
        covers = true)))
