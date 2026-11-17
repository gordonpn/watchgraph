import picocli.CommandLine
import picocli.CommandLine.Command
import kotlin.math.floor
import kotlin.math.pow

 data class Item(val id: Int, val rating: Double, val votes: Int, val popularity: Int,
                 val userScore: Int, val months: Int, val occurrences: Int)
 data class Ranked(val id: Int, val component: Int, val score: Long)

fun score(item: Item): Long {
    val rating = (item.votes * item.rating + 100 * 7.0) / (item.votes + 100)
    val taste = if (item.userScore == 0) 1.0 else item.userScore / 7.0
    val occurrence = item.occurrences * taste * 2.0.pow(-item.months / 12.0)
    val popularity = 10000.0 / (10000.0 + item.popularity) * 10.0
    return floor((occurrence + 0.5 * rating + 0.0001 * popularity) * 1000000.0 + 0.5).toLong()
}

@Command(name = "rank", mixinStandardHelpOptions = true)
class Rank : Runnable {
    override fun run() {
        val items = (0 until 1000).map { i -> Item(i, (i * 17 % 101) / 10.0,
            i * 31 % 10001, i * 13 % 10000 + 1, i % 11, i % 61, i % 9 + 1) }
        val graph = List(1000) { mutableListOf<Int>() }
        for (i in 0 until 999) if (i % 7 != 6) {
            graph[i].add(i + 1)
            graph[i + 1].add(i)
        }
        val components = IntArray(1000) { -1 }
        for (i in items.indices) {
            if (components[i] != -1) continue
            val queue = mutableListOf(i)
            components[i] = i
            var index = 0
            while (index < queue.size) {
                for (neighbour in graph[queue[index++]]) if (components[neighbour] == -1) {
                    components[neighbour] = i
                    queue.add(neighbour)
                }
            }
        }
        val ranked = items.map { Ranked(it.id, components[it.id], score(it)) }
            .sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.id })
        print(ranked.joinToString("") { "${it.id},${it.component},${it.score}\n" })
    }
}
fun main(args: Array<String>) { kotlin.system.exitProcess(CommandLine(Rank()).execute(*args)) }
