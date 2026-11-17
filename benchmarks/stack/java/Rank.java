import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Rank {
    record Item(int id, double rating, int votes, int popularity, int userScore,
                int months, int occurrences) {}
    record Ranked(int id, int component, long score) {}

    static long score(Item item) {
        double rating = (item.votes() * item.rating() + 100 * 7.0) / (item.votes() + 100);
        double taste = item.userScore() == 0 ? 1.0 : item.userScore() / 7.0;
        double occurrence = item.occurrences() * taste * Math.pow(2.0, -item.months() / 12.0);
        double popularity = 10000.0 / (10000.0 + item.popularity()) * 10.0;
        return (long) Math.floor((occurrence + 0.5 * rating + 0.0001 * popularity) * 1000000.0 + 0.5);
    }

    public static void main(String[] args) {
        List<Item> items = new ArrayList<>();
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            items.add(new Item(i, (i * 17 % 101) / 10.0, i * 31 % 10001,
                i * 13 % 10000 + 1, i % 11, i % 61, i % 9 + 1));
            graph.add(new ArrayList<>());
        }
        for (int i = 0; i < 999; i++) {
            if (i % 7 != 6) {
                graph.get(i).add(i + 1);
                graph.get(i + 1).add(i);
            }
        }
        int[] components = new int[1000];
        Arrays.fill(components, -1);
        for (int i = 0; i < 1000; i++) {
            if (components[i] != -1) continue;
            List<Integer> queue = new ArrayList<>();
            queue.add(i);
            components[i] = i;
            for (int index = 0; index < queue.size(); index++) {
                for (int neighbour : graph.get(queue.get(index))) {
                    if (components[neighbour] == -1) {
                        components[neighbour] = i;
                        queue.add(neighbour);
                    }
                }
            }
        }
        List<Ranked> ranked = new ArrayList<>();
        for (Item item : items) ranked.add(new Ranked(item.id(), components[item.id()], score(item)));
        ranked.sort(Comparator.comparingLong(Ranked::score).reversed().thenComparingInt(Ranked::id));
        StringBuilder output = new StringBuilder();
        for (Ranked row : ranked) output.append(row.id()).append(',').append(row.component())
            .append(',').append(row.score()).append('\n');
        System.out.print(output);
    }
}
