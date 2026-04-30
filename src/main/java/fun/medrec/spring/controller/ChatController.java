package fun.medrec.spring.controller;

import fun.medrec.spring.Ai.AiAgent;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Chat;
import fun.medrec.spring.service.AgentService;
import fun.medrec.spring.utils.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@Slf4j
public class ChatController {
    @Autowired
    private AgentService agentService;
    @Autowired
    private ModelUtil modelUtil;

    @PostMapping(value = "/chat/{id}", produces = "text/html;charset=utf-8")
    public Flux<String> chat(@RequestParam String question, @PathVariable Integer id) {
        AiAgent agent = AiAgent.getAgent(id);
        if (agent == null) {
            agent = agentService.createAgent(id);
        }
        return agent.chat(question);
    }

    @PostMapping(value = "/query", produces = "text/html;charset=utf-8")
    public Flux<String> query(
            @RequestParam String query,
            @RequestParam Integer n,
            @RequestBody List<String> messageList
            ) {
        List<UserMessage> l = messageList.stream().map(UserMessage::new).toList();
        List<Message> list = new ArrayList<>(l);

        return modelUtil.searchWithFlux(query, list, n);
    }

    // 加载历史聊天记录
    @GetMapping("/history/{id}")
    public Result<List<Chat>> getHistory(@PathVariable Integer id) {
        AiAgent agent = AiAgent.getAgent(id);
        if (agent == null) {
            agent = agentService.createAgent(id);
        }
        return Result.success(agent.getHistory(id));
    }

    @DeleteMapping("/{agentId}/{id}")
    public Result<Void> delete(@PathVariable Integer agentId, @PathVariable Integer id) {
        AiAgent agent = AiAgent.getAgent(agentId);
        if (agent == null) {
            agent = agentService.createAgent(agentId);
        }
        agent.delete(id);
        return Result.success();
    }

    @DeleteMapping("/all/{id}")
    public Result<Void> deleteAll(@PathVariable Integer id) {
        AiAgent agent = AiAgent.getAgent(id);
        if (agent == null) {
            agent = agentService.createAgent(id);
        }
        agent.deleteAll();
        return Result.success();
    }

    // 获取ai参考的知识片段片段
    @GetMapping("/vectors/{id}")
    public Result<List<Document>> getVectors(@PathVariable Integer id) {
        AiAgent agent = AiAgent.getAgent(id);
        if (agent == null) {
            agent = agentService.createAgent(id);
        }
        List<Document> documents = agentService.getVectors(agent);
        return Result.success(documents);
    }

}
