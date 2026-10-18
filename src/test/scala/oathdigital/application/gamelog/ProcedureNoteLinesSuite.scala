package oathdigital.application.gamelog

import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.actions.campaign.CampaignNotes
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.negotiation.NegotiationProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.setup.SetupProcedure
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

  test("a Finish Rest line posts at Delta, not Action"):
    assertEquals(kindOf(PhaseTransitionRef.FinishRest,
      FinishRestProcedure.supplyIncreased), LogKind.Delta)

  private val stickyFire: CardId = CatalogNames.relic("Sticky Fire")
  private val birdsong: CardId = CatalogNames.denizen("Birdsong")

  test("a relic given in a negotiation reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Negotiation, NegotiationProcedure.gaveCard,
        NoteArg.Player(PlayerId("p1")), NoteArg.Card(stickyFire),
        NoteArg.Player(rival)),
      Vector[LogSpan](words.player(PlayerId("p1")), Text(" gave "),
        Text("a Relic"), Text(" to "), words.player(rival)))

  test("a site relic shown in a negotiation reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Negotiation, NegotiationProcedure.wasShown,
        NoteArg.Player(rival), NoteArg.Card(stickyFire),
        NoteArg.Site(brokenPeaks)),
      Vector[LogSpan](words.player(rival), Text(" was shown "),
        Text("a Relic"), Text(" at "), words.site(brokenPeaks)))

  // Accepted in slice 2 (design, "Two things slice 4 must settle first"):
  // the deleted rule read this card through `LogWords.slotted` and wrote
  // "facedown adviser (slot 1)". A note's card word has no slot fallback.
  test("a disclosed card a viewer cannot identify reads as its back"):
    assertEquals(spoken(ActionRef.Negotiation, NegotiationProcedure.showed,
        NoteArg.Player(rival), NoteArg.Player(PlayerId("p1")),
        NoteArg.Card(birdsong)),
      Vector[LogSpan](words.player(rival), Text(" showed "),
        words.player(PlayerId("p1")), Text(" "), Text("a Denizen")))

  test("every Negotiation settlement line posts at Delta"):
    Vector(NegotiationProcedure.gaveFavor, NegotiationProcedure.gaveCard,
      NegotiationProcedure.showed, NegotiationProcedure.wasShown).foreach(key =>
        assertEquals(kindOf(ActionRef.Negotiation, key), LogKind.Delta,
          s"${key.name}"))

  test("a Negotiation's opening lines post at Action"):
    assertEquals(kindOf(ActionRef.Negotiation,
      NegotiationProcedure.negotiated), LogKind.Action)
    assertEquals(kindOf(ActionRef.Negotiation, NegotiationProcedure.ended),
      LogKind.Action)

  test("a card revealed in Setup reads as the rule wrote it"):
    assertEquals(spoken(TriggeredProcedureRef.Setup,
        SetupProcedure.revealedCard, NoteArg.Player(rival),
        NoteArg.Card(birdsong)),
      Vector[LogSpan](words.player(rival), Text(" revealed "),
        Text("a Denizen")))

  test("Setup's kept and revealed lines post at Decision, its pawn at Action"):
    assertEquals(kindOf(TriggeredProcedureRef.Setup,
      SetupProcedure.placedPawn), LogKind.Action)
    assertEquals(kindOf(TriggeredProcedureRef.Setup,
      SetupProcedure.keptAdviser), LogKind.Decision)
    assertEquals(kindOf(TriggeredProcedureRef.Setup,
      SetupProcedure.revealedCard), LogKind.Decision)

  test("a Negotiation with two partners joins them"):
    val third = PlayerId("p3")
    assertEquals(spoken(ActionRef.Negotiation,
        NegotiationProcedure.negotiated,
        NoteArg.Players(Vector(rival, third))),
      Vector[LogSpan](Text("Negotiated with "), words.player(rival),
        Text(" and "), words.player(third)))

  test("a Trade for favor reads as the rule wrote it"):
    assertEquals(spoken(ActionRef.Trade, TradeProcedure.tradedForFavor,
        NoteArg.Card(roar), NoteArg.Number(3)),
      Vector[LogSpan](Text("Traded with "), Text("a Denizen"),
        Text(" for "), Text("3"), Text(" favor")))

  test("a Trade for one secret says secret"):
    assertEquals(said(ActionRef.Trade, TradeProcedure.tradedForSecrets,
      NoteArg.Card(roar), NoteArg.Number(1)),
      "Traded with a Denizen for 1 secret")

  // The rule this replaced wrote "for no secrets" here whichever resource
  // the Trade was for; nothing covered it (slice 3 plan-time ruling 3, and
  // the wording ledger).
  test("a Trade for favor that gained none says no favor"):
    assertEquals(said(ActionRef.Trade, TradeProcedure.tradedForNoFavor,
      NoteArg.Card(roar)), "Traded with a Denizen for no favor")

  test("both Recover lines post at Action"):
    assertEquals(kindOf(ActionRef.Recover, RecoverProcedure.recovered),
      LogKind.Action)
    assertEquals(kindOf(ActionRef.Recover, RecoverProcedure.failed),
      LogKind.Action)

  test("a discard that reached no regional pile names no pile"):
    assertEquals(said(ActionRef.PlayFacedownAdviser,
      CardPlayProcedure.discarded, NoteArg.Card(birdsong)),
      "Discarded a Denizen")

  test("every card-play line posts at Action, under whichever procedure"):
    Vector(CardPlayProcedure.discardedTo, CardPlayProcedure.discarded,
      CardPlayProcedure.playedAtSite, CardPlayProcedure.revealedVision,
      CardPlayProcedure.playedAsAdviser).foreach(key =>
        assertEquals(kindOf(ActionRef.PlayFacedownAdviser, key),
          LogKind.Action, key.name))

  test("a Search of a discard pile names the pile"):
    assertEquals(said(ActionRef.Search, SearchProcedure.drewFromDiscard,
      NoteArg.Cards(Vector(roar)), NoteArg.RegionName(Region.Cradle)),
      "Drew a Denizen from the Cradle discard")

  // Six drawn cards is reachable: 3 printed, +1 Augury, +2 Truthful Harp.
  // The rule this replaced called `LogWords.cards` at any length and wrote
  // "6 Denizens"; a note's `Cards` argument goes through `LogWords.listed`,
  // which above `LogWords.Inline` (5) writes "6 cards" when the viewer
  // identifies none of them and a `LogSpan.Cards` span when it identifies
  // some. Recorded in the wording ledger.
  private val faeMerchant: CardId = CatalogNames.denizen("Fae Merchant")
  private val secondChance: CardId = CatalogNames.denizen("Second Chance")
  private val piedPiper: CardId = CatalogNames.denizen("Pied Piper")

  test("a draw above five cards is counted, not listed by its backs"):
    val six = Vector(roar, faeMerchant, secondChance, piedPiper, mushrooms,
      mercenaries)
    assertEquals(said(ActionRef.Search, SearchProcedure.drewFromDeck,
      NoteArg.Cards(six)), "Drew 6 cards from the World Deck")

  /** A relic named rather than spelled by id, which renders as its back
    * here, with no state behind it. `birdsong` above is the denizen. */
  private val wine: CardId = CatalogNames.relic("Wine of Welcome")

  test("a things argument joins sites, a pawn, a card and a banner"):
    assertEquals(spoken(ActionRef.Campaign, CampaignNotes.targets,
        NoteArg.Things(Vector(NoteThing.Site(brokenPeaks),
          NoteThing.Pawn(PlayerId("p2")), NoteThing.Card(wine),
          NoteThing.Banner(Banner.DarkestSecret)))).map(_.text).mkString,
      "Targets: Broken Peaks, p2's pawn, a Relic and Darkest Secret")

  test("Campaign's pool, roll and sacrifice lines read as the rule wrote " +
      "them"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.pools,
      NoteArg.Number(3), NoteArg.Number(2)),
      "Attack Pool: 3, Defense Pool: 2")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.attack,
      NoteArg.Number(5)), "Attack: 5")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.attackWithSkulls,
      NoteArg.Number(5), NoteArg.Number(1)), "Attack: 5 with 1 skull")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.attackWithSkullKills, NoteArg.Number(6),
      NoteArg.Number(2), NoteArg.Number(4)),
      "Attack: 6 with 2 skulls, killing 4 warbands")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.defense,
      NoteArg.Number(3)), "Defense: 3")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.sacrificed,
      NoteArg.Number(1), NoteArg.Number(4)),
      "Sacrificed 1 warband for an attack of 4")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.sacrificed,
      NoteArg.Number(3), NoteArg.Number(6)),
      "Sacrificed 3 warbands for an attack of 6")

  test("Campaign's loss lines name the loser, except the actor"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.lost,
      NoteArg.Number(2)), "Lost 2 warbands")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.lost,
      NoteArg.Number(1)), "Lost 1 warband")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.playerLost,
      NoteArg.Player(PlayerId("p2")), NoteArg.Number(1)),
      "p2 lost 1 warband")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.banditsLost,
      NoteArg.Number(3)), "The bandits lost 3 warbands")

  test("a defeated Raid defender's clauses read in the rule's order"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.defenderLost,
      NoteArg.Player(PlayerId("p2")), NoteArg.Clauses(Vector(
        NoteClause.BurnedFavor(1),
        NoteClause.Discarded(Vector(birdsong)),
        NoteClause.SetAside(Vector(wine))))),
      "p2 burned 1 favor, discarded a Denizen and set aside a Relic")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.sentTo,
      NoteArg.Player(PlayerId("p2")), NoteArg.Site(brokenPeaks)),
      "p2 was sent to Broken Peaks")

  test("Campaign's winner, spoils, placement and plan lines"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.playerWins,
      NoteArg.Player(PlayerId("p1"))), "p1 wins!")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.banditsWin),
      "The bandits win!")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.took,
      NoteArg.Things(Vector(NoteThing.Banner(Banner.DarkestSecret))),
      NoteArg.Player(PlayerId("p2"))),
      "Took Darkest Secret from p2")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.placed,
      NoteArg.Placements(Vector(2 -> brokenPeaks))),
      "Placed 2 warbands on Broken Peaks")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.placed,
      NoteArg.Placements(Vector(1 -> brokenPeaks))),
      "Placed 1 warband on Broken Peaks")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.activatedPlan,
      NoteArg.Card(birdsong)), "Activated a Denizen")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.playerActivatedPlan, NoteArg.Player(PlayerId("p2")),
      NoteArg.Card(birdsong)), "p2 activated a Denizen")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.banditsActivatedPlan, NoteArg.Card(birdsong)),
      "The bandits activated a Denizen")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.activatedOathkeeperTitle,
      NoteArg.Player(PlayerId("p2"))),
      "p2 activated the Oathkeeper title")
    assertEquals(said(ActionRef.Campaign,
      CampaignNotes.activatedUsurperTitle, NoteArg.Player(PlayerId("p2"))),
      "p2 activated the Usurper title")

  test("a Campaign that ended before its battle says why"):
    assertEquals(said(ActionRef.Campaign, CampaignNotes.skipped),
      "Skipped the Campaign.")
    assertEquals(said(ActionRef.Campaign, CampaignNotes.nothingToAttack),
      "Nothing left to attack: the Campaign ended.")
