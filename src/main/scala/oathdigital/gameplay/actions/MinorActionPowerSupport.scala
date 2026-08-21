package oathdigital.gameplay.actions

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{CardId, DenizenId, VisionId}
import oathdigital.setup.OathViolation
import oathdigital.setup.OathViolation.{UnsupportedMinorActionCatalogInventory,
  UnsupportedMinorActionRule}

/** Transitional audited seam for component behavior relevant to base minor
  * actions. Phase 3 replaces these explicit unsupported classifications with
  * registered executable handlers.
  */
object MinorActionPowerSupport {
  private val ExpectedInventory =
    "e32d8c6e2816d07b7dddaa04c9446af80b2a3799534a102f6f00017f458f55a5"

  private val WhenPlayed = Set(
    "denizen.dazzle", "denizen.revelation", "denizen.threatening-roar",
    "denizen.animal-host", "denizen.a-small-favor", "denizen.key-to-the-city",
    "denizen.charlatan", "denizen.blackmail", "denizen.dissent",
    "denizen.false-prophet", "denizen.family-heirloom", "denizen.fabled-feast",
    "denizen.salad-days", "denizen.the-gathering", "denizen.faithful-friend",
    "denizen.great-herd", "denizen.pilgrimage", "denizen.twin-brother",
    "denizen.garrison", "denizen.royal-tax", "denizen.bewitch",
    "denizen.wizard-s-conclave", "denizen.long-lost-heir", "denizen.true-oath",
    "denizen.autumn-wind", "denizen.shifting-fog", "denizen.royal-ambitions",
    "denizen.riots", "denizen.bandit-chief", "denizen.reliquary-raid",
    "denizen.bandit-prince", "denizen.a-round-of-ale", "denizen.favored-son",
    "denizen.town-meeting", "denizen.ancient-pact", "denizen.search-party",
    "denizen.call-for-help")
  private val SearchModifiers = Set(
    "denizen.forced-labor", "denizen.hunting-party", "denizen.disciples",
    "denizen.spinning-bee")
  val Conspiracy: VisionId = VisionId("vision:conspiracy")

  def validateInventory(catalog: ExecutableCatalog): Either[OathViolation, Unit] = {
    val actual = fingerprint(catalog)
    Either.cond(actual == ExpectedInventory, (),
      UnsupportedMinorActionCatalogInventory(ExpectedInventory, actual))
  }

  def validateAdviserPlay(catalog: ExecutableCatalog, source: CardId,
      handlers: Vector[String]): Either[OathViolation, Unit] =
    validateInventory(catalog).flatMap { _ =>
      val relevant = handlers.filter(WhenPlayed)
      Either.cond(relevant.isEmpty, (), UnsupportedMinorActionRule(source, relevant))
    }

  def validateSearchModifier(catalog: ExecutableCatalog, source: DenizenId,
      handlers: Vector[String]): Either[OathViolation, Unit] =
    validateInventory(catalog).flatMap { _ =>
      val relevant = handlers.filter(SearchModifiers)
      Either.cond(relevant.isEmpty, (), UnsupportedMinorActionRule(source, relevant))
    }

  def validateVisionPlay(catalog: ExecutableCatalog,
      source: VisionId): Either[OathViolation, Unit] =
    validateInventory(catalog).flatMap(_ => Either.cond(source != Conspiracy, (),
      UnsupportedMinorActionRule(source, Vector("vision.conspiracy"))))

  private def fingerprint(catalog: ExecutableCatalog): String = {
    val canonical = catalog.denizens.sortBy(_.id.value).map { definition =>
      s"${definition.id.value}|${definition.handlers.sorted.mkString(",")}"
    }.mkString("\n")
    MessageDigest.getInstance("SHA-256")
      .digest(canonical.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
