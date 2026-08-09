package com.xzm.xzm_interview_helper.service;

import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AlgorithmJudgeService {

    private static final Pattern FORBIDDEN_JAVA = Pattern.compile(
            "(?m)^\\s*package\\s+|\\bpublic\\s+(?:(?:final|abstract|sealed)\\s+)*class\\s+|\\bclass\\s+Main\\b");
    private static final Pattern IMPORT_JAVA = Pattern.compile(
            "(?m)^\\s*(import\\s+(?:static\\s+)?[\\w.*]+\\s*;)\\s*(?://[^\\r\\n]*)?$");
    private static final Pattern JAVA_COMPILER_DIAGNOSTIC = Pattern.compile(
            "(?im)(?:\\.java:\\d+:\\s*error:|error:\\s*compilation failed|\\bjavac\\b)");
    private static final Pattern JAVA_UNICODE_ESCAPE = Pattern.compile(
            "(?i)\\\\u+[0-9a-f]{4}");
    /**
     * Official cases and candidate code share one sandbox process. Official
     * submissions therefore have to be pure functions: otherwise candidate
     * code could spoof the stdout protocol or exfiltrate hidden inputs.
     */
    private static final Pattern FORBIDDEN_OFFICIAL_JUDGE_API = Pattern.compile(
            "(?is)(?:"
                    // A static import can turn System.exit/out into bare
                    // identifiers and bypass an otherwise qualified-name
                    // policy. Official submissions never need static imports:
                    // java.util.* is already supplied by the harness.
                    + "(?m:^\\s*import\\s+static\\b)"
                    + "|\\b(?:java\\.lang\\.)?System\\s*(?:\\.|::)\\s*"
                    + "(?:in|out|err|console|setIn|setOut|setErr|exit|gc|"
                    + "load|loadLibrary|getenv|getProperties|getProperty|"
                    + "setProperty|clearProperty|setSecurityManager)\\b"
                    + "|\\b(?:java\\.io|java\\.nio\\.file|java\\.net|java\\.lang\\.reflect"
                    + "|java\\.lang\\.invoke|java\\.util\\.concurrent|javax\\.tools"
                    + "|javax\\.script|sun\\.misc|jdk\\.internal)\\b"
                    + "|\\b(?:Runtime|ProcessBuilder|FileDescriptor|FileOutputStream|PrintStream"
                    + "|Formatter|Socket|URLConnection|HttpClient|ClassLoader|MethodHandles"
                    + "|StackWalker|CompletableFuture|Executor|Executors|ForkJoinPool"
                    + "|Process|ProcessHandle|SecurityManager|Thread|Timer|TimerTask)\\b"
                    // Do not let candidate code read Main.java to recover the
                    // per-run result token or recursively invoke the harness.
                    + "|\\.\\s*class\\b|\\bMain\\s*(?:\\.|::)"
                    + "|\\b(?:Class\\s*(?:\\.|::)\\s*forName|getClass|getCallerClass"
                    + "|getDeclaringClass|getDeclaredMethod|getDeclaredField"
                    + "|getDeclaredConstructor|getContextClassLoader|loadClass|getMethod|getField"
                    + "|getResource|getResourceAsStream|invoke|newInstance)\\b"
                    + ")");
    private static final Map<String, JudgeDefinition> DEFINITIONS = definitions();

    private final RestTemplate restTemplate;
    private final String pistonUrl;
    private final String pistonToken;
    private Supplier<String> resultTokenSupplier =
            () -> UUID.randomUUID().toString().replace("-", "");

    public AlgorithmJudgeService(
            @Value("${app.algorithm.piston-url:http://127.0.0.1:2000/api/v2/execute}") String pistonUrl,
            @Value("${app.algorithm.piston-token:}") String pistonToken
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(25_000);
        this.restTemplate = new RestTemplate(factory);
        this.pistonUrl = pistonUrl;
        this.pistonToken = pistonToken;
    }

    public AlgorithmExecutionResponse execute(
            AlgorithmProblemSummary problem,
            String language,
            String code,
            boolean submit
    ) {
        AlgorithmExecutionResponse result = new AlgorithmExecutionResponse();
        JudgeDefinition definition = DEFINITIONS.get(problem.getSlug());
        boolean compileOnly = definition == null || !problem.isJudgeable();
        if (!"java".equalsIgnoreCase(language)) {
            result.setStatus("UNSUPPORTED_LANGUAGE");
            result.setError("接口判题当前支持 Java 17；其他语言运行支持将在沙箱扩容后开放。");
            return result;
        }
        String policySource = policyRelevantJava(code);
        if (FORBIDDEN_JAVA.matcher(policySource).find()) {
            result.setStatus("COMPILE_ERROR");
            result.setError("请只提交非 public 的 Solution 类，不要声明 package 或 Main 类。");
            return result;
        }

        // Java translates Unicode escapes before tokenizing comments and
        // identifiers. Reject them on the original source, then evaluate the
        // API policy on a comment/literal-neutralized view so constructs such
        // as System/**/.out cannot evade the boundary.
        if (JAVA_UNICODE_ESCAPE.matcher(code).find()
                || FORBIDDEN_OFFICIAL_JUDGE_API.matcher(policySource).find()) {
            result.setStatus("COMPILE_ERROR");
            result.setError("官方判题中的 Solution 必须是纯函数实现，不能访问输出流、文件、网络、进程或反射 API。");
            return result;
        }

        int caseCount = compileOnly
                ? 0
                : submit ? definition.expected().size() : Math.min(2, definition.expected().size());
        // Piston's Java runtime uses source-file mode and executes the first
        // top-level class in the file. Keep the controlled Main entry point
        // first; Java still resolves the candidate's later-declared Solution.
        String resultToken = compileOnly ? "" : nextResultToken();
        String runner = compileOnly ? """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("__XZM_COMPILE_OK__");
                    }
                }
                """ : definition.runner(caseCount, resultToken);
        String source = prepareSource(runner, code, compileOnly);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", "java");
        payload.put("version", "*");
        payload.put("files", List.of(Map.of("name", "Main.java", "content", source)));
        payload.put("stdin", "");
        payload.put("args", List.of());
        payload.put("compile_timeout", 10_000);
        payload.put("compile_cpu_time", 10_000);
        payload.put("compile_memory_limit", 384L * 1024L * 1024L);
        payload.put("run_timeout", 3_000);
        payload.put("run_cpu_time", 3_000);
        payload.put("run_memory_limit", 256L * 1024L * 1024L);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!pistonToken.isBlank()) {
            headers.setBearerAuth(pistonToken);
        }
        long started = System.nanoTime();
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    pistonUrl,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
            result.setRuntimeMs((System.nanoTime() - started) / 1_000_000L);
            Map<?, ?> body = response.getBody();
            Map<?, ?> compile = body != null && body.get("compile") instanceof Map<?, ?> value ? value : Map.of();
            Map<?, ?> run = body != null && body.get("run") instanceof Map<?, ?> value ? value : Map.of();
            Integer compileCode = nullableInteger(compile.get("code"));
            Integer runCode = nullableInteger(run.get("code"));
            String compileError = string(compile.get("stderr"));
            String runError = string(run.get("stderr"));
            String compileSignal = string(compile.get("signal"));
            String runSignal = string(run.get("signal"));
            String compileOutput = string(compile.get("output"));
            String compileStatus = string(compile.get("status"));
            String runStatus = string(run.get("status"));
            String compileMessage = string(compile.get("message"));
            String runMessage = string(run.get("message"));
            String stdout = string(run.get("stdout"));
            if (stdout.isBlank()) {
                stdout = string(run.get("output"));
            }
            // Submissions may contain server-owned cases. Candidate-controlled
            // stdout/stderr must never be returned or persisted.
            result.setOutput(submit ? null : clip(stdout, 12_000));
            result.setTotalCases(caseCount);
            if ((compileCode != null && compileCode != 0)
                    || !compileSignal.isBlank()
                    || !compileError.isBlank()
                    || !compileStatus.isBlank()) {
                result.setStatus("COMPILE_ERROR");
                result.setError(submit
                        ? "代码未通过编译，请先使用“运行”定位编译错误后再提交。"
                        : clip(firstNonBlank(
                                compileError,
                                compileMessage,
                                compileStatus,
                                compileOutput,
                                compileSignal
                        ), 8_000));
                return result;
            }
            if (runCode == null
                    || runCode != 0
                    || !runSignal.isBlank()
                    || !runError.isBlank()
                    || !runStatus.isBlank()) {
                String failure = firstNonBlank(
                        runError,
                        runMessage,
                        runStatus,
                        runSignal,
                        stdout
                );
                result.setStatus(isCompilerDiagnostic(failure) ? "COMPILE_ERROR" : "RUNTIME_ERROR");
                result.setError(submit
                        ? "代码执行失败，请检查异常处理、边界条件和复杂度后重试。"
                        : clip(failure, 8_000));
                return result;
            }
            if (compileOnly) {
                result.setStatus("COMPILED");
                result.setPassedCases(0);
                result.setTotalCases(0);
                result.setOutput("编译通过。该题暂未配置自动判题用例，本次结果不代表代码逻辑正确。");
                return result;
            }

            JudgeOutput judgeOutput = parseJudgeOutput(stdout, resultToken);
            List<String> actual = judgeOutput.values();
            List<String> expected = definition.expected().subList(0, caseCount);
            int passed = 0;
            for (int index = 0; index < caseCount; index++) {
                String actualLine = index < actual.size() ? actual.get(index) : "<missing>";
                boolean ok = expected.get(index).equals(actualLine);
                if (ok) {
                    passed++;
                }
                result.getCaseResults().add("用例 " + (index + 1) + (ok ? " 通过" : " 未通过"));
            }
            if (judgeOutput.hasUnexpectedOutput() || actual.size() != caseCount) {
                passed = 0;
                result.getCaseResults().clear();
                result.getCaseResults().add("检测到非判题输出，官方提交必须只返回方法结果");
            }
            result.setPassedCases(passed);
            result.setStatus(passed == caseCount ? "ACCEPTED" : "WRONG_ANSWER");
            if (submit) {
                result.setOutput("官方判题完成：" + passed + "/" + caseCount + " 个用例通过。");
            } else {
                result.setOutput(clip(String.join("\n", actual), 12_000));
            }
            return result;
        } catch (RestClientException exception) {
            log.warn("Algorithm judge unavailable for {}: {}", problem.getSlug(),
                    exception.getClass().getSimpleName());
            result.setRuntimeMs((System.nanoTime() - started) / 1_000_000L);
            result.setStatus("JUDGE_UNAVAILABLE");
            result.setError("判题沙箱暂时不可用，请稍后重试；代码草稿不会丢失。");
            return result;
        }
    }

    /**
     * Executes only user-authored test code inside the Piston sandbox.
     *
     * <p>This path deliberately does not read {@link #DEFINITIONS}, does not
     * persist a submission, and never emits an official judge status such as
     * {@code ACCEPTED}. A successful run without an expected value is
     * {@code EXECUTED}; otherwise it is {@code CUSTOM_PASSED} or
     * {@code CUSTOM_FAILED}.</p>
     */
    public AlgorithmExecutionResponse executeCustom(
            AlgorithmProblemSummary problem,
            String language,
            String code,
            String driver,
            String expectedOutput
    ) {
        AlgorithmExecutionResponse result = new AlgorithmExecutionResponse();
        if (!"java".equalsIgnoreCase(language)) {
            result.setStatus("CUSTOM_FAILED");
            result.setError("自定义测试当前仅支持 Java 17。");
            return result;
        }
        if (FORBIDDEN_JAVA.matcher(policyRelevantJava(code)).find()
                || FORBIDDEN_JAVA.matcher(policyRelevantJava(driver)).find()) {
            result.setStatus("CUSTOM_FAILED");
            result.setError("请只提交非 public 的 Solution 类；测试代码不要声明 package、public class 或 Main 类。");
            return result;
        }

        String source = prepareCustomSource(driver, code);
        Map<String, Object> payload = pistonPayload(source);
        HttpHeaders headers = pistonHeaders();
        long started = System.nanoTime();
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    pistonUrl,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
            result.setRuntimeMs((System.nanoTime() - started) / 1_000_000L);
            Map<?, ?> body = response.getBody();
            Map<?, ?> compile = body != null && body.get("compile") instanceof Map<?, ?> value
                    ? value : Map.of();
            Map<?, ?> run = body != null && body.get("run") instanceof Map<?, ?> value
                    ? value : Map.of();
            Integer compileCode = nullableInteger(compile.get("code"));
            Integer runCode = nullableInteger(run.get("code"));
            String compileError = string(compile.get("stderr"));
            String runError = string(run.get("stderr"));
            String compileSignal = string(compile.get("signal"));
            String runSignal = string(run.get("signal"));
            String compileOutput = string(compile.get("output"));
            String compileStatus = string(compile.get("status"));
            String runStatus = string(run.get("status"));
            String compileMessage = string(compile.get("message"));
            String runMessage = string(run.get("message"));
            String stdout = string(run.get("stdout"));
            if (stdout.isBlank()) {
                stdout = string(run.get("output"));
            }
            result.setOutput(clip(stdout, 12_000));

            if ((compileCode != null && compileCode != 0)
                    || !compileSignal.isBlank()
                    || !compileError.isBlank()
                    || !compileStatus.isBlank()) {
                result.setStatus("CUSTOM_FAILED");
                result.setError(clip(
                        firstNonBlank(
                                compileError,
                                compileMessage,
                                compileStatus,
                                compileOutput,
                                compileSignal
                        ),
                        8_000
                ));
                result.getCaseResults().add("自定义测试编译失败");
                return result;
            }
            if (runCode == null
                    || runCode != 0
                    || !runSignal.isBlank()
                    || !runError.isBlank()
                    || !runStatus.isBlank()) {
                result.setStatus("CUSTOM_FAILED");
                result.setError(clip(
                        firstNonBlank(runError, runMessage, runStatus, runSignal, stdout),
                        8_000
                ));
                result.getCaseResults().add("自定义测试运行失败");
                return result;
            }

            if (expectedOutput == null || expectedOutput.isBlank()) {
                result.setStatus("EXECUTED");
                result.getCaseResults().add("自定义测试已执行（未设置预期输出）");
                return result;
            }
            result.setTotalCases(1);
            boolean matches = normalizeCustomOutput(expectedOutput)
                    .equals(normalizeCustomOutput(stdout));
            result.setPassedCases(matches ? 1 : 0);
            result.setStatus(matches ? "CUSTOM_PASSED" : "CUSTOM_FAILED");
            result.getCaseResults().add(matches ? "自定义测试通过" : "自定义测试未通过");
            if (!matches) {
                result.setError("实际输出与预期输出不一致。");
            }
            return result;
        } catch (RestClientException exception) {
            log.warn("Custom algorithm sandbox unavailable for {}: {}",
                    problem.getSlug(), exception.getClass().getSimpleName());
            result.setRuntimeMs((System.nanoTime() - started) / 1_000_000L);
            result.setStatus("CUSTOM_FAILED");
            result.setError("代码沙箱暂时不可用，请稍后重试；代码草稿不会丢失。");
            result.getCaseResults().add("自定义测试执行服务不可用");
            return result;
        }
    }

    public static Set<String> judgeableSlugs() {
        return DEFINITIONS.keySet();
    }

    private static String prepareSource(String runner, String code, boolean includeSupportTypes) {
        Matcher imports = IMPORT_JAVA.matcher(code);
        List<String> importLines = new ArrayList<>();
        while (imports.find()) {
            importLines.add(imports.group(1).trim());
        }
        String candidate = imports.replaceAll("").trim();
        StringBuilder source = new StringBuilder("import java.util.*;\n");
        importLines.stream()
                .filter(line -> !"import java.util.*;".equals(line))
                .distinct()
                .forEach(line -> source.append(line).append('\n'));
        source.append('\n').append(runner.trim()).append("\n\n");
        if (includeSupportTypes) {
            appendLeetCodeSupportTypes(source, candidate);
        }
        return source.append(candidate).append('\n').toString();
    }

    private static String prepareCustomSource(String driver, String code) {
        List<String> importLines = new ArrayList<>();
        String cleanDriver = extractImports(driver, importLines);
        String candidate = extractImports(code, importLines);
        StringBuilder source = new StringBuilder("import java.util.*;\n");
        importLines.stream()
                .filter(line -> !"import java.util.*;".equals(line))
                .distinct()
                .forEach(line -> source.append(line).append('\n'));
        source.append("\npublic class Main {\n")
                .append("    public static void main(String[] args) throws Exception {\n");
        for (String line : cleanDriver.split("\\R", -1)) {
            source.append("        ").append(line).append('\n');
        }
        source.append("    }\n}\n\n");
        appendLeetCodeSupportTypes(source, candidate + "\n" + cleanDriver);
        return source.append(candidate.trim()).append('\n').toString();
    }

    private static String extractImports(String source, List<String> imports) {
        Matcher matcher = IMPORT_JAVA.matcher(source);
        while (matcher.find()) {
            imports.add(matcher.group(1).trim());
        }
        return matcher.replaceAll("").trim();
    }

    /**
     * Builds a policy-only Java view in which comments and literals are
     * replaced by whitespace while line boundaries are retained.
     *
     * <p>A regex over raw source can be bypassed with block comments between
     * tokens, while naively deleting comment-looking text can be bypassed by a
     * marker inside a string. This small lexer handles both cases. It is not a
     * compiler and is deliberately fail-closed; malformed source is still
     * sent to javac only after the policy check.</p>
     */
    private static String policyRelevantJava(String source) {
        StringBuilder result = new StringBuilder(source.length());
        JavaLexicalState state = JavaLexicalState.CODE;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            char afterNext = index + 2 < source.length() ? source.charAt(index + 2) : '\0';

            if (state == JavaLexicalState.CODE) {
                if (current == '/' && next == '/') {
                    result.append("  ");
                    index++;
                    state = JavaLexicalState.LINE_COMMENT;
                } else if (current == '/' && next == '*') {
                    result.append("  ");
                    index++;
                    state = JavaLexicalState.BLOCK_COMMENT;
                } else if (current == '"' && next == '"' && afterNext == '"') {
                    result.append("   ");
                    index += 2;
                    state = JavaLexicalState.TEXT_BLOCK;
                } else if (current == '"') {
                    result.append(' ');
                    state = JavaLexicalState.STRING;
                } else if (current == '\'') {
                    result.append(' ');
                    state = JavaLexicalState.CHARACTER;
                } else {
                    result.append(current);
                }
                continue;
            }

            if (current == '\n' || current == '\r') {
                result.append(current);
                if (state == JavaLexicalState.LINE_COMMENT) {
                    state = JavaLexicalState.CODE;
                }
                continue;
            }
            if (state == JavaLexicalState.LINE_COMMENT) {
                result.append(' ');
            } else if (state == JavaLexicalState.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    result.append("  ");
                    index++;
                    state = JavaLexicalState.CODE;
                } else {
                    result.append(' ');
                }
            } else if (state == JavaLexicalState.TEXT_BLOCK) {
                if (current == '\\' && next != '\0') {
                    result.append("  ");
                    index++;
                } else if (current == '"' && next == '"' && afterNext == '"') {
                    result.append("   ");
                    index += 2;
                    state = JavaLexicalState.CODE;
                } else {
                    result.append(' ');
                }
            } else {
                if (current == '\\' && next != '\0') {
                    result.append("  ");
                    index++;
                } else if ((state == JavaLexicalState.STRING && current == '"')
                        || (state == JavaLexicalState.CHARACTER && current == '\'')) {
                    result.append(' ');
                    state = JavaLexicalState.CODE;
                } else {
                    result.append(' ');
                }
            }
        }
        return result.toString();
    }

    private enum JavaLexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }

    private Map<String, Object> pistonPayload(String source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", "java");
        payload.put("version", "*");
        payload.put("files", List.of(Map.of("name", "Main.java", "content", source)));
        payload.put("stdin", "");
        payload.put("args", List.of());
        payload.put("compile_timeout", 10_000);
        payload.put("compile_cpu_time", 10_000);
        payload.put("compile_memory_limit", 384L * 1024L * 1024L);
        payload.put("run_timeout", 3_000);
        payload.put("run_cpu_time", 3_000);
        payload.put("run_memory_limit", 256L * 1024L * 1024L);
        return payload;
    }

    private HttpHeaders pistonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!pistonToken.isBlank()) {
            headers.setBearerAuth(pistonToken);
        }
        return headers;
    }

    private static void appendLeetCodeSupportTypes(StringBuilder source, String candidate) {
        if (candidate.contains("ListNode") && !declaresType(candidate, "ListNode")) {
            source.append("""
                    class ListNode {
                        int val;
                        ListNode next;
                        ListNode() {}
                        ListNode(int val) { this.val = val; }
                        ListNode(int val, ListNode next) { this.val = val; this.next = next; }
                    }

                    """);
        }
        if (candidate.contains("TreeNode") && !declaresType(candidate, "TreeNode")) {
            source.append("""
                    class TreeNode {
                        int val;
                        TreeNode left;
                        TreeNode right;
                        TreeNode() {}
                        TreeNode(int val) { this.val = val; }
                        TreeNode(int val, TreeNode left, TreeNode right) {
                            this.val = val;
                            this.left = left;
                            this.right = right;
                        }
                    }

                    """);
        }
        if (candidate.matches("(?s).*\\bNode\\b.*") && !declaresType(candidate, "Node")) {
            source.append("""
                    class Node {
                        int val;
                        Node next;
                        Node random;
                        Node left;
                        Node right;
                        List<Node> children;
                        List<Node> neighbors;
                        Node() {}
                        Node(int val) { this.val = val; }
                        Node(int val, Node next) { this.val = val; this.next = next; }
                        Node(int val, List<Node> values) {
                            this.val = val;
                            this.children = values;
                            this.neighbors = values;
                        }
                    }

                    """);
        }
    }

    private static boolean declaresType(String source, String typeName) {
        return Pattern.compile("\\b(?:class|record|interface|enum)\\s+" + Pattern.quote(typeName) + "\\b")
                .matcher(source)
                .find();
    }

    private static boolean isCompilerDiagnostic(String value) {
        return value != null && JAVA_COMPILER_DIAGNOSTIC.matcher(value).find();
    }

    private static Map<String, JudgeDefinition> definitions() {
        Map<String, JudgeDefinition> values = new LinkedHashMap<>();
        values.put("two-sum", new JudgeDefinition(
                """
                public class Main {
                    private static String out(int[] value) {
                        if (value != null) java.util.Arrays.sort(value);
                        return java.util.Arrays.toString(value);
                    }
                    public static void main(String[] args) {
                        java.io.PrintStream __judgeOut = java.lang.System.out;
                        java.io.PrintStream __candidateSink = new java.io.PrintStream(
                                java.io.OutputStream.nullOutputStream()
                        );
                        java.lang.System.setOut(__candidateSink);
                        Solution s;
                        try {
                            s = new Solution();
                        } finally {
                            java.lang.System.setOut(__judgeOut);
                        }
                        %s
                        java.lang.System.setOut(__judgeOut);
                        __candidateSink.close();
                    }
                }
                """,
                List.of(
                        "System.out.println(out(s.twoSum(new int[]{2,7,11,15}, 9)));",
                        "System.out.println(out(s.twoSum(new int[]{3,2,4}, 6)));",
                        "System.out.println(out(s.twoSum(new int[]{3,3}, 6)));",
                        "System.out.println(out(s.twoSum(new int[]{-3,4,3,90}, 0)));",
                        "System.out.println(out(s.twoSum(new int[]{1,5,8,2,11}, 9)));"
                ),
                List.of("[0, 1]", "[1, 2]", "[0, 1]", "[0, 2]", "[0, 2]")
        ));
        values.put("valid-parentheses", scalarDefinition(
                List.of(
                        "s.isValid(\"()\")", "s.isValid(\"()[]{}\")", "s.isValid(\"(]\")",
                        "s.isValid(\"([{}])\")", "s.isValid(\"(\")", "s.isValid(\"]\")"
                ),
                List.of("true", "true", "false", "true", "false", "false")
        ));
        values.put("binary-search", scalarDefinition(
                List.of(
                        "s.search(new int[]{-1,0,3,5,9,12}, 9)",
                        "s.search(new int[]{-1,0,3,5,9,12}, 2)",
                        "s.search(new int[]{5}, 5)",
                        "s.search(new int[]{5}, -5)",
                        "s.search(new int[]{1,2,3,4,5,6}, 1)",
                        "s.search(new int[]{1,2,3,4,5,6}, 6)"
                ),
                List.of("4", "-1", "0", "-1", "0", "5")
        ));
        values.put("maximum-subarray", scalarDefinition(
                List.of(
                        "s.maxSubArray(new int[]{-2,1,-3,4,-1,2,1,-5,4})",
                        "s.maxSubArray(new int[]{1})",
                        "s.maxSubArray(new int[]{5,4,-1,7,8})",
                        "s.maxSubArray(new int[]{-8,-3,-6,-2,-5,-4})",
                        "s.maxSubArray(new int[]{0,0,0})"
                ),
                List.of("6", "1", "23", "-2", "0")
        ));
        values.put("longest-substring-without-repeating-characters", scalarDefinition(
                List.of(
                        "s.lengthOfLongestSubstring(\"abcabcbb\")",
                        "s.lengthOfLongestSubstring(\"bbbbb\")",
                        "s.lengthOfLongestSubstring(\"pwwkew\")",
                        "s.lengthOfLongestSubstring(\"\")",
                        "s.lengthOfLongestSubstring(\"dvdf\")",
                        "s.lengthOfLongestSubstring(\"abba\")"
                ),
                List.of("3", "1", "3", "0", "3", "2")
        ));
        values.put("merge-intervals", new JudgeDefinition(
                """
                public class Main {
                    private static String out(int[][] value) {
                        java.util.Arrays.sort(value, java.util.Comparator.comparingInt(a -> a[0]));
                        return java.util.Arrays.deepToString(value);
                    }
                    public static void main(String[] args) {
                        java.io.PrintStream __judgeOut = java.lang.System.out;
                        java.io.PrintStream __candidateSink = new java.io.PrintStream(
                                java.io.OutputStream.nullOutputStream()
                        );
                        java.lang.System.setOut(__candidateSink);
                        Solution s;
                        try {
                            s = new Solution();
                        } finally {
                            java.lang.System.setOut(__judgeOut);
                        }
                        %s
                        java.lang.System.setOut(__judgeOut);
                        __candidateSink.close();
                    }
                }
                """,
                List.of(
                        "System.out.println(out(s.merge(new int[][]{{1,3},{2,6},{8,10},{15,18}})));",
                        "System.out.println(out(s.merge(new int[][]{{1,4},{4,5}})));",
                        "System.out.println(out(s.merge(new int[][]{{1,4},{0,2},{3,5}})));",
                        "System.out.println(out(s.merge(new int[][]{{1,4}})));"
                ),
                List.of("[[1, 6], [8, 10], [15, 18]]", "[[1, 5]]", "[[0, 5]]", "[[1, 4]]")
        ));
        values.put("minimum-window-substring", scalarDefinition(
                List.of(
                        "s.minWindow(\"ADOBECODEBANC\", \"ABC\")",
                        "s.minWindow(\"a\", \"a\")",
                        "s.minWindow(\"aa\", \"aa\")",
                        "s.minWindow(\"ab\", \"b\")",
                        "s.minWindow(\"bba\", \"ab\")"
                ),
                List.of("BANC", "a", "aa", "b", "ba")
        ));
        return Map.copyOf(values);
    }

    private static JudgeDefinition scalarDefinition(List<String> expressions, List<String> expected) {
        List<String> invocations = expressions.stream()
                .map(expression -> "System.out.println(" + expression + ");")
                .toList();
        return new JudgeDefinition(
                """
                public class Main {
                    public static void main(String[] args) {
                        java.io.PrintStream __judgeOut = java.lang.System.out;
                        java.io.PrintStream __candidateSink = new java.io.PrintStream(
                                java.io.OutputStream.nullOutputStream()
                        );
                        java.lang.System.setOut(__candidateSink);
                        Solution s;
                        try {
                            s = new Solution();
                        } finally {
                            java.lang.System.setOut(__judgeOut);
                        }
                        %s
                        java.lang.System.setOut(__judgeOut);
                        __candidateSink.close();
                    }
                }
                """,
                invocations,
                expected
        );
    }

    private record JudgeDefinition(
            String runnerTemplate,
            List<String> invocations,
            List<String> expected
    ) {
        String runner(int caseCount, String resultToken) {
            return runnerTemplate.formatted(invocations.subList(0, caseCount).stream()
                    .map(invocation -> guardInvocation(invocation, resultToken))
                    .collect(java.util.stream.Collectors.joining("\n        ")));
        }

        private static String guardInvocation(String invocation, String resultToken) {
            String expression = invocation.substring(
                    "System.out.println(".length(),
                    invocation.length() - ");".length()
            );
            String judgeInvocation = "__judgeOut.println(\"__XZM_RESULT_"
                    + resultToken
                    + "__:\" + ("
                    + expression
                    + "));";
            return """
                    java.lang.System.setOut(__candidateSink);
                    try {
                        %s
                    } finally {
                        java.lang.System.setOut(__judgeOut);
                    }
                    """.formatted(judgeInvocation).trim();
        }
    }

    private String nextResultToken() {
        String candidate;
        try {
            candidate = resultTokenSupplier.get();
        } catch (RuntimeException ignored) {
            candidate = "";
        }
        String safe = candidate == null ? "" : candidate.replaceAll("[^A-Za-z0-9]", "");
        return safe.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : safe;
    }

    private static JudgeOutput parseJudgeOutput(String output, String resultToken) {
        String prefix = "__XZM_RESULT_" + resultToken + "__:";
        List<String> values = new ArrayList<>();
        boolean unexpected = false;
        for (String rawLine : output.replace("\r", "").split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(prefix)) {
                values.add(line.substring(prefix.length()).trim());
            } else {
                unexpected = true;
            }
        }
        return new JudgeOutput(List.copyOf(values), unexpected);
    }

    private record JudgeOutput(List<String> values, boolean hasUnexpectedOutput) {
    }

    private static Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("代码执行失败");
    }

    private static String clip(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "\n[output truncated]";
    }

    private static String normalizeCustomOutput(String value) {
        return value == null
                ? ""
                : value.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }
}
