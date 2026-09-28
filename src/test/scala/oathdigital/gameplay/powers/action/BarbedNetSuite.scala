package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture, TargetsFixture}
import oathdigital.model._

class BarbedNetSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._
  import PaidActionHarness.{secrets, tokensOn, wireRoundTrips}

  private val net = RelicId("R36")
  private val source = DecisionOptionRef.Relic(net)

  /** Relics in the relic deck other than Barbed Net. */
  private def spare(ready: ReadyGame): Vector[RelicId] =
    ready.game.current.commonCards.relicDeck.filterNot(_ == net)

  /** The actor's site holds exactly `relics`, facedown, taken from the relic
    * deck. The relics it held return to the bottom of the relic deck.
    */
  private def withSiteRelics(ready: ReadyGame, relics: Vector[RelicId])
      : ReadyGame =
    val site = home(ready)
    ready.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(relicDeck =
        c.commonCards.relicDeck.filterNot(relics.contains) ++
          c.map.sites(site).relics.map(_.id)),
      map = c.map.copy(sites = c.map.sites.updated(site,
        c.map.sites(site).copy(relics = relics.map(relic =>
          RelicState(relic, Orientation.FaceDown, Tokens.empty)))))))

  /** The actor holds Barbed Net faceup with `faceUp` secrets, and the site
    * holds `count` relics. */
  private def staged(count: Int = 2, faceUp: Int = 3): ReadyGame =
    val held = inPhase(withSecrets(withRelic(base, net), actor, faceUp, 0),
      Phase.Act)
    withSiteRelics(held, spare(held).take(count))
  private def atSite(ready: ReadyGame): Vector[RelicId] =
    ready.game.current.map.sites(home(ready)).relics.map(_.id)
  private def knows(ready: ReadyGame, relic: RelicId): Boolean =
    ready.knowledge.siteRelics.getOrElse(actor, Map.empty)
      .valuesIterator.exists(_.contains(relic))
  private def choose(relic: RelicId) = pick(DecisionOptionRef.Relic(relic))

  test("it burns three secrets, peeks at every relic at the site and asks " +
      "which to take"):
    val ready = staged()
    val relics = atSite(ready)
    val t = use(ready, BarbedNet, source).toOption.get
    assert(awaits(t, BarbedNet.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(secrets(after(t)), 0)
    assertEquals(tokensOn(after(t), net), Tokens.empty)
    relics.foreach(relic => assert(knows(after(t), relic), relic.value))
    assertEquals(offered(t, actor),
      Some(relics.map(relic => "relic" -> relic.value)))

  test("the chosen relic moves facedown to the actor, and the others stay"):
    val ready = staged()
    val relics = atSite(ready)
    val (taken, left) = (relics(0), relics(1))
    val t = use(ready, BarbedNet, source).toOption.get
    val done = answer(t, actor, BarbedNet.decisionId, choose(taken)).toOption.get
    assert(player(after(done)).relics.exists(relic =>
      relic.id == taken && relic.orientation == Orientation.FaceDown))
    assertEquals(atSite(after(done)), Vector(left))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(wireRoundTrips(t.events ++ done.events))

  test("it writes its peek, covering the peek lines, then its take"):
    val ready = staged()
    val relics = atSite(ready)
    val (taken, left) = (relics(0), relics(1))
    val t = use(ready, BarbedNet, source).toOption.get
    val done = answer(t, actor, BarbedNet.decisionId, choose(taken)).toOption.get
    val site = home(ready).value
    assertEquals(NoteText.said(BarbedNet, t.events ++ done.events), Vector(
      NoteText.Said("used.peeked", s"${actor.value} peeked at the relics at " +
        s"$site: ${taken.value}, ${left.value}.", covers = true),
      NoteText.Said(NoteKey.Used,
        s"${actor.value} took ${taken.value} facedown from $site.",
        covers = false)))

  test("a site with no relic: the cost is paid and nothing else happens"):
    val ready = staged(count = 0)
    val t = use(ready, BarbedNet, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(secrets(after(t)), 0)
    assertEquals(NoteText.said(BarbedNet, t.events), Vector(NoteText.Said(
      "used.none", s"${home(ready).value} held no relic.", covers = false)))

  test("a relic not at the site is refused"):
    val ready = staged()
    val t = use(ready, BarbedNet, source).toOption.get
    assert(answer(t, actor, BarbedNet.decisionId,
      choose(spare(ready).last)).isLeft)

  test("two secrets are not enough"):
    val ready = staged(faceUp = 2)
    assert(!usableNow(ready).exists(_.power.id == BarbedNet.id))
    assert(use(ready, BarbedNet, source).isLeft)
