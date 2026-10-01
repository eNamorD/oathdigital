package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class GreatHerdSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[GreatHerd]
  private val herd = power.cardId
  private val played = hookAt(herd, homeSite)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)
  private val nomadEdifices = edificesOf(Suit.Nomad)
  /** Great Herd is site-only: it stands at p1's site. */
  private val start = Table.start.denizen(herd, at = homeSite)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def offered(ready: ReadyGame, tree: PendingTree)
      : Vector[DecisionOptionRef] = question(ready, power, played, tree).query
    .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)

  private def pick(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  test("it offers the Nomad cards at other sites, then keeping it"):
    // A Nomad denizen at Great Herd's own site and an intact Nomad edifice
    // are not offered.
    val ready = start.denizen(nomad(0), at = nearSite)
      .denizen(nomad(1), at = homeSite)
      .edifice(nomadEdifices(0), EdificeSide.Intact, at = awaySite).ready
    val first = parked(fire(ready, power, played))
    assertEquals(question(ready, power, played, first.tree).decisionId,
      GreatHerd.decisionId)
    assertEquals(offered(ready, first.tree),
      Vector(DecisionOptionRef.Denizen(nomad(0)), GreatHerd.keep))

  test("the swap moves each card to the other's site with its tokens"):
    val ready = start.denizen(nomad(0), at = nearSite)
      .tokens(nomad(0), favor = 1, secrets = 2).ready
    val first = parked(fire(ready, power, played))
    val done = finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(DecisionOptionRef.Denizen(nomad(0)))))
    val after = Look(done.treeless)
    assertEquals(after.denizens(homeSite), Vector[CardId](nomad(0)))
    assertEquals(after.denizens(nearSite), Vector[CardId](herd))
    assertEquals(after.tokensOn(nomad(0)), Tokens(1, 2))
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), Vector(NoteText.Said("swapped",
      s"${p1.value} swapped it with ${nomad(0).value} at ${nearSite.value}.",
      covers = false)))

  test("a ruined Nomad edifice can be swapped too"):
    val ruined = nomadEdifices(0)
    val ready = start.edifice(ruined, EdificeSide.Ruined, at = awaySite).ready
    val first = parked(fire(ready, power, played))
    assertEquals(offered(ready, first.tree),
      Vector(DecisionOptionRef.Edifice(ruined), GreatHerd.keep))
    val done = finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(DecisionOptionRef.Edifice(ruined))))
    assertEquals(Look(done.treeless).denizens(homeSite),
      Vector[CardId](ruined))
    assertEquals(Look(done.treeless).denizens(awaySite), Vector[CardId](herd))

  test("keeping Great Herd changes nothing and writes nothing"):
    val ready = start.denizen(nomad(0), at = nearSite).ready
    val first = parked(fire(ready, power, played))
    val done = finished(resume(ready, power, played, first.tree,
      GreatHerd.decisionId, pick(GreatHerd.keep)))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector.empty)

  test("with no Nomad card at another site nothing is asked, and the line " +
      "says so"):
    val done = finished(fire(start.denizen(nomad(1), at = homeSite).ready,
      power, played))
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No Nomad card could be swapped.", covers = false)))
