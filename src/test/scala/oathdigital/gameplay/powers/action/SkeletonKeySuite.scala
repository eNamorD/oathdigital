package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class SkeletonKeySuite extends munit.FunSuite:
  import TargetsFixture._

  private val key = CatalogNames.relic("Skeleton Key")
  private val source = DecisionOptionRef.Relic(key)
  private val wild = Table.start.ready.game.current.map.hinterland.head
  private val home = Table.homeOf(p1)
  private val (first, second) = (RelicId("R08"), RelicId("R09"))

  /** p1's Act holding Skeleton Key faceup with `secrets` faceup secrets,
    * the pawn at `at`, and `relics` facedown there. */
  private def staged(at: SiteId = wild,
      relics: Vector[RelicId] = Vector(first, second),
      secrets: Int = 2): ReadyGame =
    relics.foldLeft(Table.start.turn(p1, Phase.Act).relic(p1, key)
      .secrets(p1, secrets).pawn(p1, at))(_.relicAt(_, at)).ready

  private def knows(ready: ReadyGame, relic: RelicId): Boolean =
    ready.knowledge.siteRelics.getOrElse(p1, Map.empty)
      .valuesIterator.exists(_.contains(relic))
  private def relicRef(id: RelicId): DecisionAnswer =
    pick(DecisionOptionRef.Relic(id))

  test("at a Hinterland site it peeks at every relic there and asks which " +
      "to take"):
    val t = use(staged(), SkeletonKey, source).toOption.get
    assert(awaits(t, SkeletonKey.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).faceUpSecrets(p1), 0)
    assertEquals(Look(after(t)).tokensOn(key), Tokens(0, 1))
    assert(knows(after(t), first) && knows(after(t), second))
    assertEquals(offered(t, p1),
      Some(Vector("relic" -> first.value, "relic" -> second.value)))

  test("the chosen relic moves facedown to the player, and the other stays"):
    val ready = staged()
    val t = use(ready, SkeletonKey, source).toOption.get
    val done = answer(t, p1, SkeletonKey.decisionId, relicRef(first))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assert(Look(end).player(p1).relics.exists(relic =>
      relic.id == first && relic.orientation == Orientation.FaceDown))
    assertEquals(Look(end).relicsAt(wild), Vector(second))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("it writes its peek, covering the peek lines, then its take"):
    val t = use(staged(), SkeletonKey, source).toOption.get
    val done = answer(t, p1, SkeletonKey.decisionId, relicRef(first))
      .toOption.get
    assertEquals(NoteText.said(SkeletonKey, t.events ++ done.events), Vector(
      NoteText.Said("used.peeked", s"${p1.value} peeked at the relics at " +
        s"${wild.value}: ${first.value}, ${second.value}.", covers = true),
      NoteText.Said(NoteKey.Used,
        s"${p1.value} took ${first.value} facedown from ${wild.value}.",
        covers = false)))

  test("away from the Hinterland the cost is paid and nothing else happens"):
    assert(!Table.start.ready.game.current.map.hinterland.contains(home))
    val ready = staged(at = home)
    val done = use(ready, SkeletonKey, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(key), Tokens(0, 1))
    assertEquals(Look(after(done)).relicsAt(home), Vector(first, second))
    assertEquals(NoteText.said(SkeletonKey, done.events), Vector(
      NoteText.Said("used.away", s"${p1.value} was not at a Hinterland site.",
        covers = false)))

  test("a Hinterland site with no relic: the line says so"):
    val done = use(staged(relics = Vector.empty), SkeletonKey, source)
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(SkeletonKey, done.events), Vector(
      NoteText.Said("used.none", s"${wild.value} held no relic.",
        covers = false)))

  test("one secret is not enough"):
    val broke = staged(secrets = 1)
    assert(!usableNow(broke).exists(_.power.id == SkeletonKey.id))
    assert(use(broke, SkeletonKey, source).isLeft)
