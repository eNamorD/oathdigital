package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Unwrap.accepted

class MarbleFountainsSuite extends munit.FunSuite:
  import PowerFixture._

  private val fountains = CatalogNames.edifice("Marble Fountains")
  private val source = DecisionOptionRef.Edifice(fountains)
  private val rules = ActPowerHarness.rules()
  // The actor's site on the quiet table, and another site.
  private val home = CatalogNames.site("Ancient City")
  private val far = CatalogNames.site("Broken Peaks")

  /** The actor in Wake with 1 Supply, so a refresh shows. The edifice is at
    * the actor's site, or at `far`, which the actor rules with one warband,
    * so that in the second case only the pawn condition fails.
    */
  private def staged(side: EdificeSide = EdificeSide.Intact,
      pawnAtEdifice: Boolean = true): ReadyGame =
    val waking = Table.start.turn(actor, Phase.Wake).supply(actor, 1)
    if pawnAtEdifice then waking.edifice(fountains, side, at = home).ready
    else waking.edifice(fountains, side, at = far).warbandsAt(far, actor, 1)
      .ready
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(MarbleFountains.id), actor, Vector.empty, Vector(source))

  test("it refreshes Supply to the maximum when the pawn is at the site"):
    val used = Outcomes.ready(accepted(use(staged())).state)
    // From 1 Supply to the maximum of 7.
    assertEquals(Look(used).supply(actor), 7)

  test("it is once per turn"):
    val first = Outcomes.ready(accepted(use(staged())).state)
    val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(fountains),
      MarbleFountains.id)
    assert(first.game.current.turn.usedPowers.contains(used))
    // It was Wake's only option, so Wake ended with it: put the turn back in
    // Wake, its uses kept, to try again.
    val again = Table.from(first).backInPhase(Phase.Wake).unchecked
    assertEquals(use(again).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))

  test("it is unusable when the pawn is at another site"):
    val away = staged(pawnAtEdifice = false)
    assert(PowerAccess.locate(away, actor, fountains).isDefined,
      "the edifice must be reachable, or this test proves nothing")
    assertEquals(PhasePowerProcedure.usable(catalog, away, actor,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty), Vector.empty)
    assert(use(away).isLeft)

  test("a ruined Marble Fountains offers nothing"):
    val ruined = staged(EdificeSide.Ruined)
    assertEquals(PhasePowerProcedure.usable(catalog, ruined, actor,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty), Vector.empty)

  test("it writes the Supply it refreshed to"):
    val done = accepted(use(staged()))
    assertEquals(NoteText.said(MarbleFountains, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value}'s Supply refreshed to 7.",
        covers = false)))
