package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, as, present}

class IvoryEyeSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val eye = CatalogNames.relic("Ivory Eye")
  private val source = DecisionOptionRef.Relic(eye)
  private val target = p2
  private val third = p3
  private val faith = VisionRules.Faith

  /** p1's Act: p1 holds the Eye faceup and the quiet table's 1 faceup
    * secret. Each player holds one facedown adviser: p1 Wrestlers, p2 Battle
    * Honors, p3 Rain Boots. */
  private def staged = Table.start
    .relic(p1, eye)
    .adviser(p1, "Wrestlers", facedown = true)
    .adviser(p2, "Battle Honors", facedown = true)
    .adviser(p3, "Rain Boots", facedown = true)
    .ready
  private def firstAdviser(ready: ReadyGame, owner: PlayerId): CardId =
    Look(ready).player(owner).advisers.head.id
  private def known(ready: ReadyGame, viewer: PlayerId): Vector[WorldCardId] =
    ready.knowledge.advisers.getOrElse(viewer, Vector.empty)
  private def facedown(ready: ReadyGame) = for
    p <- ready.game.current.players
    (adviser, slot) <- p.advisers.zipWithIndex
    if (adviser match {
      case d: DenizenState => d.orientation == Orientation.FaceDown
      case v: VisionState => v.orientation == Orientation.FaceDown })
  yield IvoryEye.optionFor(p.player, slot)
  private def peekAt(owner: PlayerId, slot: Int) =
    pick(IvoryEye.optionFor(owner, slot))

  test("it places a secret on the relic and offers every facedown adviser " +
      "of every player, and no faceup one"):
    val ready = Table.from(staged).adviser(target,
      CatalogNames.denizen("Elders"))
      .adviser(actor, faith, facedown = true).unchecked
    val t = accepted(use(ready, IvoryEye, source))
    assert(awaits(t, IvoryEye.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(eye), Tokens(0, 1))
    assertEquals(Look(after(t)).player(actor).board.faceUpSecrets, 0)
    assertEquals(offered(t, actor),
      Some(facedown(ready).map(o => o.kind -> o.wireId)))
    assert(!present(offered(t, actor), "the offered options").contains(
      IvoryEye.optionFor(target, 1).kind -> IvoryEye.optionFor(target, 1).wireId))
    assertEquals(facedown(ready).size, 4)

  test("its options are adviser slots, drawn as card backs for another " +
      "player's adviser"):
    val t = accepted(use(staged, IvoryEye, source))
    val options = present(queryOf(t, actor), "a parked query").offeredOptions
    assert(options.forall(_.kind == "adviser-slot"), options.toString)
    assert(options.filter(_.id.startsWith(s"${target.value}:"))
      .forall(_.card.exists(_.hidden)), options.toString)

  test("a peek records knowledge for the actor only and changes nothing else"):
    val t = accepted(use(staged, IvoryEye, source))
    val card = as[WorldCardId](firstAdviser(staged, target))
    val done = accepted(answer(t, actor, IvoryEye.decisionId, peekAt(target,
      0)))
    assert(known(after(done), actor).contains(card))
    assert(!known(after(done), target).contains(card))
    assert(!known(after(done), third).contains(card))
    assertEquals(Look(after(done)).player(target), Look(after(t))
      .player(target))
    assertEquals(replayed(staged, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("the peeked adviser is named to the actor and to nobody else"):
    val t = accepted(use(staged, IvoryEye, source))
    val card = firstAdviser(staged, target).value
    def named(board: oathdigital.protocol.projection.PlayerBoardProjection) =
      board.advisers.exists(c => c.cardId == card && !c.hidden)
    assert(!named(boardOf(t, actor, target)))
    val done = accepted(answer(t, actor, IvoryEye.decisionId, peekAt(target,
      0)))
    assert(named(boardOf(done, actor, target)))
    assert(!named(boardOf(done, third, target)))
    assert(named(boardOf(t, target, target)), "the owner always knows it")
    assert(!named(publicBoardOf(done, target)))

  test("a facedown Vision can be peeked"):
    val ready = Table.from(staged).adviser(target, faith, facedown = true)
      .unchecked
    val t = accepted(use(ready, IvoryEye, source))
    val slot = Look(ready).player(target).advisers.indexWhere(_.id == faith)
    val done = accepted(answer(t, actor, IvoryEye.decisionId, peekAt(target,
      slot)))
    assert(known(after(done), actor).contains(faith))

  test("only the acting player answers, with an offered adviser"):
    val ready = Table.from(staged).adviser(target,
      CatalogNames.denizen("Elders")).unchecked
    val t = accepted(use(ready, IvoryEye, source))
    assert(answer(t, target, IvoryEye.decisionId, peekAt(target, 0)).isLeft)
    assert(answer(t, actor, IvoryEye.decisionId, peekAt(target, 1)).isLeft)
    assert(answer(t, actor, IvoryEye.decisionId, peekAt(target, 7)).isLeft)

  test("with no facedown adviser the cost is paid and nothing else happens"):
    val ready = (others(quietTable) :+ actor).foldLeft(staged)(withoutAdvisers)
    val t = accepted(use(ready, IvoryEye, source))
    parked.assertNotParked(t.state)
    assertEquals(Look(after(t)).tokensOn(eye), Tokens(0, 1))

  test("it is unusable without a faceup secret"):
    val broke = Table.from(staged).secrets(actor, 0, 3).unchecked
    assertEquals(usableNow(broke), Vector.empty)
    assert(use(broke, IvoryEye, source).isLeft)

  test("it writes the adviser it peeked at in place of the generic line"):
    val t = accepted(use(staged, IvoryEye, source))
    val card = firstAdviser(staged, target)
    val done = accepted(answer(t, actor, IvoryEye.decisionId, peekAt(target,
      0)))
    assertEquals(NoteText.said(IvoryEye, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"${actor.value} peeked at ${card.value} in ${target.value}'s advisers.",
      covers = true)))
