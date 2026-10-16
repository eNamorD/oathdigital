package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.AppendedAutomaticUses

class VowOfWanderingSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Wandering")
  private val beastkin = CatalogNames.denizen("Vow of Beastkin")
  private val home = CatalogNames.site("Ancient City")
  /** A plain Beast denizen to Muster on. It matches no Nomad adviser. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Errand Boy, with 1 favor and 7 Supply to pay
    * a Muster, and no adviser yet. */
  private def besideErrandBoy: Table = Table.start.pawn(p1, at = home)
    .denizen(errandBoy, at = home).favor(p1, 1).supply(p1, 7)

  private def musteredOnErrandBoy(ready: ReadyGame)
      : EconomyActions.Completed = EconomyActions.muster(rules, ready, p1,
    Vector.empty, DecisionOptionRef.Denizen(errandBoy))

  private def gainedLine(n: Int): NoteText.Said = NoteText.Said("gained",
    s"Gained $n more ${if n == 1 then "warband" else "warbands"}.",
    covers = true)

  private def wanderingSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(VowOfWanderingMuster.id, VowOfWanderingMuster.noteKeys,
      events)

  test("every Muster gains two more warbands, and the line says so"):
    val ready = besideErrandBoy.adviser(p1, vow).ready
    val done = musteredOnErrandBoy(ready)
    // 1, plus none for a matching adviser, plus the Vow's 2 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(2)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("with Vow of Beastkin, each vow's line tells its own part"):
    val ready = besideErrandBoy.adviser(p1, beastkin).adviser(p1, vow).ready
    val done = musteredOnErrandBoy(ready)
    // 1, plus 1 for Vow of Beastkin, a Beast adviser, plus 1 and 2 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 5)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(2)))
    assertEquals(NoteText.said(VowOfBeastkin.id,
      VowOfBeastkin.forCatalog(catalog).noteKeys, done.journal.events),
      Vector(gainedLine(1)))

  test("a short bank gives what it holds, and the line says what came"):
    val ready = besideErrandBoy.adviser(p1, vow).warbandsInBank(p1, 2).ready
    val done = musteredOnErrandBoy(ready)
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 2)
    assertEquals(wanderingSaid(done.journal.events), Vector(gainedLine(1)))

  test("another player's Vow, or a facedown one, changes nothing"):
    val rivalHolds = besideErrandBoy.adviser(p2, vow).ready
    val facedown = besideErrandBoy.adviser(p1, vow, facedown = true).ready
    Vector(rivalHolds, facedown).foreach { ready =>
      val done = musteredOnErrandBoy(ready)
      assertEquals(Look(done.result).boardWarbands(p1),
        Look(ready).boardWarbands(p1) + 1)
      assertEquals(wanderingSaid(done.journal.events), Vector.empty)
    }

  // ---- the restriction ----------------------------------------------------
  private val near = CatalogNames.site("Broken Peaks")
  private val placing = VowOfWandering.operationRestrictions.head
  private val refusal = Some(OperationReason("vow-of-wandering",
    "Vow of Wandering forbids placing warbands on sites",
    OperationReasonKind.Impossible))

  /** p1's Act holding Vow of Wandering faceup, with 3 warbands on the board
    * and 2 at Broken Peaks. */
  private def holdingVowInAct: Table = Table.start.turn(p1, Phase.Act)
    .adviser(p1, vow).boardWarbands(p1, 3).warbandsAt(near, p1, 2)

  /** One warband of `kind` moved from `from` to `to`. */
  private def moved(kind: ForceKind, from: Location, to: Location)
      : CoreOperation = Move(Piece.Warbands(kind, 1),
    PositionedLocation(from), PositionedLocation(to))

  /** `ready` with `player`'s lineage made Citizens. No Table step reaches
    * lineage roles. */
  private def asCitizen(ready: ReadyGame, player: PlayerId): ReadyGame =
    val lineage = Look(ready).player(player).lineage
    ready.updateCampaign(campaign => campaign.copy(lineages =
      campaign.lineages.updatedWith(lineage)(
        _.map(_.copy(role = Role.Citizen)))))

  test("the holder's warbands may not reach a site from the board, the " +
      "bank or another site"):
    val ready = holdingVowInAct.ready
    val mine = Look(ready).forceKind(p1)
    Vector(Location.PlayArea(p1), Location.WarbandBank(mine),
      Location.Site(near)).foreach(from => assertEquals(
        placing.reason(ready, moved(mine, from, Location.Site(home))),
        refusal, from.toString))

  test("a Replace that brings them to a site is refused too"):
    val ready = holdingVowInAct.ready
    assertEquals(placing.reason(ready, Replace(
      Piece.Warbands(ForceKind.Bandit, 2),
      Piece.Warbands(Look(ready).forceKind(p1), 2),
      PositionedLocation(Location.Site(home)))), refusal)

  test("moving them back to the board, and other players' warbands, are " +
      "allowed"):
    val ready = holdingVowInAct.ready
    assertEquals(placing.reason(ready, moved(Look(ready).forceKind(p1),
      Location.Site(near), Location.PlayArea(p1))), None)
    assertEquals(placing.reason(ready, moved(Look(ready).forceKind(p2),
      Location.PlayArea(p2), Location.Site(home))), None)
    assertEquals(placing.reason(ready, moved(ForceKind.Bandit,
      Location.WarbandBank(ForceKind.Bandit), Location.Site(home))), None)

  test("a facedown Vow refuses nothing, and another player's binds only " +
      "them"):
    val facedown = Table.start.turn(p1, Phase.Act)
      .adviser(p1, vow, facedown = true).ready
    assertEquals(placing.reason(facedown, moved(Look(facedown).forceKind(p1),
      Location.PlayArea(p1), Location.Site(home))), None)
    val rivals = Table.start.turn(p1, Phase.Act).adviser(p2, vow).ready
    assertEquals(placing.reason(rivals, moved(Look(rivals).forceKind(p1),
      Location.PlayArea(p1), Location.Site(home))), None)
    assertEquals(placing.reason(rivals, moved(Look(rivals).forceKind(p2),
      Location.PlayArea(p2), Location.Site(home))), refusal)

  test("a Citizen holder's Imperial warbands are refused on their own " +
      "turn, or from their own board"):
    val fromBank = moved(ForceKind.Imperial,
      Location.WarbandBank(ForceKind.Imperial), Location.Site(home))
    assertEquals(placing.reason(asCitizen(holdingVowInAct.ready, p1),
      fromBank), refusal)
    // On p2's turn, Imperial warbands from the bank may be the Chancellor's.
    val p2sTurn = asCitizen(holdingVowInAct.turn(p2, Phase.Act).ready, p1)
    assertEquals(placing.reason(p2sTurn, fromBank), None)
    assertEquals(placing.reason(p2sTurn, moved(ForceKind.Imperial,
      Location.PlayArea(p1), Location.Site(home))), refusal)

  /** A Muster's gain node: one warband for p1. */
  private val p1MusterGain: Operation = Sequence(Vector[Operation](
    Gain.Warbands(p1, ForceKind.Bandit, 1)), Some(PowerWindow.MusterGain))

  test("its extra warbands are an automatic use of the musterer's"):
    assertEquals(AppendedAutomaticUses.at(VowOfWanderingMuster,
      PowerWindow.MusterGain, p1MusterGain), Vector(
      AppendedAutomaticUses.Named(p1, VowOfWanderingMuster.id, vow)))
