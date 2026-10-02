package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class BookOfRecordsSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._
  import ActPowerHarness.allSecrets

  private val book = CatalogNames.relic("Book of Records")
  private val source = DecisionOptionRef.Relic(book)
  private val holder = Table.p2
  private val bystander = Table.p3

  private def held(ready: ReadyGame, banner: Banner): Int = banner match
    case Banner.PeoplesFavor => ready.game.current.banners.peoplesFavor.favor
    case Banner.DarkestSecret => ready.game.current.banners.darkestSecret.secrets
  private def choose(banner: Banner) = pick(DecisionOptionRef.Banner(banner))

  /** p1's Act at Ancient City holding Book of Records faceup with `faceUp`
    * secrets. `holder` (p2) stands at Ancient City holding the People's Favor
    * with `favor`; `bystander` (p3) stands at Buried Giant holding the
    * Darkest Secret with 3 secrets, so it is never offered.
    */
  private def table(favor: Int = 3, faceUp: Int = 3): Table =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .relic(actor, book).secrets(actor, faceUp)
      .pawn(holder, "Ancient City").pawn(bystander, "Buried Giant")
      .peoplesFavor(Some(holder), favor).darkestSecret(Some(bystander), 3)
  private def staged(favor: Int = 3, faceUp: Int = 3): ReadyGame =
    table(favor, faceUp).ready

  /** The walk Book of Records started and the walk after `banner` was
    * picked. */
  private case class Taken(started: OathTransition, answered: OathTransition)

  /** Uses it on `ready` and answers `banner`. */
  private def takeFrom(ready: ReadyGame, banner: Banner): Taken =
    val started = accepted(use(ready, BookOfRecords, source))
    Taken(started, accepted(answer(started, actor, BookOfRecords.decisionId,
      choose(banner))))

  test("it places a secret, burns two, and offers the banners held at the " +
      "actor's site"):
    val t = accepted(use(staged(), BookOfRecords, source))
    assert(awaits(t, BookOfRecords.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor),
      Some(Vector("banner" -> Banner.PeoplesFavor.key)))
    assertEquals(Look(after(t)).tokensOn(book), Tokens(0, 1))
    assertEquals(allSecrets(after(t)), 0)

  test("the People's Favor gives two favor"):
    // The banner's 3 favor less the 2 taken leave 1.
    val ready = staged(favor = 3)
    val Taken(t, done) = takeFrom(ready, Banner.PeoplesFavor)
    assertEquals(Look(after(done)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor + 2)
    assertEquals(held(after(done), Banner.PeoplesFavor), 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 favor from " +
        s"${holder.value}'s People's Favor.", covers = false)))

  test("the Darkest Secret gives two secrets, faceup"):
    // 3 faceup less 1 placed and 2 burnt leave 0, then 2 are gained.
    val ready = table(faceUp = 3).darkestSecret(Some(holder), 3).ready
    val done = takeFrom(ready, Banner.DarkestSecret).answered
    assertEquals(Look(after(done)).player(actor).board.faceUpSecrets, 2)
    assertEquals(held(after(done), Banner.DarkestSecret), 1)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 secrets from " +
        s"${holder.value}'s Darkest Secret.", covers = false)))

  test("a banner holding one gives one"):
    val ready = staged(favor = 1)
    val done = takeFrom(ready, Banner.PeoplesFavor).answered
    assertEquals(held(after(done), Banner.PeoplesFavor), 0)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 1 favor from " +
        s"${holder.value}'s People's Favor.", covers = false)))

  test("the actor's own banner may be chosen"):
    val ready = table().peoplesFavor(Some(actor), 2).ready
    val t = accepted(use(ready, BookOfRecords, source))
    assertEquals(offered(t, actor),
      Some(Vector("banner" -> Banner.PeoplesFavor.key)))
    val done = accepted(answer(t, actor, BookOfRecords.decisionId,
      choose(Banner.PeoplesFavor)))
    assertEquals(Look(after(done)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor + 2)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} took 2 favor from " +
        s"${actor.value}'s People's Favor.", covers = false)))

  test("an empty banner gives nothing, and the line says so"):
    val ready = staged(favor = 0)
    val done = takeFrom(ready, Banner.PeoplesFavor).answered
    assertEquals(Look(after(done)).player(actor).board.favor, Look(ready)
      .player(actor).board.favor)
    assertEquals(NoteText.said(BookOfRecords, done.events), Vector(
      NoteText.Said("used.empty",
        s"${holder.value}'s People's Favor held nothing to take.",
        covers = false)))

  test("with no banner held at the site the cost is paid and nothing else " +
      "happens"):
    val ready = table().peoplesFavor(None, 3).ready
    val t = accepted(use(ready, BookOfRecords, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).tokensOn(book), Tokens(0, 1))
    assertEquals(held(after(t), Banner.PeoplesFavor), 3)
    assertEquals(NoteText.said(BookOfRecords, t.events), Vector(
      NoteText.Said("used.none", "No player at the site held a banner.",
        covers = false)))

  test("a banner held away from the site is refused"):
    val t = accepted(use(staged(), BookOfRecords, source))
    assert(answer(t, actor, BookOfRecords.decisionId,
      choose(Banner.DarkestSecret)).isLeft)

  test("two secrets are not enough"):
    val ready = staged(faceUp = 2)
    assert(!usableNow(ready).exists(_.power.id == BookOfRecords.id))
    assert(use(ready, BookOfRecords, source).isLeft)
