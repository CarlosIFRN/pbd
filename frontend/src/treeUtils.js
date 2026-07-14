export function findNode(node, relativePath) {
  if (!node) return null
  if (node.relativePath === relativePath) return node
  for (const child of node.children) {
    const found = findNode(child, relativePath)
    if (found) return found
  }
  return null
}

export function findPath(node, relativePath, trail = []) {
  const newTrail = [...trail, node.name]
  if (node.relativePath === relativePath) return newTrail
  for (const child of node.children) {
    const result = findPath(child, relativePath, newTrail)
    if (result) return result
  }
  return null
}
