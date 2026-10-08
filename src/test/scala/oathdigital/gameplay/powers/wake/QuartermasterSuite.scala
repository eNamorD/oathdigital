package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Refusals, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class QuartermasterSuite extends munit.FunSuite:
  private val quartermaster = CatalogNames.denizen("Quartermaster")
  private val source = DecisionOptionRef.Denizen(quartermaster)
  private val far = CatalogNames.site("Deep Woods")
  private val rules = ActPowerHarness.rules()

  /** p1's Wake with `supply` Supply and Quartermaster at Deep Woods. p1 rules
    * Deep Woods with one warband unless `ruled` is false. p1's pawn stays at
    * Ancient City unless `pawnThere`. */
  private def staged(supply: Int, ruled: Boolean = true,
      pawnThere: Boolean = false): ReadyGame =
    val table = Table.start.turn(p1, Phase.Wake).supply(p1, supply)
      .denizen(quartermaster, at = far)
    val held = if ruled then table.warbandsAt(far, p1, 1) else table
    (if pawnThere then held.pawn(p1, far) else held).ready

  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(Quartermaster.id), p1, Vector.empty, Vector(source))

  private def offered(ready: ReadyGame): Boolean = PhasePowerProcedure.usable(
    catalog, ready, p1, PhasePowerCatalog.default(catalog), WalkerPowers.empty)
    .exists(_.power.id == Quartermaster.id)

  test("its site's ruler gains 1 Supply, wherever their pawn is"):
    val ready = staged(supply = 3)
    assert(offered(ready))
    // 3, plus the 1 Quartermaster gives.
    assertEquals(Look(accepted(use(ready)).state).supply(p1), 4)

  test("it is once per turn"):
    val first = Outcomes.ready(accepted(use(staged(supply = 3))).state)
    val ref = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(quartermaster),
      Quartermaster.id)
    assert(first.game.current.turn.usedPowers.contains(ref))
    // The use may end Wake when it was Wake's only option: put the turn back
    // in Wake, its uses kept, to try again.
    val again = Table.from(first).backInPhase(Phase.Wake).unchecked
    assertEquals(use(again).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(ref)))

  test("a player who does not rule its site cannot use it, even with the " +
      "pawn there"):
    val ready = staged(supply = 3, ruled = false, pawnThere = true)
    assert(!offered(ready))
    assertEquals(use(ready).left.toOption,
      Some(Refusals.nothingToDo(Quartermaster.id, source)))

  test("its line names the Supply gained"):
    assertEquals(NoteText.said(Quartermaster,
      accepted(use(staged(supply = 3))).events),
      Vector(NoteText.Said(NoteKey.Used, s"${p1.value} gained 1 Supply.",
        covers = false)))

  test("on a full Supply track it gains nothing and writes no line"):
    // 7 is the top of the track.
    val done = accepted(use(staged(supply = 7)))
    assertEquals(Look(done.state).supply(p1), 7)
    assertEquals(NoteText.said(Quartermaster, done.events), Vector.empty)
