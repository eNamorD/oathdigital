package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Unwrap.as

class GreatHerdSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[GreatHerd]
  private val herd = power.cardId
  private val played = hookAt(herd, homeSite)
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val lancers = CatalogNames.denizen("Lancers")
  // One Nomad edifice: Great Forge intact, Broken Forge ruined.
  private val forge = CatalogNames.edifice("Great Forge")
  /** Great Herd is site-only: it stands at p1's site. */
  private val start = Table.start.denizen(herd, at = homeSite)

  private def offered(ready: ReadyGame, tree: PendingTree)
      : Vector[DecisionOptionRef] =
    val query = question(ready, power, played, tree).query
    as[DecisionQuery.ChooseOne](query).options.map(_.ref)

  private def pick(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  test("it offers the Nomad cards at other sites, then keeping it"):
    // A Nomad denizen at Great Herd's own site and an intact Nomad edifice
    // are not offered.
    val ready = start.denizen(wildMounts, at = nearSite)
      .denizen(lancers, at = homeSite)
      .edifice("Great Forge", EdificeSide.Intact, at = awaySite).ready
    val first = Outcomes.parked(fire(ready, power, played))
    assertEquals(question(ready, power, played, first.tree).decisionId,
      GreatHerd.decisionId)
    assertEquals(offered(ready, first.tree),
      Vector(DecisionOptionRef.Denizen(wildMounts), GreatHerd.keep))

  test("the swap moves each card to the other's site with its tokens"):
    val ready = start.denizen(wildMounts, at = nearSite)
      .tokens(wildMounts, favor = 1, secrets = 2).ready
    val first = Outcomes.parked(fire(ready, power, played))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(DecisionOptionRef.Denizen(wildMounts))))
    val after = Look(done.treeless)
    assertEquals(after.cardsAt(homeSite), Vector[CardId](wildMounts))
    assertEquals(after.cardsAt(nearSite), Vector[CardId](herd))
    assertEquals(after.tokensOn(wildMounts), Tokens(1, 2))
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(power, done.events), Vector(NoteText.Said("swapped",
      s"p1 swapped it with ${wildMounts.value} at ${nearSite.value}.",
      covers = false)))

  test("a ruined Nomad edifice can be swapped too"):
    val ready = start.edifice("Broken Forge", EdificeSide.Ruined, at = awaySite)
      .ready
    val first = Outcomes.parked(fire(ready, power, played))
    assertEquals(offered(ready, first.tree),
      Vector(DecisionOptionRef.Edifice(forge), GreatHerd.keep))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(DecisionOptionRef.Edifice(forge))))
    assertEquals(Look(done.treeless).cardsAt(homeSite),
      Vector[CardId](forge))
    assertEquals(Look(done.treeless).cardsAt(awaySite), Vector[CardId](herd))

  test("keeping Great Herd changes nothing and writes nothing"):
    val ready = start.denizen(wildMounts, at = nearSite).ready
    val first = Outcomes.parked(fire(ready, power, played))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(GreatHerd.keep)))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector.empty)

  test("with no Nomad card at another site nothing is asked, and the line " +
      "says so"):
    val done = Outcomes.finished(
      fire(start.denizen(lancers, at = homeSite).ready, power, played))
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      "No Nomad card could be swapped.", covers = false)))
