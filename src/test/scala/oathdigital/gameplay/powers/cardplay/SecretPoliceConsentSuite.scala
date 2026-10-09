package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.{CardPlayProcedure, CardSlots}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.ConsentAskProjection
import oathdigital.testkit.{CatalogNames, PhasePowerHarness, PlacementPicks,
  Table}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Which faceup plays Secret Police makes askable (consent design, ruling 1
  * and slice 3): an enemy at a site the Police's ruler rules may ask that
  * ruler for consent to play a Vision faceup. */
class SecretPoliceConsentSuite extends munit.FunSuite:
  import PowerFixture.actor
  import VisionPlayFixture.{fromAdvisers, searched}

  private val police = CatalogNames.denizen("Secret Police")
  private val vow = CatalogNames.denizen("Vow of Obedience")
  private val other = Table.p2
  private val home = CatalogNames.site("Ancient City")
  private val faith = VisionRules.Faith
  private val placement = CardPlayProcedure.placementDecisionId(faith)
  private val spot = DecisionOptionRef.RevealedVision(actor)
  private val asksOther = Vector(ConsentAsk(other, Vector(SecretPolice.id)))

  /** A Search that will draw `vision`, with p1 at Ancient City beside the
    * Police; p2 rules Ancient City unless `arrange` says otherwise. */
  private def board(arrange: Table => Table = _.warbandsAt(home, other, 2),
      vision: VisionId = faith): ReadyGame =
    arrange(Table.from(SearchFixture.staged(Vector(vision), supply = 5))
      .pawn(actor, home).denizen(police, at = home)).unchecked

  /** `ready` remembering `answer` for the faceup Faith, this turn. */
  private def remembering(ready: ReadyGame, answer: ConsentAnswer)
      : ReadyGame =
    ready.copy(consent = ConsentState(
      answers = Map(ConsentKey(SecretPolice.id, actor, placement, spot) ->
        answer),
      turn = Some(ConsentTurn.of(ready))))

  private def faceup(from: OathTransition, ref: DecisionOptionRef = spot)(
      using munit.Location): DecisionOption =
    present(PlacementPicks.parkedOptions(from.state,
      WalkerPowerCatalog.default(catalog)).find(_.ref == ref),
      "the faceup play")

  private def assertNotedFaceup(option: DecisionOption)(
      using munit.Location): Unit = option match
    case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
      assertEquals(note.key, "no-faceup")
    case other => fail(s"expected a plain unavailable faceup play, got $other")

  private def askable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case _ => false

  test("an enemy's faceup Vision is askable of the Police's ruler"):
    faceup(searched(board(), faith)) match
      case DecisionOption.Unavailable(_, OptionReason.Askable(power, note,
          asks)) =>
        assertEquals(power, SecretPolice.id)
        assertEquals(note, SecretPolice.noFaceupConsent(
          PowerSourceRef.Card(police)))
        assertEquals(asks, asksOther)
      case other => fail(s"expected an askable faceup play, got $other")

  test("the player sees it askable, with the consent reason and whom to " +
      "ask"):
    val row = present(PhasePowerHarness.queryOf(searched(board(), faith),
      actor).toVector.flatMap(_.offeredOptions)
      .find(_.kind == "revealed-vision"), "the revealed Vision row")
    assertEquals(row.state, "askable")
    assertEquals(row.reason, Some("Secret Police: Cannot play a Vision " +
      "faceup without ruler's consent."))
    assertEquals(row.consent, Some(ConsentAskProjection(Vector("p2"),
      Vector("Secret Police"))))

  test("under bandit rule the faceup play keeps its ordinary reason: " +
      "bandits never consent"):
    assertNotedFaceup(faceup(searched(board(_.bandits(home, 2)), faith)))

  test("a Vow of Obedience the player holds keeps it unavailable, not " +
      "askable"):
    val vowed = board(_.warbandsAt(home, other, 2).adviser(actor, vow))
    assertNotedFaceup(faceup(searched(vowed, faith)))

  test("the Conspiracy's Play is askable too"):
    val drawn = searched(board(vision = VisionRules.Conspiracy),
      VisionRules.Conspiracy)
    assert(askable(faceup(drawn, CardSlots.play)))

  test("a facedown Vision played from the advisers is askable too"):
    val ready = Table.start.pawn(actor, home).denizen(police, at = home)
      .warbandsAt(home, other, 2).ready
    assert(askable(faceup(fromAdvisers(ready, faith))))

  test("a grant remembered for the placement opens the faceup play, and the " +
      "Vision is revealed"):
    val parked = searched(remembering(board(), ConsentAnswer.Granted), faith)
    assert(faceup(parked).available)
    val placed = accepted(SearchFixture.place(parked, faith, PlayedAs.Faceup))
    assertEquals(SearchFixture.after(placed).game.current.players
      .find(_.player == actor).flatMap(_.revealedVision).map(_.id),
      Some(faith))

  test("a refusal remembered this turn leaves the ordinary reason, and the " +
      "faceup play is refused"):
    val parked = searched(remembering(board(), ConsentAnswer.Refused), faith)
    assertNotedFaceup(faceup(parked))
    assert(SearchFixture.place(parked, faith, PlayedAs.Faceup).isLeft)

  test("the asks of the faceup play"):
    assertEquals(SearchFixture.rules.consentAsks(searched(board(), faith)
      .state, actor, placement, spot), Right(asksOther))
