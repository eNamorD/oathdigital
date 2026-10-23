# Retiring the Reviewed Power Catalog: Deleted on the Evidence

> Status: decided and shipped 2026-10-23, from the architecture scan of the
> rules engine of 2026-10-23, which raised this as candidate 1 and rated it
> strong. The ROADMAP item "Retire the reviewed-catalog machinery" is closed
> by it; the [card classes design](2026-10-01-card-classes-design.md) had put
> the retirement out of its scope.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **modifier
decision**, the one reader that keeps `PowerWindow.key` alive; the
[power resolution](../../architecture/rule-resolution.md) page describes
the walker and phase catalogs that remain.

## What was there

The reviewed-catalog path was the pre-walker power resolver, kept after the
walker shipped to record which applicable powers the engine did not yet run.
It was `gameplay/PowerRuntime.scala`, `powers/PowerSupport.scala`,
`powers/ReviewedPowerCatalog.scala`, ten stub registries
(`ActionPowers` ... `NegotiationPowers`, 73 `ReviewedPower` objects),
`powerresolver/{PowerModel,PowerHandlers,PowerRegistry,PowerResolver}.scala`,
and the `OathEvent.IgnoredRulesRecorded` event with its
`IgnoredRuleDiagnostic`, `ActionKind` and `RuleTiming` vocabulary, its
lifecycle codec arms and its wire tag.

## The evidence

Two facts, both measured at `6ef76ad4` before anything was deleted.

1. **Nothing read the output.** The path's only product was the
   `IgnoredRulesRecorded` event, written at five sites (`OathRules` twice,
   `OathDriver` twice, `OathRulesWalker` once). Its one reader in
   `src/main` was `GameLogFormatter`, which mapped it to no line. No
   projection, protocol, client or server file named it. Every other
   production reader of the path's types was inside the path.

2. **The stubs carried no information.** The one place a stub's
   `implemented = true` flag could still matter was
   `PowerImplementationStatus.implemented`, which was "covered by
   `WalkerPowerCatalog`, `PhasePowerCatalog` or a printed rule" OR "every
   stub handler says implemented". A throwaway suite compared the two halves
   over every id the catalog inventory, the stubs and the catalogs know:
   445 ids, 309 implemented, 309 covered, zero ids implemented by a stub
   alone. The implemented set dumped before the deletion and after it is
   identical (`diff` empty), and `UnimplementedPowersSuite` pins the
   unimplemented set from the catalog inventory alone, so it stood as an
   independent check throughout.

Had any id been implemented only because a stub said so, the retirement
would have stopped there; none was.

## What was deleted

- The path above: 18 production files deleted outright (630 lines), and
  970 lines deleted against 43 added across `src/main`, in three commits
  that each compiled and passed their suites.
- `PowerImplementationStatus.implemented` is now the covered half alone.
- What lost its last reader with it: `RuleSourceAccess` (the pre-walker
  access rule; `PowerAccess` is the one in use), `IndexedRuleSource.handlerIds`
  (a compatibility projection read only by tests), `RuleSourceRef.parse`
  (read only by the deleted codec arm), `WalkerProcedureRegistry.Entry
  .fallbackKind` with its accessor (the column that chose the window
  `PowerRuntime` resolved against), the `ActionKind` enum, and the five
  `PowerWindow` cases no power or procedure hooked: `SearchEligibility`,
  `SearchPlayToSite`, `RecoverEligibility`, `RestReturnSecrets`, `RestEnd`.
  No codec spells a `PowerWindow`, so the wire is unchanged by that.
- Tests of the deleted resolver only: `RuleResolutionSuite`,
  `PowerResolverSuite`, `ActionKindSuite`, and three of the six tests in
  `PowerImplementationStatusSuite`. The `RuleSourceRef.stableKey` and
  `PowerId` checks the first two also held moved to `RuleSourceRefSuite`
  and `OpaqueIdSuite`. Suites that asserted on the diagnostic beside
  something else kept the something else: an unimplemented power still does
  not block Rest or a Negotiation, and a corrupt replay still reports its
  index.

## What was kept, and why

- `PowerWindow.key`, `PowerWindow.associatedMajorAction` and the nine
  `*Window` sub-traits. The scan listed them as readerless once the
  reviewed resolver went, but `ModifierPicks.decisionId` reads both to name
  a procedure's modifier decision (`search.modifiers`), which the client
  answers. They stay with that one reader.
- `PowerResolution` (`PlayerSelected` / `Automatic`): `ContributingPower
  .resolution` and `ModifierPicks` read it.
- `CatalogHandlerInventory.handlerIds`: `UnimplementedPowersSuite` and
  `CatalogHandlerInventorySuite` read it.

## Journals

A journal holding a `diagnostic.ignored-rules-recorded` envelope no longer
decodes: `GameEventWire.decode` reports `UnknownEventType` for it. The
project's standing rule is that wire changes need not keep old journals
decodable, so there is no tolerant decode arm and none is planned. Every
game saved after this change carries no such envelope, since nothing writes
one.

## Behaviour

No gameplay behaviour changed. The implemented set is the proof, and the
full gate (`check-architecture.py`, `check-markdown-links.py`, `sbt test
frontend/test`) was green on the final commit.
