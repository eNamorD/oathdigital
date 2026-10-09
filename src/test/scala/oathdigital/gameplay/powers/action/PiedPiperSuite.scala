package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap
import oathdigital.testkit.Unwrap.{accepted, present}

class PiedPiperSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val piper = CatalogNames.denizen("Pied Piper")
  private val source = DecisionOptionRef.Denizen(piper)
  private val receiver = Table.p2
  private val plain = Vector("Threatening Roar", "Fae Merchant", "Wrestlers")
    .map(CatalogNames.denizen(_))
  /** A plain denizen in hand. */
  private val played = CatalogNames.denizen("Errand Boy")

  private def favorOf(ready: ReadyGame, id: PlayerId = actor) =
    Look(ready).player(id).board.favor
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))

  /** The actor's Act at Ancient City holding `held` as facedown advisers
    * after `first`, and `card` in hand. */
  private def playing(card: DenizenId, first: Table => Table,
      held: Vector[DenizenId]): ReadyGame =
    held.foldLeft(first(Table.start.turn(actor, Phase.Act)
      .pawn(actor, "Ancient City").hand(actor, card)))(
      _.adviser(actor, _, facedown = true)).ready

  /** The placement options of `card` from hand, with Pied Piper's power in
    * force, that play it `as`. */
  private def placementOptions(ready: ReadyGame, card: DenizenId,
      as: PlayedAs): Vector[DecisionOption] =
    val tree = accepted(CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand))
    val powers = WalkerPowers(Vector(PiedPiper))
    val parked = Unwrap.as[WalkerOutcome.Parked](accepted(
      ProcedureWalker.advance(ready, tree, None, powers))).tree
    Unwrap.as[DecisionQuery.ChooseOne](present(
      ProcedureWalker.parkedDecide(ready, tree, parked, powers),
      "the placement decision").query).options
      .filter(option => CardPlayProcedure.playedAs(option.ref).contains(as))

  private def adviserPlay(index: Int, faceup: Boolean): DecisionOption =
    DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(actor, index, faceup))

  private def replacingPlain(faceup: Boolean, from: Int)
      : Vector[DecisionOption] = plain.zipWithIndex.map((card, index) =>
    DecisionOption.Replacing(adviserPlay(index + from, faceup), card))

  /** The actor's Act at Ancient City holding Pied Piper faceup and one
    * secret. The receiver stands at Buried Giant, another site, with
    * `favor`. */
  private def staged(favor: Int = 3, secrets: Int = 1) =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .adviser(actor, piper).secrets(actor, secrets)
      .pawn(receiver, "Buried Giant").favor(receiver, favor)

  private def movedTo(ready: ReadyGame) =
    val t = accepted(use(ready, PiedPiper, source))
    (t, accepted(answer(t, actor, PiedPiper.decisionId, choose(receiver))))

  test("played faceup at the limit it takes a free slot; facedown it needs " +
      "Replace"):
    val ready = playing(piper, identity, plain)
    assertEquals(placementOptions(ready, piper, PlayedAs.Faceup),
      Vector(adviserPlay(3, faceup = true)))
    assertEquals(placementOptions(ready, piper, PlayedAs.Facedown),
      replacingPlain(faceup = false, from = 0))

  test("held faceup at the limit, it is shown unavailable for Replace"):
    val ready = playing(played, _.adviser(actor, piper), plain)
    assertEquals(placementOptions(ready, played, PlayedAs.Faceup),
      DecisionOption.Unavailable(DecisionOption.Replacing(
        adviserPlay(0, faceup = true), piper),
        OptionReason.Written(CardPlayRules.notCounted)) +:
        replacingPlain(faceup = true, from = 1))

  test("held facedown, it counts toward the limit"):
    val ready = playing(played, _.adviser(actor, piper, facedown = true),
      plain.take(2))
    assertEquals(placementOptions(ready, played, PlayedAs.Faceup),
      DecisionOption.Replacing(adviserPlay(0, faceup = true), piper) +:
        plain.take(2).zipWithIndex.map((card, index) =>
          DecisionOption.Replacing(adviserPlay(index + 1, faceup = true),
            card)))

  test("its ACTION places a secret on it and offers every other player"):
    val ready = staged().ready
    val t = accepted(use(ready, PiedPiper, source))
    assert(awaits(t, PiedPiper.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor).map(_.toSet), Some(
      ready.game.current.players.map(_.player).filter(_ != actor)
        .map(other => "player" -> other.value).toSet))
    assertEquals(Look(after(t)).tokensOn(piper), Tokens(0, 1))

  test("the chosen player receives it with its secret and gives two favor"):
    val ready = staged().ready
    val (t, done) = movedTo(ready)
    assertEquals(Look(after(done)).advisers(receiver), Vector[CardId](piper))
    assertEquals(Look(after(done)).advisers(actor), Vector.empty)
    assertEquals(Look(after(done)).tokensOn(piper), Tokens(0, 1))
    assertEquals(favorOf(after(done), receiver), 1)
    assertEquals(favorOf(after(done)), favorOf(ready) + 2)
    assertEquals(NoteText.said(PiedPiper, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"Moved to ${receiver.value}'s advisers " +
        "and took 2 favor from them.", covers = false)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("a receiver at the limit still receives it"):
    val ready = plain.foldLeft(staged())(_.adviser(receiver, _)).ready
    val (_, done) = movedTo(ready)
    assertEquals(Look(after(done)).advisers(receiver), plain :+ piper)

  test("it takes what favor the receiver has, and says when there is none"):
    val (_, short) = movedTo(staged(favor = 1).ready)
    assertEquals(favorOf(after(short), receiver), 0)
    val ready = staged(favor = 0).ready
    val (_, broke) = movedTo(ready)
    assertEquals(favorOf(after(broke)), favorOf(ready))
    assertEquals(NoteText.said(PiedPiper, broke.events), Vector(
      NoteText.Said("used.empty", s"Moved to ${receiver.value}'s advisers. " +
        s"${receiver.value} had no favor to take.", covers = false)))

  test("it is unusable without a secret to place"):
    val ready = staged(secrets = 0).ready
    assert(!usableNow(ready).exists(_.power.id == PiedPiper.id))
    assertEquals(use(ready, PiedPiper, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))
