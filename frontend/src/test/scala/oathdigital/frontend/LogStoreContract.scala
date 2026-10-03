package oathdigital.frontend

/** What every `LogStore` promises (the trait's doc): a key that was written
  * reads back its value, and a second write to a key replaces the first.
  * `FakeLogStore`, which the log suites use, and `LogStore.browser`, over a
  * working `localStorage`, both run it, so the fake cannot drift from the
  * real store.
  */
private[frontend] abstract class LogStoreContract extends munit.FunSuite:
  /** A new, empty store. */
  protected def make(): LogStore
  /** Undoes what `make` set up. */
  protected def release(store: LogStore): Unit = ()

  private val fresh = FunFixture[LogStore](_ => make(), release)

  fresh.test("a key that was written reads back its value"): store =>
    store.write("oath.log.seen.g1.red", "42")
    assertEquals(store.read("oath.log.seen.g1.red"), Some("42"))

  fresh.test("a second write to a key replaces the first"): store =>
    store.write("oath.log.seen.g1.red", "42")
    store.write("oath.log.seen.g1.red", "57")
    assertEquals(store.read("oath.log.seen.g1.red"), Some("57"))

  fresh.test("a key that was never written reads as nothing"): store =>
    store.write("oath.log.seen.g1.red", "42")
    assertEquals(store.read("oath.log.seen.g1.blue"), None)
