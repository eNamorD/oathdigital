package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, ParkedNode,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Ward of Silence: X secrets placed on the card, and X of a player's
  * faceup secrets flipped facedown. */
class WardOfSilenceSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val ward = CatalogNames.denizen("Ward of Silence")
  private val source = DecisionOptionRef.Denizen(ward)

  /** p1's Act at Ancient City holding Ward of Silence as an adviser and
    * `own` faceup secrets. p2 stands there with `targetFaceUp` faceup and 1
    * facedown secret; p3 stands there with 2 facedown secrets only. */
  private def wardHeldWithP2AndP3AtTheSite(own: Int = 3,
      targetFaceUp: Int = 2): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, ward).secrets(p1, own)
      .pawn(p2, "Ancient City").secrets(p2, targetFaceUp, 1)
      .pawn(p3, "Ancient City").secrets(p3, 0, 2).ready

  /** The options of the decision the use is parked on. */
  private def parkedOptions(t: OathTransition): Vector[DecisionOption] =
    ParkedNode.of(t.state, catalog,
      phasePowers = PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        decide.query match
          case one: DecisionQuery.ChooseOne => one.options
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a parked decision, got $other")

  /** Why the Act menu shows Ward of Silence unavailable, if it does. */
  private def menuReason(ready: ReadyGame): Option[OptionReason] =
    PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .map(offer => MenuPlay.withoutBand(offer.option)).collectFirst {
        case DecisionOption.Unavailable(shown, reason)
            if shown.ref == source => reason
      }

  private def secretsOf(ready: ReadyGame, id: PlayerId): (Int, Int) =
    (Look(ready).faceUpSecrets(id), Look(ready).faceDownSecrets(id))

  /** `ready`'s Ward of Silence used and aimed at p2, parked on X. */
  private def wardAimedAtP2(ready: ReadyGame): OathTransition =
    val t = accepted(use(ready, WardOfSilence, source))
    accepted(answer(t, p1, WardOfSilence.targetDecisionId,
      pick(DecisionOptionRef.Player(p2))))

  test("it offers the players at the site, one with no faceup secret " +
      "unavailable, and places nothing yet"):
    val t = accepted(use(wardHeldWithP2AndP3AtTheSite(), WardOfSilence,
      source))
    assert(awaits(t, WardOfSilence.targetDecisionId))
    assertEquals(parkedOptions(t), Vector[DecisionOption](
      DecisionOption.Player(DecisionOptionRef.Player(p2)),
      DecisionOption.Unavailable(
        DecisionOption.Player(DecisionOptionRef.Player(p3)),
        OptionReason.Written(WardOfSilence.noFaceupSecrets))))
    assertEquals(Look(after(t)).tokensOn(ward), Tokens(0, 0))

  test("X secrets go on the card as X of the target's faceup secrets " +
      "flip facedown"):
    val ready = wardHeldWithP2AndP3AtTheSite()
    val started = accepted(use(ready, WardOfSilence, source))
    val chose = accepted(answer(started, p1, WardOfSilence.targetDecisionId,
      pick(DecisionOptionRef.Player(p2))))
    assert(awaits(chose, WardOfSilence.amountDecisionId))
    val done = accepted(answer(chose, p1, WardOfSilence.amountDecisionId,
      ChooseAmountAnswer(2)))
    val end = after(done)
    assertEquals(Look(end).tokensOn(ward), Tokens(0, 2))
    assertEquals(secretsOf(end, p1), (1, 0))
    assertEquals(secretsOf(end, p2), (0, 3))
    val events = started.events ++ chose.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)

  test("X is bounded by the smaller of the two faceup counts"):
    // The target's two faceup secrets bound X under the user's three.
    val byTarget = wardAimedAtP2(wardHeldWithP2AndP3AtTheSite())
    assertEquals(answer(byTarget, p1, WardOfSilence.amountDecisionId,
      ChooseAmountAnswer(3)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${WardOfSilence.amountDecisionId} amount 3 is " +
          "outside 1..2")))
    // The user's one faceup secret bounds X under the target's two.
    val byUser = wardAimedAtP2(wardHeldWithP2AndP3AtTheSite(own = 1))
    assertEquals(answer(byUser, p1, WardOfSilence.amountDecisionId,
      ChooseAmountAnswer(2)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${WardOfSilence.amountDecisionId} amount 2 is " +
          "outside 1..1")))

  test("its line names the target and the secrets flipped"):
    val aimed = wardAimedAtP2(wardHeldWithP2AndP3AtTheSite())
    val done = accepted(answer(aimed, p1, WardOfSilence.amountDecisionId,
      ChooseAmountAnswer(2)))
    assertEquals(NoteText.said(WardOfSilence, done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"Flipped 2 secrets on ${p2.value}'s board facedown.",
        covers = false)))

  test("it is unavailable without a faceup secret of its own, or while " +
      "its card holds a secret"):
    val unusable = Vector(
      wardHeldWithP2AndP3AtTheSite(own = 0),
      Table.from(wardHeldWithP2AndP3AtTheSite()).tokens(ward, secrets = 1)
        .ready)
    unusable.foreach { ready =>
      assert(!usableNow(ready).exists(_.power.id == WardOfSilence.id))
      assertEquals(menuReason(ready),
        Some(OptionReason.Written("Nothing to do now")))
    }

  test("with nobody at the site holding a faceup secret, the menu says no " +
      "valid enemy pawn"):
    assertEquals(menuReason(wardHeldWithP2AndP3AtTheSite(targetFaceUp = 0)),
      Some(OptionReason.Written(PawnMoves.noValidEnemyPawn)))
