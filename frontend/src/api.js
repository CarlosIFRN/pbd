const BASE_URL = '/api/filemanager'

async function handleResponse(response) {
  if (!response.ok) {
    let message = `Erro ${response.status}`
    try {
      const data = await response.json()
      if (data?.message) message = data.message
    } catch {
      // corpo vazio ou não-JSON, mantém mensagem padrão
    }
    throw new Error(message)
  }
  if (response.status === 204) return null
  return response.json()
}

export function fetchTree() {
  return fetch(`${BASE_URL}/tree`).then(handleResponse)
}

export function createFolder(parentPath, name) {
  return fetch(`${BASE_URL}/folder`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ parentPath, name }),
  }).then(handleResponse)
}

export function createFile(parentPath, name) {
  return fetch(`${BASE_URL}/file`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ parentPath, name }),
  }).then(handleResponse)
}

export function deleteItem(relativePath) {
  return fetch(`${BASE_URL}/item?path=${encodeURIComponent(relativePath)}`, {
    method: 'DELETE',
  }).then(handleResponse)
}
