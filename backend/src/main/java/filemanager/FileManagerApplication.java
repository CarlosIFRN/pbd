package filemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SpringBootApplication
@RestController
@RequestMapping("/api/filemanager")
@CrossOrigin(origins = "*") // libere apenas a origem real do frontend em produção
public class FileManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileManagerApplication.class, args);
    }

    // ======================================================================
    // RAIZ DO FILE MANAGER NO DISCO
    // ======================================================================

    /** Pasta raiz real onde tudo é criado. Isolada para proteger o resto do disco. */
    private static final Path ROOT_DIR = Paths.get("filemanager-storage").toAbsolutePath().normalize();

    static {
        try {
            Files.createDirectories(ROOT_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar o diretório raiz do File Manager: " + ROOT_DIR, e);
        }
    }

    // ======================================================================
    // DTOs (requisição / resposta)
    // ======================================================================

    /** Corpo esperado para criar pasta/arquivo. parentPath é relativo à raiz ("" = raiz). */
    public static class CreateNodeRequest {
        private String parentPath;
        private String name;

        public String getParentPath() { return parentPath; }
        public void setParentPath(String parentPath) { this.parentPath = parentPath; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** Nó formatado para exibição em árvore no frontend (com filhos aninhados). */
    public static class TreeNodeResponse {
        private final String name;
        private final String type; // "FOLDER" ou "FILE"
        private final String relativePath; // caminho relativo à raiz, usado como identificador nas outras chamadas
        private final String absolutePath; // caminho absoluto real no disco
        private final List<TreeNodeResponse> children;

        public TreeNodeResponse(String name, String type, String relativePath, String absolutePath, List<TreeNodeResponse> children) {
            this.name = name;
            this.type = type;
            this.relativePath = relativePath;
            this.absolutePath = absolutePath;
            this.children = children;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getRelativePath() { return relativePath; }
        public String getAbsolutePath() { return absolutePath; }
        public List<TreeNodeResponse> getChildren() { return children; }
    }

    /** Resposta simples para devolver apenas o caminho absoluto. */
    public static class PathResponse {
        private final String absolutePath;
        public PathResponse(String absolutePath) { this.absolutePath = absolutePath; }
        public String getAbsolutePath() { return absolutePath; }
    }

    /** Resposta padrão de erro. */
    public static class ErrorResponse {
        private final String message;
        public ErrorResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
    }

    // ======================================================================
    // EXCEÇÃO CUSTOMIZADA
    // ======================================================================

    public static class FileManagerException extends RuntimeException {
        private final HttpStatus status;
        public FileManagerException(String message, HttpStatus status) {
            super(message);
            this.status = status;
        }
        public HttpStatus getStatus() { return status; }
    }

    // ======================================================================
    // ENDPOINTS
    // ======================================================================

    /** Retorna a árvore inteira a partir da raiz real no disco. */
    @GetMapping("/tree")
    public ResponseEntity<TreeNodeResponse> getTree() {
        return ResponseEntity.ok(buildTree(ROOT_DIR));
    }

    /** Cria uma pasta real dentro de parentPath (relativo à raiz). */
    @PostMapping("/folder")
    public ResponseEntity<TreeNodeResponse> createFolder(@RequestBody CreateNodeRequest request) {
        Path created = createNode(request, true);
        return ResponseEntity.status(HttpStatus.CREATED).body(buildTree(created));
    }

    /** Cria um arquivo real (vazio) dentro de parentPath (relativo à raiz). */
    @PostMapping("/file")
    public ResponseEntity<TreeNodeResponse> createFile(@RequestBody CreateNodeRequest request) {
        Path created = createNode(request, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(buildTree(created));
    }

    /** Remove um item real do disco (pasta é removida recursivamente). */
    @DeleteMapping("/item")
    public ResponseEntity<Void> deleteNode(@RequestParam("path") String relativePath) {
        Path target = resolveSafePath(relativePath);
        if (target.equals(ROOT_DIR)) {
            throw new FileManagerException("Não é possível remover a raiz.", HttpStatus.BAD_REQUEST);
        }
        if (!Files.exists(target)) {
            throw new FileManagerException("Item não encontrado: " + relativePath, HttpStatus.NOT_FOUND);
        }
        deleteRecursively(target);
        return ResponseEntity.noContent().build();
    }

    /** Retorna o caminho absoluto real do item selecionado. */
    @GetMapping("/path")
    public ResponseEntity<PathResponse> getAbsolutePath(@RequestParam("path") String relativePath) {
        Path target = resolveSafePath(relativePath);
        if (!Files.exists(target)) {
            throw new FileManagerException("Item não encontrado: " + relativePath, HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(new PathResponse(target.toString()));
    }

    /** Tratamento centralizado de erros de negócio. */
    @ExceptionHandler(FileManagerException.class)
    public ResponseEntity<ErrorResponse> handleFileManagerException(FileManagerException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getMessage()));
    }

    /** Tratamento de erros de I/O inesperados (disco cheio, permissão negada, etc). */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Erro de disco: " + ex.getMessage()));
    }

    // ======================================================================
    // LÓGICA INTERNA
    // ======================================================================

    private Path createNode(CreateNodeRequest request, boolean isFolder) {
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            throw new FileManagerException("O nome não pode ser vazio.", HttpStatus.BAD_REQUEST);
        }
        String name = request.getName().trim();
        if (name.contains("/") || name.contains("\\")) {
            throw new FileManagerException("O nome não pode conter barras.", HttpStatus.BAD_REQUEST);
        }

        String parentRelative = request.getParentPath() == null ? "" : request.getParentPath();
        Path parentDir = resolveSafePath(parentRelative);

        if (!Files.exists(parentDir) || !Files.isDirectory(parentDir)) {
            throw new FileManagerException("Pasta pai inexistente.", HttpStatus.BAD_REQUEST);
        }

        Path target = parentDir.resolve(name).normalize();
        if (!target.startsWith(ROOT_DIR)) {
            throw new FileManagerException("Caminho inválido.", HttpStatus.BAD_REQUEST);
        }
        if (Files.exists(target)) {
            throw new FileManagerException("Já existe um item com esse nome nesta pasta.", HttpStatus.CONFLICT);
        }

        try {
            if (isFolder) {
                Files.createDirectory(target);
            } else {
                Files.createFile(target);
            }
        } catch (IOException e) {
            throw new FileManagerException("Falha ao criar item no disco: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return target;
    }

    /** Resolve um caminho relativo (vindo do frontend) para um Path real, garantindo que fique dentro de ROOT_DIR. */
    private Path resolveSafePath(String relativePath) {
        String cleaned = relativePath == null ? "" : relativePath.trim();
        // remove barra inicial para tratar sempre como relativo à raiz
        while (cleaned.startsWith("/") || cleaned.startsWith("\\")) {
            cleaned = cleaned.substring(1);
        }
        Path resolved = cleaned.isEmpty() ? ROOT_DIR : ROOT_DIR.resolve(cleaned).normalize();
        if (!resolved.startsWith(ROOT_DIR)) {
            // impede path traversal (ex: "../../etc/passwd")
            throw new FileManagerException("Caminho inválido.", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    /** Remove um arquivo ou pasta (com todo o conteúdo) recursivamente. */
    private void deleteRecursively(Path target) {
        try {
            if (Files.isDirectory(target)) {
                try (var stream = Files.list(target)) {
                    List<Path> children = stream.toList();
                    for (Path child : children) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.delete(target);
        } catch (IOException e) {
            throw new FileManagerException("Falha ao remover item: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Constrói a árvore recursivamente a partir de um diretório real, lendo o disco. */
    private TreeNodeResponse buildTree(Path path) {
        boolean isDirectory = Files.isDirectory(path);
        String name = path.equals(ROOT_DIR) ? "raiz" : path.getFileName().toString();
        String relativePath = ROOT_DIR.relativize(path).toString().replace('\\', '/');
        List<TreeNodeResponse> children = new ArrayList<>();

        if (isDirectory) {
            try (var stream = Files.list(path)) {
                List<Path> entries = stream.toList();
                for (Path entry : entries) {
                    children.add(buildTree(entry));
                }
            } catch (IOException e) {
                throw new FileManagerException("Falha ao ler diretório: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
            // pastas primeiro, depois arquivos, ambos em ordem alfabética
            children.sort(Comparator
                    .comparing((TreeNodeResponse n) -> !"FOLDER".equals(n.getType()))
                    .thenComparing(n -> n.getName().toLowerCase()));
        }

        return new TreeNodeResponse(
                name,
                isDirectory ? "FOLDER" : "FILE",
                relativePath,
                path.toString(),
                children
        );
    }
}
