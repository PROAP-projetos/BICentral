## Plan: Fix null fields in GET /api/paineis

Investigate why DTOs return null by checking mapping, repository usage, and schema alignment; then adjust service/controller to map correctly and validate results.

### Steps
1. Inspect entity `backend/src/main/java/com/bicentral/bicentral_backend/model/Painel.java` for column names/nullability; confirm DB table `add_painel` columns match `link_power_bi`, `imagem_capa_url`, `status_captura`.
2. Check DTO mapping in `backend/src/main/java/com/bicentral/bicentral_backend/service/PainelService.java` `converterParaDTO` and ensure `listarTodos` uses `findAllWithUsuario()` if user data is required to avoid lazy issues.
3. Verify controller `backend/src/main/java/com/bicentral/bicentral_backend/controller/PainelController.java` GET `/api/paineis/com-capa` delegates to the correct service method and returns DTO fields (add `/api/paineis` alias if front uses that path).
4. Confirm only one `@RestControllerAdvice` exists (`backend/src/main/java/com/bicentral/bicentral_backend/config/GlobalExceptionHandler.java`) and no duplicate bean registration; if another exists, consolidate to prevent ambiguous handler issues that can mask mapping errors.
5. Run a GET against the database with sample data; log results in `PainelService.listarTodos` (temporarily) or use debug to confirm entity fields are populated before mapping; compare DTO output to entity to spot missing assignments.

### Further Considerations
1. Do you want the repository to always eager-load `usuario` via `findAllWithUsuario()` or keep `findAll()` and drop user data from DTO?
2. Should `/api/paineis` (without `/com-capa`) return the same DTO list for backward compatibility?

