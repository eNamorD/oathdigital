package oathdigital.gameplay.powers.action

import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.PhasePowerHarness.{offered, UsedAndAnswered}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class RelicThiefSuite extends munit.FunSuite:
  import ActPowerHarness._

  private val walkerParked = PhasePowerHarness.parked
  private val power = RelicThief.forCatalog(catalog)
  private val thief = CatalogNames.denizen("Relic Thief")
  private val source = DecisionOptionRef.Denizen(thief)
  private val home = CatalogNames.site("Ancient City")
  /** Defense 1. */
  private val whistle = CatalogNames.relic("Whistle")
  /** Defense 2. */
  private val ivoryEye = CatalogNames.relic("Ivory Eye")
  /** Defense 0. */
  private val harp = CatalogNames.relic("Truthful Harp")
  /** Defense 2. */
  private val clayRattle = CatalogNames.relic("Clay Rattle")
  private val lostTongue = CatalogNames.denizen("Lost Tongue")

  /** p1's Act at Ancient City holding Relic Thief, with 1 favor and 1 faceup
    * secret to pay it. p2 stands there too, holding Whistle faceup; p3 stands
    * at Buried Giant. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).adviser(p1, thief).favor(p1, 1)
    .secrets(p1, faceUp = 1).pawn(p2, at = home).relic(p2, whistle)
    .pawn(p3, at = "Buried Giant")

  /** The walk Relic Thief started on `before` and the walk after `relic` was
    * chosen, the dice showing `dice`. */
  private def chooseRelic(before: ReadyGame, relic: RelicId,
      dice: WalkerDice = WalkerDice.unavailable): UsedAndAnswered =
    val rolling = rules(dice)
    val started = accepted(use(rolling, before, RelicThief.id, source))
    UsedAndAnswered(started, accepted(answer(rolling, started.state,
      RelicThief.decisionId, DecisionOptionRef.Relic(relic))))

  test("it places a favor and a secret and offers the faceup relics of " +
      "other players at the site"):
    val before = staged.relic(p2, ivoryEye, facedown = true).relic(p1, harp)
      .relic(p3, clayRattle).ready
    val t = accepted(use(rules(), before, RelicThief.id, source))
    walkerParked.assertParked(t.state, ActionRef.UsePower(RelicThief.id),
      RelicThief.decisionId, p1)
    assertEquals(offered(t, p1), Some(Vector("relic" -> whistle.value)))
    assertEquals(Look(ready(t.state)).tokensOn(thief), Tokens(1, 1))

  test("rolling no shield takes the relic, and the line names its holder"):
    val before = staged.ready
    val blank = defenseDice(DefenseDieFace.Blank)
    val walked = chooseRelic(before, whistle, blank)
    walkerParked.assertResumed(walked.answered.state, Phase.Act, p1)
    val end = Look(ready(walked.answered.state))
    assertEquals(end.relics(p1), Vector(whistle))
    assertEquals(end.relics(p2), Vector.empty[RelicId])
    assertEquals(NoteText.said(power, walked.answered.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Rolled no shield and took ${whistle.value} from ${p2.value}.",
        covers = false)))
    assertEquals(replayed(rules(blank), before, walked.events),
      ready(walked.answered.state))
    assertWireRoundTrips(walked.events)

  test("rolling a shield leaves the relic with its holder"):
    val walked = chooseRelic(staged.ready, whistle,
      defenseDice(DefenseDieFace.OneShield))
    assertEquals(Look(ready(walked.answered.state)).relics(p2), Vector(whistle))
    assertEquals(NoteText.said(power, walked.answered.events), Vector(
      NoteText.Said("used.kept",
        s"Rolled a shield; ${p2.value} kept ${whistle.value}.",
        covers = false)))

  test("a relic of defense 0 is taken without a roll"):
    // The dice are unavailable, so a roll would fail the command.
    val walked = chooseRelic(staged.relic(p2, harp).ready, harp)
    assertEquals(Look(ready(walked.answered.state)).relics(p1), Vector(harp))

  test("a relic Lost Tongue protects is not offered"):
    // p1 rules no Nomad card, so p2's faceup Lost Tongue keeps Whistle.
    val before = staged.adviser(p2, lostTongue).pawn(p3, at = home)
      .relic(p3, harp).ready
    val t = accepted(use(rules(), before, RelicThief.id, source))
    assertEquals(offered(t, p1), Some(Vector("relic" -> harp.value)))

  test("with no faceup relic to take it asks nothing, and the line says so"):
    val before = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .adviser(p1, thief).pawn(p2, at = home)
      .relic(p2, whistle, facedown = true).ready
    val done = accepted(use(rules(), before, RelicThief.id, source))
    walkerParked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No faceup relic could be taken.", covers = false)))

  test("Jinx rerolls the Relic Thief's roll"):
    val jinx = CatalogNames.denizen("Jinx")
    val dice = catalog.relic(whistle).get.defense
    val before = staged.adviser(p1, jinx).secrets(p1, faceUp = 2).ready
    val rules0 = rulesWithPrintedRestrictions(scripted(
      Vector.fill(dice)(DefenseDieFace.TwoShields),
      Vector.fill(dice)(DefenseDieFace.Blank)))
    val reroll = RerollChoice.decisionId(RelicThief.pool, 1)
    val started = accepted(use(rules0, before, RelicThief.id, source))
    val parked = accepted(answer(rules0, started.state,
      RelicThief.decisionId, DecisionOptionRef.Relic(whistle)))
    parkedWithWalkerPowers.assertParked(parked.state,
      ActionRef.UsePower(RelicThief.id), reroll, p1)
    val rerolled = accepted(answer(rules0, parked.state, reroll,
      DecisionOptionRef.Denizen(jinx)))
    // The rerolled dice show no shield, so the relic is taken.
    assertEquals(Look(ready(rerolled.state)).relics(p1), Vector(whistle))
