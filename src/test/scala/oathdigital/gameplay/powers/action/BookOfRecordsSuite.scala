package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture, TargetsFixture}
import oathdigital.model._

class BookOfRecordsSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._
  import PaidActionHarness.{secrets, tokensOn, wireRoundTrips}

  private val book = RelicId("R19")
  private val source = DecisionOptionRef.Relic(book)
  private val holder = others(base)(0)
  private val bystander = others(base)(1)

  private def elsewhere(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(_ != home(ready)).get
  private def withBanner(ready: ReadyGame, banner: Banner,
      owner: Option[PlayerId], amount: Int): ReadyGame =
    ready.updateCurrent(c => c.copy(banners = banner match
      case Banner.PeoplesFavor => c.banners.copy(peoplesFavor =
        c.banners.peoplesFavor.copy(holder = owner, favor = amount))
      case Banner.DarkestSecret => c.banners.copy(darkestSecret =
        c.banners.darkestSecret.copy(holder = owner, secrets = amount))))
  private def held(ready: ReadyGame, banner: Banner): Int = banner match
    case Banner.PeoplesFavor => ready.game.current.banners.peoplesFavor.favor
    case Banner.DarkestSecret => ready.game.current.banners.darkestSecret.secrets
  private def choose(banner: Banner) = pick(DecisionOptionRef.Banner(banner))

  /** The actor holds Book of Records faceup with `faceUp` secrets. `holder`
    * stands at the actor's site holding the People's Favor with `favor`;
    * `bystander` stands elsewhere holding the Darkest Secret with 3 secrets,
    * so it is never offered.
    */
  private def staged(favor: Int = 3, faceUp: Int = 3): ReadyGame =
    val actorReady = inPhase(withSecrets(withRelic(base, book), actor, faceUp,
      0), Phase.Act)
    val placed = withPawn(withPawn(actorReady, holder, home(actorReady)),
      bystander, elsewhere(actorReady))
    withBanner(withBanner(placed, Banner.PeoplesFavor, Some(holder), favor),
      Banner.DarkestSecret, Some(bystander), 3)

  /** Uses it on `ready` and answers `banner`. */
  private def takeFrom(ready: ReadyGame, banner: Banner) =
    val t = use(ready, BookOfRecords, source).toOption.get
    (t, answer(t, actor, BookOfRecords.decisionId, choose(banner)).toOption.get)

  test("it places a secret, burns two, and offers the banners held at the " +
      "actor's site"):
    val t = use(staged(), BookOfRecords, source).toOption.get
    assert(awaits(t, BookOfRecords.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor),
      Some(Vector("banner" -> Banner.PeoplesFavor.key)))
    assertEquals(tokensOn(after(t), book), Tokens(0, 1))
    assertEquals(secrets(after(t)), 0)

  test("the People's Favor gives two favor"):
    val ready = staged()
    val (t, done) = takeFrom(ready, Banner.PeoplesFavor)
    assertEquals(player(after(done)).board.favor, player(ready).board.favor + 2)
    assertEquals(held(after(done), Banner.PeoplesFavor), 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(wireRoundTrips(t.events ++ done.events))
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 favor from " +
        s"${holder.value}'s People's Favor.", covers = false)))

  test("the Darkest Secret gives two secrets, faceup"):
    val ready = withBanner(staged(), Banner.DarkestSecret, Some(holder), 3)
    val (_, done) = takeFrom(ready, Banner.DarkestSecret)
    assertEquals(player(after(done)).board.faceUpSecrets, 2)
    assertEquals(held(after(done), Banner.DarkestSecret), 1)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 secrets from " +
        s"${holder.value}'s Darkest Secret.", covers = false)))

  test("a banner holding one gives one"):
    val ready = staged(favor = 1)
    val (_, done) = takeFrom(ready, Banner.PeoplesFavor)
    assertEquals(held(after(done), Banner.PeoplesFavor), 0)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 1 favor from " +
        s"${holder.value}'s People's Favor.", covers = false)))

  test("the actor's own banner may be chosen"):
    val ready = withBanner(staged(), Banner.PeoplesFavor, Some(actor), 2)
    val t = use(ready, BookOfRecords, source).toOption.get
    assertEquals(offered(t, actor),
      Some(Vector("banner" -> Banner.PeoplesFavor.key)))
    val done = answer(t, actor, BookOfRecords.decisionId,
      choose(Banner.PeoplesFavor)).toOption.get
    assertEquals(player(after(done)).board.favor, player(ready).board.favor + 2)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 favor from " +
        s"${actor.value}'s People's Favor.", covers = false)))

  test("an empty banner gives nothing, and the line says so"):
    val ready = staged(favor = 0)
    val (_, done) = takeFrom(ready, Banner.PeoplesFavor)
    assertEquals(player(after(done)).board.favor, player(ready).board.favor)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said("used.empty",
        s"${holder.value}'s People's Favor held nothing to take.",
        covers = false)))

  test("with no banner held at the site the cost is paid and nothing else " +
      "happens"):
    val ready = withBanner(staged(), Banner.PeoplesFavor, None, 3)
    val t = use(ready, BookOfRecords, source).toOption.get
    parked.assertNotParked(t.state)
    assertEquals(tokensOn(after(t), book), Tokens(0, 1))
    assertEquals(held(after(t), Banner.PeoplesFavor), 3)
    assertEquals(NoteText.said(BookOfRecords, t.events), Vector(
      NoteText.Said("used.none", "No player at the site held a banner.",
        covers = false)))

  test("a banner held away from the site is refused"):
    val t = use(staged(), BookOfRecords, source).toOption.get
    assert(answer(t, actor, BookOfRecords.decisionId,
      choose(Banner.DarkestSecret)).isLeft)

  test("two secrets are not enough"):
    val ready = staged(faceUp = 2)
    assert(!usableNow(ready).exists(_.power.id == BookOfRecords.id))
    assert(use(ready, BookOfRecords, source).isLeft)
