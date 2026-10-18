package oathdigital.gameplay.campaign

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignNotes}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** What a Campaign's note builders read from the step they restate, with
  * no formatter in the way (procedures journal their own notes design,
  * slice 4). The sentences themselves are `ProcedureNoteLinesSuite`'s;
  * each assertion here names the number or the clause list.
  */
class CampaignNoteReadsSuite extends munit.FunSuite:
  /** `result` recorded on `ready`, as the battle records it. */
  private def withResult(ready: ReadyGame, result: CampaignResult)
      : ReadyGame =
    ready.updateCurrent(_.copy(lastCampaignResult = Some(result)))

  /** A Conquest of `sites` that p1 won against `loser`. */
  private def conquestWon(loser: PlayerId, sites: Vector[SiteId])
      : CampaignResult = CampaignResult(p1, CampaignKind.Conquest,
    CampaignDefender.Player(loser), targetSites = sites,
    raidTargets = Vector.empty, force = 4, attackFaces = Vector.empty,
    attackScore = 5, skullLosses = 0, sacrificed = 0,
    defenseFaces = Vector.empty, defenseScore = 1, attackerWins = true)

  /** A Raid on `loser`'s pawn that p1 won. */
  private def raidWon(loser: PlayerId): CampaignResult =
    CampaignResult(p1, CampaignKind.Raid, CampaignDefender.Player(loser),
      targetSites = Vector.empty,
      raidTargets = Vector(CampaignRaidTarget.Pawn(loser)), force = 4,
      attackFaces = Vector.empty, attackScore = 5, skullLosses = 0,
      sacrificed = 0, defenseFaces = Vector.empty, defenseScore = 1,
      attackerWins = true)

  /** The attack the sacrifice line names, for `count` warbands worth
    * `worth` each, over an attack already scored at two. */
  private def sacrificeAttack(worth: Option[Int], count: Int)(
      using munit.Location): Int =
    val faces = Vector[DieFace](AttackDieFace.OneSword,
      AttackDieFace.OneSword)
    val rolled = CampaignFixture.board(warbands = 4, supply = 4).ready
      .updateCurrent(current => current.copy(
        rollOutcomes = current.rollOutcomes.updated(CampaignIds.attackPool,
          RollOutcome(CampaignIds.attackPool, faces.size, faces,
            skulls = 0, score = 2)),
        rollPools = worth.fold(current.rollPools)(value =>
          current.rollPools.updated(CampaignIds.sacrificeWorth,
            DicePoolState(value)))))
    val answered = Vector(Answered(CampaignIds.sacrifice,
      DecisionAnswer.ChooseAmountAnswer(count), p1))
    CampaignNotes.sacrificedNote(NoteStates(rolled, None, answered))
      .flatMap(_.args.lift(1)).collect { case NoteArg.Number(n) => n }
      .getOrElse(fail("no sacrifice line"))

  test("the sacrifice line counts what a plan said each warband adds"):
    // Zealots records three; each warband adds one while the pool is
    // absent.
    assertEquals(sacrificeAttack(worth = None, count = 2), 4)
    assertEquals(sacrificeAttack(worth = Some(3), count = 2), 8)

  test("a Conquest defender's loss is net of the half that came back"):
    val site = CatalogNames.site("Broken Peaks")
    val seats = Table.start.pawn(p1, "Ancient City")
      .pawn(p2, "Broken Peaks")
    val result = conquestWon(p2, Vector(site))
    // Four of p2's warbands stood at the target; all four die and half
    // come back to p2's board, which is a loss of two.
    val before = withResult(seats.warbandsAt("Broken Peaks", p2, 4)
      .boardWarbands(p2, 0).ready, result)
    val after = withResult(seats.boardWarbands(p2, 2).ready, result)
    val note = CampaignNotes.lossNote(
      NoteStates(after, Some((before, after)), Vector.empty))
    assertEquals(note.map(_.key), Some(CampaignNotes.playerLost.name))
    assertEquals(note.map(_.args), Some(Vector[NoteArg](
      NoteArg.Player(p2), NoteArg.Number(2))))

  test("a loss counts two sites that held the same number as two"):
    val result = CampaignResult(p1, CampaignKind.Conquest,
      CampaignDefender.Bandits,
      targetSites = Vector(CatalogNames.site("Broken Peaks"),
        CatalogNames.site("Buried Giant")),
      raidTargets = Vector.empty, force = 6, attackFaces = Vector.empty,
      attackScore = 7, skullLosses = 0, sacrificed = 0,
      defenseFaces = Vector.empty, defenseScore = 1, attackerWins = true)
    val seats = Table.start.pawn(p1, "Ancient City")
    val held = seats.bandits("Broken Peaks", 2).bandits("Buried Giant", 2)
    val before = withResult(held.ready, result)
    val after = withResult(seats.ready, result)
    val note = CampaignNotes.lossNote(
      NoteStates(after, Some((before, after)), Vector.empty))
    assertEquals(note.map(_.key), Some(CampaignNotes.banditsLost.name))
    // Four, not two: the two sites held the same number, and a count read
    // over a set of sites would have collapsed them into one.
    assertEquals(note.map(_.args),
      Some(Vector[NoteArg](NoteArg.Number(4))))

  test("a defeated Raid defender's clauses read the burn, the discards " +
      "and the relics set aside"):
    val adviser = CatalogNames.denizen("Birdsong")
    val relic = CatalogNames.relic("Wine of Welcome")
    val held = Table.start.pawn(p1, "Ancient City").pawn(p2, "Ancient City")
      .adviser(p2, adviser, facedown = true)
      .relic(p2, relic, facedown = true).favor(p2, 2)
    val result = raidWon(p2)
    val before = withResult(held.ready, result)
    // The consequences, applied to the same board so that no card is
    // counted twice: the facedown adviser to a discard pile, the facedown
    // relic set aside, half the favor burnt.
    val after = withResult(held.favor(p2, 1).update(_.updateCurrent(current =>
      current.copy(
        players = current.players.map(state =>
          if state.player == p2 then
            state.copy(advisers = Vector.empty, relics = Vector.empty)
          else state),
        commonCards = current.commonCards.copy(regionalDiscards =
          current.commonCards.regionalDiscards.updated(Region.Provinces,
            Vector(adviser))),
        setAsideRelics = Vector(relic)))).ready, result)
    val note = CampaignNotes.consequenceNote(
      NoteStates(after, Some((before, after)), Vector.empty))
    assertEquals(note.map(_.args), Some(Vector[NoteArg](
      NoteArg.Player(p2), NoteArg.Clauses(Vector(
        NoteClause.BurnedFavor(1),
        NoteClause.Discarded(Vector(adviser)),
        NoteClause.SetAside(Vector(relic)))))))
