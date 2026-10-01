package oathdigital.catalog

import oathdigital.model.{CardId, CatalogRef, DenizenId, EdificeId, LegacyId,
  PowerId, RelicId, SiteId, Suit}

/**
 * The complete runtime component catalog: card objects indexed by id and by
 * printed power. `gameplay.cards.NewFoundations.catalog` is the production
 * one; a test may `copy` in variant cards. Setup cards, player boards and
 * Visions are rules-owned code, not catalog data.
 */
final case class ExecutableCatalog(
    ref: CatalogRef,
    denizens: Vector[Denizen],
    relics: Vector[Relic],
    edifices: Vector[Edifice],
    legacies: Vector[Legacy],
    sites: Vector[Site]
):
  def denizen(id: DenizenId): Option[Denizen] = denizenById.get(id.value)
  def relic(id: RelicId): Option[Relic] = relicById.get(id.value)
  def edifice(id: EdificeId): Option[Edifice] = edificeById.get(id.value)
  def legacy(id: LegacyId): Option[Legacy] = legacyById.get(id.value)
  def site(id: SiteId): Option[Site] = siteById.get(id)

  /** The card whose printed powers include `power`. */
  def denizenWithPower(power: PowerId): Option[Denizen] =
    denizenByPower.get(power)
  def relicWithPower(power: PowerId): Option[Relic] = relicByPower.get(power)
  /** Either face's powers count. */
  def edificeWithPower(power: PowerId): Option[Edifice] =
    edificeByPower.get(power)
  def siteWithHandler(handler: PowerId): Option[Site] =
    siteByHandler.get(handler)

  /** A power printed on a denizen, relic, edifice face or legacy. Sites carry
    * handler IDs only, so a site handler has no printed power.
    */
  def printedPower(id: PowerId): Option[PrintedPower] = powerById.get(id)

  /** Suit of a denizen or edifice; other card kinds have none. */
  def suitOf(id: CardId): Option[Suit] =
    denizenById.get(id.value).map(_.suit)
      .orElse(edificeById.get(id.value).map(_.suit))

  // Built on first use, so a test's `copy` indexes its own components. The
  // first entry for a key wins, matching the scans these replace.
  private def firstBy[K, A](entries: Iterable[(K, A)]): Map[K, A] =
    entries.foldLeft(Map.empty[K, A]) { case (index, (key, value)) =>
      if index.contains(key) then index else index.updated(key, value)
    }

  private lazy val denizenById = firstBy(denizens.map(d => d.id.value -> d))
  private lazy val relicById = firstBy(relics.map(r => r.id.value -> r))
  private lazy val edificeById = firstBy(edifices.map(e => e.id.value -> e))
  private lazy val legacyById = firstBy(legacies.map(l => l.id.value -> l))
  private lazy val siteById = firstBy(sites.map(s => s.id -> s))
  private lazy val denizenByPower =
    firstBy(denizens.flatMap(d => d.powers.map(_.id -> d)))
  private lazy val relicByPower =
    firstBy(relics.flatMap(r => r.powers.map(_.id -> r)))
  private lazy val edificeByPower = firstBy(edifices.flatMap(e =>
    (e.intact.powers ++ e.ruined.powers).map(_.id -> e)))
  private lazy val siteByHandler =
    firstBy(sites.flatMap(s => s.handlers.map(PowerId(_) -> s)))
  private lazy val powerById = firstBy(
    (denizens.flatMap(_.powers) ++ relics.flatMap(_.powers) ++
      edifices.flatMap(e => e.intact.powers ++ e.ruined.powers) ++
      legacies.flatMap(_.powers)).map(p => p.id -> p))
