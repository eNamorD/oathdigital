package oathdigital.protocol

import oathdigital.protocol.projection.OathkeeperPresentation

/** The Oath as the four goal cards print it: the title, the goal the
  * Oathkeeper holds it by, and the successor clause on the purple band.
  */
class OathkeeperPresentationSuite extends munit.FunSuite {
  test("every goal key carries its printed title and both lines") {
    assertEquals(OathkeeperPresentation.byGoal("supremacy"),
      OathkeeperPresentation("Oathkeeper of Supremacy", Vector(
        "Rules the most sites",
        "Successor to the Chancellor: Holds more relics")))
    assertEquals(OathkeeperPresentation.byGoal("protection"),
      OathkeeperPresentation("Oathkeeper of Protection", Vector(
        "Holds the most relics",
        "Successor to the Chancellor: Holds the People's Favor")))
    assertEquals(OathkeeperPresentation.byGoal("devotion"),
      OathkeeperPresentation("Oathkeeper of Devotion", Vector(
        "Holds the Darkest Secret",
        "Successor to the Chancellor: Holds the Grand Scepter")))
    assertEquals(OathkeeperPresentation.byGoal("the-people"),
      OathkeeperPresentation("Oathkeeper of the People", Vector(
        "Holds the People's Favor",
        "Successor to the Chancellor: Holds the Darkest Secret")))
  }

  test("the map covers every goal the model declares") {
    assertEquals(OathkeeperPresentation.byGoal.keySet,
      oathdigital.model.OathkeeperGoal.all.map(_.key).toSet)
  }
}
