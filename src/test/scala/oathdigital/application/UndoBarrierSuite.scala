package oathdigital.application

import oathdigital.gameplay.walker.{DeltaMeaning, RollPayload,
  WalkerStepPayload, WalkerStepRecorded}
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.model._
import oathdigital.model.OathEvent.{OwnedRelicRevealed, SiteRelicsPeeked}
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Which closed command batches undo never takes back (undo design,
  * "Barriers"). Each case is one batch of one event. */
class UndoBarrierSuite extends munit.FunSuite:
  private val table = Table.start
  private val before = table.state
  private val relicCard: CardId = CatalogNames.card("Sticky Fire")
  private val relic: RelicId = CatalogNames.relic("Sticky Fire")
  private val peaks: SiteId = CatalogNames.site("Broken Peaks")

  private def step(payload: WalkerStepPayload, ops: CoreOperation*)
      : WalkerStepRecorded =
    WalkerStepRecorded("n", payload, ops.toVector, Vector.empty)
  private def delta(ops: CoreOperation*): WalkerStepRecorded =
    step(DeltaRecorded(DeltaMeaning.SupplySpent(p1, 0)), ops*)
  private def blocks(events: OathEvent*): Boolean =
    UndoBarrier.blocks(events.toVector, before, before)

  test("an ordinary batch is not a barrier"):
    assert(!blocks())
    assert(!blocks(delta(Flip(relicCard, Location.PlayArea(p1),
      Orientation.FaceDown))))

  test("a roll is a barrier"):
    assert(blocks(step(RollPayload(PoolKey("attack"), Vector.empty,
      automatic = true))))

  test("a shuffle, a peek, a reveal and a faceup flip are barriers"):
    assert(blocks(delta(Shuffle(SearchSource.WorldDeck))))
    assert(blocks(delta(Peek(p1, relicCard, Location.Site(peaks)))))
    assert(blocks(delta(Reveal(relicCard, Location.PlayArea(p1)))))
    assert(blocks(delta(Flip(relicCard, Location.PlayArea(p1),
      Orientation.FaceUp))))

  test("a legacy peek or reveal is a barrier"):
    assert(blocks(SiteRelicsPeeked(p1, peaks, Vector(relic))))
    assert(blocks(OwnedRelicRevealed(p1, relic)))

  test("a batch that hands the turn over is a barrier"):
    assert(UndoBarrier.blocks(Vector.empty, before,
      table.turn(p2, Phase.Wake).state))

  test("a batch that sets the game result is a barrier"):
    val won = OathState.Ready(table.update(_.updateCurrent(
      _.copy(result = Some(GameResult(p1))))).unchecked)
    assert(UndoBarrier.blocks(Vector.empty, before, won))

  test("a batch begun before the game started is a barrier"):
    assert(UndoBarrier.blocks(Vector.empty, OathState.NoGame, before))
