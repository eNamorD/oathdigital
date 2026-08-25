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

## Adding a power

1. Verify the exact catalog handler and printed source.
2. Confirm `RuleSourceIndex` exposes the needed factual state and
   `CatalogHandlerInventory` covers the family.
3. Add an explicit typed registration/classification in the owning action or
   timing module.
4. Reuse the owning legality/evolution path for commands, projection, and replay.
5. Test ordering, inactive sources, replay tampering, and unsupported inventory.
