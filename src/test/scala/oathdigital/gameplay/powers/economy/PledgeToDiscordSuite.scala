package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.HonorGuard
import oathdigital.gameplay.powers.cardplay.WildCry
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, PhasePowerHarness,
  PowerRules, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Pledge to Discord (catalog batch 8, slice 8): every reader of its
  * holder's faceup adviser suits sees Discord. One test per kind of
  * reader: a Trade's matching advisers, a target rule with the bank a
  * bury returns to, and a card played to the holder's advisers. */
class PledgeToDiscordSuite extends munit.FunSuite:
  private val pledge = CatalogNames.denizen("Pledge to Discord")
  private val marriage = CatalogNames.denizen("Marriage")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val home = CatalogNames.site("Ancient City")
  /** A plain Discord denizen. */
  private val plainDiscord = SearchFixture.anyPlainDenizensOf(Suit.Discord)
    .head
  private val rules = PowerRules.withWalkerPowers

  test("a Trade with a Discord card counts every faceup adviser of the " +
      "holder, Marriage as two"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, pledge)
      .adviser(p1, marriage).adviser(p1, wrestlers)
      .denizen(plainDiscord, at = home).secrets(p1, 1)
      .bankFavor(Suit.Discord, 6).ready
    val begun = accepted(MenuPlay.started(rules, Ready(ready), p1,
      ActionRef.Trade, Vector(DecisionOptionRef.Button("favor"))))
    val done = accepted(rules.resolveWalker(begun.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(plainDiscord))))
    val end = Look(done.state)
    // 1, and 4 more: Pledge, Marriage twice, Wrestlers.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 5)
    assertEquals(end.bankFavor(Suit.Discord), 1)

  test("Honor Guard targets a holder with no Order adviser, and the " +
      "buried adviser's favor goes to the Discord bank"):
    val guard = CatalogNames.denizen("Honor Guard")
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, home)
      .adviser(p1, guard).favor(p1, 3).pawn(p2, at = home)
      .adviser(p2, pledge).adviser(p2, wrestlers)
      .tokens(wrestlers, favor = 1).ready
    val power = HonorGuard.forCatalog(catalog)
    val source = DecisionOptionRef.Denizen(guard)
    val asked = accepted(PhasePowerHarness.use(ready, power, source))
    assertEquals(PhasePowerHarness.offered(asked, p1), Some(Vector(
      "denizen" -> pledge.value, "denizen" -> wrestlers.value)))
    val PhasePowerHarness.UsedAndAnswered(_, done) =
      PhasePowerHarness.useAndAnswer(ready, power, source,
        HonorGuard.decisionId, DecisionOptionRef.Denizen(wrestlers))
    val end = PhasePowerHarness.after(done)
    assertEquals(end.banks.favor.getOrElse(Suit.Discord, 0),
      ready.banks.favor.getOrElse(Suit.Discord, 0) + 1)
    assertEquals(end.banks.favor.getOrElse(Suit.Order, 0),
      ready.banks.favor.getOrElse(Suit.Order, 0))

  test("a Beast card played faceup to the holder's advisers is Discord, " +
      "so Wild Cry gives nothing; played to a site it gives"):
    import PowerFixture.actor
    val wildCry = CatalogNames.denizen("Wild Cry")
    val beast = CatalogNames.denizen("Errand Boy")
    val fillers = Vector("Homesteaders", "Land Warden")
      .map(CatalogNames.denizen(_))
    val ready = Table.from(SearchFixture.staged(Vector(beast) ++ fillers,
      supply = 5)).denizen(wildCry, at = home).adviser(actor, pledge)
      .ready
    // 5 Supply less the world Search's 2.
    val faceup = SearchFixture.after(SearchFixture.play(ready,
      Vector(WildCry.id), beast, PlayedAs.Faceup))
    assertEquals(Look(faceup).supply(actor), 3)
    val atSite = SearchFixture.after(SearchFixture.play(ready,
      Vector(WildCry.id), beast, PlayedAs.AtSite))
    assertEquals(Look(atSite).supply(actor), 4)
