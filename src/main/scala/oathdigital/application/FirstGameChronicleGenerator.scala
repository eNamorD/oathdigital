package oathdigital.application

import oathdigital.catalog.{ExecutableCatalog, RelicRole}
import oathdigital.model._

sealed trait ChronicleGeneratorFailure extends Product with Serializable
object ChronicleGeneratorFailure {
  final case class WrongSiteCount(actual: Int) extends ChronicleGeneratorFailure
  final case class TooFewSuitDenizens(suit: Suit, implemented: Int, unimplemented: Int)
      extends ChronicleGeneratorFailure
  final case class InvariantViolated(detail: String) extends ChronicleGeneratorFailure
}

/**
 * Produces a random first-game Chronicle (2026-09-21 Chronicle design, "The
 * first-game generator"): all 24 sites shuffled into the atlas box, each
 * Homeland carrying its suit's edifice (its implemented one when the suit
 * has one; otherwise the lowest-id edifice of that suit, which plays inert
 * with the existing ignored-rule diagnostic -- a Homeland is never left
 * without its edifice card just because none of its suit's five are
 * implemented yet); a 60-denizen world deck, 10 per suit (every implemented
 * denizen of the suit, up to 10, topped up with random unimplemented ones);
 * 12 dispossessed denizens, 2 unimplemented per suit, drawn from what the 60
 * left behind; and the full ordinary relic deck. Both decks are ordered
 * implemented-first by `policy`. `implemented` is the per-power answer
 * `ImplementedCardCatalog` reads. Self-validates the counts before returning.
 */
object FirstGameChronicleGenerator {
  import ChronicleGeneratorFailure._

  def generate(catalog: ExecutableCatalog, implemented: PowerId => Boolean,
      random: ChronicleRandomPort, policy: ShufflePolicy)
      : Either[ChronicleGeneratorFailure, Chronicle] =
    for {
      atlas <- atlasBox(catalog, implemented, random)
      pools <- denizenPools(catalog, implemented, random)
      (worldPool, dispossessedPool) = pools
      implementedDenizens = ImplementedCardCatalog.denizens(catalog, implemented)
      implementedRelics = ImplementedCardCatalog.ordinaryRelics(catalog, implemented)
      relicPool = catalog.relics.filter(_.role == RelicRole.Ordinary)
        .map(r => RelicId(r.id.value))
      chronicle = Chronicle(
        atlas,
        worldDeck = policy.order(worldPool, implementedDenizens, random),
        relicDeck = policy.order(relicPool, implementedRelics, random),
        dispossessed = dispossessedPool)
      _ <- validate(catalog, chronicle)
    } yield chronicle

  private def atlasBox(catalog: ExecutableCatalog, implemented: PowerId => Boolean,
      random: ChronicleRandomPort)
      : Either[ChronicleGeneratorFailure, Vector[StoredSite]] = {
    val sites = catalog.sites.map(_.id)
    if (sites.size != 24) Left(WrongSiteCount(sites.size))
    else Right(random.shuffle(sites).map { siteId =>
      homelandSuit(catalog, siteId) match {
        case None => StoredSite(siteId)
        case Some(suit) =>
          StoredSite(siteId, Vector(edificeForHomeland(catalog, implemented, suit)))
      }
    })
  }

  /** The suit's implemented edifice when it has one; otherwise the lowest-id
    * edifice of that suit, so a Homeland always carries an edifice card even
    * when none of its suit's five are implemented yet. */
  private def edificeForHomeland(catalog: ExecutableCatalog,
      implemented: PowerId => Boolean, suit: Suit): EdificeId =
    ImplementedCardCatalog.homelandEdifice(catalog, suit, implemented).getOrElse(
      EdificeId(catalog.edifices.filter(_.suit == suit).map(_.id.value).min))

  private val PerSuit = 10
  private val DispossessedPerSuit = 2

  /** Per suit: every implemented denizen (up to 10, chosen at random past
    * that) goes into the world deck, random unimplemented ones fill it to 10,
    * and the next 2 unimplemented ones are dispossessed. */
  private def denizenPools(catalog: ExecutableCatalog,
      implementedPower: PowerId => Boolean, random: ChronicleRandomPort)
      : Either[ChronicleGeneratorFailure, (Vector[DenizenId], Vector[DenizenId])] = {
    val implemented = ImplementedCardCatalog.denizens(catalog, implementedPower)
    Suit.all.foldLeft[Either[ChronicleGeneratorFailure,
        (Vector[DenizenId], Vector[DenizenId])]](Right(Vector.empty -> Vector.empty)) {
      (acc, suit) =>
      acc.flatMap { case (worldPool, dispossessedPool) =>
        val suited = catalog.denizens.filter(_.suit == suit)
          .map(d => DenizenId(d.id.value))
        val (impl, unimpl) = suited.partition(implemented)
        val chosenImpl = random.shuffle(impl).take(PerSuit)
        val filler = PerSuit - chosenImpl.size
        if (unimpl.size < filler + DispossessedPerSuit)
          Left(TooFewSuitDenizens(suit, impl.size, unimpl.size))
        else {
          val shuffledUnimpl = random.shuffle(unimpl)
          Right((worldPool ++ chosenImpl ++ shuffledUnimpl.take(filler),
            dispossessedPool ++
              shuffledUnimpl.slice(filler, filler + DispossessedPerSuit)))
        }
      }
    }
  }

  private def homelandSuit(catalog: ExecutableCatalog, siteId: SiteId): Option[Suit] =
    catalog.sites.find(_.id == siteId).get.handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)

  private def validate(catalog: ExecutableCatalog, chronicle: Chronicle)
      : Either[ChronicleGeneratorFailure, Unit] = {
    val suitOf = catalog.denizens.map(d => DenizenId(d.id.value) -> d.suit).toMap
    def perSuitCount(ids: Vector[DenizenId]): Map[Suit, Int] =
      Suit.all.map(suit => suit -> ids.count(id => suitOf.get(id).contains(suit))).toMap

    if (chronicle.worldDeck.size != 60)
      Left(InvariantViolated(
        s"world deck must have 60 denizens, has ${chronicle.worldDeck.size}"))
    else if (chronicle.worldDeck.distinct.size != 60)
      Left(InvariantViolated("world deck denizens must be unique"))
    else if (perSuitCount(chronicle.worldDeck).values.exists(_ != 10))
      Left(InvariantViolated("world deck must have 10 denizens per suit"))
    else if (chronicle.dispossessed.size != 12)
      Left(InvariantViolated(
        s"dispossessed must have 12 denizens, has ${chronicle.dispossessed.size}"))
    else if (chronicle.dispossessed.distinct.size != 12)
      Left(InvariantViolated("dispossessed denizens must be unique"))
    else if (perSuitCount(chronicle.dispossessed).values.exists(_ != 2))
      Left(InvariantViolated("dispossessed must have 2 denizens per suit"))
    else if ((chronicle.worldDeck.toSet intersect chronicle.dispossessed.toSet).nonEmpty)
      Left(InvariantViolated("world deck and dispossessed must not overlap"))
    else Right(())
  }
}
