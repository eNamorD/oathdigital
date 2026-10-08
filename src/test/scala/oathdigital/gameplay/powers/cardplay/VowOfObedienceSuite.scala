package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, PowerRules, Refusals, Table}
import oathdigital.testkit.Unwrap.accepted

class VowOfObedienceSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val vow = CatalogNames.denizen("Vow of Obedience")
  private val other = Table.p2

  private def holding(ready: ReadyGame, owner: PlayerId = actor,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    Table.from(ready)
      .adviser(owner, vow, facedown = orientation == Orientation.FaceDown)
      .unchecked

  private def search(vision: VisionId, arrange: ReadyGame => ReadyGame)
      : OathTransition =
    val ready = arrange(SearchFixture.staged(Vector(vision), supply = 5))
    searched(ready, vision)

  private def hidden(from: OathTransition): Vector[NoteText.Said] =
    val power = VowOfObedience
    NoteText.said(power.id, power.noteKeys, from.events)

  private val restRules = PowerRules.withWalkerAndPhasePowers

  private val restParked = PowerRules.parkedUnderWalkerAndPhasePowers

  private val use = ActionRef.UsePower(VowOfObedience.id)

  private val source = DecisionOptionRef.Denizen(vow)

  /** The Rest phase, the actor holding a faceup Vow, with exactly these banks
    * holding favor.
    */
  private def resting(stocked: Map[Suit, Int]): ReadyGame =
    Suit.all.foldLeft(Table.start.turn(actor, Phase.Rest).adviser(actor, vow))(
      (table, suit) => table.bankFavor(suit, stocked.getOrElse(suit, 0))).ready

  private def rest(ready: ReadyGame): OathTransition = accepted(
    restRules.startWalker(Ready(ready), use, actor, Vector.empty,
      Vector(source)))

  test("a faceup holder's Search does not offer a Vision faceup, and " +
      "refuses it"):
    val parkedAt = search(VisionRules.Faith, holding(_))
    assert(!offered(parkedAt).contains(PlayedAs.Faceup))
    assert(offered(parkedAt).contains(PlayedAs.Discarded))
    assert(offered(parkedAt).contains(PlayedAs.Facedown))
    assertEquals(SearchFixture.place(parkedAt, VisionRules.Faith,
      PlayedAs.Faceup).left.toOption,
      Some(Refusals.notOffered(
        CardPlayProcedure.placementDecisionId(VisionRules.Faith))))

  test("the holder cannot play a facedown Vision faceup either"):
    val parkedAt = fromAdvisers(holding(inPhase(quietTable, Phase.Act)),
      VisionRules.Faith)
    assert(!offered(parkedAt).contains(PlayedAs.Faceup))
    assert(offered(parkedAt).contains(PlayedAs.Discarded))

  test("the Conspiracy is a Vision, so it is forbidden too"):
    val parkedAt = search(VisionRules.Conspiracy, holding(_))
    assert(!offered(parkedAt).contains(PlayedAs.Faceup))

  test("the hidden faceup placement writes no line: its reason is on hover"):
    assertEquals(hidden(search(VisionRules.Faith, holding(_))), Vector.empty)
    assertEquals(hidden(fromAdvisers(holding(inPhase(quietTable, Phase.Act)),
      VisionRules.Faith)), Vector.empty)

  test("a Vow that forbids nothing writes nothing"):
    assertEquals(hidden(search(VisionRules.Faith, holding(_,
      orientation = Orientation.FaceDown))), Vector.empty)

  test("a facedown Vow, or another player's faceup Vow, forbids nothing"):
    assert(offered(search(VisionRules.Faith, holding(_,
      orientation = Orientation.FaceDown))).contains(PlayedAs.Faceup))
    assert(offered(search(VisionRules.Faith, holding(_, owner = other)))
      .contains(PlayedAs.Faceup))

  // ---- REST: Take a favor from any one favor bank ----

  test("REST: several stocked banks ask which one to take a favor from"):
    val ready = resting(Map(Suit.Arcane -> 3, Suit.Order -> 3))
    val choice = VowOfObedience.choiceDecisionId(ready, actor)
    val started = rest(ready)
    restParked.assertParked(started.state, use, choice, actor)
    val taken = accepted(restRules.resolveWalker(started.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order))))
    val after = SearchFixture.after(taken)
    assertEquals(after.banks.favor(Suit.Order), 2)
    assertEquals(after.banks.favor(Suit.Arcane), 3)
    assertEquals(Look(after).favor(actor), Look(ready).favor(actor) + 1)

  test("REST: one stocked bank is taken without asking"):
    val ready = resting(Map(Suit.Hearth -> 2))
    val after = SearchFixture.after(rest(ready))
    assertEquals(after.banks.favor(Suit.Hearth), 1)
    assertEquals(Look(after).favor(actor), Look(ready).favor(actor) + 1)

  test("REST: with every bank empty the power is not offered"):
    assert(!PhasePowerProcedure.usable(catalog, resting(Map.empty), actor,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .exists(_.power.id == VowOfObedience.id))

  test("REST: the take is written as its own line, covering the generic one"):
    val done = rest(resting(Map(Suit.Hearth -> 2)))
    assertEquals(NoteText.said(VowOfObedience, done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from the Hearth bank.", covers = true)))
