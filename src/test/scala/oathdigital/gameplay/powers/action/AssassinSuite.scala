package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class AssassinSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val power = Assassin.forCatalog(catalog)
  private val assassin = CatalogNames.denizen("Assassin")
  private val source = DecisionOptionRef.Denizen(assassin)
  private val home = CatalogNames.site("Ancient City")
  // Denizens whose powers these tests do not use.
  private val elders = CatalogNames.denizen("Elders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  /** Locked and adviser-only. */
  private val insomnia = CatalogNames.denizen("Insomnia")
  /** The automatic production powers, as a command with no modifier walks. */
  private val automaticPowers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  /** p1's Act at Ancient City holding Assassin faceup, with the quiet
    * table's 1 favor to pay it. p2 stands there too, holding Land Warden
    * faceup. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).adviser(p1, assassin)
    .pawn(p2, at = home).adviser(p2, landWarden)

  /** The walk Assassin started and the walk after `card` was chosen. */
  private case class Chosen(started: OathTransition, answered: OathTransition):
    def events: Vector[OathEvent] = started.events ++ answered.events

  private def chooseAdviser(ready: ReadyGame, card: DenizenId): Chosen =
    val started = accepted(use(ready, power, source))
    Chosen(started, accepted(answer(started, p1, Assassin.decisionId,
      pick(DecisionOptionRef.Denizen(card)))))

  /** The faceup placement options of `card` from p1's hand, under the
    * production powers. */
  private def faceupPlacements(ready: ReadyGame, card: DenizenId)
      : Vector[DecisionOption] =
    val tree = PlacementFixture.build(ready, p1, card)
    PlacementFixture.placementOptions(ready, tree,
      PlacementFixture.park(ready, tree, automaticPowers), automaticPowers)
      .filter(option =>
        CardPlayProcedure.playedAs(option.ref).contains(PlayedAs.Faceup))

  test("it places a favor and offers the faceup advisers of players at the " +
      "site, the actor's own included, not locked, facedown or elsewhere"):
    val ready = staged.adviser(p1, elders)
      .adviser(p2, wildMounts, facedown = true).adviser(p2, insomnia)
      .adviser(p3, errandBoy).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, Assassin.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> elders.value,
      "denizen" -> landWarden.value)))
    assertEquals(Look(after(t)).tokensOn(assassin), Tokens(1, 0))

  test("the adviser is discarded to the next region's pile with its returns"):
    val ready = staged.tokens(landWarden, favor = 1, secrets = 1).ready
    val hearth = Look(ready).bankFavor(Suit.Hearth)
    val walked = chooseAdviser(ready, landWarden)
    assert(backToActing(walked.answered))
    val end = Look(after(walked.answered))
    assertEquals(end.advisers(p2), Vector.empty[CardId])
    assertEquals(end.discardAfter(home).lastOption, Some(landWarden))
    assertEquals(end.bankFavor(Suit.Hearth), hearth + 1)
    assertEquals(end.faceDownSecrets(p1), Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, walked.events), Right(walked.answered.state))
    assertWireRoundTrips(walked.events)

  test("its line names the card and its owner, covering the Discarded line"):
    val done = chooseAdviser(staged.ready, landWarden).answered
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"Discarded ${landWarden.value} from ${p2.value}'s advisers.",
      covers = true)))

  test("with only the locked Assassin to discard it asks nothing, and the " +
      "line says so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .adviser(p1, assassin).ready
    val done = accepted(use(ready, power, source))
    assert(backToActing(done))
    assertEquals(Look(after(done)).advisers(p1), Vector[CardId](assassin))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No faceup adviser could be discarded.", covers = true)))

  test("its holder may have only two advisers: a third faceup adviser " +
      "replaces one, and the locked Assassin cannot go"):
    val ready = Table.start.hand(p1, homesteaders).adviser(p1, assassin)
      .adviser(p1, landWarden, facedown = true).ready
    val faceup = faceupPlacements(ready, homesteaders)
    assertEquals(faceup.filter(_.available).map(PlacementPicks.replaces),
      Vector(Some(landWarden)))
    assertEquals(faceup.filterNot(_.available).map(PlacementPicks.replaces),
      Vector(Some(assassin)))
