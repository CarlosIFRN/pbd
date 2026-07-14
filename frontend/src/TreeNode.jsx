import { useState } from 'react'
import {
  FaFolder,
  FaFolderOpen,
  FaFileAlt,
  FaChevronRight,
  FaChevronDown,
} from 'react-icons/fa'

function TreeNode({ node, depth, selectedPath, onSelect }) {
  const [expanded, setExpanded] = useState(depth < 1)
  const isFolder = node.type === 'FOLDER'
  const isSelected = node.relativePath === selectedPath

  function handleClick() {
    onSelect(node.relativePath)
    if (isFolder) setExpanded((prev) => !prev)
  }

  return (
    <div>
      <div
        className={`tree-row${isSelected ? ' selected' : ''}`}
        style={{ paddingLeft: `${depth * 18 + 8}px` }}
        onClick={handleClick}
      >
        <span className="chevron">
          {isFolder ? (
            expanded ? <FaChevronDown size={10} /> : <FaChevronRight size={10} />
          ) : null}
        </span>
        <span className="icon">
          {isFolder ? (
            expanded ? <FaFolderOpen color="#e8a33d" /> : <FaFolder color="#e8a33d" />
          ) : (
            <FaFileAlt color="#7a9cc6" />
          )}
        </span>
        <span className="label">{node.name}</span>
      </div>

      {isFolder && expanded && (
        <div>
          {node.children.map((child) => (
            <TreeNode
              key={child.relativePath}
              node={child}
              depth={depth + 1}
              selectedPath={selectedPath}
              onSelect={onSelect}
            />
          ))}
        </div>
      )}
    </div>
  )
}

export default TreeNode
