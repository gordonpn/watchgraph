import unittest
from pydantic import ValidationError
from rank import Item, run, score
from measure import percentile

class ContractTests(unittest.TestCase):
    def test_prior_unrated_and_half_life(self) -> None:
        item = Item(id=0, rating=10.0, votes=0, popularity=10000,
                    user_score=0, months=12, occurrences=7)
        self.assertEqual(score(item), 7000500)

    def test_strict_validation(self) -> None:
        with self.assertRaises(ValidationError):
            Item.model_validate_json('{"id":"0","rating":7.0,"votes":0,"popularity":1,"user_score":0,"months":0,"occurrences":1}')

    def test_partition_and_order(self) -> None:
        rows = run()
        self.assertEqual(len(rows), 1000)
        self.assertEqual({row.id for row in rows}, set(range(1000)))
        self.assertEqual(len({row.component for row in rows}), 143)
        for row in rows:
            self.assertEqual(row.component, row.id // 7 * 7)
        self.assertEqual(rows, sorted(rows, key=lambda row: (-row.score, row.id)))

    def test_small_sample_percentiles(self) -> None:
        self.assertEqual(percentile([float(i) for i in range(1, 11)], .5), 5)
        self.assertEqual(percentile([float(i) for i in range(1, 11)], .95), 10)
        self.assertEqual(percentile([float(i) for i in range(1, 11)], .99), 10)

if __name__ == '__main__':
    unittest.main()
