package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.model._

/** Staging shared by the banner-face suites. The first game seats three
  * players: the actor (p2) at ancient-city, p1 at buried-giant and p3 at
  * broken-peaks. Both banners start unheld, on Mob and Wandering Flame.
  */
object BannerFixture {
  import PowerFixture._

  val p1: PlayerId = PlayerId("p1")
  val p3: PlayerId = PlayerId("p3")

  val ancientCity: SiteId = SiteId("site:ancient-city")
  val brokenPeaks: SiteId = SiteId("site:broken-peaks")
  val buriedGiant: SiteId = SiteId("site:buried-giant")
  val deepWoods: SiteId = SiteId("site:deep-woods")

  val darkestSecret: DecisionOptionRef =
    DecisionOptionRef.Banner(Banner.DarkestSecret)
  val peoplesFavor: DecisionOptionRef =
    DecisionOptionRef.Banner(Banner.PeoplesFavor)

  /** The actor holds the Banner of the Darkest Secret, on `face`. */
  def holdingFlame(ready: ReadyGame,
      face: DarkestSecretFace = DarkestSecretFace.WanderingFlame,
      holder: Option[PlayerId] = Some(actor)): ReadyGame =
    ready.updateCurrent(c => c.copy(banners = c.banners.copy(darkestSecret =
      c.banners.darkestSecret.copy(active = face, holder = holder))))

  /** The actor holds the Banner of the People's Favor, on `face`. */
  def holdingFavor(ready: ReadyGame,
      face: PeoplesFavorFace = PeoplesFavorFace.Mob,
      holder: Option[PlayerId] = Some(actor)): ReadyGame =
    ready.updateCurrent(c => c.copy(banners = c.banners.copy(peoplesFavor =
      c.banners.peoplesFavor.copy(active = face, holder = holder))))

  def withSiteSecrets(ready: ReadyGame, site: SiteId, secrets: Int)
      : ReadyGame = ready.updateCurrent(c => c.copy(map = c.map.copy(
    sites = c.map.sites.updated(site, c.map.sites(site).copy(tokens =
      c.map.sites(site).tokens.copy(secrets = secrets))))))

  /** No site holds a secret of its own. The first game starts with some (a
    * site's printed starting resources), so a test that counts them clears
    * them first.
    */
  def withoutSiteSecrets(ready: ReadyGame): ReadyGame =
    ready.game.current.map.inPlay.foldLeft(ready)(withSiteSecrets(_, _, 0))

  /** `card`, already at `site`, holds `tokens`. */
  def withCardTokens(ready: ReadyGame, site: SiteId, card: DenizenId,
      tokens: Tokens): ReadyGame = ready.updateCurrent(c => c.copy(map =
    c.map.copy(sites = c.map.sites.updated(site, c.map.sites(site).copy(
      denizens = c.map.sites(site).denizens.map {
        case d: DenizenState if d.id == card => d.copy(tokens = tokens)
        case other => other
      })))))

  def siteSecrets(ready: ReadyGame, site: SiteId): Int =
    ready.game.current.map.sites(site).tokens.secrets

  def pawnOf(ready: ReadyGame, id: PlayerId = actor): SiteId =
    player(ready, id).pawnSite.get

  def usable(ready: ReadyGame, power: PowerId): Boolean =
    PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog)).exists(_.power.id == power)

  def backToActing(transition: OathTransition): Boolean =
    transition.continue == OathContinue.ActActionSelection(actor)

  def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: oathdigital.gameplay.walker.WalkerStepRecorded =>
      step.ops }.flatten
}
