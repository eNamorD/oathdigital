package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class BarbedNetSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._
  import ActPowerHarness.allSecrets

  private val net = CatalogNames.relic("Barbed Net")
  private val source = DecisionOptionRef.Relic(net)

  // The relic the tests take from the site, the one they leave there, and
  // one that stays in the relic deck.
  private val taken = CatalogNames.relic("Dowsing Sticks")
  private val left = CatalogNames.relic("Cracked Horn")
  private val inRelicDeck = CatalogNames.relic("Whistle")

  /** p1's Act at Ancient City holding Barbed Net faceup with `faceUp`
    * secrets. The site holds Dowsing Sticks and Cracked Horn facedown, or no
    * relic when `relicsAtSite` is false. */
  private def staged(relicsAtSite: Boolean = true, faceUp: Int = 3)
      : ReadyGame =
    val holding = Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .relic(actor, net).secrets(actor, faceUp)
    if relicsAtSite then
      holding.relicAt(taken, "Ancient City").relicAt(left, "Ancient City").ready
    else holding.ready
  private def atSite(ready: ReadyGame): Vector[RelicId] =
    ready.game.current.map.sites(actorsSite(ready)).relics.map(_.id)
  private def knows(ready: ReadyGame, relic: RelicId): Boolean =
    ready.knowledge.siteRelics.getOrElse(actor, Map.empty)
      .valuesIterator.exists(_.contains(relic))
  private def choose(relic: RelicId) = pick(DecisionOptionRef.Relic(relic))

  test("it burns three secrets, peeks at every relic at the site and asks " +
      "which to take"):
    // 3 faceup secrets, all burnt.
    val ready = staged(faceUp = 3)
    val relics = atSite(ready)
    val t = accepted(use(ready, BarbedNet, source))
    assert(awaits(t, BarbedNet.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(allSecrets(after(t)), 0)
    assertEquals(Look(after(t)).tokensOn(net), Tokens.empty)
    relics.foreach(relic => assert(knows(after(t), relic), relic.value))
    assertEquals(offered(t, actor),
      Some(relics.map(relic => "relic" -> relic.value)))

  test("the chosen relic moves facedown to the actor, and the others stay"):
    val ready = staged()
    val t = accepted(use(ready, BarbedNet, source))
    val done = accepted(answer(t, actor, BarbedNet.decisionId, choose(taken)))
    assert(Look(after(done)).player(actor).relics.exists(relic =>
      relic.id == taken && relic.orientation == Orientation.FaceDown))
    assertEquals(atSite(after(done)), Vector(left))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("it writes its peek, covering the peek lines, then its take"):
    val ready = staged()
    val t = accepted(use(ready, BarbedNet, source))
    val done = accepted(answer(t, actor, BarbedNet.decisionId, choose(taken)))
    val site = actorsSite(ready).value
    assertEquals(NoteText.said(BarbedNet, t.events ++ done.events), Vector(
      NoteText.Said("used.peeked", s"${actor.value} peeked at the relics at " +
        s"$site: ${taken.value}, ${left.value}.", covers = true),
      NoteText.Said(NoteKey.Used,
        s"${actor.value} took ${taken.value} facedown from $site.",
        covers = false)))

  test("a site with no relic: the cost is paid and nothing else happens"):
    val ready = staged(relicsAtSite = false)
    val t = accepted(use(ready, BarbedNet, source))
    parked.assertNotParked(t.state)
    assertEquals(allSecrets(after(t)), 0)
    assertEquals(NoteText.said(BarbedNet, t.events), Vector(NoteText.Said(
      "used.none", s"${actorsSite(ready).value} held no relic.", covers = false)))

  test("a relic not at the site is refused"):
    val ready = staged()
    val t = accepted(use(ready, BarbedNet, source))
    assert(answer(t, actor, BarbedNet.decisionId,
      choose(inRelicDeck)).isLeft)

  test("two secrets are not enough"):
    val ready = staged(faceUp = 2)
    assert(!usableNow(ready).exists(_.power.id == BarbedNet.id))
    assert(use(ready, BarbedNet, source).isLeft)
