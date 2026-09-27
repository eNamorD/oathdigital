package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{CardStaging, NoteText, PhasePowerCatalog,
  PowerFixture,
  PowerImplementationStatus, SearchFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.OathState.Ready

class VowOfObedienceSuite extends munit.FunSuite:
  import PowerFixture._
  import VisionPlayFixture._

  private val vow = DenizenId("121")
  private val other = TargetsFixture.others(base).head

  private def holding(ready: ReadyGame, owner: PlayerId = actor,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    TargetsFixture.giveAdviser(CardStaging.without(ready, vow), owner, vow,
      orientation)

  private def search(vision: VisionId, arrange: ReadyGame => ReadyGame)
      : OathTransition =
    val ready = arrange(SearchFixture.staged(Vector(vision)))
    searched(ready, vision)

  test("Vow of Obedience is registered as a walker rule and a REST power, " +
      "and is implemented"):
    val power = VowOfObedience.forCatalog(catalog).get
    assertEquals(power.cardId, vow)
    assertEquals(power.resolution, PowerResolution.Automatic)
    assert(WalkerPowerCatalog.default(catalog).powers.exists(
      _.id == VowOfObedience.id))
    assert(PhasePowerCatalog.default(catalog).find(VowOfObedience.id).nonEmpty)
    assert(PowerImplementationStatus.implemented(catalog)(VowOfObedience.id))

  test("a faceup holder's Search does not offer a Vision faceup, and refuses it"):
    val parkedAt = search(VisionRules.Faith, holding(_))
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))
    assert(offered(parkedAt).contains("adviser-facedown"))
    assert(SearchFixture.place(parkedAt, VisionRules.Faith,
      "adviser-faceup").isLeft)

  test("the holder cannot play a facedown Vision faceup either"):
    val parkedAt = fromAdvisers(holding(inPhase(base, Phase.Act)),
      VisionRules.Faith)
    assert(!offered(parkedAt).contains("adviser-faceup"))
    assert(offered(parkedAt).contains("discard"))

  test("the Conspiracy is a Vision, so it is forbidden too"):
    val parkedAt = search(VisionRules.Conspiracy, holding(_))
    assert(!offered(parkedAt).contains("adviser-faceup"))

  test("a facedown Vow, or another player's faceup Vow, forbids nothing"):
    assert(offered(search(VisionRules.Faith, holding(_,
      orientation = Orientation.FaceDown))).contains("adviser-faceup"))
    assert(offered(search(VisionRules.Faith, holding(_, owner = other)))
      .contains("adviser-faceup"))

  // ---- REST: Take a favor from any one favor bank ----

  private val restRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val restParked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))
  private val use = ActionRef.UsePower(VowOfObedience.id)
  private val source = DecisionOptionRef.Denizen(vow)

  /** The Rest phase, the actor holding a faceup Vow, with exactly these banks
    * holding favor.
    */
  private def resting(stocked: Map[Suit, Int]): ReadyGame =
    val ready = inPhase(holding(base), Phase.Rest)
    ready.copy(banks = ready.banks.copy(favor =
      Suit.all.map(suit => suit -> stocked.getOrElse(suit, 0)).toMap))

  private def rest(ready: ReadyGame): OathTransition = restRules.startWalker(
    Ready(ready), use, actor, Vector.empty, Vector(source)).fold(
    error => throw new AssertionError(error.toString), identity)

  test("REST: several stocked banks ask which one to take a favor from"):
    val ready = resting(Map(Suit.Arcane -> 3, Suit.Order -> 3))
    val choice = VowOfObedience.choiceDecisionId(ready, actor)
    val started = rest(ready)
    restParked.assertParked(started.state, use, choice, actor)
    val taken = restRules.resolveWalker(started.state, actor, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order)))
      .toOption.get
    val after = SearchFixture.after(taken)
    assertEquals(after.banks.favor(Suit.Order), 2)
    assertEquals(after.banks.favor(Suit.Arcane), 3)
    assertEquals(player(after).board.favor, player(ready).board.favor + 1)

  test("REST: one stocked bank is taken without asking"):
    val ready = resting(Map(Suit.Hearth -> 2))
    val after = SearchFixture.after(rest(ready))
    assertEquals(after.banks.favor(Suit.Hearth), 1)
    assertEquals(player(after).board.favor, player(ready).board.favor + 1)

  test("REST: with every bank empty the power is not offered"):
    assert(!PhasePowerProcedure.usable(catalog, resting(Map.empty), actor,
      PhasePowerCatalog.default(catalog)).exists(_.power.id == VowOfObedience.id))

  test("REST: the take is written as its own line, covering the generic one"):
    val done = rest(resting(Map(Suit.Hearth -> 2)))
    assertEquals(NoteText.said(VowOfObedience.forCatalog(catalog).get, done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} took 1 favor from the Hearth bank.", covers = true)))
