package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.{GameIntent, WalkerStartArgWire}

/** The consent intents bind their sender as requester or granter. */
class ConsentIntentMapperSuite extends munit.FunSuite:
  private val red = PlayerId("red")
  private def failure(path: String, message: String) =
    Left(GameIntentMappingFailure(path, message))

  test("a consent request binds its sender as the requester"):
    assertEquals(GameIntentMapper.bind(red, GameIntent.RequestConsent(
      "travel.destination", WalkerStartArgWire("site", "site:deep-woods"))),
      Right(GameCommand.RequestConsent(red, "travel.destination",
        DecisionOptionRef.Site(SiteId("site:deep-woods")))))

  test("an answer binds its sender as the granter"):
    assertEquals(GameIntentMapper.bind(red, GameIntent.AnswerConsent("never")),
      Right(GameCommand.AnswerConsent(red, ConsentChoice.Never)))
    assertEquals(GameIntentMapper.bind(red, GameIntent.AnswerConsent("maybe")),
      failure("$.intent.choice", "invalid consent choice 'maybe'"))

  test("a preference binds its sender as the granter"):
    assertEquals(GameIntentMapper.bind(red, GameIntent.SetConsentPreference(
      "site.narrow-pass.pass", "blue", "case-by-case")),
      Right(GameCommand.SetConsentPreference(red,
        PowerId("site.narrow-pass.pass"), PlayerId("blue"),
        ConsentSetting.CaseByCase)))
    assertEquals(GameIntentMapper.bind(red, GameIntent.SetConsentPreference(
      "site.narrow-pass.pass", "blue", "sometimes")),
      failure("$.intent.setting", "invalid consent setting 'sometimes'"))
