package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class BedOfRootsSuite extends munit.FunSuite:
  import TargetsFixture._

  private val bed = CatalogNames.denizen("Bed of Roots")
  private val source = DecisionOptionRef.Denizen(bed)
  private val power = BedOfRoots.forCatalog(catalog).get
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val insomnia = CatalogNames.denizen("Insomnia")
  private val vision = VisionId("vision:vision-of-faith")

  /** p1's Act beside a site Bed of Roots, with `favor` favor. Bed of Roots
    * is site-only; it stands at p1's pawn site. */
  private def staged(favor: Int = 3): Table =
    Table.start.turn(p1, Phase.Act).denizen(bed, at = Table.homeOf(p1))
      .favor(p1, favor)

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)
  private def denizenRef(id: DenizenId): DecisionAnswer =
    pick(DecisionOptionRef.Denizen(id))

  test("it burns 3 favor and offers only the faceup denizen advisers"):
    val ready = staged().adviser(p1, plain(0))
      .adviser(p1, plain(1), facedown = true).adviser(p1, vision, facedown = true).ready
    val t = use(ready, power, source).toOption.get
    assert(awaits(t, BedOfRoots.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).favor(p1), 0)
    assertEquals(offered(t, p1).map(_.map(_._2)), Some(Vector(plain(0).value)))

  test("the adviser is buried with its returns, and 2 secrets are gained"):
    val ready = staged().adviser(p1, plain(0))
      .tokens(plain(0), favor = 1, secrets = 1).ready
    val hearth = ready.banks.favor.getOrElse(Suit.Hearth, 0)
    val t = use(ready, power, source).toOption.get
    val done = answer(t, p1, BedOfRoots.decisionId, denizenRef(plain(0)))
      .toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).advisers(p1), Vector.empty[CardId])
    assertEquals(end.game.current.commonCards.worldDeck.last, plain(0))
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0), hearth + 1)
    assertEquals(secretsOf(end), secretsOf(ready) + 3)
    assertEquals(Look(end).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the adviser and the secrets gained, covering the " +
      "Buried and gain lines"):
    val ready = staged().adviser(p1, plain(0))
      .tokens(plain(0), secrets = 1).ready
    val t = use(ready, power, source).toOption.get
    val done = answer(t, p1, BedOfRoots.decisionId, denizenRef(plain(0)))
      .toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${p1.value} buried ${plain(0).value} and gained 2 secrets.",
      covers = true)))

  test("a locked adviser may be buried"):
    val ready = staged().adviser(p1, insomnia).ready
    val t = use(ready, power, source).toOption.get
    val done = answer(t, p1, BedOfRoots.decisionId, denizenRef(insomnia))
      .toOption.get
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])

  test("with no faceup adviser, the cost stays paid and the line says so"):
    val ready = staged().adviser(p1, plain(1), facedown = true).ready
    val done = use(ready, power, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).favor(p1), 0)
    assertEquals(Look(after(done)).advisers(p1), Vector[CardId](plain(1)))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", s"${p1.value} had no faceup adviser.", covers = true)))

  test("it is unusable with less than 3 favor"):
    val broke = staged(favor = 2).adviser(p1, plain(0)).ready
    assert(!usableNow(broke).exists(_.power.id == BedOfRoots.id))
    assert(use(broke, power, source).isLeft)
