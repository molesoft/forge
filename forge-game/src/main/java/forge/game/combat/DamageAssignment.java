package forge.game.combat;

import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.keyword.Keyword;
import forge.game.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules for dividing one combatant's combat damage among the cards it fights, and the defender
 * when trample allows it. A UI uses this to suggest a split and to check the player's answer, so the
 * rules don't live in each UI.
 * <p>
 * In every map, a null key means the defender. Lethal values are read when this is built, which is
 * while the engine waits for the answer, so a split can be checked from another thread.
 */
public final class DamageAssignment {
    private static final String DIVIDE_AS_YOU_CHOOSE = "You may assign CARDNAME's combat damage divided as you choose among " +
            "defending player and/or any number of creatures they control.";

    private final List<Card> targets;
    private final int damage;
    private final GameEntity defender;
    private final boolean overrideOrder;
    private final boolean divideAsYouChoose;
    private final boolean toDefender;
    private final Map<Card, Integer> lethal = new HashMap<>();

    /**
     * @param source the card dealing the damage
     * @param targets the cards it fights, in damage assignment order
     * @param damage the combat damage it deals
     * @param defender the player, planeswalker or battle it attacks, or null if it is blocking
     * @param overrideOrder true when damage may be split without regard to order, as under current rules
     */
    public DamageAssignment(final Card source, final CardCollectionView targets, final int damage, final GameEntity defender, final boolean overrideOrder) {
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
        this.damage = damage;
        this.defender = defender;
        this.overrideOrder = overrideOrder;
        this.divideAsYouChoose = source.hasKeyword(DIVIDE_AS_YOU_CHOOSE);
        this.toDefender = defender != null && (source.hasKeyword(Keyword.TRAMPLE) || (divideAsYouChoose && overrideOrder));

        final boolean deathtouch = source.hasKeyword(Keyword.DEATHTOUCH);
        for (final Card target : targets) {
            lethal.put(target, Math.max(0, target.getExcessDamageValue(deathtouch)));
        }
        if (toDefender) {
            lethal.put(null, Math.max(0, lethalForDefender(source)));
        }
    }

    private int lethalForDefender(final Card source) {
        if (defender instanceof Player player) {
            if (source.hasKeyword(Keyword.INFECT)) {
                return player.getGame().getRules().getPoisonCountersToLose() - player.getPoisonCounters();
            }
            return player.getLife();
        }
        if (defender instanceof Card card) {
            return card.getExcessDamageValue(false);
        }
        return 0;
    }

    public List<Card> getTargets() {
        return targets;
    }

    public int getDamage() {
        return damage;
    }

    /** Whether damage may go to the defender, which is a null key in every map. */
    public boolean canAssignToDefender() {
        return toDefender;
    }

    /** Damage that counts as lethal for a target, or for the defender when given null. */
    public int lethal(final Card target) {
        final Integer value = lethal.get(target);
        if (value == null) {
            throw new IllegalArgumentException(target + " is not a target of this damage");
        }
        return value;
    }

    /** Lethal damage to each target in order, then the rest to the defender if allowed, or else to the last target. */
    public Map<Card, Integer> suggested() {
        final Map<Card, Integer> split = new LinkedHashMap<>();
        int left = damage;
        for (final Card target : targets) {
            final int amount = Math.min(lethal(target), left);
            split.put(target, amount);
            left -= amount;
        }
        if (toDefender) {
            split.put(null, left);
        } else if (!targets.isEmpty()) {
            split.merge(targets.get(targets.size() - 1), left, Integer::sum);
        }
        return split;
    }

    /** Why a split is not legal, or null if it is. Targets missing from the split receive no damage. */
    public String problemWith(final Map<Card, Integer> split) {
        int total = 0;
        for (final Map.Entry<Card, Integer> entry : split.entrySet()) {
            final Card target = entry.getKey();
            final int amount = entry.getValue() == null ? 0 : entry.getValue();
            if (!lethal.containsKey(target)) {
                return target == null ? "Damage can't be assigned to " + defenderName() : target.getName() + " can't be assigned this damage";
            }
            if (amount < 0) {
                return "Damage can't be negative";
            }
            total += amount;
        }
        if (total != damage) {
            return "Assign all " + damage + " damage (" + total + " assigned)";
        }
        if (divideAsYouChoose && overrideOrder) {
            return null;
        }

        // Under current rules only the defender waits for lethal damage on every card. Under damage
        // assignment order, each card waits for lethal damage on every card before it.
        final List<Card> order = new ArrayList<>(targets);
        if (toDefender) {
            order.add(null);
        }
        Card firstShort = null;
        for (final Card target : order) {
            final int amount = split.getOrDefault(target, 0);
            final boolean waits = target == null || !overrideOrder;
            if (amount > 0 && waits && firstShort != null) {
                return "Assign lethal damage to " + firstShort.getName() + " before assigning damage to " +
                        (target == null ? defenderName() : target.getName());
            }
            if (target != null && firstShort == null && split.getOrDefault(target, 0) < lethal(target)) {
                firstShort = target;
            }
        }
        return null;
    }

    private String defenderName() {
        return defender == null ? "the defender" : defender.getName();
    }
}
