package com.xzm.xzm_interview_helper.service;

import com.xzm.xzm_interview_helper.model.dto.AlgorithmExecutionResponse;
import com.xzm.xzm_interview_helper.model.dto.AlgorithmProblemSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AlgorithmJudgeServiceTest {

    private AlgorithmJudgeService judgeService;
    private MockRestServiceServer server;
    private AlgorithmProblemSummary problem;

    @BeforeEach
    void setUp() {
        judgeService = new AlgorithmJudgeService("http://judge.local/api/v2/execute", "");
        RestTemplate restTemplate =
                (RestTemplate) ReflectionTestUtils.getField(judgeService, "restTemplate");
        ReflectionTestUtils.setField(
                judgeService,
                "resultTokenSupplier",
                (java.util.function.Supplier<String>) () -> "testtoken"
        );
        server = MockRestServiceServer.bindTo(restTemplate).build();
        problem = new AlgorithmProblemSummary();
        problem.setSlug("two-sum");
        problem.setDifficulty("EASY");
        problem.setJudgeable(true);
    }

    @Test
    void sendsExplicitSandboxLimitsAndAcceptsMatchingPublicCases() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"run_memory_limit\":268435456"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"compile_timeout\":10000"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"content\":\"import java.util.*;\\n\\npublic class Main"
                )))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{"code":0,"stderr":"","status":null},
                          "run":{"code":0,"stderr":"","status":null,
                            "stdout":"__XZM_RESULT_testtoken__:[0, 1]\\n__XZM_RESULT_testtoken__:[1, 2]\\n"}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                "class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0, 1}; } }",
                false
        );

        assertEquals("ACCEPTED", response.getStatus());
        assertEquals(2, response.getPassedCases());
        assertEquals(2, response.getTotalCases());
        server.verify();
    }

    @Test
    void classifiesSourceFileModeCompilerDiagnosticsAsCompileErrors() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{},
                          "run":{
                            "code":1,
                            "stderr":"Main.java:17: error: missing return statement\\nerror: compilation failed",
                            "status":null
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                "class Solution { public int[] twoSum(int[] nums, int target) { } }",
                false
        );

        assertEquals("COMPILE_ERROR", response.getStatus());
        assertTrue(response.getError().contains("missing return statement"));
        server.verify();
    }

    @Test
    void compilesNonJudgeableProblemsAndMovesImportsAheadOfRunner() {
        AlgorithmProblemSummary compileOnlyProblem = new AlgorithmProblemSummary();
        compileOnlyProblem.setSlug("reverse-nodes-in-k-group");
        compileOnlyProblem.setDifficulty("HARD");
        compileOnlyProblem.setJudgeable(false);

        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"content\":\"import java.util.*;\\nimport java.time.Instant;\\n\\npublic class Main"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class ListNode"
                )))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{},
                          "run":{"code":0,"stderr":"","status":null,"stdout":"__XZM_COMPILE_OK__\\n"}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.execute(
                compileOnlyProblem,
                "java",
                """
                import java.time.Instant;
                class Solution {
                    ListNode reverseKGroup(ListNode head, int k) { return head; }
                }
                """,
                true
        );

        assertEquals("COMPILED", response.getStatus());
        assertEquals(0, response.getTotalCases());
        assertTrue(response.getOutput().contains("不代表代码逻辑正确"));
        server.verify();
    }

    @Test
    void treatsSandboxTimeoutStatusAsACompileErrorEvenWithoutStderr() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{"code":null,"stderr":"","status":"TO","message":"compile time limit exceeded"},
                          "run":{}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                "class Solution { public int[] twoSum(int[] nums, int target) { return null; } }",
                true
        );

        assertEquals("COMPILE_ERROR", response.getStatus());
        assertTrue(response.getError().contains("编译"));
        assertTrue(!response.getError().contains("time limit"));
        server.verify();
    }

    @Test
    void officialJudgeRejectsCandidateOutputChannelsBeforeCallingSandbox() {
        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                """
                class Solution {
                    int[] twoSum(int[] values, int target) {
                        System.out.println(target);
                        return new int[]{0, 1};
                    }
                }
                """,
                true
        );

        assertEquals("COMPILE_ERROR", response.getStatus());
        assertTrue(response.getError().contains("纯函数"));
        server.verify();
    }

    @Test
    void officialJudgeRejectsStaticSystemImportAliasBeforeCallingSandbox() {
        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                """
                import static java.lang.System.*;
                class Solution {
                    int[] twoSum(int[] values, int target) {
                        out.println(target);
                        exit(0);
                        return new int[]{0, 1};
                    }
                }
                """,
                true
        );

        assertEquals("COMPILE_ERROR", response.getStatus());
        assertTrue(response.getError().contains("纯函数"));
        server.verify();
    }

    @Test
    void officialJudgeRejectsCommentObfuscatedOutputApiBeforeCallingSandbox() {
        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                """
                import/* policy gap */static java.lang.System.*;
                class Solution {
                    int[] twoSum(int[] values, int target) {
                        java.lang.System/**/.out.println(target);
                        return new int[]{0, 1};
                    }
                }
                """,
                true
        );

        assertEquals("COMPILE_ERROR", response.getStatus());
        assertTrue(response.getError().contains("纯函数"));
        server.verify();
    }

    @Test
    void officialJudgeRejectsMultiUUnicodeEscapesBeforeCallingSandbox() {
        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                """
                class Solution {
                    int[] twoSum(int[] values, int target) {
                        // Java translates the escape before it recognizes this comment.
                        \\uuuu0053ystem.out.println(target);
                        return new int[]{0, 1};
                    }
                }
                """,
                true
        );

        assertEquals("COMPILE_ERROR", response.getStatus());
        assertTrue(response.getError().contains("纯函数"));
        server.verify();
    }

    @Test
    void officialJudgeRejectsHarnessSourceAndAsyncApisBeforeCallingSandbox() {
        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                """
                class Solution {
                    int[] twoSum(int[] values, int target) {
                        var source = Solution.class.getResourceAsStream("/Main.java");
                        java.util.concurrent.CompletableFuture.runAsync(() -> Main.main(null));
                        return new int[]{0, 1};
                    }
                }
                """,
                true
        );

        assertEquals("COMPILE_ERROR", response.getStatus());
        assertTrue(response.getError().contains("纯函数"));
        server.verify();
    }

    @Test
    void officialJudgeRejectsProcessInspectionApisBeforeCallingSandbox() {
        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                """
                class Solution {
                    int[] twoSum(int[] values, int target) {
                        long pid = ProcessHandle.current().pid();
                        return pid > 0 ? new int[]{0, 1} : null;
                    }
                }
                """,
                true
        );

        assertEquals("COMPILE_ERROR", response.getStatus());
        assertTrue(response.getError().contains("纯函数"));
        server.verify();
    }

    @Test
    void officialSubmitCannotAcceptExtraSpoofedLinesOrPersistRawOutput() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "java.lang.System.setOut(__candidateSink)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "__XZM_RESULT_testtoken__:"
                )))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{},
                          "run":{
                            "code":0,
                            "stderr":"",
                            "status":null,
                            "stdout":"__XZM_RESULT_testtoken__:[0, 1]\\n__XZM_RESULT_testtoken__:[1, 2]\\n__XZM_RESULT_testtoken__:[0, 1]\\n__XZM_RESULT_testtoken__:[0, 2]\\n__XZM_RESULT_testtoken__:[0, 2]\\nPRIVATE_OUTPUT\\n"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                "class Solution { int[] twoSum(int[] values, int target) { return new int[]{0, 1}; } }",
                true
        );

        assertEquals("WRONG_ANSWER", response.getStatus());
        assertEquals(0, response.getPassedCases());
        assertTrue(!response.getOutput().contains("PRIVATE_OUTPUT"));
        assertTrue(response.getOutput().contains("0/5"));
        server.verify();
    }

    @Test
    void officialSubmitRedactsCandidateControlledRuntimeErrors() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{},
                          "run":{
                            "code":1,
                            "stderr":"secret hidden input was 1,5,8,2,11",
                            "status":null
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                "class Solution { int[] twoSum(int[] values, int target) { throw new RuntimeException(); } }",
                true
        );

        assertEquals("RUNTIME_ERROR", response.getStatus());
        assertTrue(!response.getError().contains("1,5,8,2,11"));
        assertTrue(!response.getError().contains("hidden"));
        server.verify();
    }

    @Test
    void killedOfficialProcessCannotMasqueradeAsAcceptedWithSpoofedOutput() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{},
                          "run":{
                            "code":null,
                            "signal":"SIGKILL",
                            "stderr":"",
                            "stdout":"__XZM_RESULT_testtoken__:[0, 1]\\n__XZM_RESULT_testtoken__:[1, 2]\\n__XZM_RESULT_testtoken__:[0, 1]\\n__XZM_RESULT_testtoken__:[0, 2]\\n__XZM_RESULT_testtoken__:[0, 2]\\n"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.execute(
                problem,
                "java",
                "class Solution { int[] twoSum(int[] values, int target) { return null; } }",
                true
        );

        assertEquals("RUNTIME_ERROR", response.getStatus());
        assertEquals(0, response.getPassedCases());
        assertTrue(!response.getError().contains("SIGKILL"));
        server.verify();
    }

    @Test
    void customDriverUsesOnlyUserAuthoredCasesAndNeverReturnsOfficialJudgeStatus() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Solution solution = new Solution();"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "new int[]{4, 5}"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("new int[]{2,7,11,15}")
                )))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{"code":0,"stderr":"","status":null},
                          "run":{"code":0,"stderr":"","status":null,"stdout":"[0, 1]\\n"}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.executeCustom(
                problem,
                "java",
                "class Solution { int[] twoSum(int[] values, int target) { return new int[]{0, 1}; } }",
                """
                Solution solution = new Solution();
                System.out.println(java.util.Arrays.toString(
                    solution.twoSum(new int[]{4, 5}, 9)
                ));
                """,
                "[0, 1]"
        );

        assertEquals("CUSTOM_PASSED", response.getStatus());
        assertEquals(1, response.getPassedCases());
        assertEquals(1, response.getTotalCases());
        server.verify();
    }

    @Test
    void customExecutionWithoutExpectedOutputIsExecutedRatherThanAccepted() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{},
                          "run":{"code":0,"stderr":"","status":null,"stdout":"diagnostic\\n"}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.executeCustom(
                problem,
                "java",
                "class Solution { int value() { return 42; } }",
                "System.out.println(\"diagnostic\");",
                null
        );

        assertEquals("EXECUTED", response.getStatus());
        assertEquals(0, response.getTotalCases());
        server.verify();
    }

    @Test
    void customCompilerFailureCannotMasqueradeAsAnOfficialJudgeStatus() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{"code":1,"stderr":"Main.java:3: error: ';' expected"},
                          "run":{}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.executeCustom(
                problem,
                "java",
                "class Solution {}",
                "System.out.println(\"broken\")",
                "broken"
        );

        assertEquals("CUSTOM_FAILED", response.getStatus());
        assertTrue(response.getError().contains("expected"));
        server.verify();
    }

    @Test
    void malformedCustomSandboxResponseFailsClosed() {
        server.expect(requestTo("http://judge.local/api/v2/execute"))
                .andRespond(withSuccess(
                        """
                        {
                          "compile":{},
                          "run":{}
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AlgorithmExecutionResponse response = judgeService.executeCustom(
                problem,
                "java",
                "class Solution {}",
                "System.out.println(\"should not execute\");",
                null
        );

        assertEquals("CUSTOM_FAILED", response.getStatus());
        assertTrue(response.getCaseResults().stream().anyMatch(value -> value.contains("运行失败")));
        server.verify();
    }
}
