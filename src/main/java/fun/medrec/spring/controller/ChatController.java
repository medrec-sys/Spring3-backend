package fun.medrec.spring.controller;

import fun.medrec.spring.Ai.AiAgent;
import fun.medrec.spring.service.AgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@Slf4j
public class ChatController {
    @Autowired
    private AgentService agentService;

    @PostMapping(value = "/chat/{id}", produces = "text/html;charset=utf-8")
    public Flux<String> chat(@RequestBody String question, @PathVariable Integer id) {
        AiAgent agent = AiAgent.getAgent(id);
        if (agent == null) {
            agent = agentService.createAgent(id);
        }
        return agent.chat(question);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        AiAgent agent = AiAgent.getAgent(id);
        if (agent == null) {
            agent = agentService.createAgent(id);
        }
        agent.delete(id);
    }

    @DeleteMapping("/all/{id}")
    public void deleteAll(@PathVariable Integer id) {
        AiAgent agent = AiAgent.getAgent(id);
        if (agent == null) {
            agent = agentService.createAgent(id);
        }
        agent.deleteAll();
    }

}
