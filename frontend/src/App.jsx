import { useEffect, useState } from 'react'
import { FaFolderPlus, FaFileMedical, FaTrashAlt } from 'react-icons/fa'
import TreeNode from './TreeNode'
import { findNode } from './treeUtils'
import { fetchTree, createFolder, createFile, deleteItem } from './api'
import './App.css'

function App() {
  const [tree, setTree] = useState(null)
  const [selectedPath, setSelectedPath] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  function loadTree() {
    setLoading(true)
    setError('')
    return fetchTree()
      .then((data) => setTree(data))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadTree()
  }, [])

  const selectedNode = tree ? findNode(tree, selectedPath) : null

  async function handleCreate(type) {
    const targetFolderPath =
      selectedNode?.type === 'FOLDER' ? selectedNode.relativePath : ''

    const name = window.prompt(
      type === 'folder' ? 'Nome da nova pasta:' : 'Nome do novo arquivo:'
    )
    if (!name) return

    try {
      if (type === 'folder') {
        await createFolder(targetFolderPath, name)
      } else {
        await createFile(targetFolderPath, name)
      }
      await loadTree()
    } catch (err) {
      window.alert(err.message)
    }
  }

  async function handleDelete() {
    if (!selectedNode || selectedNode.relativePath === '') return
    if (!window.confirm(`Remover "${selectedNode.name}"?`)) return

    try {
      await deleteItem(selectedNode.relativePath)
      setSelectedPath('')
      await loadTree()
    } catch (err) {
      window.alert(err.message)
    }
  }

  return (
    <div className="app">
      <header className="toolbar">
        <h1>Explorador de Arquivos</h1>
        <div className="toolbar-actions">
          <button onClick={() => handleCreate('folder')}>
            <FaFolderPlus /> Nova pasta
          </button>
          <button onClick={() => handleCreate('file')}>
            <FaFileMedical /> Novo arquivo
          </button>
          <button
            className="danger"
            onClick={handleDelete}
            disabled={!selectedNode || selectedNode.relativePath === ''}
          >
            <FaTrashAlt /> Remover
          </button>
        </div>
      </header>

      <main className="content">
        <div className="tree-panel">
          {loading && <p>Carregando...</p>}
          {error && <p className="error">Erro ao carregar: {error}</p>}
          {tree && (
            <TreeNode
              node={tree}
              depth={0}
              selectedPath={selectedPath}
              onSelect={setSelectedPath}
            />
          )}
        </div>
      </main>

      <footer className="path-bar">
        <strong>Caminho absoluto:</strong> {selectedNode?.absolutePath || '—'}
      </footer>
    </div>
  )
}

export default App
