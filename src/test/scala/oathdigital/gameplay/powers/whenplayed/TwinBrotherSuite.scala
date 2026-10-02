package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ProcedureWalker
import oathdigital.model._
import oathdigital.testkit.{Look, PhasePowerHarness, Table}

class TwinBrotherSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness.others
  import WhenPlayedHarness._

  private val power = TwinBrother.forCatalog(catalog)
  private val card = power.cardId
  private val enemy = others(quietTable)(0)
  private val archers = DenizenId("24")   // nomad, unrestricted
  private val faithful = DenizenId("28")  // nomad, locked
  private val charming = DenizenId("131") // hearth

  /** Twin Brother is the actor's faceup adviser; the enemy holds Horse
    * Archers faceup with 1 favor and 2 secrets on it. */
  private def staged = Table.from(asAdviser(quietTable, card))
    .adviser(enemy, archers).tokens(archers, favor = 1, secrets = 2).unchecked

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
    val ready = Table.from(staged).adviser(enemy, faithful).adviser(enemy,
      charming).adviser(actor, DenizenId("26")).unchecked
    assertEquals(choice(ready), Vector(DecisionOptionRef.Denizen(archers),
      TwinBrother.keep))

  test("a facedown nomad adviser is not offered, and with none nothing is asked"):
    val ready = Table.from(asAdviser(quietTable, card)).adviser(enemy, archers,
      facedown = true).unchecked
    val done = finished(play(ready, power, card))
    assertEquals(recorded(done.events), Vector.empty)

  test("the swap moves both cards faceup, each with its own favor and secrets"):
    val (first, done) = answer(staged, DecisionOptionRef.Denizen(archers))
    val mine = Look(done.treeless).player(actor).advisers
    val theirs = Look(done.treeless).player(enemy).advisers
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
