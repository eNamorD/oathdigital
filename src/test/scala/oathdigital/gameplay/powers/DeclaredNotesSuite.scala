package oathdigital.gameplay.powers

import oathdigital.gameplay.actions.RuleNotes
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.PowerId

class DeclaredNotesSuite extends munit.FunSuite:
  private val declared = DeclaredNotes.of(catalog)

  test("it declares a walker power's key"):
    assert(declared.declares(PowerId("denizen.warning-signals"), "redistributed"))

  test("it declares a phase power's used line"):
    assert(declared.declares(PowerId("denizen.gambling-hall"), "used"))

  test("it declares a game rule's key, which no power owns"):
    val (rule, keys) = RuleNotes.all.head
    assert(declared.declares(rule, keys.head.name))

  test("it declares nothing for an unknown power or an unknown key"):
    assert(!declared.declares(PowerId("denizen.nonesuch"), "took"))
    assert(!declared.declares(PowerId("denizen.warning-signals"), "nonesuch"))

  test("a power in both catalogs declares each of its keys once"):
    val both = PowerId("denizen.assassin")
    val names = declared.keys.getOrElse(both, Vector.empty).map(_.name)
    assertEquals(names.distinct, names, names.mkString(", "))
    assert(names.nonEmpty)
