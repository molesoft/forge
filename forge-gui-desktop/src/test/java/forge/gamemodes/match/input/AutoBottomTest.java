package forge.gamemodes.match.input;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** The London mulligan's Auto: the AI's choice of bottom cards, never the last land (Firstpick QA, 2026-11-23). */
public class AutoBottomTest extends AITest {
    private Player player;

    private CardCollection hand(final String... names) {
        final Game game = initAndCreateGame();
        player = game.getPlayers().get(0);
        for (int i = 0; i < 20; i++) {
            addCardToZone(i % 2 == 0 ? "Mountain" : "Raging Goblin", player, ZoneType.Library);
        }
        final CardCollection hand = new CardCollection();
        for (final String n : names) {
            hand.add(addCardToZone(n, player, ZoneType.Hand));
        }
        return hand;
    }

    @Test
    public void keepsTheOnlyLand() {
        // The hand Auto bottomed to a landless 5: the Mountain first in hand order.
        final CardCollection hand = hand("Mountain", "Axgard Cavalry", "Fanatical Firebrand", "Abrade", "Abrade", "Brazen Scourge", "Firebrand Archer");
        final CardCollection bottom = InputLondonMulligan.autoBottom(player, hand, 2, true);
        AssertJUnit.assertEquals(2, bottom.size());
        final CardCollection kept = new CardCollection(hand);
        kept.removeAll(bottom);
        AssertJUnit.assertEquals(1, CardLists.count(kept, CardPredicates.LANDS));
    }

    @Test
    public void bottomsTheRightNumberFromTheHand() {
        final CardCollection hand = hand("Mountain", "Mountain", "Mountain", "Raging Goblin", "Shock", "Hill Giant", "Lightning Bolt");
        final CardCollection bottom = InputLondonMulligan.autoBottom(player, hand, 1, false);
        AssertJUnit.assertEquals(1, bottom.size());
        AssertJUnit.assertTrue(hand.containsAll(bottom));
        final CardCollection kept = new CardCollection(hand);
        kept.removeAll(bottom);
        AssertJUnit.assertTrue(CardLists.count(kept, CardPredicates.LANDS) > 0);
    }

    @Test
    public void neverEmptiesTheLandsEvenWhenItMustGuess() {
        // Hand order alone would bottom the Mountain; the guard keeps it while a spell can go.
        final CardCollection hand = hand("Mountain", "Shock");
        final CardCollection bottom = InputLondonMulligan.autoBottom(player, hand, 1, true);
        AssertJUnit.assertEquals(1, bottom.size());
        AssertJUnit.assertFalse(bottom.get(0).isLand());
    }
}
