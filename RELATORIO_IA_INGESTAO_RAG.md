# Relatorio de IA, Ingestao e RAG do BI Central

## Resumo executivo

O projeto tem uma base pronta de IA e ingestao, mas ainda nao fecha um RAG completo.

O que ja existe hoje:
- Integracao com LangChain4j para Gemini e Ollama.
- Endpoint de teste para chamar os modelos.
- Pipeline de ingestao para PDF e XLSX.
- Limpeza, chunking e exportacao de chunks em JSON com metadados.

O que ainda nao apareceu no codigo:
- Embeddings.
- Vector store.
- Retriever semantico.
- Fluxo de pergunta e resposta com contexto recuperado.
- Persistencia real dos chunks para busca posterior.

## O que ja esta pronto

### 1) Configuracao de modelos de IA

Arquivo: [backend/src/main/java/com/bicentral/bicentral_backend/config/AiConfig.java](backend/src/main/java/com/bicentral/bicentral_backend/config/AiConfig.java)

```java
@Configuration
public class AiConfig {

    @Value("${langchain4j.google-ai-gemini.api-key}")
    private String geminiApiKey;

    @Value("${ollama.base.url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model.name:llama3}")
    private String ollamaModelName;

    @Bean("geminiModel")
    public ChatLanguageModel geminiModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-2.5-flash")
                .build();
    }

    @Bean("ollamaModel")
    public ChatLanguageModel ollamaModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .build();
    }
}
```

### 2) Endpoint de teste para IA e ingestao

Arquivo: [backend/src/main/java/com/bicentral/bicentral_backend/controller/AiTestController.java](backend/src/main/java/com/bicentral/bicentral_backend/controller/AiTestController.java)

```java
@RestController
@RequestMapping("/ai/test")
public class AiTestController {

    @Qualifier("geminiModel")
    @Autowired
    private ChatLanguageModel geminiModel;

    @Qualifier("ollamaModel")
    @Autowired
    private ChatLanguageModel ollamaModel;

    @Autowired
    private IngestaoService ingestaoService;

    @GetMapping("/gemini")
    public String testGemini(@RequestParam(defaultValue = "Olá!") String msg) {
        return geminiModel.chat(msg);
    }

    @GetMapping("/ollama")
    public String testOllama(@RequestParam(defaultValue = "Olá!") String msg) {
        return ollamaModel.chat(msg);
    }
}
```

### 3) Pipeline de ingestao e chunking

Arquivo: [backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java)

```java
public String extrairTextoPDF(String caminhoArquivo) throws IOException {
    File file = new File(caminhoArquivo);
    try (PDDocument document = PDDocument.load(file)) {
        PDFTextStripper stripper = new PDFTextStripper();
        return stripper.getText(document);
    }
}

public String extrairTextoExcel(String caminhoArquivo) throws IOException {
    StringBuilder sb = new StringBuilder();

    try (InputStream is = new FileInputStream(new File(caminhoArquivo));
         Workbook workbook = new XSSFWorkbook(is)) {

        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        DataFormatter formatter = new DataFormatter();

        if (headerRow == null) return "";

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row currentRow = sheet.getRow(i);
            if (currentRow == null) continue;

            StringBuilder linhaNarrativa = new StringBuilder();

            for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                String cabecalho = formatter.formatCellValue(headerRow.getCell(j));
                String valor = formatter.formatCellValue(currentRow.getCell(j));

                if (!valor.trim().isEmpty()) {
                    linhaNarrativa.append("[").append(cabecalho).append(": ").append(valor).append("] ");
                }
            }
            sb.append(linhaNarrativa).append("\n");
        }
    }
    return sb.toString();
}
```

```java
public List<String> fatiarTexto(String textoLimpo) {
    int tamanhoChunk = 512;
    int overlap = 64;

    String[] palavras = textoLimpo.split("\\s+");
    List<String> chunks = new ArrayList<>();

    if (palavras.length <= tamanhoChunk) {
        chunks.add(textoLimpo);
        return chunks;
    }

    for (int i = 0; i < palavras.length; i += (tamanhoChunk - overlap)) {
        StringBuilder chunkAtual = new StringBuilder();

        for (int j = i; j < i + tamanhoChunk && j < palavras.length; j++) {
            chunkAtual.append(palavras[j]).append(" ");
        }

        chunks.add(chunkAtual.toString().trim());

        if (i + tamanhoChunk >= palavras.length) break;
    }

    return chunks;
}
```

### 4) Preview de chunks com metadados

Arquivo: [backend/src/main/java/com/bicentral/bicentral_backend/controller/AiTestController.java](backend/src/main/java/com/bicentral/bicentral_backend/controller/AiTestController.java)

```java
@PostMapping("/ingestao-json")
public ResponseEntity<List<ChunkDTO>> gerarPreviewIngestao(
        @RequestParam String caminhoArquivo,
        @RequestParam(defaultValue = "COMUNICACAO") String equipe,
        @RequestParam(defaultValue = "Publico") String acesso,
        @RequestParam(required = false) String nomeArquivo
) throws Exception {
    String textoBruto;
    String caminhoLower = caminhoArquivo.toLowerCase();

    if (caminhoLower.endsWith(".xlsx")) {
        textoBruto = ingestaoService.extrairTextoExcel(caminhoArquivo);
    } else {
        textoBruto = ingestaoService.extrairTextoPDF(caminhoArquivo);
    }

    String textoLimpo = ingestaoService.limparTexto(textoBruto);
    List<String> chunks = ingestaoService.fatiarTexto(textoLimpo);

    String nomeFinalArquivo = (nomeArquivo == null || nomeArquivo.isBlank())
            ? new java.io.File(caminhoArquivo).getName()
            : nomeArquivo;

    ingestaoService.mockSalvarNoBanco(chunks, equipe, acesso, nomeFinalArquivo);
    List<ChunkDTO> json = ingestaoService.montarChunksComMetadados(chunks, equipe, acesso, nomeFinalArquivo);
    return ResponseEntity.ok(json);
}
```

### 5) Estrutura dos chunks

Arquivo: [backend/src/main/java/com/bicentral/bicentral_backend/dto/ChunkDTO.java](backend/src/main/java/com/bicentral/bicentral_backend/dto/ChunkDTO.java)

```java
@Data
@AllArgsConstructor
public class ChunkDTO {
    private String conteudo;
    private String equipe;
    private String acesso;
    private String grupoId;
    private String nomeArquivo;
}
```

### 6) Exportacao local em JSON

Arquivo: [backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java](backend/src/main/java/com/bicentral/bicentral_backend/service/IngestaoService.java)

```java
public void mockSalvarNoBanco(List<String> chunks, String equipe, String acesso, String nomeArquivo) {
    List<ChunkDTO> listaParaSalvar = montarChunksComMetadados(chunks, equipe, acesso, nomeArquivo);

    try {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        File pasta = new File("output");
        if (!pasta.exists()) pasta.mkdirs();

        File ficheiroJson = new File("output/processado_" + System.currentTimeMillis() + ".json");
        mapper.writeValue(ficheiroJson, listaParaSalvar);

        System.out.println("\n✅ SUCESSO: Dados exportados para: " + ficheiroJson.getAbsolutePath());
    } catch (Exception e) {
        System.err.println("Erro ao gerar ficheiro JSON: " + e.getMessage());
    }
}
```

## O que ainda falta para virar RAG

Nao encontrei ainda nenhum destes blocos no backend:
- `EmbeddingModel`
- `EmbeddingStore`
- `VectorStore`
- `ContentRetriever`
- `RetrievalAugmentor`
- chain de pergunta e resposta com contexto recuperado

Ou seja: hoje o sistema prepara dados, mas nao faz a busca semantica nem monta contexto para o LLM responder com base nos documentos.

## Estado atual do projeto

O BI Central hoje esta assim:
- IA: pronto para teste de modelo.
- Ingestao: parcialmente pronta, com parse e chunking.
- RAG: ainda nao implementado.
- Frontend: sem fluxo de IA visivel.

## Risco importante

Ha credenciais sensiveis em texto puro em [backend/src/main/resources/application.properties](backend/src/main/resources/application.properties). Isso deve ser movido para variaveis de ambiente o quanto antes.

## Conclusao

Se a meta e ter um RAG funcional, este projeto esta no estagio 1: preparacao de dados e teste de modelos. O proximo passo natural e ligar os chunks a embeddings + persistencia vetorial + consulta semantica.