package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.service.AgentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/list")
    public Result<PageVO<Agent>> getPage(@RequestBody PageDTO<Agent> page) {
        return Result.success(agentService.getPage(page));
    }

    @GetMapping("/{id}")
    public Result<Agent> getById(@PathVariable String id) {
        return Result.success(agentService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Integer> delete(@PathVariable Integer id) {
        log.info("删除智能体 id: {}", id);
        return Result.success(agentService.deleteById(id));
    }

    @PostMapping
    public Result<Integer> add(@Valid @RequestBody Agent agent) {
        return Result.success(agentService.create(agent));
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody Agent agent) {
        agentService.updateById(agent);
        agentService.reBuildAgent(agent.getId());
        return Result.success();
    }

    @PostMapping("/knowledge")
    public Result<Void> addVector(@RequestParam Integer agentId, @RequestParam Integer vectorId) {
        agentService.addVector(agentId, vectorId);
        agentService.reBuildAgent(agentId);
        return Result.success();
    }

    @DeleteMapping("/knowledge")
    public Result<Void> deleteVector(@RequestParam Integer agentId, @RequestParam Integer vectorId) {
        agentService.removeVector(agentId, vectorId);
        agentService.reBuildAgent(agentId);
        return Result.success();
    }
}
