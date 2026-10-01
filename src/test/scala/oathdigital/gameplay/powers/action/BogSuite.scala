package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BogSuite extends munit.FunSuite:
  import TargetsFixture._

  private val bog = CatalogNames.denizen("Bog")
  private val source = DecisionOptionRef.Denizen(bog)
  private val scepter = RelicId("grand-scepter")
  private val whistle = RelicId("R08")

  /** p1's Act beside a site Bog, holding `relics` faceup, with `beast` favor
    * in the Beast bank. Bog is site-only; it stands at p1's pawn site. */
  private def staged(relics: Vector[RelicId] = Vector(whistle),
      beast: Int = 5): ReadyGame =
    relics.foldLeft(Table.start.turn(p1, Phase.Act)
      .denizen(bog, at = Table.homeOf(p1)).bankFavor(Suit.Beast, beast))(
      _.relic(p1, _)).ready

  private def beastBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Beast, 0)
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))
  private def used(ready: ReadyGame): OathTransition =
    val t = use(ready, Bog, source).toOption.get
    answer(t, p1, Bog.decisionId, relicRef(whistle)).toOption.get

  test("it costs nothing and asks which held relic to discard"):
    val t = use(staged(), Bog, source).toOption.get
    assert(awaits(t, Bog.decisionId), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("relic" -> whistle.value)))
    assertEquals(Look(after(t)).tokensOn(bog), Tokens.empty)

  test("the relic is discarded and 3 favor come from the Beast bank"):
    val ready = staged()
    val t = use(ready, Bog, source).toOption.get
    val done = answer(t, p1, Bog.decisionId, relicRef(whistle)).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).relics(p1), Vector.empty[RelicId])
    assertEquals(Look(end).favor(p1), Look(ready).favor(p1) + 3)
    assertEquals(beastBank(end), 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

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

  test("holding only the Grand Scepter, it asks nothing and writes nothing"):
    val ready = staged(Vector(scepter))
    val done = use(ready, Bog, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).relics(p1), Vector(scepter))
    assertEquals(beastBank(after(done)), 5)
    assertEquals(NoteText.said(Bog, done.events), Vector.empty)

  test("holding no relic, nothing happens and the line says so"):
    val done = use(staged(Vector.empty), Bog, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(Bog, done.events), Vector(
      NoteText.Said("used.none", s"${p1.value} held no relic.",
        covers = true)))
