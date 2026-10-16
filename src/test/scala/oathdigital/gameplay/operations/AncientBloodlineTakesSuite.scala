package oathdigital.gameplay.operations

import oathdigital.gameplay.{OathRules, PowerAccess}
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.action.SkeletonKey
import oathdigital.gameplay.powers.wake.HornedMask
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.protocol.projection.ConsentAskProjection
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Ancient Bloodline on the questions that take a card from a site
  * (catalog batch 8, slice 11): Recover, Skeleton Key and Horned Mask
  * offer a card its holder locks askable of the holder. */
class AncientBloodlineTakesSuite extends munit.FunSuite:
  import DefenseDieFace.TwoShields

  private val bloodline = CatalogNames.denizen("Ancient Bloodline")
  private val askedReason = Some("Ancient Bloodline: Locked and needs the " +
    "holder's consent.")
  private val askingP2 = Some(ConsentAskProjection(Vector("p2"),
    Vector("Ancient Bloodline")))

  /** `ready` with the answer `answer` remembered, given by p2, for
    * `covers`. */
  private def rememberingP2(ready: ReadyGame, covers: ConsentKey,
      answer: ConsentAnswer): ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(covers ->
      answer.by(p2)), turn = Some(ConsentTurn.of(ready))))

  /** The row for `ref` in the question `t` is parked on, as p1 sees it. */
  private def rowFor(t: OathTransition, ref: DecisionOptionRef)(
      using munit.Location) =
    present(PhasePowerHarness.queryOf(t, p1).toVector
      .flatMap(_.offeredOptions).find(_.id == ref.wireId), ref.wireId)

  // Recover: Fair Isle's difficulty is 3, and two shields twice make 4.
  private val isle = CatalogNames.site("Fair Isle")
  private val sticks = CatalogNames.relic("Dowsing Sticks")
  private val sticksRef = DecisionOptionRef.Relic(sticks)
  /** Fresh for each walk: the scripted dice are used up as they roll. */
  private def recoverRules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = scripted(Vector(TwoShields, TwoShields)))

  /** p1's Act at Fair Isle with Dowsing Sticks there, which `ruled` gives
    * its ruler, and p2 holding Ancient Bloodline faceup. */
  private def sticksAtIsleBesideBloodline(
      ruled: Table => Table = _.warbandsAt(isle, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = isle)
      .relicAt(sticks, at = isle).adviser(p2, bloodline)).ready

  private def recoveredUpToTheRelic(ready: ReadyGame): OathTransition =
    accepted(recoverRules.startWalker(Ready(ready), ActionRef.Recover, p1))

  private val coversSticks = ConsentKey(AncientBloodline.id, p1,
    AncientBloodline.moveKey, sticksRef)

  test("Recover offers a relic the holder locks askable of the holder"):
    val ready = sticksAtIsleBesideBloodline()
    assert(PowerAccess.ruledSites(ready, p2).contains(isle))
    val t = recoveredUpToTheRelic(ready)
    assert(PhasePowerHarness.awaits(t, RecoverProcedure.relicDecisionId))
    val row = rowFor(t, sticksRef)
    assertEquals(row.state, "askable")
    assertEquals(row.reason, askedReason)
    assertEquals(row.consent, askingP2)

  test("after the holder refuses, the relic shows the plain reason"):
    val t = recoveredUpToTheRelic(rememberingP2(
      sticksAtIsleBesideBloodline(), coversSticks, ConsentAnswer.Refused))
    val row = rowFor(t, sticksRef)
    assertEquals(row.state, "unavailable")
    assertEquals(row.reason, Some("Ancient Bloodline: Locked."))

  test("a grant opens the relic, and p1 recovers it facedown"):
    val t = recoveredUpToTheRelic(rememberingP2(
      sticksAtIsleBesideBloodline(), coversSticks, ConsentAnswer.Granted))
    assertEquals(rowFor(t, sticksRef).state, "available")
    val done = accepted(recoverRules.resolveWalker(t.state, p1,
      RecoverProcedure.relicDecisionId,
      DecisionAnswer.ChooseOneAnswer(sticksRef)))
    assert(Look(PhasePowerHarness.after(done)).player(p1).relics.exists(
      relic => relic.id == sticks &&
        relic.orientation == Orientation.FaceDown))

  test("the holder's own Recover takes the relic freely"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = isle)
      .relicAt(sticks, at = isle).adviser(p1, bloodline)
      .warbandsAt(isle, p1, 1).ready
    assertEquals(rowFor(recoveredUpToTheRelic(ready), sticksRef).state,
      "available")

  test("Skeleton Key offers a relic the holder locks askable of the " +
      "holder"):
    import PhasePowerHarness.{awaits, use}
    val key = CatalogNames.relic("Skeleton Key")
    val dunes = CatalogNames.site("Dunes")
    val whistle = CatalogNames.relic("Whistle")
    val ready = Table.start.turn(p1, Phase.Act).relic(p1, key)
      .secrets(p1, 2).pawn(p1, dunes).relicAt(whistle, at = dunes)
      .warbandsAt(dunes, p2, 1).adviser(p2, bloodline).ready
    assert(PowerAccess.ruledSites(ready, p2).contains(dunes))
    val t = accepted(use(ready, SkeletonKey, DecisionOptionRef.Relic(key)))
    assert(awaits(t, SkeletonKey.decisionId))
    val row = rowFor(t, DecisionOptionRef.Relic(whistle))
    assertEquals(row.state, "askable")
    assertEquals(row.reason, askedReason)

  test("Horned Mask offers a denizen the holder locks askable of the " +
      "holder"):
    import PhasePowerHarness.{awaits, use}
    val mask = CatalogNames.relic("Horned Mask")
    val home = CatalogNames.site("Ancient City")
    val inn = CatalogNames.denizen("Wayside Inn")
    val ready = Table.start.turn(p1, Phase.Wake).pawn(p1, home)
      .relic(p1, mask).denizen(inn, at = home)
      .warbandsAt(home, p2, 1).adviser(p2, bloodline).ready
    assert(PowerAccess.ruledSites(ready, p2).contains(home))
    val t = accepted(use(ready, HornedMask(catalog),
      DecisionOptionRef.Relic(mask)))
    assert(awaits(t, HornedMask.denizenDecisionId))
    val row = rowFor(t, DecisionOptionRef.Denizen(inn))
    assertEquals(row.state, "askable")
    assertEquals(row.reason, askedReason)
