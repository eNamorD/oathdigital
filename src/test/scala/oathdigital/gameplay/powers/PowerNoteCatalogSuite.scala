package oathdigital.gameplay.powers

import oathdigital.gameplay.powers.action.GamblingHall
import oathdigital.gameplay.powers.campaign.VowOfPeaceContribution
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Every power's declared notes (power log lines design, "Wording"). */
class PowerNoteCatalogSuite extends munit.FunSuite:
  private val declared: Vector[(PowerId, Vector[NoteKey])] =
    WalkerPowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys) ++
    PhasePowerCatalog.default(catalog).powers.map(power =>
      power.id -> power.noteKeys)

  test("a power names each of its notes once"):
    declared.foreach { case (id, keys) =>
      assertEquals(keys.map(_.name).distinct, keys.map(_.name), id.value) }

  test("every sentence starts with an argument or a capital letter"):
    declared.foreach { case (id, keys) => keys.foreach { key =>
      val where = s"${id.value}.${key.name}"
      key.template.headOption match
        case Some(NotePart.Text(words)) =>
          assert(words.headOption.exists(_.isUpper), where)
        case Some(_) => ()
        case None => fail(s"$where has no sentence")
    } }

  test("Gambling Hall and Vow of Peace declare their notes"):
    val keys = declared.toMap.view.mapValues(_.map(_.name)).toMap
    assertEquals(keys.get(GamblingHall.id), Some(Vector(NoteKey.Used, "gained")))
    assertEquals(keys.get(VowOfPeaceContribution.id), Some(Vector("no-sacrifice")))
