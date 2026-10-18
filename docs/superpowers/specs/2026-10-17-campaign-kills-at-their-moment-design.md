# Campaign kills at their moment

**Status:** designed 2026-10-17; done 2026-10-17. It covers roadmap item **13b. Campaign
kills at their moment** under **Phase - Catalog batch 8**. It builds on
slice 13 of [catalog batch 8](2026-10-12-catalog-batch-8-design.md) (Wild
Allies and Captains), whose origin split question it moves, and on the
[bounded Campaign](../../architecture/bounded-campaign.md) step order.

## Goal

A Campaign today kills all of the attacker's dead in one batch at
`CampaignLosses`: the skull losses, the sacrifices and, on a defeat, half
the survivors, summed. The rules kill each as it happens. The rules
reference reads "attacker rolls and counts swords while suffering skull
losses; attacker may sacrifice force warbands for +1 attack each", and the
New Foundations delta adds "Attacker sacrifices occur before defense roll."

With this design the skull losses die at the attack result, the sacrifices
die when they are chosen, and the defeat's half dies at `CampaignLosses`.
No card's outcome changes. The log follows the new moments.

## Rulings

Ruled 2026-10-17.

### The three kills

1. **Skull losses** die in a new window, `CampaignSkullLosses`, walked
   right after `CampaignAttackResult`. The count is the attack outcome's
   final skull losses, after every plan in the attack result window has
   rewritten it: Weeping Banner's two kills per skull, Outriders, Lancers,
   War Tortoise, Fearsome General, Fae Battalion, Rusting Ray, Great Levy,
   Kindred Warriors and Rangers. A plan that ignores the skulls leaves
   nothing to kill, and the window asks and kills nothing.
2. **Sacrifices** die in a new window, `CampaignSacrificeLosses`, walked
   right after the sacrifice decision and before the defense roll. The
   count is the sacrifice answer. When the decision is absent (nothing
   survives the skulls, Vow of Peace, or VictoriousNow), the window kills
   nothing.
3. **The defeat's half** dies at `CampaignLosses`, as today: half the
   survivors, rounded down, where the survivors are
   `force - skullLosses - sacrificed`. The defender's kills follow there,
   unchanged. A victory kills nothing of the attacker's at this step.

- Conquest placement still takes `force - skullLosses - sacrificed` as the
  survivors.
- **Careful Plans** reorders the five battle steps. Each new window travels
  with its step: `CampaignSkullLosses` stays right after the attack result,
  and `CampaignSacrificeLosses` right after the sacrifice. With the defense
  roll first, the skull kill still comes at the attack result.
- `CampaignResult` keeps `skullLosses` and `sacrificed`. Readers after the
  outcome (Fog, Doctor, Conquest, the losses line) read them as today.

### The origin split at each kill

Wild Allies and Captains add warbands at the origin site to the force.
Slice 13 asked once, at the kill step, how many of the D dead come from the
site. With this design each kill asks its own question.

- For a kill of k warbands, the site's share runs from max(0, k−B) to
  min(k, S), where B is the attacker's warbands on their board and S their
  warbands at the origin **as they stand when that kill runs**. Earlier
  kills have already lowered them.
- When that range holds more than one value, the attacker answers "Choose
  how many of the k dead warbands come from {site}", a choice of amount
  that suggests the least from the site. Otherwise the one value applies
  without a question.
- Each window has its own question id, so the three answers never collide.
  The sacrifice question and its split question stay two questions.
- With no origin force (any Campaign but Wild Allies' and Captains'), every
  kill is from the board and asks nothing.

### Loss powers

- **Billowing Fog** and **Traveling Doctor** ("If you're defeated, kill no
  warbands in your force") spare only the defeat's half at
  `CampaignLosses`, as already ruled: the skulls and the sacrifices have
  died by then. "{Blue}'s force was spared." is unchanged. Neither touches
  the new windows.
- **Peace Envoy** is unchanged. VictoriousNow empties the attack roll and
  sacrifice, so the new windows kill nothing, and at `CampaignLosses` it
  drops every kill.
- **Hospital** ("If any of your warbands would be killed, place them on
  Hospital's site instead if you still rule it") counts its user's kills in
  `CampaignSkullLosses` and `CampaignSacrificeLosses` as well as at
  `CampaignLosses`. It still places once, after the Campaign. The outcome
  is unchanged.
- **Cursed Cauldron** ("If you're victorious, gain 1 warband per enemy
  warband killed in this campaign") counts the enemy's kills in the two new
  windows as well as at `CampaignLosses`. The batch 4 ruling that a
  defender's Cauldron counts the attacker's skull losses and sacrifice
  stands. The gain still comes once, after the Campaign.
- **Bear Traps** is unchanged. It applies at plan time, before any roll, so
  no kill has run whatever their timing, and its "a warband outside the
  force" test (board plus origin warbands exceed the force, and the board
  holds one) stays right. The roadmap entry's claim that Bear Traps "then
  sees the right board" was mistaken.
- **Sticky Fire** and **Shield Wall** run after `CampaignLosses`, and their
  whole-force kills read the board left by every earlier kill, as today.
- **Zealots** reads the committed force at plan time and is unchanged (its
  batch 7 FAQ: later skull losses do not trigger it).

### Lines

- **The attack line** counts the skull faces and, when they differ, the
  kills: "Attack: 5 with 2 skulls, killing 4 warbands." The skulls are the
  attack's skull faces as the scoring counts them (doubled when the attack
  is doubled). The kills are the outcome's skull losses. When the two are
  equal the line is "Attack: 5 with 2 skulls", and with no kills it is
  "Attack: 5". Today the line prints the kills as skulls; Weeping Banner's
  doubling and the force cap now show.
- **The sacrifice line** ("Sacrificed {n} warbands for an attack of
  {total}") is unchanged.
- **The closing losses line** ("{Blue} lost N warbands…", or "Lost N
  warbands…" for the actor) counts only the kills at `CampaignLosses`: the
  defeat's half, including its dead at the origin. A defeat whose half is
  zero or spared writes no attacker loss line. The defender's loss lines are
  unchanged.
- Neither the attack line nor the sacrifice line mentions the origin split.
  The split question's answer is in the log as a decision.

## Engine shape

- `PowerWindow` gains `CampaignSkullLosses` and `CampaignSacrificeLosses`.
  Neither is in `BattlePlan`'s after-outcome set, since both run before
  `RecordCampaignResult`.
- `CampaignProcedure.battle` walks
  `Sequence(CampaignSkullLosses, BuildOps(skull kill))` after the attack
  result and `Sequence(CampaignSacrificeLosses, BuildOps(sacrifice kill))`
  after the sacrifice step.
- `CampaignBattle` builds each kill from the walk's state: one board `Kill`
  and, with an origin share, one site `Kill`, as `losses` does today. The
  origin split step comes first in each window and reads B and S from the
  current board.
- `CampaignBattle.attackerDeaths` at `CampaignLosses` becomes the defeat's
  half alone (zero on a victory or when spared). `originDeathRange` takes
  the kill size and the current B and S.
- Hospital and Cursed Cauldron add the two windows to their wrappings, with
  the same counting as at `CampaignLosses`.
- Careful Plans' Branch carries each new window with its step.
- The architecture doc's "Nothing leaves the board before
  `CampaignLosses`" no longer holds. It becomes: the skull and sacrifice
  losses die in their own windows, and `CampaignLosses` kills the defeat's
  half and the defender's dead.

Old replays need no support.

## Testing

- **Moments.** After the attack result the board has lost the skull kills.
  After the sacrifice it has lost the sacrificed warbands, before the
  defense roll. On a defeat `CampaignLosses` kills only the half. A victory
  kills only the skulls and the sacrifices.
- **Weeping Banner.** Two skull faces against its holder kill four
  warbands at `CampaignSkullLosses`, and the attack line reads "with 2
  skulls, killing 4 warbands".
- **The force cap.** Under Weeping Banner, two skulls against a force of
  three kill three at the attack result, and the line wording for a capped
  kill is tested directly.
- **Careful Plans.** With the defense first, the skull kill still follows
  the attack result.
- **Origin split.** With Wild Allies or Captains, each kill whose range
  holds more than one value asks its own question, over the board and site
  left by the earlier kills. A kill with one possible value asks nothing.
- **Fog and Doctor.** A defeated force loses its skulls and its sacrifice,
  and the half is spared.
- **Peace Envoy.** Nothing dies on either side.
- **Hospital** saves the warbands of every kill, and **Cursed Cauldron**
  counts every enemy kill, with the same totals as before.
- **Sticky Fire** kills the survivors on the board and at the origin.
- **Lines.** The closing loss line counts only the half. The `raid.*.log`
  fixtures and the game log suites (rare lines, action line, Campaign,
  Captains scripts) follow.
- Suites that pin the batched kill change with it: `CampaignProcedureSuite`,
  `CampaignOriginSuite`, and the suites for Billowing Fog, Traveling Doctor,
  Peace Envoy, Hospital, Cursed Cauldron, Sticky Fire, Shield Wall, Bear
  Traps and Weeping Banner.

## Docs

- [Bounded Campaign](../../architecture/bounded-campaign.md): the step
  diagram gains the two windows, and "Losses and resolution" is rewritten as
  above.
- [Catalog batch 8 rulings](2026-10-12-catalog-batch-8-rulings.md): slice
  13's "The question is asked once, at the kill step" becomes "Each kill
  asks its own question", with a link here.
- [Catalog batch 4 rulings](2026-10-05-catalog-batch-4-rulings.md): "On a
  defeat today, the attacker loses skulls, sacrifice and half the
  surviving force" gains a note that each dies at its moment since 13b.
- [Roadmap](../../ROADMAP.md): 13b links this design, its Bear Traps
  claim is corrected, and it is marked done when the plan lands.

## Departures

The implementation departs from this design in three places.

1. **The force cap.** The test "three skull faces against a force of two
   kill two" cannot be rolled: the attack pool holds one die per committed
   warband. The cap shows only when each skull kills more than one warband
   (Weeping Banner) or counts twice (Lancers). The suites test Weeping
   Banner with an odd force, and test the line's wording for a capped kill
   directly.
2. **`originDeathRange` keeps its signature.** It takes an `OriginForce`,
   which already carries B (`onBoard`) and S (`atSite`). The early kills
   build that value from the live board with `CampaignSetup.originForce`,
   and the losses read it from the result.
3. **`CampaignResult.originForce` is recorded after the early kills.** The
   field is unchanged, but it now means "the board and the origin as the
   losses find them". When the early kills empty the origin, the field is
   `None`, and the half dies from the board.
