package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class IvoryEyeSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val eye = RelicId("R16")
  private val source = DecisionOptionRef.Relic(eye)
  private val target = others(base)(0)
  private val third = others(base)(1)
  private val faith = VisionId("vision:vision-of-faith")

  private def staged = inPhase(withRelic(base, eye), Phase.Act)
  private def relicOf(ready: ReadyGame) = player(ready).relics
    .find(_.id == eye).get
  private def firstAdviser(ready: ReadyGame, owner: PlayerId): CardId =
    player(ready, owner).advisers.head.id
  private def known(ready: ReadyGame, viewer: PlayerId): Vector[WorldCardId] =
    ready.knowledge.advisers.getOrElse(viewer, Vector.empty)
  private def facedown(ready: ReadyGame) = for {
    p <- ready.game.current.players
    (adviser, slot) <- p.advisers.zipWithIndex
    if (adviser match {
      case d: DenizenState => d.orientation == Orientation.FaceDown
      case v: VisionState => v.orientation == Orientation.FaceDown })
  } yield IvoryEye.optionFor(p.player, slot)
  private def peekAt(owner: PlayerId, slot: Int) =
    pick(IvoryEye.optionFor(owner, slot))

  test("Ivory Eye is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(IvoryEye.id).isDefined)
  }

  test("it places a secret on the relic and offers every facedown adviser " +
      "of every player, and no faceup one") {
    val ready = giveVision(giveAdviser(staged, target, DenizenId("26"),
      Orientation.FaceUp), actor, faith, Orientation.FaceDown)
    val t = use(ready, IvoryEye, source).toOption.get
    assert(awaits(t, IvoryEye.decisionId), t.continue.toString)
    assertEquals(relicOf(after(t)).tokens, Tokens(0, 1))
    assertEquals(player(after(t)).board.faceUpSecrets, 0)
    assertEquals(offered(t, actor),
      Some(facedown(ready).map(o => o.kind -> o.wireId)))
    assert(!offered(t, actor).get.contains(
      IvoryEye.optionFor(target, 1).kind -> IvoryEye.optionFor(target, 1).wireId))
    assertEquals(facedown(ready).size, 4)
  }

  test("a peek records knowledge for the actor only and changes nothing else") {
    val t = use(staged, IvoryEye, source).toOption.get
    val card = firstAdviser(staged, target).asInstanceOf[WorldCardId]
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, 0))
      .toOption.get
    assert(known(after(done), actor).contains(card))
    assert(!known(after(done), target).contains(card))
    assert(!known(after(done), third).contains(card))
    assertEquals(player(after(done), target), player(after(t), target))
    assertEquals(replayed(staged, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))
  }

  test("the peeked adviser is named to the actor and to nobody else") {
    val t = use(staged, IvoryEye, source).toOption.get
    val card = firstAdviser(staged, target).value
    def named(board: oathdigital.protocol.projection.PlayerBoardProjection) =
      board.advisers.exists(c => c.cardId == card && !c.hidden)
    assert(!named(boardOf(t, actor, target)))
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, 0))
      .toOption.get
    assert(named(boardOf(done, actor, target)))
    assert(!named(boardOf(done, third, target)))
    assert(named(boardOf(t, target, target)), "the owner always knows it")
    assert(!named(publicBoardOf(done, target)))
  }

  test("a facedown Vision can be peeked") {
    val ready = giveVision(staged, target, faith, Orientation.FaceDown)
    val t = use(ready, IvoryEye, source).toOption.get
    val slot = player(ready, target).advisers.indexWhere(_.id == faith)
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, slot))
      .toOption.get
    assert(known(after(done), actor).contains(faith))
  }

  test("the actor's own facedown adviser is a legal target") {
    val t = use(staged, IvoryEye, source).toOption.get
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(actor, 0)).toOption
    assert(done.nonEmpty)
  }

  test("only the acting player answers, with an offered adviser") {
    val ready = giveAdviser(staged, target, DenizenId("26"), Orientation.FaceUp)
    val t = use(ready, IvoryEye, source).toOption.get
    assert(answer(t, target, IvoryEye.decisionId, peekAt(target, 0)).isLeft)
    assert(answer(t, actor, IvoryEye.decisionId, peekAt(target, 1)).isLeft)
    assert(answer(t, actor, IvoryEye.decisionId, peekAt(target, 7)).isLeft)
  }

  test("with no facedown adviser the cost is paid and nothing else happens") {
    val ready = (others(base) :+ actor).foldLeft(staged)(withoutAdvisers)
    val t = use(ready, IvoryEye, source).toOption.get
    assert(!t.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      t.continue.toString)
    assertEquals(relicOf(after(t)).tokens, Tokens(0, 1))
  }

  test("it is unusable without a faceup secret, or while the relic holds one") {
    val broke = withSecrets(staged, actor, 0, 3)
    assertEquals(usableNow(broke), Vector.empty)
    assert(use(broke, IvoryEye, source).isLeft)
    val t = use(staged, IvoryEye, source).toOption.get
    val done = answer(t, actor, IvoryEye.decisionId, peekAt(target, 0))
      .toOption.get
    assertEquals(usableNow(withSecrets(after(done), actor, 1, 0)), Vector.empty)
  }
}
