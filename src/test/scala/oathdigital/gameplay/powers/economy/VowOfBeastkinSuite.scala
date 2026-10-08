package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, PowerRules, Refusals, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class VowOfBeastkinSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Beastkin")
  private val power = VowOfBeastkin.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Beast denizen to Muster on. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen, which matches no Beast adviser. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** An Order adviser, so that Wrestlers matches one. */
  private val messenger = CatalogNames.denizen("Messenger")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Errand Boy and Wrestlers, with 1 favor and 7
    * Supply to pay a Muster, and no adviser yet. */
  private def musterSite: Table = Table.start.pawn(p1, at = home)
    .denizen(errandBoy, at = home).denizen(wrestlers, at = home)
    .favor(p1, 1).supply(p1, 7)

  private def mustered(ready: ReadyGame, card: DenizenId)
      : EconomyActions.Completed = EconomyActions.muster(rules, ready, p1,
    Vector.empty, DecisionOptionRef.Denizen(card))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("its holder is offered only the cards matching an adviser, and an " +
      "answer naming another is refused"):
    val started = accepted(rules.startWalker(musterSite.adviser(p1, vow).state,
      ActionRef.Muster, p1, Vector.empty))
    assertEquals(PhasePowerHarness.offered(started, p1),
      Some(Vector("denizen" -> errandBoy.value)))
    assertEquals(rules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(wrestlers))).left.toOption,
      Some(Refusals.notOffered(MusterProcedure.decisionId)))

  test("every Muster gains one more warband, and the line says so"):
    val ready = musterSite.adviser(p1, vow).ready
    val done = mustered(ready, errandBoy)
    // 1, plus 1 for the Vow itself, a Beast adviser, plus its 1 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)
    assertEquals(said(done.journal.events), Vector(NoteText.Said("gained",
      "Gained 1 more warband.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("a faceup Order adviser lets its holder Muster on an Order card"):
    val ready = musterSite.adviser(p1, vow).adviser(p1, messenger).ready
    val done = mustered(ready, wrestlers)
    // 1, plus 1 for Messenger, plus the Vow's 1 more.
    assertEquals(Look(done.result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)

  test("another player's Vow, or a facedown one, changes nothing"):
    val rivalHolds = musterSite.adviser(p2, vow).ready
    val facedown = musterSite.adviser(p1, vow, facedown = true).ready
    Vector(rivalHolds, facedown).foreach { ready =>
      val done = mustered(ready, wrestlers)
      assertEquals(Look(done.result).boardWarbands(p1),
        Look(ready).boardWarbands(p1) + 1)
      assertEquals(said(done.journal.events), Vector.empty)
    }
