package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerOutcome
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.as

class TwinBrotherSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = TwinBrother.forCatalog(catalog)
  private val card = power.cardId
  private val played = hook(card)
  // Horse Archers and Faithful Friend are Nomad, Faithful Friend locked;
  // Charming Friend is Hearth.
  private val archers = CatalogNames.denizen("Horse Archers")
  private val faithful = CatalogNames.denizen("Faithful Friend")
  private val charming = CatalogNames.denizen("Charming Friend")

  /** Twin Brother is p1's faceup adviser; p2 holds Horse Archers faceup with
    * 1 favor and 2 secrets on it. */
  private val staged: Table = Table.start.adviser(p1, card)
    .adviser(p2, archers).tokens(archers, favor = 1, secrets = 2)

  private def choice(ready: ReadyGame): Vector[DecisionOptionRef] =
    val first = Outcomes.parked(play(ready, power, card))
    val query = question(ready, power, played, first.tree).query
    as[DecisionQuery.ChooseOne](query).options.map(_.ref)

  /** The walk parked on the choice, and the walk the answer finished. */
  private final case class Resolved(first: WalkerOutcome.Parked,
      done: WalkerOutcome.Finished)

  private def answer(ready: ReadyGame, ref: DecisionOptionRef): Resolved =
    val first = Outcomes.parked(play(ready, power, card))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      TwinBrother.decisionId, DecisionAnswer.ChooseOneAnswer(ref)))
    Resolved(first, done)

  test("Twin Brother is in the default walker catalog"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))

  test("it offers other players' faceup unlocked nomad advisers, then keeping"):
    // Faithful Friend is locked, Charming Friend is Hearth and Wrestlers is
    // Order. Elders is a Nomad adviser, but p1's own.
    val ready = staged.adviser(p2, faithful).adviser(p2, charming)
      .adviser(p2, "Wrestlers").adviser(p1, "Elders").ready
    assertEquals(choice(ready), Vector(DecisionOptionRef.Denizen(archers),
      TwinBrother.keep))

  test("a facedown nomad adviser is not offered, and with none nothing is " +
      "asked"):
    val ready = Table.start.adviser(p1, card)
      .adviser(p2, archers, facedown = true).ready
    val done = Outcomes.finished(play(ready, power, card))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)

  test("the swap moves both cards faceup, each with its own favor and secrets"):
    val ready = staged.ready
    val swap = answer(ready, DecisionOptionRef.Denizen(archers))
    val done = swap.done
    val mine = Look(done.treeless).player(p1).advisers
    val theirs = Look(done.treeless).player(p2).advisers
    assert(mine.contains(DenizenState(archers, Orientation.FaceUp,
      Tokens(1, 2))), mine.toString)
    assert(theirs.contains(DenizenState(card, Orientation.FaceUp,
      Tokens.empty)), theirs.toString)
    assert(!mine.exists(_.id == card))
    assertEquals(replayed(ready, swap.first.events ++ done.events),
      done.treeless)
    assertEquals(said(power, done.events),
      Vector(NoteText.Said("swapped",
        s"p1 swapped it for p2's ${archers.value}.", covers = false)))

  test("keeping Twin Brother changes nothing and writes nothing"):
    val done = answer(staged.ready, TwinBrother.keep).done
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events),
      Vector.empty)

  test("an option that is not offered is rejected"):
    val ready = staged.ready
    val first = Outcomes.parked(play(ready, power, card))
    assert(resume(ready, power, played, first.tree, TwinBrother.decisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(charming)))
      .isLeft)
