package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Unwrap.accepted

class MagicWaterskinSuite extends munit.FunSuite:
  import PowerFixture._

  private val skin = CatalogNames.relic("Magic Waterskin")
  private val source = DecisionOptionRef.Relic(skin)
  private val rules = ActPowerHarness.rules()
  /** p1's Act holding Magic Waterskin, with `supply` Supply. */
  private def stagedTable(supply: Int = 1): Table =
    Table.start.turn(actor, Phase.Act).relic(actor, skin)
      .supply(actor, supply)
  private def staged(supply: Int = 1): ReadyGame = stagedTable(supply).ready
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(MagicWaterskin.id), actor, Vector.empty, Vector(source))

  test("it buries itself at the bottom of the relic deck and gains 4 Supply"):
    // 1 Supply + 4 = 5.
    val used = Outcomes.ready(accepted(use(staged(supply = 1))).state)
    assert(!Look(used).player(actor).relics.exists(_.id == skin))
    assertEquals(used.game.current.commonCards.relicDeck.last, skin)
    assertEquals(Look(used).player(actor).board.supply, SupplyTrack(5))

  test("a secret on the relic returns to its holder facedown"):
    val ready = stagedTable().tokens(skin, secrets = 1).ready
    val used = Outcomes.ready(accepted(use(ready)).state)
    assertEquals(Look(used).player(actor).board.faceDownSecrets,
      Look(ready).player(actor).board.faceDownSecrets + 1)
    assertEquals(used.game.current.commonCards.relicDeck.last, skin)

  test("the gain is clamped at the track maximum"):
    val used = Outcomes.ready(accepted(use(staged(supply = 5))).state)
    assertEquals(Look(used).player(actor).board.supply, SupplyTrack(7))

  test("it writes the Supply it gained, as the track allowed"):
    assertEquals(NoteText.said(MagicWaterskin, accepted(use(staged())).events),
      Vector(NoteText.Said(NoteKey.Used, s"${actor.value} gained 4 Supply.",
        covers = false)))
    assertEquals(NoteText.said(MagicWaterskin,
      accepted(use(staged(supply = 5))).events).map(_.text),
      Vector(s"${actor.value} gained 2 Supply."))
