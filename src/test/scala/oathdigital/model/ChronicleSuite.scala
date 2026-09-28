package oathdigital.model

class ChronicleSuite extends munit.FunSuite:
  test("a stored site holds at most three items"):
    intercept[IllegalArgumentException]:
      StoredSite(SiteId("x"), Vector(RelicId("a"), RelicId("b"),
        RelicId("c"), RelicId("d")))
