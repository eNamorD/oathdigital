package oathdigital.testkit

import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** A Recover game around Catacombs, for the suites that drive the power
  * through the rules: on the quiet table, p1 in Act at Fair Isle (Recover
  * difficulty 3, one relic slot) beside a faceup Catacombs.
  */
object CatacombsGame:
  val catacombsId: PowerId = PowerId("denizen.catacombs")
  val catacombsCard: DenizenId = CatalogNames.denizen("Catacombs")
  val fairIsle: SiteId = CatalogNames.site("Fair Isle")
  val brokenPeaks: SiteId = CatalogNames.site("Broken Peaks")

  /** The game, its actor, the site Catacombs stands at, and the relic deck's
    * top relic, which Catacombs would place. */
  final case class Game(ready: ReadyGame, actor: PlayerId, site: SiteId,
      topRelic: RelicId)

  /** Fair Isle holds no relic, and p1 has `secrets` faceup secrets. */
  def reliclessSite(secrets: Int)(using munit.Location): Game =
    game(atFairIsle(secrets), fairIsle)

  /** Fair Isle holds Dowsing Sticks facedown, and p1 has 2 faceup secrets. */
  def relicSite()(using munit.Location): Game =
    game(atFairIsle(2).relicAt("Dowsing Sticks", at = fairIsle), fairIsle)

  /** Fair Isle's one relic slot holds Dowsing Sticks faceup, and p1 has 2
    * faceup secrets: Catacombs must reject placement. */
  def fullRelicSite()(using munit.Location): Game =
    game(atFairIsle(2).relicAt("Dowsing Sticks", at = fairIsle,
      facedown = false), fairIsle)

  /** The `relicSite` game, with Catacombs moved to Broken Peaks, which has
    * free relic slots: p1 rules it with 1 warband when `ruled`, else 1 bandit
    * rules it. `site` is Broken Peaks. */
  def ruledElsewhere(ruled: Boolean = true)(using munit.Location): Game =
    val home = atFairIsle(2).relicAt("Dowsing Sticks", at = fairIsle)
      .denizen(catacombsCard, at = brokenPeaks)
    game(if ruled then home.warbandsAt(brokenPeaks, Table.p1, 1)
      else home.bandits(brokenPeaks, 1), brokenPeaks)

  private def atFairIsle(secrets: Int)(using munit.Location): Table =
    val difficulty = RecoverRules.difficulty(catalog, fairIsle)
    munit.Assertions.assert(
      Table.start.ready.game.current.map.inPlay.contains(fairIsle) &&
        difficulty.exists(d => d > 0 && d <= 4) &&
        catalog.sites.find(_.id == fairIsle).exists(_.relicSlots == 1),
      "Fair Isle must be in play, a Recover site of difficulty 4 or less, " +
        "with one relic slot")
    Table.start.pawn(Table.p1, at = fairIsle)
      .secrets(Table.p1, faceUp = secrets)
      .denizen(catacombsCard, at = fairIsle)

  private def game(table: Table, site: SiteId)(using munit.Location): Game =
    val ready = table.ready
    Game(ready, Table.p1, site, ready.game.current.commonCards.relicDeck.head)
