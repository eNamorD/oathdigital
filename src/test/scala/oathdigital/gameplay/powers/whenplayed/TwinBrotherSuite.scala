package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PowerFixture, TargetsFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ProcedureWalker
import oathdigital.model._

class TwinBrotherSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture.{giveAdviser, others, updatePlayer}
  import WhenPlayedHarness._

  private val power = TwinBrother.forCatalog(catalog)
  private val card = power.cardId
  private val enemy = others(base)(0)
  private val archers = DenizenId("24")   // nomad, unrestricted
  private val faithful = DenizenId("28")  // nomad, locked
  private val charming = DenizenId("131") // hearth

  /** Twin Brother is the actor's faceup adviser; the enemy holds Horse
    * Archers faceup with 1 favor and 2 secrets on it. */
  private def staged = updatePlayer(giveAdviser(asAdviser(base, card), enemy,
    archers, Orientation.FaceUp), enemy)(p => p.copy(advisers = p.advisers.map {
      case d: DenizenState if d.id == archers => d.copy(tokens = Tokens(1, 2))
      case other => other }))

  private def choice(ready: ReadyGame) =
    val first = parked(play(ready, power, card))
    ProcedureWalker.parkedDecide(ready, hook(card), first.tree, powers(power))
      .get.query.asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)

  private def answer(ready: ReadyGame, ref: DecisionOptionRef) =
    val first = parked(play(ready, power, card))
    (first, finished(ProcedureWalker.resolve(ready, hook(card), first.tree,
      Answered(TwinBrother.decisionId, DecisionAnswer.ChooseOneAnswer(ref),
        actor), powers(power))))

  test("Twin Brother is in the default walker catalog"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))

  test("it offers other players' faceup unlocked nomad advisers, then keeping"):
    val ready = giveAdviser(giveAdviser(giveAdviser(staged, enemy, faithful,
      Orientation.FaceUp), enemy, charming, Orientation.FaceUp), actor,
      DenizenId("26"), Orientation.FaceUp)
    assertEquals(choice(ready), Vector(DecisionOptionRef.Denizen(archers),
      TwinBrother.keep))

  test("a facedown nomad adviser is not offered, and with none nothing is asked"):
    val ready = giveAdviser(asAdviser(base, card), enemy, archers,
      Orientation.FaceDown)
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)

  test("the swap moves both cards faceup, each with its own favor and secrets"):
    val (first, done) = answer(staged, DecisionOptionRef.Denizen(archers))
    val mine = player(done.treeless).advisers
    val theirs = player(done.treeless, enemy).advisers
    assert(mine.contains(DenizenState(archers, Orientation.FaceUp,
      Tokens(1, 2))), mine.toString)
    assert(theirs.contains(DenizenState(card, Orientation.FaceUp,
      Tokens.empty)), theirs.toString)
    assert(!mine.exists(_.id == card))
    assertEquals(replayed(staged, first.events ++ done.events), done.treeless)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("swapped", s"${actor.value} swapped it for " +
        s"${enemy.value}'s ${archers.value}.", covers = false)))

  test("keeping Twin Brother changes nothing and writes nothing"):
    val (_, done) = answer(staged, TwinBrother.keep)
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector.empty)

  test("an option that is not offered is rejected"):
    val first = parked(play(staged, power, card))
    assert(ProcedureWalker.resolve(staged, hook(card), first.tree, Answered(
      TwinBrother.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Denizen(charming)), actor), powers(power)).isLeft)
