package oathdigital.frontend

/** The in-memory store the log suites use, held to the store contract. */
class FakeLogStoreSuite extends LogStoreContract:
  protected def make(): LogStore = new FakeLogStore()
