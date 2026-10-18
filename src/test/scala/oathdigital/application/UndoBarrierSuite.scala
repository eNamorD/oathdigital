package oathdigital.application

import oathdigital.gameplay.walker.{RollPayload, WalkerStepPayload,
  WalkerStepRecorded}
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Which closed command batches undo never takes back (undo design,
  * "Barriers"). Each case is one batch of one event. */
class UndoBarrierSuite extends munit.FunSuite:
  private val table = Table.start.turn(p1, Phase.Act)
  private val before = table.state
  private val relicCard: CardId = CatalogNames.card("Sticky Fire")
  private val peaks: SiteId = CatalogNames.site("Broken Peaks")

  private def step(payload: WalkerStepPayload, ops: CoreOperation*)
      : WalkerStepRecorded =
    WalkerStepRecorded("n", payload, ops.toVector, Vector.empty)

  /** A walker step recording a delta that applies `ops`. */
  private def deltaStep(ops: CoreOperation*): WalkerStepRecorded =
    step(DeltaRecorded, ops*)
  private def blocks(events: OathEvent*): Boolean =
    UndoBarrier.blocks(events.toVector, before, before)

  test("an ordinary batch is not a barrier"):
    assert(!blocks())
    assert(!blocks(deltaStep(Flip(relicCard, Location.PlayArea(p1),
      Orientation.FaceDown))))

  test("a roll is a barrier"):
    assert(blocks(step(RollPayload(PoolKey("attack"), Vector.empty,
      automatic = true))))

  test("a shuffle, a peek, a reveal and a faceup flip are barriers"):
    assert(blocks(deltaStep(Shuffle(SearchSource.WorldDeck))))
    assert(blocks(deltaStep(Peek(p1, relicCard, Location.Site(peaks)))))
    assert(blocks(deltaStep(Reveal(relicCard, Location.PlayArea(p1)))))
    assert(blocks(deltaStep(Flip(relicCard, Location.PlayArea(p1),
      Orientation.FaceUp))))

  test("a batch that hands the turn over is a barrier"):
    assert(UndoBarrier.blocks(Vector.empty, before,
      table.turn(p2, Phase.Wake).state))

  test("a batch that sets the game result is a barrier"):
    // Last-resort `update`: no builder step sets the game result, and only
    // this test needs one.
    val won = OathState.Ready(table.update(_.updateCurrent(
      _.copy(result = Some(GameResult(p1))))).unchecked)
    assert(UndoBarrier.blocks(Vector.empty, before, won))

  test("a batch begun before the game started is a barrier"):
    assert(UndoBarrier.blocks(Vector.empty, OathState.NoGame, before))
