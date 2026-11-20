package forge.gamemodes.limited;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.localinstance.properties.ForgeConstants;
import forge.util.FileUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * ReadDraftRankings class.
 * 
 */
public class ReadDraftRankings {

    /** Constant <code>comment="//"</code>. */
    private static final String COMMENT = "//";

    private final Map<String, Map<String, Integer>> draftRankings;
    private final Map<String, Integer> setSizes;

    /**
     * <p>
     * Constructor for ReadPriceList.
     * </p>
     */
    public ReadDraftRankings() {
        this.setSizes = new HashMap<>();
        this.draftRankings = readRankingFolder();
    } // setup()

    public ReadDraftRankings(String customFile) {
        this.setSizes = new HashMap<>();
        this.draftRankings = this.readFile(FileUtil.readFile(ForgeConstants.DRAFT_DIR + customFile));
    }

    private Map<String, Map<String, Integer>> readRankingFolder() {
        Map<String, Map<String, Integer>> map = new HashMap<>();

        File rankingDirectory = new File(ForgeConstants.DRAFT_RANKINGS_FOLDER);

        if (!rankingDirectory.isDirectory()) {
            System.out.println(rankingDirectory + " is not a directory...");
            return null;
        }

        for(File rank : rankingDirectory.listFiles()) {
            if (rank.isDirectory()) {
                continue;
            }

            this.readFile(FileUtil.readFile(rank), map);
        }

        return map;
    }


    private Map<String, Map<String, Integer>> readFile(List<String> lines) {
        final Map<String, Map<String, Integer>> map = new HashMap<>();

        return readFile(lines, map);
    }
    private Map<String, Map<String, Integer>> readFile(List<String> lines, Map<String, Map<String, Integer>> map) {
        for (String line : lines) {
            // stop reading if end of file or blank line is read
            if (line == null || line.length() == 0) {
                break;
            }

            if (line.startsWith(ReadDraftRankings.COMMENT)) {
                continue;
            }
            final String[] s = line.split("\\|");
            final String rankStr = s[0].trim().substring(1);
            final String name = StringUtils.stripAccents(s[1].trim());
            // final String rarity = s[2].trim();
            final String edition = s[3].trim();

            try {
                final int rank = Integer.parseInt(rankStr);
                if (!map.containsKey(edition)) {
                    map.put(edition, new HashMap<>());
                }
                map.get(edition).put(name, rank);
                if (setSizes.containsKey(edition)) {
                    setSizes.put(edition, Math.max(setSizes.get(edition), rank));
                } else {
                    setSizes.put(edition, rank);
                }
            } catch (NumberFormatException nfe) {
                System.err.println("NumberFormatException: " + nfe.getMessage());
            }
        }

        return map;
    } // readFile()

    /**
     * Get the relative ranking for the given card name in the given edition.
     * 
     * @param cardName
     *            the card name
     * @param edition
     *            the card's edition
     * @return ranking
     */
    public Double getRanking(String cardName, String edition) {
        if (draftRankings == null) {
            return null;
        }
        // handle split cards
        final String safeName = StringUtils.stripAccents(cardName).replace(" // ", " ");
        final Double own = rankingIn(edition, safeName);
        if (own != null) {
            return own;
        }
        // A printing from a set without rankings (a reprint, a special frame, an old set) is ranked as the card is
        // in the most recent set that ranks it, so bots value a card the same whichever printing it is.
        return latestRankings().get(safeName);
    }

    private Double rankingIn(String edition, String safeName) {
        final Map<String, Integer> set = draftRankings.get(edition);
        if (set == null) {
            return null;
        }
        final Integer rank = set.get(safeName);
        // If a card has no ranking, don't try to look it up --BBU
        return rank == null ? null : (double) rank / (double) setSizes.get(edition);
    }

    private Map<String, Double> latestRankings;

    /** Each card's relative ranking in the most recent set that ranks it, by the set's release date. */
    private synchronized Map<String, Double> latestRankings() {
        if (latestRankings != null) {
            return latestRankings;
        }
        final Map<String, Double> latest = new HashMap<>();
        final List<String> editions = new java.util.ArrayList<>(draftRankings.keySet());
        editions.sort(java.util.Comparator.comparing(ReadDraftRankings::releaseDate));
        for (final String edition : editions) {
            for (final String name : draftRankings.get(edition).keySet()) {
                latest.put(name, rankingIn(edition, name));
            }
        }
        latestRankings = latest;
        return latest;
    }

    private static java.util.Date releaseDate(String code) {
        try {
            final forge.card.CardEdition edition = forge.StaticData.instance().getEditions().get(code);
            if (edition != null && edition.getDate() != null) {
                return edition.getDate();
            }
        } catch (RuntimeException ignored) {
            // No card data loaded: order doesn't matter then.
        }
        return new java.util.Date(0);
    }
}
