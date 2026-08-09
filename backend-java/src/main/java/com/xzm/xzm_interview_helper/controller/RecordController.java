package com.xzm.xzm_interview_helper.controller;

import com.xzm.xzm_interview_helper.mapper.AiConversationMapper;
import com.xzm.xzm_interview_helper.model.dto.ChatHistorySummaryDTO;
import com.xzm.xzm_interview_helper.model.entity.AiConversation;
import com.xzm.xzm_interview_helper.service.AiConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT-owned chat history endpoints.
 *
 * <p>The obsolete {@code /record/Interview/**} workflow was removed: the
 * frontend uses the durable {@code /interview-agent/**} state machine.</p>
 */
@RestController
@RequestMapping("record")
public class RecordController {

    @Autowired
    private AiConversationMapper aiConversationMapper;

    @Autowired
    private AiConversationService aiConversationService;

    @GetMapping("/histories/user/{userId}/page")
    public Map<String, Object> getHistoriesByUserPaged(
            @PathVariable int userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request
    ) {
        int authenticatedUserId = requireMatchingUserId(request, userId);
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        long offset = (long) (safePageNum - 1) * safePageSize;

        long total = aiConversationMapper.countDistinctMemoryIdByUser(authenticatedUserId);
        List<ChatHistorySummaryDTO> records =
                aiConversationMapper.selectHistorySummariesByUserPaged(
                        authenticatedUserId,
                        offset,
                        safePageSize
                );
        boolean hasMore = offset + records.size() < total;

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNum", safePageNum);
        result.put("pageSize", safePageSize);
        result.put("hasMore", hasMore);
        return result;
    }

    @GetMapping("/histories")
    public List<ChatHistorySummaryDTO> getHistories(HttpServletRequest request) {
        return aiConversationMapper.selectHistorySummariesByUserPaged(
                currentUserId(request),
                0,
                1000
        );
    }

    @GetMapping("/histories/user/{userId}")
    public List<ChatHistorySummaryDTO> getHistoriesByUser(
            @PathVariable int userId,
            HttpServletRequest request
    ) {
        return aiConversationMapper.selectHistorySummariesByUserPaged(
                requireMatchingUserId(request, userId),
                0,
                1000
        );
    }

    @GetMapping("/clear/{memoryId}/user/{userId}")
    public Map<String, Object> clearHistoryByUser(
            @PathVariable int memoryId,
            @PathVariable int userId,
            HttpServletRequest request
    ) {
        int authenticatedUserId = requireMatchingUserId(request, userId);
        boolean removed = aiConversationService.lambdaUpdate()
                .eq(AiConversation::getMemory_id, memoryId)
                .eq(AiConversation::getUser_id, authenticatedUserId)
                .remove();
        Map<String, Object> result = new HashMap<>();
        result.put("success", removed);
        return result;
    }

    @GetMapping("/count/{memoryId}/user/{userId}")
    public Long countHistoryByUser(
            @PathVariable int memoryId,
            @PathVariable int userId,
            HttpServletRequest request
    ) {
        int authenticatedUserId = requireMatchingUserId(request, userId);
        return aiConversationService.lambdaQuery()
                .eq(AiConversation::getMemory_id, memoryId)
                .eq(AiConversation::getUser_id, authenticatedUserId)
                .count();
    }

    @PostMapping("/voice_Conversion")
    public String voiceConversion(
            @RequestParam("Data") String data,
            @RequestParam("DataLen") Long dataLen
    ) {
        return "";
    }

    @GetMapping("/history/{memoryId}")
    public List<AiConversation> getHistory(
            @PathVariable int memoryId,
            HttpServletRequest request
    ) {
        return aiConversationService.lambdaQuery()
                .eq(AiConversation::getMemory_id, memoryId)
                .eq(AiConversation::getUser_id, currentUserId(request))
                .orderByAsc(AiConversation::getChat_time)
                .orderByAsc(AiConversation::getId)
                .list();
    }

    @GetMapping("/history/{memoryId}/user/{userId}")
    public List<AiConversation> getHistoryByUser(
            @PathVariable int memoryId,
            @PathVariable int userId,
            HttpServletRequest request
    ) {
        int authenticatedUserId = requireMatchingUserId(request, userId);
        return aiConversationService.lambdaQuery()
                .eq(AiConversation::getMemory_id, memoryId)
                .eq(AiConversation::getUser_id, authenticatedUserId)
                .orderByAsc(AiConversation::getChat_time)
                .orderByAsc(AiConversation::getId)
                .list();
    }

    @DeleteMapping("/history/{memoryId}")
    public Map<String, Object> deleteHistory(
            @PathVariable int memoryId,
            HttpServletRequest request
    ) {
        boolean removed = aiConversationService.lambdaUpdate()
                .eq(AiConversation::getMemory_id, memoryId)
                .eq(AiConversation::getUser_id, currentUserId(request))
                .remove();
        Map<String, Object> result = new HashMap<>();
        result.put("success", removed);
        return result;
    }

    /**
     * The JWT filter is the sole identity authority. Legacy chat-history endpoints retain their
     * userId parameters for client compatibility, but those values may only repeat that identity.
     */
    private int requireMatchingUserId(HttpServletRequest request, int suppliedUserId) {
        int authenticatedUserId = currentUserId(request);
        if (authenticatedUserId != suppliedUserId) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "无权访问其他用户的记录"
            );
        }
        return authenticatedUserId;
    }

    private int currentUserId(HttpServletRequest request) {
        Object rawUserId = request.getAttribute("userId");
        if (rawUserId instanceof Number number) {
            try {
                return Math.toIntExact(number.longValue());
            } catch (ArithmeticException ignored) {
                // User ids are stored as integers in this application.
            }
        }
        if (rawUserId instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // Fall through to the authorization error below.
            }
        }
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "未能识别当前登录用户"
        );
    }
}
