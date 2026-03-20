package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.service.AgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {
    @Autowired
    private AgentService agentService;

    @PostMapping("/list")
    public Result<PageVO<Agent>> getPage(@RequestBody PageDTO<Agent> page) {
        return Result.success(agentService.getPage(page));
    }

    @GetMapping("/{id}")
    public Result<Agent> getById(@PathVariable String id) {
        return Result.success(agentService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        return agentService.removeById(id) ? Result.success() : Result.error("删除失败");
    }

    @PostMapping
    public Result<Integer> add(@RequestBody Agent agent) {
        return Result.success(agentService.create(agent));
    }

    @PutMapping
    public Result<Void> update(@RequestBody Agent agent) {
        agentService.updateById(agent);
        agentService.reBuildAgent(agent.getId());
        return Result.success();
    }

    @PostMapping("/knowledge")
    public Result<Void> addVector(@RequestParam Integer agentId, @RequestParam Integer vectorId) {
        agentService.addVector(agentId, vectorId);
        return Result.success();
    }

    @DeleteMapping("/knowledge")
    public Result<Void> deleteVector(@RequestParam Integer agentId, @RequestParam Integer vectorId) {
        agentService.removeVector(agentId, vectorId);
        agentService.reBuildAgent(agentId);
        return Result.success();
    }
}
