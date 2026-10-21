package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.walker.{LogNoted, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Unwrap.accepted

class GreatForgeRulesSuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = CatalogNames.edifice("Great Forge")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  // The generic driver always answers a Decide with its first offered
  // option, and `siteOptions` is `map.inPlay` order -- Ancient City comes
  // first, so the edifice sits under the FIRST participant's (setup's first
  // player) pawn-placement choice.
  private val site = CatalogNames.site("Ancient City")
  // p2 is the first player of the fixture's first game.
  private val firstPlayer = Table.p2
  // The fresh deal tops the relic deck with Dowsing Sticks, and puts Sticky
  // Fire and Cursed Cauldron at Broken Peaks, in the Cradle with Ancient City.
  private val topRelic = CatalogNames.relic("Dowsing Sticks")
  private val relicSite = CatalogNames.site("Broken Peaks")
  private val cradleRelics = Vector(CatalogNames.relic("Sticky Fire"),
    CatalogNames.relic("Cursed Cauldron"))

  // The Setup walk is the subject (the edifice's effect runs when the walk
  // reaches it), so these tests start from the fresh Setup, not a Table.
  private def stagedAt(side: EdificeSide)(using munit.Location): ReadyGame =
    Table.from(FirstGameSetupFixture.freshReady)
      .edifice(edifice, side, at = site).ready

  private def finish(ready: ReadyGame): ReadyGame =
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    SetupWalkDriver.driveToCompletion(ready, tree, TriggeredProcedureRef.Setup,
      powers)

  private def events(ready: ReadyGame): Vector[OathEvent] =
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    SetupWalkDriver.driveWithEvents(ready, tree, TriggeredProcedureRef.Setup,
      powers).events

  private val great = GreatForge.forCatalog(catalog)

  private val broken = BrokenForge.forCatalog(catalog)

  test("Great Forge gives the placing player the top relic facedown"):
    val staged = stagedAt(EdificeSide.Intact)
    val finished = finish(staged)
    val player = Look(finished).player(firstPlayer)
    assert(player.relics.exists(r => r.id == topRelic &&
      r.orientation == Orientation.FaceDown))

  test("Broken Forge discards every relic in its region to setAsideRelics"):
    val staged = stagedAt(EdificeSide.Ruined)
    val relicsBefore = Look(staged).relicsAt(relicSite).toSet
    assertEquals(relicsBefore, cradleRelics.toSet)
    val finished = finish(staged)
    assertEquals(Look(finished).relicsAt(relicSite), Vector.empty[RelicId])
    assert(relicsBefore.subsetOf(finished.game.current.setAsideRelics.toSet),
      "the discarded relics are set aside")

  // ---- Lines ----

  test("Great Forge writes the relic its player drew"):
    val staged = stagedAt(EdificeSide.Intact)
    val said = NoteText.said(great.id, great.noteKeys, events(staged))
    assertEquals(said.head, NoteText.Said(NoteKey.Used,
      s"${firstPlayer.value} drew ${topRelic.value} facedown.", covers = false))
    // The driver places every pawn at the first site, so each player draws.
    assertEquals(said.size, staged.game.current.players.size)

  test("Great Forge with an empty relic deck writes nothing"):
    val staged = Table.from(stagedAt(EdificeSide.Intact)).relicDeckEmptied
      .ready
    assertEquals(NoteText.said(great.id, great.noteKeys, events(staged)),
      Vector.empty)

  test("Broken Forge writes the relics it discarded"):
    val staged = stagedAt(EdificeSide.Ruined)
    assertEquals(NoteText.said(broken.id, broken.noteKeys, events(staged)),
      Vector(NoteText.Said("discarded",
        s"Discarded ${cradleRelics.map(_.value).mkString(", ")}.",
        covers = false)))

  /** A power hooked at `SetupPawnPlaced` appends its leaves to the node the
    * window hangs on, so the pawn's own line must be inside that node: a
    * sibling after it would read second, and the rule this line replaced
    * read first. */
  test("the pawn's own line comes before the edifice's"):
    val staged = stagedAt(EdificeSide.Intact)
    val written = events(staged).collect {
      case LogNoted(power, note, _) => s"${power.value}/${note.key}"
    }
    assertEquals(written.take(2), Vector(
      s"${ProcedureNotes.id(TriggeredProcedureRef.Setup).value}/placed-pawn",
      s"${great.id.value}/${NoteKey.Used}"))
