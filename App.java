import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Classe principal da aplicação Kanban.
 * Implementa um servidor HTTP simples para gerenciar tarefas.
 */
public class App {

    // Constantes da aplicação
    static final int PORT = 8080; // Porta em que o servidor HTTP será executado
    static final String CSV = "data_tasks.csv"; // Nome do arquivo CSV para persistência dos dados
    static final int MAX = 5000; // Capacidade máxima de tarefas

    // Arrays para armazenar os dados das tarefas em memória
    static String[] ids = new String[MAX]; // IDs das tarefas
    static String[] titulos = new String[MAX]; // Títulos das tarefas
    static String[] descrs = new String[MAX]; // Descrições das tarefas
    static int[] status = new int[MAX];     // Status das tarefas (0: TODO, 1: DOING, 2: DONE)
    static long[] criados = new long[MAX]; // Timestamp de criação das tarefas
    static int n = 0; // Contador de tarefas ativas

    /**
     * Método principal que inicia a aplicação.
     * Carrega os dados existentes, cria e inicia o servidor HTTP.
     * @param args Argumentos de linha de comando (não utilizados).
     * @throws Exception Em caso de erro ao iniciar o servidor ou carregar dados.
     */
    public static void main(String[] args) throws Exception {
        carregar(); // Carrega as tarefas do arquivo CSV

        // Cria um servidor HTTP na porta especificada
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        // Define o manipulador para a rota raiz ("/")
        server.createContext("/", new RootHandler());
        // Define o manipulador para a rota da API de tarefas ("/api/tasks")
        server.createContext("/api/tasks", new ApiTasksHandler());
        server.setExecutor(null); // Usa o executor padrão (cria novas threads conforme necessário)
        System.out.println("Servindo em http://localhost:" + PORT); // Informa a URL do servidor
        server.start(); // Inicia o servidor HTTP
    }

    /**
     * Manipulador HTTP para a rota raiz ("/").
     * Serve o arquivo HTML principal da aplicação (Kanban).
     */
    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            // Verifica se o método da requisição é GET
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 405, ""); // Retorna 405 Method Not Allowed se não for GET
                return;
            }
            // Obtém o conteúdo HTML da constante INDEX_HTML
            byte[] body = INDEX_HTML.getBytes(StandardCharsets.UTF_8);
            // Define o cabeçalho Content-Type como HTML
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            // Envia os cabeçalhos da resposta com status 200 OK e o tamanho do corpo
            ex.sendResponseHeaders(200, body.length);
            // Escreve o corpo da resposta
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    /**
     * Manipulador HTTP para a API de tarefas ("/api/tasks").
     * Gerencia as operações CRUD (Criar, Ler, Atualizar, Deletar) de tarefas.
     */
    static class ApiTasksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod(); // Obtém o método da requisição (GET, POST, PATCH, DELETE)
            URI uri = ex.getRequestURI(); // Obtém a URI da requisição
            String path = uri.getPath(); // Obtém o caminho da requisição

            try {
                // GET /api/tasks: Lista todas as tarefas
                if ("GET".equals(method) && "/api/tasks".equals(path)) {
                    sendJson(ex, 200, listarJSON()); // Retorna a lista de tarefas em formato JSON
                    return;
                }

                // POST /api/tasks: Cria uma nova tarefa
                if ("POST".equals(method) && "/api/tasks".equals(path)) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // Lê o corpo da requisição
                    String titulo = jsonGet(body, "titulo"); // Extrai o título do JSON
                    String descricao = jsonGet(body, "descricao"); // Extrai a descrição do JSON
                    // Valida se o título é obrigatório
                    if (titulo == null || titulo.isBlank()) {
                        sendJson(ex, 400, "{\"error\":\"titulo obrigatório\"}"); // Retorna erro 400 se o título estiver ausente
                        return;
                    }
                    // Cria a tarefa e a salva
                    Map<String, Object> t = criar(titulo, descricao == null ? "" : descricao);
                    salvar(); // Salva as tarefas no CSV
                    sendJson(ex, 200, toJsonTask(t)); // Retorna a tarefa criada em JSON
                    return;
                }

                // PATCH /api/tasks/{id}/status: Atualiza o status de uma tarefa
                if ("PATCH".equals(method) && path.startsWith("/api/tasks/") && path.endsWith("/status")) {
                    // Extrai o ID da tarefa do caminho da URL
                    String id = path.substring("/api/tasks/".length(), path.length() - "/status".length());
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // Lê o corpo da requisição
                    String stStr = jsonGet(body, "status"); // Extrai o status do JSON
                    // Valida se o status está presente
                    if (stStr == null) {
                        sendJson(ex, 400, "{\"error\":\"status ausente\"}");
                        return;
                    }
                    int st = clampStatus(parseIntSafe(stStr, 0)); // Converte e valida o status
                    int i = findIdxById(id); // Encontra o índice da tarefa pelo ID
                    // Verifica se a tarefa foi encontrada
                    if (i < 0) {
                        sendJson(ex, 404, "{\"error\":\"not found\"}"); // Retorna 404 Not Found
                        return;
                    }
                    status[i] = st; // Atualiza o status da tarefa
                    salvar(); // Salva as tarefas no CSV
                    sendJson(ex, 200, toJsonTask(mapOf(i))); // Retorna a tarefa atualizada em JSON
                    return;
                }

                // DELETE /api/tasks/{id}: Exclui uma tarefa
                if ("DELETE".equals(method) && path.startsWith("/api/tasks/")) {
                    String id = path.substring("/api/tasks/".length()); // Extrai o ID da tarefa do caminho da URL
                    int i = findIdxById(id); // Encontra o índice da tarefa pelo ID
                    // Verifica se a tarefa foi encontrada
                    if (i < 0) {
                        sendJson(ex, 404, "{\"error\":\"not found\"}"); // Retorna 404 Not Found
                        return;
                    }
                    // Remove a tarefa deslocando os elementos subsequentes
                    for (int k = i; k < n - 1; k++) {
                        ids[k] = ids[k + 1];
                        titulos[k] = titulos[k + 1];
                        descrs[k] = descrs[k + 1];
                        status[k] = status[k + 1];
                        criados[k] = criados[k + 1];
                    }
                    n--; // Decrementa o contador de tarefas
                    salvar(); // Salva as tarefas no CSV
                    sendJson(ex, 204, ""); // Retorna 204 No Content para indicar sucesso sem conteúdo
                    return;
                }

                send(ex, 404, ""); // Retorna 404 Not Found para rotas não mapeadas
            } catch (Exception e) {
                e.printStackTrace(); // Imprime o stack trace do erro
                sendJson(ex, 500, "{\"error\":\"server\"}"); // Retorna 500 Internal Server Error
            }
        }
    }

    /**
     * Conteúdo HTML da página principal da aplicação Kanban.
     * Inclui estilos CSS e lógica JavaScript para interagir com a API.
     */
    static final String INDEX_HTML = """
<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Kanban Local (sem framework)</title>
<style>
  :root{--bg:#f6f7fb;--card:#fff;--muted:#666;}
  *{box-sizing:border-box} body{margin:0;font:16px system-ui,Segoe UI,Roboto,Arial;background:var(--bg)}
  header{background:#111;color:#fff;padding:12px 16px}
  .wrap{max-width:1100px;margin:0 auto;padding:16px}
  form{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}
  input,textarea,button,select{border:1px solid #ddd;border-radius:10px;padding:10px;font:inherit}
  textarea{min-width:260px;min-height:40px}
  button{cursor:pointer}
  .board{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}
  .col{background:var(--card);border-radius:14px;box-shadow:0 6px 16px rgba(0,0,0,.06);padding:12px}
  .col h2{margin:6px 4px 10px}
  .task{border:1px solid #eee;border-radius:12px;background:#fafafa;margin:8px 0;padding:10px}
  .row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
  small{color:var(--muted)}
  .pill{font-size:.75rem;padding:2px 8px;border-radius:999px;background:#eef;border:1px solid #dde}
</style>
</head>
<body>
<header><b>Kanban Local</b> — Gestão de Atividades</header>
<div class="wrap">

  <h3>Nova tarefa</h3>
  <form id="f">
    <input id="t" placeholder="Título" required>
    <textarea id="d" placeholder="Descrição (opcional)"></textarea>
    <button>Adicionar</button>
  </form>

  <div class="board">
    <div class="col"><h2>To-Do</h2><div id="todo"></div></div>
    <div class="col"><h2>Doing</h2><div id="doing"></div></div>
    <div class="col"><h2>Done</h2><div id="done"></div></div>
  </div>

</div>

<script>
const API = "/api/tasks";

async function listar(){
  const r = await fetch(API);
  const data = await r.json();
  render(data);
}

function el(html){
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstChild;
}

// CORRIGIDO: função sem erro de sintaxe
function escapeHtml(s){
  return s.replace(/[&<>\""]/g, c => ({
    '&':'&amp;',
    '<':'&lt;',
    '>':'&gt;',
    '"':'&quot;',
    "'":'&#039;'
  }[c]));
}

function card(t){
  const div = el(`<div class="task">
      <strong>${escapeHtml(t.titulo)}</strong>
      <div class="row">
        <span class="pill">${['TODO','DOING','DONE'][t.status] || t.status}</span>
        <small>criado: ${new Date(t.criadoEm).toLocaleString()}</small>
      </div>
      <p>${escapeHtml(t.descricao||'')}</p>
      <div class="row">
        ${t.status!==0?'<button data-prev>◀</button>':''}
        ${t.status!==2?'<button data-next>▶</button>':''}
        <button data-del>Excluir</button>
      </div>
  </div>`);

  if(div.querySelector('[data-prev]')){
    div.querySelector('[data-prev]').onclick=()=>mover(t.id, t.status===2?1:0);
  }
  if(div.querySelector('[data-next]')){
    div.querySelector('[data-next]').onclick=()=>mover(t.id, t.status===0?1:2);
  }
  div.querySelector('[data-del]').onclick=()=>excluir(t.id);
  return div;
}

function render(arr){
  ['todo','doing','done'].forEach(id=>document.getElementById(id).innerHTML='');
  arr.filter(x=>x.status===0).forEach(x=>document.getElementById('todo').appendChild(card(x)));
  arr.filter(x=>x.status===1).forEach(x=>document.getElementById('doing').appendChild(card(x)));
  arr.filter(x=>x.status===2).forEach(x=>document.getElementById('done').appendChild(card(x)));
}

document.getElementById('f').onsubmit=async (e)=>{
  e.preventDefault();
  const titulo = document.getElementById('t').value.trim();
  const descricao = document.getElementById('d').value.trim();
  if(!titulo) return;
  await fetch(API,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({titulo,descricao})});
  e.target.reset(); listar();
};

async function mover(id,status){
  await fetch(`${API}/${id}/status`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({status})});
  listar();
}
async function excluir(id){
  await fetch(`${API}/${id}`,{method:'DELETE'}); listar();
}

listar();
</script>
</body>
</html>
""";

    /**
     * Carrega as tarefas do arquivo CSV para a memória.
     * Se o arquivo não existir, a lista de tarefas permanece vazia.
     */
    static void carregar() {
        n = 0; // Reseta o contador de tarefas
        Path p = Paths.get(CSV); // Obtém o caminho do arquivo CSV
        if (!Files.exists(p)) return; // Se o arquivo não existe, não faz nada
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line; // Variável para armazenar cada linha lida
            // Lê o arquivo linha por linha
            while ((line = br.readLine()) != null) {
                // Ignora linhas em branco ou a linha de cabeçalho
                if (line.isBlank() || line.startsWith("id;")) continue;
                String[] a = splitCsv(line); // Divide a linha em campos CSV
                if (a.length < 5) continue; // Ignora linhas com menos de 5 campos
                if (n >= MAX) break; // Interrompe se a capacidade máxima for atingida
                // Atribui os valores lidos aos arrays em memória
                ids[n] = a[0];
                titulos[n] = a[1];
                descrs[n] = a[2];
                status[n] = clampStatus(parseIntSafe(a[3], 0)); // Converte e valida o status
                criados[n] = parseLongSafe(a[4], System.currentTimeMillis()); // Converte e valida o timestamp
                n++; // Incrementa o contador de tarefas
            }
        } catch (IOException e) {
            System.out.println("Falha ao ler CSV: " + e.getMessage()); // Imprime erro em caso de falha na leitura
        }
    }

    /**
     * Salva as tarefas da memória para o arquivo CSV.
     * Cria o arquivo se não existir e sobrescreve o conteúdo existente.
     */
    static void salvar() {
        Path p = Paths.get(CSV); // Obtém o caminho do arquivo CSV
        try {
            // Cria diretórios pai se não existirem
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            // Abre o arquivo para escrita, criando-o se não existir e truncando o existente
            try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                bw.write("id;titulo;descricao;status;criadoEm\n"); // Escreve o cabeçalho do CSV
                // Escreve cada tarefa nos arrays para o arquivo CSV
                for (int i = 0; i < n; i++) {
                    bw.write(esc(ids[i]) + ";" + esc(titulos[i]) + ";" + esc(descrs[i]) + ";"
                            + status[i] + ";" + criados[i] + "\n");
                }
            }
        } catch (IOException e) {
            System.out.println("Falha ao salvar CSV: " + e.getMessage()); // Imprime erro em caso de falha na escrita
        }
    }

    /**
     * Cria uma nova tarefa e a adiciona aos arrays em memória.
     * @param titulo O título da nova tarefa.
     * @param descr A descrição da nova tarefa.
     * @return Um mapa contendo os detalhes da tarefa criada.
     */
    static Map<String, Object> criar(String titulo, String descr) {
        if (n >= MAX) throw new RuntimeException("Capacidade cheia"); // Lança exceção se a capacidade máxima for atingida
        String id = UUID.randomUUID().toString().substring(0, 8); // Gera um ID único para a tarefa
        // Atribui os valores da nova tarefa aos arrays
        ids[n] = id;
        titulos[n] = titulo;
        descrs[n] = descr;
        status[n] = 0; // Define o status inicial como TODO (0)
        criados[n] = System.currentTimeMillis(); // Define o timestamp de criação
        n++; // Incrementa o contador de tarefas
        return mapOf(n - 1); // Retorna um mapa da tarefa recém-criada
    }

    /**
     * Encontra o índice de uma tarefa nos arrays em memória pelo seu ID.
     * @param id O ID da tarefa a ser encontrada.
     * @return O índice da tarefa se encontrada, ou -1 se não encontrada.
     */
    static int findIdxById(String id) {
        for (int i = 0; i < n; i++) {
            if (ids[i].equals(id)) return i; // Retorna o índice se o ID corresponder
        }
        return -1; // Retorna -1 se a tarefa não for encontrada
    }

    /**
     * Cria um mapa (LinkedHashMap) com os detalhes de uma tarefa a partir de seu índice.
     * @param i O índice da tarefa nos arrays.
     * @return Um mapa contendo os detalhes da tarefa.
     */
    static Map<String, Object> mapOf(int i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ids[i]);
        m.put("titulo", titulos[i]);
        m.put("descricao", descrs[i]);
        m.put("status", status[i]);
        m.put("criadoEm", criados[i]);
        return m;
    }

    /**
     * Gera uma string JSON representando todas as tarefas em memória.
     * @return Uma string JSON de um array de objetos de tarefa.
     */
    static String listarJSON() {
        StringBuilder sb = new StringBuilder("["); // Inicia o JSON como um array
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(","); // Adiciona vírgula entre os objetos, exceto para o primeiro
            sb.append(toJsonTask(mapOf(i))); // Adiciona a representação JSON de cada tarefa
        }
        sb.append("]"); // Fecha o array JSON
        return sb.toString();
    }

    /**
     * Converte um mapa de tarefa em uma string JSON.
     * @param t O mapa contendo os detalhes da tarefa.
     * @return Uma string JSON representando a tarefa.
     */
    static String toJsonTask(Map<String, Object> t) {
        return "{\"id\":\"" + jsonEsc((String) t.get("id")) + "\"," +
                "\"titulo\":\"" + jsonEsc((String) t.get("titulo")) + "\"," +
                "\"descricao\":\"" + jsonEsc((String) t.get("descricao")) + "\"," +
                "\"status\":" + t.get("status") + "," +
                "\"criadoEm\":" + t.get("criadoEm") + "}";
    }

    /**
     * Extrai o valor de uma chave de uma string JSON simples.
     * Suporta apenas JSONs de nível único e não aninhados.
     * @param body A string JSON de entrada.
     * @param key A chave cujo valor deve ser extraído.
     * @return O valor associado à chave, ou null se a chave não for encontrada.
     */
    static String jsonGet(String body, String key) {
        if (body == null) return null;
        String s = body.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inQ = !inQ;
            if (c == ',' && !inQ) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else cur.append(c);
        }
        if (cur.length() > 0) parts.add(cur.toString());

        for (String kv : parts) {
            int i = kv.indexOf(':');
            if (i <= 0) continue;
            String k = kv.substring(0, i).trim();
            String v = kv.substring(i + 1).trim();
            k = stripQuotes(k);
            if (key.equals(k)) {
                v = stripQuotes(v);
                return v;
            }
        }
        return null;
    }

    /**
     * Remove aspas de uma string, se presentes, e trata aspas escapadas.
     * @param s A string de entrada.
     * @return A string sem aspas externas e com aspas internas desescapadas.
     */
    static String stripQuotes(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s;
    }

    /**
     * Envia uma resposta HTTP com um código de status e corpo.
     * @param ex O HttpExchange da requisição.
     * @param code O código de status HTTP a ser enviado.
     * @param body O corpo da resposta.
     * @throws IOException Em caso de erro de I/O.
     */
    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Envia uma resposta HTTP com um código de status e corpo JSON.
     * Define o cabeçalho Content-Type como application/json.
     * @param ex O HttpExchange da requisição.
     * @param code O código de status HTTP a ser enviado.
     * @param body O corpo da resposta JSON.
     * @throws IOException Em caso de erro de I/O.
     */
    static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(ex, code, body == null ? "" : body);
    }

    /**
     * Escapa uma string para ser usada em formato CSV.
     * Adiciona aspas se a string contiver ponto e vírgula, aspas ou quebras de linha.
     * Aspas internas são duplicadas.
     * @param s A string a ser escapada.
     * @return A string escapada para CSV.
     */
    static String esc(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    /**
     * Divide uma linha CSV em um array de strings.
     * Lida com campos entre aspas e aspas escapadas.
     * @param line A linha CSV a ser dividida.
     * @return Um array de strings, onde cada elemento é um campo CSV.
     */
    static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else inQ = false;
                } else cur.append(c);
            } else {
                if (c == ';') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else if (c == '"') {
                    inQ = true;
                } else cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    /**
     * Escapa uma string para ser usada em formato JSON.
     * Escapa barras invertidas, aspas duplas e quebras de linha.
     * @param s A string a ser escapada.
     * @return A string escapada para JSON.
     */
    static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    /**
     * Garante que o valor do status esteja dentro do intervalo válido (0, 1 ou 2).
     * @param s O valor do status a ser validado.
     * @return O valor do status ajustado para o intervalo válido.
     */
    static int clampStatus(int s) {
        return Math.max(0, Math.min(2, s));
    }

    /**
     * Converte uma string para um inteiro de forma segura.
     * Retorna um valor padrão se a conversão falhar.
     * @param s A string a ser convertida.
     * @param def O valor padrão a ser retornado em caso de erro.
     * @return O inteiro convertido ou o valor padrão.
     */
    static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Converte uma string para um long de forma segura.
     * Retorna um valor padrão se a conversão falhar.
     * @param s A string a ser convertida.
     * @param def O valor padrão a ser retornado em caso de erro.
     * @return O long convertido ou o valor padrão.
     */
    static long parseLongSafe(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}


