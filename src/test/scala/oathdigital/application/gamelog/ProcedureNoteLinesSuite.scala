package oathdigital.application.gamelog

import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.protocol.projection.{LogKind, LogSpan}
import oathdigital.testkit.CatalogNames
import LogSpan.Text

/** What a migrated procedure's line reads, on the branches no golden script
  * exercises, and at which kind it posts (procedures journal their own notes
  * design, slice 2). A golden file proves the branch its script plays; these
  * are the branches it does not play, written down before the rule that
  * worded them was deleted.
  *
  * Rendering takes no game state: every assertion here is over plain text,
  * players, sites, banners and numbers, and a card with no states behind it
  * renders as its back, which is the part these branches leave untested
  * elsewhere.
  */
class ProcedureNoteLinesSuite extends munit.FunSuite:
  private val words = new LogWords(catalog, LogScripts.presentation)
  private val lines = new PowerLines(words, NoteWordings.default(catalog),
    LogScripts.formatter.procedures.noteKind)

  /** `key`'s sentence, as the Game Log renders it for a procedure's note. */
  private def spoken(procedure: ProcedureRef, key: NoteKey, args: NoteArg*)(
      using munit.Location): Vector[LogSpan] =
    lines.spoken(ProcedureNotes.id(procedure), key.sourceless(args*),
      Vector.empty, None).getOrElse(
        fail(s"no template for ${procedure.key}/${key.name}"))

  /** `key`'s sentence as plain text, for a line with no card in it. */
  private def said(procedure: ProcedureRef, key: NoteKey, args: NoteArg*)(
      using munit.Location): String =
    spoken(procedure, key, args*).map(_.text).mkString

  /** The kind `procedure`'s entry declares for `key`. */
  private def kindOf(procedure: ProcedureRef, key: NoteKey): LogKind =
    LogScripts.formatter.procedures.of(procedure).noteKind(key.name)

  test("a procedure's note posts at Action unless its entry says otherwise"):
    assertEquals(kindOf(ActionRef.Muster, MusterProcedure.mustered),
      LogKind.Action)

  test("a procedure whose line is still a rule declares no kind"):
    assertEquals(LogScripts.formatter.procedures.of(ActionRef.Travel)
      .noteKind("anything"), LogKind.Action)

  test("a count of one reads singular"):
    assertEquals(said(ActionRef.Muster, MusterProcedure.mustered,
      NoteArg.Number(1)), "Mustered 1 warband with ")

  private val brokenPeaks = SiteId("site:broken-peaks")

  test("Take Wealth of a secret reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.TakeWealth, TakeWealthProcedure.tookSecret,
        NoteArg.Site(brokenPeaks)),
      Vector[LogSpan](Text("Took 1 secret from "), words.site(brokenPeaks)))

  private val roar: CardId = CatalogNames.denizen("Threatening Roar")
  private val mushrooms: CardId = CatalogNames.denizen("Mushrooms")
  private val mercenaries: CardId = CatalogNames.denizen("Mercenaries")

  test("a Forge paying secrets only reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Forge, ForgeProcedure.paidSecrets,
        NoteArg.Cards(Vector(roar, mushrooms, mercenaries))),
      Vector[LogSpan](Text("Placed secrets on "), Text("3 Denizens")))

  test("a Forge paying both joins the halves as the rule joined them"):
    assertEquals(spoken(ActionRef.Forge, ForgeProcedure.paidBoth,
        NoteArg.Cards(Vector(roar, mushrooms)),
        NoteArg.Cards(Vector(mercenaries))),
      Vector[LogSpan](Text("Placed favor on "), Text("2 Denizens"),
        Text(" and secrets on "), Text("a Denizen")))

  private val rival = PlayerId("p2")

  test("a Challenge against a holder reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Challenge,
        ChallengeProcedure.tookFromPlayerWithFavor,
        NoteArg.Banner(Banner.PeoplesFavor), NoteArg.Player(rival),
        NoteArg.Number(2)),
      Vector[LogSpan](Text("Took "), words.banner(Banner.PeoplesFavor),
        Text(" from "), words.player(rival), Text(" with "), Text("2"),
        Text(" favor")))

  test("a Challenge for Darkest Secret counts secrets"):
    assertEquals(spoken(ActionRef.Challenge,
        ChallengeProcedure.tookFromBankWithSecrets,
        NoteArg.Banner(Banner.DarkestSecret), NoteArg.Number(2)),
      Vector[LogSpan](Text("Took "), words.banner(Banner.DarkestSecret),
        Text(" from the bank with "), Text("2"), Text(" "), Text("secrets")))

  test("a Challenge for one secret says secret"):
    assertEquals(spoken(ActionRef.Challenge,
        ChallengeProcedure.tookFromPlayerWithSecrets,
        NoteArg.Banner(Banner.DarkestSecret), NoteArg.Player(rival),
        NoteArg.Number(1)),
      Vector[LogSpan](Text("Took "), words.banner(Banner.DarkestSecret),
        Text(" from "), words.player(rival), Text(" with "), Text("1"),
        Text(" "), Text("secret")))

  test("placing secrets on a banner reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.PlaceBannerResource,
        PlaceBannerResourceProcedure.placedSecrets, NoteArg.Number(2),
        NoteArg.Banner(Banner.DarkestSecret)),
      Vector[LogSpan](Text("Placed "), Text("2"), Text(" "), Text("secrets"),
        Text(" on "), words.banner(Banner.DarkestSecret)))

  test("the Oathkeeper passing to the bank reads as the rule wrote it"):
    assertEquals(spoken(TriggeredProcedureRef.Oathkeeper,
        OathkeeperProcedure.passedToBank),
      Vector[LogSpan](Text("Oathkeeper passed to the bank")))

  test("an Oathkeeper line posts at Trigger, not Action"):
    assertEquals(kindOf(TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.passedToPlayer), LogKind.Trigger)
    assertEquals(kindOf(TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.passedToBank), LogKind.Trigger)
