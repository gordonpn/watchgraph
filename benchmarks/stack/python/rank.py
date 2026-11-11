"""Typed, validated implementation of the isolated scoring contract."""
from dataclasses import dataclass
import math
from pydantic import BaseModel, ConfigDict

class Item(BaseModel):
    model_config = ConfigDict(strict=True, frozen=True)
    id: int
    rating: float
    votes: int
    popularity: int
    user_score: int
    months: int
    occurrences: int

@dataclass(frozen=True)
class Ranked:
    id: int
    component: int
    score: int

def score(item: Item) -> int:
    rating = (item.votes * item.rating + 100 * 7.0) / (item.votes + 100)
    taste = item.user_score / 7.0 if item.user_score else 1.0
    occurrence = item.occurrences * taste * math.pow(2.0, -item.months / 12.0)
    popularity = 10000.0 / (10000.0 + item.popularity) * 10.0
    return math.floor((occurrence + 0.5 * rating + 0.0001 * popularity) * 1000000.0 + 0.5)

def run() -> list[Ranked]:
    items = [Item(id=i, rating=(i * 17 % 101) / 10.0, votes=i * 31 % 10001,
                  popularity=i * 13 % 10000 + 1, user_score=i % 11,
                  months=i % 61, occurrences=i % 9 + 1) for i in range(1000)]
    graph: list[list[int]] = [[] for _ in items]
    for i in range(999):
        if i % 7 != 6:
            graph[i].append(i + 1)
            graph[i + 1].append(i)
    components = [-1] * len(items)
    for i in range(len(items)):
        if components[i] != -1:
            continue
        queue = [i]
        components[i] = i
        for node in queue:
            for neighbour in graph[node]:
                if components[neighbour] == -1:
                    components[neighbour] = i
                    queue.append(neighbour)
    return sorted([Ranked(item.id, components[item.id], score(item)) for item in items],
                  key=lambda row: (-row.score, row.id))

if __name__ == '__main__':
    print(''.join(f'{row.id},{row.component},{row.score}\n' for row in run()), end='')
