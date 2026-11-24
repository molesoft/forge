package forge.game.mulligan;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** Rule 103.5: after a London mulligan the player keeps or not looking at seven cards, and only then bottoms. */
public class LondonMulliganTest extends AITest {
    private Player setUp() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        for (int i = 0; i < 30; i++) {
            addCardToZone(i % 2 == 0 ? "Island" : "Grizzly Bears", p, ZoneType.Library);
        }
        p.drawCards(7);
        return p;
    }

    @Test
    public void mulliganDrawsAFullSevenAndBottomsNothingYet() {
        final Player p = setUp();
        final LondonMulligan mulligan = new LondonMulligan(p, false);
        mulligan.mulligan();
        AssertJUnit.assertEquals(7, p.getCardsIn(ZoneType.Hand).size());
        AssertJUnit.assertEquals(23, p.getCardsIn(ZoneType.Library).size());
        AssertJUnit.assertEquals(1, mulligan.tuckCardsDuringMulligan());
    }

    @Test
    public void keepingBottomsOneCardPerMulligan() {
        final Player p = setUp();
        final LondonMulligan mulligan = new LondonMulligan(p, false);
        mulligan.mulligan();
        mulligan.mulligan();
        AssertJUnit.assertEquals(7, p.getCardsIn(ZoneType.Hand).size());
        mulligan.keep();
        AssertJUnit.assertTrue(mulligan.hasKept());
        AssertJUnit.assertEquals(5, p.getCardsIn(ZoneType.Hand).size());
        AssertJUnit.assertEquals(25, p.getCardsIn(ZoneType.Library).size());
    }

    @Test
    public void keepingTheFirstHandBottomsNothing() {
        final Player p = setUp();
        final LondonMulligan mulligan = new LondonMulligan(p, false);
        mulligan.keep();
        AssertJUnit.assertEquals(7, p.getCardsIn(ZoneType.Hand).size());
    }

    @Test
    public void aFreeFirstMulliganBottomsNothingOnKeep() {
        final Player p = setUp();
        final LondonMulligan mulligan = new LondonMulligan(p, true);
        mulligan.mulligan();
        mulligan.keep();
        AssertJUnit.assertEquals(7, p.getCardsIn(ZoneType.Hand).size());
    }
}
