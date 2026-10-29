export function beginRecipeRefresh(query, random = Math.random) {
  query.current = 1
  if (query.sort === 'random') {
    query.randomPivot = Math.floor(random() * 1_000_000_000) + 1
  }
}
