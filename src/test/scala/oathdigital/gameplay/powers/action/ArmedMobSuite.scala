package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class ArmedMobSuite extends munit.FunSuite:
  import TargetsFixture._

  private val mob = CatalogNames.denizen("Armed Mob")
  private val source = DecisionOptionRef.Denizen(mob)
  private val power = ArmedMob.forCatalog(catalog)
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val insomnia = CatalogNames.denizen("Insomnia")

  /** p1's Act beside a site Armed Mob, with `favor` favor. Armed Mob is
    * site-only; it stands at p1's pawn site. p2 holds the Darkest Secret. */
  private def staged(favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).denizen(mob, at = Table.homeOf(p1))
      .favor(p1, favor).darkestSecret(Some(p2), 1)

  private def discarded(ready: ReadyGame): Vector[WorldCardId] =
    val current = ready.game.current
    val region = current.map.regionOf(Table.homeOf(p1)).map(CardPlay.nextRegion)
    current.commonCards.discard(region.get)

  private def choose(ready: ReadyGame, card: DenizenId) =
    val t = use(ready, power, source).toOption.get
    (t, answer(t, p1, ArmedMob.decisionId,
      pick(DecisionOptionRef.Denizen(card))).toOption.get)

  test("it places a favor and offers the Darkest Secret holder's faceup " +
      "advisers, not facedown or locked ones"):
    val ready = staged().adviser(p2, plain(0))
      .adviser(p2, plain(1), facedown = true).adviser(p2, insomnia).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, ArmedMob.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(mob), Tokens(1, 0))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> plain(0).value)))

  test("the adviser is discarded to the next region's pile with its returns"):
    val ready = staged().adviser(p2, plain(0))
      .tokens(plain(0), favor = 1, secrets = 1).ready
    val hearth = ready.banks.favor.getOrElse(Suit.Hearth, 0)
    val (t, done) = choose(ready, plain(0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).advisers(p2), Vector.empty[CardId])
    assertEquals(discarded(end).lastOption, Some(plain(0)))
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0), hearth + 1)
    assertEquals(Look(end).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the card and its owner, covering the Discarded line"):
    val (_, done) = choose(staged().adviser(p2, plain(0)).ready, plain(0))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} discarded ${plain(0).value} from ${p2.value}'s advisers.",
      covers = true)))

  test("the player may be the target"):
    val ready = staged().darkestSecret(Some(p1), 1).adviser(p1, plain(0))
      .ready
    val (_, done) = choose(ready, plain(0))
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])

  test("a holder of both banners is no target, and the line says so"):
    val ready = staged().peoplesFavor(Some(p2), 1).adviser(p2, plain(0)).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](plain(0)))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none",
      "No player held the Darkest Secret without the People's Favor.",
      covers = true)))

  test("a target with only a locked adviser keeps it, and the line says so"):
    val ready = staged().adviser(p2, insomnia).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](insomnia))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.empty", s"${p2.value} had no faceup adviser to discard.",
      covers = true)))

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).adviser(p2, plain(0)).ready
    assert(!usableNow(broke).exists(_.power.id == ArmedMob.id))
    assert(use(broke, power, source).isLeft)
