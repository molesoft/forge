package forge.player;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterEnumType;
import forge.game.combat.DamageAssignment;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * The rules for dividing combat damage, as the desktop dialog ({@code VAssignCombatDamage}) applies
 * them, stated as engine tests. In every split a null key is the defender.
 */
public class DamageAssignmentShould {

    private static final String DIVIDE = "K:You may assign CARDNAME's combat damage divided as you choose among " +
            "defending player and/or any number of creatures they control.";

    private final HumanControlledGame g = new HumanControlledGame();

    private Card creature(final String name, final String pt, final String... extra) {
        final String[] lines = new String[extra.length + 2];
        lines[0] = "Types:Creature Beast";
        lines[1] = "PT:" + pt;
        System.arraycopy(extra, 0, lines, 2, extra.length);
        return g.card(g.opponent, name, lines);
    }

    private Card attacker(final String pt, final String... keywords) {
        final String[] lines = new String[keywords.length + 2];
        lines[0] = "Types:Creature Beast";
        lines[1] = "PT:" + pt;
        System.arraycopy(keywords, 0, lines, 2, keywords.length);
        return g.card(g.human, "Attacker", lines);
    }

    private static Map<Card, Integer> split(final Object... pairs) {
        final Map<Card, Integer> split = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            split.put((Card) pairs[i], (Integer) pairs[i + 1]);
        }
        return split;
    }

    // --- Current rules: no damage assignment order ---

    @Test
    public void suggestLethalDamageToEachBlockerInOrderWithTheRestOnTheLast() {
        Card first = creature("First", "2/2");
        Card second = creature("Second", "3/3");
        DamageAssignment a = new DamageAssignment(attacker("6/6"), new CardCollection(List.of(first, second)), 6, g.opponent, true);

        assertThat(a.canAssignToDefender()).isFalse();
        assertThat(a.suggested()).containsOnly(entry(first, 2), entry(second, 4));
        assertThat(a.problemWith(a.suggested())).isNull();
    }

    @Test
    public void allowAnySplitAmongBlockers() {
        Card first = creature("First", "2/2");
        Card second = creature("Second", "3/3");
        DamageAssignment a = new DamageAssignment(attacker("5/5"), new CardCollection(List.of(first, second)), 5, g.opponent, true);

        assertThat(a.problemWith(split(first, 0, second, 5))).isNull();
        assertThat(a.problemWith(split(first, 1, second, 4))).isNull();
        assertThat(a.problemWith(split(second, 5))).isNull();
    }

    @Test
    public void refuseASplitThatDoesNotAssignExactlyAllTheDamage() {
        Card first = creature("First", "2/2");
        Card second = creature("Second", "3/3");
        DamageAssignment a = new DamageAssignment(attacker("5/5"), new CardCollection(List.of(first, second)), 5, g.opponent, true);

        assertThat(a.problemWith(split(first, 2, second, 2))).isEqualTo("Assign all 5 damage (4 assigned)");
        assertThat(a.problemWith(split(first, 3, second, 3))).isEqualTo("Assign all 5 damage (6 assigned)");
    }

    @Test
    public void refuseNegativeAmounts() {
        Card first = creature("First", "2/2");
        Card second = creature("Second", "3/3");
        DamageAssignment a = new DamageAssignment(attacker("5/5"), new CardCollection(List.of(first, second)), 5, g.opponent, true);

        assertThat(a.problemWith(split(first, -1, second, 6))).isEqualTo("Damage can't be negative");
    }

    @Test
    public void refuseCardsThatAreNotTargetsAndTheDefenderWithoutTrample() {
        Card first = creature("First", "2/2");
        Card second = creature("Second", "3/3");
        Card bystander = creature("Bystander", "1/1");
        DamageAssignment a = new DamageAssignment(attacker("5/5"), new CardCollection(List.of(first, second)), 5, g.opponent, true);

        assertThat(a.problemWith(split(first, 2, bystander, 3))).isEqualTo("Bystander can't be assigned this damage");
        assertThat(a.problemWith(split(first, 2, null, 3))).isEqualTo("Damage can't be assigned to Opponent");
    }

    // --- Trample ---

    @Test
    public void requireLethalDamageOnEveryBlockerBeforeTrampling() {
        Card blocker = creature("Blocker", "2/2");
        DamageAssignment a = new DamageAssignment(attacker("6/6", "K:Trample"), new CardCollection(blocker), 6, g.opponent, true);

        assertThat(a.canAssignToDefender()).isTrue();
        assertThat(a.suggested()).containsOnly(entry(blocker, 2), entry(null, 4));
        assertThat(a.problemWith(split(blocker, 1, null, 5)))
                .isEqualTo("Assign lethal damage to Blocker before assigning damage to Opponent");
        assertThat(a.problemWith(split(blocker, 6, null, 0))).isNull();
        assertThat(a.problemWith(split(blocker, 3, null, 3))).isNull();
    }

    @Test
    public void countOneDamageAsLethalWithDeathtouch() {
        Card first = creature("First", "4/4");
        Card second = creature("Second", "5/5");
        DamageAssignment a = new DamageAssignment(attacker("6/6", "K:Trample", "K:Deathtouch"),
                new CardCollection(List.of(first, second)), 6, g.opponent, true);

        assertThat(a.lethal(first)).isEqualTo(1);
        assertThat(a.problemWith(split(first, 1, second, 1, null, 4))).isNull();
        assertThat(a.suggested()).containsOnly(entry(first, 1), entry(second, 1), entry(null, 4));
    }

    @Test
    public void countDamageAlreadyMarkedTowardLethal() {
        Card hurt = creature("Hurt", "3/3");
        hurt.setDamage(3);
        DamageAssignment a = new DamageAssignment(attacker("4/4", "K:Trample"), new CardCollection(hurt), 4, g.opponent, true);

        assertThat(a.lethal(hurt)).isZero();
        assertThat(a.problemWith(split(hurt, 0, null, 4))).isNull();
    }

    @Test
    public void measureLethalForThePlayerInPoisonWithInfect() {
        Card blocker = creature("Blocker", "1/1");
        g.opponent.setCounters(CounterEnumType.POISON, 4);
        DamageAssignment a = new DamageAssignment(attacker("8/8", "K:Trample", "K:Infect"), new CardCollection(blocker), 8, g.opponent, true);

        assertThat(a.lethal(null)).isEqualTo(6);
    }

    @Test
    public void measureLethalForAPlaneswalkerDefenderByLoyalty() {
        Card blocker = creature("Blocker", "1/1");
        Card walker = g.card(g.opponent, "Walker", "Types:Legendary Planeswalker Jace", "Loyalty:4");
        walker.setCounters(CounterEnumType.LOYALTY, 4);
        DamageAssignment a = new DamageAssignment(attacker("8/8", "K:Trample"), new CardCollection(blocker), 8, walker, true);

        assertThat(a.lethal(null)).isEqualTo(4);
        assertThat(a.problemWith(split(blocker, 1, null, 7))).isNull();
    }

    @Test
    public void measureLethalForABattleDefenderByDefense() {
        Card blocker = creature("Blocker", "1/1");
        Card battle = g.card(g.opponent, "Siege", "Types:Battle Siege", "Defense:5");
        battle.setCounters(CounterEnumType.DEFENSE, 5);
        DamageAssignment a = new DamageAssignment(attacker("8/8", "K:Trample"), new CardCollection(blocker), 8, battle, true);

        assertThat(a.lethal(null)).isEqualTo(5);
    }

    @Test
    public void treatAPlaneswalkerAmongTheTargetsLikeABlockerForTrampleOverPlaneswalkers() {
        // Combat puts the attacked planeswalker after the blockers and makes its controller the defender.
        Card blocker = creature("Blocker", "2/2");
        Card walker = g.card(g.opponent, "Walker", "Types:Legendary Planeswalker Jace", "Loyalty:3");
        walker.setCounters(CounterEnumType.LOYALTY, 3);
        DamageAssignment a = new DamageAssignment(attacker("7/7", "K:Trample:Planeswalker"),
                new CardCollection(List.of(blocker, walker)), 7, g.opponent, true);

        assertThat(a.canAssignToDefender()).isTrue();
        assertThat(a.lethal(walker)).isEqualTo(3);
        assertThat(a.suggested()).containsOnly(entry(blocker, 2), entry(walker, 3), entry(null, 2));
        assertThat(a.problemWith(split(blocker, 2, walker, 2, null, 3)))
                .isEqualTo("Assign lethal damage to Walker before assigning damage to Opponent");
    }

    // --- "Divide as you choose" ---

    @Test
    public void allowAnySplitIncludingTheDefenderWhenDividingAsYouChoose() {
        Card first = creature("First", "2/2");
        Card second = creature("Second", "3/3");
        DamageAssignment a = new DamageAssignment(attacker("5/5", DIVIDE), new CardCollection(List.of(first, second)), 5, g.opponent, true);

        assertThat(a.canAssignToDefender()).isTrue();
        assertThat(a.problemWith(split(first, 1, null, 4))).isNull();
    }

    // --- Legacy damage assignment order ---

    @Test
    public void requireLethalDamageOnEachBlockerBeforeTheNextUnderAssignmentOrder() {
        Card first = creature("First", "2/2");
        Card second = creature("Second", "3/3");
        DamageAssignment a = new DamageAssignment(attacker("5/5"), new CardCollection(List.of(first, second)), 5, g.opponent, false);

        assertThat(a.problemWith(split(first, 1, second, 4)))
                .isEqualTo("Assign lethal damage to First before assigning damage to Second");
        assertThat(a.problemWith(split(first, 2, second, 3))).isNull();
        assertThat(a.problemWith(split(first, 5, second, 0))).isNull();
    }

    @Test
    public void allowAllDamageOnTheFirstBlockerWhenItIsLessThanLethal() {
        Card first = creature("First", "4/4");
        Card second = creature("Second", "3/3");
        DamageAssignment a = new DamageAssignment(attacker("2/2"), new CardCollection(List.of(first, second)), 2, g.opponent, false);

        assertThat(a.suggested()).containsOnly(entry(first, 2), entry(second, 0));
        assertThat(a.problemWith(split(first, 2))).isNull();
        assertThat(a.problemWith(split(first, 1, second, 1)))
                .isEqualTo("Assign lethal damage to First before assigning damage to Second");
    }

    @Test
    public void keepTheOrderWhenDividingAsYouChooseUnderAssignmentOrder() {
        Card first = creature("First", "2/2");
        Card second = creature("Second", "3/3");
        DamageAssignment a = new DamageAssignment(attacker("5/5", DIVIDE), new CardCollection(List.of(first, second)), 5, g.opponent, false);

        assertThat(a.canAssignToDefender()).isFalse();
        assertThat(a.problemWith(split(first, 1, second, 4)))
                .isEqualTo("Assign lethal damage to First before assigning damage to Second");
    }

    // --- A blocker dividing its damage ---

    @Test
    public void letABlockerDivideItsDamageAmongTheAttackersItBlocks() {
        Card first = g.card(g.human, "First", "Types:Creature Beast", "PT:2/2");
        Card second = g.card(g.human, "Second", "Types:Creature Beast", "PT:2/2");
        Card blocker = creature("Blocker", "3/3");
        DamageAssignment a = new DamageAssignment(blocker, new CardCollection(List.of(first, second)), 3, null, true);

        assertThat(a.canAssignToDefender()).isFalse();
        assertThat(a.problemWith(split(first, 1, second, 2))).isNull();
        assertThat(a.problemWith(split(first, 1, null, 2))).isEqualTo("Damage can't be assigned to the defender");
    }
}
