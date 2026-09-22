> **PARTIALLY SUPERSEDED — replay/command/action-model parts change under the
> procedure-walker redesign**: replay applies recorded ops only (no evolve
> re-derive); the command surface collapses to Start/Resolve/RollSubmitted;
> `PendingProcedure` becomes a `PendingTree` pointer; dice pools/roll outcomes
> become state; `usedPowers` tracking stays. Storage/journal/domain-foundation
> content here remains valid.

# Core domain model

Status: accepted foundation, reviewed August 2026.

This note records the durable boundary decisions for the core model. It is not
a gameplay-rules specification.

## Boundaries

- `oathdigital.model` owns immutable game and campaign state, typed component
  identities, container-owned card state, derived card locations, and
  aggregate validation.
- `oathdigital.engine` owns the deterministic transition and journal
  abstractions. Concrete Oath commands, events, continuations, and rules do not
  belong in the state model.
- `oathdigital.presentation` consumes projections. Presentation IDs and image
  references are not domain identities and never become authoritative state.
- `docs/catalog/new-foundations-component-catalog.json` is component
  definition data. Runtime state refers to its printed component identities;
  card text and handlers remain catalog/application concerns.

## Accepted identity and state decisions

- Printed cards are singletons. A typed `CardId` is both definition identity
  and physical-card identity; there is no `CardInstanceId`.
- `CatalogRef(ruleset, version)` pins an `OathGame` to the catalog's
  `ruleset.id` and `catalogVersion`. Catalog schema versioning is independent
  of saved-game versioning.
- State is owned by its container. Decks, discards, the Reliquary,
  Dispossessed, and reserves store IDs because cards there have no mutable
  runtime state. Placed denizens, advisers, relics, edifices, and legacies
  store their state in their owning container.
- `CardIndex` is a derived, non-serialized reverse lookup. It detects duplicate
  or missing cards and provides convenient location/state access without
  creating a second source of truth.
- The Atlas is one ordered sequence: the head is Recent and the last element is
  Forgotten. Recent removal returns front-to-back removal order; Forgotten
  removal returns back-to-front removal order. An encountered Empire divider
  is removable but does not count toward the requested number of sites.
- Supply is remaining spendable Supply in the inclusive range 0 through 7, not
  a marker coordinate. Refresh tables are board data.
- A pawn location is optional during setup. A lineage may retain multiple
  ordered starting advisers.
- Banner families have distinct typed face values and one active face in
  runtime state. Their held favor or secrets stay with the banner family when
  it flips.

## Aggregate invariants

Value constructors reject blank identities, negative resources and forces,
invalid Supply, invalid integer ranges, and invalid round values.
`DomainValidation` checks invariants that require the whole aggregate:

- region sizes, unique map sites, and exact agreement between topology and site
  state;
- unique players and active lineage assignments;
- lineage map keys matching their embedded IDs and all six Foundation slots;
- sites appearing at most once in the Atlas and never both in play and stored;
- player lineage and pawn references, current banner/title holders, active
  player, and Exile-force lineage references;
- at most one Chancellor lineage; and
- card uniqueness plus optional completeness against a caller-supplied catalog
  card-ID set.

Resource conservation is deliberately not asserted by the structural model.
The legal totals and exceptions depend on setup, player count, component
powers, and the exact ruleset. Those checks belong beside the rules that move
the resources.

## Serialization boundary

The case classes are the in-memory model, not a permanent wire format. A saved
game must use an explicit envelope containing:

- a save-format version;
- the `CatalogRef`;
- deterministic random state or a replayable record of random outcomes; and
- either a versioned state payload or the authoritative action/event journal.

Do not serialize `CardIndex`, presentation views, or Scala class names as
external type discriminators. Oath Digital uses explicit versioned event
envelopes and reconstructs state through deterministic replay; see
[authoritative-events.md](authoritative-events.md).

## Certain versus rules-dependent

The identity, Atlas, Supply, banner, card-location, map-region, player/lineage,
campaign/current-game, and projection decisions above are accepted. The
machine-readable component catalog is authoritative for its inventory, printed
identities, reviewed text, restrictions, and stable handler keys. Catalog
validity does not imply that every handler has executable behavior;
unsupported relevant powers must fail explicitly.

The following remain rules-dependent and are not foundation invariants:

- exact card powers, costs, suit attributes, site capacity, and component
  limits;
- transaction boundaries for nested powers and state-based checks;
- hidden-information and reveal policy;
- consent timing, simultaneous-effect ordering, and context-specific ties;
- exact Chronicle transformations and first-game exceptions; and
- seeded-random command/event semantics.

These items are tracked in `docs/rules/ambiguities.md` and should be resolved in
the vertical slice that first needs them. Current behavior belongs in
`docs/rules/implementation-traceability.md`; scheduling belongs in the roadmap.
