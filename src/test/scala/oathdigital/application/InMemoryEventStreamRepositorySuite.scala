package oathdigital.application

/** The in-memory repository, the fake every service test uses, held to
  * the repository contract. */
class InMemoryEventStreamRepositorySuite
    extends EventStreamRepositoryContract[InMemoryEventStreamRepository]:
  protected def make() = new InMemoryEventStreamRepository
