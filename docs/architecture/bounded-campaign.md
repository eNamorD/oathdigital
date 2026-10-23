# Campaign

Design: [campaign walker design](../superpowers/specs/2026-09-19-campaign-walker-design.md)

Campaign implements the Combined Rulebook and New Foundations procedure for
Conquest against bandits or a player, and Raid against a co-located player. It
costs 2 Supply. The active player starts it from the Act menu, or a power
queues it as a follow-up, whose actor may be off turn (Sneak Attack). Every
later choice is a parked walker decision answered with `ResolveWalker`.

## Procedure

`CampaignProcedure` (`gameplay/actions/campaign/`) builds one tree in rulebook
order. It opens with the modifier decision (`CampaignModifierSelection`),
which asks nothing while no power selects there. The Campaign itself follows,
under the window `CampaignActionEligibility`:

```
Sequence(
  Sequence(CampaignCost, SpendSupply(actor, 2))
  Sequence(CampaignBeforeTargets,
    Decide(campaign.kind)                 -- omitted when one kind is legal, unless a Skip is offered
    Decide(campaign.defender)             -- Raid only, omitted with one enemy pawn
    Decide(campaign.targets)              -- optional additions, omitted when none
    EndWalk)                              -- only when no kind is settled
  Decide(campaign.force)                  -- always asked, 0 to the board's warbands
  Sequence(CampaignGatherPools, BuildOps(both dice pools))
  attacker battle plans                   -- CampaignAttackerBattlePlans
  defender battle plans                   -- CampaignDefenderBattlePlans
  Roll(campaign.attack, Automatic)        -- CampaignAttackRoll
  CampaignAttackResult                    -- capped attack written as a roll outcome
  Sequence(CampaignSkullLosses,           -- the skull losses die
    Decide(campaign.skull-origin-deaths)  -- only with a queued origin and a real split
    BuildOps(kill))
  Decide(campaign.sacrifice)              -- omitted when nothing survives the skulls
  Sequence(CampaignSacrificeLosses,       -- the sacrificed warbands die
    Decide(campaign.sacrifice-origin-deaths)
    BuildOps(kill))
  Roll(campaign.defense, Automatic)       -- CampaignDefenseRoll
  CampaignDefenseResult                   -- dice score plus the defender's force
  Sequence(CampaignAfterOutcome, RecordCampaignResult)
  Branch(lastCampaignResult):
    CampaignLosses
    victory: Conquest placement, or Raid transfer and relocation)
```

Careful Plans' rewrite at `CampaignActionEligibility` puts the seven steps from
the attack roll to the defense result in one `Branch`, whether or not the plan
is in play, so the root keeps one shape on every fold. When the attacker chose
it, the branch walks the defense roll and its result first; the sacrifice still
follows the attack roll, the skull losses still die right after the attack
result, and the sacrificed warbands right after the sacrifice.

- **Ending early.** The last before-targets step runs `EndWalk` when no kind
  is settled, which ends the walk there: no force, no dice, no result and no
  after-Campaign step. That happens when a power at the Campaign's start left
  no legal kind, or when the attacker answered "Skip Campaign". A power offers
  the Skip by recording the marker pool `campaign.skip-offered` (Vow of Union
  does so when it moved a warband); the kind question is then asked even with
  one legal kind. The Supply stays spent.
- **Start.** A start is refused unless at least one kind is legal; the
  procedure does not check the phase. Conquest is legal when the actor's pawn
  site is ruled by Bandits or by another player. Raid is legal when another
  player's pawn stands at the actor's site. Supply is not a gate: `SpendSupply`
  owns it, so a start with too little Supply fails at the first step before
  anything is persisted. The Act menu's Campaign option is available exactly
  when a dry run of that first walk accepts.
- **Targets.** Only the mandatory piece is fixed: the Conquest pawn site or the
  Raid enemy pawn. `campaign.targets` answers the optional additions: other sites
  ruled by the same defender, in map order, for a Conquest; the defender's faceup
  relics and held banners, in stable order, for a Raid. The full set is the
  mandatory piece plus the answer, canonicalised.
- **Force.** `ChooseAmount(0, board warbands)`. A zero-force Campaign is legal
  (the Empty Attack Pool rule). Nothing moves for the force: committed warbands
  stay on the board until they die or are placed.
- **Pools.** Both pools are gathered once, after the force answer, because nothing
  on the board changes before the terminal steps. The attack pool holds the
  committed force. The defense pool holds the targets' printed defense: for a
  Raid, two for the pawn, each targeted relic's printed defense and three per
  banner. A pool of zero dice is not created and is never rolled.
- **Battle plans.** Each side's window is a `Repeat` of `CampaignPlanChoice` passes. The node is an
  `OfferHost`: its window gathers the `Offer` contributions of the powers in play, and each pass asks
  the user to choose one plan they can pay for, or Finish, and applies it as a
  `CampaignPlanApplication` (window `CampaignPlanApplication`). Each plan is therefore paid and
  applied the moment it is chosen, and the next options see the result. A plan is usable only by the
  ruler of its source: the holder of an adviser or of a faceup relic, or the ruler of the site a card
  or edifice stands at. Whether the user can pay is found by dry-running the plan's application
  through the same windows, so a power that adds to a cost changes what is offered, and each option
  carries the price the dry run found. A source may be chosen once. A pass with nothing to offer does
  nothing, and the loop ends. A player defender owns the defender window. A bandit defender applies
  every cost-free plan of a site Bandits rule that no power makes unpayable, without choosing, and
  records each in a pool marker so a later window can read it. A facedown adviser is revealed when
  it is used. Each plan is a `BattlePlan` power. Its `plan` returns a `CampaignPlanOffer`
  (source, label, costs and effects) for the side asking, and the trait hooks it as an
  `Offer` at that side's plan window. There are about 70: one per battle-plan card, plus
  `TitleDefensePlan` (one defense die for an Oathkeeper, two for a Usurper), which no card
  prints. `WalkerPowerCatalog` registers them in groups: `BattlePlans`, `SimplePlans`,
  `PlanRules`, `PlanRestrictions` and `PlanRewrites`. `CampaignPlans` holds what the plan
  window and the plans share: who uses a plan, how its source is named in a decision, and
  the order the offers are listed in.
  A plan its user can pay for may carry a warning (`CampaignPlanOffer.warning`), shown in the
  option's warning state while it stays choosable (Zealots, whose condition fails).
- **Attack.** The roll is automatic. A skull removes one force warband and its two
  swords count only when that loss can be paid. Under the defender's Weeping Banner
  each skull removes two (`AttackRules.skullKills`), capped by the force, and its
  swords count only when both are paid. Skulls beyond the force add
  nothing. Hollow swords score one per pair. Outriders, once chosen, scores the attack
  again without the cap.
  The capped result is written over the rolled outcome. Brass Army's dice do not
  raise the physical force or any loss, sacrifice or placement limit.
- **Sacrifice.** `ChooseAmount(0, force - skulls)` warbands. Each adds its worth to
  the attack: one, or what a plan recorded in the pool `campaign.sacrifice-worth` when
  it was applied (three for Zealots). The pool is cleared with the others when the
  Campaign ends. The heading is the prompt alone, naming the worth; the dice, the
  attack and the skull loss are drawn beside it as a roll outcome.
- **Defense and the victor.** The defense roll is automatic. The recorded defense
  is the dice score plus the defender's force (the warbands at every target, or a
  Raid defender's board), written before any defender warband dies, so the victor is read
  from recorded outcomes only. Attack after the sacrifice must strictly exceed
  defense.
- **Result.** `RecordCampaignResult` writes the public `CampaignResult`. It is the
  only thing the steps after the losses read, because the losses change the board.
  `attackerWins` is true when the attacker prevailed and false when the defender
  did, whichever side a battle plan's user is on. `sacrificeWorth` records what each
  sacrificed warband added, and `attackTotal` counts it; a stored result without it
  reads one.

## Losses and resolution

The attacker's dead die at three moments. The skull losses die in
`CampaignSkullLosses`, right after the attack result has its final count.
The sacrificed warbands die in `CampaignSacrificeLosses`, right after the
sacrifice and before the defense roll. `CampaignLosses` kills half the
survivors, rounded down, on a defeat, and the defender's dead. With a queued
origin's warbands in the force, each kill asks its own split question over
the board and the site as that kill finds them ([catalog batch 8, item
13b](../superpowers/specs/2026-10-17-campaign-kills-at-their-moment-design.md)).
On a victory every warband at every target dies. A player defender keeps
half the killed force, rounded up, returned from the supply to their board.
Losses are plain operations in named windows, not a policy registry.

**Conquest placement.** `campaign.placement` is a `ChooseAmount` for one target
and a `Distribute` with `minTotal = 0` and `maxTotal = survivors` for several. Placed
survivors move from the board onto the targets. Unplaced survivors stay on the
board. The decision is omitted when nothing survives.

**Raid resolution.** In the printed order, in the `CampaignRaidTransfer` window.
First the takes: targeted faceup relics, then banners. A banner's resources
leave with it: People's Favor returns one unit at a time to the least-filled,
leftmost-on-tie favor bank, and the exact number of Darkest Secret resources
burns. Then the consequences, in their own `CampaignRaidConsequences` window:
ordinary facedown advisers go facedown on the next-region discard, facedown
relics are set aside for the Chronicle, half the defender's favor, rounded
down, burns, and a facedown Conspiracy returns to the box. Half the
defender's board warbands, rounded down, died in the losses. The attacker then
answers `campaign.relocation`, a choice of every other in-play site, and the
defender's pawn moves. This relocation is not Travel.

After the action, the aggregate boundary refills every empty positive-capacity
site with its printed Bandit force once, then runs the bounded Supremacy
evaluation once. If that displaces the Oathkeeper into a tie, the former holder
receives the durable recipient decision before normal Act controls resume.

## Powers

- **Vow of Peace** is a root `Restriction` at `CampaignActionEligibility`. A faceup
  copy held as an adviser blocks its holder's Campaign with
  `CampaignUnavailable`. Its second sentence is a `Transform` at
  `CampaignSacrificeSelection` that removes the attacker's sacrifice decision
  when the defender (the targets' ruler in a Conquest) holds a faceup copy.
- **Narrow Pass** gains an `OptionRestriction` at `CampaignTargetSelection`, beside
  its Travel restriction. It removes another site in the Pass's region from the
  targets when the actor's pawn is outside that region and the actor does not rule
  the Pass. The check is per candidate site, the Pass itself stays targetable, and
  only site options are considered. The Pass's ruler is never blocked. Anyone else
  may ask that ruler for consent: the restriction carries a waiver, worded
  `NarrowPassSitePower.noTargetConsent` (consent design,
  `docs/superpowers/specs/2026-10-08-consent-system-design.md`).
- `OptionRestriction` is the general channel for a power that forbids a choice. The
  walker removes the forbidden options in the window fold, so the projector, the
  answer check and simulation all see the filtered set. An optional decision left
  empty is dropped. A required decision left empty rejects the start with the
  restriction's own violation.
- Battle plans are `BattlePlan` powers (see Battle plans above): an `Offer` at a plan window
  and, for what a used plan does later, a hook at a later window that reads the picks from
  `PowerCtx.answered`.

## Unsupported Campaign rules

Unsupported handlers are ignored, not blocking: the Mountain and Plains Campaign
effects and the unsupported Raid and player-defender powers do not stop a Campaign.
Bag of Siegeworks, Weeping Banner and Peace Envoy are battle plans. Nothing
records an unsupported handler. The engine does not infer mechanics from
rules text.

## Visibility

The defender's plan decision is shown only to the defender. The attacker and every
other viewer see the generic waiting notice. Dice are public. A decision about a roll
draws that roll beside it: the attack roll at the sacrifice, the defense roll at placement
and relocation. The last Campaign's result stays in state as `lastCampaignResult`, which
Keep, Herald and Sneak Attack read; no panel draws it. A Raid's targets are a pawn,
faceup relics and banners. Hidden discarded identities and the relocation controls are
never projected to anyone but the deciding player. The panels are the generic walker panels for `ChooseOne`,
`ChooseMany`, `ChooseAmount` and `Distribute`.

## Journal

Each answer is a `WalkerStepRecorded` carrying a `ChoicePayload`. Each automatic roll
is a `RollPayload` with `automatic = true`, which replay applies without asking the
dice source. The recorded result carries the key `attackerWins`. Every other step is a recorded operation batch, including
`RecordCampaignResult`. A plan's payment is recorded as the requested `PayCost`, and replay
settles it again when its payer is not the active player. Campaign has no events
of its own.

## Scope

Campaign has no first-game gates: it does not check roles, Foundation faces or
legacies, and the audit of the rules such gates would stand for is deferred.
There is no cancel after the start. An unsupported handler does not block. A
plan window finishes by itself when no plan is left.

## Deferred

- The first-game rule audit (roles, Foundation faces, legacies).
- Further optional attacker, defender and deterministic bandit plan families,
  non-deterministic loss choices, and the additional Raid, victory, defeat and
  `At End` handlers. The timing windows exist and no behavior is inferred for them.
- A battle summary in the Game Log, tracked as ROADMAP "Phase - Campaign battle
  summary". The durable `lastCampaignResult` stays, for the powers that read
  the last result.
