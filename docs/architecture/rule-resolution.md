# Typed rule resolution

Oath Digital executes component powers through explicit typed Scala behavior.
Catalog handler IDs select registered behavior; catalog `rulesText` is
documentation and is never an executable language.

## Facts, inventory, and activation

`RuleSourceIndex` is the factual inventory for the current game. It emits
stable `RuleSourceRef` identities for sites, site cards and edifice faces,
advisers, relics, banners, Foundations, legacies, and game rules together with
their declared handler IDs and state.

`CatalogHandlerInventory` independently enumerates the complete catalog
handler vocabulary and produces a stable fingerprint. Bounded modules pin or
classify that inventory so a catalog change cannot silently bypass an audit.

A command/query chooses which factual sources are active and relevant. Facedown,
remote, inaccessible, or wrong-window sources remain factual but are activated
only when the printed rule permits it. Relevant missing behavior produces a
typed unsupported violation with handler and source identity.

## Registries and ordering

`RuleResolution.scala` defines shared activation/query/outcome types and
deterministic ordering. Small generic modifiers use explicit registries such as
`RuntimeRuleRegistry`. Action-specific systems may own narrower registries:
Campaign plans author side/window-scoped options in
`gameplay/actions/CampaignPlans.scala`; other bounded actions use exact-ID
classifications beside their rule modules.

Order is explicit: priority, stable source key, then handler ID unless the
owning procedure defines a stricter printed order. Coast replacement, Pass
restriction, and Island/Mountain cost modifiers are the canonical Travel
example.

Typed outcomes may allow or block, alter costs, create a decision boundary, or
author an owning action's effect. They are not generic scripts. Reserved effect
forms reject until both a registered handler and a window-specific executor
exist.

## Commands, projection, and replay

Commands and `LegalActionProjector` call the same gameplay legality APIs.
Events record the fully resolved durable facts. Replay resolves the same
sources/handlers again and rejects changed source, order, cost, decision, or
resource result. The frontend receives only shared projection DTOs and never
runs the registry.

Campaign defender decisions can belong to a non-active player. Bandit choices
use a deterministic policy only for cost-free, choice-free registered options;
paid, ambiguous, or unsupported relevant behavior blocks.

## Window-driven power runtime

`PowerRegistry` and `PowerResolver` keep four concepts separate: factual sources
discovered by `RuleSourceIndex`, precise procedure windows, implemented typed
handlers, and reviewed powers that the pre-alpha deliberately ignores. Reviewed
definitions live under `gameplay/powers`. Their audited catalog fingerprint
makes that last category closed:
optional unimplemented handlers are neither options nor blockers, reached
mandatory/triggered handlers emit durable `IgnoredRulesRecorded` diagnostics,
and a changed handler vocabulary still rejects.

Actorless preview requests contain the expected journal position, base action
parameters, and ordered source/handler references. Preview is stateless and
does not create pending state or events. The final command carries the same
order and the application service revalidates it against the newly loaded
authoritative state. Empty option sets skip the modifier stage; the frontend
selection model nevertheless preserves click order, keyboard reordering, and
clears stale drafts when context, candidates, or preview identity changes.

The runtime is connected to Travel, Search, Campaign, Muster, Trade, Forge, and
Recover plus Wake, Rest, card-play, and post-action windows; reviewed
Negotiation definitions are indexed for its existing explicit blocking boundary.
It does not
replace action ownership: Travel still resolves automatic Coast/Island/
Mountain/Pass topology, Campaign retains its later attacker/defender/bandit
battle-plan windows, and Economy target choice remains an explicit confirm.
`PowerRuntime` translates precise resolver results into the current command and
durable-event shapes. `MajorActionPowerShell` has no production or test callers;
its old implementation remains only as removal debt while the stable
`MajorActionKind`, `RuleTiming`, invocation, and diagnostic wire types still
share that source file. No component effect or universal effect language is
introduced by this layer.

## Adding a power

1. Verify the exact catalog handler and printed source.
2. Confirm `RuleSourceIndex` exposes the needed factual state and
   `CatalogHandlerInventory` covers the family.
3. Add an explicit typed registration/classification in the owning action or
   timing module.
4. Reuse the owning legality/evolution path for commands, projection, and replay.
5. Test ordering, inactive sources, replay tampering, and unsupported inventory.
